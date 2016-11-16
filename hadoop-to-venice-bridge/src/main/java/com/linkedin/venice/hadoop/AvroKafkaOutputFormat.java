package com.linkedin.venice.hadoop;

import com.linkedin.venice.hadoop.utils.HadoopUtils;
import com.linkedin.venice.utils.VeniceProperties;
import org.apache.avro.mapred.AvroWrapper;
import org.apache.avro.generic.IndexedRecord;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.OutputFormat;
import org.apache.hadoop.mapred.RecordWriter;
import org.apache.hadoop.util.Progressable;

import java.io.IOException;

/**
 * An {@link org.apache.hadoop.mapred.OutputFormat} implementation which instantiates
 * and configures an {@link com.linkedin.venice.hadoop.AvroKafkaRecordWriter} in order
 * to write a job's output into Kafka.
 */
public class AvroKafkaOutputFormat implements OutputFormat<AvroWrapper<IndexedRecord>, NullWritable> {
  public AvroKafkaOutputFormat() {
    super();
  }

  @Override
  public RecordWriter<AvroWrapper<IndexedRecord>, NullWritable> getRecordWriter(FileSystem fileSystem,
                                                                                JobConf conf,
                                                                                String arg2,
                                                                                Progressable progress) throws IOException {
    VeniceProperties props = HadoopUtils.getVeniceProps(conf);
    return new AvroKafkaRecordWriter(props, progress);
  }

  @Override
  public void checkOutputSpecs(FileSystem arg0, JobConf arg1) throws IOException {
    // TODO Auto-generated method stub

  }
}
