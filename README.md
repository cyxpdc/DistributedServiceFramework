# 分布式服务框架相关

## 一：使用netty实现简易版的WebService

包：com.pdc.catalina

### 类简介

MyNioWebServer：服务器类，阻塞等待客户端连接

MyHandler：实现ChannelInboundHandlerAdapter，处理客户端请求，channelRead方法中调用servlet的doGet()

> 搭了个大致架构，可参考myTomcat：https://github.com/cyxpdc/myTomcat自行扩展

