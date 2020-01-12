# 分布式服务框架相关

## 一：使用Netty实现简易版的WebService

包：com.pdc.catalina

### 1.类简介

MyNioWebServer：服务器类，阻塞等待客户端连接

MyHandler：实现ChannelInboundHandlerAdapter，处理客户端请求，channelRead方法中调用servlet的doGet()

简单地自定义了request、response和servlet

> 搭了个大致架构，可参考myTomcat：https://github.com/cyxpdc/myTomcat自行扩展

## 二：使用Netty实现im聊天系统

### 1.架构：

多个Client和一个Server，请求涉及协议、编码解码、业务逻辑(消息记录保存、分发，当前在线人数的统计等)

Client功能：发送表情、文字；登陆退出、送赞。

### 2.实现思路：

Server里有一个pipeline，pipeline里用许多Handler处理，如HttpHandler、WebSocketHandler、自定义协议的IMPHandler，根据判断来调用Handler；
使用异步处理，结果最终返回给客户端，客户端需要使用**WebSocket**，实现实时交互

### 3.类简介

MyImServer：服务端

HttpHandler：处理handler，继承SimpleChannelInboundHandler，实现了channelRead0方法，使用RandomAccessFile类来加载文件；使HttpHandler.class.getProtectionDomain().getCodeSource().getLocation()获取类的根目录classPath
此处bug：关于path的处理

### 4.自定义协议

规定头元素用[]，每个[]中的内容表示一个头元素，如[LOGIN]表示登录动作





> 流程：

IMServer启动后，每来一个请求，则新开一个线程，跑childHandler方法
childHandler方法中，有三个处理器：WebSocketHandler、HttpHandler、SocketHandler
1.WebSocketHandler将前端传来的数据交给IMServerProcessor处理，如登录、聊天、送鲜花，此handler主要用来确保系统使用WebSocket协议；
2.HTTPHandler则负责解析HTTP，填充response；
3.SocketHandler则使用普通的socket协议

接下来，用户开始操作，操作过程中，起作用的只有WebSocketHandler#channelRead0

IMEncoder和IMDecoder用在IMServerProcessor中：接收到字符串时，使用自定义的IMDecoder#decode，发送到浏览器时，使用自定义的IMEncoder#encode。
重写的decode则在

IMMessage：实体类，每一个命令代表一个IMMessage

IMServerProcessor：使用ChannelGroup代表当前用户数，封装了真正的业务，如登录、聊天、送花

登录：

```
如果不是本身，则通知用户有人加入，如果是本身，则告知自己已加入
```

如果不是本身，则通知用户有人加入，如果是本身，则告知自己已加入

聊天：

```
聊天，包括文字和表情
流程：
* 依次判断发消息的人与用户的关系
* 如果发消息的人是本身，则将名称改为自己
* 如果发消息的人不是本身，则将名称设置为发消息的人
* 最后发送到聊天面板上
```

送鲜花：

```
客户端发出送花命令,前端设置好协议，请求后端
后端获取自定义属性,判断是否能刷花，即60秒之内不允许重复刷鲜花
```

# 基于BIO的简易RPC框架

功能：支持一个服务的发布

类简介：

> ConsumerProxy：服务消费者代理类，使用动态代理创建接口的代理类
>
> ProviderReflect：服务发布类，使用线程池技术，给一个请求开一个线程
>
> HelloService：测试接口
>
> HelloServiceImpl：测试接口实现类
>
> RpcProviderMain：发布服务测试类
>
> RpcConsumerMain：服务调用测试类

用法：

> 定义好接口和实现类，然后创建实现类对象，使用ProviderReflect.provider(实现类, 8083);启动，即可发布服务
>
> 调用则使用ConsumerProxy.consume(接口.class, "ip", 端口);来获取接口代理类，调用想要的方法即可

原理：

