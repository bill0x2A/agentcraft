package dev.bill.minecraft.client;

import dev.bill.minecraft.TerminalDisplay;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Chooses the terminal of one terminal display, as the address bar chooses the page of a browser display. */
final class TerminalChooserScreen extends Screen {
    private final Screen previous;
    private final TerminalDisplay display;
    private EditBox name;
    private String notice = "", draft;
    private List<OfficeBridge.Session> displayed = List.of();
    private String displayedError;
    private boolean busy;
    private int page, perPage;

    TerminalChooserScreen(Screen previous, TerminalDisplay display) {
        super(Component.literal("Office terminal")); this.previous = previous; this.display = display;
    }

    @Override protected void init() {
        if (draft == null) draft = freeName();
        if (name != null) draft = name.getValue();
        clearWidgets();
        int left = Math.max(12, (width-400)/2), w = Math.min(400, width-24);
        perPage = Math.max(1, (height-194)/24);
        name = new EditBox(font, left, 51, w-104, 20, Component.literal("Session name"));
        name.setMaxLength(48); name.setValue(draft); addRenderableWidget(name);
        addRenderableWidget(Button.builder(Component.literal("Create + show"), b -> create())
            .bounds(left+w-100, 51, 100, 20).build()).active = !busy && OfficeClient.error == null;
        String shown = display.terminal();
        boolean usable = !shown.isEmpty() && OfficeClient.error == null && OfficeClient.session(shown) != null;
        String open = shown.isEmpty() ? "This display shows no terminal" : usable ? "Open " + TerminalDisplays.label(shown) : TerminalDisplays.label(shown) + ": " + TerminalDisplays.status(shown).toLowerCase();
        addRenderableWidget(Button.builder(Component.literal(font.plainSubstrByWidth(open, w-14)), b -> minecraft.gui.setScreen(new TerminalScreen(previous, shown)))
            .bounds(left, 77, w, 20).build()).active = !busy && usable;
        displayed = OfficeClient.sessions;
        displayedError = OfficeClient.error;
        int maxPage = Math.max(0, (displayed.size()-1)/perPage); page = Math.min(page, maxPage);
        for (int i=page*perPage; i<Math.min(displayed.size(), (page+1)*perPage); i++) {
            OfficeBridge.Session session = displayed.get(i);
            boolean current = session.terminal().equals(shown);
            String label = (current ? "Showing: " : "Show: ")+TerminalDisplays.label(session.terminal())+" · "+session.status();
            addRenderableWidget(Button.builder(Component.literal(font.plainSubstrByWidth(label,w-14)), b -> show(session.terminal()))
                .bounds(left, 117+(i-page*perPage)*24, w, 20).build()).active = !busy && !current && OfficeClient.error == null;
        }
        int bottom = height-66;
        addRenderableWidget(Button.builder(Component.literal("Previous"), b -> { page--; init(); }).bounds(left,bottom,75,20).build()).active = page > 0 && !busy;
        addRenderableWidget(Button.builder(Component.literal("Next"), b -> { page++; init(); }).bounds(left+79,bottom,60,20).build()).active = page < maxPage && !busy;
        addRenderableWidget(Button.builder(Component.literal("Refresh"), b -> OfficeClient.refresh(minecraft)).bounds(left+143,bottom,70,20).build()).active = !busy;
        addRenderableWidget(Button.builder(Component.literal("Remove"), b -> { TerminalDisplays.remove(minecraft, display); onClose(); }).bounds(left+w-133,bottom,64,20).build()).active = !busy;
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose()).bounds(left+w-65,bottom,65,20).build());
    }

    /** The first of `Terminal 1`, `Terminal 2`, … that no session has. */
    private static String freeName() {
        Set<String> used = new HashSet<>();
        OfficeClient.sessions.forEach(session -> used.add(session.label()));
        int number = 1;
        while (used.contains("Terminal " + number)) number++;
        return "Terminal " + number;
    }

    private void show(String terminal) {
        TerminalDisplays.assign(minecraft, display, terminal);
        onClose();
        OfficeClient.message(minecraft, "The display shows " + TerminalDisplays.label(terminal) + ". " + TerminalDisplays.HINT);
    }

    /** A shell session with no agent mob. The office screen can link it later. */
    private void create() {
        if (busy) return;
        String label = name.getValue().strip();
        if (label.isEmpty()) { notice = "Enter a session name"; return; }
        var server = minecraft.getSingleplayerServer();
        busy = true; notice = "Creating shell session…"; init();
        OfficeBridge.create(label).whenComplete((session, failure) -> minecraft.execute(() -> {
            busy = false;
            if (failure != null) { notice = "Could not create session: "+failure.getCause().getMessage(); if (minecraft.gui.screen() == this) init(); return; }
            OfficeClient.refresh(minecraft);
            if (minecraft.getSingleplayerServer() != server || !OfficeClient.localWorld(minecraft) || display.isRemoved()) return;
            // The session list does not have the new session yet, so give its name here.
            TerminalDisplays.assign(minecraft, display, session.terminal(), session.label());
            if (minecraft.gui.screen() == this) onClose();
            OfficeClient.message(minecraft, "The display shows the new shell session " + session.label() + ". " + TerminalDisplays.HINT);
        }));
    }

    @Override public void tick() {
        if (display.isRemoved()) { onClose(); return; }
        if (!busy && (!displayed.equals(OfficeClient.sessions) || !Objects.equals(displayedError, OfficeClient.error))) init();
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int x, int y, float delta) {
        g.fill(0,0,width,height,0xee0a101a);
        int left = Math.max(12,(width-400)/2);
        g.text(font, "OFFICE TERMINAL", left, 14, 0xff7cdbb6, false);
        g.text(font, "Choose the terminal that this display shows, or create a shell session.", left, 31, 0xffbdcce0, false);
        g.text(font, "Sessions — page "+(page+1), left, 103, 0xffbdcce0, false);
        super.extractRenderState(g,x,y,delta);
        String status = !notice.isEmpty() ? notice : OfficeClient.error != null ? OfficeClient.error : displayed.isEmpty() ? "No sessions. Create a shell session above." : "";
        g.text(font, font.plainSubstrByWidth(status, width-left*2), left, height-40, 0xffe5c07b, false);
        g.text(font, "The display only shows the terminal. Each session continues in Herdr.", left, height-16, 0xff8c9cb2, false);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { minecraft.gui.setScreen(previous); }
}
