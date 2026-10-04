package dev.bill.minecraft.client;

import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Session creation/linking is explicit; selecting a session never executes a command. */
final class OfficeScreen extends Screen {
    private final Screen previous;
    private EditBox name;
    private String notice = "", draft = "New project";
    private String character = "codex";
    private List<OfficeBridge.Session> displayed = List.of();
    private String displayedError;
    private boolean busy, createdSession;
    private int page, perPage;

    OfficeScreen(Screen previous) { super(Component.literal("Agent office")); this.previous = previous; }

    @Override protected void init() {
        boolean focusName = name == null || name.isFocused();
        int cursor = name == null ? draft.length() : name.getCursorPosition();
        if (name != null) draft = name.getValue();
        clearWidgets();
        int left = Math.max(12, (width-400)/2), w = Math.min(400, width-24);
        perPage = Math.max(1, (height-194)/24);
        name = new EditBox(font, left, 51, w-182, 20, Component.literal("Session name"));
        name.setMaxLength(48); name.setValue(draft); addRenderableWidget(name);
        name.setCursorPosition(Math.min(cursor, draft.length()));
        name.setHighlightPos(Math.min(cursor, draft.length()));
        if (focusName) setInitialFocus(name);
        addRenderableWidget(Button.builder(Component.literal(character.equals("codex") ? "Codex" : "Claude"), b -> {
            character = character.equals("codex") ? "claude" : "codex"; init();
        }).bounds(left+w-178,51,78,20).build()).active = !busy;
        addRenderableWidget(Button.builder(Component.literal("Create + link"), button -> create())
            .bounds(left+w-96, 51, 96, 20).build()).active = !busy;
        addRenderableWidget(Button.builder(Component.literal("Place browser screen"), b -> BrowserEngine.place(minecraft,this))
            .bounds(left,77,w/2-2,20).build()).active = !busy && BrowserEngine.available();
        addRenderableWidget(Button.builder(Component.literal("Place terminal display"), b -> TerminalDisplays.placeAhead(minecraft,this))
            .bounds(left+w/2+2,77,w-w/2-2,20).build()).active = !busy;
        displayed = OfficeClient.sessions;
        displayedError = OfficeClient.error;
        int maxPage = Math.max(0, (displayed.size()-1)/perPage); page = Math.min(page, maxPage);
        for (int i=page*perPage; i<Math.min(displayed.size(), (page+1)*perPage); i++) {
            OfficeBridge.Session session = displayed.get(i);
            boolean linked = OfficeWorld.linked(session.terminal());
            String label = (linked ? "Linked: " : "Link: ")+session.label()+" · "+session.status();
            addRenderableWidget(Button.builder(Component.literal(font.plainSubstrByWidth(label,w-14)), button -> {
                busy = true; init(); OfficeClient.link(minecraft, session, this);
            }).bounds(left, 117+(i-page*perPage)*24, w, 20).build()).active = !busy && !linked && OfficeClient.error == null;
        }
        int bottom = height-66;
        addRenderableWidget(Button.builder(Component.literal("Previous"), b -> { page--; init(); }).bounds(left,bottom,75,20).build()).active = page > 0 && !busy;
        addRenderableWidget(Button.builder(Component.literal("Next"), b -> { page++; init(); }).bounds(left+79,bottom,60,20).build()).active = page < maxPage && !busy;
        addRenderableWidget(Button.builder(Component.literal("Refresh"), b -> OfficeClient.refresh(minecraft)).bounds(left+143,bottom,70,20).build()).active = !busy;
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose()).bounds(left+w-65,bottom,65,20).build());
    }

    private void create() {
        if (busy) return;
        String label = name.getValue().strip();
        if (label.isEmpty()) { notice = "Enter a session name"; return; }
        var server = minecraft.getSingleplayerServer();
        busy = true; notice = "Creating shell session…"; init();
        OfficeBridge.create(label).whenComplete((session, failure) -> minecraft.execute(() -> {
            if (failure != null) { finished("Could not create session: "+failure.getCause().getMessage()); return; }
            createdSession = true;
            OfficeClient.refresh(minecraft);
            if (minecraft.getSingleplayerServer() != server || !OfficeClient.localWorld(minecraft)) {
                finished("Session created. Link it after returning to your world."); return;
            }
            OfficeClient.link(minecraft, session, this);
        }));
    }

    void finished(String message) {
        busy = false;
        notice = message + (createdSession ? ". Session is available to link below." : "");
        createdSession = false;
        if (minecraft.gui.screen() == this) init();
    }
    @Override public void tick() {
        if (!busy && (!displayed.equals(OfficeClient.sessions) || !java.util.Objects.equals(displayedError, OfficeClient.error))) init();
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int x, int y, float delta) {
        g.fill(0,0,width,height,0xee0a101a);
        int left = Math.max(12,(width-400)/2);
        g.text(font, "AGENT OFFICE", left, 14, 0xff7cdbb6, false);
        g.text(font, "Choose a mob, then create or link a session near you.", left, 31, 0xffbdcce0, false);
        g.text(font, "Existing sessions — page "+(page+1), left, 103, 0xffbdcce0, false);
        super.extractRenderState(g,x,y,delta);
        String status = notice.isEmpty() ? (OfficeClient.error == null ? "Detected Claude/Codex sessions choose their own mob." : OfficeClient.error) : notice;
        String first = font.plainSubstrByWidth(status, width-left*2);
        g.text(font, first, left, height-40, 0xffe5c07b, false);
        if (first.length() < status.length()) g.text(font, font.plainSubstrByWidth(status.substring(first.length()).stripLeading(), width-left*2), left, height-30, 0xffe5c07b, false);
        g.text(font, "Closing the view leaves its session running in Herdr.", left, height-16, 0xff8c9cb2, false);
    }
    @Override public boolean isPauseScreen() { return false; }
    String character() { return character; }
    @Override public void onClose() { minecraft.gui.setScreen(previous); }
}
