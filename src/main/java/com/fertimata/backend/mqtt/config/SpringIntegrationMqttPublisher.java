package com.fertimata.backend.mqtt.config;

import com.fertimata.backend.common.exception.MessagePublishException;
import com.fertimata.backend.mqtt.MqttPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.integration.mqtt.event.MqttConnectionFailedEvent;
import org.springframework.integration.mqtt.event.MqttSubscribedEvent;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * Publishes through the Spring Integration outbound channel.
 *
 * <p>The rest of the backend only sees {@link MqttPublisher}; the Paho client, the channel and the
 * header names stop here.
 */
@Component
@ConditionalOnProperty(prefix = "app.mqtt", name = "enabled", havingValue = "true")
public class SpringIntegrationMqttPublisher implements MqttPublisher {

    private static final Logger log = LoggerFactory.getLogger(SpringIntegrationMqttPublisher.class);

    private final MessageChannel outboundChannel;
    private volatile boolean connected;

    public SpringIntegrationMqttPublisher(MessageChannel mqttOutboundChannel) {
        this.outboundChannel = mqttOutboundChannel;
    }

    @Override
    public void publish(String topic, String payload) {
        try {
            boolean accepted = outboundChannel.send(
                    MessageBuilder.withPayload(payload).setHeader(MqttHeaders.TOPIC, topic).build());
            if (!accepted) {
                throw new MessagePublishException("The MQTT outbound channel rejected the message for " + topic);
            }
            log.debug("Published to {}", topic);
        } catch (MessagePublishException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            // Includes broker-unreachable failures raised by the Paho handler.
            throw new MessagePublishException("Could not publish to MQTT topic " + topic, ex);
        }
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public String transportName() {
        return "MQTT";
    }

    @EventListener
    public void onSubscribed(MqttSubscribedEvent event) {
        connected = true;
        log.info("MQTT connection established: {}", event.getMessage());
    }

    @EventListener
    public void onConnectionFailed(MqttConnectionFailedEvent event) {
        connected = false;
        log.error("MQTT connection failed: {}", event.getCause() == null ? "unknown cause" : event.getCause()
                .getMessage());
    }
}
