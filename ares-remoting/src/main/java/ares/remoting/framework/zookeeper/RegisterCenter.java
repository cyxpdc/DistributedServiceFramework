package ares.remoting.framework.zookeeper;


import ares.remoting.framework.helper.IPHelper;
import ares.remoting.framework.helper.PropertyConfigeHelper;
import ares.remoting.framework.model.InvokerService;
import ares.remoting.framework.model.ProviderService;
import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.I0Itec.zkclient.ZkClient;
import org.I0Itec.zkclient.serialize.SerializableSerializer;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;
import java.util.Map;

/**
 * 注册中心实现类
 * @author pdc
 */
public class RegisterCenter implements IRegisterCenter4Invoker, IRegisterCenter4Provider, IRegisterCenter4Governance {

    private static RegisterCenter registerCenter = new RegisterCenter();

    /**
     * 本地服务提供者列表缓存,Key:服务提供者接口  value:服务提供者服务方法列表
     * 由我们到xml中写bean，注册到ProviderFactoryBean中，封装给providerServiceMap，然后写到Zookeeper
     * Netty服务端处理业务逻辑时会根据providerServiceMap来发起服务调用，返回给客户端
     * 即架构图的服务注册
     */
    private static final Map<String, List<ProviderService>> providerServiceMap = Maps.newConcurrentMap();
    /**
     * 服务端ZK服务元信息,选择服务(第一次直接从ZK拉取,后续由ZK的监听机制主动更新)
     * 为服务调用方本地缓存，从zk拉取后，交给客户端使用
     * 即架构图的服务订阅
     */
    private static final Map<String, List<ProviderService>> serviceMetaDataMap4Consume = Maps.newConcurrentMap();
    /**
     * 主机地址列表
     */
    private static String ZK_SERVICE = PropertyConfigeHelper.getZkService();

    private static int ZK_SESSION_TIME_OUT = PropertyConfigeHelper.getZkConnectionTimeout();

    private static int ZK_CONNECTION_TIME_OUT = PropertyConfigeHelper.getZkConnectionTimeout();

    private static String ROOT_PATH = "/config_register";

    public static String PROVIDER_TYPE = "provider";

    public static String INVOKER_TYPE = "consumer";

    private static volatile ZkClient zkClient = null;

    public static RegisterCenter singleton() {
        return registerCenter;
    }


    @Override
    public Map<String, List<ProviderService>> getProviderServiceMap() {
        return providerServiceMap;
    }

    /**
     * 消费端获取服务提供者信息
     * @return
     */
    @Override
    public Map<String, List<ProviderService>> getServiceMetaDataMap4Consume() {
        return serviceMetaDataMap4Consume;
    }

    /**
     * 消费端注册消费者信息
     * @param invoker
     */
    @Override
    public void registerInvoker(InvokerService invoker) {
        if (invoker == null) return;
        //连接zk,注册服务
        synchronized (RegisterCenter.class) {
            if (zkClient == null)
                zkClient = new ZkClient(ZK_SERVICE, ZK_SESSION_TIME_OUT, ZK_CONNECTION_TIME_OUT, new SerializableSerializer());
            //创建 ZK命名空间/当前部署应用APP命名空间/
            boolean exist = zkClient.exists(ROOT_PATH);
            if (!exist) {
                zkClient.createPersistent(ROOT_PATH, true);
            }
            //创建服务消费者节点
            String remoteAppKey = invoker.getRemoteAppKey();
            String groupName = invoker.getGroupName();
            String serviceNode = invoker.getServiceItf().getName();
            String servicePath = ROOT_PATH + "/" + remoteAppKey + "/" + groupName + "/" + serviceNode + "/" + INVOKER_TYPE;
            exist = zkClient.exists(servicePath);
            if (!exist) {
                zkClient.createPersistent(servicePath, true);
            }
            //创建当前服务器节点
            String localIp = IPHelper.localIp();
            String currentServiceIpNode = servicePath + "/" + localIp;
            exist = zkClient.exists(currentServiceIpNode);
            if (!exist) {
                //注意,这里创建的是临时节点
                zkClient.createEphemeral(currentServiceIpNode);
            }
        }
    }

