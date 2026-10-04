package dev.bill.minecraft.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/** Sends one short text to the terminal of an agent mob, without the terminal screen. */
final class MemoScreen extends Screen {
    private static boolean enter = true;
    private final Screen previous;
    private final String terminal, label;
    private EditBox text;
    private String draft = "", notice = "";
    private boolean busy;

    MemoScreen(Screen previous, UUID mob) {
        super(Component.literal("Memo"));
        this.previous = previous;
        OfficeWorld.Binding binding = OfficeWorld.binding(mob);
        terminal = binding == null ? null : binding.terminal(); label = binding == null ? "" : binding.label();
    }

    @Override protected void init() {
        if (text != null) draft = text.getValue();
        clearWidgets();
        int w = Math.min(360, width-24), left = (width-w)/2, top = height/2-22, third = (w-8)/3;
        text = new EditBox(font, left, top, w, 20, Component.literal("Memo text"));
        text.setMaxLength(1000); text.setValue(draft); addRenderableWidget(text);
        setInitialFocus(text);
        addRenderableWidget(Button.builder(Component.literal("Send"), b -> submit()).bounds(left, top+26, third, 20).build()).active = !busy;
        addRenderableWidget(Button.builder(Component.literal(enter ? "Enter: yes" : "Enter: no"), b -> { enter = !enter; init(); })
            .bounds(left+third+4, top+26, third, 20).build()).active = !busy;
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose()).bounds(left+w-third, top+26, third, 20).build());
    }

    private void submit() {
        if (busy) return;
        String memo = text.getValue().strip();
        if (terminal == null) { notice = "This mob has no session"; return; }
        if (memo.isEmpty()) { notice = "Type a text first"; return; }
        busy = true; notice = "Sending…"; init();
        TerminalPool.paste(terminal, memo, enter, failure -> {
            if (failure != null) { busy = false; notice = "Not sent: " + failure; if (minecraft.gui.screen() == this) init(); return; }
            if (minecraft.gui.screen() == this) onClose();
            OfficeClient.message(minecraft, "Memo sent to " + label + ".");
        });
    }

    @Override public boolean keyPressed(KeyEvent event) {
        if (text.isFocused() && (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER)) { submit(); return true; }
        return super.keyPressed(event);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int x, int y, float delta) {
        g.fill(0,0,width,height,0xee0a101a);
        int left = (width-Math.min(360, width-24))/2, top = height/2-22;
        g.text(font, "MEMO TO " + label.toUpperCase(java.util.Locale.ROOT), left, top-26, 0xff7cdbb6, false);
        g.text(font, enter ? "The text goes to the terminal, then Enter." : "The text goes to the terminal. You press Enter there.", left, top-13, 0xffbdcce0, false);
        super.extractRenderState(g,x,y,delta);
        g.text(font, font.plainSubstrByWidth(notice, width-left*2), left, top+52, 0xffe5c07b, false);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { minecraft.gui.setScreen(previous); }
}
