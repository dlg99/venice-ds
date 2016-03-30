package com.linkedin.venice.controller.server;

import com.linkedin.venice.controller.Admin;
import com.linkedin.venice.controllerapi.ControllerClient;
import com.linkedin.venice.controllerapi.StoreCreationResponse;
import com.linkedin.venice.integration.utils.PortUtils;
import com.linkedin.venice.meta.Version;
import org.mockito.Mockito;
import static org.mockito.Mockito.*;

import org.testng.Assert;
import org.testng.annotations.Test;

public class ClientTest {
  private static final String STORE_NAME = "mystore";
  private static final String OWNER = "matt-the-awesome";
  private static final int VERSION = 17;

  @Test
  public void serverCanTalkToClient()
      throws Exception {
    int retry = 5;
    while (retry>0) {
      try {
        int port = PortUtils.getFreePort();
        String controllerUrl = "http://localhost:"+ port;
        ControllerClient client = new ControllerClient(controllerUrl);

        Admin mockAdmin = Mockito.mock(Admin.class);
        Version version = new Version(STORE_NAME, VERSION );
        doReturn(version).when(mockAdmin)
            .incrementVersion(Mockito.anyString(), Mockito.anyString(), Mockito.anyInt(), Mockito.anyInt());
        AdminSparkServer server = new AdminSparkServer(port, "cluster-for-tests", mockAdmin);
        server.start();
        int storeSizeMb = 500;
        StoreCreationResponse response = client.createNewStoreVersion(STORE_NAME, OWNER, storeSizeMb);
        server.stop();

        Assert.assertEquals(response.getName(), STORE_NAME);
        Assert.assertEquals(response.getVersion(), VERSION);
        Assert.assertEquals(response.getOwner(), OWNER);
        Assert.assertEquals(response.getPartitions(), 3);  //TODO change this when we add actual partition calculation logic
        Assert.assertEquals(response.getReplicas(), 1);
        break;
      } catch (java.net.BindException e) {
        System.err.println("Failed to bind to port, trying again " + retry-- + " more times");
        if (retry <= 0){
          throw new Exception(ClientTest.class.toString() + " couldn't get a free port", e);
        }
      }
    }
  }
}
