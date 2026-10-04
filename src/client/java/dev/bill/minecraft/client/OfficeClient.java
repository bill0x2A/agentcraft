package dev.bill.minecraft.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.bill.minecraft.AgentActivity;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;

final class OfficeClient {
    static volatile List<OfficeBridge.Session> sessions = List.of();
    static volatile String error = "Connecting to bridge…";
    private static boolean polling, held;
    private static long nextPoll;

    static boolean localWorld(Minecraft client) {
        return client.player != null && client.getSingleplayerServer() != null && !client.getSingleplayerServer().isPublished();
    }

    static void register() {
        OfficeWorld.register();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            boolean down = InputConstants.isKeyDown(InputConstants.KEY_F7);
            if (down && !held && localWorld(client) && !(client.gui.screen() instanceof TerminalScreen) && !(client.gui.screen() instanceof BrowserPreviewScreen))
                client.gui.setScreen(new OfficeScreen(client.gui.screen()));
            held = down;
            if (localWorld(client) && !polling && System.nanoTime() >= nextPoll) refresh(client);
        });
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (OfficeWorld.binding(entity.getUUID()) == null) return InteractionResult.PASS;
            Minecraft client = Minecraft.getInstance();
            if (!localWorld(client)) return InteractionResult.PASS;
            if (level.isClientSide() && !OfficeTools.useOnMob(client, player, entity.getUUID())) openMob(client, entity.getUUID());
            return InteractionResult.SUCCESS;
        });
        OfficeTools.register();
    }

    static void refresh(Minecraft client) {
        if (polling) return;
        polling = true;
        OfficeBridge.sessions().whenComplete((result, failure) -> client.execute(() -> {
            if (failure == null) { sessions = result; error = null; }
            else { error = "Bridge offline — start ./scripts/terminal"; }
            polling = false; nextPoll = System.nanoTime()+2_000_000_000L;
        }));
    }

    static String status(String terminal) {
        if (error != null) return "Bridge offline";
        return sessions.stream().filter(s -> s.terminal().equals(terminal)).findFirst()
            .map(OfficeBridge.Session::status).orElse("Unavailable");
    }

    static OfficeBridge.Session session(String terminal) {
        return sessions.stream().filter(s -> s.terminal().equals(terminal)).findFirst().orElse(null);
    }

    static AgentActivity activity(String terminal) {
        if (error != null) return AgentActivity.OFFLINE;
        OfficeBridge.Session session = session(terminal);
        if (session == null) return AgentActivity.OFFLINE;
        if (session.agent().isEmpty()) return AgentActivity.RESTING;
        return switch (session.reportedStatus()) {
            case "working" -> AgentActivity.WORKING;
            case "blocked" -> AgentActivity.ATTENTION;
            case "idle", "done" -> AgentActivity.RESTING;
            default -> AgentActivity.UNKNOWN;
        };
    }

    static boolean openMob(Minecraft client, UUID mob) {
        OfficeWorld.Binding binding = OfficeWorld.binding(mob);
        if (!localWorld(client) || binding == null) return false;
        if (status(binding.terminal()).equals("Unavailable")) {
            message(client, binding.label()+": session unavailable. Its original terminal has ended.");
            return false;
        }
        client.gui.setScreen(new TerminalScreen(client.gui.screen(), binding.terminal()));
        return true;
    }

    static void link(Minecraft client, OfficeBridge.Session session, OfficeScreen screen) {
        var server = client.getSingleplayerServer();
        if (!localWorld(client)) { screen.finished("Open a private single-player world first"); return; }
        UUID player = client.player.getUUID();
        server.execute(() -> {
            try {
                OfficeWorld.Binding binding = OfficeWorld.spawn(server, player, session, screen.character());
                client.execute(() -> {
                    if (client.getSingleplayerServer() == server) {
                        if (client.gui.screen() == screen) screen.onClose();
                        message(client, "Created " + binding.label()+". Right-click the mob to resume its terminal.");
                    }
                });
            } catch (Exception e) { client.execute(() -> screen.finished(e.getMessage())); }
        });
    }

    static void rename(Minecraft client, UUID mob, String label, RenameScreen screen) {
        var server = client.getSingleplayerServer();
        if (!localWorld(client)) { screen.finished("Open a private single-player world first"); return; }
        server.execute(() -> {
            try {
                OfficeWorld.Binding binding = OfficeWorld.rename(server, mob, label);
                client.execute(() -> {
                    if (client.getSingleplayerServer() == server) {
                        if (client.gui.screen() == screen) screen.onClose();
                        message(client, "Renamed to " + binding.label() + ".");
                        TerminalDisplays.renamed(client, binding.terminal());
                    }
                });
            } catch (Exception e) { client.execute(() -> screen.finished(e.getMessage())); }
        });
    }

    static void message(Minecraft client, String message) {
        if (client.player != null) client.player.sendSystemMessage(Component.literal(message));
    }
}
