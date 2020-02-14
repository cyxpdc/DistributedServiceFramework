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
    private static final long interval = 1000_000_000;

    /**
     * 如果线程请求令牌的时间在下一令牌产生时间之后，那么该线程立刻就能够获取令牌；
     * 反之，如果请求时间在下一令牌产生时间之前，那么该线程是在下一令牌产生的时间获取令牌。由于此时下
     * 一令牌已经被该线程预占，所以下一令牌产生的时间需要加上1秒。
     *
     * 令牌要首先从令牌桶中出，所以我们需要按需计算令牌桶中的数量，当有线程请求令牌时，先从令牌桶中出
     */
    public void acquire() {
        //申请令牌时的时间
        long now = System.nanoTime();
        long at;
        synchronized(this){
            if (now > next) {
                //新产⽣的令牌数,可能为0、1..，为整数
                long newPermits = (now - next) / interval;
                //新令牌增加到令牌桶
                storedPermits = Math.min(maxPermits,storedPermits + newPermits);
                //将下⼀个令牌发放时间设置为当前时间
                next = now;
            }
            //能够获取令牌的时间
            at = next;
            //令牌桶中能提供的令牌是否有一个，如果没有，则需要预支令牌
            long fb = Math.min(1, storedPermits);
            long nr = 1 - fb;
            next += nr * interval;//如果nr等于1，代表需要预支一个，当前next被预支了，所以需要走到下一个next，即+= nr * interval
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
