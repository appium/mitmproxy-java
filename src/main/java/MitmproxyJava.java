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

    public MitmproxyJava(Function<InterceptedMessage, Boolean> messageInterceptor) throws URISyntaxException {
//        InterceptedMessage message = new InterceptedMessage();
//        message.message = "hi from message";
//        messageInterceptor.apply(message);

        server = new MitmproxyServer(new InetSocketAddress("localhost", WEBSOCKET_PORT));
        server.run();
        System.out.println("you started me");
    }

    public void stop() throws IOException, InterruptedException {
        server.stop();
    }
}

