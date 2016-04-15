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
- Passes 70/72 tests. Issue in the 2 failures is the missing ListenerConfiguration support in the Configuration class implementation.
- After that TCK hangs during org.jsr107.tck.expiry.CacheExpiryTest in org.jsr107.tck.integration.CacheLoaderServer, due to yet incomplete Loader implementation
