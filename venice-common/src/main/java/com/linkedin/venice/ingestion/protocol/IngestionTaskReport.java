/**
 * Autogenerated by Avro
 * 
 * DO NOT EDIT DIRECTLY
 */
package com.linkedin.venice.ingestion.protocol;

@SuppressWarnings("all")
public class IngestionTaskReport extends org.apache.avro.specific.SpecificRecordBase implements org.apache.avro.specific.SpecificRecord {
  public static final org.apache.avro.Schema SCHEMA$ = org.apache.avro.Schema.parse("{\"type\":\"record\",\"name\":\"IngestionTaskReport\",\"namespace\":\"com.linkedin.venice.ingestion.protocol\",\"fields\":[{\"name\":\"topicName\",\"type\":\"string\",\"default\":\"\"},{\"name\":\"partitionId\",\"type\":\"int\"},{\"name\":\"offset\",\"type\":\"long\"},{\"name\":\"isPositive\",\"type\":\"boolean\",\"default\":true},{\"name\":\"reportType\",\"type\":\"int\",\"doc\":\"0 => Completed, 1=> Errored, 2 => Started, 3 => Restarted, 4 => Progress, 5 => EndOfPushReceived, 6 => StartOfBufferReplayReceived, 7 => StartOfIncrementalPushReceived, 8 => EndOfIncrementalPushReceived, 9 => TopicSwitchReceived\"},{\"name\":\"message\",\"type\":\"string\",\"default\":\"\"},{\"name\":\"offsetRecordArray\",\"type\":{\"type\":\"array\",\"items\":\"bytes\"},\"default\":[]},{\"name\":\"storeVersionState\",\"type\":[\"null\",\"bytes\"],\"default\":null},{\"name\":\"leaderFollowerState\",\"type\":\"int\"}]}");
  public java.lang.CharSequence topicName;
  public int partitionId;
  public long offset;
  public boolean isPositive;
  /** 0 => Completed, 1=> Errored, 2 => Started, 3 => Restarted, 4 => Progress, 5 => EndOfPushReceived, 6 => StartOfBufferReplayReceived, 7 => StartOfIncrementalPushReceived, 8 => EndOfIncrementalPushReceived, 9 => TopicSwitchReceived */
  public int reportType;
  public java.lang.CharSequence message;
  public java.util.List<java.nio.ByteBuffer> offsetRecordArray;
  public java.nio.ByteBuffer storeVersionState;
  public int leaderFollowerState;
  public org.apache.avro.Schema getSchema() { return SCHEMA$; }
  // Used by DatumWriter.  Applications should not call. 
  public java.lang.Object get(int field$) {
    switch (field$) {
    case 0: return topicName;
    case 1: return partitionId;
    case 2: return offset;
    case 3: return isPositive;
    case 4: return reportType;
    case 5: return message;
    case 6: return offsetRecordArray;
    case 7: return storeVersionState;
    case 8: return leaderFollowerState;
    default: throw new org.apache.avro.AvroRuntimeException("Bad index");
    }
  }
  // Used by DatumReader.  Applications should not call. 
  @SuppressWarnings(value="unchecked")
  public void put(int field$, java.lang.Object value$) {
    switch (field$) {
    case 0: topicName = (java.lang.CharSequence)value$; break;
    case 1: partitionId = (java.lang.Integer)value$; break;
    case 2: offset = (java.lang.Long)value$; break;
    case 3: isPositive = (java.lang.Boolean)value$; break;
    case 4: reportType = (java.lang.Integer)value$; break;
    case 5: message = (java.lang.CharSequence)value$; break;
    case 6: offsetRecordArray = (java.util.List<java.nio.ByteBuffer>)value$; break;
    case 7: storeVersionState = (java.nio.ByteBuffer)value$; break;
    case 8: leaderFollowerState = (java.lang.Integer)value$; break;
    default: throw new org.apache.avro.AvroRuntimeException("Bad index");
    }
  }
}
