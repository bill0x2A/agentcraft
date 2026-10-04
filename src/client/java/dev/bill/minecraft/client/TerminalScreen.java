package dev.bill.minecraft.client;

import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.InputConstants;

/** A real Herdr terminal surface. F8 detaches; Escape belongs to the terminal. */
public final class TerminalScreen extends Screen {
    // Layout units. One unit is `zoom` physical pixels, so the grid does not follow Minecraft's GUI scale.
    private static final int LEFT = 12, HEADER = 12, TOP = 14, FOOTER = 13, CELL_W = 6, CELL_H = 10;
    private static final int MAX_ZOOM = 12; // One font/terminal_<zoom>.json for each zoom.
    private static final int BACKGROUND = 0xff111720;
    private static int zoom; // 0 until the first screen picks a default; kept for the game session.
    private Style mono;
    private static final int[] PALETTE = {0x151a23,0xe06c75,0x98c379,0xe5c07b,0x61afef,0xc678dd,0x56b6c2,0xabb2bf,
        0x596273,0xff808b,0xb5e890,0xffda95,0x85c5ff,0xe2a0ff,0x7bdde5,0xf2f5fa};
    private final Screen previous;
    final TerminalConnection connection;
    private int cols, rows;
    private float scale;
    private int viewW, viewH;
    private int ticks;
    private boolean captured;
    private boolean mouseReporting;
    int windowX, windowY, windowWidth, windowHeight;
    private boolean positioned, draggingWindow, resizingWindow;
    private double grabX, grabY;
    private int resizeWidth, resizeHeight;
    private int sentCols=-1, sentRows=-1;
    private static int savedX=-1, savedY, savedWidth, savedHeight;

    public TerminalScreen(Screen previous) {
        this(previous, null);
    }

    public TerminalScreen(Screen previous, String terminal) {
        super(Component.literal("Herdr Terminal"));
        this.previous = previous;
        connection = TerminalPool.acquire(terminal, true);
    }

    @Override protected void init() {
        minecraft.textInputManager().startTextInput(this);
        int guiScale = minecraft.getWindow().getGuiScale();
        // Default: a little smaller than the GUI text. Ctrl+= / Ctrl+- / Ctrl+0 change it.
        if (zoom == 0) zoom = defaultZoom(guiScale);
        if (!positioned) {
            windowWidth = savedX < 0 ? Math.round(width * .82f) : savedWidth;
            windowHeight = savedX < 0 ? Math.round(height * .76f) : savedHeight;
            windowX = savedX < 0 ? (width-windowWidth)/2 : savedX;
            windowY = savedX < 0 ? (height-windowHeight)/2 : savedY;
            positioned = true;
        }
        clampWindow();
        layout(guiScale);
        // Do not keep a zoom that leaves less than the smallest grid the bridge accepts.
        while (zoom > 1 && ((viewW - LEFT * 2) / CELL_W < 20 || (viewH - TOP - FOOTER) / CELL_H < 5)) { zoom--; layout(guiScale); }
        minecraft.textInputManager().setTextInputArea(windowX+(int)(LEFT*scale), windowY+(int)(TOP*scale), (int)(cols*CELL_W*scale), (int)(rows*CELL_H*scale));
        if (cols==sentCols && rows==sentRows) return;
        sentCols=cols; sentRows=rows;
        JsonObject message = TerminalConnection.event("resize");
        message.addProperty("cols", cols);
        message.addProperty("rows", rows);
        connection.send(message);
    }

    private void clampWindow() {
        windowWidth=Math.clamp(windowWidth,Math.min(220,width-12),width-12);
        windowHeight=Math.clamp(windowHeight,Math.min(130,height-12),height-12);
        windowX=Math.clamp(windowX,6,Math.max(6,width-windowWidth-6));
        windowY=Math.clamp(windowY,6,Math.max(6,height-windowHeight-6));
        savedX=windowX;savedY=windowY;savedWidth=windowWidth;savedHeight=windowHeight;
    }

    private boolean inWindow(double x,double y) {
        return x>=windowX && x<windowX+windowWidth && y>=windowY && y<windowY+windowHeight;
    }
    private boolean inGrid(double x,double y) {
        double localX=(x-windowX)/scale,localY=(y-windowY)/scale;
        return localX>=LEFT && localX<LEFT+cols*CELL_W && localY>=TOP && localY<TOP+rows*CELL_H;
    }

