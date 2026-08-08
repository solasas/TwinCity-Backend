package com.sashank.DigitalTwinBackend.realtime;

import com.sashank.DigitalTwinBackend.gtfs.Vehicle;
import java.nio.charset.StandardCharsets;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Subscribes to {@link VehicleUpdatesChannel#NAME} and forwards each change to {@link VehicleUpdatePublisher}. */
@Component
public class VehicleUpdateRedisListener implements MessageListener {

    private final VehicleUpdatePublisher publisher;
    private final ObjectMapper objectMapper;

    public VehicleUpdateRedisListener(VehicleUpdatePublisher publisher, ObjectMapper objectMapper) {
        this.publisher = publisher;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String json = new String(message.getBody(), StandardCharsets.UTF_8);
        publisher.publish(objectMapper.readValue(json, Vehicle.class));
    }
}