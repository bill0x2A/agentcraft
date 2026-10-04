package dev.bill.minecraft.client;

import dev.bill.minecraft.ModdingWorkspace;
import dev.bill.minecraft.Studio;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/** Reproduces command-tree synchronization on fresh login and saved-world reload. */
final class WorldJoinVerification {
    private static final Path REPORT = Path.of("../.runtime/world-join-verification.txt");
    private static final String WORLD = "join-test-" + System.currentTimeMillis();
    private static final BlockPos EDIT = new BlockPos(0, 66, 20);
    private static final long DEADLINE = System.nanoTime() + 240_000_000_000L;
    private static int phase, ticks;
    private static CompletableFuture<?> pending;

    static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (phase == 99) return;
            try {
                check(client.gameDirectory.toPath().getFileName().toString().equals("run-join-test"), "Wrong test directory");
                client.options.pauseOnLostFocus = false;
                if (client.gui.screen() instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen s) s.onClose();
                check(!(client.gui.screen() instanceof net.minecraft.client.gui.screens.DisconnectedScreen), "Disconnected during world join");
                check(System.nanoTime() < DEADLINE, "Timeout at phase " + phase);
                if (pending != null) {
                    if (!pending.isDone()) return;
                    pending.join(); pending = null; ticks = 0;
                }
                switch (phase) {
                    case 0 -> {
                        if (!(client.gui.screen() instanceof TitleScreen)) return;
                        Files.writeString(REPORT, "START world join regression (no cheats, disposable world)\n");
                        client.createWorldOpenFlows().createFreshLevel(WORLD,
                            new LevelSettings("World join regression", GameType.CREATIVE,
                                new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false),
                                false, WorldDataConfiguration.DEFAULT),
                            new WorldOptions(42, false, false), WorldPresets::createTestWorldDimensions, client.gui.screen());
                        phase = 1;
                    }
                    case 1 -> {
                        if (!OfficeClient.localWorld(client) || ++ticks < 40) return;
                        check(client.getConnection().getCommands().getRoot().getChild("office") != null, "Office command not synchronized");
                        pass("Fresh world joins and receives office command tree without Invalid player data");
                        client.getConnection().sendCommand("office studio");
                        phase = 2; ticks = 0;
                    }
                    case 2 -> {
                        if (!client.level.dimension().equals(Studio.DIMENSION) || ++ticks < 40) return;
                        pass("Player command enters generated studio without cheats");
                        var server = client.getSingleplayerServer();
                        pending = server.submit(() -> server.getLevel(Studio.DIMENSION).setBlock(EDIT, Blocks.DIAMOND_BLOCK.defaultBlockState(), 3));
                        phase = 3;
                    }
                    case 3 -> { client.disconnect(new TitleScreen(), false); phase = 4; }
                    case 4 -> {
                        if (!(client.gui.screen() instanceof TitleScreen) || client.getSingleplayerServer() != null) return;
                        client.createWorldOpenFlows().openWorld(WORLD, () -> client.gui.setScreen(new TitleScreen()));
                        phase = 5; ticks = 0;
                    }
                    case 5 -> {
                        if (!OfficeClient.localWorld(client) || ++ticks < 40) return;
                        check(client.level.dimension().equals(Studio.DIMENSION), "Saved player dimension lost");
                        check(client.level.getBlockState(EDIT).is(Blocks.DIAMOND_BLOCK), "Studio edit lost on reload");
                        pass("Saved world rejoins in studio with player dimension and edits preserved");
                        client.getConnection().sendCommand("office overworld");
                        phase = 6; ticks = 0;
                    }
                    case 6 -> {
                        if (!client.level.dimension().equals(Level.OVERWORLD) || ++ticks < 20) return;
                        pass("Player command returns to overworld");
                        client.getConnection().sendCommand("office studio"); phase = 7; ticks = 0;
                    }
                    case 7 -> {
                        if (!client.level.dimension().equals(Studio.DIMENSION) || ++ticks < 20) return;
                        check(client.level.getBlockState(EDIT).is(Blocks.DIAMOND_BLOCK), "Studio rebuilt over edits");
                        pass("Returning to studio preserves edits; ALL PASS");
                        phase = 99; client.stop();
                    }
                }
            } catch (Exception e) {
                phase = 99;
                ModdingWorkspace.LOGGER.error("World join regression failed", e);
                try { Files.writeString(REPORT, "FAIL " + e + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND); }
                catch (Exception ignored) { }
                client.stop();
            }
        });
    }

    private static void check(boolean ok, String message) { if (!ok) throw new IllegalStateException(message); }
    private static void pass(String message) throws Exception { Files.writeString(REPORT, "PASS " + message + "\n", StandardOpenOption.APPEND); }
}
