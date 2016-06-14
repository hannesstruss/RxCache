package de.hannesstruss.rxcache;

import org.junit.Before;
import org.junit.Test;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Func0;
import rx.observers.TestSubscriber;
import rx.schedulers.TestScheduler;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static com.google.common.truth.Truth.assertThat;

@SuppressWarnings("unchecked")
public class RxCacheTest {
  private static final long EXPIRY = 1000;

  private Func0<Observable<Long>> producer;
  private TestScheduler scheduler;
  private RxCache<Long> cache;
  private TestSubscriber<Long> subscriber;

  @Before
  public void setUp() {
    scheduler = new TestScheduler();
    producer = new Func0<Observable<Long>>() {
      @Override public Observable<Long> call() {
        return Observable.just(scheduler.now());
      }
    };

    cache = new RxCache<>(EXPIRY, scheduler, Observable.defer(
        new Func0<Observable<Long>>() {
          @Override public Observable<Long> call() {
            return producer.call();
          }
        }));

    subscriber = new TestSubscriber<>();
  }

  @Test
  public void shouldReturnCachedValues() {
    final long firstValue = scheduler.now();

    cache.get().subscribe(subscriber);
    subscriber.assertValues(firstValue);

    scheduler.advanceTimeBy(500, TimeUnit.MILLISECONDS);

    TestSubscriber<Long> subscriber2 = new TestSubscriber<>();
    cache.get().subscribe(subscriber2);
    subscriber2.assertValues(firstValue);
  }

  @Test public void shouldReturnCachedValuesWithAsyncScheduler() {
    final AtomicLong results = new AtomicLong(0);
    cache = new RxCache<>(500, Observable.defer(new Func0<Observable<Long>>() {
      @Override public Observable<Long> call() {
        return Observable.just(results.getAndIncrement());
      }
    }));

    long first = cache.get().toBlocking().first();
    long second = cache.get().toBlocking().first();
    long third = cache.get().toBlocking().first();

    assertThat(second).isEqualTo(first);
    assertThat(third).isEqualTo(first);
  }

  @Test public void shouldRefreshCacheWhenItExpires() {
    final long firstValue = scheduler.now();

    cache.get().subscribe(subscriber);
    subscriber.assertValues(firstValue);

    // advance over expiry, expect refreshed cache
    scheduler.advanceTimeBy(1500, TimeUnit.MILLISECONDS);

    TestSubscriber<Long> subscriber2 = new TestSubscriber<>();
    cache.get().subscribe(subscriber2);
    subscriber2.assertValues(firstValue + 1500);

    // advance within expiration period, expect same cached data as before
    scheduler.advanceTimeBy(300, TimeUnit.MILLISECONDS);

    TestSubscriber<Long> subscriber3 = new TestSubscriber<>();
    cache.get().subscribe(subscriber3);
    subscriber3.assertValues(firstValue + 1500);
  }

  @Test public void shouldAvoidCacheStampede() {
    final AtomicLong countingProducer = new AtomicLong(0);

    Observable<Long> o = Observable.create(new Observable.OnSubscribe<Long>() {
      @Override public void call(Subscriber<? super Long> subscriber) {
        subscriber.onNext(countingProducer.getAndIncrement());
        subscriber.onCompleted();
      }
    });

    cache = new RxCache<>(EXPIRY, scheduler, o.delay(500, TimeUnit.MILLISECONDS, scheduler));

    // Warm up cache
    cache.get().subscribe();
    scheduler.advanceTimeBy(500, TimeUnit.MILLISECONDS);
    assertThat(countingProducer.get()).isEqualTo(1);

    // Wait for cache to expire
    scheduler.advanceTimeBy(EXPIRY * 2, TimeUnit.MILLISECONDS);

    // Fetch new data
    cache.get().subscribe();

    // Subscribe second time while request is still running
    scheduler.advanceTimeBy(250, TimeUnit.MILLISECONDS);
    cache.get().subscribe();

    // Wait for all requests to finish
    scheduler.advanceTimeBy(500, TimeUnit.MILLISECONDS);

    // Should have made one request for initial warm up and one for the two subscriptions later
    assertThat(countingProducer.get()).isEqualTo(2);
  }

  @Test public void shouldComplete() {
    cache.get().subscribe(subscriber);
    subscriber.assertCompleted();
  }

  @Test public void shouldPropagateErrors() {
    final Exception e = new RuntimeException("Test");
    producer = new Func0<Observable<Long>>() {
      @Override public Observable<Long> call() {
        return Observable.error(e);
      }
    };

    cache.get().subscribe(subscriber);

    subscriber.assertError(e);
    subscriber.assertNoValues();
  }

  @Test public void shouldNotCacheErrors() {
    final Exception e = new RuntimeException("Test");
    producer = new Func0<Observable<Long>>() {
      @Override public Observable<Long> call() {
        return Observable.error(e);
      }
    };

    cache.get().subscribe(subscriber);
    subscriber.assertError(e);

    producer = new Func0<Observable<Long>>() {
      @Override public Observable<Long> call() {
        return Observable.just(2355L);
      }
    };

    TestSubscriber<Long> subscriber2 = new TestSubscriber<>();
    cache.get().subscribe(subscriber2);
    subscriber2.assertValues(2355L);
  }

  @Test public void shouldSyncAndEmit() {
    long firstValue = scheduler.now();
    cache.get().subscribe(subscriber);
    subscriber.assertValueCount(1);
    subscriber.assertNoTerminalEvent();

    scheduler.advanceTimeBy(EXPIRY, TimeUnit.MILLISECONDS);

    cache.sync().subscribe();
    subscriber.assertValues(firstValue, scheduler.now());
  }

  @Test public void failedSyncShouldNotAffectSubscribers() {
    long firstValue = scheduler.now();
    final AtomicBoolean fail = new AtomicBoolean(false);
    producer = new Func0<Observable<Long>>() {
      @Override public Observable<Long> call() {
        if (fail.get()) {
          return Observable.error(new RuntimeException("FAIL"));
        }
        return Observable.just(scheduler.now());
      }
    };

    cache.get().subscribe(subscriber);

    fail.set(true);
    TestSubscriber<Long> syncSubscriber = new TestSubscriber<>();
    cache.sync().subscribe(syncSubscriber);

    scheduler.advanceTimeBy(EXPIRY, TimeUnit.MILLISECONDS);
    fail.set(false);
    TestSubscriber<Long> syncSubscriber2 = new TestSubscriber<>();
    cache.sync().subscribe(syncSubscriber2);

    syncSubscriber.assertError(RuntimeException.class);
    syncSubscriber2.assertValues(firstValue + EXPIRY);
    syncSubscriber2.assertCompleted();
    subscriber.assertNoTerminalEvent();
    subscriber.assertValues(firstValue);
  }

  @Test public void syncResultShouldBeCached() {
    long firstValue = scheduler.now();
    cache.get().subscribe(subscriber);
    scheduler.advanceTimeBy(EXPIRY, TimeUnit.MILLISECONDS);

    cache.sync().subscribe();
    scheduler.advanceTimeBy(500, TimeUnit.MILLISECONDS);
    TestSubscriber<Long> subscriber2 = new TestSubscriber<>();
    cache.get().subscribe(subscriber2);
    subscriber2.assertValues(firstValue + EXPIRY);
  }
}
