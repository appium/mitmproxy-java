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
        InterceptedMessage incomingMessage = this.messageSerializer.deserializeMessage(rawInputMessage);

        InterceptedMessage modifiedMessage = interceptor.apply(incomingMessage);

        // if the supplied interceptor function does not return a message, assume no changes were intended and just
        // complete the request
        InterceptedMessage messageToSendBack = modifiedMessage;

        if (modifiedMessage == null) {
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
