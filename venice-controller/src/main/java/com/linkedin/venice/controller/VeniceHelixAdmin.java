package com.linkedin.venice.controller;

import com.linkedin.venice.controller.kafka.TopicCreator;
import com.linkedin.venice.controlmessage.StatusUpdateMessage;
import com.linkedin.venice.exceptions.VeniceException;
import com.linkedin.venice.helix.HelixAdapterSerializer;
import com.linkedin.venice.helix.HelixCachedMetadataRepository;
import com.linkedin.venice.helix.HelixControlMessageChannel;
import com.linkedin.venice.helix.HelixJobRepository;
import com.linkedin.venice.helix.HelixRoutingDataRepository;
import com.linkedin.venice.job.ExecutionStatus;
import com.linkedin.venice.meta.Store;
import com.linkedin.venice.meta.Version;
import java.util.HashMap;
import java.util.Map;
import org.apache.helix.ControllerChangeListener;
import org.apache.helix.HelixAdmin;
import org.apache.helix.HelixManager;
import org.apache.helix.HelixManagerFactory;
import org.apache.helix.InstanceType;
import org.apache.helix.NotificationContext;
import org.apache.helix.manager.zk.ZKHelixAdmin;
import org.apache.helix.manager.zk.ZKHelixManager;
import org.apache.helix.manager.zk.ZkClient;
import org.apache.helix.model.HelixConfigScope;
import org.apache.helix.model.IdealState;
import org.apache.helix.model.builder.HelixConfigScopeBuilder;
import org.apache.log4j.Logger;


/**
 * Helix Admin based on 0.6.5 APIs.
 */
public class VeniceHelixAdmin implements Admin,ControllerChangeListener {
    private final String controllerName;
    private final String zkConnString;
    private final String kafkaBootstrapServers;

    private final Map<String, HelixManager> helixManagers = new HashMap<>();
    private static final Logger logger = Logger.getLogger(VeniceHelixAdmin.class.getName());
    private final HelixAdmin admin;
    private TopicCreator topicCreator;
    private final ZkClient zkClient;
    private final Map<String, HelixCachedMetadataRepository> repositories = new HashMap<>();
    private final Map<String, VeniceControllerClusterConfig> configs = new HashMap<>();
    private final Map<String, VeniceJobManager> jobManagers = new HashMap<>();
    private final Map<String, HelixControlMessageChannel> channels = new HashMap<>();

    public VeniceHelixAdmin(String controllerName, String zkConnString,
            String kafkaZkConnString, String kafkaBootstrapServers) {
        /* Controller name can be generated from the hostname and
        VMID https://docs.oracle.com/javase/7/docs/api/java/rmi/dgc/VMID.html
        but taking this parameter from the user for now
         */
        this.controllerName = controllerName;
        this.zkConnString = zkConnString;
        this.kafkaBootstrapServers =  kafkaBootstrapServers;
        this.topicCreator = new TopicCreator(kafkaZkConnString);
        admin = new ZKHelixAdmin(zkConnString);
        //There is no way to get the internal zkClient from HelixManager or HelixAdmin. So create a new one here.
        zkClient = new ZkClient(zkConnString, ZkClient.DEFAULT_SESSION_TIMEOUT, ZkClient.DEFAULT_CONNECTION_TIMEOUT);
    }

