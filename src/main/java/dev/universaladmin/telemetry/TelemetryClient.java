package dev.universaladmin.telemetry;

/**
 * Delivers a {@link TelemetryPayload} somewhere. The seam that keeps
 * {@link TelemetryService} testable without a network, and the seam an
 * official backend endpoint plugs into later without touching any other
 * class.
 *
 * <p>Implementations are called from a background thread only (never the
 * Paper main thread - see docs/architecture/threading.md) and may block for
 * as long as their own timeout allows, but no longer.
 */
public interface TelemetryClient extends AutoCloseable {

    /**
     * Sends one heartbeat, throwing on any failure (unreachable host,
     * timeout, non-2xx response). The caller decides how loudly to react;
     * see {@link TelemetryService}, which swallows failures by design so a
     * backend outage can never affect the server.
     */
    void send(TelemetryPayload payload) throws Exception;

    /**
     * Whether this client can actually deliver anything. {@code false} for
     * {@link NoOpTelemetryClient}, which lets startup log the "nothing will
     * be sent" state once instead of pretending telemetry is live.
     */
    default boolean isConfigured() {
        return true;
    }

    @Override
    void close();
}
