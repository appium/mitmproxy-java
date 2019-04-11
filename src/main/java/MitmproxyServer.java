import com.fasterxml.jackson.core.JsonProcessingException;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;

public class MitmproxyServer extends WebSocketServer {

    private Function<InterceptedMessage, InterceptedMessage> interceptor;

    public MitmproxyServer(InetSocketAddress address, Function<InterceptedMessage, InterceptedMessage> interceptor) {
        super(address);
        this.interceptor = interceptor;
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
    //    conn.send("Welcome to the server!"); //This method sends a message to the new client
    //    broadcast( "new connection: " + handshake.getResourceDescriptor() ); //This method sends a message to all clients connected
        System.out.println("new connection to " + conn.getRemoteSocketAddress());
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        System.out.println("closed " + conn.getRemoteSocketAddress() + " with exit code " + code + " additional info: " + reason);
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        System.out.println("received message from "	+ conn.getRemoteSocketAddress() + ": " + message);
    }

    @Override
    public void onMessage( WebSocket conn, ByteBuffer message ) {
        System.out.println("received ByteBuffer from "	+ conn.getRemoteSocketAddress());
        InterceptedMessage intercepted = null;
        InterceptedMessage modifiedMessage = null;

        try {
            intercepted = new InterceptedMessage(message);
        } catch (IOException e) {
            System.out.println("Could not parse message");
            e.printStackTrace();
        }
        System.out.println(intercepted.requestURL.toString());

        modifiedMessage = interceptor.apply(intercepted);

        // if the supplied interceptor function does not return a message, assume no changes were intended and just
        // complete the request
        if (modifiedMessage == null) {
            modifiedMessage = intercepted;
        }

        try {
            conn.send(modifiedMessage.serializedResponseToMitmproxy());
        } catch (JsonProcessingException e) {
            System.out.println("Could not encode response to mitmproxy");
            e.printStackTrace();
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        System.err.println("an error occured on connection " + conn.getRemoteSocketAddress()  + ":" + ex);
    }

    @Override
    public void onStart() {
        System.out.println("server started successfully");
    }
}