    @Override
    public synchronized void start(String clusterName, VeniceControllerClusterConfig config) {
        if (helixManagers.containsKey(clusterName)) {
            throw new VeniceException("Cluster " + clusterName + " already has a helix controller ");
        }
        //Simply validate cluster name here.
        clusterName = clusterName.trim();
        if (clusterName.startsWith("/") || clusterName.endsWith("/") || clusterName.indexOf(' ') >= 0) {
            throw new IllegalArgumentException("Invalid cluster name:" + clusterName);
        }
        configs.put(clusterName, config);
        createClusterIfRequired(clusterName);

        // Use CONTROLLER_PARTICPANT to let Helix treat controllers as a cluster and do the leader election when the
        // master controller is failed.
        // TODO need double check with Helix team again. If we use CONTROLLER_PARTICIPANT here, controller will also
        // TODO receive state transition message in some cases.
        HelixManager helixManager = HelixManagerFactory.getZKHelixManager(clusterName,
                                            controllerName,
                                            InstanceType.CONTROLLER,
                                            zkConnString);
        try {
            helixManager.connect();
        } catch (Exception ex) {
            String errorMessage = " Error starting Helix controller cluster " +
                    clusterName + " controller " + controllerName;
            logger.error( errorMessage , ex );
            throw new VeniceException(errorMessage , ex);
        }
        helixManagers.put(clusterName, helixManager);
        helixManager.addControllerListener(this);

        logger.info(
            "VeniceHelixAdmin is started. Controller name: '" + controllerName + "', Cluster name: '" + clusterName
                + "'.");
    }

    @Override
    public synchronized void addStore(String clusterName, String storeName, String owner) {
        checkControllerMastership(clusterName);
        HelixCachedMetadataRepository repository = repositories.get(clusterName);
        if(repository.getStore(storeName)!=null){
            handleStoreAlreadyExists(clusterName, storeName);
        }
        VeniceControllerClusterConfig config = configs.get(clusterName);
        Store store = new Store(storeName, owner, System.currentTimeMillis(), config.getPersistenceType(),
            config.getRoutingStrategy(), config.getReadStrategy(), config.getOfflinePushStrategy());
        repository.addStore(store);
    }

    @Override
    public synchronized  Version addVersion(String clusterName, String storeName, int versionNumber) {
        checkControllerMastership(clusterName);
        VeniceControllerClusterConfig config = configs.get(clusterName);
        this.addVersion(clusterName, storeName, versionNumber, config.getNumberOfPartition(), config.getReplicaFactor());
    }

    @Override
        
    public synchronized Version addVersion(String clusterName, String storeName,int versionNumber, int numberOfPartition, int replicaFactor) {
        checkControllerMastership(clusterName);
        HelixCachedMetadataRepository repository = repositories.get(clusterName);
        repository.lock();
        Version version = null;
        try {
            Store store = repository.getStore(storeName);
            if(store == null){
                handleStoreDoseNotExist(clusterName, storeName);
            }
            if(store.containsVersion(versionNumber)){
                handleVersionAlreadyExists(storeName, versionNumber);
            }
            version = new Version(storeName,versionNumber);
            store.addVersion(version);
            repository.updateStore(store);
            logger.info("Add version:"+version.getNumber()+" for store:" + storeName);
        } finally {
            repository.unLock();
        }

        addKafkaTopic(clusterName, version.kafkaTopicName(), numberOfPartition, replicaFactor,
            configs.get(clusterName).getKafkaReplicaFactor());
        //Start offline push job for this new version.
        startOfflinePush(clusterName, version.kafkaTopicName(), numberOfPartition, replicaFactor);
        return version;
    }

    @Override
    public synchronized Version incrementVersion(String clusterName, String storeName, int numberOfPartition,
        int replicaFactor) {
        checkControllerMastership(clusterName);
        HelixCachedMetadataRepository repository = repositories.get(clusterName);

        repository.lock();
        Version version = null;
        try {
            Store store = repository.getStore(storeName);
            if(store == null){
                handleStoreDoseNotExist(clusterName, storeName);
            }
            version = store.increaseVersion();
            repository.updateStore(store);
            logger.info("Add version:"+version.getNumber()+" for store:" + storeName);
        } finally {
            repository.unLock();
        }

        addKafkaTopic(clusterName, version.kafkaTopicName(), numberOfPartition, replicaFactor,
            configs.get(clusterName).getKafkaReplicaFactor());
        //Start offline push job for this new version.
        startOfflinePush(clusterName, version.kafkaTopicName(), numberOfPartition, replicaFactor);
        return version;
    }

