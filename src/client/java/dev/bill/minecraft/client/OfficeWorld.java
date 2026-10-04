package dev.bill.minecraft.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.bill.minecraft.ModdingWorkspace;
import dev.bill.minecraft.AgentActivity;
import dev.bill.minecraft.AgentMob;
import dev.bill.minecraft.OfficeEntities;
import dev.bill.minecraft.OfficeBlocks;
import dev.bill.minecraft.OfficeFurnitureBlock;
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
    record Binding(UUID mob, String terminal, String label, String dimension, double x, double y, double z, String character, Float yaw, Float pitch, Long desk, boolean archived) {}
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
                    if (saved == null || saved.version() < 1 || saved.version() > 4 || saved.bindings() == null) throw new IllegalStateException("Unsupported office save");
                    Set<String> terminals = new HashSet<>();
                    for (Binding raw : saved.bindings()) {
                        Binding b = withCharacter(raw, "claude".equals(raw.character()) ? "claude" : "codex");
                        if (b.mob() == null || b.terminal() == null || b.label() == null || b.dimension() == null
                            || !Double.isFinite(b.x()) || !Double.isFinite(b.y()) || !Double.isFinite(b.z())
                            || b.yaw() != null && !Float.isFinite(b.yaw()) || b.pitch() != null && !Float.isFinite(b.pitch())
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
                if (binding.archived()) { mob.discard(); continue; }
                if (binding.yaw() == null || binding.pitch() == null) {
                    // Older saves stored only a position. Capture the entity's facing before it moves.
                    binding = new Binding(binding.mob(), binding.terminal(), binding.label(), binding.dimension(),
                        binding.x(), binding.y(), binding.z(), binding.character(), mob.getYRot(), mob.getXRot(), binding.desk(), binding.archived());
                    BINDINGS.put(binding.mob(), binding);
                    try { save(); } catch (Exception e) { ModdingWorkspace.LOGGER.error("Could not save home orientation", e); }
                }
                OfficeBridge.Session session = OfficeClient.session(binding.terminal());
                String detected = session == null ? "" : session.agent();
                String character = detected.equals("claude") || detected.equals("codex") ? detected : binding.character();
                if (!(mob instanceof AgentMob agent) || agent.getType() != OfficeEntities.type(character)) {
                    try {
                        AgentMob replacement = OfficeEntities.type(character).create(level, EntitySpawnReason.COMMAND);
                        if (replacement == null) continue;
                        replacement.setUUID(binding.mob());
                        replacement.snapTo(mob.getX(), mob.getY(), mob.getZ(), mob.getYRot(), mob.getXRot());
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
                Binding adjacent = adjacentDeskHome(level, BINDINGS.get(binding.mob()), mob);
                if (adjacent != BINDINGS.get(binding.mob())) {
                    Binding previous = BINDINGS.put(binding.mob(), adjacent);
                    try { save(); binding = adjacent; }
                    catch (Exception e) { BINDINGS.put(binding.mob(), previous); ModdingWorkspace.LOGGER.error("Could not migrate desk work spot", e); }
                }
                mob.setPermanentlyInvulnerable(true); mob.setPersistenceRequired();
                ((AgentMob)mob).home(binding.x(), binding.y(), binding.z(), binding.yaw(), binding.pitch());
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

    /** Changes only this world's label. The next office tick puts it on the nameplate. */
    static Binding rename(MinecraftServer server, UUID mob, String label) throws Exception {
        if (server != owner || server.isPublished()) throw new IllegalStateException("Open a private single-player world first");
        Binding old = BINDINGS.get(mob);
        if (old == null) throw new IllegalStateException("This mob has no session");
        String name = label == null ? "" : label.strip();
        if (name.isEmpty() || name.length() > 48 || name.chars().anyMatch(c -> c < 32))
            throw new IllegalStateException("Choose a name of 1–48 printable characters");
        Binding renamed = new Binding(old.mob(), old.terminal(), name, old.dimension(), old.x(), old.y(), old.z(), old.character(), old.yaw(), old.pitch(), old.desk(), old.archived());
        BINDINGS.put(mob, renamed);
        try { save(); } catch (Exception e) { BINDINGS.put(mob, old); throw e; }
        return renamed;
    }

    /** Removes the mob and its binding. The session continues in Herdr and can be linked again. */
    static Binding dismiss(MinecraftServer server, UUID mob) throws Exception {
        if (server != owner || server.isPublished()) throw new IllegalStateException("Open a private single-player world first");
        Binding old = BINDINGS.remove(mob);
        if (old == null) throw new IllegalStateException("This mob has no session");
        try { save(); } catch (Exception e) { BINDINGS.put(mob, old); throw e; }
        ServerLevel level = level(server, old.dimension());
        if (level != null && level.getEntity(mob) != null) level.getEntity(mob).discard();
        return old;
    }

    /** Moves the home to a block the player chose. The mob walks there and then faces where the player stood. */
    static Binding moveHome(MinecraftServer server, UUID playerId, UUID mob, BlockPos target) throws Exception {
        if (server != owner || server.isPublished()) throw new IllegalStateException("Open a private single-player world first");
        Binding old = BINDINGS.get(mob);
        if (old == null) throw new IllegalStateException("This mob has no session");
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) throw new IllegalStateException("Player left the world");
        ServerLevel level = player.level();
        if (!old.dimension().equals(level.dimension().identifier().toString())) throw new IllegalStateException("The home must stay in the dimension of the mob");
        BlockPos home = homeAt(level, target, mob, false);
        // A home the player chose replaces a desk assignment.
        Binding moved = new Binding(old.mob(), old.terminal(), old.label(), old.dimension(), home.getX()+.5, home.getY(), home.getZ()+.5,
            old.character(), player.getYRot()+180, 0F, null, old.archived());
        BINDINGS.put(mob, moved);
        try { save(); } catch (Exception e) { BINDINGS.put(mob, old); throw e; }
        return moved;
    }

    /** Throws the reason if a mob cannot have its home at this block. */
    static void checkHome(MinecraftServer server, UUID playerId, BlockPos target) {
        if (server != owner || server.isPublished()) throw new IllegalStateException("Open a private single-player world first");
        if (loadError != null) throw new IllegalStateException(loadError);
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) throw new IllegalStateException("Player left the world");
        homeAt(player.level(), target, null, false);
    }

    private static BlockPos homeAt(ServerLevel level, BlockPos home, UUID except, boolean inFront) {
        int drop = 0;
        while (level.getBlockState(home.below()).getCollisionShape(level, home.below()).isEmpty() && drop++ < 8) home = home.below();
        if (level.getBlockState(home.below()).getCollisionShape(level, home.below()).isEmpty())
            throw new IllegalStateException(inFront ? "Stand near solid ground to place a home" : "Choose solid ground for the home");
        for (Binding existing : BINDINGS.values()) {
            if (!existing.archived() && !existing.mob().equals(except) && existing.dimension().equals(level.dimension().identifier().toString())
                && BlockPos.containing(existing.x(),existing.y(),existing.z()).equals(home))
                throw new IllegalStateException(inFront ? "That home is occupied; move to another spot" : "That home is occupied; choose another block");
        }
        // Do not place the mob inside blocks or edit the player's world to fit it.
        if (!level.getBlockState(home).getCollisionShape(level, home).isEmpty()
            || !level.getBlockState(home.above()).getCollisionShape(level, home.above()).isEmpty())
            throw new IllegalStateException(inFront ? "Leave two clear blocks in front of you for the mob" : "Leave two clear blocks there for the mob");
        return home;
    }

    static Binding spawn(MinecraftServer server, UUID playerId, OfficeBridge.Session session) throws Exception {
        return spawn(server, playerId, session, "codex");
    }

    static Binding spawn(MinecraftServer server, UUID playerId, OfficeBridge.Session session, String preferredCharacter) throws Exception {
        return spawn(server, playerId, session, preferredCharacter, null);
    }

    /** A null target is the usual home, two blocks in front of the player. */
    static Binding spawn(MinecraftServer server, UUID playerId, OfficeBridge.Session session, String preferredCharacter, BlockPos target) throws Exception {
        if (server != owner || server.isPublished()) throw new IllegalStateException("Open a private single-player world first");
        if (loadError != null) throw new IllegalStateException(loadError);
        if (linked(session.terminal())) throw new IllegalStateException("This session already has a mob in this world");
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) throw new IllegalStateException("Player left the world");
        ServerLevel level = player.level();
        BlockPos home = homeAt(level, target != null ? target : player.blockPosition().relative(player.getDirection(), 2), null, target == null);
        String character = session.agent().equals("claude") || session.agent().equals("codex") ? session.agent()
            : "claude".equals(preferredCharacter) ? "claude" : "codex";
        AgentMob mob = OfficeEntities.type(character).create(level, EntitySpawnReason.COMMAND);
        if (mob == null) throw new IllegalStateException("Could not create office mob");
        mob.snapTo(home.getX()+.5, home.getY(), home.getZ()+.5, player.getYRot()+180, 0);
        mob.setYBodyRot(player.getYRot()+180); mob.setYHeadRot(player.getYRot()+180);
        mob.setNoAi(true); mob.setNoGravity(true); mob.setPermanentlyInvulnerable(true); mob.setPersistenceRequired();
        mob.setCustomName(Component.literal(session.label() + " · " + session.status()));
        mob.setCustomNameVisible(true);
        Binding b = new Binding(mob.getUUID(), session.terminal(), session.label(), level.dimension().identifier().toString(), mob.getX(), mob.getY(), mob.getZ(), character, mob.getYRot(), mob.getXRot(), null, false);
        mob.home(b.x(), b.y(), b.z(), b.yaw(), b.pitch());
        BINDINGS.put(b.mob(), b);
        try {
            save();
            if (!level.addFreshEntity(mob)) throw new IllegalStateException("Could not add office mob to world");
        } catch (Exception e) {
            BINDINGS.remove(b.mob()); save(); throw e;
        }
        return b;
    }

    static Binding forTerminal(String terminal) {
        return BINDINGS.values().stream().filter(b -> b.terminal().equals(terminal)).findFirst().orElse(null);
    }

    static Binding atDesk(String dimension, BlockPos pos) {
        return BINDINGS.values().stream().filter(b -> !b.archived() && b.dimension().equals(dimension)
            && b.desk() != null && b.desk() == pos.asLong()).findFirst().orElse(null);
    }

    private static void writable(MinecraftServer server) {
        if (server != owner || server.isPublished()) throw new IllegalStateException("Open a private single-player world first");
        if (loadError != null) throw new IllegalStateException(loadError);
    }

    /** Filing away hides the mob, never ends or detaches its terminal process. */
    static void archive(MinecraftServer server, String terminal) throws Exception {
        writable(server);
        Binding old = forTerminal(terminal);
        if (old == null || old.archived()) throw new IllegalStateException("Choose an active agent");
        ServerLevel level = level(server, old.dimension());
        var entity = level == null ? null : level.getEntity(old.mob());
        if (entity == null) throw new IllegalStateException("Visit this agent so its chunk is loaded before filing it away");
        Binding archived = new Binding(old.mob(), old.terminal(), old.label(), old.dimension(), old.x(), old.y(), old.z(),
            old.character(), old.yaw(), old.pitch(), null, true);
        BINDINGS.put(old.mob(), archived);
        try { save(); } catch (Exception e) { BINDINGS.put(old.mob(), old); throw e; }
        entity.discard();
    }

    /** Upgrade existing desk homes without moving the player's furniture or entering occupied blocks. */
    private static Binding adjacentDeskHome(ServerLevel level, Binding binding, Mob mob) {
        if (binding.desk() == null) return binding;
        BlockPos desk = BlockPos.of(binding.desk());
        if (!level.hasChunkAt(desk)) return binding;
        var state = level.getBlockState(desk);
        if (!OfficeBlocks.is(state, OfficeFurnitureBlock.Kind.DESK)) return binding;
        var facing = state.getValue(OfficeFurnitureBlock.FACING);
        BlockPos home = desk.relative(facing);
        if (BlockPos.containing(binding.x(), binding.y(), binding.z()).equals(home)) return binding;
        if (level.getBlockState(home.below()).getCollisionShape(level, home.below()).isEmpty()) return binding;
        for (Binding other : BINDINGS.values()) if (!other.archived() && !other.mob().equals(binding.mob())
            && other.dimension().equals(binding.dimension()) && BlockPos.containing(other.x(), other.y(), other.z()).equals(home)) return binding;
        double y = home.getY() + (OfficeBlocks.is(level.getBlockState(home), OfficeFurnitureBlock.Kind.CHAIR) ? .5 : 0);
        var target = new net.minecraft.world.phys.Vec3(home.getX()+.5, y, home.getZ()+.5);
        if (!level.noCollision(mob, mob.getBoundingBox().move(target.subtract(mob.position())))) return binding;
        return new Binding(binding.mob(), binding.terminal(), binding.label(), binding.dimension(), home.getX()+.5, home.getY(), home.getZ()+.5,
            binding.character(), facing.getOpposite().toYRot(), 0F, binding.desk(), false);
    }

    /** Moves or restores an existing agent, or links a session directly to this desk. */
    static Binding assignDesk(MinecraftServer server, ServerPlayer player, BlockPos desk, String terminal) throws Exception {
        writable(server);
        ServerLevel level = player.level();
        var state = level.getBlockState(desk);
        if (!OfficeBlocks.is(state, OfficeFurnitureBlock.Kind.DESK)) throw new IllegalStateException("The desk was removed");
        String dimension = level.dimension().identifier().toString();
        Binding occupied = atDesk(dimension, desk);
        if (occupied != null) throw new IllegalStateException("File away this desk's agent before assigning another");
        Binding old = forTerminal(terminal);
        OfficeBridge.Session session = OfficeClient.session(terminal);
        if (old == null && (session == null || OfficeClient.error != null)) throw new IllegalStateException("This session is unavailable");
        if (old != null && !old.archived() && !old.dimension().equals(dimension))
            throw new IllegalStateException("File away this agent in its own dimension first");
        var facing = state.getValue(OfficeFurnitureBlock.FACING);
        BlockPos home = desk.relative(facing);
        boolean chair = OfficeBlocks.is(level.getBlockState(home), OfficeFurnitureBlock.Kind.CHAIR);
        if (level.getBlockState(home.below()).getCollisionShape(level, home.below()).isEmpty())
            throw new IllegalStateException("Add a solid floor directly in front of the desk");
        for (Binding b : BINDINGS.values()) if (!b.archived() && (old == null || !b.mob().equals(old.mob()))
            && b.dimension().equals(dimension) && BlockPos.containing(b.x(), b.y(), b.z()).equals(home))
            throw new IllegalStateException("Another agent already uses this work spot");
        AgentMob mob = old != null && level.getEntity(old.mob()) instanceof AgentMob agent ? agent : null;
        if (mob == null && old != null && !old.archived()) throw new IllegalStateException("Visit this agent so its chunk is loaded before moving it");
        String character = old != null ? old.character() : "claude".equals(session.agent()) ? "claude" : "codex";
        boolean created = mob == null;
        if (created) mob = OfficeEntities.type(character).create(level, EntitySpawnReason.COMMAND);
        if (mob == null) throw new IllegalStateException("Could not create agent");
        double y = home.getY() + (chair ? .5 : 0);
        var target = new net.minecraft.world.phys.Vec3(home.getX()+.5, y, home.getZ()+.5);
        if (!level.noCollision(mob, mob.getBoundingBox().move(target.subtract(mob.position()))))
            throw new IllegalStateException("Leave two clear blocks above the work spot or chair");
        if (old != null) mob.setUUID(old.mob());
        Binding binding = new Binding(mob.getUUID(), terminal, old == null ? session.label() : old.label(), dimension,
            home.getX()+.5, home.getY(), home.getZ()+.5, character, facing.getOpposite().toYRot(), 0F, desk.asLong(), false);
        BINDINGS.put(binding.mob(), binding);
        try { save(); } catch (Exception e) {
            if (old == null) BINDINGS.remove(binding.mob()); else BINDINGS.put(old.mob(), old);
            throw e;
        }
        mob.snapTo(target.x, target.y, target.z, binding.yaw(), 0);
        mob.home(binding.x(), binding.y(), binding.z(), binding.yaw(), 0);
        mob.setCustomName(Component.literal(binding.label())); mob.setCustomNameVisible(true);
        if (created && !level.addFreshEntity(mob)) {
            if (old == null) BINDINGS.remove(binding.mob()); else BINDINGS.put(old.mob(), old);
            save(); throw new IllegalStateException("Could not restore this agent");
        }
        return binding;
    }

    private static ServerLevel level(MinecraftServer server, String dimension) {
        for (ServerLevel level : server.getAllLevels()) if (level.dimension().identifier().toString().equals(dimension)) return level;
        return null;
    }

    private static void save() throws Exception {
        Files.createDirectories(file.getParent());
        Path temp = file.resolveSibling(file.getFileName()+".tmp");
        Files.writeString(temp, JSON.toJson(new Save(4, bindings()))+"\n");
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private static Binding withCharacter(Binding b, String character) {
        return new Binding(b.mob(), b.terminal(), b.label(), b.dimension(), b.x(), b.y(), b.z(), character, b.yaw(), b.pitch(), b.desk(), b.archived());
    }
}
