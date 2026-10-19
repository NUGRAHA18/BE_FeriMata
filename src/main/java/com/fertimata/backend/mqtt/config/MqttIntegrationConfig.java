package com.fertimata.backend.mqtt.config;

import com.fertimata.backend.mqtt.MqttInboundDispatcher;
import com.fertimata.backend.mqtt.MqttProperties;
import com.fertimata.backend.mqtt.MqttTopicResolver;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.mqtt.inbound.Mqttv5PahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.outbound.Mqttv5PahoMessageHandler;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;

/**
 * Wires the real MQTT client.
 *
 * <p>Only created when {@code app.mqtt.enabled} is true. With it off the application starts with the
 * loopback transport instead, which is what makes the backend runnable with no broker and no
 * hardware present.
 *
 * <p>Note what this class does <em>not</em> do: it contains no topic strings (they come from
 * {@link MqttTopicResolver}) and no business logic (inbound messages go straight to
 * {@link MqttInboundDispatcher}).
 */
@Configuration
@ConditionalOnProperty(prefix = "app.mqtt", name = "enabled", havingValue = "true")
public class MqttIntegrationConfig {

    private static final Logger log = LoggerFactory.getLogger(MqttIntegrationConfig.class);

    public static final String OUTBOUND_CHANNEL = "mqttOutboundChannel";
    public static final String INBOUND_CHANNEL = "mqttInboundChannel";

    @Bean
    public MqttConnectionOptions mqttConnectionOptions(MqttProperties properties) {
        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setServerURIs(new String[] {properties.brokerUrl()});
        options.setAutomaticReconnect(properties.automaticReconnect());
        options.setCleanStart(properties.cleanStart());
        if (properties.keepAlive() != null) {
            options.setKeepAliveInterval((int) properties.keepAlive().toSeconds());
        }
        if (properties.connectionTimeout() != null) {
            options.setConnectionTimeout((int) properties.connectionTimeout().toSeconds());
        }
        if (properties.username() != null && !properties.username().isBlank()) {
            options.setUserName(properties.username());
        }
        if (properties.password() != null && !properties.password().isEmpty()) {
            options.setPassword(properties.password().getBytes(StandardCharsets.UTF_8));
        }
        // Credentials are never logged, here or anywhere else.
        log.info("MQTT enabled, broker {} (clientId {})", properties.brokerUrl(), properties.clientId());
        return options;
    }

    @Bean(OUTBOUND_CHANNEL)
    public MessageChannel mqttOutboundChannel() {
        return new DirectChannel();
    }

    @Bean(INBOUND_CHANNEL)
    public MessageChannel mqttInboundChannel() {
        return new DirectChannel();
    }

    @Bean
    @ServiceActivator(inputChannel = OUTBOUND_CHANNEL)
    public MessageHandler mqttOutboundHandler(MqttConnectionOptions options, MqttProperties properties) {
        Mqttv5PahoMessageHandler handler =
                new Mqttv5PahoMessageHandler(options, properties.clientId() + "-out");
        handler.setAsync(false); // surface a publish failure to the caller instead of swallowing it
        handler.setDefaultQos(properties.qos());
        return handler;
    }

    @Bean
    public Mqttv5PahoMessageDrivenChannelAdapter mqttInboundAdapter(
            MqttConnectionOptions options, MqttProperties properties, MqttTopicResolver topicResolver) {

        List<String> subscriptions = topicResolver.inboundSubscriptions();
        if (subscriptions.isEmpty()) {
            throw new IllegalStateException(
                    "MQTT is enabled but no inbound topic templates are configured under app.mqtt.topics");
        }

        Mqttv5PahoMessageDrivenChannelAdapter adapter = new Mqttv5PahoMessageDrivenChannelAdapter(
                options, properties.clientId() + "-in", subscriptions.toArray(String[]::new));
        adapter.setOutputChannelName(INBOUND_CHANNEL);
        adapter.setPayloadType(String.class);
        if (properties.completionTimeout() != null) {
            adapter.setCompletionTimeout(properties.completionTimeout().toMillis());
        }
        int[] qos = new int[subscriptions.size()];
        java.util.Arrays.fill(qos, properties.qos());
        adapter.setQos(qos);

        log.info("Subscribing to MQTT topics {}", subscriptions);
        return adapter;
    }

    /**
     * The only bridge between the messaging library and the application.
     *
     * <p>It extracts the topic and the body and hands both to the dispatcher - no parsing, no
     * persistence, no business rules.
     */
    @Bean
    @ServiceActivator(inputChannel = INBOUND_CHANNEL)
    public MessageHandler mqttInboundHandler(MqttInboundDispatcher dispatcher) {
        return message -> {
            String topic = (String) message.getHeaders().get(MqttHeaders.RECEIVED_TOPIC);
            Object payload = message.getPayload();
            dispatcher.dispatch(topic, payload == null ? null : payload.toString());
        };
    }
}