> 内部通过Socket交互：
>
> 使用ConsumerProxy#consume时，返回一个JDK动态代理对象，调用方法时，触发invoke方法，发起socket；
>
> ProviderReflect#provider启动后一直阻塞，直到ConsumerProxy#consume发起socket；
>
> ConsumerProxy#consume将方法名和参数写入到socket，交给ProviderReflect#provider；
>
> ProviderReflect#provider获取到方法名和参数后，通过反射调用方法获取返回结果，将结果写入到socket，返回给ConsumerProxy#consume，该返回结果就是接口实现类方法的返回结果了





# 分布式服务框架开发

## 1.分布式服务框架总体结构与功能

面向SOA；可以简单地认为，RPC+服务治理(服务依赖梳理、负载均衡、服务分组灰度发布、链路监控、服务质量统计、服务自动发现、自动下线与服务注册中心等功能)，就构成了一套完整的分布式服务框架，分布式服务框架是SOA能够最终成功落地实现价值交付的技术保障

主要组成：服务消费端、服务提供端、服务数据网络传输的序列化与反序列化、服务数据的通信机制、服务注册中心、服务治理

服务的引入与发布，即服务消费端与提供端：使用Spring来实现

服务数据网络传输的序列化与反序列化：Java默认序列化、XML、JSON、Hessian、protostuff、Thrift、Avro等

服务数据的通信机制：Netty

服务注册中心：Zookeeper，用来实现服务注册、服务发现、服务自动上下线

服务治理：编码手段    待补充

## 2.分布式服务框架序列化与反序列化实现

### 实现自己的序列化工具引擎

目的：整合9种序列化解决方案，可以通过输入不同配置随意选择使用哪一种解决方案

SerializerEngine：主类，通过传入参数serializeType的方式，可以自由选择具体的序列化/反序列化方案

SerializeType：序列化类型枚举

## 3.实现与Spring的集成

使用Spring来管理远程服务的发布与引入，实现了远程服务调用编程界面与本地Bean方法调用的一致性，屏蔽了远程服务调用与本地方法调用的差异性

- 类简介

服务发布：

ProviderFactoryBean：组合了服务提供者本身的属性，然后将服务提供者按照方法的粒度拆分，注册到Zookeeper；即为服务Bean的发布入口；然后使用了NettyServer

NettyServer：Netty服务端，将服务对外发布出去，使其能够接受外部其他机器的调用请求

服务引入：

RevokerFactoryBean：组合了远程服务引入相关的的属性，引入远程服务，将服务提供者拉到本地缓存、注册服务消费者信息(服务治理用的)

- 使用示例

服务发布:

1.自定义接口HelloService和实现类HelloServiceImpl

2.xml配置：将HelloService作为远程服务发布出去

服务引入：

1.xml配置：引入HelloService。这里配置好了接口后，在RevokerFactoryBean中得到匹配此接口的服务提供者列表，再根据软负载均衡策略选取一个服务提供者代理对象，发起调用

最后由MainClient和MainServer进行测试

- 通过自定义标签简化xml配置

1.定义自定义标签的命名空间、路径和构成规则：定义remote-reference.xsd文件、remote-service.xsd文件（主要是配置RevokerFactoryBean和ProviderFactoryBean的属性）
然后由spring.schemas文件引入xsd文件，再由spring.handlers文件定义解析自定义标签工具类AresRemoteReferenceNamespaceHandler和AresRemoteServiceNamespaceHandler

2.AresRemoteReferenceNamespaceHandler中，扩展NamespaceHandlerSupport类，覆盖init方法
方法中，由RevokerFactoryBeanDefinitionParser来解析远程服务引入的自定义标签，获得xml标签配置的属性值，写入到远程服务引入的Bean中，完成构建

3.AresRemoteServiceNamespaceHandler中，由ProviderFactoryBeanDefinitionParser来执行解析服务发布自定义标签，获得xml标签配置的属性值，写入到服务发布的Bean中，完成构建

4.最后，xml进行服务的发布和引入配置即可
发布服务：

