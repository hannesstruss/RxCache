# RxCache

[![Build Status](https://travis-ci.org/hannesstruss/RxCache.svg?branch=master)](https://travis-ci.org/hannesstruss/RxCache)

A simple cache based on RxJava

## Example:

```java
RxCache<String> cache = new RxCache<>(1000, api.getMessageOfTheDay());

cache.get().subscribe(new Action1<String>() {
  @Override
  public void call(String s) {
    System.out.println(s);
  }
});
```
