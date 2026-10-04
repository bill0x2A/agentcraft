package dev.bill.minecraft.client;

import dev.bill.minecraft.AgentActivity;
import dev.bill.minecraft.ModdingWorkspace;
import dev.bill.minecraft.OfficeEntities;
import dev.bill.minecraft.OfficeItems;
import java.util.*;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** What each office item does. The items themselves are plain; see OfficeItems. */
final class OfficeTools {
    // The Home Flag takes two clicks: an agent mob, then a block. The Terminal Display can do the same.
    private static UUID selected;
    private static Item selectedFor;
    private static String lastSaid = "";
    private static long quietUntil;
    private static boolean hatching;

    static void register() {
        EntityRendererRegistry.register(OfficeEntities.TERMINAL, TerminalDisplayRenderer::new);
        TerminalDisplays.register();
        MonitorPanels.register();
        UseBlockCallback.EVENT.register(OfficeTools::useOnBlock);
        UseItemCallback.EVENT.register(OfficeTools::useInAir);
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(ModdingWorkspace.MOD_ID, "status_tablet"), OfficeTools::tablet);
    }

    /** Client side of a right-click on an agent mob. False leaves the click to the terminal. */
    static boolean useOnMob(Minecraft client, Player player, UUID mob) {
        OfficeWorld.Binding binding = OfficeWorld.binding(mob);
        if (binding == null) return false;
        if (player.isHolding(OfficeItems.NAME_BADGE)) client.gui.setScreen(new RenameScreen(client.gui.screen(), mob));
        else if (player.isHolding(OfficeItems.MEMO)) {
            if (OfficeClient.status(binding.terminal()).equals("Unavailable") || OfficeClient.error != null)
                say(client, binding.label() + ": the session is unavailable, so it cannot receive a memo.");
            else client.gui.setScreen(new MemoScreen(client.gui.screen(), mob));
        }
        else if (player.isHolding(OfficeItems.DISMISSAL_SLIP)) dismiss(client, mob);
        else if (player.isHolding(OfficeItems.HOME_FLAG)) select(client, mob, OfficeItems.HOME_FLAG, "Right-click a block to set the home of " + binding.label() + ".");
        else if (player.isHolding(OfficeItems.TERMINAL_DISPLAY)) select(client, mob, OfficeItems.TERMINAL_DISPLAY, "Right-click a block to place the terminal display of " + binding.label() + ".");
        else return false;
        return true;
    }

    private static void select(Minecraft client, UUID mob, Item item, String message) {
        selected = mob; selectedFor = item; say(client, message);
    }

    private static InteractionResult useOnBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
        ItemStack stack = player.getItemInHand(hand);
        boolean flag = stack.is(OfficeItems.HOME_FLAG), display = stack.is(OfficeItems.TERMINAL_DISPLAY);
        boolean egg = stack.is(OfficeItems.CLAUDE_EGG) || stack.is(OfficeItems.CODEX_EGG);
        if (!flag && !display && !egg) return InteractionResult.PASS;
        Minecraft client = Minecraft.getInstance();
        if (!OfficeClient.localWorld(client)) return InteractionResult.PASS;
        // The client side does the work. The server side only keeps the block itself unused.
        if (!level.isClientSide()) return InteractionResult.SUCCESS;
        BlockPos target = hit.getBlockPos().relative(hit.getDirection());
        OfficeWorld.Binding chosen = selected == null || selectedFor != stack.getItem() ? null : OfficeWorld.binding(selected);
        if (egg) hatch(client, stack.is(OfficeItems.CLAUDE_EGG) ? "claude" : "codex", target);
        // With no agent mob chosen, the display asks for its terminal after it is placed.
        else if (display) { if (chosen != null) selected = null; TerminalDisplays.place(client, chosen == null ? "" : chosen.terminal(), target, hit.getDirection(), null); }
        else if (chosen == null) say(client, "Right-click an agent mob with the Home Flag first.");
        else { selected = null; moveHome(client, chosen.mob(), target); }
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult useInAir(Player player, Level level, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        boolean tablet = stack.is(OfficeItems.STATUS_TABLET), book = stack.is(OfficeItems.HANDBOOK);
        if (!tablet && !book || !level.isClientSide()) return InteractionResult.PASS;
        Minecraft client = Minecraft.getInstance();
        if (book) client.gui.setScreen(new BookViewScreen(new BookViewScreen.BookAccess(Handbook.pages())));
        else if (OfficeClient.localWorld(client)) client.gui.setScreen(new OfficeScreen(client.gui.screen()));
        else say(client, "The office works in a private single-player world.");
        return InteractionResult.SUCCESS;
    }

    private static void dismiss(Minecraft client, UUID mob) {
        var server = client.getSingleplayerServer();
        server.execute(() -> {
            try {
                OfficeWorld.Binding binding = OfficeWorld.dismiss(server, mob);
                client.execute(() -> OfficeClient.message(client, "Dismissed " + binding.label() + ". Its session continues; press F7 to link it again."));
            } catch (Exception e) { client.execute(() -> say(client, e.getMessage())); }
        });
    }

    private static void moveHome(Minecraft client, UUID mob, BlockPos target) {
        var server = client.getSingleplayerServer(); UUID player = client.player.getUUID();
        server.execute(() -> {
            try {
                OfficeWorld.Binding binding = OfficeWorld.moveHome(server, player, mob, target);
                client.execute(() -> OfficeClient.message(client, binding.label() + " has a new home."));
            } catch (Exception e) {
                // Keep the selection, so the player can choose a better block at once.
                client.execute(() -> { selected = mob; selectedFor = OfficeItems.HOME_FLAG; say(client, e.getMessage()); });
            }
        });
    }

    /** Creates a shell session and links it at the block. The egg starts no agent. */
    private static void hatch(Minecraft client, String character, BlockPos target) {
        if (hatching) return;
        if (OfficeClient.error != null) { say(client, OfficeClient.error); return; }
        hatching = true;
        var server = client.getSingleplayerServer(); UUID player = client.player.getUUID();
        Set<String> used = new HashSet<>();
        OfficeClient.sessions.forEach(session -> used.add(session.label()));
        OfficeWorld.bindings().forEach(binding -> used.add(binding.label()));
        String name = character.equals("claude") ? "Claude" : "Codex";
        int number = 1;
        while (used.contains(name + " " + number)) number++;
        String label = name + " " + number;
        server.execute(() -> {
            // Refuse a bad block before the session exists.
            try { OfficeWorld.checkHome(server, player, target); }
            catch (Exception e) { client.execute(() -> { hatching = false; say(client, e.getMessage()); }); return; }
            OfficeBridge.create(label).whenComplete((session, failure) -> {
                if (failure != null) { client.execute(() -> { hatching = false; say(client, "Could not create a session: " + failure.getCause().getMessage()); }); return; }
                server.execute(() -> {
                    try {
                        OfficeWorld.Binding binding = OfficeWorld.spawn(server, player, session, character, target);
                        client.execute(() -> { hatching = false; OfficeClient.refresh(client);
                            OfficeClient.message(client, "Created " + binding.label() + ". Right-click the mob to open its terminal."); });
                    } catch (Exception e) {
                        client.execute(() -> { hatching = false; OfficeClient.refresh(client);
                            OfficeClient.message(client, e.getMessage() + ". The session " + label + " exists; press F7 to link it."); });
                    }
                });
            });
        });
    }

    /** A held right mouse button repeats the click, so do not repeat the same line. */
    static void say(Minecraft client, String message) {
        long now = System.nanoTime();
        if (message.equals(lastSaid) && now < quietUntil) return;
        lastSaid = message; quietUntil = now + 2_000_000_000L;
        OfficeClient.message(client, message);
    }

    private record Row(String label, String status, String where, int color, int order, double distance) {}

    private static void tablet(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.gui.screen() != null || !client.player.isHolding(OfficeItems.STATUS_TABLET) || !OfficeClient.localWorld(client)) return;
        var font = client.font;
        String dimension = client.level.dimension().identifier().toString();
        List<Row> rows = new ArrayList<>();
        for (OfficeWorld.Binding binding : OfficeWorld.bindings()) {
            if (binding.archived()) { rows.add(new Row(binding.label(), "Filed away", "", 0xff8c9cb2, 5, 0)); continue; }
            AgentActivity activity = OfficeClient.activity(binding.terminal());
            String status = activity == AgentActivity.OFFLINE ? OfficeClient.status(binding.terminal()) : activity.label;
            int color = switch (activity) {
                case WORKING -> 0xff62f2b2; case ATTENTION -> 0xffffd05a; case OFFLINE -> 0xffe06c75; default -> 0xffa8bad2;
            };
            int order = switch (activity) { case ATTENTION -> 0; case WORKING -> 1; case OFFLINE -> 4; default -> 2; };
            var entity = client.level.getEntity(binding.mob());
            Vec3 at = entity != null ? entity.position() : binding.dimension().equals(dimension) ? new Vec3(binding.x(), binding.y(), binding.z()) : null;
            double distance = at == null ? Double.MAX_VALUE : Math.sqrt(client.player.distanceToSqr(at));
            rows.add(new Row(binding.label(), status, at == null ? "other dimension" : Math.round(distance) + " m", color, order, distance));
        }
        rows.sort(Comparator.comparingInt(Row::order).thenComparingDouble(Row::distance));
        int hidden = Math.max(0, rows.size() - 10);
        if (hidden > 0) rows = rows.subList(0, 10);
        String note = OfficeClient.error != null ? OfficeClient.error : rows.isEmpty() ? "No agent mobs in this world. Use an egg or F7." : hidden > 0 ? "+" + hidden + " more" : null;
        int labelW = 0, statusW = 0, whereW = 0;
        for (Row row : rows) { labelW = Math.max(labelW, font.width(row.label())); statusW = Math.max(statusW, font.width(row.status())); whereW = Math.max(whereW, font.width(row.where())); }
        int w = Math.max(font.width("AGENT OFFICE"), Math.max(note == null ? 0 : font.width(note), 8 + labelW + 10 + statusW + 10 + whereW)) + 12;
        int h = 18 + rows.size() * 11 + (note == null ? 0 : 11) + 3;
        int x = 6, y = 6;
        g.fill(x, y, x + w, y + h, 0xd00a101a);
        g.outline(x - 1, y - 1, w + 2, h + 2, 0xff466478);
        g.text(font, "AGENT OFFICE", x + 6, y + 5, 0xff7cdbb6, false);
        int line = y + 18;
        for (Row row : rows) {
            g.fill(x + 6, line + 2, x + 10, line + 6, row.color());
            g.text(font, row.label(), x + 14, line, 0xffd8e1ed, false);
            g.text(font, row.status(), x + 14 + labelW + 10, line, row.color(), false);
            g.text(font, row.where(), x + 14 + labelW + 10 + statusW + 10, line, 0xff8c9cb2, false);
            line += 11;
        }
        if (note != null) g.text(font, note, x + 6, line, 0xffe5c07b, false);
    }
}
