# triava

The triava project contains several of trivago's core libraries for Java-based projects: caching, collections, annotations, concurrency libraries and more.

The included Cache implementation tCache is heading for full JSR107 (Java Caching) compliance. Most operations work fully compliant, including creating caches through the Service Provider Interface, MBean support and obviously putting and getting values. Cache Listeners are supported since v0.9.4. in both sync and async modes.

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
      <version>0.9.4</version>
    </dependency>
  </dependencies>
```


### Usage in Gradle: build.gradle
```
dependencies {
	compile 'com.trivago:triava:0.9.4'
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
    - cache: Listener support for synchronous and asynchronous events
    - util: Add a UnitFormatter to format values to units, e.g. "10.53MiB", "20.5s" or "10GW, 200MW, 25W".
           The Unit formatter supports different Unit Systems: SI units (1000 based, Kilo, k), IEC units (1024 based, Kibi, Ki) and JEDEC (1024 based, Kilo, K)


## Building:
triava requires Java 7 and depends on the JSR107 API javax.cache:cache-api:1.0.0. Dependencies are resolved by Maven. The following will build everything, including Javadoc and a source jar:

`mvn package`

This will create three artefacts in the target/ folder:

- triava-[version].jar
- triava-[version]-sources.jar
- triava-[version]-javadoc.jar

Maintainers can upload new versions to Maven Central Staging:
`mvn clean deploy -P release` 


## Examples
Have a look at the [examples folder](./src/examples/java/com/trivago/examples).
