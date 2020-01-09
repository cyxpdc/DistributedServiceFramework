package com.pdc.im.server;

import com.pdc.im.code.IMDecoder;
import com.pdc.im.code.IMEncoder;
import com.pdc.im.handler.HttpHandler;
import com.pdc.im.handler.SocketHandler;
import com.pdc.im.handler.WebSocketHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import org.apache.log4j.Logger;

/**
 * @author PDC
 */
public class MyImServer {

    private static Logger LOG = Logger.getLogger(MyImServer.class);

    private final int port = 8888;

    //主从模型
    private void start(){
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
                        protected void initChannel(SocketChannel client) {
                            //业务逻辑链
                            client.pipeline()/** 支持自定义socket协议 */
                                .addLast(new IMDecoder())
                                .addLast(new IMEncoder())
                                .addLast(new SocketHandler())
                                /** 解析HTTP协议 */
                                .addLast(new HttpServerCodec())//编码解码器
                                .addLast(new HttpObjectAggregator(64*1024))//最大http请求头信息
                                .addLast(new ChunkedWriteHandler())//用于处理文件流的handler
                                .addLast(new HttpHandler())//业务逻辑处理,交给handler
                                /** 解析WebSocket请求
                                 *  ws://im代表websocket，/im需要与前端请求serverAddr一致
                                 * */
                                .addLast(new WebSocketServerProtocolHandler("/im"))
                                .addLast(new WebSocketHandler());
                        }
                     });
            //启动，sync代表阻塞,等客户端连接
            ChannelFuture future = server.bind(port).sync();
            LOG.info("ImServer已启动，端口号为：" + port);
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
            new MyImServer().start();
        }catch (Exception e){
            e.printStackTrace();
        }
    }
}
