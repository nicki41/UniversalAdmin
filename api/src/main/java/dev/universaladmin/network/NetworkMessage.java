package dev.universaladmin.network;

import java.time.Instant;
import java.util.Map;

/**
 * A message from another server - see {@link NetworkService#publish}.
 *
 * @param source the server that sent it
 */
public record NetworkMessage(String topic, String source, Map<String, String> payload, Instant sentAt) {

    public NetworkMessage {
        payload = Map.copyOf(payload);
    }

    /** {@code payload.get(key)}, or {@code fallback} if absent. */
    public String get(String key, String fallback) {
        return payload.getOrDefault(key, fallback);
    }
}
