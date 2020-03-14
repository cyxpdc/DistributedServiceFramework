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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 注册中心实现类
 * 每个系统都可能是服务端，也可能是消费端，因此两个接口都要实现
 * 每个系统各自上传自己的服务提供者列表providerServiceMap和客户端本地缓存serviceMetaDataMap4Consume到zookeeper
 * @author pdc
 */
public class RegisterCenter implements IRegisterCenter4Invoker, IRegisterCenter4Provider, IRegisterCenter4Governance {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegisterCenter.class);

    private static final RegisterCenter registerCenter = new RegisterCenter();

    /**
     * 服务提供者列表,Key:服务提供者接口名  value:服务提供者服务方法列表
     * 使用者到xml中写bean，注册到ProviderFactoryBean中，封装给providerServiceMap，然后写到Zookeeper
     * Netty服务端处理业务逻辑时会根据providerServiceMap来发起服务调用，返回给客户端
     * 即服务注册
     */
    private static final Map<String, List<ProviderService>> providerServiceMap = Maps.newConcurrentMap();

    /**x
     * 服务端ZK服务元信息,选择服务(第一次直接从ZK拉取,后续由ZK的监听机制主动更新)
     * 为服务调用方本地缓存，从zk拉取后，交给客户端使用
     * 即服务订阅
     */
    private static final Map<String, List<ProviderService>> serviceMetaDataMap4Consume = Maps.newConcurrentMap();
    private static final TimeUnit MINUTES = TimeUnit.MINUTES;

    /**
     * 实现功能：将本地路由表自动保存到本地文件中
     * 线程池用来将路由表写⼊本地⽂件
     */
    private volatile boolean changed;
    private ScheduledExecutorService ses= Executors.newSingleThreadScheduledExecutor();
    private static final String FILE_PATH = "C:/file";

    private static String ZK_SERVICE = PropertyConfigeHelper.getZkService();

    private static int ZK_SESSION_TIME_OUT = PropertyConfigeHelper.getZkSessionTimeout();

    private static int ZK_CONNECTION_TIME_OUT = PropertyConfigeHelper.getZkConnectionTimeout();

    private static String ROOT_PATH = "/config_register";

    public static String PROVIDER_TYPE = "provider";

    public static String INVOKER_TYPE = "consumer";

    private static volatile ZkClient zkClient = null;//单例

    public static RegisterCenter singleton() {
        return registerCenter;
    }

    /**
     * 服务端获取服务提供者信息
     * @return providerServiceMap
     */
    @Override
    public Map<String, List<ProviderService>> getProviderServiceMap() {
        return providerServiceMap;
    }

    /**
     * 消费端获取服务提供者信息
     * @return serviceMetaDataMap4Consume
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
        if (invoker == null) {
            return;
        }
        //连接zk,注册服务
        synchronized (RegisterCenter.class) {
            if (zkClient == null) {
                zkClient = new ZkClient(ZK_SERVICE, ZK_SESSION_TIME_OUT, ZK_CONNECTION_TIME_OUT, new SerializableSerializer());
            }
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
                zkClient.createPersistent(servicePath, true);//持久
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
     * 将bean中配置好的serviceMetaData（serviceMetaData以方法为粒度，在NettyServerInvokeHandler中才能得到指定方法）封装到providerServiceMap
     * 将providerServiceMap作为Zookeeper子节点写入Zookeeper
     * @param serviceMetaData 服务提供者信息列表
     */
    @Override
    public void registerProvider(final List<ProviderService> serviceMetaData) {
        if (CollectionUtils.isEmpty(serviceMetaData)) {
            return;
        }
        //连接zk,注册服务，使用synchronized防止重复注册
        synchronized (RegisterCenter.class) {
            for (ProviderService provider : serviceMetaData) {
                String serviceItfKey = provider.getServiceItf().getName();

                List<ProviderService> providers = providerServiceMap.get(serviceItfKey);
                if (providers == null) {
                    providers = Lists.newCopyOnWriteArrayList();
                }
                providers.add(provider);
                providerServiceMap.put(serviceItfKey, providers);//一个接口对应多个方法不同的提供者
            }

            if (zkClient == null) {
                zkClient = new ZkClient(ZK_SERVICE, ZK_SESSION_TIME_OUT, ZK_CONNECTION_TIME_OUT, new SerializableSerializer());
            }
            //创建 ZK命名空间/当前部署应用APP命名空间/ 文件夹
            String APP_KEY = serviceMetaData.get(0).getAppKey();//随便一个提供者的app是一样的
            String ZK_PATH = ROOT_PATH + "/" + APP_KEY;
            if (!zkClient.exists(ZK_PATH)) {
                zkClient.createPersistent(ZK_PATH, true);
            }

            for (Map.Entry<String, List<ProviderService>> entry : providerServiceMap.entrySet()) {
                //服务分组
                String groupName = entry.getValue().get(0).getGroupName();
                //创建服务提供者
                String serviceNode = entry.getKey();//接口名
                String servicePath = ZK_PATH + "/" + groupName + "/" + serviceNode + "/" + PROVIDER_TYPE;
                //一个方法对应一个providerService，但是节点则是一个ip对应一个节点，一个实现可以用一个ip表示
                //所以一个节点可以意味着多个方法，因此要用exist，最终就是一个接口对应一个节点
                if (!zkClient.exists(servicePath)) {
                    zkClient.createPersistent(servicePath, true);
                }
                //创建当前服务器节点
                int serverPort = entry.getValue().get(0).getServerPort();//服务端口
                int weight = entry.getValue().get(0).getWeight();//服务权重
                int workerThreads = entry.getValue().get(0).getWorkerThreads();//服务工作线程
                String localIp = IPHelper.localIp();
                String currentServiceIpNode = servicePath + "/" + localIp + "|" + serverPort + "|" + weight + "|" + workerThreads + "|" + groupName;
                if (!zkClient.exists(currentServiceIpNode)) {
                    //注意,这里创建的是临时节点
                    zkClient.createEphemeral(currentServiceIpNode);//这里也是一个接口对应一个节点
                }
                changed = true;
            }
        }
    }

    /**
     * 初始化服务提供者信息客户端本地缓存serviceMetaDataMap4Consume
     * 这里则需要上下线，需要消费端需要获取所有服务信息
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
            if (zkClient == null) {
                zkClient = new ZkClient(ZK_SERVICE, ZK_SESSION_TIME_OUT, ZK_CONNECTION_TIME_OUT, new SerializableSerializer());
            }
        }
        //从ZK获取对应的服务提供者列表，封装给新的providerServiceMap
        String providePath = ROOT_PATH + "/" + remoteAppKey + "/" + groupName;
        List<String> providerServices = zkClient.getChildren(providePath);
        for (String serviceName : providerServices) {
            String servicePath = providePath + "/" + serviceName + "/" + PROVIDER_TYPE;
            List<String> ipPathList = zkClient.getChildren(servicePath);
            for (String ipPath : ipPathList) {//对每个ip创建一个ProviderService
                String[] providerInfo = StringUtils.split(ipPath, "|");
                String serverIp = providerInfo[0];
                String serverPort = providerInfo[1];
                int weight = Integer.parseInt(providerInfo[2]);
                int workerThreads = Integer.parseInt(providerInfo[3]);
                String group = providerInfo[4];

                List<ProviderService> providerServiceList = providerServiceMap.get(serviceName);
                if (providerServiceList == null) {
                    providerServiceList = Lists.newCopyOnWriteArrayList();
                }
                ProviderService providerService = new ProviderService();
                try {
                    providerService.setServiceItf(ClassUtils.getClass(serviceName));
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
        changed = true;
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
                newProviderServiceList = Lists.newCopyOnWriteArrayList();
            }
            //用来添加新服务
            ProviderService flag = null;
            //保存还拥有的服务，如果有下线了的服务器的话
            saveOldService(curServiceIpList, oldServiceList, newProviderServiceList, flag);
            //保存新上线的服务，如果有的话
            saveNewService(curServiceIpList, newProviderServiceList, flag);
            newServiceMetaDataMap.put(serviceItfKey, newProviderServiceList);
        }
        serviceMetaDataMap4Consume.clear();
        serviceMetaDataMap4Consume.putAll(newServiceMetaDataMap);
        changed = true;
    }

    private void saveOldService(List<String> curServiceIpList, List<ProviderService> oldServiceList, List<ProviderService> newProviderServiceList, ProviderService flag) {
        for (ProviderService oldServiceMetaData : oldServiceList) {
            if (curServiceIpList.contains(oldServiceMetaData.getServerIp())) {
                newProviderServiceList.add(oldServiceMetaData);
                flag = oldServiceMetaData;
                // 原先有的服务就移除，因为消费端不需要“一个接口对应多个不同方法的提供者”
                // 只需要一个接口对应一个ip的提供者，这样saveNewService的逻辑就可以为“直接添加”
                curServiceIpList.remove(oldServiceMetaData.getServerIp());
            }
        }
    }

    private void saveNewService(List<String> curServiceIpList, List<ProviderService> newProviderServiceList, ProviderService flag) {
        for(String  curServiceIp: curServiceIpList){
            ProviderService newService = flag.copy();
            newService.setServerIp(curServiceIp);
            newProviderServiceList.add(newService);
        }
    }

    /**
     * 获取指定接口的服务提供者和消费者
     * @param serviceName 服务名
     * @param appKey 应用
     * @return 提供者和消费者
     */
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
                if(service.equals(serviceName)){
                    //获取ROOT_PATH + APP_KEY + group + service 注册中心子目录列表
                    String servicePath = groupPath + "/" + service;
                    List<String> serviceTypes = zkClient.getChildren(servicePath);
                    if (CollectionUtils.isEmpty(serviceTypes)) {
                        continue;
                    }
                    for (String serviceType : serviceTypes) {
                        if (StringUtils.equals(serviceType, PROVIDER_TYPE)) {
                            addPrioverServices(appKey, providerServices, group, servicePath, serviceType);
                        } else if (StringUtils.equals(serviceType, INVOKER_TYPE)) {
                            addInvokerServices(appKey, invokerServices, group, servicePath, serviceType);
                        }
                    }
                }
            }

        }
        return Pair.of(providerServices, invokerServices);
    }

    private void addInvokerServices(String appKey, List<InvokerService> invokerServices, String group, String servicePath, String serviceType) {
        //获取ROOT_PATH + APP_KEY + group + service + serviceType 注册中心子目录列表
        List<String> invokers = getPath(servicePath, serviceType);
        if (invokers == null) {
            return;
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

    private void addPrioverServices(String appKey, List<ProviderService> providerServices, String group, String servicePath, String serviceType) {
        //获取ROOT_PATH + APP_KEY + group + service + serviceType 注册中心子目录列表
        List<String> providers = getPath(servicePath, serviceType);
        if (providers == null) {
            return;
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
    }

    private List<String> getPath(String servicePath, String serviceType) {
        String pecifiedPath = servicePath + "/" + serviceType;
        List<String> pecifiedServices = zkClient.getChildren(pecifiedPath);
        if (CollectionUtils.isEmpty(pecifiedServices)) {
            return null;
        }
        return pecifiedServices;
    }

    /**
     * 启动定时任务
     * 将变更后的路由表写⼊本地⽂件
     *
     * ScheduledExecutorService继承于ExecutorService接口并添加了scheduleAtFixedRate和scheduleWithFixedDelay等方法。
     * 两个方法的区别是：
     * 前者是周期性的按照一定的时间进行任务的执行,如果一个任务执行超过了周期时间，则任务执行完之后会马上进行下一次任务的执行。
     * 而后者在这样的情况出现的时候会在任务执行完之后仍然间隔周期时间进行下一次任务的执行。
     * 采用scheduleWithFixedDelay()这种调度方式能保证同一时刻只有一个线程执行autoSave()方法，这样就无需加锁
     */
    public void startLocalSaver(){
        ses.scheduleWithFixedDelay(()->{
            autoSave();
        }, 1, 1, MINUTES);
    }

    private void autoSave() {
        if (!changed) {
            return;
        }
        changed = false;
        //将路由表写⼊本地⽂件
        this.save2Local();
    }

    private void save2Local() {
        //将providerServiceMap和serviceMetaDataMap4Consume按照自己想要的格式写入到文件即可
        try {
            OutputStream os = new BufferedOutputStream(new FileOutputStream(FILE_PATH));
            saveMap(providerServiceMap,os);
            os.write("start serviceMetaDataMap4Consume".getBytes());
            os.write("/r/n".getBytes());
            saveMap(serviceMetaDataMap4Consume,os);
        } catch (FileNotFoundException e) {
            LOGGER.error("打开文件失败：" + e);
        } catch (IOException e) {
            LOGGER.error("写入文件失败：" + e);
        }
    }

    /**
     * 格式为：127.0.0.1:xxx xxx xxx
     * @param map
     * @param os
     * @throws IOException
     */
    private void saveMap(Map<String,List<ProviderService>> map,OutputStream os) throws IOException {
        for(Map.Entry<String, List<ProviderService>> entry : map.entrySet()){
            List<ProviderService> providerServices = entry.getValue();
            for(ProviderService providerService : providerServices){
                os.write((entry.getKey()+ ":").getBytes());
                os.write((providerService.getServiceItf().getName() + " ").getBytes());
                os.write((String.valueOf(providerService.getWeight()) + " ").getBytes());
                os.write((String.valueOf(providerService.getServerPort()) + " ").getBytes());
                os.write((String.valueOf(providerService.getWorkerThreads()) + " ").getBytes());
                os.write((providerService.getAppKey() + " ").getBytes());
                os.write((providerService.getGroupName() + " ").getBytes());
                os.write((providerService.getServerIp() + " ").getBytes());
                os.write((providerService.getServiceObject().getClass().getName() + " ").getBytes());
                os.write((providerService.getServiceMethod().getName() + " ").getBytes());
                os.write((String.valueOf(providerService.getTimeout())).getBytes());
                os.write("/r/n".getBytes());
            }
            os.write("/r/n".getBytes());
        }
    }

    /**
     * 如果zk崩了，可以从本地文件读取到两个map中，算是一种降级
     */
    private void readLocal2Map(){
        try {
            BufferedReader bf = new BufferedReader(new FileReader(FILE_PATH));
            String str = null;
            List<ProviderService> providerServices = null;
            List<ProviderService> serviceMetaData = null;
            String key = null;
            while ((str = bf.readLine()) != null) {
                if(str == "start serviceMetaDataMap4Consume"){
                    break;
                }
                key = str = str.substring(0,str.indexOf(":"));
                providerServices = providerServiceMap.get(key);
                if(providerServices == null){
                    providerServices = Lists.newCopyOnWriteArrayList();
                }
                setProvider(providerServices,str);
            }
            providerServiceMap.put(key,providerServices);
            while((str = bf.readLine()) != null){
                key = str = str.substring(0,str.indexOf(":"));
                providerServices = serviceMetaDataMap4Consume.get(key);
                if(providerServices == null){
                    providerServices = Lists.newCopyOnWriteArrayList();
                }
                setProvider(serviceMetaData,str);
            }
            serviceMetaDataMap4Consume.put(key,serviceMetaData);
        } catch (FileNotFoundException e) {
            LOGGER.error("打开文件失败：" + e);
        } catch (IOException e) {
            LOGGER.error("读取文件失败：" + e);
        } catch (ClassNotFoundException e) {
            LOGGER.error("Class.forName出错：" + e);
        } catch (NoSuchMethodException e) {
            LOGGER.error("找不到此方法：" + e);
        } catch (IllegalAccessException | InstantiationException e) {
            LOGGER.error("newInstance 出错：" + e);
        }
    }

    private void setProvider(List<ProviderService> providerServices,String str) throws ClassNotFoundException, IllegalAccessException, InstantiationException, NoSuchMethodException {
        String[] arr = str.split(" ");
        if(arr.length == 10){
            ProviderService service = new ProviderService();
            service.setServiceItf(Class.forName(arr[0]));
            service.setWeight(Integer.parseInt(arr[1]));
            service.setServerPort(Integer.parseInt(arr[2]));
            service.setWorkerThreads(Integer.parseInt(arr[3]));
            service.setAppKey(arr[4]);
            service.setGroupName(arr[5]);
            service.setServerIp(arr[6]);
            service.setServiceObject(Class.forName(arr[7]).newInstance());
            service.setServiceMethod(service.getServiceObject().getClass().getMethod(arr[8]));
            service.setTimeout(Long.parseLong(arr[9]));
            providerServices.add(service);
        }
    }
}
