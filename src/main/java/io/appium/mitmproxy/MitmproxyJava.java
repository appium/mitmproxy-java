package io.appium.mitmproxy;

import com.google.common.util.concurrent.Uninterruptibles;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;
import org.zeroturnaround.exec.stream.slf4j.Slf4jStream;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;

@Slf4j
public class MitmproxyJava {

  private static final String LOCALHOST_IP = "127.0.0.1";
  private static final int TIMEOUT_FOR_SOCKET_CHECKING_MINS = 5;

  private final String mitmproxyPath;

  private final Function<InterceptedMessage, InterceptedMessage> messageInterceptor;

  private final int proxyPort;

  // volatile: start() and stop() are frequently called from different threads
  // (e.g. a test's @Before/@After running on the test runner thread vs. a
  // teardown/shutdown-hook thread). Without volatile, one thread may never
  // observe the write the other thread made to these fields.
  private volatile MitmproxyServer server;

  private final List<String> extraMitmdumpParams;

  private volatile Future<ProcessResult> mitmproxyProcess;

  // Guards against concurrent/duplicate start() or stop() calls, and against
  // stop() being called before start() (or twice in a row) corrupting state.
  private final AtomicBoolean isRunning = new AtomicBoolean(false);

  public MitmproxyJava(String mitmproxyPath, Function<InterceptedMessage, InterceptedMessage> messageInterceptor, int proxyPort, List<String> extraMitmdumpParams) {
    this.mitmproxyPath = mitmproxyPath;
    this.messageInterceptor = messageInterceptor;
    this.proxyPort = proxyPort;
    this.extraMitmdumpParams = extraMitmdumpParams;
  }

  public MitmproxyJava(String mitmproxyPath, Function<InterceptedMessage, InterceptedMessage> messageInterceptor) {
    this(mitmproxyPath, messageInterceptor, 8080, null);
  }

  public synchronized MitmproxyJava start() throws IOException, TimeoutException {
    if (!isRunning.compareAndSet(false, true)) {
      throw new IllegalStateException("MitmproxyJava is already running; call stop() first");
    }

    log.info("Starting mitmproxy on port {}", proxyPort);

    try {
      // Each MitmproxyJava instance gets its OWN free port for the Java<->Python
      // control channel, instead of the old hardcoded 8765. Without this, two
      // instances running at the same time (e.g. parallel test execution) would
      // both try to bind the same port and the second one would fail with
      // "Address already in use".
      int websocketPort = findFreePort();

      server = new MitmproxyServer(new InetSocketAddress(LOCALHOST_IP, websocketPort), messageInterceptor);
      server.start();

      // python script file is zipped inside our jar. extract it into a temporary file.
      String pythonScriptPath = extractPythonScriptToFile();

      final List<String> mitmproxyStartParams = new ArrayList<>();
      mitmproxyStartParams.add(mitmproxyPath);
      mitmproxyStartParams.add("--anticache");
      mitmproxyStartParams.add("-p");
      mitmproxyStartParams.add(String.valueOf(proxyPort));
      mitmproxyStartParams.add("-s");
      mitmproxyStartParams.add(pythonScriptPath);
      // Tell proxy.py which port to connect back to for the control channel —
      // must match the port MitmproxyServer just bound to above.
      mitmproxyStartParams.add("--set");
      mitmproxyStartParams.add("wsPort=" + websocketPort);

      // adding params if needed for mitmproxy
      if (isNotEmpty(this.extraMitmdumpParams)) {
        mitmproxyStartParams.addAll(this.extraMitmdumpParams);
      }

      mitmproxyProcess = new ProcessExecutor()
          .command(mitmproxyStartParams)
          .redirectOutput(Slf4jStream.ofCaller().asInfo())
          .destroyOnExit()
          .start()
          .getFuture();

      waitForPortToBeInUse(proxyPort);
      log.info("Mitmproxy started on port {}", proxyPort);
      return this;
    } catch (IOException | TimeoutException | RuntimeException e) {
      // Roll back the "running" flag so a failed start() can be retried
      // instead of permanently wedging this instance in a started state.
      isRunning.set(false);
      throw e;
    }
  }

  /**
   * Asks the OS for a free ephemeral port by briefly binding a ServerSocket
   * to port 0, reading back the port it was assigned, then releasing it.
   * There's a small unavoidable window between releasing this socket and
   * MitmproxyServer binding to the same port where another process could
   * theoretically grab it first — but this is the standard, practical way
   * to get a free port in Java, and is a large improvement over the
   * previous hardcoded-for-the-whole-JVM port.
   */
  private static int findFreePort() throws IOException {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    }
  }

  private String extractPythonScriptToFile() throws IOException {
    File outfile = File.createTempFile("mitmproxy-python-plugin", ".py");

    try (
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream("scripts/proxy.py");
        FileOutputStream outputStream = new FileOutputStream(outfile)) {

      IOUtils.copy(inputStream, outputStream);
    }

    return outfile.getCanonicalPath();
  }

  public synchronized void stop() throws InterruptedException {
    if (!isRunning.compareAndSet(true, false)) {
      // Already stopped (or never started) — nothing to do. This makes
      // stop() safe to call from a finally block / teardown hook even if
      // start() failed or stop() was already called elsewhere. This is
      // an expected, supported case (that's the point of making stop()
      // idempotent), so INFO rather than WARN is the right severity —
      // it's not something the caller needs to investigate.
      log.info("stop() called but MitmproxyJava is not currently running; ignoring");
      return;
    }

    try {
      if (mitmproxyProcess != null) {
        mitmproxyProcess.cancel(true);
      }
      if (server != null) {
        server.stop(1000);
      }
      waitForPortToBeFree(proxyPort);
    } finally {
      server = null;
      mitmproxyProcess = null;
    }
  }

  @SneakyThrows
  private void waitForPortToBeFree(int port) {
    final LocalDateTime timeoutTime = LocalDateTime.now().plusMinutes(TIMEOUT_FOR_SOCKET_CHECKING_MINS);

    while (true) {
      if (timeoutTime.isBefore(LocalDateTime.now())) {
        throw new TimeoutException("Timed out waiting for mitmproxy to stop");
      }

      if (!isPortInUse(port)) {
        return;
      }

      Uninterruptibles.sleepUninterruptibly(100, TimeUnit.MILLISECONDS);
    }
  }

  private boolean isPortInUse(int port) {
    // A successful connection means something is still listening (port busy).
    // A connection failure (refused) means nothing's there (port free).
    try (Socket s = new Socket(LOCALHOST_IP, port)) {
      return true;
    } catch (IOException ioe) {
      return false;
    }
  }

  private void waitForPortToBeInUse(int port) throws TimeoutException {
    final LocalDateTime timeoutTime = LocalDateTime.now().plusMinutes(TIMEOUT_FOR_SOCKET_CHECKING_MINS);

    while (true) {
      if (timeoutTime.isBefore(LocalDateTime.now())) {
        throw new TimeoutException("Timed out waiting for mitmproxy to start");
      }

      if (isPortInUse(port)) {
        return;
      }

      Uninterruptibles.sleepUninterruptibly(100, TimeUnit.MILLISECONDS);
    }
  }
}