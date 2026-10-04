package dev.bill.minecraft.client;

import dev.bill.minecraft.OfficeEntities;
import dev.bill.minecraft.OfficeItems;
import dev.bill.minecraft.TerminalDisplay;
import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.AABB;

/** Live terminal views in the world. A display never sends input or a size; it draws the shared connection's frame. */
final class TerminalDisplays {
    static final int CELL_W = 6, CELL_H = 10, BACKGROUND = 0xff111720;
    /** Cells of one row with the same colors and style. A null text is a background only. */
    record Run(int x, int y, int cells, int background, int foreground, boolean bold, FormattedCharSequence text) {}
    record Layout(TerminalConnection.Frame frame, List<Run> runs) {}
    private static final Style MONO = Style.EMPTY.withFont(new FontDescription.Resource(Identifier.fromNamespaceAndPath("modding_workspace", "terminal_4")));
    private static final Map<String, TerminalConnection> LINKS = new HashMap<>();
    private static final Map<String, Layout> LAYOUTS = new HashMap<>();
    private static final Map<String, Long> RETRY = new HashMap<>();
    private static ClientLevel world;
    // A new display with no terminal opens its chooser when the client knows the entity.
    private static UUID chooserFor;
    private static boolean placing;
    private static long placedAt;

    static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (world != client.level || !OfficeClient.localWorld(client)) {
                LINKS.values().forEach(TerminalPool::release);
                LINKS.clear(); LAYOUTS.clear(); RETRY.clear(); world = client.level; chooserFor = null; placing = false;
                MonitorPanels.clear();
            }
            if (!OfficeClient.localWorld(client)) return;
            if (chooserFor != null && client.level.getEntity(chooserFor) instanceof TerminalDisplay display) {
                chooserFor = null;
                if (client.gui.screen() == null) client.gui.setScreen(new TerminalChooserScreen(null, display));
            }
            // A monitor panel is a display too: it shows the terminal of the agent at the desk in front of it.
            Set<String> wanted = MonitorPanels.refresh(client);
            for (var entity : client.level.entitiesForRendering())
                if (entity instanceof TerminalDisplay display && !display.terminal().isEmpty()) wanted.add(display.terminal());
            LINKS.entrySet().removeIf(link -> {
                if (wanted.contains(link.getKey())) return false;
                TerminalPool.release(link.getValue()); LAYOUTS.remove(link.getKey()); return true;
            });
            long now = System.nanoTime();
            for (String terminal : wanted) {
                TerminalConnection link = LINKS.get(terminal);
                if (link != null && !link.closed() || now < RETRY.getOrDefault(terminal, 0L)) continue;
                RETRY.put(terminal, now + 3_000_000_000L);
                if (link != null) { TerminalPool.release(link); LINKS.remove(terminal); }
                // Attach only to a session that exists. An ended terminal keeps its last frame.
                if (OfficeClient.error == null && OfficeClient.session(terminal) != null) LINKS.put(terminal, TerminalPool.acquire(terminal));
            }
        });
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (!(entity instanceof TerminalDisplay display)) return InteractionResult.PASS;
            Minecraft client = Minecraft.getInstance();
            if (!OfficeClient.localWorld(client)) return InteractionResult.PASS;
            if (level.isClientSide()) {
                if (player.isHolding(OfficeItems.DISMISSAL_SLIP)) remove(client, display);
                // Sneak to choose another terminal. A display with no usable terminal has nothing else to open.
                else if (player.isShiftKeyDown() || display.terminal().isEmpty() || OfficeClient.error == null && OfficeClient.session(display.terminal()) == null)
                    client.gui.setScreen(new TerminalChooserScreen(client.gui.screen(), display));
                else if (OfficeClient.error != null) OfficeTools.say(client, status(display.terminal()) + ".");
                else client.gui.setScreen(new TerminalScreen(client.gui.screen(), display.terminal()));
            }
            return InteractionResult.SUCCESS;
        });
    }

    /** The text to show in place of a frame. */
    static String status(String terminal) {
        if (terminal.isEmpty()) return "No terminal · right-click to choose";
        if (OfficeClient.error != null) return "Bridge offline";
        if (OfficeClient.session(terminal) == null) return "Session unavailable";
        return "Connecting";
    }

    static Layout layout(String terminal, Font font) {
        TerminalConnection link = LINKS.get(terminal);
        TerminalConnection.Frame frame = link == null ? null : link.frame;
        Layout layout = LAYOUTS.get(terminal);
        if (frame == null || layout != null && layout.frame() == frame) return layout;
        layout = new Layout(frame, runs(frame, font));
        LAYOUTS.put(terminal, layout);
        return layout;
    }

    private static List<Run> runs(TerminalConnection.Frame frame, Font font) {
        List<Run> runs = new ArrayList<>();
        int width = frame.width();
        for (int y = 0; y < frame.height(); y++) {
            int x = 0;
            while (x < width) {
                int background = background(frame.cells()[y * width + x]);
                if (background == BACKGROUND && blank(frame.cells()[y * width + x])) { x++; continue; }
                int start = x, foreground = 0, style = -1;
                StringBuilder text = new StringBuilder();
                for (; x < width; x++) {
                    TerminalConnection.Cell cell = frame.cells()[y * width + x];
                    if (background(cell) != background) break;
                    if (blank(cell)) { text.append(' '); continue; }
                    int cellStyle = cell.modifiers() & (1 | 4 | 8 | 256), cellForeground = foreground(cell);
                    if (style == -1) { style = cellStyle; foreground = cellForeground; }
                    else if (cellStyle != style || cellForeground != foreground) break;
                    text.append(cell.text());
                }
                if (style == -1) { runs.add(new Run(start, y, x - start, background, 0, false, null)); continue; }
                FormattedCharSequence sequence = sequence(text.toString(), style);
                if (font.width(sequence) == (x - start) * CELL_W) { runs.add(new Run(start, y, x - start, background, foreground, (style & 1) != 0, sequence)); continue; }
                // A glyph from a fallback font has a different width. Place each cell of this run by itself.
                runs.add(new Run(start, y, x - start, background, 0, false, null));
                for (int i = start; i < x; i++) {
                    TerminalConnection.Cell cell = frame.cells()[y * width + i];
                    if (!blank(cell)) runs.add(new Run(i, y, 1, BACKGROUND, foreground, (style & 1) != 0, sequence(cell.text(), style)));
                }
            }
        }
        return runs;
    }

    private static FormattedCharSequence sequence(String text, int style) {
        return Component.literal(text).setStyle(MONO.withItalic((style & 4) != 0).withUnderlined((style & 8) != 0).withStrikethrough((style & 256) != 0)).getVisualOrderText();
    }
    private static boolean blank(TerminalConnection.Cell cell) { return cell.skip() || (cell.modifiers() & 128) != 0 || cell.text().isBlank(); }
    private static boolean reversed(TerminalConnection.Cell cell) { return (cell.modifiers() & 64) != 0; }
    private static int background(TerminalConnection.Cell cell) { return reversed(cell) ? TerminalScreen.color(cell.foreground(), false) : TerminalScreen.color(cell.background(), true); }
    private static int foreground(TerminalConnection.Cell cell) { return reversed(cell) ? TerminalScreen.color(cell.background(), true) : TerminalScreen.color(cell.foreground(), false); }

    /** The name of a terminal for the player: the label of its agent mob, else the name of its session. */
    static String label(String terminal) {
        OfficeWorld.Binding binding = OfficeWorld.forTerminal(terminal);
        if (binding != null) return binding.label();
        OfficeBridge.Session session = OfficeClient.session(terminal);
        return session != null ? session.label() : "Terminal";
    }

    private static Component name(String terminal, String label) {
        return Component.literal(terminal.isEmpty() ? "Office terminal · right-click" : label + " · terminal");
    }

    /** From the office screen: four blocks ahead, as a browser display. */
    static void placeAhead(Minecraft client, OfficeScreen screen) {
        place(client, "", client.player.blockPosition().relative(client.player.getDirection(), 4), Direction.UP, screen);
    }

    /**
     * The face a player clicked decides the direction: a wall holds the display, the ground lets it stand.
     * An empty terminal places a display that opens its chooser.
     */
    static void place(Minecraft client, String terminal, BlockPos target, Direction face, OfficeScreen screen) {
        // A held right mouse button repeats the click. One click places one display.
        if (placing || System.nanoTime() - placedAt < 700_000_000L) return;
        placing = true;
        var server = client.getSingleplayerServer(); UUID playerId = client.player.getUUID();
        String label = label(terminal);
        server.execute(() -> {
            try {
                var player = server.getPlayerList().getPlayer(playerId);
                if (player == null || server.isPublished()) throw new IllegalStateException("Open a private single-player world first");
                var level = player.level();
                boolean wall = face.getAxis().isHorizontal();
                Direction direction = wall ? face.getOpposite() : player.getDirection();
                for (int side = -2; side <= 2; side++) for (int y = 0; y < 3; y++) {
                    BlockPos check = target.relative(direction.getClockWise(), side).above(y);
                    if (!level.getBlockState(check).getCollisionShape(level, check).isEmpty()) throw new IllegalStateException("Leave a clear area 5 wide and 3 high for the display");
                }
                double back = wall ? .44 : 0;
                double x = target.getX()+.5+direction.getStepX()*back, z = target.getZ()+.5+direction.getStepZ()*back;
                if (!level.getEntitiesOfClass(TerminalDisplay.class, new AABB(x-.4, target.getY(), z-.4, x+.4, target.getY()+2.5, z+.4)).isEmpty())
                    throw new IllegalStateException("A terminal display is already here");
                TerminalDisplay display = OfficeEntities.TERMINAL.create(level, EntitySpawnReason.COMMAND);
                if (display == null) throw new IllegalStateException("Could not create the display");
                display.snapTo(x, target.getY(), z, direction.toYRot(), 0);
                display.terminal(terminal);
                display.setCustomName(name(terminal, label));
                if (!level.addFreshEntity(display)) throw new IllegalStateException("Could not place the display");
                UUID id = display.getUUID();
                client.execute(() -> {
                    placing = false; placedAt = System.nanoTime();
                    if (client.getSingleplayerServer() != server) return;
                    if (screen != null && client.gui.screen() == screen) screen.onClose();
                    if (terminal.isEmpty()) chooserFor = id;
                    else OfficeClient.message(client, "Placed the terminal display of " + label + ". " + HINT);
                });
            } catch (Exception e) {
                client.execute(() -> { placing = false; if (screen != null) screen.finished(e.getMessage()); else OfficeTools.say(client, e.getMessage()); });
            }
        });
    }
    static final String HINT = "Right-click it to type. Sneak and right-click it to choose another terminal.";

    /** Sets the terminal that a display shows. The world save keeps it. */
    static void assign(Minecraft client, TerminalDisplay display, String terminal) { assign(client, display, terminal, label(terminal)); }
    static void assign(Minecraft client, TerminalDisplay display, String terminal, String label) {
        var server = client.getSingleplayerServer();
        if (!OfficeClient.localWorld(client)) return;
        var dimension = display.level().dimension(); UUID id = display.getUUID();
        Component name = name(terminal, label);
        // Update client data immediately, then persist on the integrated server thread.
        display.terminal(terminal);
        server.execute(() -> {
            var level = server.getLevel(dimension);
            if (level != null && level.getEntity(id) instanceof TerminalDisplay found) { found.terminal(terminal); found.setCustomName(name); }
        });
    }

    /** A Name Badge changed the label of a terminal; give its loaded displays the new name. */
    static void renamed(Minecraft client, String terminal) {
        for (var entity : client.level.entitiesForRendering())
            if (entity instanceof TerminalDisplay display && display.terminal().equals(terminal)) assign(client, display, terminal);
    }

    static void remove(Minecraft client, TerminalDisplay display) {
        var server = client.getSingleplayerServer();
        var dimension = display.level().dimension(); UUID id = display.getUUID();
        server.execute(() -> {
            var level = server.getLevel(dimension);
            if (level != null && level.getEntity(id) instanceof TerminalDisplay found) found.discard();
            client.execute(() -> OfficeClient.message(client, "Removed the terminal display."));
        });
    }
}
