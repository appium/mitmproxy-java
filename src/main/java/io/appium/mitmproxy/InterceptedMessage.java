package io.appium.mitmproxy;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;

import java.util.List;

@Data
public class InterceptedMessage {

    private final static ObjectMapper objectMapper = new ObjectMapper();

    private Request request;

    private Response response;

    @Data
    public static class Request {

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