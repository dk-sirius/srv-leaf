package com.leaf.leafcore;

import com.google.common.base.Preconditions;
import com.leaf.leafcore.config.GeneratorProperties;
import com.leaf.leafcore.config.Zookeeper;
import com.leaf.leafcore.utils.IPUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/**
 * | 符号位（1bit）时间长度（41bit）| workId（10bit）｜序列号（12bit）|
 */
@Slf4j
public class SnowflakeIdGenerator extends AbsGenerator {

    private final long startTimestamp;

    /**
     * 机器ID: 10bit = 2^10 = 1024, 最多可以部署1024个ID服务，即0-1023
     */
    private final static long WORKER_ID_BIT = 10L;

    /**
     * 最大机器ID 位1023
     */
    private final static long MAX_WORKER_ID = ~(-1L << WORKER_ID_BIT);

    /**
     * 序列号: 12bit= 2^12=4096,1毫秒内最多可以生成4096个，即0-4095
     */
    private final static long SEQUENCE_BIT = 12L;

    /**
     * 最大序号位4095
     */
    private final static long MAX_SEQUENCE = ~(-1L << SEQUENCE_BIT);

    /**
     * 机器ID的偏移量：工作机器ID位于序列号左边，所以序列号就是他的偏移量
     */
    private final static long WORKER_ID_LEFT_SHIFT_BIT = SEQUENCE_BIT;

    /**
     * 时间戳偏移量:时间戳位于机器ID + 序列号左边，所以时间偏移量=（序列号+机器ID）
     */
    private final static long TIMESTAMP_LEFT_SHIFT_BIT = SEQUENCE_BIT + WORKER_ID_BIT;

    /**
     * 允许的最大回拨时间
     */
    private final static long MAX_MOVED_BACK_OFFSET = 3;

    private long workerId;

    private long sequence = 0L;

    private long lastTimestamp = -1L;

    public SnowflakeIdGenerator(GeneratorProperties properties) {
        this.startTimestamp = properties.getStartTimestamp();
        Preconditions.checkArgument(currentTmp() >= startTimestamp, "start time must lte to current time !");
        // 获取当前服务器的某个网卡的IP
        String ip = IPUtils.getIp(null);
        Preconditions.checkArgument(!ip.isEmpty(), "could not find ip");
        Zookeeper zookeeper = new Zookeeper(properties.getZookeeperProperties(), ip);
        boolean initial = zookeeper.init();
        if (initial) {
            this.workerId = zookeeper.getWorkerId();
            if (workerId >= 0 && workerId <= MAX_WORKER_ID) {
                if (log.isInfoEnabled()) {
                    log.info("Zookeeper initialized successfully , current work id is : {}", workerId);
                }
            } else {
                throw new IllegalStateException(
                        String.format("Zookeeper initialized successfully , but work id %d is not available , because work id must gte %d and lte %d"
                                , workerId, 0, MAX_WORKER_ID));
            }
        } else {
            throw new IllegalStateException("Zookeeper initialized failed!");
        }
        if (log.isInfoEnabled()) {
            log.info("SnowflakeIdGenerator was created!");
        }
    }

    @Override
    public synchronized Result id(int size) {
        long curTmp = currentTmp();
        if (curTmp < this.lastTimestamp) {
            long offset = lastTimestamp - curTmp;
            // 如果回拨小于等于3ms,尚且在可接受的范围之内，就等其一倍的时间
            if (offset <= MAX_MOVED_BACK_OFFSET) {
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(MAX_MOVED_BACK_OFFSET << 1));
                curTmp = currentTmp();
                if (curTmp < lastTimestamp) {
                    // 抛异常
                    return Result.systemClockGoBack(curTmp, lastTimestamp);
                }
            } else {
                // 抛异常
                return Result.systemClockGoBack(curTmp, lastTimestamp);
            }
        } else if (curTmp > lastTimestamp) {
            // 正常获取, 重置序列
            sequence = 0L;
        }
        return build(curTmp, size);
    }

    private Result build(long curTmp, int size) {
        int count = size <= 0 ? 1 : size;
        // 相同时间内,就自增序列
        List<Long> idList = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                curTmp = tillNextMillis(lastTimestamp);
            }
            long id = (curTmp - startTimestamp) << TIMESTAMP_LEFT_SHIFT_BIT | (workerId << WORKER_ID_LEFT_SHIFT_BIT) | sequence;
            idList.add(id);
        }
        lastTimestamp = curTmp;
        return Result.id(idList);
    }
}
