package com.pdc.catalina.http;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.QueryStringDecoder;

import java.util.List;
import java.util.Map;

/**
 * @author PDC
 */
public class MyRequest {

    private ChannelHandlerContext ctx;
    private HttpRequest request;

    public MyRequest(ChannelHandlerContext ctx, HttpRequest request) {
        this.ctx = ctx;
        this.request = request;
    }

    public String getUri(){
        return request.uri();
    }

    public String getMethod(){
        return request.method().name();
    }

    public Map<String, List<String>> getParameters(){
        QueryStringDecoder decoder = new QueryStringDecoder(getUri());
        return decoder.parameters();
    }

    public String getParameter(String name){
        Map<String, List<String>> parameters = getParameters();
        List<String> param = parameters.get(name);
        if(null == param){
            return null;
        }else{
            return param.get(0);
        }
    }

}
