# triava

The triava project contains several of trivago's core libraries for Java-based projects: caching, collections, annotations, concurrency libraries and more.

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
      <version>0.9.0</version>
    </dependency>
  </dependencies>
```


### Usage in Gradle: build.gradle
```
dependencies {
	compile 'com.trivago:triava:0.9.3'
}
```

## Changes ##
- v0.4.0 Initial version. Production ready.
- v0.9.0 Finalizing package structure. Move existing unit tests to triava project.
- v0.9.1 Implementing JSR107 compliance (work in progress)
    - MXBean support: Configuration and Statistics
    - Added CacheManager.destroyCache()
    - Added JSR methods ...replace...() methods.


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
