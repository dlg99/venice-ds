package com.linkedin.venice;

import com.linkedin.venice.utils.Time;
import java.util.function.Supplier;
import org.apache.kafka.common.config.SslConfigs;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.conscrypt.Conscrypt;


public class ConfigConstants {
  private static final Logger LOGGER = LogManager.getLogger(ConfigConstants.class);
  /**
   * Start of controller config default value
   */

  /**
   * Default value of sleep interval for polling topic deletion status from ZK.
   */
  public static final int DEFAULT_TOPIC_DELETION_STATUS_POLL_INTERVAL_MS = 2 * Time.MS_PER_SECOND;

  public static final long DEFAULT_KAFKA_ADMIN_GET_TOPIC_CONFIG_RETRY_IN_SECONDS = 600;

  public static final int UNSPECIFIED_REPLICATION_METADATA_VERSION = -1;

  /**
   * End of controller config default value
   */

  // Start of server config default value

  /**
   * Default Kafka SSL context provider class name.
   *
   * {@link org.apache.kafka.common.security.ssl.BoringSslContextProvider} supports openssl.
   * BoringSSL is the c implementation of OpenSSL, and conscrypt add a java wrapper around BoringSSL.
   * The default BoringSslContextProvider mainly relies on conscrypt.
   */
  public static final String DEFAULT_KAFKA_SSL_CONTEXT_PROVIDER_CLASS_NAME = ((Supplier<String>) () -> {
    try {
      Conscrypt.checkAvailability();
      return "org.apache.kafka.common.security.ssl.BoringSslContextProvider";
    } catch (UnsatisfiedLinkError e) {
      LOGGER.warn("Conscrypt is not available, falling back to {}", SslConfigs.DEFAULT_SSL_CONTEXT_PROVIDER_CLASS, e);
      return SslConfigs.DEFAULT_SSL_CONTEXT_PROVIDER_CLASS;
    }
  }).get();

  /**
   * Default Kafka batch size and linger time for better producer performance during ingestion.
   */
  public static final String DEFAULT_KAFKA_BATCH_SIZE = "524288";

  public static final String DEFAULT_KAFKA_LINGER_MS = "1000";
  // End of server config default value
}
