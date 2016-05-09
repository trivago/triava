# triava - Technology Compatibility Kit integration

This folder contains all information to check standards compliance via  
Technology Compatibility Kits (TCK). 

## JSR107 - Java Caching compliance
To verify compliance, clone the Technology Compatibility Kit from https://github.com/jsr107/jsr107tck .
```
 git clone https://github.com/jsr107/jsr107tck.git
 cd jsr107tck.git
 
 # Configure triava as implementation to test
 diff <TRIAVA_DISTRIBUTION>/tck/jsr107-pom.xml pom.xml
 # If changes look well, continue
 cp <TRIAVA_DISTRIBUTION>/tck/jsr107-pom.xml pom.xml
 
 # Run test
 mvn clean install
```

### Current compliance status:
- All core functionality tests pass. This includes creating and destroying caches. Also all put, get, replace, delete Operations work compliant. 
- Passes 351/465 tests. (75%)

## Unclear JSR107 Specs, but compliant according to TCK
The following tests or Specs are unclear and should be adressed to the JSR107 working group. Please add newly found issus here and mark them in the Code with
	// TCK CHALLENGE

# RemoveTest.remove_2arg_NullValue()
	// The TCK test demands that we throw a NPE, which is IMO not required by the JSR107 Spec.
	// While a JCache may not contain null values, this does not mean to throw NPE. I would expect to return false.

# PutTest.putAll_NullKey()
	// It disallows partial success, even though this is not explicitly required, instead the Spec reads:
	//
	// " * The effect of this call is equivalent to that of calling
	//   * {@link #put(Object, Object) put(k, v)} on this cache once for each mapping
	//   * from key <tt>k</tt> to value <tt>v</tt> in the specified map.
	//   *
	// * The order in which the individual puts occur is undefined."
	//
	// There is no mention of a "rollback" for partial failures. In contrast CacheWriter has explicit documentation ("remove succesful writes from the Map").
	// The API / Spec should be changed to reflect the TCK test or vice versa.

# CacheManagerTest createCacheSameName() and createCacheSame()
	// Observation: Mandates to throw CacheException when "the same" cahe is to be created twice.
	// Issue: Javadocs has no "@throws CacheException". It is only in the text. 
	
# CacheManagerTest getNullTypeCacheRequest()
	// Observation: Mandates to throw NullPointerException when passing null as keyClass or valueClass
	// Issue: Javadocs and Spec do not mention behavior on null.
	
# org.jsr107.tck.event.CacheListenerTest.testFilteredListener(CacheListenerTest.java:396)
	// Observation: Unclear spec for Cache.remove(key): We need to use oldValue as value in the event. The Spec is not clear about this, but the TCK bombs
	// us with NPE when we would use null as "value" and oldValue as "old value". The JSR107 RI uses oldValue as "value" and leaves the "old value" unset.
	// org.jsr107.tck.event.CacheListenerTest$MyCacheEntryEventFilter.evaluate(CacheListenerTest.java:344)  // <<< Location of NPE

	
# CacheMBStatisticsBeanTest
	// Three wrong assertEquals() checks, where the "expected" and "actual" parameters are exchanged.
	// Has no influence on test result, but wrong test output.
	    assertEquals(result, "Sooty");
	    assertEquals(result, "Trinity");

# org.jsr107.tck.processor.CacheInvokeTest.noValueException()
	// Observation: This test checks for IllegalAccessError, but the ThrowExceptionEntryProcessor class wraps it and throws "new EntryProcessorException(t);"
	// An implementation should wrap EntryProcessor Exceptions also in EntryProcessorException, which means the IllegalAccessError gets double wrapped.
	// The noValueException() test treats double wrapping as wrong, but IMO the Spec says to wrap ALL EntryProcessor Exceptions.
	// Proposed solution: Change the TCK, or change the Spec to explicitly say that "exc instanceof EntryProcessorException" should not be wrapped again.  
	// See: https://github.com/jsr107/jsr107tck/issues/85

# org.jsr107.tck.processor.CacheInvokeTest
	// nullProcessor() and invokeAll_nullProcessor() require a NullPointerException. It is not mentioned in the Spec. An alternative would be to ignore a null processor. 
  	// Proposed solution: Add "@throws NullPointerException if entryProcessor is null" to Javadocs
  	
# Unclear spec which kind of Listener should fire on Evictions
	// Observation: Spec is unlcear. It talks about "evictions" when doing "expiration", but not about "true" evicitions.
	// ehcache sends EVICTED, it seems. I will go for it, but it should be clarified.
	// Proposed solution: Clarification
	
	