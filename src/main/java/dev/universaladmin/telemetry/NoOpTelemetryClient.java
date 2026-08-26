package dev.universaladmin.telemetry;

/**
 * A {@link TelemetryClient} that sends nothing, anywhere - installed when
 * {@code telemetry.enabled: false}. The whole subsystem is inert then: no
 * installation id, no HTTP client, no timer, and {@link #isConfigured()}
 * says so out loud at startup.
 */
public final class NoOpTelemetryClient implements TelemetryClient {

    @Override
    public void send(TelemetryPayload payload) {
        // Intentionally empty.
    }

    @Override
    public boolean isConfigured() {
        return false;
    }

    @Override
    public void close() {
        // Nothing to release.
    }
}
