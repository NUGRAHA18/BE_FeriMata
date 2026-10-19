package com.fertimata.backend.websocket;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Authenticates the STOMP {@code CONNECT} frame.
 *
 * <p>The HTTP handshake is left open because browsers cannot set an {@code Authorization} header on
 * a WebSocket upgrade. The token is therefore carried in the CONNECT frame instead, and this is
 * where it is verified - before any subscription is accepted.
 */
public class WebSocketAuthenticationInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(WebSocketAuthenticationInterceptor.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtDecoder jwtDecoder;
    private final WebSocketProperties properties;

    public WebSocketAuthenticationInterceptor(JwtDecoder jwtDecoder, WebSocketProperties properties) {
        this.jwtDecoder = jwtDecoder;
        this.properties = properties;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }

        String token = extractToken(accessor);
        if (token == null) {
            if (properties.requireAuthentication()) {
                log.warn("Rejected STOMP CONNECT without an access token");
                throw new BadCredentialsException("Missing access token on STOMP CONNECT");
            }
            return message;
        }

        try {
            Jwt jwt = jwtDecoder.decode(token);
            JwtAuthenticationToken authentication =
                    new JwtAuthenticationToken(jwt, new com.fertimata.backend.security.jwt.JwtRoleConverter()
                            .convert(jwt));
            accessor.setUser(authentication);
            log.debug("STOMP connection authenticated for {}", jwt.getSubject());
        } catch (JwtException ex) {
            log.warn("Rejected STOMP CONNECT with an invalid access token");
            throw new BadCredentialsException("Invalid access token on STOMP CONNECT");
        }
        return message;
    }

    private String extractToken(StompHeaderAccessor accessor) {
        List<String> values = accessor.getNativeHeader("Authorization");
        if (values == null || values.isEmpty()) {
            return null;
        }
        String raw = values.get(0);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.startsWith(BEARER_PREFIX) ? raw.substring(BEARER_PREFIX.length()).trim() : raw.trim();
    }
}
