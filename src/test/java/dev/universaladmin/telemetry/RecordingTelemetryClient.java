package dev.universaladmin.telemetry;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A {@link TelemetryClient} that records what it was asked to send instead of
 * touching the network. Every telemetry test uses this - no test in this
 * project ever performs a real HTTP request.
 */
final class RecordingTelemetryClient implements TelemetryClient {

    private final List<TelemetryPayload> sent = new CopyOnWriteArrayList<>();
    private final Exception failure;
    private boolean closed;

    RecordingTelemetryClient() {
        this(null);
    }

    /** @param failure thrown by every {@link #send} call, to exercise the failure path */
    RecordingTelemetryClient(Exception failure) {
        this.failure = failure;
    }

    @Override
    public void send(TelemetryPayload payload) throws Exception {
        sent.add(payload);
        if (failure != null) {
            throw failure;
        }
    }

    @Override
    public void close() {
        closed = true;
    }

    List<TelemetryPayload> sent() {
        return List.copyOf(sent);
    }

    boolean isClosed() {
        return closed;
    }
}
