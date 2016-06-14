package de.hannesstruss.rxcache;

import rx.Completable;
import rx.Observable;
import rx.Scheduler;
import rx.Single;
import rx.functions.Func1;
import rx.schedulers.Schedulers;
import rx.schedulers.Timestamped;
import rx.subjects.BehaviorSubject;

public final class RxCache<T> {
  private final long expiryMs;
  private final Observable<T> coldSource;
  private final Scheduler scheduler;

  private final BehaviorSubject<Observable<Timestamped<T>>> sources;

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
   * Creates a new {@code RxCache} that expires after {@code expiryMs} milliseconds.
   */
  @SuppressWarnings("unused")
  public RxCache(long expiryMs, Single<T> coldSource) {
    this(expiryMs, Schedulers.immediate(), coldSource);
  }

  /**
   * Creates a new {@code RxCache} that expires after {@code expiryMs} milliseconds. {@code timestampScheduler}
   * will be used to obtain the timestamp driving expiry.
   */
  public RxCache(long expiryMs, Scheduler timestampScheduler, Single<T> coldSource) {
    this.expiryMs = expiryMs;
    this.coldSource = coldSource.toObservable();
    this.scheduler = timestampScheduler;
    this.sources = BehaviorSubject.create(fetch());
    invalidate();
  }

  /**
   * Get a stream of values which can error if the cache is empty/stale and fetching the value errors.
   */
  public Observable<T> get() {
    return Observable.switchOnNext(sources)
        .flatMap(new Func1<Timestamped<T>, Observable<Timestamped<T>>>() {
          @Override public Observable<Timestamped<T>> call(Timestamped<T> tTimestamped) {
            if (isFresh.call(tTimestamped)) {
              return Observable.just(tTimestamped);
            } else {
              return next(true).ignoreElements();
            }
          }
        })
        .map(unwrap);
  }

  /**
   * Fetches a new value, caches it and immediately emits it to subscribers. If this errors,
   * the error will <em>not</em> be relayed to subscribers of {@link #get()}
   */
  public Completable sync() {
    return next(false).toCompletable();
  }

  public void invalidate() {
    // TODO
  }

  private Observable<Timestamped<T>> next(final boolean emitErrors) {
    final Observable<Timestamped<T>> fetch = fetch();
    sources.onNext(fetch.onErrorResumeNext(new Func1<Throwable, Observable<Timestamped<T>>>() {
      @Override public Observable<Timestamped<T>> call(Throwable throwable) {
        if (emitErrors) {
          return Observable.error(throwable);
        } else {
          return fetch();
        }
      }
    }));
    return fetch;
  }

  private Observable<Timestamped<T>> fetch() {
    return coldSource.timestamp(scheduler).cache();
  }
}
