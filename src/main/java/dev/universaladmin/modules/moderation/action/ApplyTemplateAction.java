package dev.universaladmin.modules.moderation.action;

import dev.universaladmin.action.Action;
import dev.universaladmin.action.ActionContext;
import dev.universaladmin.action.ActionExecutor;
import dev.universaladmin.action.ActionId;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.localization.MessageService;
import dev.universaladmin.localization.ResultMessages;
import dev.universaladmin.modules.moderation.Punishment;
import dev.universaladmin.modules.moderation.templates.PunishmentTemplate;
import dev.universaladmin.modules.moderation.templates.PunishmentTemplateService;
import dev.universaladmin.modules.moderation.templates.TemplateStep;
import dev.universaladmin.modules.moderation.templates.TemplateTexts;
import dev.universaladmin.scheduler.TaskScheduler;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * {@code /punish <player> <template>}: works out the target's next step of the
 * template and runs the moderation action for it - warn, kick, mute,
 * temp-mute, ban or temp-ban - as the same actor, through the
 * {@link ActionExecutor}. That action's permission, policy, enforcement,
 * network message and audit entry therefore apply exactly as if the
 * punishment had been issued directly; this action itself is not audited.
 * The punishment row carries the template id, which is what later
 * escalation counts.
 */
public final class ApplyTemplateAction implements Action<TemplateInput, Punishment> {

    private final PunishmentTemplateService templates;
    private final ActionExecutor executor;
    private final MessageService messages;
    private final TaskScheduler scheduler;

    public ApplyTemplateAction(
            PunishmentTemplateService templates, ActionExecutor executor, MessageService messages, TaskScheduler scheduler) {
        this.templates = templates;
        this.executor = executor;
        this.messages = messages;
        this.scheduler = scheduler;
    }

    @Override
    public ActionId id() {
        return ModerationActionIds.PUNISH;
    }

    @Override
    public CompletableFuture<ActionResult<Punishment>> execute(ActionContext context, TemplateInput input) {
        Optional<PunishmentTemplate> template = templates.find(input.template());
        if (template.isEmpty()) {
            String known = templates.templates().stream().map(PunishmentTemplate::id).collect(Collectors.joining(", "));
            return CompletableFuture.completedFuture(ActionResult.failure(ActionResult.FailureReason.NOT_FOUND,
                    MessageKey.of("moderation.template.unknown"), input.template(),
                    known.isEmpty() ? MessageKey.of("common.none") : known));
        }
        return templates.next(input.targetId(), template.get()).thenCompose(choice -> {
            // Permission checks and the moderation actions expect the main thread.
            CompletableFuture<ActionResult<Punishment>> done = new CompletableFuture<>();
            scheduler.runOnMainThread(() -> apply(context, input, choice).whenComplete((result, error) -> {
                if (error != null) {
                    done.completeExceptionally(error);
                } else {
                    done.complete(result);
                }
            }));
            return done;
        });
    }

    private CompletableFuture<ActionResult<Punishment>> apply(
            ActionContext context, TemplateInput input, PunishmentTemplateService.Choice choice) {
        PunishmentTemplate template = choice.template();
        TemplateStep step = choice.step();
        String reason = input.note() == null || input.note().isBlank()
                ? template.reason() : template.reason() + " - " + input.note().trim();
        Instant expiresAt = step.permanent() ? null : Instant.now().plus(step.duration());
        UUID target = input.targetId();
        CompletableFuture<ActionResult<Punishment>> result = switch (step.kind()) {
            case WARN -> executor.execute(ModerationActionIds.WARN, context, new WarnInput(target, reason, template.id()));
            case KICK -> executor.execute(ModerationActionIds.KICK, context, new KickInput(target, reason, template.id()));
            case MUTE -> executor.execute(step.permanent() ? ModerationActionIds.MUTE : ModerationActionIds.TEMP_MUTE, context,
                    new MuteInput(target, reason, expiresAt, template.id()));
            case BAN -> executor.execute(step.permanent() ? ModerationActionIds.BAN : ModerationActionIds.TEMP_BAN, context,
                    new BanInput(target, reason, expiresAt, template.id()));
        };
        return result.thenApply(inner -> describe(inner, choice));
    }

    /** The step's own result, introduced with the template and step - or why the step could not be applied. */
    private ActionResult<Punishment> describe(ActionResult<Punishment> inner, PunishmentTemplateService.Choice choice) {
        String step = TemplateTexts.step(messages, choice.step());
        return switch (inner) {
            case ActionResult.Success<Punishment> success -> ActionResult.success(success.value(),
                    MessageKey.of("moderation.template.applied"), choice.template().name(), String.valueOf(choice.stepNumber()),
                    String.valueOf(choice.template().steps().size()),
                    MessageService.markup(ResultMessages.success(messages, success, MessageKey.of("command.done"))));
            case ActionResult.Failure<Punishment> failure when failure.reason() == ActionResult.FailureReason.NOT_PERMITTED
                    && failure.messageKey() == null -> ActionResult.failure(ActionResult.FailureReason.NOT_PERMITTED,
                    MessageKey.of("moderation.template.step-not-permitted"), choice.template().name(), MessageService.markup(step));
            case ActionResult.Failure<Punishment> failure -> failure;
        };
    }
}
