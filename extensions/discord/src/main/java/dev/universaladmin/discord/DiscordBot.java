package dev.universaladmin.discord;

import dev.universaladmin.action.Actor;
import dev.universaladmin.audit.AuditEvent;
import dev.universaladmin.chat.StaffChat;
import dev.universaladmin.command.RemoteCommands;
import dev.universaladmin.core.UniversalAdmin;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.network.NetworkListener;
import dev.universaladmin.permission.PermissionNode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.modals.Modal;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import net.dv8tion.jda.api.utils.messages.MessageRequest;
import org.bukkit.Bukkit;

/**
 * Bot mode: one Discord bot that
 * <ul>
 *   <li>posts admin actions to a log channel (same filter as the webhook),</li>
 *   <li>bridges the staff chat with a channel, both ways,</li>
 *   <li>offers slash commands ({@code /ban}, {@code /mute}, {@code /history},
 *       {@code /ua <any admin command>}, ...) that run as the Discord member,
 *       with the permissions their roles grant ({@link RolePermissions}),</li>
 *   <li>posts new reports with Claim / Resolve / Reject buttons and keeps
 *       those cards up to date.</li>
 * </ul>
 * Everything the bot does in UniversalAdmin goes through the normal command
 * and action path ({@link RemoteCommands}), so it is permission-checked and
 * audited like in game. Mentions are switched off for every message.
 */
final class DiscordBot {

    /** Shown in front of a Discord member's name in game and in the audit log. */
    static final String ACTOR_PREFIX = "Discord:";
    static final String VIA = "Discord";

    private static final PermissionNode STAFF_CHAT = PermissionNode.of("universaladmin.chat.staff");
    private static final PermissionNode MANAGE_REPORTS = PermissionNode.of("universaladmin.reports.manage");
    private static final String REPORT_BUTTON = "ua-report:";
    private static final String REPORT_MODAL = "ua-report-note:";
    private static final String REPORTS_TOPIC = "reports.created";
    private static final int ORANGE = 0xE67E22;
    private static final int GREEN = 0x2ECC71;
    private static final int GRAY = 0x95A5A6;
    private static final int BLUE = 0x3498DB;

    private final UniversalAdmin universalAdmin;
    private final Logger logger;
    private final ScheduledExecutorService timer;
    private final Map<Long, String> reportCards = new ConcurrentHashMap<>();
    private final StaffChat.Listener staffChatListener = this::toDiscord;
    private final NetworkListener remoteReports = message -> postReportCard(
            Long.parseLong(message.get("id", "0")), message.get("reporter", "?"), message.get("target", "?"),
            message.get("reason", ""), message.source());

    private volatile DiscordConfig config;
    private volatile JDA jda;
    private volatile String stateKey = "discord.bot.state.starting";
    private volatile Object[] stateArgs = {};

