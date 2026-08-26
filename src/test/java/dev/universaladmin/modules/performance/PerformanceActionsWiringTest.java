package dev.universaladmin.modules.performance;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.universaladmin.action.ActionDefinition;
import dev.universaladmin.action.ActionRegistry;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.action.AuditDetails;
import dev.universaladmin.modules.performance.action.ClearEntitiesAction;
import dev.universaladmin.modules.performance.action.ClearEntitiesInput;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

/**
 * Regression coverage for the {@code core:performance.clear-entities} audit
 * metadata crash: {@code entityTypes} used to be put into {@link
 * AuditDetails} metadata as a raw {@code List}, which {@code MetadataJson}
 * (only String/Number/Boolean/null allowed) rejected at save time, silently
 * dropping the audit entry for every entity-clear run (see
 * {@code MetadataJsonTest} for the codec's own contract). Exercises {@link
 * PerformanceModule#registerActions} directly, the same "no live server
 * needed" pattern {@code ModerationActionsWiringTest} established - every
 * dependency below is only ever stored, never invoked.
 */
class PerformanceActionsWiringTest {

    private static final Logger NOOP_LOGGER = Logger.getLogger("PerformanceActionsWiringTest");

    @Test
    void clearEntitiesAuditMetadataNeverContainsAListValue() {
        ActionRegistry registry = new ActionRegistry();
        PerformanceModule.registerActions(registry, null, null, NOOP_LOGGER);

        ActionDefinition<ClearEntitiesInput, Integer> definition =
                registry.<ClearEntitiesInput, Integer>get(ClearEntitiesAction.ID).orElseThrow();
        ClearEntitiesInput input = new ClearEntitiesInput(Set.of(EntityType.ZOMBIE, EntityType.SKELETON), null);
        AuditDetails details = definition.auditDetails().apply(input, ActionResult.success(2));

        Object typesValue = details.metadata().get("types");
        assertInstanceOf(String.class, typesValue, "metadata values must be String/Number/Boolean/null - see MetadataJson.encodeValue");
        assertTrue(((String) typesValue).contains("ZOMBIE"));
    }
}
