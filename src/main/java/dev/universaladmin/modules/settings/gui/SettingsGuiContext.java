package dev.universaladmin.modules.settings.gui;

import dev.universaladmin.action.ActionExecutor;
import dev.universaladmin.gui.GuiFramework;
import dev.universaladmin.localization.MessageService;
import dev.universaladmin.scheduler.TaskScheduler;
import dev.universaladmin.settings.SettingDefinition;
import dev.universaladmin.settings.SettingRegistry;
import dev.universaladmin.settings.SettingsService;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** What every settings page needs - same bundling idea as the other modules' GUI contexts. */
public record SettingsGuiContext(
        GuiFramework framework,
        MessageService messages,
        TaskScheduler scheduler,
        ActionExecutor actionExecutor,
        SettingsService settings,
        SettingRegistry registry) {

    /** Every registered setting grouped by the first segment of its config path ({@code gui}, {@code moderation}, ...). */
    public Map<String, List<SettingDefinition<?>>> sections() {
        Map<String, List<SettingDefinition<?>>> sections = new TreeMap<>();
        for (SettingDefinition<?> definition : registry.all()) {
            if (definition.key().configPath().equals("config-version")) {
                continue;
            }
            sections.computeIfAbsent(sectionOf(definition), ignored -> new java.util.ArrayList<>()).add(definition);
        }
        sections.values().forEach(list -> list.sort(java.util.Comparator.comparing(d -> d.key().configPath())));
        return sections;
    }

    public static String sectionOf(SettingDefinition<?> definition) {
        String path = definition.key().configPath();
        int dot = path.indexOf('.');
        return (dot < 0 ? path : path.substring(0, dot)).toLowerCase(Locale.ROOT);
    }
}
