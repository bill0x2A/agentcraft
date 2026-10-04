package dev.bill.minecraft.client;

import dev.bill.minecraft.AgentActivity;
import dev.bill.minecraft.AgentMob;
import dev.bill.minecraft.ModdingWorkspace;
import dev.bill.minecraft.OfficeEntities;
import java.nio.file.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;

/** Opt-in movement regression check in a disposable world, with no terminal fixtures. */
final class AttentionVerification {
    private static final Path REPORT = Path.of("../.runtime/attention-verification.txt");
    private static final Vec3 HOME = new Vec3(5.5, 65, 6.5);
    private static final float YAW = 123, PITCH = 12;
    private static final long DEADLINE = System.nanoTime() + 240_000_000_000L;
    private static volatile boolean started, finished;
    private static AgentMob mob;
    private static Vec3 previous;
    private static int character, phase, ticks;

    static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            try {
                if (!client.gameDirectory.toPath().getFileName().toString().equals("run-attention-test"))
                    throw new IllegalStateException("Wrong attention verification directory");
                client.options.pauseOnLostFocus = false;
                if (client.gui.screen() instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen onboarding) onboarding.onClose();
                if (finished) { client.stop(); return; }
                if (System.nanoTime() > DEADLINE) throw new IllegalStateException("Attention verification timeout");
                if (!started && client.gui.screen() instanceof TitleScreen) {
                    started = true;
                    Files.createDirectories(REPORT.getParent());
                    Files.writeString(REPORT, "START attention movement verification\n");
                    client.createWorldOpenFlows().createFreshLevel("attention-test-" + System.currentTimeMillis(),
                        new LevelSettings("Attention Verification", GameType.CREATIVE,
                            new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT),
                        new WorldOptions(42L, false, false), WorldPresets::createTestWorldDimensions, client.gui.screen());
                }
            } catch (Exception e) { fail(e); }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!started || finished || !server.isSingleplayer() || server.getPlayerList().getPlayers().isEmpty()) return;
            try { tick(server); } catch (Exception e) { fail(e); }
        });
    }

    private static void tick(MinecraftServer server) throws Exception {
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        var level = server.overworld();
        if (mob == null) {
            for (int x = -12; x <= 12; x++) for (int z = -12; z <= 12; z++) {
                level.setBlock(new BlockPos(x, 64, z), Blocks.STONE_BRICKS.defaultBlockState(), 3);
                for (int y = 65; y < 70; y++) level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
            }
            // Force navigation around a wall instead of a straight slide through blocks.
            for (int x = -2; x <= 2; x++) for (int y = 65; y <= 67; y++)
                level.setBlock(new BlockPos(x, y, 4), Blocks.STONE_BRICKS.defaultBlockState(), 3);
            player.teleportTo(.5, 65, .5); player.setYRot(0); player.setXRot(0);
            mob = (character == 0 ? OfficeEntities.CODEX : OfficeEntities.CLAUDE).create(level, EntitySpawnReason.COMMAND);
            mob.snapTo(HOME.x, HOME.y, HOME.z, YAW, PITCH);
            mob.home(HOME.x, HOME.y, HOME.z, YAW, PITCH);
            mob.activity(AgentActivity.RESTING);
            level.addFreshEntity(mob);
            previous = mob.position(); phase = 0; ticks = 0;
            return;
        }
        if (++ticks > 360) throw new IllegalStateException("phase " + phase + " timed out: " + mob.position()
            + " navigationDone=" + mob.getNavigation().isDone() + " noAi=" + mob.isNoAi());
        check(mob.position().distanceToSqr(previous) < 1.0, "mob teleported during movement");
        previous = mob.position();
        switch (phase) {
            case 0 -> {
                if (ticks < 20) return;
                check(atHome(), "resting home pose changed");
                mob.activity(AgentActivity.ATTENTION); advance();
            }
            case 1 -> {
                if (!inFront(player)) return;
                pass("runs around obstacles and stands facing the player");
                player.setYRot(180); advance();
            }
            case 2 -> {
                if (!inFront(player)) return;
                pass("moves back in front when the player turns");
                mob.activity(AgentActivity.WORKING); advance();
            }
            case 3 -> {
                if (!atHome()) return;
                pass("walks home and restores exact position, body/head yaw and pitch");
                mob.activity(AgentActivity.ATTENTION); advance();
            }
            case 4 -> {
                if (mob.distanceToSqr(HOME) < 2) return;
                mob.activity(AgentActivity.RESTING); advance();
            }
            case 5 -> {
                if (!atHome()) return;
                pass("attention clearing mid-approach returns to the original pose");
                mob.activity(AgentActivity.ATTENTION); advance();
            }
            case 6 -> {
                if (!inFront(player)) return;
                mob.activity(AgentActivity.OFFLINE); advance();
            }
            case 7 -> {
                if (!atHome()) return;
                pass("unavailable session returns home");
                mob.activity(AgentActivity.ATTENTION); advance();
            }
            case 8 -> {
                if (!inFront(player)) return;
                player.setGameMode(GameType.SPECTATOR); advance();
            }
            case 9 -> {
                if (!atHome()) return;
                pass("returns home when no eligible player remains");
                player.setGameMode(GameType.CREATIVE);
                mob.discard(); mob = null;
                if (++character == 2) { pass("ALL PASS"); finished = true; }
            }
        }
    }

    private static boolean inFront(ServerPlayer player) {
        Vec3 delta = mob.position().subtract(player.position());
        double yaw = Math.toRadians(player.getYRot());
        float facing = (float)Math.toDegrees(Math.atan2(-delta.z, -delta.x)) - 90;
        return mob.isNoAi() && delta.lengthSqr() < 20 && delta.x * -Math.sin(yaw) + delta.z * Math.cos(yaw) > 1.2
            && Math.abs(Mth.wrapDegrees(mob.getYRot() - facing)) < 2;
    }

    private static boolean atHome() {
        return mob.distanceToSqr(HOME) < .0001 && Math.abs(Mth.wrapDegrees(mob.getYRot() - YAW)) < .01
            && Math.abs(Mth.wrapDegrees(mob.getYHeadRot() - YAW)) < .01
            && Math.abs(Mth.wrapDegrees(mob.yBodyRot - YAW)) < .01 && Math.abs(mob.getXRot() - PITCH) < .01;
    }

    private static void advance() { phase++; ticks = 0; }
    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private static void pass(String message) throws Exception {
        String line = "PASS " + (character == 0 ? "Codex: " : "Claude: ") + message;
        Files.writeString(REPORT, line + "\n", StandardOpenOption.APPEND);
        ModdingWorkspace.LOGGER.info(line);
    }
    private static void fail(Exception e) {
        finished = true;
        ModdingWorkspace.LOGGER.error("Attention verification failed", e);
        try { Files.writeString(REPORT, "FAIL " + e + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND); }
        catch (Exception ignored) { }
    }
}
