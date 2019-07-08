package io.appium.mitmproxy;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.apache.http.HttpHost;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

public class MitmproxyJavaTest {

    //private static final String MITMDUMP_PATH = "C:\\Python37\\Scripts\\mitmdump.exe";
    private static final String MITMDUMP_PATH = "/usr/local/bin/mitmdump";

    @Test
    public void ConstructorTest() throws InterruptedException, IOException, TimeoutException {
        MitmproxyJava proxy = new MitmproxyJava(MITMDUMP_PATH, (InterceptedMessage m) -> {
            System.out.println(m.getRequest().getUrl());
            return m;
        });
        proxy.start();
        System.out.println("advanced in test");
        proxy.stop();
    }

    @Test
    public void SimpleTest() throws InterruptedException, TimeoutException, IOException, UnirestException {
        List<InterceptedMessage> messages = new ArrayList<>();

        MitmproxyJava proxy = new MitmproxyJava(MITMDUMP_PATH, (InterceptedMessage m) -> {
            messages.add(m);
            return m;
        });
        proxy.start();

        Unirest.setProxy(new HttpHost("localhost", 8080));
        Unirest.get("http://appium.io").header("myTestHeader", "myTestValue").asString();

        proxy.stop();
        final InterceptedMessage firstMessage = messages.get(0);

        assertThat(firstMessage.getRequest().getUrl()).startsWith("http://appium.io");
        assertThat(firstMessage.getRequest().getHeaders()).containsOnlyOnce(new String[]{"myTestHeader", "myTestValue"});
        assertThat(firstMessage.getResponse().getStatusCode()).isEqualTo(200);
    }

    @Test
    public void NullInterceptorReturnTest() throws InterruptedException, TimeoutException, IOException, UnirestException {
        List<InterceptedMessage> messages = new ArrayList<>();

        MitmproxyJava proxy = new MitmproxyJava(MITMDUMP_PATH, (InterceptedMessage m) -> {
            messages.add(m);
            return null;
        }, 8087, null);
        proxy.start();

        Unirest.setProxy(new HttpHost("localhost", 8087));
        Unirest.get("http://appium.io").header("myTestHeader", "myTestValue").asString();

        proxy.stop();

        assertThat(messages).isNotEmpty();

        final InterceptedMessage firstMessage = messages.get(0);

        assertThat(firstMessage.getRequest().getUrl()).startsWith("http://appium.io");
        assertThat(firstMessage.getRequest().getHeaders()).containsOnlyOnce(new String[]{"myTestHeader", "myTestValue"});
        assertThat(firstMessage.getResponse().getStatusCode()).isEqualTo(200);
    }

    @Test
    public void ResponseModificationTest() throws InterruptedException, TimeoutException, IOException, UnirestException {
        List<InterceptedMessage> messages = new ArrayList<>();

        MitmproxyJava proxy = new MitmproxyJava(MITMDUMP_PATH, (InterceptedMessage m) -> {
            messages.add(m);
            m.getResponse().setBody("Hi from Test".getBytes(StandardCharsets.UTF_8));
            m.getResponse().getHeaders().add(new String[]{"myTestResponseHeader", "myTestResponseHeaderValue"});
            m.getResponse().setStatusCode(208);
            return m;
        });
        proxy.start();

        Unirest.setProxy(new HttpHost("localhost", 8080));
        HttpResponse<String> response = Unirest.get("http://appium.io").header("myTestHeader", "myTestValue").asString();
        proxy.stop();

        assertThat(response.getBody()).isEqualTo("Hi from Test");

        final InterceptedMessage firstMessage = messages.get(0);

        assertThat(firstMessage.getRequest().getUrl()).startsWith("http://appium.io");
        assertThat(firstMessage.getRequest().getHeaders()).containsOnlyOnce(new String[]{"myTestHeader", "myTestValue"});
        assertThat(firstMessage.getResponse().getHeaders()).containsOnlyOnce(new String[]{"myTestResponseHeader", "myTestResponseHeaderValue"});
        assertThat(firstMessage.getResponse().getStatusCode()).isEqualTo(208);
    }

    @Test
    public void shouldAddParametersToMitmdumpStart() throws IOException, TimeoutException, InterruptedException {
        List<String> mitmdumpParams = new ArrayList<>();
        mitmdumpParams.add("testParam");

        List<String> spiedParams = spy(mitmdumpParams);

        MitmproxyJava proxy = new MitmproxyJava(MITMDUMP_PATH, (InterceptedMessage m) -> {
            m.getResponse().setBody("Hi from Test".getBytes(StandardCharsets.UTF_8));
            m.getResponse().getHeaders().add(new String[]{"myTestResponseHeader", "myTestResponseHeaderValue"});
            m.getResponse().setStatusCode(208);
            return m;
        }, 8087, spiedParams);

        proxy.start();
        proxy.stop();

        //to verify that additional params were actually included to start path
        verify(spiedParams).toArray();

    }
}