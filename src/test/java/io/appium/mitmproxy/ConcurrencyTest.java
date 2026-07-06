package io.appium.mitmproxy;

import kong.unirest.core.Unirest;
import org.junit.After;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests specifically exercising the thread-safety fixes made to
 * {@link MitmproxyJava} and {@link MitmproxyServer}:
 *
 * 1. Many requests in flight at once should all be captured correctly by a
 *    thread-safe interceptor, with no lost/corrupted messages.
 * 2. A racing start() from multiple threads should let exactly one caller
 *    succeed and everyone else should fail fast with IllegalStateException,
 *    rather than double-starting the websocket server / mitmdump process.
 * 3. An interceptor that throws should not hang the in-flight HTTP request —
 *    the request should still complete (unmodified) instead of timing out.
 * 4. stop() without a successful start() should be a safe no-op, not an NPE.
 *
 * Requires mitmdump to be resolvable — see MitmproxyJavaTest.getMITMDumpPath().
 */
public class ConcurrencyTest {

  private MitmproxyJava proxy;

  @After
  public void tearDown() throws InterruptedException {
    if (proxy != null) {
      proxy.stop(); // safe no-op now even if start() never succeeded
    }
    // Unirest 4's Config locks itself once the underlying HTTP client is
    // built (i.e. after the first request), so the *next* test's call to
    // Unirest.config().proxy(...) would throw UnirestConfigException
    // without this reset(). reset() also clears default headers, proxy,
    // etc., so there's no separate clearDefaultHeaders() call needed.
    Unirest.config().reset();
  }

  @Test
  public void concurrentRequestsAreAllCapturedWithoutLossOrCorruption() throws Exception {
    int requestCount = 25;
    int port = 8090;

    // The collection under test: this MUST be thread-safe, since the
    // interceptor is invoked concurrently once more than one request is
    // in flight. This is exactly the pattern from ThreadSafeMessageCollector.
    List<InterceptedMessage> messages = new CopyOnWriteArrayList<>();

    proxy = new MitmproxyJava(MitmproxyJavaTest.getMITMDumpPath(), m -> {
      messages.add(m);
      return m;
    }, port, null);
    proxy.start();

    Unirest.config().proxy("localhost", port);

    ExecutorService pool = Executors.newFixedThreadPool(10);
    try {
      List<Future<?>> futures = IntStream.range(0, requestCount)
          .mapToObj(i -> pool.submit(() -> {
            try {
              Unirest.get("http://appium.io")
                  .header("requestIndex", String.valueOf(i))
                  .asString();
            } catch (Exception e) {
              throw new RuntimeException(e);
            }
          }))
          .collect(Collectors.toList());

      for (Future<?> f : futures) {
        f.get(15, TimeUnit.SECONDS); // fail the test if any request hangs
      }
    } finally {
      pool.shutdown();
    }

    proxy.stop();

    // No lost or duplicated messages despite concurrent writers.
    assertThat(messages).hasSize(requestCount);

    // Every request index shows up exactly once — if the underlying list
    // were a plain ArrayList, concurrent adds could silently drop entries
    // or throw ConcurrentModificationException before we even get here.
    List<String> seenIndices = messages.stream()
        .map(m -> headerValue(m, "requestIndex"))
        .collect(Collectors.toList());
    List<String> expectedIndices = IntStream.range(0, requestCount)
        .mapToObj(String::valueOf)
        .collect(Collectors.toList());
    assertThat(seenIndices).containsExactlyInAnyOrderElementsOf(expectedIndices);
  }

  @Test
  public void onlyOneConcurrentStartCallSucceeds() throws Exception {
    int port = 8091;
    proxy = new MitmproxyJava(MitmproxyJavaTest.getMITMDumpPath(), m -> m, port, null);

    int threadCount = 5;
    CountDownLatch readyLatch = new CountDownLatch(threadCount);
    CountDownLatch goLatch = new CountDownLatch(1);
    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger failureCount = new AtomicInteger(0);

    ExecutorService pool = Executors.newFixedThreadPool(threadCount);
    try {
      for (int i = 0; i < threadCount; i++) {
        pool.submit(() -> {
          readyLatch.countDown();
          try {
            goLatch.await(); // line every thread up to hit start() at once
            proxy.start();
            successCount.incrementAndGet();
          } catch (IllegalStateException e) {
            failureCount.incrementAndGet();
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
      }

      readyLatch.await(5, TimeUnit.SECONDS);
      goLatch.countDown(); // release all threads simultaneously

      pool.shutdown();
      assertThat(pool.awaitTermination(15, TimeUnit.SECONDS)).isTrue();
    } finally {
      if (!pool.isShutdown()) {
        pool.shutdownNow();
      }
    }

    // synchronized + AtomicBoolean guard means exactly one thread wins the race.
    assertThat(successCount.get()).isEqualTo(1);
    assertThat(failureCount.get()).isEqualTo(threadCount - 1);

    // and the proxy is actually usable after the race settles
    Unirest.config().proxy("localhost", port);
    assertThat(Unirest.get("http://appium.io").asString().getStatus()).isEqualTo(200);
  }

  @Test
  public void interceptorExceptionDoesNotHangTheRequest() throws Exception {
    int port = 8092;

    proxy = new MitmproxyJava(MitmproxyJavaTest.getMITMDumpPath(), m -> {
      throw new RuntimeException("boom - simulated broken interceptor");
    }, port, null);
    proxy.start();

    Unirest.config().proxy("localhost", port);

    // Before the try/catch fix in MitmproxyServer.onMessage, this would
    // hang indefinitely because the connection thread died with an
    // uncaught exception and never sent a response back to mitmdump.
    ExecutorService pool = Executors.newSingleThreadExecutor();
    try {
      Future<Integer> future = pool.submit(() ->
          Unirest.get("http://appium.io").asString().getStatus());

      Integer status = future.get(10, TimeUnit.SECONDS); // throws TimeoutException if it hangs
      assertThat(status).isEqualTo(200);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  public void stopWithoutStartIsASafeNoOp() throws InterruptedException {
    proxy = new MitmproxyJava(MitmproxyJavaTest.getMITMDumpPath(), m -> m, 8093, null);
    // start() deliberately not called
    proxy.stop(); // should not throw
  }

  @Test
  public void stopCanBeCalledMultipleTimesSafely() throws Exception {
    proxy = new MitmproxyJava(MitmproxyJavaTest.getMITMDumpPath(), m -> m, 8094, null);
    proxy.start();
    proxy.stop();
    proxy.stop(); // second call should be a no-op, not throw
  }

  @Test
  public void startAfterStopWorksAgain() throws Exception {
    proxy = new MitmproxyJava(MitmproxyJavaTest.getMITMDumpPath(), m -> m, 8095, null);
    proxy.start();
    proxy.stop();

    // instance should be reusable, not permanently wedged
    proxy.start();
    Unirest.config().proxy("localhost", 8095);
    assertThat(Unirest.get("http://appium.io").asString().getStatus()).isEqualTo(200);
  }

  @Test
  public void startTwiceWithoutStopThrows() throws Exception {
    proxy = new MitmproxyJava(MitmproxyJavaTest.getMITMDumpPath(), m -> m, 8096, null);
    proxy.start();

    assertThatThrownBy(() -> proxy.start())
        .isInstanceOf(IllegalStateException.class);
  }

  private static String headerValue(InterceptedMessage message, String headerName) {
    return message.getRequest().getHeaders().stream()
        .filter(h -> h.length == 2 && headerName.equalsIgnoreCase(h[0]))
        .map(h -> h[1])
        .findFirst()
        .orElse(null);
  }
}