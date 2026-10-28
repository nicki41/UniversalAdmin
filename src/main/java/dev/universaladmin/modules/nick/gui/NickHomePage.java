package dev.universaladmin.modules.nick.gui;

import dev.universaladmin.action.ActionExecutor;
import dev.universaladmin.action.ActionId;
import dev.universaladmin.core.PlayerNames;
import dev.universaladmin.core.PlayerRef;
import dev.universaladmin.gui.AbstractGuiPage;
import dev.universaladmin.gui.ConfirmationDialog;
import dev.universaladmin.gui.GuiActions;
import dev.universaladmin.gui.GuiButton;
import dev.universaladmin.gui.GuiClickContext;
import dev.universaladmin.gui.GuiForm;
import dev.universaladmin.gui.GuiFramework;
import dev.universaladmin.gui.GuiItem;
import dev.universaladmin.gui.GuiLayout;
import dev.universaladmin.gui.GuiPageId;
import dev.universaladmin.gui.GuiRenderContext;
import dev.universaladmin.gui.GuiView;
import dev.universaladmin.localization.MessageService;
import dev.universaladmin.modules.nick.NickNames;
import dev.universaladmin.modules.nick.NickPermissions;
import dev.universaladmin.modules.nick.action.NickActions;
import dev.universaladmin.modules.nick.action.NickInput;
import dev.universaladmin.scheduler.TaskScheduler;
import dev.universaladmin.status.Nicks;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * {@code /admin} → Nick: your own nick (random, chosen in a form, removed)
 * and - for staff who may see real names - every nick shown right now.
 * Every button runs a {@link NickActions} action.
 */
public final class NickHomePage extends AbstractGuiPage {

    public static final GuiPageId ID = GuiPageId.core("nick.home");

    /** Rows 3 and 4 of the content area list the active nicks. */
    private static final int FIRST_LIST_INDEX = 18;

    private final Nicks nicks;
    private final ActionExecutor actions;
    private final TaskScheduler scheduler;

    public NickHomePage(GuiFramework framework, MessageService messages, Nicks nicks, ActionExecutor actions, TaskScheduler scheduler) {
        super(ID, framework, messages);
        this.nicks = nicks;
        this.actions = actions;
        this.scheduler = scheduler;
    }

    @Override
    protected Component title(Player viewer) {
        return text("nick.gui.title");
    }

    @Override
    protected void renderContent(GuiRenderContext context) {
        GuiView view = context.view();
        Player viewer = context.viewer();
        Optional<Nicks.Nick> own = nicks.nick(viewer.getUniqueId());

        view.place(GuiLayout.contentSlot(2), GuiItem.of(Material.NAME_TAG,
                own.map(nick -> text("nick.gui.status-nicked", nick.name())).orElseGet(() -> text("nick.gui.status-plain")),
                own.map(nick -> List.of(skinLine(nick))).orElse(List.of(text("nick.gui.status-plain-hint")))));
        view.place(GuiLayout.contentSlot(4), new GuiButton(GuiItem.of(Material.ENDER_EYE, text("nick.gui.random"),
                        List.of(text("nick.gui.random-hint"))),
                NickPermissions.USE,
                ctx -> run(ctx.viewer(), NickActions.SET, new NickInput(viewer.getUniqueId(), PlayerNames.real(viewer), null,
                        NickInput.Skin.random()))), viewer);
        view.place(GuiLayout.contentSlot(5), new GuiButton(GuiItem.of(Material.WRITABLE_BOOK, text("nick.gui.choose"),
                        List.of(text("nick.gui.choose-hint"))),
                NickPermissions.USE, ctx -> choose(ctx.viewer(), own)), viewer);
        if (own.isPresent()) {
            view.place(GuiLayout.contentSlot(6), new GuiButton(GuiItem.of(Material.BARRIER, text("nick.gui.remove"),
                            List.of(text("nick.gui.remove-hint", own.get().realName()))),
                    NickPermissions.USE,
                    ctx -> run(ctx.viewer(), NickActions.CLEAR, new PlayerRef(viewer.getUniqueId(), own.get().realName()))), viewer);
        }

        if (viewer.hasPermission(NickPermissions.SEE.value())) {
            renderActive(view, viewer);
        }
    }

