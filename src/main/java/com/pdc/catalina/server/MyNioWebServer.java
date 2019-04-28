package com.pdc.catalina.server;
/*NIO
    ServerSocketChannel channel = ServerSocketChannel.open();
    channel.bind(local);
  BIO
    ServerSocket server = new ServerSocket(port);
*/
import com.pdc.catalina.handler.MyHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;

/**
 * author PDC
 */
public class MyNioWebServer {
    //主从模型
    private void start(int port) throws  Exception{
        //Boss线程，EventLoopGroup相当于一个处理线程组，用来接收请求和处理IO
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        //Worker线程
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            //Netty服务引擎，用来做配置和启动
            ServerBootstrap server = new ServerBootstrap();
            //使用反射来生成NioServer
            //有客户端传来请求，则响应childHandler
            server.group(bossGroup, workerGroup)
                    //主线程处理类
                    .channel(NioServerSocketChannel.class)
                    //子线程处理类，一个线程一个handler，每来一个请求，新开一个线程
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        //无锁化串行编程,类似栈的调用，压到last，调用也是从last开始
                        //也就是先接收逻辑，然后解码，编码，完成逻辑
                        protected void initChannel(SocketChannel client) {
                            //业务逻辑链
                            client.pipeline()
                                    .addLast(new HttpResponseEncoder())//编码器
                                    .addLast(new HttpRequestDecoder());//解码器
                            //业务逻辑处理
                            client.pipeline().addLast(new MyHandler());

                        }
                    })
                    //配置信息
                    //主线程,最多128个；客户端请求如果来不及处理，阻塞在SO_BACKLOG队列里
                    .option(ChannelOption.SO_BACKLOG,128)
                    .childOption(ChannelOption.SO_KEEPALIVE,true);//子线程
            //启动，sync代表阻塞,等客户端连接
            ChannelFuture future = server.bind(port).sync();
            System.out.println("webServer已启动，端口号为：" + port);
            future.channel().closeFuture().sync();
        } catch (Exception e){
            e.printStackTrace();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
        
    }

    public static void main(String[] args) {
        try {
            new MyNioWebServer().start(8080);
        }catch (Exception e){
            e.printStackTrace();
        }
    }
}
