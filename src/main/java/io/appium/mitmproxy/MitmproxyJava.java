package io.appium.mitmproxy;

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
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;

@Slf4j
public class MitmproxyJava {

    private static final String LOCALHOST_IP = "127.0.0.1";
    private static final int WEBSOCKET_PORT = 8765;

    private String mitmproxyPath;

    private Function<InterceptedMessage, InterceptedMessage> messageInterceptor;

    private int proxyPort;

    private MitmproxyServer server;

    private List<String> extraMitmdumpParams;

    private Future<ProcessResult> mitmproxyProcess;

    public MitmproxyJava(String mitmproxyPath, Function<InterceptedMessage, InterceptedMessage> messageInterceptor, int proxyPort, List<String> extraMitmdumpParams) {
        this.mitmproxyPath = mitmproxyPath;
        this.messageInterceptor = messageInterceptor;
        this.proxyPort = proxyPort;
        this.extraMitmdumpParams = extraMitmdumpParams;
    }

    public MitmproxyJava(String mitmproxyPath, Function<InterceptedMessage, InterceptedMessage> messageInterceptor) {
        this(mitmproxyPath, messageInterceptor, 8080, null);
    }

    public void start() throws IOException, TimeoutException {
        log.info("Starting mitmproxy on port {}", proxyPort);

        server = new MitmproxyServer(new InetSocketAddress(LOCALHOST_IP, WEBSOCKET_PORT), messageInterceptor);
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
        waitForPortToBeFree(proxyPort);
    }

    private void waitForPortToBeFree(int port) {

        while (true) {
            try (Socket s = new Socket(LOCALHOST_IP, port)) {
            } catch (IOException e) {
                return;
            }
        }
    }

    private void waitForPortToBeInUse(int port) throws TimeoutException {
        boolean inUse = false;
        int tries = 0;
        int maxTries = 60 * 1000 / 100;

        while (!inUse) {

            try (Socket s = new Socket(LOCALHOST_IP, port)) {
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

