package dev.universaladmin.chat;

/**
 * The staff chat ({@code /sc}), for bridges to other places such as a
 * Discord channel. Provided by the Chat module through the
 * {@link dev.universaladmin.core.ServiceRegistry}.
 *
 * @since 1.2
 */
public interface StaffChat {

    /**
     * Shows {@code message} to the staff - on this server and, in network
     * mode, on every server - as written by {@code sender} through
     * {@code via} (shown as e.g. {@code [Staff] [Discord] Nick: ...}). Safe to
     * call from any thread. Staff chat is conversation, not an admin action:
     * it is not audited.
     */
    void send(String sender, String message, String via);

    /** Gets every staff chat message from now on: written in game, on other servers, or through a bridge. */
    void subscribe(Listener listener);

    void unsubscribe(Listener listener);

    @FunctionalInterface
    interface Listener {

        /** Called on the main thread. */
        void onMessage(Message message);
    }

    /**
     * @param server where it was written; {@code null} for this server
     * @param via    the bridge it came through ("Discord"); {@code null} if written in game - a bridge skips
     *               its own messages by this
     */
    record Message(String sender, String text, String server, String via) {
    }
}
