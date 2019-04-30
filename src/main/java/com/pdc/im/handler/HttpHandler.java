package com.pdc.im.handler;

import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URISyntaxException;
import java.net.URL;

/**
 * MyImServer中addLast了此Handler
 * author PDC
 */
public class HttpHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static Logger LOG = Logger.getLogger(HttpHandler.class);

    /**
     * 获取类的根目录classPath
     */
    private URL baseURL = HttpHandler.class.getProtectionDomain().getCodeSource().getLocation();
    private final String WEB_ROOT = "webroot";
    private final String DEFAULT_HOME = "chat.html";
    private final String CHARSET = "charset=utf-8;";

    //read0在Netty外代表实现类的方法，类似Spring中的do开头方法
    //不用做过多的处理，已经是完整的处理结果了
    //此处加载静态文件
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
        String uri = request.getUri();
        String fileName = uri.equals("/") ? DEFAULT_HOME : uri;
        RandomAccessFile file = null;
        try {
            file = new RandomAccessFile(getFileFromRoot(fileName), "r");
            System.out.println(file);
            //填充ContextType
            String contextType = getContextType(uri);
            //填充HttpResponse
            HttpResponse response = getHttpResponse(request, file, contextType);
            //写到浏览器
            ctx.write(response);
            ctx.write(new DefaultFileRegion(file.getChannel(),0,file.length()));
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                ChannelFuture future = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);//bug
                if(!HttpHeaders.isKeepAlive(request)){//如果不是长连接，则关闭
                    future.addListener(ChannelFutureListener.CLOSE);
                }
                file.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private File getFileFromRoot(String fileName){
        try {
            String path = baseURL.toURI() + WEB_ROOT + "/" + fileName;
            if(path != null){
                //bug:此处要将file:/去掉，并且替换/
                path = !path.contains("file:") ? path : path.substring(5);
                path = path.replaceAll("//","/");
                System.out.println(path);
               return new File(path);
            }
            System.out.println("path为null");
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        return null;
    }

    private HttpResponse getHttpResponse(FullHttpRequest request, RandomAccessFile file, String contextType) throws IOException {
        HttpResponse response = new DefaultHttpResponse(request.getProtocolVersion(), HttpResponseStatus.OK);
        response.headers().set(HttpHeaders.Names.CONTENT_TYPE,contextType + CHARSET);
        if(HttpHeaders.isKeepAlive(request)){//此处易出bug
            response.headers().set(HttpHeaders.Names.CONTENT_LENGTH,file.length());
            response.headers().set(HttpHeaders.Names.CONNECTION,HttpHeaders.Values.KEEP_ALIVE);
        }
        return response;
    }

    private String getContextType(String uri) {
        String contextType = "text/html;";
        if(uri.endsWith(".css")){
            contextType = "text/css;";
        }else if(uri.endsWith(".js")){
            contextType = "text/javascript;";
        }else if(uri.toLowerCase().matches("(jpg|png|gif)$")){
            String ext = uri.substring(uri.lastIndexOf("."));
            contextType = "image/" + ext + ";";
        }
        return contextType;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        Channel client = ctx.channel();
        LOG.info("Client:"+client.remoteAddress()+"异常");
        // 当出现异常就关闭连接
        cause.printStackTrace();
        ctx.close();
    }
}
