package io.appium.mitmproxy;

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
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

public class MitmproxyJava {

    private String mitmproxyPath;
    private Function<InterceptedMessage, InterceptedMessage> messageInterceptor;
    private int proxyPort;
    private MitmproxyServer server;
    private Future<ProcessResult> mitmproxyProcess;
    public static final int WEBSOCKET_PORT = 8765;

    public MitmproxyJava(String mitmproxyPath, Function<InterceptedMessage, InterceptedMessage> messageInterceptor, int proxyPort) {
        this.mitmproxyPath = mitmproxyPath;
        this.messageInterceptor = messageInterceptor;
        this.proxyPort = proxyPort;
    }

    public MitmproxyJava(String mitmproxyPath, Function<InterceptedMessage, InterceptedMessage> messageInterceptor) {
        this(mitmproxyPath, messageInterceptor, 8080);
    }

    public void start() throws IOException, TimeoutException {
        System.out.println("starting mitmproxy on port " + proxyPort);

        server = new MitmproxyServer(new InetSocketAddress("localhost", WEBSOCKET_PORT), messageInterceptor);
        server.start();

        // python script file is zipped inside our jar. extract it into a temporary file.
        String pythonScriptPath = extractPythonScriptToFile();

        mitmproxyProcess = new ProcessExecutor()
                .command(mitmproxyPath, "--anticache", "-s", pythonScriptPath)
                .redirectOutput(Slf4jStream.ofCaller().asInfo())
                .destroyOnExit()
                .start()
                .getFuture();

        waitForPortToBeInUse(proxyPort);
        System.out.println("mitmproxy started on port " + proxyPort);

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

    public void stop() throws InterruptedException {
        if (mitmproxyProcess != null) {
            mitmproxyProcess.cancel(true);
        }
        server.stop(1000);
        Thread.sleep(200); // this pains me. but it seems that it takes a moment for the server to actually relinquish the port it uses.
    }

    private void waitForPortToBeInUse(int port) throws TimeoutException {
        boolean inUse = false;
        int tries = 0;
        int maxTries = 60 * 1000 / 100;

        while (!inUse) {

            try (Socket s = new Socket("localhost", port)) {
                break;
            } catch (IOException e) {
                inUse = false;
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

