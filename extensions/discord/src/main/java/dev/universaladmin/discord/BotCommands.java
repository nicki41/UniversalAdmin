package dev.universaladmin.discord;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * The bot's slash commands and how each one becomes an {@code /admin}
 * command line - {@code /ban player:Steve duration:7d reason:griefing} runs
 * {@code /admin ban Steve 7d griefing} as the Discord member, through
 * {@link dev.universaladmin.command.RemoteCommands}. Option values are
 * checked first, so a "player" can never smuggle extra arguments in.
 */
final class BotCommands {

    static final String PLAYER = "player";
    static final String DURATION = "duration";
    static final String REASON = "reason";
    static final String COMMAND = "command";

    private static final Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}|[0-9a-fA-F-]{32,36}");
    private static final Pattern DURATION_TEXT = Pattern.compile("(?i)(\\d+[smhdwy])+|perm(anent)?");

    /** One slash command: its name, the {@code /admin} subcommand and which options it takes. */
    record Definition(String name, String adminCommand, List<Option> options) {
    }

    /** @param autocomplete players are completed from the server */
    record Option(String name, boolean required, boolean autocomplete) {
    }

    static final List<Definition> ALL = List.of(
            new Definition("ban", "ban", List.of(player(), optional(DURATION), optional(REASON))),
            new Definition("mute", "mute", List.of(player(), optional(DURATION), optional(REASON))),
            new Definition("kick", "kick", List.of(player(), optional(REASON))),
            new Definition("warn", "warn", List.of(player(), new Option(REASON, true, false))),
            new Definition("unban", "unban", List.of(player())),
            new Definition("unmute", "unmute", List.of(player())),
            new Definition("history", "history", List.of(player())),
            new Definition("check", "check", List.of(player())),
            new Definition("find", "find", List.of(player())),
            new Definition("ua", "", List.of(new Option(COMMAND, true, true))));

    private BotCommands() {
    }

    private static Option player() {
        return new Option(PLAYER, true, true);
    }

    private static Option optional(String name) {
        return new Option(name, false, false);
    }

    static Optional<Definition> find(String name) {
        return ALL.stream().filter(definition -> definition.name().equals(name)).findFirst();
    }

    /** What went wrong with an option - a {@code discord.bot.*} message key. */
    record Invalid(String messageKey) {
    }

    /**
     * The {@code /admin} command line for {@code definition} with the given
     * option values, or why the values are refused.
     */
    static Result commandLine(Definition definition, Function<String, String> option) {
        if (definition.adminCommand().isEmpty()) {
            String command = option.apply(COMMAND);
            return command == null || command.isBlank() ? new Result(null, new Invalid("discord.bot.empty-command"))
                    : new Result(command.trim().replaceAll("\\s+", " "), null);
        }
        List<String> words = new ArrayList<>(List.of(definition.adminCommand()));
        for (Option candidate : definition.options()) {
            String value = option.apply(candidate.name());
            if (value == null || value.isBlank()) {
                if (candidate.required()) {
                    return new Result(null, new Invalid("discord.bot.missing-option"));
                }
                continue;
            }
            value = value.trim();
            switch (candidate.name()) {
                case PLAYER -> {
                    if (!PLAYER_NAME.matcher(value).matches()) {
                        return new Result(null, new Invalid("discord.bot.invalid-player"));
                    }
                }
                case DURATION -> {
                    if (!DURATION_TEXT.matcher(value).matches()) {
                        return new Result(null, new Invalid("discord.bot.invalid-duration"));
                    }
                    if (value.toLowerCase(java.util.Locale.ROOT).startsWith("perm")) {
                        continue; // no duration = permanent
                    }
                }
                default -> value = value.replaceAll("\\s+", " ");
            }
            words.add(value);
        }
        return new Result(String.join(" ", words), null);
    }

    /** Exactly one of the two is set. */
    record Result(String commandLine, Invalid invalid) {
    }

    /** The slash command's description in the server's language - {@code discord.bot.command.<name>}. */
    static String descriptionKey(Definition definition) {
        return "discord.bot.command." + definition.name();
    }

    static String optionDescriptionKey(Option option) {
        return "discord.bot.option." + option.name();
    }

    /** Required options first - Discord insists on that order. */
    static List<Option> ordered(Definition definition) {
        List<Option> required = new ArrayList<>(definition.options().stream().filter(Option::required).toList());
        required.addAll(definition.options().stream().filter(option -> !option.required()).toList());
        return required;
    }
}
