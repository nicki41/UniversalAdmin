package dev.universaladmin.command;

import dev.universaladmin.action.Actor;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;

/**
 * Runs {@code /admin} commands for someone who is not on the server - a
 * Discord user through the Discord bot, later a web panel. The command goes
 * through exactly the same path as in game: the same permission checks
 * (against {@link Actor#permissions()}), the same actions, the same audit
 * entries with the actor's name. Commands that need a player in game
 * answer that they are for players only.
 *
 * <p>Provided by the core through the
 * {@link dev.universaladmin.core.ServiceRegistry}.
 *
 * @since 1.2
 */
public interface RemoteCommands {

    /**
     * Runs {@code /admin <commandLine>} (e.g. {@code "ban Steve 7d griefing"})
     * as {@code actor}. Safe to call from any thread. Every reply the command
     * sends goes to {@code replies} - possibly later and from other threads,
     * because most commands answer once their action has finished.
     */
    void run(Actor actor, String commandLine, Consumer<Component> replies);

    /** Tab completion for {@code commandLine} as {@code actor} - the candidates for its last word. */
    CompletableFuture<List<String>> complete(Actor actor, String commandLine);
}
