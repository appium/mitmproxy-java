package io.appium.mitmproxy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;

import java.io.IOException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class MessageSerializer {

    private final static ObjectMapper objectMapper = new ObjectMapper();
    private static final int START_BYTES = 8;

    @SneakyThrows(IOException.class)
    public InterceptedMessage deserializeMessage(ByteBuffer buffer) {
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        int metadataSize = buffer.getInt();
        int request_content_size = buffer.getInt();
        int response_content_size = buffer.getInt();

        byte[] metadataBytes = new byte[metadataSize];
        buffer.get(metadataBytes);

        byte[] requestBody = new byte[request_content_size];
        buffer.get(requestBody);

        byte[] responseBody = new byte[response_content_size];
        buffer.get(responseBody);

        InterceptedMessage interceptedMessage = objectMapper.readValue(metadataBytes, InterceptedMessage.class);
        interceptedMessage.getRequest().setBody(requestBody);
        interceptedMessage.getResponse().setBody(responseBody);

        return interceptedMessage;
    }

    public ByteBuffer serializeMessage(InterceptedMessage message) throws JsonProcessingException {
        byte[] responseBody = message.getResponse().getBody();

        int contentLength = responseBody.length;

        byte[] metadata = objectMapper.writeValueAsBytes(message.getResponse());
        int metadataLength = metadata.length;

        ByteBuffer buffer = ByteBuffer.allocate(START_BYTES + metadataLength + contentLength);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(metadataLength);
        buffer.putInt(contentLength);
        buffer.put(metadata);
        buffer.put(message.getResponse().getBody());

        return (ByteBuffer) buffer.rewind();
    }
}
