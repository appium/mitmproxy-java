package io.appium.mitmproxy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;

import java.net.URL;
import java.util.List;

@Data
public class InterceptedMessage {

    private final static ObjectMapper objectMapper = new ObjectMapper();

    /**
     * @deprecated use {@link #getRequest()}.{@link Request#getMethod()} instead
     */
    @JsonIgnore
    @Deprecated
    public String requestMethod;

    /**
     * @deprecated use {@link #getRequest()}.{@link Request#getUrl()} instead
     */
    @JsonIgnore
    @Deprecated
    public URL requestURL;

    /**
     * @deprecated use {@link #getRequest()}.{@link Request#getHeaders()} instead
     */
    @JsonIgnore
    @Deprecated
    public List<String[]> requestHeaders;

    /**
     * @deprecated use {@link #getResponse()}.{@link Response#getStatusCode()} instead
     */
    @JsonIgnore
    @Deprecated
    public int responseCode;

    /**
     * @deprecated use {@link #getResponse()}.{@link Response#getHeaders()} instead
     */
    @JsonIgnore
    @Deprecated
    public List<String[]> responseHeaders;

    /**
     * @deprecated use {@link #getRequest()}.{@link Request#getBody()} instead
     */
    @JsonIgnore
    @Deprecated
    private byte[] requestBody;

    /**
     * @deprecated use {@link #getResponse()}.{@link  Response#getBody()} instead
     */
    @JsonIgnore
    @Deprecated
    private byte[] responseBody;

    private Request request;

    private Response response;

    @Data
    static class Request {

        private String method;

        private String url;

        private List<String[]> headers;

        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        private byte[] body;
    }

    @Data
    public static class Response {

        @JsonProperty("status_code")
        private int statusCode;

        private List<String[]> headers;

        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        private byte[] body;
    }
}