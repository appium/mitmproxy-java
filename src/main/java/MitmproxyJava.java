import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.function.Function;

public class MitmproxyJava {

    private MitmproxyServer server;
    public static final int WEBSOCKET_PORT = 8765;

    public MitmproxyJava(Function<InterceptedMessage, InterceptedMessage> messageInterceptor) throws URISyntaxException {
        server = new MitmproxyServer(new InetSocketAddress("localhost", WEBSOCKET_PORT), messageInterceptor);
        server.run();
        System.out.println("you started me");
    }

    public void stop() throws IOException, InterruptedException {
        server.stop();
    }
}

