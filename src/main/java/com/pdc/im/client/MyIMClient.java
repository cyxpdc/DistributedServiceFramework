package com.pdc.im.client;

import com.pdc.im.client.handler.MyIMClientHandler;
import com.pdc.im.code.IMDecoder;
import com.pdc.im.code.IMEncoder;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.io.IOException;

/**
 * 客户端的逻辑实现
 * @author PDC
 */
public class MyIMClient{
    private MyIMClientHandler clientHandler;
    private String host;
    private int port;

    public MyIMClient(String nickName){
        this.clientHandler = new MyIMClientHandler(nickName);
    }

    public void connect(String host,int port){
        this.host = host;
        this.port = port;

        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(workerGroup);
            bootstrap.channel(NioSocketChannel.class);
            bootstrap.option(ChannelOption.SO_KEEPALIVE, true);
            bootstrap.handler(new ChannelInitializer<SocketChannel>() {
                @Override
                public void initChannel(SocketChannel ch) throws Exception {
                    ch.pipeline().addLast(new IMDecoder())
                                 .addLast(new IMEncoder())
                                 .addLast(clientHandler);//调用clientHandler的channelActive
                }
            });
            ChannelFuture f = bootstrap.connect(this.host, this.port).sync();
            f.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            workerGroup.shutdownGracefully();
        }
    }


    public static void main(String[] args) throws IOException {
        new MyIMClient("Sam").connect("127.0.0.1",8888);
    }
}
