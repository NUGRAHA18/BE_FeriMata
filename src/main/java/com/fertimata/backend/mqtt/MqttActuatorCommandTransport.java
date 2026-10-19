package com.fertimata.backend.mqtt;

import com.fertimata.backend.actuator.domain.ActuatorCommandTransport;
import com.fertimata.backend.actuator.domain.OutboundCommand;
import com.fertimata.backend.common.exception.MessagePublishException;
import com.fertimata.backend.common.util.JsonSupport;
import com.fertimata.backend.mqtt.payload.ActuatorCommandPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * MQTT adapter for the actuator command port.
 *
 * <p>This is the seam between the domain and the messaging world: it is the only class that knows a
 * command becomes JSON on a topic. {@code ActuatorCommandService} knows none of that.
 */
@Component
public class MqttActuatorCommandTransport implements ActuatorCommandTransport {

    private static final Logger log = LoggerFactory.getLogger(MqttActuatorCommandTransport.class);

    private final MqttPublisher publisher;
    private final MqttTopicResolver topicResolver;
    private final JsonSupport jsonSupport;

    public MqttActuatorCommandTransport(
            MqttPublisher publisher, MqttTopicResolver topicResolver, JsonSupport jsonSupport) {
        this.publisher = publisher;
        this.topicResolver = topicResolver;
        this.jsonSupport = jsonSupport;
    }

    @Override
    public void send(OutboundCommand command) {
        String topic;
        String payload;
        try {
            topic = topicResolver.topicFor(TopicKind.COMMAND, command.deviceCode());
            payload = jsonSupport.toJson(new ActuatorCommandPayload(
                    command.commandUid(),
                    command.deviceCode(),
                    command.actuatorCode(),
                    command.commandType(),
                    command.parameters(),
                    command.issuedAt(),
                    command.expiresAt()));
        } catch (RuntimeException ex) {
            throw new MessagePublishException("Could not build the outbound command message", ex);
        }

        publisher.publish(topic, payload);
        log.info("Command {} published to {}", command.commandUid(), topic);
    }

    @Override
    public boolean isAvailable() {
        return publisher.isConnected();
    }

    @Override
    public String describe() {
        return publisher.transportName();
    }
}
