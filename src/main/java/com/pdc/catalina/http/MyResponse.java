package com.pdc.catalina.http;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;


/**
 * @author PDC
 */
public class MyResponse {

    private ChannelHandlerContext ctx;
    private HttpRequest request;

    public MyResponse(ChannelHandlerContext ctx, HttpRequest request) {
        this.ctx = ctx;
        this.request = request;
    }

    public void write(String output){
        try {
            if(output != null){//需要先判断，否则报空指针异常
                System.out.println("output不为null");
                //填充HTTP协议
                FullHttpResponse response = new DefaultFullHttpResponse
                        (HttpVersion.HTTP_1_1,
                                HttpResponseStatus.OK,
                                Unpooled.wrappedBuffer(output.getBytes("UTF-8")));
                response.headers().set(HttpHeaders.Names.CONTENT_TYPE,"text/json");//json
                response.headers().set(HttpHeaders.Names.CONTENT_LENGTH,response.content().readableBytes());
                response.headers().setInt(HttpHeaders.Names.EXPIRES,0);
                if(HttpHeaders.isKeepAlive(request)){
                    response.headers().set(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
                }
                //写到浏览器
                ctx.write(response);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            ctx.flush();
        }

    }
}
