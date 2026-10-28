package dev.universaladmin.modules.moderation.alts;

import dev.universaladmin.action.ActionContext;
import dev.universaladmin.action.ActionExecutor;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.action.Actor;
import dev.universaladmin.action.Source;
import dev.universaladmin.core.PlayerNames;
import dev.universaladmin.core.PlayerRef;
import dev.universaladmin.gui.AbstractListGuiPage;
import dev.universaladmin.gui.GuiClickContext;
import dev.universaladmin.gui.GuiFramework;
import dev.universaladmin.gui.GuiItem;
import dev.universaladmin.gui.GuiPageId;
import dev.universaladmin.localization.MessageService;
import dev.universaladmin.permission.bukkit.PermissiblePermissionEvaluator;
import dev.universaladmin.scheduler.TaskScheduler;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Possible alts of one player - loaded through the audited view action, banned accounts in red. */
public final class AltsPage extends AbstractListGuiPage<AltMatch> {

    private static final GuiPageId ID = GuiPageId.core("moderation.alts");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final ActionExecutor actions;
    private final PlayerRef target;

    public AltsPage(GuiFramework framework, MessageService messages, TaskScheduler scheduler, ActionExecutor actions, PlayerRef target) {
        super(ID, framework, messages, scheduler);
        this.actions = actions;
        this.target = target;
    }

    @Override
    protected Component title(Player viewer) {
        return text("moderation.alts.gui.title", target.name());
    }

    @Override
    protected CompletableFuture<List<AltMatch>> loadItems(Player viewer) {
        Actor actor = Actor.player(viewer.getUniqueId(), PlayerNames.real(viewer), new PermissiblePermissionEvaluator(viewer));
        return actions.<AltActions.Target, List<AltMatch>>execute(AltActions.VIEW, new ActionContext(actor, Source.GUI),
                        new AltActions.Target(target.id(), target.name()))
                .thenApply(result -> result instanceof ActionResult.Success<List<AltMatch>> success ? success.value() : List.of());
    }

    @Override
    protected GuiItem render(AltMatch alt) {
        List<Component> lore = new ArrayList<>();
        if (alt.banned()) {
            lore.add(text("moderation.alts.gui.banned"));
        }
        lore.add(text("moderation.alts.gui.shared", alt.sharedAddresses()));
        lore.add(text("moderation.alts.gui.last-seen", TIME.format(alt.lastSeen())));
        Component name = Component.text(alt.name(), alt.banned() ? NamedTextColor.RED : NamedTextColor.GOLD);
        return alt.banned()
                ? GuiItem.of(Material.BARRIER, name, lore)
                : GuiItem.playerHead(Bukkit.getOfflinePlayer(alt.playerId()), name, lore);
    }

    @Override
    protected void onSelect(GuiClickContext context, AltMatch alt) {
        // Display only - the profile of the other account is one /admin away.
    }
}
