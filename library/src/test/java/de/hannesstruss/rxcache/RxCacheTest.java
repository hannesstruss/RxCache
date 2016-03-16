package de.hannesstruss.rxcache;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Func0;
import rx.schedulers.TestScheduler;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class RxCacheTest {
  private static final long EXPIRY = 1000;

  @Mock
  Action1<Long> action;
  @Mock Action1<Throwable> errorAction;
  @Mock
  Action0 completeAction;

  private Func0<Observable<Long>> producer;
  private TestScheduler scheduler;
  private RxCache<Long> cache;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);

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
  }

  @Test
  public void shouldReturnCachedValues() {
    final long firstValue = scheduler.now();

    cache.get().subscribe(action);
    verify(action).call(firstValue);

    scheduler.advanceTimeBy(500, TimeUnit.MILLISECONDS);

    Mockito.reset(action);
    cache.get().subscribe(action);
    verify(action).call(firstValue);
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

    cache.get().subscribe(action);
    verify(action).call(firstValue);

    // advance over expiry, expect refreshed cache
    scheduler.advanceTimeBy(1500, TimeUnit.MILLISECONDS);

    Mockito.reset(action);
    cache.get().subscribe(action);
    verify(action).call(firstValue + 1500);

    // advance within expiration period, expect same cached data as before
    scheduler.advanceTimeBy(300, TimeUnit.MILLISECONDS);

    Mockito.reset(action);
    cache.get().subscribe(action);
    verify(action).call(firstValue + 1500);
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
    cache.get().subscribe(action);
    scheduler.advanceTimeBy(500, TimeUnit.MILLISECONDS);
    assertThat(countingProducer.get()).isEqualTo(1);

    // Wait for cache to expire
    scheduler.advanceTimeBy(EXPIRY * 2, TimeUnit.MILLISECONDS);

    // Fetch new data
    cache.get().subscribe(action);

    // Subscribe second time while request is still running
    scheduler.advanceTimeBy(250, TimeUnit.MILLISECONDS);
    cache.get().subscribe(action);

    // Wait for all requests to finish
    scheduler.advanceTimeBy(500, TimeUnit.MILLISECONDS);

    // Should have made one request for initial warm up and one for the two subscriptions later
    assertThat(countingProducer.get()).isEqualTo(2);
  }

  @Test public void shouldComplete() {
    cache.get().subscribe(action, errorAction, completeAction);
    verify(completeAction).call();
  }

  @Test public void shouldPropagateErrors() {
    final Exception e = new RuntimeException("Test");
    producer = new Func0<Observable<Long>>() {
      @Override public Observable<Long> call() {
        return Observable.error(e);
      }
    };

    cache.get().subscribe(action, errorAction);

    verifyNoMoreInteractions(action);
    verify(errorAction).call(e);
  }

  @Test public void shouldNotCacheErrors() {
    final Exception e = new RuntimeException("Test");
    producer = new Func0<Observable<Long>>() {
      @Override public Observable<Long> call() {
        return Observable.error(e);
      }
    };

    cache.get().subscribe(action, errorAction);
    verify(errorAction).call(e);

    producer = new Func0<Observable<Long>>() {
      @Override public Observable<Long> call() {
        return Observable.just(2355L);
      }
    };

    cache.get().subscribe(action);
    verify(action).call(2355L);
  }
}
