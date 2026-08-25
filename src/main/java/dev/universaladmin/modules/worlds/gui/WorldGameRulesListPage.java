package dev.universaladmin.modules.worlds.gui;

import dev.universaladmin.gui.AbstractListGuiPage;
import dev.universaladmin.gui.GuiClickContext;
import dev.universaladmin.gui.GuiItem;
import dev.universaladmin.gui.GuiPageId;
import dev.universaladmin.gui.GuiTextInput;
import dev.universaladmin.modules.worlds.action.SetGameRuleInput;
import dev.universaladmin.modules.worlds.action.WorldActionIds;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Every gamerule {@link World#getGameRules()} currently reports, read
 * dynamically - no rule is hardcoded, so a future Minecraft version adding
 * new ones shows up here with no code change (see
 * docs/user/modules/worlds.md's "Gamerules" section). {@link
 * GameRule#getByName(String)}{@code .getType()} decides the edit widget:
 * {@code Boolean} toggles in place on click, everything else (today just
 * {@code Integer}, but this branch is what covers "weitere Typen
 * entsprechend API" without another {@code if}) opens a {@link GuiTextInput}
 * prompt. See {@code SetGameRuleAction}'s javadoc for why the string-based
 * {@code getGameRuleValue}/{@code getByName} pair is used deliberately
 * despite being flagged deprecated-for-removal on the target Paper API
 * build - the typed replacement chain is equally deprecated there, with no
 * documented alternative yet.
 */
@SuppressWarnings({"deprecation", "removal"})
public final class WorldGameRulesListPage extends AbstractListGuiPage<String> {

    public static final GuiPageId ID = GuiPageId.core("worlds.gamerules");

    private final WorldsGuiContext ctx;
    private final String worldName;

    public WorldGameRulesListPage(WorldsGuiContext ctx, String worldName) {
        super(ID, ctx.framework(), ctx.messages(), ctx.scheduler());
        this.ctx = ctx;
        this.worldName = worldName;
    }

    @Override
    protected Component title(Player viewer) {
        return text("worlds.gui.gamerules.title", worldName);
    }

    @Override
    protected CompletableFuture<List<String>> loadItems(Player viewer) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return CompletableFuture.completedFuture(List.of());
        }
        List<String> rules = Arrays.asList(world.getGameRules());
        rules.sort(String.CASE_INSENSITIVE_ORDER);
        return CompletableFuture.completedFuture(rules);
    }

    @Override
    protected GuiItem render(String ruleName) {
        String value = currentValue(ruleName);
        return GuiItem.of(Material.COMMAND_BLOCK, Component.text(ruleName, NamedTextColor.GOLD),
                List.of(Component.text(value, NamedTextColor.GRAY)));
    }

    @Override
    protected void onSelect(GuiClickContext context, String ruleName) {
        Player viewer = context.viewer();
        GameRule<?> rule = GameRule.getByName(ruleName);
        if (rule != null && rule.getType() == Boolean.class) {
            toggleBoolean(viewer, ruleName);
            return;
        }
        promptValue(viewer, ruleName);
    }

    private String currentValue(String ruleName) {
        World world = Bukkit.getWorld(worldName);
        String value = world != null ? world.getGameRuleValue(ruleName) : null;
        return value != null ? value : "?";
    }

    private void toggleBoolean(Player viewer, String ruleName) {
        boolean current = Boolean.parseBoolean(currentValue(ruleName));
        apply(viewer, ruleName, String.valueOf(!current));
    }

    private void promptValue(Player viewer, String ruleName) {
        String current = currentValue(ruleName);
        GuiTextInput.request(viewer, Component.text(ruleName), text("worlds.gui.prompt.gamerule-value"), current,
                text("gui.confirm"), text("gui.cancel"),
                submitted -> apply(viewer, ruleName, submitted),
                () -> this.open(viewer));
    }

    private void apply(Player viewer, String ruleName, String value) {
        WorldsGuiActions.runAction(ctx, viewer, WorldActionIds.SET_GAME_RULE,
                new SetGameRuleInput(worldName, ruleName, value), () -> this.open(viewer));
    }
}
