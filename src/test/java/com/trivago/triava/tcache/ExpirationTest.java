package com.trivago.triava.tcache;

import com.trivago.triava.tcache.core.Builder;
import com.trivago.triava.tcache.util.TestUtils;
import com.trivago.triava.time.TimeSource;

import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import javax.cache.Cache;
import javax.cache.configuration.MutableConfiguration;

import static org.junit.Assert.*;

public class ExpirationTest {

    static final String CACHE_NAME = "warmup";

    @Test
    public void longTermCaching() {

        long now = 100000000;
        long time_1hour = 3600_000;
        long time_1day = time_1hour * 24;
        long time_1week = time_1day * 7;
        long time_1month = time_1week * 31; // approximately :-)
        long time_1year= time_1day * 365;

        ManualClock ts = new ManualClock(now);
        Builder config = new Builder();
        config.setStatistics(true).setTimeSource(ts);
        config.setMaxCacheTime(Integer.MAX_VALUE, TimeUnit.DAYS).setMaxIdleTime(Integer.MAX_VALUE, TimeUnit.DAYS);
        Cache<Integer, Integer> cache = TestUtils.createJsrCacheIntInt(CACHE_NAME + "-longTermCaching", config);

        cache.put(1, 1);
        cache.put(1, 2);
        Assert.assertTrue(cache.containsKey(1));

        ts.setMillis(now+time_1hour);
        Assert.assertTrue(cache.containsKey(1));

        ts.setMillis(now+time_1day);
        Assert.assertTrue(cache.containsKey(1));

        ts.setMillis(now+time_1week*1);
        Assert.assertTrue(cache.containsKey(1));

        ts.setMillis(now+time_1week*2);
        Assert.assertTrue(cache.containsKey(1));

        ts.setMillis(now+time_1week*3);
        Assert.assertTrue(cache.containsKey(1));

        ts.setMillis(now+time_1week*4);
        Assert.assertTrue(cache.containsKey(1));

        ts.setMillis(now+time_1month);
        Assert.assertTrue(cache.containsKey(1));

        ts.setMillis(now+time_1year);
        Assert.assertTrue(cache.containsKey(1));


        TestUtils.destroyJsrCache(CACHE_NAME);
    }


    @Test
    public void addEntryLongAfterStart() {
        long now = 100000000;
        long time_1hour = 3600_000;
        long time_1day = time_1hour * 24;
        long time_1week = time_1day * 7;
        long time_1month = time_1week * 31; // approximately :-)
        long time_1year= time_1day * 365;

        ManualClock ts = new ManualClock(now);
        Builder config = new Builder();
        config.setStatistics(true).setTimeSource(ts);
        config.setMaxCacheTime(5, TimeUnit.HOURS).setMaxIdleTime(5, TimeUnit.HOURS);
        Cache<Integer, Integer> cache = TestUtils.createJsrCacheIntInt(CACHE_NAME + "-addEntryLongAfterStart", config);

        long addTimeMilliOffsets[] = { 0, 1, time_1hour, 2*time_1hour, 3*time_1hour, 23*time_1hour,
                                       1*time_1day, 2*time_1day, 3*time_1day, 4*time_1day, 30*time_1day,
                                       1*time_1week, 2*time_1week, 3*time_1week, 4*time_1week,
                                       1*time_1month, 2*time_1month, 3*time_1month, 4*time_1month,
                                       time_1year };

        for (long offset : addTimeMilliOffsets) {
            putLateAndVerifyPresentLater(cache, now+offset, time_1hour*4, ts);
        }

        TestUtils.destroyJsrCache(CACHE_NAME);
    }

    private void putLateAndVerifyPresentLater(Cache<Integer, Integer> cache, long offset, long checkOffset,
        ManualClock ts) {
        if (offset == 2692000000L) {
            System.out.println("oops");
        }
        ts.setMillis(offset);
        cache.put(1, 1);
        Assert.assertTrue("Should contain 1 after offset " + offset,  cache.containsKey(1));
        ts.setMillis(offset+checkOffset);
        Assert.assertTrue("Should contain 1 after offset+checkOffset " + offset+checkOffset,  cache.containsKey(1));
        Assert.assertTrue(cache.containsKey(1));
    }


    class ManualClock implements  TimeSource {
        long now;

        ManualClock(long now) {
            this.now = now;
        }

        void setMillis(long now) {
            this.now = now;
        }

        void addMillis(long millis) {
            this.now += millis;
        }

        @Override
        public long time(TimeUnit timeUnit) {
            return timeUnit.convert(now, TimeUnit.MILLISECONDS);
        }

        @Override
        public long seconds() {
            return now / 1000;
        }

        @Override
        public long millis() {
            return now;
        }

        @Override
        public void shutdown() {
        }
    }

}