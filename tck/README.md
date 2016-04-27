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
- All basic functionality tests pass. This includes creating and destroying caches. Also all put, get, replace, delete Operations work compliant. 
- Passes 288/466 tests. 

## Unclear JSR107 Specs, but compliant according to TCK
The following tests or Specs are unclear and should be adressed to the JSR107 working group.

# RemoveTest.remove_2arg_NullValue()
	// The TCK test demands that we throw a NPE, which is IMO not required by the JSR107 Spec.
	// While a JCache may not contain null values, this does not mean to throw NPE. I would expect to return false.

# CacheMBStatisticsBeanTest
	// Three wrong assertEquals() checks, where the "expected" and "actual" parameters are exchanged.
	// Has no influence on test result, but wrong test output.
	    assertEquals(result, "Sooty");
	    assertEquals(result, "Trinity");
	