    private void renderActive(GuiView view, Player viewer) {
        List<Nicks.Nick> active = new ArrayList<>(nicks.active());
        active.sort(Comparator.comparing(Nicks.Nick::name, String.CASE_INSENSITIVE_ORDER));
        int room = GuiLayout.contentSize() - FIRST_LIST_INDEX;
        if (active.isEmpty()) {
            view.place(GuiLayout.contentSlot(FIRST_LIST_INDEX + 4), GuiItem.of(framework.icons().empty(), text("nick.gui.none-active")));
            return;
        }
        for (int i = 0; i < Math.min(active.size(), room); i++) {
            Nicks.Nick nick = active.get(i);
            List<Component> lore = new ArrayList<>();
            lore.add(text("nick.gui.real-name", nick.realName()));
            lore.add(skinLine(nick));
            if (nick.server() != null) {
                lore.add(text("nick.gui.server", nick.server()));
            }
            if (viewer.hasPermission(NickPermissions.OTHERS.value())) {
                lore.add(text("nick.gui.remove-other-hint"));
            }
            view.place(GuiLayout.contentSlot(FIRST_LIST_INDEX + i), new GuiButton(
                    GuiItem.playerHead(Bukkit.getOfflinePlayer(nick.playerId()), text("nick.gui.entry", nick.name()), lore),
                    NickPermissions.OTHERS, ctx -> confirmRemove(ctx, nick)), viewer);
        }
        if (active.size() > room) {
            view.place(GuiLayout.contentSlot(GuiLayout.contentSize() - 1),
                    GuiItem.of(Material.PAPER, text("nick.gui.more", active.size() - room + 1)));
        }
    }

    private Component skinLine(Nicks.Nick nick) {
        return nick.skinOf() == null ? text("nick.gui.skin-own") : text("nick.gui.skin-of", nick.skinOf());
    }

    private void confirmRemove(GuiClickContext ctx, Nicks.Nick nick) {
        ConfirmationDialog.open(ctx.viewer(), framework, messages, text("nick.gui.remove-other", nick.name()),
                List.of(text("nick.gui.remove-other-confirm", nick.name(), nick.realName())), ConfirmationDialog.DangerLevel.WARNING,
                confirm -> run(confirm.viewer(), NickActions.CLEAR, new PlayerRef(nick.playerId(), nick.realName())),
                GuiClickContext::back);
    }

    /** One form: the name, and where the skin comes from. */
    private void choose(Player viewer, Optional<Nicks.Nick> own) {
        List<GuiForm.Option> skins = List.of(
                new GuiForm.Option("name", text("nick.gui.form.skin-name")),
                new GuiForm.Option("own", text("nick.gui.form.skin-own")),
                new GuiForm.Option("random", text("nick.gui.form.skin-random")),
                new GuiForm.Option("account", text("nick.gui.form.skin-account")));
        GuiForm.builder(text("nick.gui.choose"))
                .text("name", text("nick.gui.form.name"), own.map(Nicks.Nick::name).orElse(""), 16)
                .choice("skin", text("nick.gui.form.skin"), skins, "name")
                .text("account", text("nick.gui.form.account"), "", 16)
                .open(viewer, text("gui.confirm"), text("gui.cancel"), response -> {
                    String name = response.text("name").trim();
                    if (!NickNames.valid(name)) {
                        viewer.sendMessage(text("nick.invalid-name", name));
                        this.open(viewer);
                        return;
                    }
                    String account = response.text("account").trim();
                    NickInput.Skin skin = switch (response.choice("skin")) {
                        case "own" -> NickInput.Skin.own();
                        case "random" -> NickInput.Skin.random();
                        case "account" -> account.isEmpty() ? NickInput.Skin.ofName() : NickInput.Skin.of(account);
                        default -> NickInput.Skin.ofName();
                    };
                    if (skin.kind() == NickInput.Kind.ACCOUNT && !NickNames.PATTERN.matcher(account).matches()) {
                        viewer.sendMessage(text("nick.invalid-name", account));
                        this.open(viewer);
                        return;
                    }
                    run(viewer, NickActions.SET, new NickInput(viewer.getUniqueId(), PlayerNames.real(viewer), name, skin));
                }, () -> this.open(viewer));
    }

    private <I> void run(Player viewer, ActionId id, I input) {
        GuiActions.runAndReport(actions, scheduler, messages, viewer, id, input, null, () -> this.open(viewer));
    }
}
