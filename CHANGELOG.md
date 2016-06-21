Changelog
---------

1.1 (2016-06-21)
================

- Added `RxCache#updates()`, a version of `#get()` that never emits errors.

1.0 (2016-06-14)
================

- Added `RxCache#sync()`
- `coldSource` must be an `rx.Single<T>` now
- `RxCache#get` does not complete anymore. Use `.first()` instead.
- `RxCache` is final
- Depends on RxJava 1.1.5


0.1
===

- Initial Release