```xml
<!-- 发布远程服务的，配置的属性会注入到此bean中 -->
<bean id="helloService" class="ares.remoting.test.HelloServiceImpl"/>
<AresServer:service id="helloServiceRegister"
                    interface="ares.remoting.test.HelloService"
                    ref="helloService"
                    groupName="default"
                    weight="2"
                    appKey="ares"
                    workerThreads="100"
                    serverPort="8081"
                    timeout="600"/>
```

引入服务：

```xml
<!-- 引入远程服务，配置的属性会注入到此bean中 -->
<AresClient:reference id="remoteHelloService"
                      interface="ares.remoting.test.HelloService"
                      clusterStrategy="WeightRandom"
                      remoteAppKey="ares"
                      groupName="default"
                      timeout="3000"/>
```

最后由MainClient和MainServer进行测试

流程：

> 远程服务发布到Zookeeper，服务调用方则拉取服务提供者信息到本地，得到匹配服务接口的服务提供者列表，再根据软负载策略选取其中一个服务提供者，通过Netty发起RPC调用过程

- 功能：

灰度发布：ProviderFactoryBean的groupName字段

软负载：ProviderFactoryBean的weight字段

## 4.分布式服务框架注册中心

- 类简介

IRegisterCenter4Provider：服务端注册中心接口

IRegisterCenter4Invoker：消费端注册中心接口

