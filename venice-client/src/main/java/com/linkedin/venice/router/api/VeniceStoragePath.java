package com.linkedin.venice.router.api;

import com.linkedin.ddsstorage.router.api.ResourcePath;
import com.linkedin.venice.RequestConstants;
import java.util.ArrayList;
import java.util.Collection;


/**
 * Created by mwise on 3/3/16.
 */
public class VeniceStoragePath implements ResourcePath<RouterKey> {

  private String resourceName;
  private Collection<RouterKey> keys;
  private String partition;

  public VeniceStoragePath(String resourceName, Collection<RouterKey> keys, String partition){
    this.resourceName = resourceName;
    this.keys = keys;
    this.partition = partition;
  }

  @Override
  public String getLocation() {
    String sep = VenicePathParser.SEP;
    return VenicePathParser.TYPE_STORAGE + sep +
        resourceName + sep +
        partition + sep +
        getPartitionKey().base64Encoded() + "?" + RequestConstants.FORMAT_KEY +"="+ RequestConstants.B64_FORMAT;
  }

  @Override
  public Collection<RouterKey> getPartitionKeys() {
    return keys;
  }

  @Override
  public String getResourceName() {
    return resourceName;
  }

  @Override
  public VeniceStoragePath clone() {
    return new VeniceStoragePath(new String(resourceName), new ArrayList<>(keys), new String(partition));
  }
}
