package com.linkedin.davinci.consumer;

import com.linkedin.venice.client.store.ClientFactory;
import com.linkedin.venice.controllerapi.D2ControllerClient;
import com.linkedin.venice.controllerapi.D2ControllerClientFactory;
import com.linkedin.venice.controllerapi.StoreResponse;
import com.linkedin.venice.exceptions.VeniceException;
import com.linkedin.venice.meta.ViewConfig;
import com.linkedin.venice.views.ChangeCaptureView;
import io.tehuti.metrics.MetricsRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.kafka.clients.consumer.KafkaConsumer;


public class VeniceChangelogConsumerClientFactory {
  private final Map<String, VeniceChangelogConsumer> storeClientMap = new HashMap<>();

  private final MetricsRepository metricsRepository;

  private final ChangelogClientConfig globalChangelogClientConfig;

  private D2ControllerClient d2ControllerClient;

  private KafkaConsumer consumer;

  protected ViewClassGetter viewClassGetter;

  public VeniceChangelogConsumerClientFactory(
      ChangelogClientConfig globalChangelogClientConfig,
      MetricsRepository metricsRepository) {
    this.globalChangelogClientConfig = globalChangelogClientConfig;
    this.metricsRepository = metricsRepository;
    this.viewClassGetter = getDefaultViewClassGetter();
  }

  protected void setD2ControllerClient(D2ControllerClient d2ControllerClient) {
    this.d2ControllerClient = d2ControllerClient;
  }

  protected void setConsumer(KafkaConsumer consumer) {
    this.consumer = consumer;
  }

  public synchronized <K, V> VeniceChangelogConsumer<K, V> getChangelogConsumer(String storeName) {
    return storeClientMap.computeIfAbsent(storeName, name -> {

      ChangelogClientConfig newStoreChangelogClientConfig =
          ChangelogClientConfig.cloneConfig(globalChangelogClientConfig).setStoreName(storeName);
      newStoreChangelogClientConfig.getInnerClientConfig().setMetricsRepository(metricsRepository);

      D2ControllerClient d2ControllerClient;
      if (this.d2ControllerClient != null) {
        d2ControllerClient = this.d2ControllerClient;
      } else if (newStoreChangelogClientConfig.getD2Client() != null) {
        d2ControllerClient = D2ControllerClientFactory.discoverAndConstructConrollerClient(
            storeName,
            globalChangelogClientConfig.getControllerD2ServiceName(),
            globalChangelogClientConfig.getControllerRequestRetryCount(),
            newStoreChangelogClientConfig.getD2Client());
      } else {
        d2ControllerClient = D2ControllerClientFactory.discoverAndConstructControllerClient(
            storeName,
            globalChangelogClientConfig.getControllerD2ServiceName(),
            globalChangelogClientConfig.getLocalD2ZkHosts(),
            Optional.ofNullable(newStoreChangelogClientConfig.getInnerClientConfig().getSslFactory()),
            globalChangelogClientConfig.getControllerRequestRetryCount());
      }
      newStoreChangelogClientConfig.setD2ControllerClient(d2ControllerClient);
      if (newStoreChangelogClientConfig.getSchemaReader() == null) {
        newStoreChangelogClientConfig
            .setSchemaReader(ClientFactory.getSchemaReader(newStoreChangelogClientConfig.getInnerClientConfig()));
      }

      // TODO: This is a redundant controller query. Need to condense it with the storeInfo query that happens
      // inside the changecaptureclient itself
      String viewClass =
          newStoreChangelogClientConfig.getViewName() == null ? "" : newStoreChangelogClientConfig.getViewName();
      if (!viewClass.isEmpty()) {
        viewClass = getViewClass(
            storeName,
            newStoreChangelogClientConfig.getViewName(),
            d2ControllerClient,
            globalChangelogClientConfig.getControllerRequestRetryCount());
      }
      if (viewClass.equals(ChangeCaptureView.class.getCanonicalName())) {
        return new VeniceChangelogConsumerImpl(
            newStoreChangelogClientConfig,
            consumer != null ? consumer : new KafkaConsumer<>(newStoreChangelogClientConfig.getConsumerProperties()));
      }
      return new VeniceAfterImageConsumerImpl(
          newStoreChangelogClientConfig,
          consumer != null ? consumer : new KafkaConsumer<>(newStoreChangelogClientConfig.getConsumerProperties()));
    });
  }

  private String getViewClass(String storeName, String viewName, D2ControllerClient d2ControllerClient, int retries) {
    return this.viewClassGetter.apply(storeName, viewName, d2ControllerClient, retries);
  }

  private ViewClassGetter getDefaultViewClassGetter() {
    return (String storeName, String viewName, D2ControllerClient d2ControllerClient, int retries) -> {
      StoreResponse response =
          d2ControllerClient.retryableRequest(retries, controllerClient -> controllerClient.getStore(storeName));
      if (response.isError()) {
        throw new VeniceException(
            "Couldn't retrieve store information when building change capture client for store " + storeName);
      }
      ViewConfig viewConfig = response.getStore().getViewConfigs().get(viewName);
      if (viewConfig == null) {
        throw new VeniceException(
            "Couldn't retrieve store view information when building change capture client for store " + storeName
                + " viewName " + viewName);
      }
      return viewConfig.getViewClassName();
    };
  }

  protected void setViewClassGetter(ViewClassGetter viewClassGetter) {
    this.viewClassGetter = viewClassGetter;
  }

  @FunctionalInterface
  public interface ViewClassGetter {
    String apply(String storeName, String viewName, D2ControllerClient d2ControllerClient, int retries);
  }
}
