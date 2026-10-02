package dev.bill.minecraft.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.bill.minecraft.ModdingWorkspace;
import dev.bill.minecraft.AgentActivity;
import dev.bill.minecraft.AgentMob;
import dev.bill.minecraft.OfficeEntities;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.storage.LevelResource;

/** Owned by the integrated-server thread. Immutable bindings are safe for client lookup. */
final class OfficeWorld {
    record Binding(UUID mob, String terminal, String label, String dimension, double x, double y, double z, String character) {}
    record Save(int version, List<Binding> bindings) {}
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<UUID, Binding> BINDINGS = new ConcurrentHashMap<>();
    private static volatile MinecraftServer owner;
    private static Path file;
    private static volatile String loadError;
    private static int ticks;

    static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            if (!server.isSingleplayer()) return;
            owner = server; BINDINGS.clear(); loadError = null; ticks = 0;
            file = server.getWorldPath(LevelResource.ROOT).resolve("data/agent-office.json");
            try {
                if (Files.exists(file)) {
                    Save saved = JSON.fromJson(Files.readString(file), Save.class);
                    if (saved == null || saved.version() < 1 || saved.version() > 2 || saved.bindings() == null) throw new IllegalStateException("Unsupported office save");
                    Set<String> terminals = new HashSet<>();
                    for (Binding raw : saved.bindings()) {
                        Binding b = withCharacter(raw, "claude".equals(raw.character()) ? "claude" : "codex");
                        if (b.mob() == null || b.terminal() == null || b.label() == null || b.dimension() == null
                            || !Double.isFinite(b.x()) || !Double.isFinite(b.y()) || !Double.isFinite(b.z())
                            || !terminals.add(b.terminal()) || BINDINGS.putIfAbsent(b.mob(), b) != null) throw new IllegalStateException("Invalid office binding");
                    }
                }
                ModdingWorkspace.LOGGER.info("Office loaded {} bindings from {}", BINDINGS.size(), file);
            } catch (Exception e) {
                BINDINGS.clear(); loadError = "Office save could not be read; existing file preserved";
                ModdingWorkspace.LOGGER.error(loadError, e);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            if (owner == server) { owner = null; BINDINGS.clear(); file = null; }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server != owner || server.isPublished() || ++ticks % 20 != 0) return;
            for (Binding binding : BINDINGS.values()) {
                ServerLevel level = level(server, binding.dimension());
                if (level == null || !(level.getEntity(binding.mob()) instanceof Mob mob)) continue;
                OfficeBridge.Session session = OfficeClient.session(binding.terminal());
                String detected = session == null ? "" : session.agent();
                String character = detected.equals("claude") || detected.equals("codex") ? detected : binding.character();
                if (!(mob instanceof AgentMob agent) || agent.getType() != OfficeEntities.type(character)) {
                    try {
                        AgentMob replacement = OfficeEntities.type(character).create(level, EntitySpawnReason.COMMAND);
                        if (replacement == null) continue;
                        replacement.setUUID(binding.mob());
                        replacement.snapTo(binding.x(), binding.y(), binding.z(), mob.getYRot(), mob.getXRot());
                        replacement.setYBodyRot(mob.getYRot()); replacement.setYHeadRot(mob.getYRot());
                        mob.discard();
                        if (!level.addFreshEntity(replacement)) throw new IllegalStateException("Could not replace office mob");
                        mob = replacement;
                        BINDINGS.put(binding.mob(), withCharacter(binding, character));
                        save();
                    } catch (Exception e) { ModdingWorkspace.LOGGER.error("Could not migrate office mob", e); continue; }
                }
                if (!BINDINGS.get(binding.mob()).character().equals(character)) {
                    BINDINGS.put(binding.mob(), withCharacter(binding, character));
                    try { save(); } catch (Exception e) { ModdingWorkspace.LOGGER.error("Could not save detected agent character", e); }
                }
                mob.setNoAi(true); mob.setNoGravity(true); mob.setPermanentlyInvulnerable(true); mob.setPersistenceRequired();
                if (mob.distanceToSqr(binding.x(), binding.y(), binding.z()) > .01) mob.snapTo(binding.x(), binding.y(), binding.z());
                mob.setDeltaMovement(0, 0, 0);
                AgentActivity activity = OfficeClient.activity(binding.terminal());
                ((AgentMob)mob).activity(activity);
                String status = activity == AgentActivity.OFFLINE ? OfficeClient.status(binding.terminal()) : activity.label;
                ChatFormatting color = switch (activity) {
                    case WORKING -> ChatFormatting.GREEN;
                    case ATTENTION -> ChatFormatting.GOLD;
                    case OFFLINE -> ChatFormatting.RED;
                    default -> ChatFormatting.GRAY;
                };
                mob.setCustomName(Component.literal(binding.label() + " · " + status).withStyle(color));
                mob.setCustomNameVisible(true);
            }
        });
    }

    static Binding binding(UUID mob) { return BINDINGS.get(mob); }
    static List<Binding> bindings() { return List.copyOf(BINDINGS.values()); }
    static boolean linked(String terminal) { return BINDINGS.values().stream().anyMatch(b -> b.terminal().equals(terminal)); }

    static Binding spawn(MinecraftServer server, UUID playerId, OfficeBridge.Session session) throws Exception {
        return spawn(server, playerId, session, "codex");
    }

    static Binding spawn(MinecraftServer server, UUID playerId, OfficeBridge.Session session, String preferredCharacter) throws Exception {
        if (server != owner || server.isPublished()) throw new IllegalStateException("Open a private single-player world first");
        if (loadError != null) throw new IllegalStateException(loadError);
        if (linked(session.terminal())) throw new IllegalStateException("This session already has a mob in this world");
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) throw new IllegalStateException("Player left the world");
        ServerLevel level = player.level();
        BlockPos home = player.blockPosition().relative(player.getDirection(), 2);
        int drop = 0;
        while (level.getBlockState(home.below()).getCollisionShape(level, home.below()).isEmpty() && drop++ < 8) home = home.below();
        if (level.getBlockState(home.below()).getCollisionShape(level, home.below()).isEmpty())
            throw new IllegalStateException("Stand near solid ground to place a home");
        for (Binding existing : BINDINGS.values()) {
            if (existing.dimension().equals(level.dimension().identifier().toString())
                && BlockPos.containing(existing.x(),existing.y(),existing.z()).equals(home))
                throw new IllegalStateException("That home is occupied; move to another spot");
        }
        // Do not place the mob inside blocks or edit the player's world to fit it.
        if (!level.getBlockState(home).getCollisionShape(level, home).isEmpty()
            || !level.getBlockState(home.above()).getCollisionShape(level, home.above()).isEmpty())
            throw new IllegalStateException("Leave two clear blocks in front of you for the mob");
        String character = session.agent().equals("claude") || session.agent().equals("codex") ? session.agent()
            : "claude".equals(preferredCharacter) ? "claude" : "codex";
        AgentMob mob = OfficeEntities.type(character).create(level, EntitySpawnReason.COMMAND);
        if (mob == null) throw new IllegalStateException("Could not create office mob");
        mob.snapTo(home.getX()+.5, home.getY(), home.getZ()+.5, player.getYRot()+180, 0);
        mob.setYBodyRot(player.getYRot()+180); mob.setYHeadRot(player.getYRot()+180);
        mob.setNoAi(true); mob.setNoGravity(true); mob.setPermanentlyInvulnerable(true); mob.setPersistenceRequired();
        mob.setCustomName(Component.literal(session.label() + " · " + session.status()));
        mob.setCustomNameVisible(true);
        Binding b = new Binding(mob.getUUID(), session.terminal(), session.label(), level.dimension().identifier().toString(), mob.getX(), mob.getY(), mob.getZ(), character);
        BINDINGS.put(b.mob(), b);
        try {
            save();
            if (!level.addFreshEntity(mob)) throw new IllegalStateException("Could not add office mob to world");
        } catch (Exception e) {
            BINDINGS.remove(b.mob()); save(); throw e;
        }
        return b;
    }

    private static ServerLevel level(MinecraftServer server, String dimension) {
        for (ServerLevel level : server.getAllLevels()) if (level.dimension().identifier().toString().equals(dimension)) return level;
        return null;
    }

    private static void save() throws Exception {
        Files.createDirectories(file.getParent());
        Path temp = file.resolveSibling(file.getFileName()+".tmp");
        Files.writeString(temp, JSON.toJson(new Save(2, bindings()))+"\n");
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private static Binding withCharacter(Binding b, String character) {
        return new Binding(b.mob(), b.terminal(), b.label(), b.dimension(), b.x(), b.y(), b.z(), character);
    }
}
