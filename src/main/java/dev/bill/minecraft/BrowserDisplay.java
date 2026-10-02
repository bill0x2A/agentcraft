package dev.bill.minecraft;

import net.minecraft.network.syncher.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** A saved monitor; its Chromium process belongs to the local client, not the world save. */
public final class BrowserDisplay extends Mob {
    private static final EntityDataAccessor<String> URL = SynchedEntityData.defineId(BrowserDisplay.class, EntityDataSerializers.STRING);
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
    }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input); url(input.getStringOr("OfficeBrowserUrl","office:home"));
    }
}
