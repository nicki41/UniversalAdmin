package dev.universaladmin.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.universaladmin.action.Actor;
import dev.universaladmin.action.ActorType;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

/** The stand-in sender remote actors run commands with. */
class RemoteCommandServiceTest {

    @Test
    void senderChecksTheActorsPermissionsAndForwardsMessages() {
        Actor actor = Actor.remote("Discord:Nick", node -> node.value().startsWith("universaladmin.moderation."));
        List<Component> replies = new ArrayList<>();

        CommandSender sender = RemoteCommandService.sender(actor, replies::add);

        assertEquals(ActorType.REMOTE, actor.type());
        assertTrue(sender.hasPermission("universaladmin.moderation.ban"));
        assertFalse(sender.hasPermission("universaladmin.server.shutdown"));
        assertFalse(sender.hasPermission("not a valid node!"));
        assertFalse(sender.isOp());
        assertEquals("Discord:Nick", sender.getName());

        sender.sendMessage(Component.text("Banned Steve."));
        sender.sendMessage("legacy text");

        assertEquals(List.of("Banned Steve.", "legacy text"),
                replies.stream().map(PlainTextComponentSerializer.plainText()::serialize).toList());
        assertEquals(sender, sender);
    }
}
