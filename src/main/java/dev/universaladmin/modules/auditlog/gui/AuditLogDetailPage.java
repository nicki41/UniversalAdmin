package dev.universaladmin.modules.auditlog.gui;

import dev.universaladmin.action.ActorType;
import dev.universaladmin.audit.AuditEvent;
import dev.universaladmin.gui.AbstractGuiPage;
import dev.universaladmin.gui.GuiFramework;
import dev.universaladmin.gui.GuiItem;
import dev.universaladmin.gui.GuiLayout;
import dev.universaladmin.gui.GuiPageId;
import dev.universaladmin.gui.GuiRenderContext;
import dev.universaladmin.localization.MessageService;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Static detail view of one {@link AuditEvent} - Actor/Action/Target/Time/
 * Source/Result/Reason/Old-New/Metadata, as required by docs/user/audit-log.md.
 * Not registered in {@link dev.universaladmin.gui.GuiRegistry}: like
 * {@link dev.universaladmin.gui.ConfirmationDialog}, it is ephemeral - built
 * fresh with the specific event to show every time {@link AuditLogListPage}
 * opens one.
 */
public final class AuditLogDetailPage extends AbstractGuiPage {

    public static final GuiPageId ID = GuiPageId.core("audit-log.detail");

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT).withZone(ZoneId.systemDefault());

    private final AuditEvent event;

    public AuditLogDetailPage(GuiFramework framework, MessageService messages, AuditEvent event) {
        super(ID, framework, messages);
        this.event = event;
    }

    @Override
    protected Component title(Player viewer) {
        return text("audit.gui.detail-title");
    }

    @Override
    protected void renderContent(GuiRenderContext context) {
        List<GuiItem> fields = new ArrayList<>();
        fields.add(actorField());
        fields.add(field(Material.WRITABLE_BOOK, "audit.gui.field.action", event.type().toString()));
        if (event.module() != null) {
            fields.add(field(Material.CHEST, "audit.gui.field.module", event.module()));
        }
        if (event.target() != null) {
            fields.add(field(Material.TARGET, "audit.gui.field.target",
                    event.target().displayName() + " (" + event.target().type() + ": " + event.target().id() + ")"));
        }
        fields.add(field(Material.CLOCK, "audit.gui.field.time", TIME_FORMAT.format(event.timestamp())));
        fields.add(field(Material.COMPASS, "audit.gui.field.source", event.source().toString()));
        fields.add(field(event.success() ? Material.LIME_DYE : Material.RED_DYE, "audit.gui.field.result",
                text(event.success() ? "audit.gui.result.success" : "audit.gui.result.failure")));
        if (event.reason() != null) {
            fields.add(field(Material.PAPER, "audit.gui.field.reason", event.reason()));
        }
        if (event.oldValue() != null || event.newValue() != null) {
            fields.add(field(Material.HOPPER, "audit.gui.field.old-new",
                    (event.oldValue() == null ? "?" : event.oldValue()) + " -> " + (event.newValue() == null ? "?" : event.newValue())));
        }
        if (event.world() != null || event.position() != null) {
            String location = (event.world() == null ? "" : event.world())
                    + (event.position() == null ? "" : " " + formatPosition(event));
            fields.add(field(Material.MAP, "audit.gui.field.location", location.trim()));
        }
        if (!event.metadata().isEmpty()) {
            fields.add(metadataField());
        }
        if (event.correlationId() != null) {
            fields.add(field(Material.STRING, "audit.gui.field.correlation-id", event.correlationId()));
        }

        for (int i = 0; i < fields.size() && i < GuiLayout.contentSize(); i++) {
            context.view().place(GuiLayout.contentSlot(i), fields.get(i));
        }
    }

    private String formatPosition(AuditEvent event) {
        var position = event.position();
        return "%.1f, %.1f, %.1f".formatted(position.x(), position.y(), position.z());
    }

    private GuiItem actorField() {
        Component value = Component.text(event.actor().displayName() + " (" + event.actor().type() + ")", NamedTextColor.GRAY);
        if (event.actor().type() == ActorType.PLAYER && event.actor().playerId() != null) {
            return GuiItem.playerHead(Bukkit.getOfflinePlayer(event.actor().playerId()), text("audit.gui.field.actor"), List.of(value));
        }
        return field(Material.PLAYER_HEAD, "audit.gui.field.actor", value);
    }

    private GuiItem field(Material material, String labelKey, Component value) {
        return GuiItem.of(material, text(labelKey), List.of(value));
    }

    private GuiItem field(Material material, String labelKey, String value) {
        return field(material, labelKey, Component.text(value, NamedTextColor.GRAY));
    }

    private GuiItem metadataField() {
        List<Component> lore = new ArrayList<>();
        for (Map.Entry<String, Object> entry : event.metadata().entrySet()) {
            lore.add(Component.text(entry.getKey() + ": " + entry.getValue(), NamedTextColor.GRAY));
        }
        return GuiItem.of(Material.BOOK, text("audit.gui.field.metadata"), lore);
    }
}
