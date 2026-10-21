package dev.universaladmin.discord;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * Turns the chat replies of an {@code /admin} command into one Discord
 * reply. Commands answer asynchronously, often in several lines, so lines
 * are collected and the reply is (re)written shortly after the last one -
 * and once more for anything arriving later. If nothing comes at all, a
 * "no reply" text is shown.
 */
final class ReplyCollector implements Consumer<Component> {

    /** Discord's limit is 2000 characters; leave room for the code block. */
    static final int MAX_LENGTH = 1900;

    private static final long SETTLE_MILLIS = 700;
    private static final long NO_REPLY_MILLIS = 6000;

    private final ScheduledExecutorService timer;
    private final Consumer<String> output;
    private final String noReply;
    private final List<String> lines = new ArrayList<>();
    private boolean scheduled;

    /** @param output receives the full reply text each time it changes - e.g. editing the deferred interaction reply */
    ReplyCollector(ScheduledExecutorService timer, Consumer<String> output, String noReply) {
        this.timer = timer;
        this.output = output;
        this.noReply = noReply;
    }

    /** Starts the "no reply" fallback. */
    ReplyCollector start() {
        timer.schedule(() -> {
            synchronized (this) {
                if (!lines.isEmpty()) {
                    return;
                }
            }
            output.accept(noReply);
        }, NO_REPLY_MILLIS, TimeUnit.MILLISECONDS);
        return this;
    }

    @Override
    public void accept(Component component) {
        String text = PlainTextComponentSerializer.plainText().serialize(component).strip();
        if (text.isEmpty()) {
            return;
        }
        synchronized (this) {
            lines.add(text);
            if (scheduled) {
                return;
            }
            scheduled = true;
        }
        timer.schedule(this::flush, SETTLE_MILLIS, TimeUnit.MILLISECONDS);
    }

    private void flush() {
        String text;
        synchronized (this) {
            scheduled = false;
            text = render(lines);
        }
        output.accept(text);
    }

    /** The lines as one Discord message: without the chat prefix, in a code block so nothing is read as Markdown. */
    static String render(List<String> lines) {
        StringBuilder out = new StringBuilder();
        for (String line : lines) {
            String clean = line.startsWith("[UA] ") ? line.substring(5) : line;
            clean = clean.replace("```", "'''");
            if (out.length() + clean.length() + 1 > MAX_LENGTH) {
                out.append("…\n");
                break;
            }
            out.append(clean).append('\n');
        }
        return "```\n" + out + "```";
    }
}
