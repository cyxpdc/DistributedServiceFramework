package ares.remoting.framework.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * 与信号量区别：信号量是“一次性可以有多少个线程一起执行”，限流器是“每秒最多允许几个请求通过”“1个请求/xxx秒”
 * @author PDC
 */
class SimpleLimiter {

    private static final Logger LOGGER = LoggerFactory.getLogger(SimpleLimiter.class);

    //当前令牌桶中的令牌数量
    private long storedPermits = 0;
    //令牌桶的容量，防止突发流量
    private long maxPermits = 3;
    //下⼀令牌产⽣时间
    private long next = System.nanoTime();
    //发放令牌间隔：纳秒
    private static final long INTERVAL = 1000_000_000;

    /**
     * 初始时，系统就开始产生令牌，不过没有使用后台线程来往令牌桶里加令牌，所以需要在代码里的(now - next) / interval计算
     *
     * 有请求来时，首先判断请求令牌的时间now是否在下一令牌产生时间next之后：
     *     如果是，那么先将这期间产生的令牌加入桶，且next变为now，即之前的时间都生成了令牌了，从now开始继续计算即可
     *     如果不是，则什么都不做
     * 得到能够获取令牌的时间at=next，然后判断是否能从桶里取令牌：（代码优化掉了if）
     *     桶里还有令牌的话就取
     *     桶里没有令牌的话就让next移到下一个interval，表示当前令牌被预支了
     * 最后at-now得到等待的时间：如果在第一步中next变为了now，说明不用等，如果没有，则需要等
     */
    public void acquire() {
        //申请令牌时的时间
        long now = System.nanoTime();
        long at;
        synchronized(this){
            if (now > next) {//判断是否可以往令牌桶里加令牌
                //新产⽣的令牌数,可能为0、1..，为整数
                long newPermits = (now - next) / INTERVAL;
                //新令牌增加到令牌桶
                storedPermits = Math.min(maxPermits,storedPermits + newPermits);
                //将下⼀个令牌发放时间设置为当前时间
                next = now;
            }
            //能够获取令牌的时间
            //如果上面都if可以运行，则next = now运行了，则说明可以获取令牌，因为next时刻就产生了令牌，所以不用等
            at = next;
            //令牌桶中能提供的令牌是否有一个，如果没有，则需要预支令牌
            long fb = Math.min(1, storedPermits);
            long nr = 1 - fb;
            next += nr * INTERVAL;//如果nr等于1，代表需要预支一个，当前next被预支了，所以需要走到下一个next，即+= nr * interval
            this.storedPermits -= fb;
        }
        //按照条件等待，预支令牌的请求则需要等
        long waitTime = Math.max(at - now, 0);
        if(waitTime > 0) {
            try {
                TimeUnit.NANOSECONDS.sleep(waitTime);
            }catch(InterruptedException e){
                LOGGER.error("被中断：" + e);
            }
        }
    }
}
