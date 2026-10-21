package dev.universaladmin.modules.chat;

import dev.universaladmin.chat.StaffChat;
import dev.universaladmin.modules.chat.action.ChatActions;
import dev.universaladmin.network.NetworkService;
import dev.universaladmin.scheduler.TaskScheduler;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link StaffChat} for bridges (Discord): sends messages into the staff
 * chat like {@code /sc} does - here and on the other servers of a network -
 * and tells subscribers about every staff chat message, wherever it was
 * written.
 */
public final class StaffChatHub implements StaffChat {

    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final TaskScheduler scheduler;
    private final NetworkService network;
    private final Logger logger;
    private ChatActions.StaffMessage delivery;

    public StaffChatHub(TaskScheduler scheduler, NetworkService network, Logger logger) {
        this.scheduler = scheduler;
        this.network = network;
        this.logger = logger;
    }

    /** The action that shows messages to the staff on this server - set once while the module enables. */
    public void deliverWith(ChatActions.StaffMessage delivery) {
        this.delivery = delivery;
    }

    @Override
    public void send(String sender, String message, String via) {
        String text = message == null ? "" : message.trim();
        if (text.isEmpty() || delivery == null) {
            return;
        }
        delivery.deliver(sender, text, null, via);
        network.publish(ChatActions.StaffMessage.NETWORK_TOPIC,
                via == null ? Map.of("sender", sender, "message", text) : Map.of("sender", sender, "message", text, "via", via));
        fire(new Message(sender, text, null, via));
    }

    @Override
    public void subscribe(Listener listener) {
        listeners.add(listener);
    }

    @Override
    public void unsubscribe(Listener listener) {
        listeners.remove(listener);
    }

    /** Tells every subscriber, on the main thread. */
    public void fire(Message message) {
        if (listeners.isEmpty()) {
            return;
        }
        scheduler.runOnMainThread(() -> {
            for (Listener listener : listeners) {
                try {
                    listener.onMessage(message);
                } catch (RuntimeException e) {
                    logger.log(Level.WARNING, "A staff chat listener failed", e);
                }
            }
        });
    }
}
