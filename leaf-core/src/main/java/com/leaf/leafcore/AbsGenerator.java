package com.leaf.leafcore;

public abstract class AbsGenerator implements IdGenerator {

    protected long tillNextMillis(long lastTimestamp) {
        long timestamp = currentTmp();
        while (timestamp <= lastTimestamp) {
            timestamp = currentTmp();
        }
        return timestamp;
    }

    protected long currentTmp() {
        return System.currentTimeMillis();
    }
}
