import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;
import org.zeroturnaround.exec.stream.slf4j.Slf4jStream;

import java.io.*;
import java.net.*;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

public class MitmproxyJava {

    private String mitmproxyPath;
    private MitmproxyServer server;
    private Future<ProcessResult> mitmproxyProcess;
    public static final int WEBSOCKET_PORT = 8765;

    public MitmproxyJava(String mitmproxyPath, Function<InterceptedMessage, InterceptedMessage> messageInterceptor) {
        this.mitmproxyPath = mitmproxyPath;
        server = new MitmproxyServer(new InetSocketAddress("localhost", WEBSOCKET_PORT), messageInterceptor);
        server.start();
    }

    public void start() throws IOException, TimeoutException, URISyntaxException {
        System.out.println("starting mitmproxy on port 8080");

        // python script file is zipped inside our jar. extract it into a temporary file.
        String pythonScriptPath = extractPythonScriptToFile();

        mitmproxyProcess = new ProcessExecutor()
                .command(mitmproxyPath, "--anticache", "-s", pythonScriptPath)
                .redirectOutput(Slf4jStream.ofCaller().asInfo())
                .destroyOnExit()
                .start()
                .getFuture();

        waitForPortToBeInUse(8080);
        System.out.println("mitmproxy started on port 8080");

    }

    private String extractPythonScriptToFile() throws URISyntaxException, IOException {
        URL resource = MitmproxyJava.class.getResource("scripts/proxy.py");
        URI pythonScriptUri = resource.toURI();

        File infile = new File(pythonScriptUri);
        File outfile = File.createTempFile("mitmproxy-python-plugin", ".py");

        FileInputStream instream = new FileInputStream(infile);
        FileOutputStream outstream = new FileOutputStream(outfile);

        byte[] buffer = new byte[1024];

        int length;
        /*copying the contents from input stream to
         * output stream using read and write methods
         */
        while ((length = instream.read(buffer)) > 0){
            outstream.write(buffer, 0, length);
        }

        //Closing the input/output file streams
        instream.close();
        outstream.close();

        return outfile.getCanonicalPath();
    }

    public void stop() throws IOException, InterruptedException {
        if (mitmproxyProcess != null) {
            mitmproxyProcess.cancel(true);
        }
        server.stop(1000);
        Thread.sleep(200); // this pains me. but it seems that it takes a moment for the server to actually relinquish the port it uses.
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

