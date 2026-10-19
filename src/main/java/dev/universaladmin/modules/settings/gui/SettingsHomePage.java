package dev.universaladmin.modules.settings.gui;

import dev.universaladmin.gui.AbstractListGuiPage;
import dev.universaladmin.gui.GuiClickContext;
import dev.universaladmin.gui.GuiItem;
import dev.universaladmin.gui.GuiPageId;
import dev.universaladmin.settings.SettingDefinition;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * {@code /admin} → Settings: one button per settings section (the first
 * segment of the config path - {@code gui}, {@code moderation},
 * {@code telemetry}, ...), built from {@code SettingRegistry} so module and
 * extension settings appear without any change here.
 */
public final class SettingsHomePage extends AbstractListGuiPage<Map.Entry<String, List<SettingDefinition<?>>>> {

    public static final GuiPageId ID = GuiPageId.core("settings.home");

    private final SettingsGuiContext ctx;

    public SettingsHomePage(SettingsGuiContext ctx) {
        super(ID, ctx.framework(), ctx.messages(), ctx.scheduler());
        this.ctx = ctx;
    }

    @Override
    protected Component title(Player viewer) {
        return text("settings.gui.home.title");
    }

    @Override
    protected CompletableFuture<List<Map.Entry<String, List<SettingDefinition<?>>>>> loadItems(Player viewer) {
        return CompletableFuture.completedFuture(List.copyOf(ctx.sections().entrySet()));
    }

    @Override
    protected GuiItem render(Map.Entry<String, List<SettingDefinition<?>>> section) {
        long restart = section.getValue().stream().filter(SettingDefinition::requiresRestart).count();
        List<Component> lore = new java.util.ArrayList<>();
        lore.add(text("settings.gui.home.section-count", section.getValue().size()));
        if (restart > 0) {
            lore.add(text("settings.gui.home.section-restart", restart));
        }
        lore.add(text("settings.gui.home.section-open"));
        return GuiItem.of(iconFor(section.getKey()), Component.text(section.getKey(), NamedTextColor.GOLD), lore);
    }

    @Override
    protected void onSelect(GuiClickContext context, Map.Entry<String, List<SettingDefinition<?>>> section) {
        context.open(new SettingsSectionPage(ctx, section.getKey()));
    }

    private static Material iconFor(String section) {
        return switch (section) {
            case "general" -> Material.COMPASS;
            case "database" -> Material.BOOKSHELF;
            case "gui" -> Material.CHEST;
            case "audit" -> Material.WRITABLE_BOOK;
            case "modules" -> Material.COMMAND_BLOCK;
            case "performance" -> Material.CLOCK;
            case "maintenance" -> Material.BARRIER;
            case "telemetry" -> Material.SPYGLASS;
            case "update" -> Material.EXPERIENCE_BOTTLE;
            case "commands" -> Material.OAK_SIGN;
            case "moderation" -> Material.IRON_SWORD;
            case "players" -> Material.PLAYER_HEAD;
            case "server" -> Material.REDSTONE_BLOCK;
            case "whitelist" -> Material.PAPER;
            case "worlds" -> Material.GRASS_BLOCK;
            case "reports" -> Material.BELL;
            case "chat" -> Material.NAME_TAG;
            default -> Material.COMPARATOR;
        };
    }
}
