package io.appium.mitmproxy;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.apache.http.HttpHost;
import org.junit.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static junit.framework.TestCase.assertTrue;

public class MitmproxyJavaTest {

    private static final String MITMDUMP_PATH = "/usr/local/bin/mitmdump";

    @Test
    public void ConstructorTest() throws InterruptedException, IOException, TimeoutException {
        MitmproxyJava proxy = new MitmproxyJava(MITMDUMP_PATH, (InterceptedMessage m) -> {
            System.out.println(m.requestURL.toString());
            return m;
        });
        proxy.start();
        System.out.println("advanced in test");
        proxy.stop();
    }

    @Test
    public void SimpleTest() throws InterruptedException, TimeoutException, IOException, URISyntaxException, UnirestException {
        List<InterceptedMessage> messages = new ArrayList<>();

        MitmproxyJava proxy = new MitmproxyJava(MITMDUMP_PATH, (InterceptedMessage m) -> {
            messages.add(m);
            return m;
        });
        proxy.start();

        Unirest.setProxy(new HttpHost("localhost", 8080));
        Unirest.get("http://appium.io").asString();

        proxy.stop();

        assertTrue(messages.size() > 0);

        InterceptedMessage appiumIORequest = messages.stream().filter((m) -> m.requestURL.getHost().equals("appium.io")).findFirst().get();

        assertTrue(appiumIORequest.responseCode == 200);
    }

    @Test
    public void NullInterceptorReturnTest() throws InterruptedException, TimeoutException, IOException, URISyntaxException, UnirestException {
        List<InterceptedMessage> messages = new ArrayList<>();

        MitmproxyJava proxy = new MitmproxyJava(MITMDUMP_PATH, (InterceptedMessage m) -> {
            messages.add(m);
            return null;
        });
        proxy.start();

        Unirest.setProxy(new HttpHost("localhost", 8080));
        Unirest.get("http://appium.io").asString();

        proxy.stop();

        assertTrue(messages.size() > 0);

        InterceptedMessage appiumIORequest = messages.stream().filter((m) -> m.requestURL.getHost().equals("appium.io")).findFirst().get();

        assertTrue(appiumIORequest.responseCode == 200);
    }

    @Test
    public void ResponseModificationTest() throws InterruptedException, TimeoutException, IOException, URISyntaxException, UnirestException {
        List<InterceptedMessage> messages = new ArrayList<>();

        MitmproxyJava proxy = new MitmproxyJava(MITMDUMP_PATH, (InterceptedMessage m) -> {
            messages.add(m);
            m.responseBody = "Hi from Test".getBytes(StandardCharsets.UTF_8);
            return m;
        });
        proxy.start();

        Unirest.setProxy(new HttpHost("localhost", 8080));
        HttpResponse<String> response = Unirest.get("http://appium.io").asString();

        assertTrue(response.getBody().equals("Hi from Test"));

        proxy.stop();

        assertTrue(messages.size() > 0);

        InterceptedMessage appiumIORequest = messages.stream().filter((m) -> m.requestURL.getHost().equals("appium.io")).findFirst().get();

        assertTrue(appiumIORequest.responseCode == 200);
    }
}