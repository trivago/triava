package com.trivago.triava.tcache.weigher;

import java.util.Collection;

public class CollectionWeigher implements Weigher<Collection> {
    @Override
    public int weight(Collection collection) {
        return collection.size();
    }
}
