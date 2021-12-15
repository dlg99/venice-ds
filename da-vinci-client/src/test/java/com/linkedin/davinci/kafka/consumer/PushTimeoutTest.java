package com.linkedin.davinci.kafka.consumer;

import com.linkedin.davinci.compression.StorageEngineBackedCompressorFactory;
import com.linkedin.davinci.config.VeniceStoreVersionConfig;
import com.linkedin.davinci.notifier.VeniceNotifier;
import com.linkedin.davinci.storage.StorageMetadataService;
import com.linkedin.venice.exceptions.VeniceTimeoutException;
import com.linkedin.venice.meta.Store;
import com.linkedin.venice.meta.Version;
import com.linkedin.venice.offsets.OffsetRecord;
import com.linkedin.venice.utils.ExceptionCaptorNotifier;
import com.linkedin.venice.utils.TestUtils;
import com.linkedin.venice.utils.Utils;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Optional;
import java.util.Properties;
import java.util.Queue;
import java.util.function.BooleanSupplier;
import org.apache.kafka.clients.CommonClientConfigs;
import org.testng.Assert;
import org.testng.annotations.Test;

import static org.mockito.Mockito.*;


public class PushTimeoutTest {
  @Test
  public void testPushTimeoutForLeaderFollowerStores() {
    String storeName = Utils.getUniqueString("store");
    int versionNumber = 1;

    ExceptionCaptorNotifier exceptionCaptorNotifier = new ExceptionCaptorNotifier();
    Queue<VeniceNotifier> notifiers = new ArrayDeque<>();
    notifiers.add(exceptionCaptorNotifier);

    StoreIngestionTaskFactory.Builder builder = TestUtils.getStoreIngestionTaskBuilder(storeName)
        .setLeaderFollowerNotifiersQueue(notifiers);

    Store mockStore = builder.getMetadataRepo().getStoreOrThrow(storeName);
    Version version = mockStore.getVersion(versionNumber).get();

    Properties mockKafkaConsumerProperties = mock(Properties.class);
    doReturn("localhost").when(mockKafkaConsumerProperties).getProperty(eq(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG));

    VeniceStoreVersionConfig mockVeniceStoreVersionConfig = mock(VeniceStoreVersionConfig.class);
    String versionTopic = version.kafkaTopicName();
    doReturn(versionTopic).when(mockVeniceStoreVersionConfig).getStoreVersionName();

    LeaderFollowerStoreIngestionTask leaderFollowerStoreIngestionTask = new LeaderFollowerStoreIngestionTask(
        builder,
        mockStore,
        version,
        mockKafkaConsumerProperties,
        mock(BooleanSupplier.class),
        mockVeniceStoreVersionConfig,
        0,
        false,
        Optional.empty(),
        mock(StorageEngineBackedCompressorFactory.class));

    leaderFollowerStoreIngestionTask.subscribePartition(versionTopic, 0);
    leaderFollowerStoreIngestionTask.run();

    // Verify that push timeout happens
    Exception latestException = exceptionCaptorNotifier.getLatestException();
    Assert.assertNotNull(latestException, "Latest exception should not be null.");
    Assert.assertTrue(latestException instanceof VeniceTimeoutException,
        "Should have caught an instance of " + VeniceTimeoutException.class.getSimpleName()
            + "but instead got: " + latestException.getClass().getSimpleName() + ".");
  }

  @Test
  public void testReportIfCatchUpBaseTopicOffsetRouteWillNotMakePushTimeout() {
    String storeName = Utils.getUniqueString("store");
    int versionNumber = 1;

    ExceptionCaptorNotifier exceptionCaptorNotifier = new ExceptionCaptorNotifier();
    Queue<VeniceNotifier> notifiers = new ArrayDeque<>();
    notifiers.add(exceptionCaptorNotifier);

    StorageMetadataService mockStorageMetadataService = mock(StorageMetadataService.class);

    StoreIngestionTaskFactory.Builder builder = TestUtils.getStoreIngestionTaskBuilder(storeName)
        .setLeaderFollowerNotifiersQueue(notifiers)
        .setStorageMetadataService(mockStorageMetadataService);

    Store mockStore = builder.getMetadataRepo().getStoreOrThrow(storeName);
    Version version = mockStore.getVersion(versionNumber).get();

    Properties mockKafkaConsumerProperties = mock(Properties.class);
    doReturn("localhost").when(mockKafkaConsumerProperties).getProperty(eq(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG));

    VeniceStoreVersionConfig mockVeniceStoreVersionConfig = mock(VeniceStoreVersionConfig.class);
    String versionTopic = version.kafkaTopicName();
    doReturn(versionTopic).when(mockVeniceStoreVersionConfig).getStoreVersionName();

    OffsetRecord mockOffsetRecord = mock(OffsetRecord.class);
    doReturn(Collections.emptyMap()).when(mockOffsetRecord).getProducerPartitionStateMap();
    /**
     * After restart, report EOP already received, in order to trigger a call into
     * {@link StoreIngestionTask#reportIfCatchUpVersionTopicOffset(PartitionConsumptionState)}
     */
    doReturn(true).when(mockOffsetRecord).isEndOfPushReceived();
    doReturn(Version.composeRealTimeTopic(storeName)).when(mockOffsetRecord).getLeaderTopic();
    /**
     * Return 0 as the max offset for VT and 1 as the overall consume progress, so reportIfCatchUpVersionTopicOffset()
     * will determine that base topic is caught up.
     */
    doReturn(1L).when(mockOffsetRecord).getLocalVersionTopicOffset();
    doReturn(mockOffsetRecord).when(mockStorageMetadataService).getLastOffset(eq(versionTopic), eq(0));

    LeaderFollowerStoreIngestionTask leaderFollowerStoreIngestionTask = new LeaderFollowerStoreIngestionTask(
        builder,
        mockStore,
        version,
        mockKafkaConsumerProperties,
        mock(BooleanSupplier.class),
        mockVeniceStoreVersionConfig,
        0,
        false,
        Optional.empty(),
        mock(StorageEngineBackedCompressorFactory.class));

    leaderFollowerStoreIngestionTask.subscribePartition(versionTopic, 0);
    /**
     * Since the mock consumer would show 0 subscription, the ingestion task will close after a few iteration.
     */
    leaderFollowerStoreIngestionTask.run();

    Assert.assertNull(exceptionCaptorNotifier.getLatestException());
  }
}
