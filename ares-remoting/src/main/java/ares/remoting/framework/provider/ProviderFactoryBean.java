package ares.remoting.framework.provider;

import ares.remoting.framework.helper.IPHelper;
import ares.remoting.framework.model.ProviderService;
import ares.remoting.framework.zookeeper.RegisterCenter;
import com.google.common.collect.Lists;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;

import java.lang.reflect.Method;
import java.util.List;

/**
 * 服务Bean发布入口
 * 实现FactoryBean，作为bean注册到Spring中
 * @author pdc
 */

public class ProviderFactoryBean implements FactoryBean, InitializingBean {
    /**
     * 服务接口：
     * 注册在服务注册中心，服务调用端获取后换成本地缓存，用于发起服务调用
     */
    private Class<?> serviceItf;
    /**
     * 服务实现
     */
    private Object serviceObject;
    /**
     * 服务端口：
     * 对外发布服务，作为Netty服务端端口
     */
    private String serverPort;
    /**
     * 服务超时时间:
     * 用于控制服务端运行超时时间
     */
    private long timeout;
    /**
     * 服务代理对象，暂时用不上
     */
    private Object serviceProxyObject;
    /**
     * 服务提供者唯一标识：
     * 唯一标识服务所在应用
     * 作为Zookeeper服务注册路径中的子路径，用于该应用所有服务的一个命名空间
     */
    private String appKey;
    /**
     * 服务分组组名：
     * 用于分组灰度发布。
     * 比如服务A通过配置不同的分组组名，
     * 可以使得调用端发起的调用只路由到与其配置的分组组名相同的服务提供者机器组上
     */
    private String groupName = "default";
    /**
     * 服务提供者权重,默认为1 ,范围为[1-100]：
     * 配置该机器对外发布的服务在集群中的权重，用于软负载算法实现
     */
    private int weight = 1;
    /**
     * 服务端线程数,默认10个线程
     * 限制服务端该服务运行线程数，用于实现资源的隔离与服务端的限流
     */
    private int workerThreads = 10;

    /**
     * context.getbean真正获得的对象
     * @return
     * @throws Exception
     */
    @Override
    public Object getObject() {
        return serviceProxyObject;
    }

    /**
     * SpringBean初始化时自动执行一次
     * 用于启动Netty发布服务、将服务信息写入zookeeper
     * @throws Exception
     */
    @Override
    public void afterPropertiesSet(){
        //启动Netty服务端，里面加了锁和使用单例，保证服务端只会启动一次
        NettyServer.singleton().start(Integer.parseInt(serverPort));
        //完成服务端信息的注册
        //buildProviderServiceInfos()：以服务方法为粒度(NettyServerInvokeHandler#channelRead0中才能得到消费端调用的方法对应的提供者)
        // 注册到zk,元数据注册中心
        RegisterCenter.singleton().registerProvider(buildProviderServiceInfos());
    }

    private List<ProviderService> buildProviderServiceInfos() {
        List<ProviderService> providerList = Lists.newArrayList();
        Method[] methods = serviceObject.getClass().getDeclaredMethods();
        for (Method method : methods) {
            ProviderService providerService = new ProviderService();
            providerService.setServiceItf(serviceItf);
            providerService.setServiceObject(serviceObject);
            providerService.setServerIp(IPHelper.localIp());//ProviderService比ProviderFactoryBean多出来的
            providerService.setServerPort(Integer.parseInt(serverPort));
            providerService.setTimeout(timeout);
            providerService.setServiceMethod(method);//ProviderService比ProviderFactoryBean多出来的
            providerService.setWeight(weight);
            providerService.setWorkerThreads(workerThreads);
            providerService.setAppKey(appKey);
            providerService.setGroupName(groupName);
            providerList.add(providerService);
        }
        return providerList;
    }

    @Override
    public Class<?> getObjectType() {
        return serviceItf;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    public long getTimeout() {
        return timeout;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }
}
