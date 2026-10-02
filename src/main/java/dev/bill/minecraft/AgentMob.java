package dev.bill.minecraft;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Custom office creature; its process and home are managed independently by the office. */
public final class AgentMob extends Mob {
    private static final EntityDataAccessor<Integer> ACTIVITY = SynchedEntityData.defineId(AgentMob.class, EntityDataSerializers.INT);

    public AgentMob(EntityType<? extends AgentMob> type, Level level) {
        super(type, level);
        setNoAi(true); setNoGravity(true); setPersistenceRequired(); setPermanentlyInvulnerable(true);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder); builder.define(ACTIVITY, AgentActivity.UNKNOWN.ordinal());
    }
    public AgentActivity activity() { return AgentActivity.fromId(entityData.get(ACTIVITY)); }
    public void activity(AgentActivity activity) { entityData.set(ACTIVITY, activity.ordinal()); }
    public boolean isClaude() { return getType() == OfficeEntities.CLAUDE; }

    @Override protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        // Persist the model type through EntityType. Activity is refreshed after reconnect.
    }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input); activity(AgentActivity.UNKNOWN);
    }
}
