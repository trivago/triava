# triava - Technology Compatibility Kit integration

This folder contains all information to check standards compliance via  
Technology Compatibility Kits (TCK). 

## JSR107 - Java Caching compliance
- All core functionality tests pass. This includes creating and destroying caches.
- PASS: put, get, replace, delete Operations
- PASS: Listeners, Write-Through, Read-Through
- Not passing fully: Statistics, Expiration, StoreByValue 
- Passes 426/465 tests. (92%)

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


## Unclear JSR107 Specs, but compliant according to TCK
The following tests or Specs are unclear and should be adressed to the JSR107 working group. Please add newly found issus here and mark them in the Code with
	// TCK CHALLENGE
	
### CacheMBStatisticsBeanTest.testPutIfAbsent()
- Observation: First assertEquals(missCount, ...) requires missCount == 0, but there was a miss (value not present before the call, but after the call)
- Observation: Second assertEquals(hitCount, ...) requires hitCount == 0, but there was a hit (value present both before and after the call)
- Issue: It is not clear why this check are in the TCK. The spec does not specify when the mapping must be in the table (before or after the call). But
         no matter if it is before or after: At least one of theTCK checks seem to be wrong.
- Proposed change: Clarify. Create ticket on TCK


----------------------------------------------------------------------------------------------------------------
--- ABOVE: NEW QUESTIONS
----------------------------------------------------------------------------------------------------------------
--- BELOW: OLD QUESTIONS
----------------------------------------------------------------------------------------------------------------

	

### PutTest.putAll_NullKey()
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

### CacheManagerTest createCacheSameName() and createCacheSame()
	// Observation: Mandates to throw CacheException when "the same" cache is to be created twice.
	// Issue: Javadocs has no "@throws CacheException". It is only in the text. 
	
### CacheManagerTest getNullTypeCacheRequest()
	// Observation: Mandates to throw NullPointerException when passing null as keyClass or valueClass
	// Issue: Javadocs and Spec do not mention behavior on null.
	
### org.jsr107.tck.event.CacheListenerTest.testFilteredListener(CacheListenerTest.java:396)
	// Observation: Unclear spec for Cache.remove(key): We need to use oldValue as value in the event. The Spec is not clear about this, but the TCK bombs
	// us with NPE when we would use null as "value" and oldValue as "old value". The JSR107 RI uses oldValue as "value" and leaves the "old value" unset.
	// org.jsr107.tck.event.CacheListenerTest$MyCacheEntryEventFilter.evaluate(CacheListenerTest.java:344)  // <<< Location of NPE

	
### CacheMBStatisticsBeanTest
	// Three wrong assertEquals() checks, where the "expected" and "actual" parameters are exchanged.
	// Has no influence on test result, but wrong test output.
	    assertEquals(result, "Sooty");
	    assertEquals(result, "Trinity");

### org.jsr107.tck.processor.CacheInvokeTest.noValueException()
	// Observation: This test checks for IllegalAccessError, but the ThrowExceptionEntryProcessor class wraps it and throws "new EntryProcessorException(t);"
	// An implementation should wrap EntryProcessor Exceptions also in EntryProcessorException, which means the IllegalAccessError gets double wrapped.
	
	// Issue: The noValueException() test treats double wrapping as wrong, but IMO the Spec says to wrap ALL EntryProcessor Exceptions.
	
	// Proposed solution: Change the TCK, or change the Spec to explicitly say that "exc instanceof EntryProcessorException" should not be wrapped again.  
	// See: https://github.com/jsr107/jsr107tck/issues/85

### org.jsr107.tck.processor.CacheInvokeTest
	// Observation: nullProcessor() and invokeAll_nullProcessor() require a NullPointerException.

	// Issue: It is not mentioned in the Spec. An alternative would be to ignore a null processor. 

	// Proposed solution: Add "@throws NullPointerException if entryProcessor is null" to Javadocs

### org.jsr107.tck.integration.CacheLoaderTest
	// Observation: shouldPropagateExceptionUsingLoadAll() requires a CacheLoaderException
	
	// Issue: Javadocs do not mandate this for Cache.loadAll().
	//    a) Javadoc states: "If a problem is
	//     encountered during the retrieving or loading of the objects,
	//     an exception is provided to the {@link CompletionListener}."
	//     b) The @throws declaration is "@throws CacheException        thrown if there is a problem performing the load."
	
	// Proposed change: Change CacheLoaderTest to check for CacheException
	// See: https://github.com/jsr107/jsr107tck/issues/99	


### org.jsr107.tck.integration.CacheWriterTest
	// Observation: SImilar to CacheLoaderTest
	// CacheWriterException

### org.jsr107.tck.integration.CacheLoaderWithoutReadThroughTest
- Observation: shouldLoadWhenAccessingWithEntryProcessor() requires that no load takes place for cache.invoke() in case of no-read-through
- Issue: While the Spec has a nice table about read-through and is clear about it, the JavaDocs do not mention it:

	// "If an {@link Entry} does not exist for the specified key, an attempt is made to load it (if a loader is configured)" (no mention of read-through)
	
- Proposed change: invoke() has fairly complex behaviour, and the documentation should be as clear as possible. 
	// Old: "an attempt is made to load it (if a loader is configured)"
	// New: "an attempt is made to load it (if the cache is read-through)"
	
### Unclear spec which kind of Listener should fire on Evictions
	// Observation: Spec is unlcear. It talks about "evictions" when doing "expiration", but not about "true" evicitions.
	// ehcache sends EVICTED, it seems. I will go for it, but it should be clarified.
	
	// Proposed change: Clarification

### Spec: CacheWriter table
	//  V getAndReplace(K key, V value)
	// Observation: Wrong description "Yes, if this method returns true"

	// Proposed change: "Yes, if this method returns a non-null value"
	
### org.jst107.tck.??? <<< Determine the test(s) that fail
	// Observation: javax.cache.Cache.Entry and MutableEntry need to be Serializable. Otherwise TCK fails (TODO: Add the test names)
	
	// Proposed action: Clarify. Possibly add "Serializable" requirement to Spec
	
###org.jsr107.tck.RemoveTest
- Observation: shouldWriteThroughRemove_SpecificEntry() mandates that remove(key,value) only(!) writes-through, if we happen to have the
  (key,value) combination in the local cache.
- Issue: This can lead to non-deterministic behavior if the local cache has evicted
  that Cache entry. It is also inconsistent with all other methods: Usually the write-through is always done, and the local
  Cache get mutated for the successfully written-through entries. But here the local Cache is inspected first.
- Proposed change: Unclear. The reason could be an omission in the CacheWriter Interface: It does not have a write(key,value) method. To be discussed.
