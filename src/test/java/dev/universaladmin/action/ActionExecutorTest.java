package dev.universaladmin.action;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.universaladmin.audit.AuditEvent;
import dev.universaladmin.audit.AuditEventType;
import dev.universaladmin.audit.AuditService;
import dev.universaladmin.permission.PermissionEvaluator;
import dev.universaladmin.permission.PermissionNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@link ActionExecutor} - the authorization/validation/audit/event
 * pipeline every frontend runs an {@link Action} through - without a running
 * Paper server or database, using in-memory fakes only (see docs/development/testing.md).
 */
class ActionExecutorTest {

    private final ActionRegistry registry = new ActionRegistry();
    private final RecordingAuditService audit = new RecordingAuditService();
    private final List<ActionEvent> events = new ArrayList<>();
    private final ActionExecutor executor = new ActionExecutor(registry, audit, Logger.getLogger("ActionExecutorTest"));

    ActionExecutorTest() {
        executor.subscribe(events::add);
    }

    @Test
    void successfulActionReturnsSuccessAndAudits() {
        registry.register(ActionDefinition.builder(new EchoAction())
                .permission(PermissionNode.core("test.echo"))
                .build());
        Actor actor = trustedPlayer();

        ActionResult<String> result =
                executor.<String, String>execute(EchoAction.ID, new ActionContext(actor, Source.COMMAND), "hello").join();

        assertEquals(ActionResult.success("hello"), result);
        assertEquals(1, audit.recorded.size());
        assertEquals(actor, audit.recorded.get(0).actor());
    }

    @Test
    void successfulActionIsAuditedAutomaticallyWithModuleTargetAndSource() {
        UUID targetId = UUID.randomUUID();
        registry.register(ActionDefinition.builder(new RecordingAction())
                .module("moderation")
                .target(input -> Optional.of(ActionTarget.player(targetId, "Alex")))
                .build());

        executor.<String, String>execute(
                RecordingAction.ID, new ActionContext(trustedPlayer(), Source.GUI), "kick Alex").join();

        assertEquals(1, audit.recorded.size());
        AuditEvent recorded = audit.recorded.get(0);
        assertEquals("moderation", recorded.module());
        assertEquals(targetId.toString(), recorded.target().id());
        assertEquals(Source.GUI, recorded.source());
        assertTrue(recorded.success());
    }

    @Test
    void failedActionIsNotAuditedByDefault() {
        registry.register(ActionDefinition.builder(new AlwaysFailsAction()).build());

        executor.<String, String>execute(
                AlwaysFailsAction.ID, new ActionContext(trustedPlayer(), Source.COMMAND), "x").join();

        assertTrue(audit.recorded.isEmpty());
    }

    @Test
    void failedActionIsAuditedWhenAuditFailuresIsEnabled() {
        registry.register(ActionDefinition.builder(new AlwaysFailsAction())
                .auditFailures()
                .build());

        executor.<String, String>execute(
                AlwaysFailsAction.ID, new ActionContext(trustedPlayer(), Source.COMMAND), "ghost-player").join();

        assertEquals(1, audit.recorded.size());
        AuditEvent recorded = audit.recorded.get(0);
        assertFalse(recorded.success());
        assertEquals("No such target: ghost-player", recorded.reason());
    }

    @Test
    void deniedActionIsAuditedWhenAuditFailuresIsEnabled() {
        registry.register(ActionDefinition.builder(new RecordingAction())
                .permission(PermissionNode.core("test.echo"))
                .auditFailures()
                .build());
        Actor actor = Actor.player(UUID.randomUUID(), "Steve", PermissionEvaluator.denyAll());

        executor.<String, String>execute(RecordingAction.ID, new ActionContext(actor, Source.COMMAND), "x").join();

        assertEquals(1, audit.recorded.size());
        assertFalse(audit.recorded.get(0).success());
        assertTrue(audit.recorded.get(0).reason().contains("permission"));
    }

    @Test
    void missingPermissionFailsBeforeTheActionRuns() {
        RecordingAction action = new RecordingAction();
        registry.register(ActionDefinition.builder(action)
                .permission(PermissionNode.core("test.echo"))
                .build());
        Actor actor = Actor.player(UUID.randomUUID(), "Steve", PermissionEvaluator.denyAll());

        ActionResult<String> result = executor.<String, String>execute(
                RecordingAction.ID, new ActionContext(actor, Source.COMMAND), "x").join();

        assertFailure(result, ActionResult.FailureReason.NOT_PERMITTED);
        assertFalse(action.wasCalled());
        assertTrue(audit.recorded.isEmpty());
    }