RegisterCenter：
注册中心，实现了IRegisterCenter4Provider和IRegisterCenter4Invoker接口；
ZkClient需要使用volatile（单例）；registerProvider方法需要加锁；
服务提供者信息的路径为：ZK命名空间/当前部署应用APP命名空间/ groupName/serviceNode(服务接口名)/PROVIDER_TYPE，为永久节点
服务器节点：服务提供者信息的路径 + "/" + localIp + "|" + serverPort + "|" + weight + "|" + workerThreads + "|" + groupName;，即为最后的子节点，设置为临时节点，用来自动上下线
（/config_register/app1/default/HelloService/PROVIDER_TYPE/**127.0.0.1/1111/2/10/default**，
   /config_register/app1/default/PdcService/PROVIDER_TYPE/**127.0.0.1/1111/2/10/default**
，一个接口有两个方法，那么也就是一个接口有两个服务提供者）

消费端节点：（/config_register/app1/default/HelloService/INVOKER_TYPE/**127.0.0.1**）

监听的是每个接口下ip的变化

synchronized防止重复注册

## 5.分布式服务框架底层通信实现

关于同步异步阻塞非阻塞：
同步阻塞，可以看作等待IO时，啥都不能做；同步非阻塞，可以看作等待IO时，可以做其他的；异步非阻塞，可以看作不用等待IO，直接做其他的，IO好了会通知我

- 服务端

NettyServer：提供启动Netty服务的方法，相对应的编解码处理器及服务端业务逻辑处理器

NettyServerInvokeHandler：服务端逻辑处理器，根据解码得到的Java请求对象确定服务提供者接口及方法，通过反射来调用；
使用了Semaphore来做流控处理，控制了服务端的服务能力(亮点)

NettyDecoderHandler：解码器，负责将字节数组解码为Java对象

NettyEncoderHandler：编码器，负责将Java对象序列化为字节数组

编解码器使用自定义方式解决来粘包/半包问题(亮点)

- 客户端

问题：

> 1.选择合适的序列化协议，解决粘包/半包问题

在编解码器中已解决：使用int数据类型来记录整个消息的字节数组长度，然后将该int数据作为消息的消息头一起传输；服务端接收消息数据时，先接收4个字节的int数据类型数据，此数据即为整个消息字节数组的长度，再接收剩余字节，直到接收的字节数组长度等于最先接收的int数据类型数据大小，即字节数组的长度

> 2.发挥长连接的优势，对Netty的Channel通道进行复用

NettyChannelPoolFactory：Channel连接池工厂类，为每一个服务提供者地址预先生成一个保存Channel的阻塞队列ArrayBlockingQueue(亮点)

创建channel时，使用了CountDownLatch来确保Channel建立的过程结束再返回Channel

> 3.客户端发起服务调用后需要同步等待调用结果，但是Netty是异步框架，所以需要自己实现同步等待机制(为了保证顺序)

为每次请求新建一个阻塞队列，返回结果的时候，存入该阻塞队列，若在超时时间内返回结果值，则调用端将该返回结果从阻塞队列中取出返回给调用方，否则超时返回null(亮点)

AresRequest：客户端请求，封装了唯一标识、服务提供者信息、调用的方法名称、传递参数、消费端应用名、消费请求超时时长

AresResponse：服务端响应，封装了唯一标识、超时时间、返回的结果对象

AresResponseWrapper：异步调用返回结果AresResponse的包装类，由保存返回结果的阻塞队列和返回时间组成；同时定义了判断返回结果是否超时过期的方法isExpire()；用来实现对Netty发起异步调用后同步等待功能

RevokerResponseHolder：保存及操作返回结果的数据容器类

> AresResponse、AresResponseWrapper、RevokerResponseHolder即同步等待Netty调用结果返回的数据结构定义，接下来将在NettyChannelPoolFactory中实现客户端业务逻辑处理器NettyClientInvokerHandler，在NettyClientInvokerHandler中获取Netty异步调用返回的结果，并将该结果保存到RevokerResponseHolder

NettyClientInvokerHandler：将Netty异步返回的结果存入阻塞队列,以便调用端同步获取

RevokerProxyBeanFactory：远程服务在服务消费端的动态代理实现，使用JDK动态代理，同样是单例，内部的fixedThreadPool也是单例，且fixedThreadPool不需要volatile，因为指令重排序不会影响线程安全

- 核心流程解析

1 一开始Spring会将RevokerFactory注入到容器中，其afterPropertiesSet方法会初始化服务提供者列表和NettyChannelPoolFactory（创建Channel，即Netty客户端，使用CountDownLatch来等待Channel创建），还会将其serviceObject属性由RevokerProxyBeanFactory#getProxy赋值，即RevokerProxyBeanFactory作为代理，那么客户端调用方法时将会调用invoke方法（JDK动态代理）

2 invoke方法根据接口和负载均衡策略选择服务提供者，创建AresRequest对象，通过单例线程池fixedThreadPool发起请求，即调用RevokerServiceCallable#call

3 RevokerServiceCallable#call方法首先根据AresRequest的唯一标识创建AresResponseWrapper，然后从NettyChannelPoolFactory中根据服务提供者ip获取对应的Channel连接，将AresRequest写入到Channel中

4 将AresRequest写入到Channel中（ctx.writeAndFlush(request)），服务端进行响应时会触发NettyClientInvokeHandler#channelRead0，内部调用RevokerResponseHolder.putResultValue(response)，将结果写入responseMap<String, AresResponseWrapper>；然后RevokerServiceCallable#call最终返回结果时，调用RevokerResponseHolder.getValue，从responseMap<String, AresResponseWrapper>中获取结果

5 RevokerServiceCallable#call处理完毕后，最终返回到RevokerProxyBeanFactory#invoke中，通过Future的get方法获取返回结果，完成调用

6 AresResponseWrapper封装了AresResponse，添加了ArrayBlockingQueue达到同步返回结果的目的

7 服务提供者也是一开始Spring会将ProviderFactory注入到容器中，然后其afterPropertiesSet方法会启动Netty服务端，并以服务方法为粒度将服务注册到zk，保存好服务端的服务提供者列表providerServiceMap和客户端的本地缓存服务提供者列表serviceMetaDataMap4Consume（两者差别见注释）

8 第4步中将AresRequest写入到Channel中后，服务端获取到客户端消息，调用NettyServerInvokeHandler#channelRead0，内部会根据AresRequest获取服务提供者信息，从providerServiceMap中获取指定接口和指定方法名的服务提供者之一，使用反射和semaphore进行调用（服务提供者providerService的实现类serviceObject由xml配置的ref属性注入）

注释：服务端的map，即providerServiceMap，是一个接口对应多个提供者，每个提供者以方法为粒度，但是注册到zk还是以ip为路径，因此客户端的map，即本地缓存serviceMetaDataMap4Consume，是一个接口对应一个提供者，因为是以路径的ip进行切割作为粒度（这里假设一个服务只在一台机器上，如果多台，依次类推），在RevokerProxyBeanFactory#invoke中获取ProviderService时，重新设置一下方法就好

## 6.分布式服务框架软负载实现

框架的负载均衡通过软件算法来实现，因此称为软负载

也可以使用基于ZooKeeper的负载均衡算法，参考《ZooKeeper》笔记

- 实现原理

在消费端实现：

1.服务消费端在应用启动时从Zookeeper获取服务提供者列表，缓存到服务调用端本地缓存

2.服务消费端发起服务调用之前，先通过某种策略或算法从服务提供者列表本地缓存中选择本次调用的目标机器，再发起服务调用，从而完成负载均衡的功能

类：cluster包下，跟序列化一样，可以通过配置选择自己想要的负载均衡算法，都使用了策略模式、门面模式

- 框架中的应用：

RevokerProxyBeanFactory：远程服务在服务消费端的动态代理实现

- 完整的远程通信逻辑

> 1.从服务注册中心获取服务提供者列表，通过服务端配置的软负载算法参数clusterStrategy，作为方法ClusterEngine.queryClusterStrategy的参数，获取到具体的算法实现clusterStrategyService，调用clusterStrategyService.select方法选择某一个服务提供者
>
> 2.组装服务调用请求AresRequest对象
>
> 3.异步提交调用请求：
> 根据服务提供者信息从Netty连接池中获取对应的Channel连接：RevokerServiceCallable#call；
> 将服务请求数据对象通过某种序列化协议编码成字节数组，通过通道Channel发送到服务端：NettyChannelPoolFactory#registerChannel
>
> 4.服务端NettyServer接收到请求后，NettyServerInvokeHandler进行处理，返回给客户端接口方法调用的结果
>
> 5.客户端通过阻塞队列机制同步等待请求返回结果

## 7 分布式服务框架服务治理

### 7.1 服务分组路由实现

比如有机器组AB，可以指定某个消费者固定调用某个集群

可以实现灰度发布或AB测试功能

- 原理

注册中心的服务路径上添加一层“服务组名”，变成“根目录/APP_KEY/服务组名/服务类路径/(服务提供者或消费者类型字符串)/(IP、端口等信息数据)”

此时，指定消费某个服务组的消费端将该服务组下的服务提供者列表获取到本地缓存，根据软负载算法发起调用即可

- 代码

对RegisterCenter进行修改

### 7.2 简单服务依赖关系分析实现

可以实时获取服务提供者信息与对应的服务消费者信息

- 原理

提供获取服务提供者信息与消费者信息列表的接口方法，从注册中心查找对应的信息

- 代码

IRegisterCenter4Governance：服务治理接口

对RegisterCenter进行修改，实现此接口

### 7.3 服务调用链路跟踪实现原理

对每一次调用使用一个唯一标识将服务之间的调用串联起来，有助于排查线上问题

- 原理

在服务调用发起方生成标识本次调用的唯一ID，传递到服务提供方，然后将该ID使用ThreadLocal保存起来，在应用的业务代码里面使用拦截器统一从ThreadLocal中获取出来

如果使用了slf4j，则可以将id存入MDC工具类，在log4j.xml或其他类型的日志类型文件中配置对应的占位符，附加在日志记录的头部，从而可以实现唯一ID的自动输出，使用唯一ID将一次调用的日志串联起来