    /**
     * 服务提供者信息注册：
     * 将bean中配置好的serviceMetaData封装到providerServiceMap
     * 将providerServiceMap作为Zookeeper子节点写入Zookeeper
     * @param serviceMetaData 服务提供者列表
     */
    @Override
    public void registerProvider(final List<ProviderService> serviceMetaData) {
        if (CollectionUtils.isEmpty(serviceMetaData)) return;
        //连接zk,注册服务
        synchronized (RegisterCenter.class) {
            for (ProviderService provider : serviceMetaData) {
                String serviceItfKey = provider.getServiceInterface().getName();

                List<ProviderService> providers = providerServiceMap.get(serviceItfKey);
                if (providers == null) {
                    providers = Lists.newArrayList();
                }
                providers.add(provider);
                providerServiceMap.put(serviceItfKey, providers);
            }

            if (zkClient == null)
                zkClient = new ZkClient(ZK_SERVICE, ZK_SESSION_TIME_OUT, ZK_CONNECTION_TIME_OUT, new SerializableSerializer());

            //创建 ZK命名空间/当前部署应用APP命名空间/ 文件夹
            String APP_KEY = serviceMetaData.get(0).getAppKey();
            String ZK_PATH = ROOT_PATH + "/" + APP_KEY;
            boolean exist = zkClient.exists(ZK_PATH);
            if (!exist) {
                zkClient.createPersistent(ZK_PATH, true);
            }

            for (Map.Entry<String, List<ProviderService>> entry : providerServiceMap.entrySet()) {
                //服务分组
                String groupName = entry.getValue().get(0).getGroupName();
                //创建服务提供者
                String serviceNode = entry.getKey();
                String servicePath = ZK_PATH + "/" + groupName + "/" + serviceNode + "/" + PROVIDER_TYPE;
                exist = zkClient.exists(servicePath);
                if (!exist) {
                    zkClient.createPersistent(servicePath, true);
                }
                //创建当前服务器节点
                int serverPort = entry.getValue().get(0).getServerPort();//服务端口
                int weight = entry.getValue().get(0).getWeight();//服务权重
                int workerThreads = entry.getValue().get(0).getWorkerThreads();//服务工作线程
                String localIp = IPHelper.localIp();
                String currentServiceIpNode = servicePath + "/" + localIp + "|" + serverPort + "|" + weight + "|" + workerThreads + "|" + groupName;
                exist = zkClient.exists(currentServiceIpNode);
                if (!exist) {
                    //注意,这里创建的是临时节点
                    zkClient.createEphemeral(currentServiceIpNode);
                }

                //监听注册服务的变化,同时更新数据到本地缓存
                zkClient.subscribeChildChanges(servicePath, (parentPath, currentChilds) -> {
                    if (currentChilds == null) {
                        currentChilds = Lists.newArrayList();
                    }
                    //存活的服务IP列表
                    List<String> activityServiceIpList = Lists.newArrayList(Lists.transform(currentChilds,
                            input -> StringUtils.split(input, "|")[0]));
                    refreshActivityService(activityServiceIpList);
                });
            }
        }
    }

    /**
     * 利用ZK自动刷新当前存活的服务提供者列表数据providerServiceMap
     * @param serviceIpList
     */
    private void refreshActivityService(List<String> serviceIpList) {
        if (serviceIpList == null) {
            serviceIpList = Lists.newArrayList();
        }

        Map<String, List<ProviderService>> currentServiceMetaDataMap = Maps.newHashMap();
        for (Map.Entry<String, List<ProviderService>> entry : providerServiceMap.entrySet()) {
            String key = entry.getKey();
            List<ProviderService> providerServices = entry.getValue();

            List<ProviderService> serviceMetaDataModelList = currentServiceMetaDataMap.get(key);
            if (serviceMetaDataModelList == null) {
                serviceMetaDataModelList = Lists.newArrayList();
            }

            for (ProviderService serviceMetaData : providerServices) {
                if (serviceIpList.contains(serviceMetaData.getServerIp())) {
                    serviceMetaDataModelList.add(serviceMetaData);
                }
            }
            currentServiceMetaDataMap.put(key, serviceMetaDataModelList);
        }

        providerServiceMap.clear();
        System.out.println("currentServiceMetaDataMap,"+ JSON.toJSONString(currentServiceMetaDataMap));
        providerServiceMap.putAll(currentServiceMetaDataMap);
    }

