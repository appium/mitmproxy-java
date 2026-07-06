package io.appium.mitmproxy;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.function.Function;

/**
 * NOTE ON THREAD SAFETY:
 * {@code java_websocket}'s {@link WebSocketServer} dispatches each connection's
 * messages on its own thread, and mitmproxy can hold multiple concurrent
 * connections open (one per in-flight HTTP transaction being proxied). That
 * means {@link #onMessage(WebSocket, ByteBuffer)} — and therefore the
 * user-supplied {@code interceptor} function — can be invoked concurrently
 * from multiple threads at the same time.
 * <p>
 * {@link MessageSerializer} and {@link InterceptedMessage} are stateless /
 * per-call, so they're safe to share across those threads as-is. The
 * remaining responsibility is on the caller: if your interceptor reads or
 * writes shared state (e.g. collecting messages into a list), that state
 * must be a thread-safe structure (e.g. {@code CopyOnWriteArrayList},
 * {@code Collections.synchronizedList}, or a {@code ConcurrentLinkedQueue}) —
 * a plain {@code ArrayList} will corrupt under concurrent access.
 */
public class MitmproxyServer extends WebSocketServer {

  private static final Logger LOGGER = LoggerFactory.getLogger(WebSocketServer.class);

  private final Function<InterceptedMessage, InterceptedMessage> interceptor;

  private final MessageSerializer messageSerializer;

  MitmproxyServer(InetSocketAddress address, Function<InterceptedMessage, InterceptedMessage> interceptor) {
    super(address);
    this.interceptor = interceptor;
    this.messageSerializer = new MessageSerializer();
  }

  @Override
  public void onOpen(WebSocket conn, ClientHandshake handshake) {
    LOGGER.debug("new connection to websocket server" + conn.getRemoteSocketAddress());
  }

  @Override
  public void onClose(WebSocket conn, int code, String reason, boolean remote) {
    LOGGER.debug("closed " + conn.getRemoteSocketAddress() + " with exit code " + code + " additional info: " + reason);
  }

  @Override
  public void onMessage(WebSocket conn, String message) {
    LOGGER.debug("received message from " + conn.getRemoteSocketAddress() + " : " + message);
  }

  @Override
  public void onMessage(WebSocket conn, ByteBuffer rawInputMessage) {
    // NOTE: this method runs concurrently on a per-connection thread — see
    // the class-level Javadoc. deserializeMessage/serializeMessage are
    // stateless so they're safe to call from multiple threads here; the
    // interceptor call below is the boundary into user code.
    InterceptedMessage incomingMessage = this.messageSerializer.deserializeMessage(rawInputMessage);

    InterceptedMessage messageToSendBack;
    try {
      InterceptedMessage modifiedMessage = interceptor.apply(incomingMessage);

      // if the supplied interceptor function does not return a message, assume no changes were intended and
      // just complete the request
      messageToSendBack = (modifiedMessage != null) ? modifiedMessage : incomingMessage;
    } catch (RuntimeException e) {
      // Without this, an exception thrown by user code (e.g. a
      // non-thread-safe collection blowing up under concurrent access)
      // propagates up through the websocket library's thread and the
      // in-flight HTTP request mitmproxy is waiting on hangs forever
      // with no indication of why. Log it and pass the request through
      // unmodified instead of leaving the caller hanging.
      LOGGER.error("interceptor threw an exception while processing " + incomingMessage.getRequest().getUrl()
          + "; passing request through unmodified", e);
      messageToSendBack = incomingMessage;
    }

    try {
      conn.send(this.messageSerializer.serializeMessage(messageToSendBack));
    } catch (JsonProcessingException e) {
      LOGGER.error(e.getMessage());
    }
  }

  @Override
  public void onError(WebSocket conn, Exception ex) {
    LOGGER.error("an error occured on connection " + conn.getRemoteSocketAddress() + ":" + ex);
  }

  @Override
  public void onStart() {
    LOGGER.info("websocket server started successfully");
  }
}