    private static int defaultZoom(int guiScale) { return Math.clamp(Math.round(guiScale * .8f), 1, MAX_ZOOM); }

    private void layout(int guiScale) {
        scale = (float)zoom / guiScale;
        // The font's oversample equals the zoom: one glyph texel is one physical pixel, so text stays sharp.
        mono = Style.EMPTY.withFont(new FontDescription.Resource(Identifier.fromNamespaceAndPath("modding_workspace", "terminal_" + zoom)));
        viewW = (int)(windowWidth / scale); viewH = (int)(windowHeight / scale);
        cols = Math.clamp((viewW - LEFT * 2) / CELL_W, 20, 240);
        rows = Math.clamp((viewH - TOP - FOOTER) / CELL_H, 5, 100);
    }

    private void zoom(int value) {
        int next = Math.clamp(value, 1, MAX_ZOOM);
        if (next == zoom) return;
        zoom = next; init();
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0,0,width,height,0x33000000);
        g.fill(windowX+4,windowY+5,windowX+windowWidth+4,windowY+windowHeight+5,0x77000000);
        g.fill(windowX,windowY,windowX+windowWidth,windowY+windowHeight,0xff0a0e15);
        g.outline(windowX-1,windowY-1,windowWidth+2,windowHeight+2,0xff466478);
        // Scissor rounds to whole GUI pixels, so set it before the scale and round outwards.
        g.enableScissor(windowX, windowY+(int)((TOP-2)*scale), windowX+windowWidth, Math.min(windowY+windowHeight,windowY+(int)Math.ceil((TOP+rows*CELL_H+2)*scale)));
        g.pose().pushMatrix();
        g.pose().translate(windowX,windowY);
        g.pose().scale(scale, scale);
        g.fill(LEFT - 2, TOP - 2, LEFT + cols * CELL_W + 2, TOP + rows * CELL_H + 2, BACKGROUND);
        TerminalConnection.Frame frame = connection.frame;
        if (frame != null) {
            for (int y = 0; y < Math.min(rows, frame.height()); y++) {
                for (int x = 0; x < Math.min(cols, frame.width()); x++) {
                    TerminalConnection.Cell cell = frame.cells()[y * frame.width() + x];
                    int fg = color(cell.foreground(), false), bg = color(cell.background(), true);
                    if ((cell.modifiers() & 64) != 0) { int swap = fg; fg = bg; bg = swap; }
                    int px = LEFT + x * CELL_W, py = TOP + y * CELL_H;
                    if (bg != BACKGROUND) g.fill(px, py, px + CELL_W, py + CELL_H, bg);
                    if (cell.skip() || (cell.modifiers() & 128) != 0 || cell.text().isBlank()) continue;
                    Style style = mono.withItalic((cell.modifiers() & 4) != 0).withUnderlined((cell.modifiers() & 8) != 0)
                        .withStrikethrough((cell.modifiers() & 256) != 0);
                    Component text = Component.literal(cell.text()).setStyle(style);
                    g.text(font, text, px, py, fg, false);
                    if ((cell.modifiers() & 1) != 0) {
                        // Minecraft's bold repeats the glyph one whole unit to the right, which shows as a
                        // double image on a sharp glyph. Repeat it half a unit away, on a physical pixel.
                        g.pose().pushMatrix();
                        g.pose().translate(Math.max(1, Math.round(zoom / 2f)) / (float)zoom, 0);
                        g.text(font, text, px, py, fg, false);
                        g.pose().popMatrix();
                    }
                }
            }
            if (frame.cursorVisible() && (System.currentTimeMillis()/500)%2 == 0) {
                int x = LEFT + frame.cursorX() * CELL_W, y = TOP + frame.cursorY() * CELL_H;
                if (frame.cursorShape() == 5 || frame.cursorShape() == 6) g.fill(x, y, x+1, y+CELL_H, 0xffb5e890);
                else if (frame.cursorShape() == 3 || frame.cursorShape() == 4) g.fill(x,y+CELL_H-2,x+CELL_W,y+CELL_H,0xffb5e890);
                else g.outline(x, y, CELL_W, CELL_H, 0xffb5e890);
            }
        } else {
            g.text(font, "Waiting for terminal. Start ./scripts/terminal in the project folder.", LEFT+6, TOP+12, 0xffa8bad2, false);
        }
        g.disableScissor();
        g.fill(0, 0, viewW + 1, HEADER, 0xff192333);
        g.text(font, "HERDR / MINECRAFT", LEFT, 2, 0xff7cdbb6, false);
        int statusX = LEFT + font.width("HERDR / MINECRAFT") + 12;
        g.text(font, font.plainSubstrByWidth(connection.status, Math.max(0, viewW - statusX - LEFT - 18)), statusX, 2, 0xffa8bad2, false);
        g.text(font,"×",viewW-LEFT,2,0xffd8e1ed,false);
        // One line. Drop whole hints from the end when the line is too wide.
        String hints = "";
        for (String hint : new String[]{"F8 close", "Esc terminal", "F6 mouse " + (mouseReporting ? "ON" : "off"), "Ctrl+-/= size",
            "Ctrl+Shift+V paste", "Ctrl+Shift+C copy screen"}) {
            String longer = hints.isEmpty() ? hint : hints + " · " + hint;
            if (font.width(longer) > viewW - LEFT * 2) break;
            hints = longer;
        }
        g.text(font, hints, LEFT, viewH - 10, 0xff8c9cb2, false);
        g.pose().popMatrix();
        for(int i=0;i<3;i++)g.fill(windowX+windowWidth-4-i*3,windowY+windowHeight-5-i*3,windowX+windowWidth-3-i*3,windowY+windowHeight-3,0xff7cdbb6);
    }

    private static int modifiers(int sdl) {
        return ((sdl & InputConstants.MOD_SHIFT) != 0 ? 1 : 0)
            | ((sdl & InputConstants.MOD_CONTROL) != 0 ? 2 : 0)
            | ((sdl & InputConstants.MOD_ALT) != 0 ? 4 : 0)
            | ((sdl & InputConstants.MOD_SUPER) != 0 ? 8 : 0);
    }

    static int color(int value, boolean background) {
        int tag = value >>> 24, n = value & 255;
        if (tag == 2) return 0xff000000 | (value & 0xffffff);
        if (tag == 0) return n == 0 ? (background ? BACKGROUND : 0xffd8e1ed) : 0xff000000 | PALETTE[Math.min(15, n-1)];
        if (n < 16) return 0xff000000 | PALETTE[n];
        if (n >= 232) { int gray = 8 + (n-232)*10; return 0xff000000 | gray*0x010101; }
        n -= 16;
        int[] levels = {0,95,135,175,215,255};
        return 0xff000000 | levels[n/36]*65536 | levels[n/6%6]*256 | levels[n%6];
    }

    @Override public boolean keyPressed(KeyEvent event) {
        int key = event.key(), mods = modifiers(event.modifiers());
        if (key == InputConstants.KEY_F8) return true; // Global F8 edge handler owns detach.
        if (key == InputConstants.KEY_F6) { mouseReporting = !mouseReporting; return true; }
        if ((mods & 14) == 2) {
            if (key == InputConstants.KEY_EQUALS) { zoom(zoom + 1); return true; }
            // Ctrl+Shift+- is Ctrl+_ and belongs to the terminal application.
            if (key == InputConstants.KEY_MINUS && (mods & 1) == 0) { zoom(zoom - 1); return true; }
            if (key == InputConstants.KEY_0 && (mods & 1) == 0) { zoom(defaultZoom(minecraft.getWindow().getGuiScale())); return true; }
        }
        if ((mods & 3) == 3 && key == InputConstants.KEY_V) {
            connection.text(minecraft.keyboardHandler.getClipboard(), true); return true;
        }
        if ((mods & 3) == 3 && key == InputConstants.KEY_C) {
            copyScreen(); return true;
        }
        String name = switch (key) {
            case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> "Enter";
            case InputConstants.KEY_BACKSPACE -> "Backspace";
            case InputConstants.KEY_TAB -> (mods & 1) != 0 ? "BackTab" : "Tab";
            case InputConstants.KEY_ESCAPE -> "Esc";
            case InputConstants.KEY_LEFT -> "Left";
            case InputConstants.KEY_RIGHT -> "Right";
            case InputConstants.KEY_UP -> "Up";
            case InputConstants.KEY_DOWN -> "Down";
            case InputConstants.KEY_HOME -> "Home";
            case InputConstants.KEY_END -> "End";
            case InputConstants.KEY_PAGEUP -> "PageUp";
            case InputConstants.KEY_PAGEDOWN -> "PageDown";
            case InputConstants.KEY_DELETE -> "Delete";
            case InputConstants.KEY_INSERT -> "Insert";
            default -> null;
        };
        if (name == null && key >= InputConstants.KEY_F1 && key <= InputConstants.KEY_F12) name = "F"+(key-InputConstants.KEY_F1+1);
        if (name == null && (mods & 6) != 0 && event.keycode() >= 32 && event.keycode() <= 126) name = String.valueOf((char)Character.toLowerCase(event.keycode()));
        if (name != null) connection.key(name, mods);
        return true;
    }

    @Override public boolean charTyped(CharacterEvent event) {
        connection.text(event.codepointAsString(), false);
        return true;
    }

    private boolean mouse(MouseButtonEvent e, String action) {
        // Protocol 20's raw attach path does not expose the application's mouse mode.
        // Explicit opt-in prevents escape sequences being inserted at a shell prompt.
        if (!mouseReporting) return false;
        double x = (e.x()-windowX) / scale, y = (e.y()-windowY) / scale;
        int col = (int)((x-LEFT)/CELL_W), row = (int)((y-TOP)/CELL_H);
        if (x < LEFT || y < TOP || col >= cols || row >= rows || e.button() < 1 || e.button() > 3) return false;
        JsonObject event = TerminalConnection.event("mouse");
        event.addProperty("action", action);
        event.addProperty("button", e.button() == 3 ? 1 : e.button() == 2 ? 2 : 0);
        event.addProperty("col", col); event.addProperty("row", row);
        event.addProperty("modifiers", modifiers(e.modifiers()));
        connection.send(event); return true;
    }

    @Override public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if(!inWindow(e.x(),e.y()))return true;
        if(e.button()==1 && e.y()<windowY+HEADER*scale) {
            if(e.x()>windowX+windowWidth-(LEFT+5)*scale){onClose();return true;}
            draggingWindow=true;grabX=e.x()-windowX;grabY=e.y()-windowY;return true;
        }
        if(e.button()==1 && e.x()>windowX+windowWidth-14 && e.y()>windowY+windowHeight-14) {
            resizingWindow=true;grabX=e.x();grabY=e.y();resizeWidth=windowWidth;resizeHeight=windowHeight;return true;
        }
        mouse(e,"down");return true;
    }
    @Override public boolean mouseReleased(MouseButtonEvent e) {
        if(draggingWindow || resizingWindow){draggingWindow=false;resizingWindow=false;return true;}
        return mouse(e,"up");
    }
    @Override public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if(draggingWindow){windowX=(int)(e.x()-grabX);windowY=(int)(e.y()-grabY);init();return true;}
        if(resizingWindow){windowWidth=resizeWidth+(int)(e.x()-grabX);windowHeight=resizeHeight+(int)(e.y()-grabY);init();return true;}
        return mouse(e,"drag");
    }
    @Override public boolean mouseScrolled(double x, double y, double sx, double sy) {
        if(!inGrid(x,y))return false;
        JsonObject event = TerminalConnection.event("scroll"); event.addProperty("up", sy > 0); connection.send(event); return true;
    }

    private void copyScreen() {
        TerminalConnection.Frame frame = connection.frame;
        if (frame == null) return;
        StringBuilder text = new StringBuilder();
        for (int y=0; y<frame.height(); y++) {
            StringBuilder line = new StringBuilder();
            for (int x=0; x<frame.width(); x++) {
                TerminalConnection.Cell cell = frame.cells()[y*frame.width()+x];
                if (!cell.skip()) line.append(cell.text());
            }
            text.append(line.toString().stripTrailing()).append('\n');
        }
        minecraft.keyboardHandler.setClipboard(text.toString());
    }

    @Override public void tick() {
        ticks++;
        if (!captured && ticks > 100 && connection.frame != null && Boolean.getBoolean("modding.terminal.capture")) {
            captured = true;
            net.minecraft.client.Screenshot.grab(minecraft.gameDirectory, "herdr-terminal.png", minecraft.gameRenderer.mainRenderTarget(), 1,
                message -> dev.bill.minecraft.ModdingWorkspace.LOGGER.info("Terminal screenshot: {}", message.getString()));
            dev.bill.minecraft.ModdingWorkspace.LOGGER.info("Terminal verified render: {}x{}, {} frames", connection.frame.width(), connection.frame.height(), connection.framesReceived);
        }
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean shouldCloseOnEsc() { return false; }
    @Override public void onClose() { minecraft.gui.setScreen(previous); }
    @Override public void removed() {
        minecraft.textInputManager().stopTextInput(this);
        TerminalPool.release(connection);
    }
}
