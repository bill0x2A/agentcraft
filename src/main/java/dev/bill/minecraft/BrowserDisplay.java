package dev.bill.minecraft;

import net.minecraft.network.syncher.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** A saved monitor; its Chromium process belongs to the local client, not the world save. */
public final class BrowserDisplay extends Mob {
    private static final EntityDataAccessor<String> URL = SynchedEntityData.defineId(BrowserDisplay.class, EntityDataSerializers.STRING);
    private net.minecraft.core.BlockPos anchor;
    public float displayScale() { return getType() == OfficeEntities.MONITOR ? .22F : 1F; }
    public net.minecraft.core.BlockPos anchor() { return anchor; }
    public void anchor(net.minecraft.core.BlockPos pos) { anchor = pos.immutable(); }
    @Override public void tick() {
        super.tick();
        if (!level().isClientSide() && anchor != null && !OfficeBlocks.is(level().getBlockState(anchor), OfficeFurnitureBlock.Kind.MONITOR)) discard();
    }
    public BrowserDisplay(EntityType<? extends BrowserDisplay> type, Level level) {
        super(type,level);
        setNoAi(true); setNoGravity(true); setPersistenceRequired(); setPermanentlyInvulnerable(true);
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder); builder.define(URL,"office:home");
    }
    public String url() { return entityData.get(URL); }
    public void url(String value) { entityData.set(URL,value); }
    @Override public boolean isPushable() { return false; }
    @Override protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output); output.putString("OfficeBrowserUrl",url());
        if (anchor != null) output.putLong("OfficeMonitorBlock", anchor.asLong());
    }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input); url(input.getStringOr("OfficeBrowserUrl","office:home"));
        long saved = input.getLongOr("OfficeMonitorBlock", Long.MIN_VALUE);
        anchor = saved == Long.MIN_VALUE ? null : net.minecraft.core.BlockPos.of(saved);
    }
}
