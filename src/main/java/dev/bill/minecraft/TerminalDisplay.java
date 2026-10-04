package dev.bill.minecraft;

import net.minecraft.core.Direction;
import net.minecraft.network.syncher.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** A saved wall display of one terminal. The local client owns the live connection, not the world save. */
public final class TerminalDisplay extends Mob {
    private static final EntityDataAccessor<String> TERMINAL = SynchedEntityData.defineId(TerminalDisplay.class, EntityDataSerializers.STRING);
    public TerminalDisplay(EntityType<? extends TerminalDisplay> type, Level level) {
        super(type,level);
        setNoAi(true); setNoGravity(true); setPersistenceRequired(); setPermanentlyInvulnerable(true);
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder); builder.define(TERMINAL,"");
    }
    public String terminal() { return entityData.get(TERMINAL); }
    public void terminal(String value) { entityData.set(TERMINAL,value); }
    @Override public boolean isPushable() { return false; }
    /** Thin along the facing axis, so a click on something in front of the face does not hit the display. */
    @Override protected AABB makeBoundingBox(Vec3 position) {
        boolean wideOnX = Direction.fromYRot(getYRot()).getAxis() == Direction.Axis.Z;
        double x = wideOnX ? 2.1 : .15, z = wideOnX ? .15 : 2.1;
        return new AABB(position.x-x, position.y, position.z-z, position.x+x, position.y+2.5, position.z+z);
    }
    @Override public void setYRot(float yRot) {
        super.setYRot(yRot);
        if (position() != null) setBoundingBox(makeBoundingBox());
    }
    @Override protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output); output.putString("OfficeTerminalId",terminal());
    }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input); terminal(input.getStringOr("OfficeTerminalId",""));
    }
}
