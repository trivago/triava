package com.trivago.triava.tcache.weigher;

public class ArrayWeigher<NATIVE_TYPE> implements Weigher<NATIVE_TYPE[]> {
    @Override
    public int weight(NATIVE_TYPE[] value) {
        return value.length;
    }
}
