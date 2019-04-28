package com.pdc.catalina.handler;

import com.pdc.catalina.http.MyRequest;
import com.pdc.catalina.http.MyResponse;
import com.pdc.catalina.servlets.impl.MyServlet;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpRequest;

/**
 * author PDC
 */
public class MyHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if(msg instanceof HttpRequest){
            HttpRequest httpRequest = (HttpRequest)msg;

            MyRequest request = new MyRequest(ctx,httpRequest);
            MyResponse response = new MyResponse(ctx,httpRequest);

            new MyServlet().doGet(request,response);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        super.exceptionCaught(ctx, cause);
    }
}
