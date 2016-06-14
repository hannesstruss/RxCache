# RxCache

[![Build Status](https://travis-ci.org/hannesstruss/RxCache.svg?branch=master)](https://travis-ci.org/hannesstruss/RxCache)

A simple cache based on RxJava

## Example

```java
long expiryMs = 1000;
Single<String> coldSource = api.getMessageOfTheDay().toSingle();

RxCache<String> cache = new RxCache<>(expiryMs, coldSource);

cache.get().subscribe(new Action1<String>() {
  @Override
  public void call(String s) {
    System.out.println(s);
  }
});

// Fetch new value and emit to all subscribers of cache.get():
cache.sync().subscribe();
```

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

## License

    Copyright 2015 Hannes Struß

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
