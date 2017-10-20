# triava

The triava project contains several of trivago's core libraries for Java-based projects: caching, collections, annotations, concurrency libraries and more.

TriavaCache is fully JCache / JSR107 (Java Caching) compliant. For more about compliance, read the [TCK information](./tck/README.md)

## License
Licensed under the Apache License, Version 2.0

## Usage:
To start, look at the [examples folder](./src/examples/java/com/trivago/examples). Further interesting examples are in the [tcache unit test folder](src/test/java/com/trivago/triava/tcache/) and [util unit test folder](src/test/java/com/trivago/triava/util/).  

### Usage in Maven: pom.xml
triava is available from [Maven Central](http://search.maven.org/#search|ga|1|a%3A%22triava%22)

```
  <dependencies>
    <dependency>
      <groupId>com.trivago</groupId>
      <artifactId>triava</artifactId>
      <version>1.0.5</version>
    </dependency>
  </dependencies>
```


### Usage in Gradle: build.gradle
```
dependencies {
	compile 'com.trivago:triava:1.0.5'
}
```

## Changes ##
See [Changelog](CHANGES.md)



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
 - Run long tests from package com.trivago.triava.tcache.integration manually
 - Run FindBugs and fix all bugs
 - Check for missing licensing header
 ```
 find . -name '*.java' ! -path './src/test/java/com/trivago/triava/tcache/tmp/*' -print0 | xargs -0 grep -L "Licensed under the Apache License, Version 2.0"
 ```
 - Release notes and versioning
     - Update [Changelog](CHANGES.md)
     - Update pom.xml with version number
     - Update this README: Update all version numbers
     - Commit changes
 - Upload to Maven Central (check with maintainers on details, e.g. in the triava Knowledge page)
 - Tag the release, if the release is good
```
version=1.0.5; git tag -a v$version -m "v$version"
mvn clean deploy -P release
```

## Examples
Have a look at the [examples folder](./src/examples/java/com/trivago/examples).
