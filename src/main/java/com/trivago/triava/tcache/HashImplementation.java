package com.trivago.triava.tcache;

public enum HashImplementation
{
	ConcurrentHashMap, // Default storage: java.util.concurrent.ConcurrentHashMap
	// Performance test storage: com.google.common.cache.LocalCache
	// Do NOT use this live. LocalCache is NOT part of official Guava API.
	// LocalCache always drops in case of a jam (like TCacheJamPolicy.DROP)
//	PerfTestGuavaLocalCache, // com.google.common.cache.LocalCache  
	HighscalelibNonBlockingHashMap, // org.cliffc.high_scale_lib.NonBlockingHashMap.java
}