    /**
     * 初始化服务提供者信息客户端本地缓存serviceMetaDataMap4Consume
     * @param remoteAppKey
     * @param groupName
     */
    @Override
    public void initProviderMap(String remoteAppKey, String groupName) {
        if (MapUtils.isEmpty(serviceMetaDataMap4Consume)) {
            serviceMetaDataMap4Consume.putAll(fetchOrUpdateServiceMetaData(remoteAppKey, groupName));
        }
    }

    private Map<String, List<ProviderService>> fetchOrUpdateServiceMetaData(String remoteAppKey, String groupName) {
        final Map<String, List<ProviderService>> providerServiceMap = Maps.newConcurrentMap();
        //连接zk
        synchronized (RegisterCenter.class) {
            if (zkClient == null)
                zkClient = new ZkClient(ZK_SERVICE, ZK_SESSION_TIME_OUT, ZK_CONNECTION_TIME_OUT, new SerializableSerializer());
        }
        //从ZK获取对应的服务提供者列表，封装给新的providerServiceMap
        String providePath = ROOT_PATH + "/" + remoteAppKey + "/" + groupName;
        List<String> providerServices = zkClient.getChildren(providePath);
        for (String serviceName : providerServices) {
            String servicePath = providePath + "/" + serviceName + "/" + PROVIDER_TYPE;
            List<String> ipPathList = zkClient.getChildren(servicePath);
            for (String ipPath : ipPathList) {
                String[] providerInfo = StringUtils.split(ipPath, "|");
                String serverIp = providerInfo[0];
                String serverPort = providerInfo[1];
                int weight = Integer.parseInt(providerInfo[2]);
                int workerThreads = Integer.parseInt(providerInfo[3]);
                String group = providerInfo[4];

                List<ProviderService> providerServiceList = providerServiceMap.get(serviceName);
                if (providerServiceList == null) {
                    providerServiceList = Lists.newArrayList();
                }
                ProviderService providerService = new ProviderService();
                try {
                    providerService.setServiceInterface(ClassUtils.getClass(serviceName));
                } catch (ClassNotFoundException e) {
                    throw new RuntimeException(e);
                }
                providerService.setServerIp(serverIp);
                providerService.setServerPort(Integer.parseInt(serverPort));
                providerService.setWeight(weight);
                providerService.setWorkerThreads(workerThreads);
                providerService.setGroupName(group);
                providerServiceList.add(providerService);

                providerServiceMap.put(serviceName, providerServiceList);
            }
            //监听注册服务的变化,以ip为划分同时更新数据到本地缓存，即当前存活的ip列表
            //当服务端下线，该临时节点会被删除，同时推送给这里，即服务消费端
            zkClient.subscribeChildChanges(servicePath, (parentPath, currentChilds) -> {
                if (currentChilds == null) {
                    currentChilds = Lists.newArrayList();
                }
                currentChilds = Lists.newArrayList(Lists.transform(currentChilds,
                        input -> StringUtils.split(input, "|")[0]));
                refreshServiceMetaDataMap(currentChilds);
            });
        }
        return providerServiceMap;
    }

    private void refreshServiceMetaDataMap(List<String> curServiceIpList) {
        if (curServiceIpList == null) {
            curServiceIpList = Lists.newArrayList();
        }

        Map<String, List<ProviderService>> newServiceMetaDataMap = Maps.newHashMap();
        for (Map.Entry<String, List<ProviderService>> entry : serviceMetaDataMap4Consume.entrySet()) {
            String serviceItfKey = entry.getKey();
            List<ProviderService> oldServiceList = entry.getValue();

            List<ProviderService> newProviderServiceList = newServiceMetaDataMap.get(serviceItfKey);
            if (newProviderServiceList == null) {
                newProviderServiceList = Lists.newArrayList();
            }
            //用来添加新服务用
            ProviderService flag = null;
            //保存还拥有的服务，如果有下线了的服务器的话
            flag = saveOldService(curServiceIpList, oldServiceList, newProviderServiceList, flag);
            //保存新上线的服务，如果有的话
            saveNewService(curServiceIpList, newProviderServiceList, flag);
            newServiceMetaDataMap.put(serviceItfKey, newProviderServiceList);
        }
        serviceMetaDataMap4Consume.clear();
        serviceMetaDataMap4Consume.putAll(newServiceMetaDataMap);
    }

