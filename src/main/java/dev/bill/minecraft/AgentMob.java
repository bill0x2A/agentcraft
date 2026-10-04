package dev.bill.minecraft;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/** Custom office creature; its process and home are managed independently by the office. */
public final class AgentMob extends Mob {
    private static final EntityDataAccessor<Integer> ACTIVITY = SynchedEntityData.defineId(AgentMob.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> SEATED = SynchedEntityData.defineId(AgentMob.class, EntityDataSerializers.BOOLEAN);
    private Vec3 home, destination;
    private boolean chairHome;
    private float homeYaw, homePitch, parkedYaw, parkedPitch;
    private boolean approaching, parked;
    private int nextPath, returnTicks;

    public AgentMob(EntityType<? extends AgentMob> type, Level level) {
        super(type, level);
        setNoAi(true); setNoGravity(true); setPersistenceRequired(); setPermanentlyInvulnerable(true);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder); builder.define(ACTIVITY, AgentActivity.UNKNOWN.ordinal()); builder.define(SEATED, false);
    }
    public AgentActivity activity() { return AgentActivity.fromId(entityData.get(ACTIVITY)); }
    public void activity(AgentActivity activity) { entityData.set(ACTIVITY, activity.ordinal()); }
    public boolean seated() { return entityData.get(SEATED); }
    public boolean isClaude() { return getType() == OfficeEntities.CLAUDE; }

    /** The binding owns this pose, so approaching the player never moves the saved home. */
    public void home(double x, double y, double z, float yaw, float pitch) {
        chairHome = OfficeBlocks.is(level().getBlockState(BlockPos.containing(x, y, z)), OfficeFurnitureBlock.Kind.CHAIR);
        home = new Vec3(x, y + (chairHome ? .5 : 0), z); homeYaw = yaw; homePitch = pitch;
    }

    @Override public void tick() {
        if (level() instanceof ServerLevel server && home != null) {
            if (server.getServer().isSingleplayer() && !server.getServer().isPublished()) tickOfficeMovement(server);
            else stopMoving();
        }
        super.tick();
        if (!level().isClientSide() && parked) {
            setDeltaMovement(Vec3.ZERO);
            setYRot(parkedYaw); setXRot(parkedPitch);
            setYBodyRot(parkedYaw); setYHeadRot(parkedYaw);
        }
    }

    private void tickOfficeMovement(ServerLevel level) {
        ServerPlayer player = null;
        double closest = 48 * 48;
        if (activity() == AgentActivity.ATTENTION) {
            for (ServerPlayer candidate : level.players()) {
                double distance = distanceToSqr(candidate);
                if (candidate.isAlive() && !candidate.isSpectator() && distance < closest) {
                    player = candidate; closest = distance;
                }
            }
        }
        boolean attention = player != null;
        if (attention != approaching) {
            entityData.set(SEATED, false);
            approaching = attention; destination = null; nextPath = 0; returnTicks = 0;
            stopMoving();
        }
        if (!attention) {
            if (distanceToSqr(home) < .36 || ++returnTicks > 400 && clearAt(level, home)) {
                // Finish exactly at the original pose, including after an obstructed return route.
                snapTo(home.x, home.y, home.z, homeYaw, homePitch);
                park(homeYaw, homePitch, chairHome); returnTicks = 0;
                return;
            }
            destination = home;
        } else if (tickCount >= nextPath || destination == null) {
            destination = frontOf(level, player);
        }
        if (destination == null) {
            stopMoving(); nextPath = tickCount + 10;
            return;
        }
        if (attention && distanceToSqr(destination) < .49) {
            float yaw = (float)Math.toDegrees(Math.atan2(player.getZ() - getZ(), player.getX() - getX())) - 90;
            park(yaw, 0, false);
            return;
        }
        parked = false; entityData.set(SEATED, false); setNoAi(false); setNoGravity(false);
        if (tickCount >= nextPath) {
            getNavigation().moveTo(destination.x, destination.y, destination.z, 0, 1.25);
            nextPath = tickCount + 10;
        }
        // Navigation finishes at a block waypoint; close the last fraction to the exact home.
        if (getNavigation().isDone() && distanceToSqr(destination) < 2.25
            && Math.abs(getY() - destination.y) < .5
            && level.noCollision(this, getBoundingBox().expandTowards(destination.subtract(position())))) {
            getMoveControl().setWantedPosition(destination.x, destination.y, destination.z, 1.0);
        }
    }

    private Vec3 frontOf(ServerLevel level, ServerPlayer player) {
        double yaw = Math.toRadians(player.getYRot());
        Vec3 forward = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        Vec3 side = new Vec3(forward.z, 0, -forward.x);
        // Nearby alternatives allow walls, ledges and other attention-seeking mobs to share space.
        for (double distance : new double[]{2.5, 3.5, 1.75}) {
            for (double offset : new double[]{0, -1.75, 1.75}) {
                BlockPos target = BlockPos.containing(player.position().add(forward.scale(distance)).add(side.scale(offset)));
                for (int dy : new int[]{0, 1, -1, -2, -3}) {
                    BlockPos feet = target.offset(0, dy, 0);
                    Vec3 point = Vec3.atBottomCenterOf(feet);
                    if (level.hasChunkAt(feet) && getNavigation().isStableDestination(feet) && clearAt(level, point)) return point;
                }
            }
        }
        return null;
    }

    private boolean clearAt(ServerLevel level, Vec3 point) {
        return level.noCollision(this, getBoundingBox().move(point.subtract(position())));
    }

    private void park(float yaw, float pitch, boolean seated) {
        stopMoving(); entityData.set(SEATED, seated); parked = true; parkedYaw = yaw; parkedPitch = pitch;
    }

    private void stopMoving() {
        getNavigation().stop(); getMoveControl().setWait();
        setZza(0); setXxa(0); setJumping(false); setDeltaMovement(Vec3.ZERO);
        setNoAi(true); setNoGravity(true); parked = false;
    }

    @Override protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        // Persist the model type through EntityType. Activity and home refresh from the binding.
    }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input); activity(AgentActivity.UNKNOWN);
        // A saved mob may have been travelling when its chunk unloaded.
        stopMoving();
    }
}
