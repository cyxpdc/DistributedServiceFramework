package ares.remoting.framework.revoker;

import ares.remoting.framework.helper.PropertyConfigeHelper;
import ares.remoting.framework.model.AresResponse;
import ares.remoting.framework.model.ProviderService;
import ares.remoting.framework.serialization.NettyDecoderHandler;
import ares.remoting.framework.serialization.NettyEncoderHandler;
import ares.remoting.framework.serialization.common.SerializeType;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;

/**
 * @author pdc
 */
public class NettyChannelPoolFactory {

    private static final Logger logger = LoggerFactory.getLogger(NettyChannelPoolFactory.class);

    private static final NettyChannelPoolFactory channelPoolFactory = new NettyChannelPoolFactory();

    /**
     * Key为服务提供者地址,即机器，Value为Netty Channel阻塞队列
     */
    private static final Map<InetSocketAddress, ArrayBlockingQueue<Channel>> channelPoolMap = Maps.newConcurrentMap();
    /**
     * 初始化Netty Channel阻塞队列的长度,该值为可配置信息
     */
    private static final int channelConnectSize = PropertyConfigeHelper.getChannelConnectSize();
    /**
     * 初始化序列化协议类型,该值为可配置信息
     */
    private static final SerializeType serializeType = PropertyConfigeHelper.getSerializeType();
    /**
     * 服务提供者列表
     */
    private List<ProviderService> serviceMetaDataList = Lists.newArrayList();

    private NettyChannelPoolFactory() {
    }

    /**
     * 使用serviceMetaDataMap4Consume初始化Netty channel 连接队列channelPoolMap
     * 逻辑：取出serviceMetaDataMap4Consume所有提供者，然后创建InetSocketAddress和队列，封装即可
     *
     * @param providerMap
     */
    public void initChannelPoolFactory(Map<String, List<ProviderService>> providerMap) {
        //将服务提供者信息存入serviceMetaDataList列表
        Collection<List<ProviderService>> collectionServiceMetaDataList = providerMap.values();
        for (List<ProviderService> serviceMetaDataModels : collectionServiceMetaDataList) {
            if (CollectionUtils.isEmpty(serviceMetaDataModels)) continue;
            serviceMetaDataList.addAll(serviceMetaDataModels);
        }
        //获取服务提供者地址列表，通过查看hashmap的putval和InetSocketAddress的equals，可知ip和port相同就相同
        Set<InetSocketAddress> socketAddressSet = Sets.newHashSet();
        for (ProviderService serviceMetaData : serviceMetaDataList) {
            String serviceIp = serviceMetaData.getServerIp();
            int servicePort = serviceMetaData.getServerPort();
            InetSocketAddress socketAddress = new InetSocketAddress(serviceIp, servicePort);
            socketAddressSet.add(socketAddress);
        }
        //根据服务提供者地址列表初始化Channel阻塞队列
        //并以地址为Key,地址对应的Channel阻塞队列为value,存入channelPoolMap
        for (InetSocketAddress socketAddress : socketAddressSet) {
            for(int realChannelConnectSize = 0;realChannelConnectSize < channelConnectSize;realChannelConnectSize++){
                Channel channel = null;
                while (channel == null) {
                    //若channel不存在,则注册新的Channel与服务端进行通信
                    channel = registerChannel(socketAddress);
                }
                //将阻塞队列channelArrayBlockingQueue作为value存入channelPoolMap
                //并将新注册的Netty Channel存入阻塞队列channelArrayBlockingQueue
                ArrayBlockingQueue<Channel> channelArrayBlockingQueue = channelPoolMap.get(socketAddress);
                if (channelArrayBlockingQueue == null) {
                    channelArrayBlockingQueue = new ArrayBlockingQueue<>(channelConnectSize);
                    channelPoolMap.put(socketAddress, channelArrayBlockingQueue);
                }
                channelArrayBlockingQueue.offer(channel);
            }
        }
    }

    /**
     * 根据服务提供者地址获取对应的Netty Channel阻塞队列
     *
     * @param socketAddress
     * @return
     */
    public ArrayBlockingQueue<Channel> acquire(InetSocketAddress socketAddress) {
        return channelPoolMap.get(socketAddress);
    }

    /**
     * Channel使用完毕之后,回收到阻塞队列arrayBlockingQueue
     *
     * @param arrayBlockingQueue
     * @param channel
     * @param inetSocketAddress
     */
    public void release(ArrayBlockingQueue<Channel> arrayBlockingQueue, Channel channel, InetSocketAddress inetSocketAddress) {
        if (arrayBlockingQueue == null) return;
        //回收之前先检查channel是否可用,不可用的话,重新注册一个,放入阻塞队列
        if (channel == null || !channel.isActive() || !channel.isOpen() || !channel.isWritable()) {
            if (channel != null) {
                channel.deregister().syncUninterruptibly().awaitUninterruptibly();
                channel.closeFuture().syncUninterruptibly().awaitUninterruptibly();
            }
            Channel newChannel = null;
            while (newChannel == null) {
                logger.debug("---------register new Channel again-------------");
                newChannel = registerChannel(inetSocketAddress);
            }
            arrayBlockingQueue.offer(newChannel);
            return;
        }
        arrayBlockingQueue.offer(channel);
    }

    /**
     * 为服务提供者地址socketAddress注册新的Channel
     *
     * @param socketAddress
     * @return
     */
    public Channel registerChannel(InetSocketAddress socketAddress) {
        try {
            EventLoopGroup group = new NioEventLoopGroup();
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.remoteAddress(socketAddress);
            bootstrap.group(group)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel ch) throws Exception {
                            //注册Netty编码器
                            ch.pipeline().addLast(new NettyEncoderHandler(serializeType));
                            //注册Netty解码器
                            ch.pipeline().addLast(new NettyDecoderHandler(AresResponse.class, serializeType));
                            //注册客户端业务逻辑处理handler
                            ch.pipeline().addLast(new NettyClientInvokeHandler());
                        }
                    });
            ChannelFuture channelFuture = bootstrap.connect().sync();
            final Channel newChannel = channelFuture.channel();
            final CountDownLatch connectedLatch = new CountDownLatch(1);

            final List<Boolean> isSuccessHolder = Lists.newArrayListWithCapacity(1);
            //监听Channel是否建立成功
            channelFuture.addListener((ChannelFutureListener) future -> {
                //若Channel建立成功,保存建立成功的标记
                if (future.isSuccess()) {
                    isSuccessHolder.add(Boolean.TRUE);
                } else {
                    //若Channel建立失败,保存建立失败的标记
                    future.cause().printStackTrace();
                    isSuccessHolder.add(Boolean.FALSE);
                }
                connectedLatch.countDown();
            });

            connectedLatch.await();
            //如果Channel建立成功,返回新建的Channel
            if (isSuccessHolder.get(0)) {
                return newChannel;
            }
        } catch (InterruptedException e) {
            logger.error("Client sync() failure" + e);
            throw new RuntimeException(e);
        }
        return null;
    }

    public static NettyChannelPoolFactory singleton() {
        return channelPoolFactory;
    }
}