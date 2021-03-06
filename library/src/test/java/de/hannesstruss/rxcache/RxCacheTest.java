package de.hannesstruss.rxcache;

import org.junit.Before;
import org.junit.Test;
import rx.Single;
import rx.SingleSubscriber;
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

  private Func0<Single<Long>> producer;
  private TestScheduler scheduler;
  private RxCache<Long> cache;
  private TestSubscriber<Long> subscriber;
  private AtomicBoolean fetchFails;
  private Exception fetchError;

  @Before
  public void setUp() {
    fetchFails = new AtomicBoolean(false);
    fetchError = new RuntimeException("Fail!");

    scheduler = new TestScheduler();
    producer = new Func0<Single<Long>>() {
      @Override public Single<Long> call() {
        if (fetchFails.get()) {
          return Single.error(fetchError);
        } else {
          return Single.just(scheduler.now());
        }
      }
    };

    cache = new RxCache<>(EXPIRY, scheduler, Single.defer(
        new Func0<Single<Long>>() {
          @Override public Single<Long> call() {
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

    Single<Long> o = Single.create(new Single.OnSubscribe<Long>() {
      @Override public void call(SingleSubscriber<? super Long> singleSubscriber) {
        singleSubscriber.onSuccess(countingProducer.getAndIncrement());
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

  @Test public void shouldPropagateErrors() {
    fetchFails.set(true);

    cache.get().subscribe(subscriber);

    subscriber.assertError(fetchError);
    subscriber.assertNoValues();
  }

  @Test public void shouldNotCacheErrors() {
    fetchFails.set(true);

    cache.get().subscribe(subscriber);
    subscriber.assertError(fetchError);

    producer = new Func0<Single<Long>>() {
      @Override public Single<Long> call() {
        return Single.just(2355L);
      }
    };

    TestSubscriber<Long> subscriber2 = new TestSubscriber<>();
    cache.get().subscribe(subscriber2);
    subscriber2.assertNoTerminalEvent();
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

    cache.get().subscribe(subscriber);

    fetchFails.set(true);
    TestSubscriber<Long> syncSubscriber = new TestSubscriber<>();
    cache.sync().subscribe(syncSubscriber);

    scheduler.advanceTimeBy(EXPIRY, TimeUnit.MILLISECONDS);
    fetchFails.set(false);
    TestSubscriber<Long> syncSubscriber2 = new TestSubscriber<>();
    cache.sync().subscribe(syncSubscriber2);

    syncSubscriber.assertError(fetchError);
    syncSubscriber2.assertCompleted();
    subscriber.assertNoTerminalEvent();
    subscriber.assertValues(firstValue, firstValue + EXPIRY);
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

  @Test public void updatesShouldNotError() {
    fetchFails.set(true);

    cache.updates().subscribe(subscriber);

    scheduler.advanceTimeBy(500, TimeUnit.MILLISECONDS);
    long ts = scheduler.now();

    fetchFails.set(false);
    cache.sync().subscribe();

    subscriber.assertNoErrors();
    subscriber.assertValues(ts);
  }
}
