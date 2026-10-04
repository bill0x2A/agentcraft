package dev.bill.minecraft.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/** Renames one mob in this world. The session and its name in Herdr are not changed. */
final class RenameScreen extends Screen {
    private final Screen previous;
    private final UUID mob;
    private EditBox name;
    private String draft, notice = "";
    private boolean busy;

    RenameScreen(Screen previous, UUID mob) {
        super(Component.literal("Rename agent"));
        this.previous = previous; this.mob = mob;
        OfficeWorld.Binding binding = OfficeWorld.binding(mob);
        draft = binding == null ? "" : binding.label();
    }

    @Override protected void init() {
        if (name != null) draft = name.getValue();
        clearWidgets();
        int w = Math.min(260, width-24), left = (width-w)/2, top = height/2-22, half = (w-4)/2;
        name = new EditBox(font, left, top, w, 20, Component.literal("Mob name"));
        name.setMaxLength(48); name.setValue(draft); addRenderableWidget(name);
        setInitialFocus(name);
        addRenderableWidget(Button.builder(Component.literal("Rename"), b -> submit()).bounds(left, top+26, half, 20).build()).active = !busy;
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose()).bounds(left+w-half, top+26, half, 20).build());
    }

    private void submit() {
        if (busy) return;
        String label = name.getValue().strip();
        if (label.isEmpty()) { notice = "Enter a name"; return; }
        busy = true; notice = "Renaming…"; init();
        OfficeClient.rename(minecraft, mob, label, this);
    }

    void finished(String message) {
        busy = false; notice = message;
        if (minecraft.gui.screen() == this) init();
    }

    @Override public boolean keyPressed(KeyEvent event) {
        if (name.isFocused() && (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER)) { submit(); return true; }
        return super.keyPressed(event);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int x, int y, float delta) {
        g.fill(0,0,width,height,0xee0a101a);
        int left = (width-Math.min(260, width-24))/2, top = height/2-22;
        g.text(font, "RENAME AGENT", left, top-26, 0xff7cdbb6, false);
        g.text(font, "The session keeps its name in Herdr.", left, top-13, 0xffbdcce0, false);
        super.extractRenderState(g,x,y,delta);
        g.text(font, font.plainSubstrByWidth(notice, width-left*2), left, top+52, 0xffe5c07b, false);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { minecraft.gui.setScreen(previous); }
}