    @Test
    void invalidInputFailsValidationBeforeTheActionRuns() {
        RecordingAction action = new RecordingAction();
        registry.register(ActionDefinition.builder(action)
                .validator((context, input) -> "".equals(input)
                        ? Optional.of(ValidationError.of(ActionResult.FailureReason.VALIDATION, "empty input"))
                        : Optional.empty())
                .build());

        ActionResult<String> result = executor.<String, String>execute(
                RecordingAction.ID, new ActionContext(trustedPlayer(), Source.COMMAND), "").join();

        assertFailure(result, ActionResult.FailureReason.VALIDATION);
        assertFalse(action.wasCalled());
    }

    @Test
    void selfTargetIsRejectedWhenForbidden() {
        UUID playerId = UUID.randomUUID();
        RecordingAction action = new RecordingAction();
        registry.register(ActionDefinition.builder(action)
                .target(input -> Optional.of(ActionTarget.player(playerId, "Steve")))
                .forbidSelfTarget()
                .build());
        Actor actor = Actor.player(playerId, "Steve", PermissionEvaluator.allowAll());

        ActionResult<String> result = executor.<String, String>execute(
                RecordingAction.ID, new ActionContext(actor, Source.COMMAND), "kick-myself").join();

        assertFailure(result, ActionResult.FailureReason.VALIDATION);
        assertFalse(action.wasCalled());
    }

    @Test
    void selfTargetPolicyDoesNotRejectDifferentPlayers() {
        RecordingAction action = new RecordingAction();
        registry.register(ActionDefinition.builder(action)
                .target(input -> Optional.of(ActionTarget.player(UUID.randomUUID(), "Alex")))
                .forbidSelfTarget()
                .build());

        ActionResult<String> result = executor.<String, String>execute(
                RecordingAction.ID, new ActionContext(trustedPlayer(), Source.COMMAND), "kick").join();

        assertTrue(result instanceof ActionResult.Success<String>);
        assertTrue(action.wasCalled());
    }

    @Test
    void invalidTargetStateSurfacesAsAFailureFromTheActionItself() {
        // "Target Zustand" checks (does the target still exist, is it online,
        // ...) need domain lookups the executor cannot do generically - they
        // run inside the action itself and come back as an ordinary Failure,
        // exactly like any other business-rule failure.
        registry.register(ActionDefinition.builder(new AlwaysFailsAction()).build());

        ActionResult<String> result = executor.<String, String>execute(
                AlwaysFailsAction.ID, new ActionContext(trustedPlayer(), Source.COMMAND), "ghost-player").join();

        assertFailure(result, ActionResult.FailureReason.NOT_FOUND);
        assertTrue(audit.recorded.isEmpty());
    }

    @Test
    void throwingActionBecomesAnInternalErrorInsteadOfPropagating() {
        registry.register(ActionDefinition.builder(new ThrowingAction()).build());

        ActionResult<String> result = executor.<String, String>execute(
                ThrowingAction.ID, new ActionContext(trustedPlayer(), Source.COMMAND), "x").join();

        assertFailure(result, ActionResult.FailureReason.INTERNAL_ERROR);
    }

    @Test
    void unknownActionIdIsReportedAsNotFound() {
        ActionResult<String> result = executor.<String, String>execute(
                ActionId.of("test", "does-not-exist"), new ActionContext(trustedPlayer(), Source.COMMAND), "x").join();

        assertFailure(result, ActionResult.FailureReason.NOT_FOUND);
    }

    @Test
    void sourceIsPropagatedIntoTheActionsContext() {
        RecordingAction action = new RecordingAction();
        registry.register(ActionDefinition.builder(action).build());

        executor.<String, String>execute(RecordingAction.ID, new ActionContext(trustedPlayer(), Source.GUI), "x").join();

        assertEquals(Source.GUI, action.lastContext().source());
    }

    @Test
    void actorModelAnswersPermissionsForEveryActorKind() {
        PermissionNode node = PermissionNode.core("test.node");

        assertTrue(Actor.player(UUID.randomUUID(), "Steve", PermissionEvaluator.allowAll()).hasPermission(node));
        assertFalse(Actor.player(UUID.randomUUID(), "Alex", PermissionEvaluator.denyAll()).hasPermission(node));
        assertTrue(Actor.console().hasPermission(node));
        assertTrue(Actor.system("cron").hasPermission(node));
        assertTrue(Actor.web("web-user", PermissionEvaluator.allowAll()).hasPermission(node));
        assertFalse(Actor.web("web-user", PermissionEvaluator.denyAll()).hasPermission(node));
    }

    @Test
    void eventsFireExecutingThenExecutedForASuccessfulRun() {
        registry.register(ActionDefinition.builder(new EchoAction()).build());

        executor.<String, String>execute(EchoAction.ID, new ActionContext(trustedPlayer(), Source.COMMAND), "hi").join();

        assertEquals(2, events.size());
        assertTrue(events.get(0) instanceof ActionEvent.Executing<?>);
        assertTrue(events.get(1) instanceof ActionEvent.Executed<?, ?>);
    }

