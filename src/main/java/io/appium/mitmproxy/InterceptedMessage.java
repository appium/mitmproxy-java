package io.appium.mitmproxy;

import java.io.IOException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class InterceptedMessage {

    public byte[] requestBody;
    public byte[] responseBody;

    public String requestMethod;
    public URL requestURL;
    public List<String[]> requestHeaders;
    public int responseCode;
    public List<String[]> responseHeaders;

    private final static ObjectMapper objectMapper = new ObjectMapper();

    public InterceptedMessage(ByteBuffer buffer) throws IOException {
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        int metadataSize = buffer.getInt();
        int request_content_size = buffer.getInt();
        int response_content_size = buffer.getInt();

        byte[] metadataBytes = new byte[metadataSize];
        buffer.get(metadataBytes);

        requestBody = new byte[request_content_size];
        buffer.get(requestBody);

        responseBody = new byte[response_content_size];
        buffer.get(responseBody);

        JsonNode metadata = objectMapper.readTree(metadataBytes);
        requestMethod = metadata.get("request").get("method").asText();
        requestURL = new URL(metadata.get("request").get("url").asText());
        JsonNode headers = metadata.get("request").get("headers");
        requestHeaders = new ArrayList<>();
        for (JsonNode headerNode : headers) {
            String[] headerArray = new String[2];
            headerArray[0] = headerNode.get(0).asText();
            headerArray[1] = headerNode.get(1).asText();
            requestHeaders.add(headerArray);
        }

        responseCode = metadata.get("response").get("status_code").asInt();
        headers = metadata.get("response").get("headers");
        responseHeaders = new ArrayList<>();
        for (JsonNode headerNode : headers) {
            String[] headerArray = new String[2];
            headerArray[0] = headerNode.get(0).asText();
            headerArray[1] = headerNode.get(1).asText();
            responseHeaders.add(headerArray);
        }
    }

    public ByteBuffer serializedResponseToMitmproxy() throws JsonProcessingException {
        int contentLength = responseBody.length;

        // create JSON for metadata. Which is the responseCode and responseHeaders.
        // while we're at it, set the Content-Length header
        ObjectNode metadataRoot = objectMapper.createObjectNode();
        metadataRoot.put("status_code", responseCode);

        ArrayNode headersNode = objectMapper.createArrayNode();
        List<ArrayNode> headerNodes = responseHeaders.stream().map((h) -> {
            ArrayNode headerPair = objectMapper.createArrayNode();
            headerPair.add(h[0]);
            if (h[0].equals("content-length")) {
                headerPair.add(Integer.toString(contentLength));
            } else {
                headerPair.add(h[1]);
            }
            return headerPair;
        }).collect(Collectors.toList());
        headersNode.addAll(headerNodes);
        metadataRoot.set("headers", headersNode);

        byte[] metadata = objectMapper.writeValueAsBytes(metadataRoot);
        int metadataLength = metadata.length;

        ByteBuffer buffer = ByteBuffer.allocate(8 + metadataLength + contentLength);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(metadataLength);
        buffer.putInt(contentLength);
        buffer.put(metadata);
        buffer.put(responseBody);

        return (ByteBuffer) buffer.rewind();
    }
}