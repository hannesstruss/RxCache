package de.hannesstruss.rxcache;

import rx.Completable;
import rx.Observable;
import rx.Scheduler;
import rx.Single;
import rx.functions.Action1;
import rx.functions.Func0;
import rx.functions.Func1;
import rx.schedulers.Schedulers;
import rx.schedulers.Timestamped;
import rx.subjects.PublishSubject;

public final class RxCache<T> {
  private final long expiryMs;
  private final Observable<T> coldSource;
  private final Scheduler scheduler;
  private final PublishSubject<T> updates;
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

  /**
   * Creates a new {@code RxCache} that expires after {@code expiryMs} milliseconds. {@code coldSource} must be
   * a cold Observable.
   */
  @SuppressWarnings("unused")
  public RxCache(long expiryMs, Single<T> coldSource) {
    this(expiryMs, Schedulers.immediate(), coldSource);
  }

  /**
   * Creates a new {@code RxCache} that expires after {@code expiryMs} milliseconds. {@code timestampScheduler}
   * will be used to obtain the timestamp driving expiry. {@code coldSource} must be
   * a cold Observable.
   */
  public RxCache(long expiryMs, Scheduler timestampScheduler, Single<T> coldSource) {
    this.expiryMs = expiryMs;
    this.coldSource = coldSource.toObservable();
    this.scheduler = timestampScheduler;
    this.updates = PublishSubject.create();
    invalidate();
  }

  /**
   * Get a stream of values which can error if the cache is empty/stale and fetching the value errors.
   */
  public Observable<T> get() {
    return Observable.merge(
        updates,
        Observable.concat(cache, fetch())
            .first(isFresh)
            .map(unwrap)
    );
  }

  /**
   * Fetches a new value, caches it and immediately emits it to subscribers. If this errors,
   * the error will <em>not</em> be relayed to subscribers of {@link #get()}
   */
  public Completable sync() {
    return fetch()
        .doOnNext(new Action1<Timestamped<T>>() {
          @Override public void call(Timestamped<T> tTimestamped) {
            updates.onNext(tTimestamped.getValue());
          }
        })
        .toCompletable();
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
