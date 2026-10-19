package dev.universaladmin.network;

/** Receives messages from other servers - see {@link NetworkService#subscribe}. Called on the main thread. */
@FunctionalInterface
public interface NetworkListener {

    void onMessage(NetworkMessage message);
}
