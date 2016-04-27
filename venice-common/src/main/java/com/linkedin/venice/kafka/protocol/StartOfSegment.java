/**
 * Autogenerated by Avro
 * 
 * DO NOT EDIT DIRECTLY
 */
package com.linkedin.venice.kafka.protocol;

@SuppressWarnings("all")
/** This ControlMessage is sent at least once per partition per producer. It may be sent more than once per partition/producer, but only after the producer has sent an EndOfSegment into that partition to terminate the previously started segment. */
public class StartOfSegment extends org.apache.avro.specific.SpecificRecordBase implements org.apache.avro.specific.SpecificRecord {
  public static final org.apache.avro.Schema SCHEMA$ = org.apache.avro.Schema.parse("{\"type\":\"record\",\"name\":\"StartOfSegment\",\"namespace\":\"com.linkedin.venice.kafka.protocol\",\"fields\":[{\"name\":\"checksumType\",\"type\":\"int\",\"doc\":\"Using int because Avro Enums are not evolvable. Readers should always handle the 'unknown' value edge case, to account for future evolutions of this protocol. The downstream consumer is expected to compute this checksum and use it to validate the incoming stream of data. The current mapping is the following: 0 => MD5, 1 => Adler32, 2 => CRC32.\"},{\"name\":\"upcomingAggregates\",\"type\":{\"type\":\"array\",\"items\":\"string\"},\"doc\":\"An array of names of aggregate computation strategies for which there will be a value percolated in the corresponding EndOfSegment ControlMessage. The downstream consumer may choose to compute these aggregates on its own and use them as additional validation safeguards, or it may choose to merely log them, or even ignore them altogether.\"}]}");
  /** Using int because Avro Enums are not evolvable. Readers should always handle the 'unknown' value edge case, to account for future evolutions of this protocol. The downstream consumer is expected to compute this checksum and use it to validate the incoming stream of data. The current mapping is the following: 0 => MD5, 1 => Adler32, 2 => CRC32. */
  public int checksumType;
  /** An array of names of aggregate computation strategies for which there will be a value percolated in the corresponding EndOfSegment ControlMessage. The downstream consumer may choose to compute these aggregates on its own and use them as additional validation safeguards, or it may choose to merely log them, or even ignore them altogether. */
  public java.util.List<java.lang.CharSequence> upcomingAggregates;
  public org.apache.avro.Schema getSchema() { return SCHEMA$; }
  // Used by DatumWriter.  Applications should not call. 
  public java.lang.Object get(int field$) {
    switch (field$) {
    case 0: return checksumType;
    case 1: return upcomingAggregates;
    default: throw new org.apache.avro.AvroRuntimeException("Bad index");
    }
  }
  // Used by DatumReader.  Applications should not call. 
  @SuppressWarnings(value="unchecked")
  public void put(int field$, java.lang.Object value$) {
    switch (field$) {
    case 0: checksumType = (java.lang.Integer)value$; break;
    case 1: upcomingAggregates = (java.util.List<java.lang.CharSequence>)value$; break;
    default: throw new org.apache.avro.AvroRuntimeException("Bad index");
    }
  }
}
