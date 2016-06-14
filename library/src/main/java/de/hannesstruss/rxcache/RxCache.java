package de.hannesstruss.rxcache;

import rx.Completable;
import rx.Observable;
import rx.Scheduler;
import rx.functions.Action1;
import rx.functions.Func0;
import rx.functions.Func1;
import rx.schedulers.Schedulers;
import rx.schedulers.Timestamped;

public final class RxCache<T> {
  private final long expiryMs;
  private final Observable<T> coldSource;
  private final Scheduler scheduler;

  private Observable<Timestamped<T>> cache;

  private final Func1<Timestamped<T>, T> unwrap = new Func1<Timestamped<T>, T>() {
    @Override public T call(Timestamped<T> tTimestamped) {
      return tTimestamped.getValue();
    }
  };

  private final Func1<Timestamped<T>, Boolean> isFresh = new Func1<Timestamped<T>, Boolean>() {
    @Override public Boolean call(Timestamped<T> tTimestamped) {
      return scheduler.now() - tTimestamped.getTimestampMillis() < expiryMs;
    }
  };

  public RxCache(long expiryMs, Observable<T> coldSource) {
    this(expiryMs, Schedulers.immediate(), coldSource);
  }

  public RxCache(long expiryMs, Scheduler timestampScheduler, Observable<T> coldSource) {
    this.expiryMs = expiryMs;
    this.coldSource = coldSource;
    this.scheduler = timestampScheduler;
    invalidate();
  }

  public Observable<T> get() {
    return Observable.concat(cache, fetch())
        .first(isFresh)
        .map(unwrap);
  }

  /** Fetches a new value, caches it and immediately emits it to subscribers */
  public Completable sync() {
    return null;
  }

  public void invalidate() {
    cache = Observable.empty();
  }

  private Observable<Timestamped<T>> fetch() {
    return Observable.defer(new Func0<Observable<Timestamped<T>>>() {
      @Override public Observable<Timestamped<T>> call() {
        cache = coldSource.timestamp(scheduler).cache();
        return cache;
      }
    }).doOnError(new Action1<Throwable>() {
      @Override public void call(Throwable throwable) {
        invalidate();
      }
    });
  }
}