    @Override
    public synchronized void setCurrentVersion(String clusterName, String storeName, int versionNumber){
        checkControllerMastership(clusterName);
        HelixCachedMetadataRepository repository = repositories.get(clusterName);
        repository.lock();
        try {
            Store store = repository.getStore(storeName);
            store.setCurrentVersion(versionNumber);
            repository.updateStore(store);
            logger.info("Set version:" + versionNumber +" for store:" + storeName);
        } finally {
            repository.unLock();
        }
    }

    private void addKafkaTopic(String clusterName, String kafkaTopic, int numberOfPartition,
        int replicaFactor, int kafkaReplicaFactor) {
        checkControllerMastership(clusterName);
        topicCreator.createTopic(kafkaTopic, numberOfPartition, kafkaReplicaFactor);

        if (!admin.getResourcesInCluster(clusterName).contains(kafkaTopic)) {
            admin.addResource(clusterName, kafkaTopic, numberOfPartition,
                VeniceStateModel.PARTITION_ONLINE_OFFLINE_STATE_MODEL, IdealState.RebalanceMode.FULL_AUTO.toString());
            admin.rebalance(clusterName, kafkaTopic, replicaFactor);
            logger.info("Added " + kafkaTopic + " as a resource to cluster: " + clusterName);
        } else {
            handleResourceAlreadyExists(kafkaTopic);
        }
    }

    @Override
    public void startOfflinePush(String clusterName, String kafkaTopic, int numberOfPartition, int replicaFactor) {
        checkControllerMastership(clusterName);
        VeniceJobManager jobManager = jobManagers.get(clusterName);
        jobManager.startOfflineJob(kafkaTopic, numberOfPartition, replicaFactor);
    }

    @Override
    public synchronized void stop(String clusterName) {
        checkControllerMastership(clusterName);
        HelixManager helixManager = helixManagers.get(clusterName);
        helixManager.disconnect();
        //Remove from map at last. Otherwise an NullPointerException will be thrown when disconnect manager(onControolerChange will be invoked when disconnecting).
        helixManagers.remove(clusterName);
        configs.remove(clusterName);
        clearRepository(clusterName);
    }

    private synchronized void clearRepository(String clusterName){
        HelixCachedMetadataRepository repository = repositories.remove(clusterName);
        if(repository!=null){
            repository.clear();
        }
        VeniceJobManager jobManager =  jobManagers.remove(clusterName);
        HelixControlMessageChannel channel= channels.remove(clusterName);
        if(channel!=null) {
            channel.unRegisterHandler(StatusUpdateMessage.class, jobManager);
        }
        if(jobManager!=null) {
            HelixJobRepository jobRepository = (HelixJobRepository)jobManager.getJobRepository();
            ((HelixRoutingDataRepository) jobRepository.getRoutingDataRepository()).clear();
            jobRepository.clear();
        }
    }

    @Override
    public ExecutionStatus getOffLineJobStatus(String clusterName, String kafkaTopic) {
        checkControllerMastership(clusterName);
        VeniceJobManager jobManager = jobManagers.get(clusterName);
        return jobManager.getOfflineJobStatus(kafkaTopic);
    }

    private void createClusterIfRequired(String clusterName) {
        if(admin.getClusters().contains(clusterName)) {
            logger.info("Cluster  " + clusterName + " already exists. ");
            return;
        }

        boolean isClusterCreated = admin.addCluster(clusterName, false);
        if(isClusterCreated == false) {
            logger.info("Cluster  " + clusterName + " Creation returned false. ");
            return;
        }

        HelixConfigScope configScope = new HelixConfigScopeBuilder(HelixConfigScope.ConfigScopeProperty.CLUSTER).
                forCluster(clusterName).build();
        Map<String, String> helixClusterProperties = new HashMap<String, String>();
        helixClusterProperties.put(ZKHelixManager.ALLOW_PARTICIPANT_AUTO_JOIN, String.valueOf(true));
        admin.setConfig(configScope, helixClusterProperties);
        logger.info("Cluster  " + clusterName + "  Completed, auto join to true. ");

        admin.addStateModelDef(clusterName, VeniceStateModel.PARTITION_ONLINE_OFFLINE_STATE_MODEL,
                VeniceStateModel.getDefinition());
    }

