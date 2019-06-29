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