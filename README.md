# RxCache

[![Build Status](https://travis-ci.org/hannesstruss/RxCache.svg?branch=master)](https://travis-ci.org/hannesstruss/RxCache)

A simple cache based on RxJava

## Example

```java
long expiryMs = 1000;
Observable<String> coldSource = api.getMessageOfTheDay();

RxCache<String> cache = new RxCache<>(expiryMs, coldSource);

cache.get().subscribe(new Action1<String>() {
  @Override
  public void call(String s) {
    System.out.println(s);
  }
});
```

## Changes

- 0.1

## Install

Via Maven:

```xml
<dependency>
  <groupId>de.hannesstruss.rxcache</groupId>
  <artifactId>rxcache</artifactId>
  <version>$latest_version</version>
</dependency>
```

or Gradle:
```groovy
compile 'de.hannesstruss.rxcache:rxcache:$latest_version'
```
