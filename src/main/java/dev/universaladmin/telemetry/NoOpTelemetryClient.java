package dev.universaladmin.telemetry;

/**
 * A {@link TelemetryClient} that sends nothing, anywhere - the default,
 * because UniversalAdmin has no official statistics endpoint yet.
 *
 * <p>This is not a placeholder that quietly posts to some other host: there
 * is no URL in this class, no fallback, and no "essential" second channel.
 * Until {@code telemetry.endpoint} names a real endpoint, the whole subsystem
 * is inert, and {@link #isConfigured()} says so out loud at startup.
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
