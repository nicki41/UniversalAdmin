package dev.universaladmin.modules.settings.gui;

import dev.universaladmin.gui.AbstractListGuiPage;
import dev.universaladmin.gui.ConfirmationDialog;
import dev.universaladmin.gui.GuiActions;
import dev.universaladmin.gui.GuiClickContext;
import dev.universaladmin.gui.GuiClickType;
import dev.universaladmin.gui.GuiItem;
import dev.universaladmin.gui.GuiPageId;
import dev.universaladmin.gui.GuiTextInput;
import dev.universaladmin.gui.SelectionDialog;
import dev.universaladmin.localization.ComponentMessages;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.settings.SetSettingAction;
import dev.universaladmin.settings.SettingChange;
import dev.universaladmin.settings.SettingDefinition;
import dev.universaladmin.settings.SettingTypes;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Every setting of one section, with its current value, default and
 * description. Click to change (booleans toggle, enums offer their values,
 * everything else opens a text prompt pre-filled with the current value);
 * right-click resets to the default. Every change runs through
 * {@link SetSettingAction}, so it is validated, saved to {@code config.yml}
 * (comments kept) and audited. Sensitive settings are shown masked and can
 * only be changed in the file.
 */
final class SettingsSectionPage extends AbstractListGuiPage<SettingDefinition<?>> {

    private static final GuiPageId ID = GuiPageId.core("settings.section");
    private static final int WRAP = 42;

    private final SettingsGuiContext ctx;
    private final String section;

    SettingsSectionPage(SettingsGuiContext ctx, String section) {
        super(ID, ctx.framework(), ctx.messages(), ctx.scheduler());
        this.ctx = ctx;
        this.section = section;
    }

    @Override
    protected Component title(Player viewer) {
        return text("settings.gui.section.title", section);
    }

    @Override
    protected CompletableFuture<List<SettingDefinition<?>>> loadItems(Player viewer) {
        return CompletableFuture.completedFuture(ctx.sections().getOrDefault(section, List.of()));
    }

    @Override
    protected GuiItem render(SettingDefinition<?> definition) {
        List<Component> lore = new ArrayList<>();
        for (String line : wrap(definition.description())) {
            lore.add(Component.text(line, NamedTextColor.GRAY));
        }
        lore.add(Component.empty());
        lore.add(text("settings.gui.section.value", ctx.settings().display(definition)));
        if (!definition.sensitive()) {
            lore.add(text("settings.gui.section.default", formatDefault(definition)));
        }
        if (definition.requiresRestart()) {
            lore.add(text("settings.gui.section.restart"));
        }
        if (definition.sensitive()) {
            lore.add(text("settings.gui.section.sensitive"));
        } else {
            lore.add(text(isBoolean(definition) ? "settings.gui.section.click-toggle"
                    : definition.type().suggestions().isEmpty() ? "settings.gui.section.click-edit" : "settings.gui.section.click-choose"));
            if (!isDefault(definition)) {
                lore.add(text("settings.gui.section.right-click-reset"));
            }
        }
        String path = definition.key().configPath();
        String name = path.startsWith(section + ".") ? path.substring(section.length() + 1) : path;
        return GuiItem.of(iconFor(definition), Component.text(name, NamedTextColor.GOLD), lore);
    }

    @Override
    protected void onSelect(GuiClickContext context, SettingDefinition<?> definition) {
        if (definition.sensitive()) {
            return;
        }
        Player viewer = context.viewer();
        String path = definition.key().configPath();
        if (context.clickType() == GuiClickType.RIGHT || context.clickType() == GuiClickType.SHIFT_RIGHT) {
            ConfirmationDialog.open(viewer, framework, messages, text("settings.gui.reset.title"),
                    List.of(text("settings.gui.reset.body", path, formatDefault(definition))),
                    ConfirmationDialog.DangerLevel.WARNING,
                    confirm -> apply(viewer, path, null),
                    GuiClickContext::back);
            return;
        }
        if (isBoolean(definition)) {
            boolean current = Boolean.parseBoolean(ctx.settings().display(definition));
            apply(viewer, path, String.valueOf(!current));
            return;
        }
        List<String> options = definition.type().suggestions();
        if (!options.isEmpty()) {
            SelectionDialog.open(viewer, framework, messages, ctx.scheduler(), text("settings.gui.choose.title", path), options,
                    option -> GuiItem.of(option.equals(ctx.settings().display(definition)) ? Material.LIME_DYE : Material.GRAY_DYE,
                            Component.text(option, NamedTextColor.WHITE)),
                    (selectCtx, option) -> apply(viewer, path, option));
            return;
        }
        GuiTextInput.request(viewer, text("settings.gui.edit.title"), text("settings.gui.edit.label", path, definition.type().describe()),
                ctx.settings().display(definition), text("gui.confirm"), text("gui.cancel"),
                typed -> apply(viewer, path, typed),
                () -> this.open(viewer));
    }

    private void apply(Player viewer, String path, String value) {
        GuiActions.runAndReport(ctx.actionExecutor(), ctx.scheduler(), messages, viewer,
                SetSettingAction.ID, new SetSettingAction.Input(path, value), null, () -> this.open(viewer));
    }

    private static boolean isBoolean(SettingDefinition<?> definition) {
        return definition.type() == SettingTypes.BOOLEAN;
    }

    private boolean isDefault(SettingDefinition<?> definition) {
        return Objects.equals(ctx.settings().display(definition), formatDefault(definition));
    }

    private static <T> String formatDefault(SettingDefinition<T> definition) {
        return definition.type().format(definition.defaultValue());
    }

    private static Material iconFor(SettingDefinition<?> definition) {
        if (definition.sensitive()) {
            return Material.BARRIER;
        }
        if (definition.type() == SettingTypes.BOOLEAN) {
            return Material.LEVER;
        }
        if (definition.type() == SettingTypes.DURATION) {
            return Material.CLOCK;
        }
        if (definition.type() == SettingTypes.STRING_LIST) {
            return Material.BOOK;
        }
        if (definition.type() == SettingTypes.INTEGER || definition.type() == SettingTypes.LONG
                || definition.type() == SettingTypes.DOUBLE) {
            return Material.COMPARATOR;
        }
        if (!definition.type().suggestions().isEmpty()) {
            return Material.NAME_TAG;
        }
        return Material.PAPER;
    }

    /** Splits long descriptions into lore lines of at most {@link #WRAP} characters, on word boundaries. */
    static List<String> wrap(String text) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return lines;
        }
        StringBuilder line = new StringBuilder();
        for (String word : text.trim().split("\\s+")) {
            if (!line.isEmpty() && line.length() + 1 + word.length() > WRAP) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (!line.isEmpty()) {
                line.append(' ');
            }
            line.append(word);
        }
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }
}
