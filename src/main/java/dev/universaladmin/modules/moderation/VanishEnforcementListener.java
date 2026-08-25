package dev.universaladmin.modules.moderation;

import dev.universaladmin.settings.SettingsService;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;

/**
 * Mob-targeting and item-pickup prevention for vanished players - both read
 * only {@link VanishRuntimeState} (in-memory), never the database or {@code
 * VanishService}'s async methods, since both these events fire extremely
 * frequently (every mob AI tick server-wide). Listens on {@link
 * EntityTargetLivingEntityEvent} specifically, not the base {@code
 * EntityTargetEvent} - confirmed (via the actual Paper API) that this
 * subclass, with its own separate {@code HandlerList}, is what fires when
 * the new target is a living entity (always true for a targeted player);
 * a handler registered only for the base class would never see it.
 */
public final class VanishEnforcementListener implements Listener {

    private final VanishRuntimeState runtimeState;
    private final SettingsService settings;

    public VanishEnforcementListener(VanishRuntimeState runtimeState, SettingsService settings) {
        this.runtimeState = runtimeState;
        this.settings = settings;
    }

    @EventHandler
    public void onTarget(EntityTargetLivingEntityEvent event) {
        LivingEntity target = event.getTarget();
        if (target instanceof Player player && runtimeState.isVanished(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player
                && runtimeState.isVanished(player.getUniqueId())
                && settings.get(ModerationSettings.VANISH_BLOCK_ITEM_PICKUP)) {
            event.setCancelled(true);
        }
    }
}
