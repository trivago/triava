# triava

The triava project contains several of trivago's core libraries for Java-based projects: caching, collections, annotations, concurrency libraries and more.

TrivaCache is fully JCache / JSR107 (Java Caching) compliant. For more about compliance, read the [TCK information](./tck/README.md)

## License
Licensed under the Apache License, Version 2.0

## Usage:
To start, look at the [examples folder](./src/examples/java/com/trivago/examples).

### Usage in Maven: pom.xml
triava is available from [Maven Central](http://search.maven.org/#search|ga|1|a%3A%22triava%22)

```
  <dependencies>
    <dependency>
      <groupId>com.trivago</groupId>
      <artifactId>triava</artifactId>
      <version>1.0-rc1</version>
    </dependency>
  </dependencies>
```


### Usage in Gradle: build.gradle
```
dependencies {
	compile 'com.trivago:triava:1.0-rc1'
}
```

## Changes ##
- v0.4.0 Initial version. Production ready.
- v0.9.0 Finalized package structure. Moved existing unit tests to triava project.
- v0.9.1 cache: Implementing JSR107 compliance (work in progress)
    - MXBean support: Configuration and Statistics
    - Added CacheManager.destroyCache()
    - Added JSR methods ...replace...() methods.
- 0.9.4
    - cache: Listener support for synchronous and asynchronous events.
    - cache: Support CacheLoader, ListenerConfiguration, MutableEntry, EntryProcessor
    - util:  Added a UnitFormatter to format values to units, e.g. "10.53MiB", "20.5s" or "10GW, 200MW, 25W". The Unit formatter
             supports SI units (1000 based, kilo, k), IEC60027-2 units (1024 based, kibi, Ki) and JEDEC units (1024 based, Kilo, K)
- 0.9.5
    - cache: Support Store-By-Value.
    - cache: Fully JSR107 compliance for Statistic, Write-Through, Read-Through
- 0.9.6
    - cache: JSR107 ExpiryPolicy support    
    - cache: Incompatible changes:
        - idleTime=0 means immediately expired instead of no expiration
        - Signature of put() with idle/cache times changed from long to int
- 0.9.8
    - cache: Fully JCache / JSR107 compliance 
- 0.9.9
    - cache: API cleanup, adding TimeUnit param to API
- 0.9.13
    - cache: API cleanup, fix duplicate notification of Listeners
- 0.9.14
    - cache: Fix missing notification of Listeners
- 0.9.15
    - Fixing the API for Version 1.0.
 - 1.0-rc1
    - Release candidate for v1.0



## Building:
triava requires Java 7 and depends on the JSR107 API javax.cache:cache-api:1.0.0. Dependencies are resolved by Maven. The following will build everything, including Javadoc and a source jar:

`mvn package`

This will create three artifacts in the target/ folder:

- triava-[version].jar
- triava-[version]-sources.jar
- triava-[version]-javadoc.jar

Maintainers can upload new versions to Maven Central Staging:

Before uploading a new version, you should:
 - Fix all Javadocs warnings
 - Run FindBugs and fix all bugs
 - Check for missing licensing header
 ```
 find . -name '*.java' ! -path './src/test/java/com/trivago/triava/tcache/tmp/*' -print0 | xargs -0 grep -L "Licensed under the Apache License, Version 2.0"
 ```
 - Update this README: Changes section
 - Update this README: Update all version numbers
 - Tag the release and upload to Maven Central (check with maintainers on details, e.g. in the triava Knowledge page)
```
version=1.0; git tag -a v$version -m "v$version"
mvn clean deploy -P release
```

## Examples
Have a look at the [examples folder](./src/examples/java/com/trivago/examples).
