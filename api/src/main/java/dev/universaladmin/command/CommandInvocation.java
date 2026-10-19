package dev.universaladmin.command;

import dev.universaladmin.action.ActionContext;
import dev.universaladmin.action.ActionExecutor;
import dev.universaladmin.action.ActionId;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.action.Actor;
import dev.universaladmin.action.Source;
import dev.universaladmin.core.PlayerDirectory;
import dev.universaladmin.core.PlayerNames;
import dev.universaladmin.core.PlayerRef;
import dev.universaladmin.core.UniversalAdminProvider;
import dev.universaladmin.localization.ComponentMessages;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.localization.MessageService;
import dev.universaladmin.localization.ResultMessages;
import dev.universaladmin.permission.PermissionNode;
import dev.universaladmin.scheduler.TaskScheduler;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * One execution (or tab completion) of an {@link AdminCommand}: who ran it,
 * what they typed, and the helpers every command needs - replying through
 * {@link MessageService}, running an action through {@link ActionExecutor}
 * and reporting its outcome uniformly.
 *
 * <p>{@link #args()} are the arguments <i>after</i> the subcommand name:
 * for {@code /admin kick Steve griefing} they are {@code [Steve, griefing]},
 * exactly as for the shortcut {@code /kick Steve griefing}.
 */
public final class CommandInvocation {

    private final CommandSender sender;
    private final Actor actor;
    private final String label;
    private final List<String> args;
    private final CommandEnvironment environment;

    public CommandInvocation(CommandSender sender, Actor actor, String label, List<String> args, CommandEnvironment environment) {
        this.sender = sender;
        this.actor = actor;
        this.label = label;
        this.args = List.copyOf(args);
        this.environment = environment;
    }

    public CommandSender sender() {
        return sender;
    }

    /** The executing player, if the sender is one. */
    public Optional<Player> player() {
        return sender instanceof Player player ? Optional.of(player) : Optional.empty();
    }

    public Actor actor() {
        return actor;
    }

    public ActionContext actionContext() {
        return new ActionContext(actor, Source.COMMAND);
    }

    /** What the sender typed before the arguments, without the slash - {@code admin kick} or {@code kick}. */
    public String label() {
        return label;
    }

    public List<String> args() {
        return args;
    }

    public int argCount() {
        return args.size();
    }

    /** The argument at {@code index}, or empty if there are fewer arguments. */
    public Optional<String> arg(int index) {
        return index < args.size() ? Optional.of(args.get(index)) : Optional.empty();
    }

    /** Every argument from {@code fromIndex} on, joined with single spaces; empty if there are none. */
    public String joinArgs(int fromIndex) {
        return fromIndex >= args.size() ? "" : String.join(" ", args.subList(fromIndex, args.size()));
    }

    /** The argument currently being completed (the last one), or {@code ""}. */
    public String currentArg() {
        return args.isEmpty() ? "" : args.get(args.size() - 1);
    }

    /**
     * Resolves the player named at {@code index} and runs {@code then} with
     * them on the main thread: someone online here, online on another server
     * of the network, or anyone who ever joined a server sharing this
     * database (see {@link PlayerDirectory}). Replies "no player named ..."
     * if there is none. Prefer this over {@link #knownPlayer} - it also finds
     * players who only ever played on another server.
     */
    public void withPlayer(int index, Consumer<PlayerRef> then) {
        String name = args.get(index);
        Optional<PlayerRef> known = Arguments.knownPlayer(name);
        if (known.isPresent()) {
            PlayerNames.remember(known.get().id(), known.get().name());
            then.accept(known.get());
            return;
        }
        Optional<PlayerDirectory> directory = UniversalAdminProvider.find()
                .flatMap(platform -> platform.services().get(PlayerDirectory.class));
        if (directory.isEmpty()) {
            reply(CommandMessages.PLAYER_NOT_FOUND, name);
            return;
        }
        directory.get().findByName(name).whenComplete((found, error) -> environment.scheduler().runOnMainThread(() -> {
            if (error != null || found.isEmpty()) {
                reply(CommandMessages.PLAYER_NOT_FOUND, name);
            } else {
                PlayerNames.remember(found.get().id(), found.get().name());
                then.accept(found.get());
            }
        }));
    }

    /**
     * The player argument {@code index} names, online or a known offline
     * player; replies "player not found" and returns empty otherwise. Callers
     * check {@link #argCount()} (and reply {@link #usage}) first.
     */
    public Optional<PlayerRef> knownPlayer(int index) {
        String name = args.get(index);
        Optional<PlayerRef> player = Arguments.knownPlayer(name);
        if (player.isEmpty()) {
            reply(CommandMessages.PLAYER_NOT_FOUND, name);
        }
        return player;
    }

    /** The online player argument {@code index} names; replies "not online" and returns empty otherwise. */
    public Optional<Player> onlinePlayer(int index) {
        String name = args.get(index);
        Optional<Player> player = Arguments.onlinePlayer(name)
                .filter(found -> !(sender instanceof Player self) || self.canSee(found));
        if (player.isEmpty()) {
            reply(CommandMessages.PLAYER_NOT_ONLINE, name);
        }
        return player;
    }

    /**
     * For an optional {@code [player]} argument: the online player named by
     * argument {@code index}, or the sender if it is omitted and the sender
     * is a player. A console sender omitting it gets {@code command}'s usage.
     */
    public Optional<Player> onlinePlayerOrSelf(int index, AdminCommand command) {
        if (argCount() > index) {
            return onlinePlayer(index);
        }
        if (sender instanceof Player self) {
            return Optional.of(self);
        }
        usage(command);
        return Optional.empty();
    }

    /** A duration argument such as {@code 7d}; replies "invalid duration" and returns empty otherwise. */
    public Optional<Duration> duration(int index) {
        String raw = args.get(index);
        Optional<Duration> parsed = Arguments.duration(raw);
        if (parsed.isEmpty()) {
            reply(CommandMessages.INVALID_DURATION, raw);
        }
        return parsed;
    }

    /** A whole-number argument; replies "invalid number" and returns empty otherwise. */
    public Optional<Integer> integer(int index) {
        String raw = args.get(index);
        OptionalInt parsed = Arguments.integer(raw);
        if (parsed.isEmpty()) {
            reply(CommandMessages.INVALID_NUMBER, raw);
            return Optional.empty();
        }
        return Optional.of(parsed.getAsInt());
    }

    /** A decimal argument; replies "invalid number" and returns empty otherwise. */
    public Optional<Double> decimal(int index) {
        String raw = args.get(index);
        OptionalDouble parsed = Arguments.decimal(raw);
        if (parsed.isEmpty()) {
            reply(CommandMessages.INVALID_NUMBER, raw);
            return Optional.empty();
        }
        return Optional.of(parsed.getAsDouble());
    }

    /**
     * Whether the last argument is the literal {@code confirm} - required by
     * commands that cannot be undone. Replies the "repeat with confirm" hint
     * when it is missing.
     */
    public boolean confirmed() {
        if (!args.isEmpty() && args.get(args.size() - 1).equalsIgnoreCase("confirm")) {
            return true;
        }
        reply(CommandMessages.CONFIRM_REQUIRED);
        return false;
    }

    public boolean hasPermission(PermissionNode node) {
        return node == null || actor.hasPermission(node);
    }

    public MessageService messages() {
        return environment.messages();
    }

    public CommandEnvironment environment() {
        return environment;
    }

    /** Sends {@code key} with the chat prefix. Safe to call from any thread. */
    public void reply(MessageKey key, Object... args) {
        send(ComponentMessages.render(environment.messages().get(CommandMessages.PREFIX)
                + environment.messages().get(key, args)));
    }

    public void reply(String key, Object... args) {
        reply(MessageKey.of(key), args);
    }

    /** Sends {@code key} without the chat prefix - for the lines of a list or report. Safe from any thread. */
    public void line(MessageKey key, Object... args) {
        send(ComponentMessages.render(environment.messages().get(key, args)));
    }

    public void line(String key, Object... args) {
        line(MessageKey.of(key), args);
    }

    /** Sends an already-built component. Safe to call from any thread. */
    public void send(Component component) {
        // Right away when we already are on the main thread: an RCON client
        // only sees what is sent while its command is still running.
        if (Bukkit.isPrimaryThread()) {
            sender.sendMessage(component);
        } else {
            environment.scheduler().runOnMainThread(() -> sender.sendMessage(component));
        }
    }

    /** Replies with "Usage: /label {@code command}'s synopsis". */
    public void usage(AdminCommand command) {
        String synopsis = environment.messages().get(command.usage());
        reply(CommandMessages.USAGE, "/" + label + (synopsis.isBlank() ? "" : " " + synopsis));
    }

    /** Runs an action through {@link ActionExecutor} as this invocation's actor, from the command source. */
    public <I, R> CompletableFuture<ActionResult<R>> execute(ActionId id, I input) {
        return environment.actionExecutor().execute(id, actionContext(), input);
    }

    /**
     * Runs an action and reports the outcome: {@code onSuccess} (on the main
     * thread) for a success, {@link #reportFailure} for anything else.
     */
    public <I, R> void run(ActionId id, I input, Consumer<R> onSuccess) {
        this.<I, R>execute(id, input).whenComplete((result, error) -> environment.scheduler().runOnMainThread(() -> {
            if (error != null) {
                environment.logger().log(Level.SEVERE, "Command /" + label + " failed unexpectedly", error);
                reply(CommandMessages.INTERNAL_ERROR);
                return;
            }
            switch (result) {
                case ActionResult.Success<R> success -> onSuccess.accept(success.value());
                case ActionResult.Failure<R> failure -> reportFailure(failure);
            }
        }));
    }

    /** Like {@link #run(ActionId, Object, Consumer)}, replying {@code successKey}/{@code successArgs} on success. */
    public <I> void run(ActionId id, I input, MessageKey successKey, Object... successArgs) {
        this.<I, Object>run(id, input, ignored -> reply(successKey, successArgs));
    }

    /**
     * Runs an action and replies with what the action reports it did (see
     * {@link ActionResult#success(Object, MessageKey, Object...)}) - or
     * {@link CommandMessages#DONE} if it reports nothing - on success, and
     * {@link #reportFailure} otherwise.
     */
    public <I> void run(ActionId id, I input) {
        this.<I, Object>execute(id, input).whenComplete((result, error) -> environment.scheduler().runOnMainThread(() -> {
            if (error != null) {
                environment.logger().log(Level.SEVERE, "Command /" + label + " failed unexpectedly", error);
                reply(CommandMessages.INTERNAL_ERROR);
                return;
            }
            report(result);
        }));
    }

    /** Replies with {@code result}'s text: what the action did ({@link CommandMessages#DONE} if it says nothing), or why it failed. */
    public void report(ActionResult<?> result) {
        switch (result) {
            case ActionResult.Success<?> success -> replyText(ResultMessages.success(environment.messages(), success, CommandMessages.DONE));
            case ActionResult.Failure<?> failure -> reportFailure(failure);
        }
    }

    /** Renders an action failure the same way for every command. */
    public void reportFailure(ActionResult.Failure<?> failure) {
        replyText(ResultMessages.failure(environment.messages(), failure));
    }

    /** Sends already-resolved text with the chat prefix. */
    private void replyText(String text) {
        send(ComponentMessages.render(environment.messages().get(CommandMessages.PREFIX) + text));
    }

    /**
     * What a command needs from the platform, bundled so one value can be
     * threaded through instead of four - the command-layer equivalent of
     * {@link dev.universaladmin.gui.GuiFramework}.
     */
    public record CommandEnvironment(
            MessageService messages, ActionExecutor actionExecutor, TaskScheduler scheduler, Logger logger) {
    }
}
