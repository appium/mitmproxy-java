import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;
import org.zeroturnaround.exec.stream.slf4j.Slf4jStream;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URISyntaxException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

public class MitmproxyJava {

    private String mitmproxyPath;
    private MitmproxyServer server;
    private Future<ProcessResult> mitmproxyProcess;
    public static final int WEBSOCKET_PORT = 8765;

    public MitmproxyJava(String mitmproxyPath, Function<InterceptedMessage, InterceptedMessage> messageInterceptor) throws URISyntaxException {
        this.mitmproxyPath = mitmproxyPath;
        server = new MitmproxyServer(new InetSocketAddress("localhost", WEBSOCKET_PORT), messageInterceptor);
        server.start();
        System.out.println("you started me");
    }

    public void start() throws IOException, InterruptedException, ExecutionException, TimeoutException {
        System.out.println("starting mitmproxy on port 8080");
        mitmproxyProcess = new ProcessExecutor()
                .command(mitmproxyPath, "--anticache", "-s", "scripts/proxy.py")
                .redirectOutput(Slf4jStream.ofCaller().asInfo())
                .destroyOnExit()
                .start()
                .getFuture();

        waitForPortToBeInUse(8080);
        System.out.println("done waiting");

    }

    public void stop() throws IOException, InterruptedException {
        mitmproxyProcess.cancel(true);
        server.stop();
    }

    private void waitForPortToBeInUse(int port) throws TimeoutException {
        boolean inUse = false;
        Socket s = null;
        int tries = 0;
        int maxTries = 60 * 1000 / 100;

        while (!inUse) {
            try
            {
                s = new Socket("localhost", port);
            }
            catch (IOException e)
            {
                inUse = false;
            }
            finally
            {
                if(s != null) {
                    inUse = true;
                    try {
                        s.close();
                    } catch (Exception e) {
                    }
                    break;
                }
            }
            tries++;
            if (tries == maxTries) {
                throw new TimeoutException("Timed out waiting for mitmproxy to start");
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}

