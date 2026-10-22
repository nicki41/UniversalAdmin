package dev.universaladmin.modules.moderation.action;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.universaladmin.action.Action;
import dev.universaladmin.action.ActionContext;
import dev.universaladmin.action.ActionDefinition;
import dev.universaladmin.action.ActionExecutor;
import dev.universaladmin.action.ActionId;
import dev.universaladmin.action.ActionRegistry;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.action.Actor;
import dev.universaladmin.action.Source;
import dev.universaladmin.audit.AuditEvent;
import dev.universaladmin.audit.AuditPage;
import dev.universaladmin.audit.AuditQuery;
import dev.universaladmin.audit.AuditService;
import dev.universaladmin.localization.MessageService;
import dev.universaladmin.modules.moderation.ModerationPermissions;
import dev.universaladmin.modules.moderation.Punishment;
import dev.universaladmin.modules.moderation.PunishmentRepository;
import dev.universaladmin.modules.moderation.PunishmentService;
import dev.universaladmin.modules.moderation.PunishmentType;
import dev.universaladmin.modules.moderation.templates.PunishmentTemplateLoader;
import dev.universaladmin.modules.moderation.templates.PunishmentTemplateService;
import dev.universaladmin.permission.PermissionNode;
import dev.universaladmin.scheduler.TaskScheduler;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link ApplyTemplateAction} runs the moderation action of the next step
 * through a real {@link ActionExecutor} - so that action's permission
 * applies - against recording stand-ins for the warn/mute/ban actions.
 */
class ApplyTemplateActionTest {

    private static final UUID STEVE = UUID.randomUUID();
    private static final TaskScheduler DIRECT = new TaskScheduler() {
        @Override
        public <T> CompletableFuture<T> supplyAsync(Supplier<T> task) {
            return CompletableFuture.completedFuture(task.get());
        }

        @Override
        public CompletableFuture<Void> runAsync(Runnable task) {
            task.run();
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void runOnMainThread(Runnable task) {
            task.run();
        }

        @Override
        public void close() {
        }
    };

    private final List<Object> inputs = new ArrayList<>();
    private final List<Punishment> history = new ArrayList<>();
    private ActionExecutor executor;
    private ApplyTemplateAction action;

    @BeforeEach
    void setUp() {
        ActionRegistry registry = new ActionRegistry();
        register(registry, ModerationActionIds.WARN, ModerationPermissions.WARN);
        register(registry, ModerationActionIds.TEMP_MUTE, ModerationPermissions.TEMPMUTE);
        register(registry, ModerationActionIds.BAN, ModerationPermissions.BAN);
        executor = new ActionExecutor(registry, new NoAudit(), Logger.getAnonymousLogger());

        PunishmentRepository repository = mock(PunishmentRepository.class);
        when(repository.findWithTemplate(STEVE)).thenAnswer(invocation -> CompletableFuture.completedFuture(List.copyOf(history)));
        PunishmentTemplateService templates = new PunishmentTemplateService(new PunishmentService(repository), Logger.getAnonymousLogger());
        templates.apply(PunishmentTemplateLoader.parse("""
                templates:
                  spam:
                    name: "Spam"
                    reason: "Spam"
                    steps: [warn, mute 1h, ban]
                """), "test");
        MessageService messages = (key, args) -> key.value() + Arrays.toString(args);
        action = new ApplyTemplateAction(templates, executor, messages, DIRECT);
    }

    @Test
    void theFirstUseRunsTheWarnAction() {
        ActionResult<Punishment> result = run(Set.of(ModerationPermissions.WARN), "spam", null);

        ActionResult.Success<Punishment> success = assertInstanceOf(ActionResult.Success.class, result);
        assertEquals("moderation.template.applied", success.messageKey().value());
        assertEquals(List.of("Spam", "1", "3"), success.messageArgs().subList(0, 3));
        assertEquals(new WarnInput(STEVE, "Spam", "spam"), inputs.get(0));
    }

    @Test
    void anEarlierUseLeadsToTheTempMuteWithTheNoteAppended() {
        history.add(earlier(PunishmentType.WARN));

        run(Set.of(ModerationPermissions.TEMPMUTE), "SPAM", "in global chat");

        MuteInput mute = assertInstanceOf(MuteInput.class, inputs.get(0));
        assertEquals("Spam - in global chat", mute.reason());
        assertEquals("spam", mute.template());
        long minutes = Duration.between(Instant.now(), mute.expiresAt()).toMinutes();
        assertTrue(minutes >= 59 && minutes <= 60, "about an hour, was " + minutes + " minutes");
    }

    @Test
    void thePermanentStepUsesThePermanentAction() {
        history.add(earlier(PunishmentType.WARN));
        history.add(earlier(PunishmentType.TEMP_MUTE));

        run(Set.of(ModerationPermissions.BAN), "spam", null);

        BanInput ban = assertInstanceOf(BanInput.class, inputs.get(0));
        assertNull(ban.expiresAt());
    }

    @Test
    void aStepTheActorMayNotIssueIsRefusedWithTheTemplatesWords() {
        history.add(earlier(PunishmentType.WARN));

        ActionResult<Punishment> result = run(Set.of(ModerationPermissions.WARN), "spam", null);

        ActionResult.Failure<Punishment> failure = assertInstanceOf(ActionResult.Failure.class, result);
        assertEquals(ActionResult.FailureReason.NOT_PERMITTED, failure.reason());
        assertEquals("moderation.template.step-not-permitted", failure.messageKey().value());
        assertTrue(inputs.isEmpty());
    }

    @Test
    void anUnknownTemplateIsNotFound() {
        ActionResult<Punishment> result = run(Set.of(ModerationPermissions.WARN), "toxic", null);

        ActionResult.Failure<Punishment> failure = assertInstanceOf(ActionResult.Failure.class, result);
        assertEquals("moderation.template.unknown", failure.messageKey().value());
        assertEquals(List.of("toxic", "spam"), failure.messageArgs());
    }

    private ActionResult<Punishment> run(Set<PermissionNode> granted, String template, String note) {
        Actor actor = Actor.player(UUID.randomUUID(), "Mod", granted::contains);
        return action.execute(new ActionContext(actor, Source.COMMAND), new TemplateInput(STEVE, template, note)).join();
    }

    private <I> void register(ActionRegistry registry, ActionId id, PermissionNode permission) {
        Action<I, Punishment> recording = new Action<>() {
            @Override
            public ActionId id() {
                return id;
            }

            @Override
            public CompletableFuture<ActionResult<Punishment>> execute(ActionContext context, I input) {
                inputs.add(input);
                return CompletableFuture.completedFuture(ActionResult.success(earlier(PunishmentType.WARN),
                        dev.universaladmin.localization.MessageKey.of("moderation.result.test")));
            }
        };
        registry.register(ActionDefinition.builder(recording).permission(permission).notAudited().build());
    }

    private static Punishment earlier(PunishmentType type) {
        return new Punishment(1, type, STEVE, "Steve", null, null, "Mod", "Spam", Instant.now().minusSeconds(3600), null,
                false, null, null, Map.of(), "spam");
    }

    private static final class NoAudit implements AuditService {
        @Override
        public CompletableFuture<Void> record(AuditEvent entry) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<AuditPage> query(AuditQuery query) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<List<AuditEvent>> recent(int limit) {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        public CompletableFuture<Optional<AuditEvent>> findById(Long id) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        @Override
        public CompletableFuture<Integer> cleanupExpired() {
            return CompletableFuture.completedFuture(0);
        }
    }
}
