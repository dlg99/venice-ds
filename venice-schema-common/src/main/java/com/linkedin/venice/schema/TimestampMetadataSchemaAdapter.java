package com.linkedin.venice.schema;

import com.linkedin.venice.exceptions.VeniceException;
import org.apache.avro.Schema;


/**
 * This is simply a wrapper class to delegate the actual schema generation to version specific adapter.
 */

public class TimestampMetadataSchemaAdapter {
  private static final TimestampMetadataSchemaAdapterV1 V1_REPLICATION_METADATA_SCHEMA_ADAPTER = new TimestampMetadataSchemaAdapterV1();

  private TimestampMetadataSchemaAdapter() {}

  public static Schema parse(String schemaStr, int version) {
    return parse(Schema.parse(schemaStr), version);
  }

  public static Schema parse(Schema schema, int version) {
    switch (version) {
      case 1:
        return V1_REPLICATION_METADATA_SCHEMA_ADAPTER.parse(schema);
      default:
        throw new VeniceException("Unknown timestamp metadata version id");
    }
  }
}