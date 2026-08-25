package dev.universaladmin.modules.performance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.junit.jupiter.api.Test;

/**
 * {@link EntityClearFilter} is the safety net behind Entity Clear (docs/development/architecture-rules.md:
 * "niemals pauschal alle Entities", "Players niemals", "gefährliche Entity
 * Types standardmäßig ausgeschlossen") - the one place worth a unit test
 * without a running server, since it's pure decision logic over mocked
 * Bukkit interfaces rather than a live-state read like {@code
 * PerformanceSamplingService} (see docs/development/testing.md).
 */
class EntityClearFilterTest {

    @Test
    void clearsAnEntityOfATargetedTypeWithNoOtherProtection() {
        Entity zombie = plainEntity(EntityType.ZOMBIE);

        assertTrue(EntityClearFilter.isClearable(zombie, Set.of(EntityType.ZOMBIE)));
    }

    @Test
    void neverClearsAPlayerEvenIfItsTypeIsTargeted() {
        Player player = mock(Player.class);
        when(player.getType()).thenReturn(EntityType.PLAYER);

        assertFalse(EntityClearFilter.isClearable(player, Set.of(EntityType.PLAYER)));
    }

    @Test
    void doesNotClearATypeThatWasNotRequested() {
        Entity zombie = plainEntity(EntityType.ZOMBIE);

        assertFalse(EntityClearFilter.isClearable(zombie, Set.of(EntityType.SKELETON)));
    }

    @Test
    void neverClearsANamedEntity() {
        Entity namedZombie = mock(Entity.class);
        when(namedZombie.getType()).thenReturn(EntityType.ZOMBIE);
        when(namedZombie.customName()).thenReturn(Component.text("Fluffy"));

        assertFalse(EntityClearFilter.isClearable(namedZombie, Set.of(EntityType.ZOMBIE)));
    }

    @Test
    void neverClearsATamedEntity() {
        // Tameable extends (transitively) LivingEntity in Bukkit's hierarchy, so a single mock covers both checks.
        Tameable tamedWolf = mock(Tameable.class);
        when(tamedWolf.getType()).thenReturn(EntityType.WOLF);
        when(tamedWolf.isTamed()).thenReturn(true);

        assertFalse(EntityClearFilter.isClearable(tamedWolf, Set.of(EntityType.WOLF)));
    }

    @Test
    void neverClearsALeashedEntity() {
        LivingEntity leashedCow = mock(LivingEntity.class);
        when(leashedCow.getType()).thenReturn(EntityType.COW);
        when(leashedCow.isLeashed()).thenReturn(true);

        assertFalse(EntityClearFilter.isClearable(leashedCow, Set.of(EntityType.COW)));
    }

    @Test
    void effectiveTargetsStripsProtectedTypesFromTheRequestedSet() {
        Set<EntityType> requested = Set.of(EntityType.ZOMBIE, EntityType.VILLAGER, EntityType.SKELETON);
        Set<EntityType> protectedTypes = Set.of(EntityType.VILLAGER);

        Set<EntityType> effective = EntityClearFilter.effectiveTargets(requested, protectedTypes);

        assertEquals(Set.of(EntityType.ZOMBIE, EntityType.SKELETON), effective);
    }

    @Test
    void resolveProtectedTypesIgnoresAnUnknownNameInsteadOfThrowing() {
        Set<EntityType> resolved = EntityClearFilter.resolveProtectedTypes(
                List.of("VILLAGER", "NOT_A_REAL_ENTITY_TYPE"), Logger.getLogger("test"));

        assertEquals(Set.of(EntityType.VILLAGER), resolved);
    }

    private Entity plainEntity(EntityType type) {
        Entity entity = mock(Entity.class);
        when(entity.getType()).thenReturn(type);
        return entity;
    }
}
