package dev.universaladmin.action;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class ActionRegistryTest {

    @Test
    void registersAndLooksUpByActionId() {
        ActionRegistry registry = new ActionRegistry();
        ActionDefinition<String, String> definition = ActionDefinition.builder(new EchoAction()).build();

        registry.register(definition);

        assertEquals(definition, registry.<String, String>get(EchoAction.ID).orElseThrow());
        assertTrue(registry.all().contains(definition));
    }

    @Test
    void rejectsDuplicateActionId() {
        ActionRegistry registry = new ActionRegistry();
        registry.register(ActionDefinition.builder(new EchoAction()).build());

        assertThrows(IllegalStateException.class,
                () -> registry.register(ActionDefinition.builder(new EchoAction()).build()));
    }

    @Test
    void unknownIdReturnsEmpty() {
        ActionRegistry registry = new ActionRegistry();

        assertTrue(registry.<String, String>get(EchoAction.ID).isEmpty());
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
}