    @Test
    void eventsFireExecutingThenFailedForADeniedAction() {
        registry.register(ActionDefinition.builder(new RecordingAction())
                .permission(PermissionNode.core("test.echo"))
                .build());
        Actor actor = Actor.player(UUID.randomUUID(), "Steve", PermissionEvaluator.denyAll());

        executor.<String, String>execute(RecordingAction.ID, new ActionContext(actor, Source.COMMAND), "x").join();

        assertEquals(2, events.size());
        assertTrue(events.get(0) instanceof ActionEvent.Executing<?>);
        assertTrue(events.get(1) instanceof ActionEvent.Failed<?, ?>);
    }

    @Test
    void reversibleActionCanBeUndone() {
        ReversibleEchoAction action = new ReversibleEchoAction();
        registry.register(ActionDefinition.builder(action).build());
        ActionContext context = new ActionContext(trustedPlayer(), Source.COMMAND);

        ActionResult<String> executed = executor.<String, String>execute(ReversibleEchoAction.ID, context, "hi").join();
        String value = ((ActionResult.Success<String>) executed).value();
        ActionResult<Void> undone = executor.undo(ReversibleEchoAction.ID, context, "hi", value).join();

        assertTrue(undone instanceof ActionResult.Success<Void>);
        assertTrue(action.wasUndone());
    }

    @Test
    void nonReversibleActionRejectsUndo() {
        registry.register(ActionDefinition.builder(new EchoAction()).build());
        ActionContext context = new ActionContext(trustedPlayer(), Source.COMMAND);

        ActionResult<Void> undone = executor.undo(EchoAction.ID, context, "hi", "hi").join();

        assertFailure(undone, ActionResult.FailureReason.VALIDATION);
    }

    private Actor trustedPlayer() {
        return Actor.player(UUID.randomUUID(), "Steve", PermissionEvaluator.allowAll());
    }

    private void assertFailure(ActionResult<?> result, ActionResult.FailureReason expected) {
        assertTrue(result instanceof ActionResult.Failure<?> failure && failure.reason() == expected,
                () -> "Expected a " + expected + " failure but got " + result);
    }

    private static final class EchoAction implements Action<String, String> {
        static final ActionId ID = ActionId.of("test", "echo");

        @Override
        public ActionId id() {
            return ID;
        }

        @Override
        public CompletableFuture<ActionResult<String>> execute(ActionContext context, String input) {
            return CompletableFuture.completedFuture(ActionResult.success(input));
        }
    }

    private static final class RecordingAction implements Action<String, String> {
        static final ActionId ID = ActionId.of("test", "recording");

        private boolean called;
        private ActionContext lastContext;

        @Override
        public ActionId id() {
            return ID;
        }

        @Override
        public CompletableFuture<ActionResult<String>> execute(ActionContext context, String input) {
            called = true;
            lastContext = context;
            return CompletableFuture.completedFuture(ActionResult.success(input));
        }

        boolean wasCalled() {
            return called;
        }

        ActionContext lastContext() {
            return lastContext;
        }
    }

    private static final class AlwaysFailsAction implements Action<String, String> {
        static final ActionId ID = ActionId.of("test", "always-fails");

        @Override
        public ActionId id() {
            return ID;
        }

        @Override
        public CompletableFuture<ActionResult<String>> execute(ActionContext context, String input) {
            return CompletableFuture.completedFuture(
                    ActionResult.failure(ActionResult.FailureReason.NOT_FOUND, "No such target: " + input));
        }
    }

    private static final class ThrowingAction implements Action<String, String> {
        static final ActionId ID = ActionId.of("test", "throws");

        @Override
        public ActionId id() {
            return ID;
        }

        @Override
        public CompletableFuture<ActionResult<String>> execute(ActionContext context, String input) {
            throw new RuntimeException("boom");
        }
    }

    private static final class ReversibleEchoAction implements ReversibleAction<String, String> {
        static final ActionId ID = ActionId.of("test", "reversible-echo");

        private boolean undone;

        @Override
        public ActionId id() {
            return ID;
        }

        @Override
        public CompletableFuture<ActionResult<String>> execute(ActionContext context, String input) {
            return CompletableFuture.completedFuture(ActionResult.success(input));
        }

        @Override
        public CompletableFuture<ActionResult<Void>> undo(ActionContext context, String input, String result) {
            undone = true;
            return CompletableFuture.completedFuture(ActionResult.success(null));
        }

        boolean wasUndone() {
            return undone;
        }
    }

    private static final class RecordingAuditService implements AuditService {
        private final List<AuditEvent> recorded = new ArrayList<>();

        @Override
        public CompletableFuture<Void> record(AuditEvent entry) {
            recorded.add(entry);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<dev.universaladmin.audit.AuditPage> query(dev.universaladmin.audit.AuditQuery query) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<List<AuditEvent>> recent(int limit) {
            return CompletableFuture.completedFuture(List.copyOf(recorded));
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