    private void handleStoreAlreadyExists(String clusterName, String storeName) {
        String errorMessage = "Store:" + storeName + " already exists. Can not add it to cluster:" + clusterName;
        logger.info(errorMessage);
        throw new VeniceException(errorMessage);
    }

    private void handleStoreDoseNotExist(String clusterName, String storeName) {
        String errorMessage = "Store:" + storeName + " dose not exist in cluster:" + clusterName;
        logger.info(errorMessage);
        throw new VeniceException(errorMessage);
    }

    private void handleResourceAlreadyExists(String resourceName) {
        String errorMessage = "Resource:" + resourceName + " already exists, Can not add it to Helix.";
        logger.info(errorMessage);
        throw new VeniceException(errorMessage);
    }

    private void handleVersionAlreadyExists(String storeName, int version) {
        String errorMessage =
            "Version" + version + " already exists in Store:" + storeName + ". Can not add it to store.";
        logger.info(errorMessage);
        throw new VeniceException(errorMessage);
    }

    private void handleClusterNotInitialized(String clusterName) {
        String errorMessage = "Cluster " + clusterName + " is not initialized.";
        logger.info(errorMessage);
        throw new VeniceException(errorMessage);
    }

    @Override
    public String getKafkaBootstrapServers() {
        return this.kafkaBootstrapServers;
    }

    private void checkControllerMastership(String clusterName) {
        HelixManager helixManager = helixManagers.get(clusterName);
        if (helixManager == null) {
            handleClusterNotInitialized(clusterName);
        } else if (!helixManager.isLeader()) {
            throw new VeniceException("Current controller is not the master. Can not handle the admin request.");
        }
    }

    @Override
    public synchronized void onControllerChange(NotificationContext changeContext) {
        String clusterName = changeContext.getManager().getClusterName();
        HelixManager helixManager = helixManagers.get(clusterName);
        synchronized (helixManager) {
            if (helixManager == null) {
                handleClusterNotInitialized(clusterName);
            }

            if (!helixManager.isLeader() || changeContext.getType().equals(NotificationContext.Type.FINALIZE)) {
                logger.info("Controller "+helixManager.getInstanceName()+" is not the master, clear repository resource.");
                clearRepository(clusterName);
                return;
            }

            if(repositories.containsKey(clusterName)){
                //Repositories had already initialized before.
                return;
            }
            logger.info("Becoming the master controller of cluster:"+clusterName+", controller name:"+controllerName);
            //Becoming Leader
            HelixAdapterSerializer adapter = new HelixAdapterSerializer();
            HelixCachedMetadataRepository repository = new HelixCachedMetadataRepository(zkClient, adapter, clusterName);
            repository.start();

            repositories.put(clusterName, repository);
            HelixRoutingDataRepository routingDataRepository = new HelixRoutingDataRepository(helixManager);
            routingDataRepository.start();
            HelixJobRepository jobRepository = new HelixJobRepository(zkClient, adapter, clusterName, routingDataRepository);
            jobRepository.start();
            VeniceJobManager jobManager = new VeniceJobManager(helixManager.getSessionId().hashCode(), jobRepository, repository);
            HelixControlMessageChannel controllerChannel = new HelixControlMessageChannel(helixManager);
            channels.put(clusterName, controllerChannel);
            controllerChannel.registerHandler(StatusUpdateMessage.class, jobManager);
            jobManagers.put(clusterName, jobManager);
            logger.info("Repositories are initialized, could start serving admin request.");
        }
    }

    protected VeniceJobManager getJobManager(String cluster){
        VeniceJobManager jobManager = jobManagers.get(cluster);
        if(jobManager == null){
            handleClusterNotInitialized(cluster);
        }
        return jobManager;
    }
}
