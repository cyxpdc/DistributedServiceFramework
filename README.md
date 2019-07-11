# 分布式服务框架相关

## 一：使用Netty实现简易版的WebService

包：com.pdc.catalina

### 1.类简介

MyNioWebServer：服务器类，阻塞等待客户端连接

MyHandler：实现ChannelInboundHandlerAdapter，处理客户端请求，channelRead方法中调用servlet的doGet()

> 搭了个大致架构，可参考myTomcat：https://github.com/cyxpdc/myTomcat自行扩展

## 二：使用Netty实现im聊天系统

### 1.架构：

多个Client和一个Server，请求涉及协议、编码解码、业务逻辑(消息记录保存、分发，当前在线人数的统计等)

Client功能：发送表情、文字；登陆退出、送赞。

![](H:\ideaCode\nettyWebServer\1.png)

### 2.实现思路：

Server里有一个pipeline，pipeline里用许多Handler处理，如HttpHandler、WebSocketHandler、自定义协议的IMPHandler，根据判断来调用Handler；
使用异步处理，结果最终返回给客户端，客户端需要使用**WebSocket**，实现实时交互

![](H:\ideaCode\nettyWebServer\2.png)
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
3.SocketHandler主要用来确保系统使用自定义协议，此时，需要自定义编码解码器，为IMEncoder和IMDecoder，即序列化和反序列化的过程，会调用IMDecoder#decode和IMEncoder#encode（此处的序列化框架为msgpack）；(其实在这里并没有用上)

- 实际上，用到的只有IMDecoder#decode(读取堆外内存)、WebSocketHandler(处理业务)、HttpHandler(解析HTTP)
  自定义的SocketHandler#channelRead0、IMDecoder#encode并没有用到
  先走IMDecoder#decode，再走HttpHandler，然后开始业务操作，即走WebSocketHandler

接下来，用户开始操作，操作过程中，起作用的只有WebSocketHandler#channelRead0

IMEncoder和IMDecoder还用在IMServerProcessor中：接收到字符串时，使用自定义的IMDecoder#decode，发送到浏览器时，使用自定义的IMEncoder#encode。
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

服务的引入与发布，即服务消费端与提供段：使用Spring来实现

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
![1561949523390](F:/markdownPicture/assets/1561949523390.png)

- 使用示例

服务发布:

1.自定义接口HelloService和实现类HelloServiceImpl

2.xml配置：将HelloService作为远程服务发布出去
![1561951483539](F:/markdownPicture/assets/1561951483539.png)
![1562029746423](F:/markdownPicture/assets/1562029746423.png)

服务引入：

1.xml配置：引入HelloService。这里配置好了接口后，在RevokerFactoryBean中得到匹配此接口的服务提供者列表，再根据软负载均衡策略选取一个服务提供者代理对象，发起调用
![1561951673185](F:/markdownPicture/assets/1561951673185.png)

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