    DiscordBot(UniversalAdmin universalAdmin, Logger logger) {
        this.universalAdmin = universalAdmin;
        this.logger = logger;
        this.timer = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "UniversalAdminDiscord-bot");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Connects in the background; problems end up in the log and in {@link #state()}. */
    void start(DiscordConfig config) {
        this.config = config;
        if (!config.bot().configured()) {
            state("discord.bot.state.not-configured");
            return;
        }
        MessageRequest.setDefaultMentions(EnumSet.noneOf(Message.MentionType.class));
        timer.execute(() -> connect(config));
    }

    private void connect(DiscordConfig config) {
        try {
            JDA connected = JDABuilder.createLight(config.bot().token(), GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT)
                    .addEventListeners(new Events())
                    .build()
                    .awaitReady();
            Guild guild = connected.getGuildById(config.bot().guildId());
            if (guild == null) {
                state("discord.bot.state.not-in-guild", config.bot().guildId());
                logger.warning("The Discord bot is not a member of server " + config.bot().guildId()
                        + " - invite it (see docs/extensions/discord.md), then /admin discord reload.");
                jda = connected;
                return;
            }
            guild.updateCommands().addCommands(slashCommands()).queue(
                    done -> { },
                    error -> logger.warning("Could not register the slash commands: " + error.getMessage()));
            jda = connected;
            state("discord.bot.state.online", connected.getSelfUser().getName(), guild.getName());
            universalAdmin.services().get(StaffChat.class).ifPresent(chat -> chat.subscribe(staffChatListener));
            universalAdmin.network().subscribe(REPORTS_TOPIC, remoteReports);
            if (!config.bot().activity().isEmpty()) {
                timer.scheduleAtFixedRate(this::updateActivity, 0, 60, TimeUnit.SECONDS);
            }
            logger.info("Discord bot online as " + connected.getSelfUser().getName() + " on " + guild.getName() + ".");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            // An invalid token, missing intents, no network...
            state("discord.bot.state.failed", String.valueOf(e.getMessage()));
            logger.warning("The Discord bot could not connect: " + e.getMessage()
                    + " - check bot.token and that the Message Content intent is enabled (docs/extensions/discord.md).");
        }
    }

    void stop() {
        universalAdmin.services().get(StaffChat.class).ifPresent(chat -> chat.unsubscribe(staffChatListener));
        universalAdmin.network().unsubscribe(REPORTS_TOPIC, remoteReports);
        timer.shutdownNow();
        JDA current = jda;
        jda = null;
        if (current != null) {
            current.shutdown();
            try {
                current.awaitShutdown(Duration.ofSeconds(5));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /** What the bot is doing right now, in the server's language - for {@code /admin discord status}. */
    String state() {
        return text(stateKey, stateArgs);
    }

    private void state(String key, Object... args) {
        stateArgs = args;
        stateKey = key;
    }

    /**
     * Posts a test message to the log channel (or, without one, the staff chat
     * or reports channel); completes with whether Discord accepted it.
     */
    CompletableFuture<Boolean> sendTest(String sentBy) {
        DiscordConfig current = config;
        if (current == null) {
            return CompletableFuture.completedFuture(false);
        }
        Optional<TextChannel> target = channel(current.bot().logChannel())
                .or(() -> channel(current.bot().staffChat()))
                .or(() -> channel(current.bot().reports()));
        if (target.isEmpty()) {
            return CompletableFuture.completedFuture(false);
        }
        MessageEmbed embed = new EmbedBuilder()
                .setTitle(text("discord.bot.test-title"))
                .setDescription(MarkdownSanitizer.escape(text("discord.bot.test-text", sentBy, current.serverName())))
                .setColor(GREEN)
                .setTimestamp(Instant.now())
                .build();
        return target.get().sendMessageEmbeds(embed).submit().handle((message, error) -> error == null);
    }

    // ---------------------------------------------------------------- audit -> Discord

    void onAudit(AuditEvent event) {
        DiscordConfig current = config;
        if (jda == null || current == null) {
            return;
        }
        String type = event.type().toString();
        if (event.success() && type.startsWith("core:reports.")) {
            reportId(event).ifPresent(id -> onReportEvent(type, id, event));
        }
        if ((event.success() || current.includeFailures()) && current.filter().matches(type)) {
            channel(current.bot().logChannel()).ifPresent(channel ->
                    channel.sendMessageEmbeds(toJda(EmbedFormatter.embed(event, current))).queue(null, this::warnOnce));
        }
    }

    private static Optional<Long> reportId(AuditEvent event) {
        String correlation = event.correlationId();
        if (correlation == null || !correlation.startsWith("report#")) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(correlation.substring("report#".length())));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private void onReportEvent(String type, long id, AuditEvent event) {
        switch (type) {
            case "core:reports.create" -> postReportCard(id, event.actor().displayName(),
                    event.target() == null ? "?" : event.target().displayName(), event.summary(), null);
            case "core:reports.claim" -> updateReportCard(id, text("discord.bot.report.claimed", event.actor().displayName()), BLUE, false);
            case "core:reports.resolve" -> updateReportCard(id, text("discord.bot.report.resolved", event.actor().displayName()), GREEN, true);
            case "core:reports.reject" -> updateReportCard(id, text("discord.bot.report.rejected", event.actor().displayName()), GRAY, true);
            default -> {
            }
        }
    }

    private void postReportCard(long id, String reporter, String target, String reason, String server) {
        DiscordConfig current = config;
        if (id <= 0 || current == null) {
            return;
        }
        channel(current.bot().reports()).ifPresent(channel -> channel
                .sendMessageEmbeds(reportEmbed(id, reporter, target, reason, server, null, ORANGE))
                .addComponents(ActionRow.of(reportButtons(id, false, false)))
                .queue(message -> reportCards.put(id, message.getId()), this::warnOnce));
    }

    private void updateReportCard(long id, String status, int color, boolean closed) {
        String messageId = reportCards.get(id);
        DiscordConfig current = config;
        if (messageId == null || current == null) {
            return;
        }
        channel(current.bot().reports()).ifPresent(channel -> channel.retrieveMessageById(messageId).queue(message -> {
            if (message.getEmbeds().isEmpty()) {
                return;
            }
            EmbedBuilder updated = new EmbedBuilder(message.getEmbeds().get(0)).setColor(color);
            updated.getFields().removeIf(field -> text("discord.bot.report.status").equals(field.getName()));
            updated.addField(text("discord.bot.report.status"), MarkdownSanitizer.escape(status), false);
            message.editMessageEmbeds(updated.build())
                    .setComponents(ActionRow.of(reportButtons(id, true, closed)))
                    .queue(null, this::warnOnce);
            if (closed) {
                reportCards.remove(id);
            }
        }, this::warnOnce));
    }

    private MessageEmbed reportEmbed(long id, String reporter, String target, String reason, String server, String status, int color) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(text("discord.bot.report.title", String.valueOf(id)))
                .setDescription(MarkdownSanitizer.escape(EmbedFormatter.limit(reason == null ? "" : reason, 4000)))
                .setColor(color)
                .addField(text("discord.bot.report.player"), MarkdownSanitizer.escape(target), true)
                .addField(text("discord.bot.report.reporter"), MarkdownSanitizer.escape(reporter), true)
                .setFooter(config.serverName() + (server == null ? "" : " • " + server))
                .setTimestamp(Instant.now());
        if (status != null) {
            embed.addField(text("discord.bot.report.status"), status, false);
        }
        return embed.build();
    }

    private List<Button> reportButtons(long id, boolean claimed, boolean closed) {
        return List.of(
                Button.primary(REPORT_BUTTON + "claim:" + id, text("discord.bot.report.claim")).withDisabled(claimed || closed),
                Button.success(REPORT_BUTTON + "resolve:" + id, text("discord.bot.report.resolve")).withDisabled(closed),
                Button.danger(REPORT_BUTTON + "reject:" + id, text("discord.bot.report.reject")).withDisabled(closed));
    }

    private MessageEmbed toJda(EmbedFormatter.Embed embed) {
        EmbedBuilder builder = new EmbedBuilder()
                .setTitle(embed.title())
                .setDescription(MarkdownSanitizer.escape(embed.description()))
                .setColor(embed.color())
                .setFooter(embed.footer())
                .setTimestamp(embed.timestamp());
        embed.fields().forEach(field -> builder.addField(field.name(), MarkdownSanitizer.escape(field.value()), field.inline()));
        return builder.build();
    }

    // ---------------------------------------------------------------- staff chat

    /** In game (or another server) -> Discord. Messages that came from Discord are not echoed back. */
    private void toDiscord(StaffChat.Message message) {
        DiscordConfig current = config;
        if (current == null || VIA.equals(message.via())) {
            return;
        }
        String prefix = message.server() == null ? "" : "**[" + MarkdownSanitizer.escape(message.server()) + "]** ";
        String via = message.via() == null ? "" : "*[" + MarkdownSanitizer.escape(message.via()) + "]* ";
        String text = prefix + via + "**" + MarkdownSanitizer.escape(message.sender()) + "**: " + MarkdownSanitizer.escape(message.text());
        channel(current.bot().staffChat()).ifPresent(channel ->
                channel.sendMessage(EmbedFormatter.limit(text, 2000)).queue(null, this::warnOnce));
    }

    // ---------------------------------------------------------------- helpers

    private Optional<TextChannel> channel(String id) {
        JDA current = jda;
        if (current == null || id == null || id.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(current.getTextChannelById(id));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** The UniversalAdmin actor for a Discord member - their name, and what their roles allow. */
    private Actor actor(Member member) {
        List<String> roles = member.getRoles().stream().map(Role::getId).toList();
        return Actor.remote(ACTOR_PREFIX + member.getEffectiveName(), new RolePermissions(config.bot().roles()).forRoles(roles));
    }

    private void run(Member member, String commandLine, InteractionHook hook) {
        ReplyCollector collector = new ReplyCollector(timer,
                text -> hook.editOriginal(text).queue(null, this::warnOnce),
                text("discord.bot.no-reply")).start();
        universalAdmin.services().get(RemoteCommands.class).ifPresentOrElse(
                commands -> commands.run(actor(member), commandLine, collector),
                () -> hook.editOriginal(text("discord.bot.unavailable")).queue());
    }

    private String text(String key, Object... args) {
        return universalAdmin.messages().get(MessageKey.of(key), args);
    }

    private void updateActivity() {
        JDA current = jda;
        DiscordConfig settings = config;
        if (current == null || settings == null || settings.bot().activity().isEmpty()) {
            return;
        }
        int online;
        try {
            online = universalAdmin.scheduler().supplyOnMainThread(() -> Bukkit.getOnlinePlayers().size()).get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            return;
        }
        current.getPresence().setActivity(Activity.customStatus(
                EmbedFormatter.limit(settings.bot().activity().replace("{online}", String.valueOf(online)), 128)));
    }

    private volatile boolean warned;

    /** Discord errors (a deleted channel, missing permissions) - logged once, not on every message. */
    private void warnOnce(Throwable error) {
        if (!warned) {
            warned = true;
            logger.log(Level.WARNING, "Discord refused a message from the bot: " + error.getMessage()
                    + " - check the channel ids and the bot's permissions there. (Shown once.)");
        }
    }

    private List<CommandData> slashCommands() {
        List<CommandData> commands = new ArrayList<>();
        for (BotCommands.Definition definition : BotCommands.ALL) {
            SlashCommandData command = Commands.slash(definition.name(),
                    EmbedFormatter.limit(text(BotCommands.descriptionKey(definition)), 100));
            for (BotCommands.Option option : BotCommands.ordered(definition)) {
                command.addOptions(new OptionData(OptionType.STRING, option.name(),
                        EmbedFormatter.limit(text(BotCommands.optionDescriptionKey(option)), 100), option.required(), option.autocomplete()));
            }
            commands.add(command);
        }
        return commands;
    }

    // ---------------------------------------------------------------- Discord events

    private final class Events extends ListenerAdapter {

        @Override
        public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
            Member member = event.getMember();
            Optional<BotCommands.Definition> definition = BotCommands.find(event.getName());
            if (member == null || definition.isEmpty() || !isOurGuild(event.getGuild())) {
                event.reply(text("discord.bot.unavailable")).setEphemeral(true).queue();
                return;
            }
            BotCommands.Result result = BotCommands.commandLine(definition.get(), name -> {
                OptionMapping option = event.getOption(name);
                return option == null ? null : option.getAsString();
            });
            if (result.invalid() != null) {
                event.reply(text(result.invalid().messageKey())).setEphemeral(true).queue();
                return;
            }
            event.deferReply(true).queue(hook -> run(member, result.commandLine(), hook));
        }

        @Override
        public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
            Member member = event.getMember();
            Optional<BotCommands.Definition> definition = BotCommands.find(event.getName());
            Optional<RemoteCommands> commands = universalAdmin.services().get(RemoteCommands.class);
            if (member == null || definition.isEmpty() || commands.isEmpty()) {
                event.replyChoiceStrings(List.of()).queue();
                return;
            }
            String typed = event.getFocusedOption().getValue();
            String line = definition.get().adminCommand().isEmpty() ? typed : definition.get().adminCommand() + " " + typed;
            commands.get().complete(actor(member), line)
                    .completeOnTimeout(List.of(), 2, TimeUnit.SECONDS)
                    .thenAccept(choices -> event.replyChoiceStrings(complete(definition.get(), typed, choices)).queue(null, error -> { }));
        }

        /** For {@code /ua}: the whole line with the last word completed; for player options: the names. */
        private List<String> complete(BotCommands.Definition definition, String typed, List<String> choices) {
            List<String> result = new ArrayList<>();
            String head = "";
            if (definition.adminCommand().isEmpty()) {
                int space = typed.lastIndexOf(' ');
                head = space < 0 ? "" : typed.substring(0, space + 1);
            }
            for (String choice : choices) {
                String value = head + choice;
                if (value.length() <= 100 && result.size() < 25) {
                    result.add(value);
                }
            }
            return result;
        }

        @Override
        public void onButtonInteraction(ButtonInteractionEvent event) {
            String id = event.getComponentId();
            if (!id.startsWith(REPORT_BUTTON) || event.getMember() == null) {
                return;
            }
            String[] parts = id.substring(REPORT_BUTTON.length()).split(":", 2);
            if (parts.length != 2 || !parts[1].matches("\\d+")) {
                return;
            }
            Member member = event.getMember();
            if (!actor(member).hasPermission(MANAGE_REPORTS)) {
                event.reply(text("discord.bot.not-allowed")).setEphemeral(true).queue();
                return;
            }
            String action = parts[0];
            String reportId = parts[1];
            if (action.equals("claim")) {
                event.deferReply(true).queue(hook -> run(member, "reports claim " + reportId, hook));
                return;
            }
            TextInput note = TextInput.create("note", TextInputStyle.PARAGRAPH).setRequired(false).setMaxLength(400).build();
            Modal modal = Modal.create(REPORT_MODAL + action + ":" + reportId, text("discord.bot.report.note-title", reportId))
                    .addComponents(Label.of(text("discord.bot.report.note-label"), note))
                    .build();
            event.replyModal(modal).queue();
        }

        @Override
        public void onModalInteraction(ModalInteractionEvent event) {
            String id = event.getModalId();
            if (!id.startsWith(REPORT_MODAL) || event.getMember() == null) {
                return;
            }
            String[] parts = id.substring(REPORT_MODAL.length()).split(":", 2);
            if (parts.length != 2 || !parts[1].matches("\\d+") || !(parts[0].equals("resolve") || parts[0].equals("reject"))) {
                return;
            }
            var value = event.getValue("note");
            String note = value == null ? "" : value.getAsString().replaceAll("\\s+", " ").trim();
            Member member = event.getMember();
            event.deferReply(true).queue(hook -> run(member, "reports " + parts[0] + " " + parts[1] + (note.isEmpty() ? "" : " " + note), hook));
        }

        @Override
        public void onMessageReceived(MessageReceivedEvent event) {
            DiscordConfig current = config;
            if (current == null || !event.isFromGuild() || event.getAuthor().isBot() || event.isWebhookMessage()
                    || !event.getChannel().getId().equals(current.bot().staffChat()) || event.getMember() == null) {
                return;
            }
            if (!actor(event.getMember()).hasPermission(STAFF_CHAT)) {
                event.getMessage().addReaction(Emoji.fromUnicode("🚫")).queue(null, error -> { });
                return;
            }
            String text = event.getMessage().getContentDisplay().replaceAll("\\s+", " ").trim();
            if (!event.getMessage().getAttachments().isEmpty()) {
                text = (text + " " + text("discord.bot.attachment")).trim();
            }
            if (text.isEmpty()) {
                return;
            }
            String finalText = EmbedFormatter.limit(text, 256);
            universalAdmin.services().get(StaffChat.class)
                    .ifPresent(chat -> chat.send(event.getMember().getEffectiveName(), finalText, VIA));
        }

        private boolean isOurGuild(Guild guild) {
            return guild != null && config != null && guild.getId().equals(config.bot().guildId());
        }
    }
}