    private ProviderService saveOldService(List<String> curServiceIpList, List<ProviderService> oldServiceList, List<ProviderService> newProviderServiceList, ProviderService flag) {
        for (ProviderService oldServiceMetaData : oldServiceList) {
            if (curServiceIpList.contains(oldServiceMetaData.getServerIp())) {
                newProviderServiceList.add(oldServiceMetaData);
                flag = oldServiceMetaData;
                curServiceIpList.remove(oldServiceMetaData.getServerIp());
            }
        }
        return flag;
    }

    private void saveNewService(List<String> curServiceIpList, List<ProviderService> newProviderServiceList, ProviderService flag) {
        for(String  curServiceIp: curServiceIpList){
            ProviderService newService = flag.copy();
            newService.setServerIp(curServiceIp);
            newProviderServiceList.add(newService);
        }
    }

    @Override
    public Pair<List<ProviderService>, List<InvokerService>> queryProvidersAndInvokers(String serviceName, String appKey) {
        //服务消费者列表
        List<InvokerService> invokerServices = Lists.newArrayList();
        //服务提供者列表
        List<ProviderService> providerServices = Lists.newArrayList();
        //连接zk
        if (zkClient == null) {
            synchronized (RegisterCenter.class) {
                if (zkClient == null) {
                    zkClient = new ZkClient(ZK_SERVICE, ZK_SESSION_TIME_OUT, ZK_CONNECTION_TIME_OUT, new SerializableSerializer());
                }
            }
        }

        String parentPath = ROOT_PATH + "/" + appKey;
        //获取 ROOT_PATH + APP_KEY注册中心子目录列表
        List<String> groupServiceList = zkClient.getChildren(parentPath);
        if (CollectionUtils.isEmpty(groupServiceList)) {
            return Pair.of(providerServices, invokerServices);
        }

        for (String group : groupServiceList) {
            String groupPath = parentPath + "/" + group;
            //获取ROOT_PATH + APP_KEY + group 注册中心子目录列表
            List<String> serviceList = zkClient.getChildren(groupPath);
            if (CollectionUtils.isEmpty(serviceList)) {
                continue;
            }
            for (String service : serviceList) {
                //获取ROOT_PATH + APP_KEY + group +service 注册中心子目录列表
                String servicePath = groupPath + "/" + service;
                List<String> serviceTypes = zkClient.getChildren(servicePath);
                if (CollectionUtils.isEmpty(serviceTypes)) {
                    continue;
                }
                for (String serviceType : serviceTypes) {
                    if (StringUtils.equals(serviceType, PROVIDER_TYPE)) {
                        //获取ROOT_PATH + APP_KEY + group +service+serviceType 注册中心子目录列表
                        String providerPath = servicePath + "/" + serviceType;
                        List<String> providers = zkClient.getChildren(providerPath);
                        if (CollectionUtils.isEmpty(providers)) {
                            continue;
                        }
                        //获取服务提供者信息
                        for (String provider : providers) {
                            String[] providerNodeArr = StringUtils.split(provider, "|");
                            ProviderService providerService = new ProviderService();
                            providerService.setAppKey(appKey);
                            providerService.setGroupName(group);
                            providerService.setServerIp(providerNodeArr[0]);
                            providerService.setServerPort(Integer.parseInt(providerNodeArr[1]));
                            providerService.setWeight(Integer.parseInt(providerNodeArr[2]));
                            providerService.setWorkerThreads(Integer.parseInt(providerNodeArr[3]));
                            providerServices.add(providerService);
                        }

                    } else if (StringUtils.equals(serviceType, INVOKER_TYPE)) {
                        //获取ROOT_PATH + APP_KEY + group +service+serviceType 注册中心子目录列表
                        String invokerPath = servicePath + "/" + serviceType;
                        List<String> invokers = zkClient.getChildren(invokerPath);
                        if (CollectionUtils.isEmpty(invokers)) {
                            continue;
                        }
                        //获取服务消费者信息
                        for (String invoker : invokers) {
                            InvokerService invokerService = new InvokerService();
                            invokerService.setRemoteAppKey(appKey);
                            invokerService.setGroupName(group);
                            invokerService.setInvokerIp(invoker);
                            invokerServices.add(invokerService);
                        }
                    }
                }
            }

        }
        return Pair.of(providerServices, invokerServices);
    }
}
