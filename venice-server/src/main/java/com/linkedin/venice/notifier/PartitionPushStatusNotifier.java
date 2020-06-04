package com.linkedin.venice.notifier;

import com.linkedin.venice.helix.HelixPartitionPushStatusAccessor;
import com.linkedin.venice.pushmonitor.ExecutionStatus;


/**
 * Notifier used to update replica status by Helix partition push status accessor.
 */
public class PartitionPushStatusNotifier implements VeniceNotifier {

  private HelixPartitionPushStatusAccessor accessor;

  public PartitionPushStatusNotifier(HelixPartitionPushStatusAccessor accessor) {
    this.accessor = accessor;
  }

  @Override
  public void started(String topic, int partitionId, String message) {
    accessor.updateReplicaStatus(topic, partitionId, ExecutionStatus.STARTED);
  }

  @Override
  public void restarted(String topic, int partitionId, long offset, String message) {
    accessor.updateReplicaStatus(topic, partitionId, ExecutionStatus.STARTED);
  }

  @Override
  public void completed(String topic, int partitionId, long offset, String message) {
    accessor.updateReplicaStatus(topic, partitionId, ExecutionStatus.COMPLETED);
  }

  @Override
  public void progress(String topic, int partitionId, long offset, String message) {
    accessor.updateReplicaStatus(topic, partitionId, ExecutionStatus.PROGRESS);
  }

  @Override
  public void endOfPushReceived(String topic, int partitionId, long offset, String message) {
    accessor.updateReplicaStatus(topic, partitionId, ExecutionStatus.END_OF_PUSH_RECEIVED);
  }

  @Override
  public void startOfBufferReplayReceived(String topic, int partitionId, long offset, String message) {
    accessor.updateReplicaStatus(topic, partitionId, ExecutionStatus.START_OF_BUFFER_REPLAY_RECEIVED);
  }

  @Override
  public void topicSwitchReceived(String topic, int partitionId, long offset, String message) {
    accessor.updateReplicaStatus(topic, partitionId, ExecutionStatus.TOPIC_SWITCH_RECEIVED);
  }

  @Override
  public void startOfIncrementalPushReceived(String topic, int partitionId, long offset, String message) {
    accessor.updateReplicaStatus(topic, partitionId, ExecutionStatus.START_OF_INCREMENTAL_PUSH_RECEIVED);
  }

  @Override
  public void endOfIncrementalPushReceived(String topic, int partitionId, long offset, String message) {
    accessor.updateReplicaStatus(topic, partitionId, ExecutionStatus.END_OF_INCREMENTAL_PUSH_RECEIVED);
  }

  @Override
  public void close() {
    // Do not need to close here. accessor should be closed by the outer class.
  }

  @Override
  public void error(String topic, int partitionId, String message, Exception ex) {
    //TODO record error message as well.
    accessor.updateReplicaStatus(topic, partitionId, ExecutionStatus.ERROR);
  }
}