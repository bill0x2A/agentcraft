package dev.bill.minecraft.client;

import com.google.gson.JsonObject;
import java.nio.file.Path;
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
    private static final int LEFT = 12, TOP = 34, CELL_W = 6, CELL_H = 10;
    private static final int BACKGROUND = 0xff111720;
    private static final Style MONO = Style.EMPTY.withFont(new FontDescription.Resource(Identifier.fromNamespaceAndPath("modding_workspace", "terminal")));
    private static final int[] PALETTE = {0x151a23,0xe06c75,0x98c379,0xe5c07b,0x61afef,0xc678dd,0x56b6c2,0xabb2bf,
        0x596273,0xff808b,0xb5e890,0xffda95,0x85c5ff,0xe2a0ff,0x7bdde5,0xf2f5fa};
    private final Screen previous;
    final TerminalConnection connection;
    private int cols, rows;
    private int ticks;
    private boolean captured;
    private boolean mouseReporting;

    public TerminalScreen(Screen previous) {
        this(previous, null);
    }

    public TerminalScreen(Screen previous, String terminal) {
        super(Component.literal("Herdr Terminal"));
        this.previous = previous;
        Path socket = Path.of(System.getProperty("modding.terminal.socket", "../.runtime/terminal.sock")).toAbsolutePath().normalize();
        connection = new TerminalConnection(socket, terminal);
    }

    @Override protected void init() {
        minecraft.textInputManager().startTextInput(this);
        minecraft.textInputManager().setTextInputArea(LEFT, TOP, width-LEFT, height-24);
        cols = Math.clamp((width - LEFT * 2) / CELL_W, 20, 240);
        rows = Math.clamp((height - TOP - 34) / CELL_H, 5, 100);
        JsonObject message = TerminalConnection.event("resize");
        message.addProperty("cols", cols);
        message.addProperty("rows", rows);
        connection.send(message);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, 0xff0a0e15);
        g.fill(0, 0, width, 26, 0xff192333);
        g.text(font, "HERDR / MINECRAFT", LEFT, 9, 0xff7cdbb6, false);
        String status = connection.status;
        if (status.length() > Math.max(20, (width-155)/6)) status = status.substring(0, Math.max(20, (width-155)/6));
        g.text(font, status, Math.min(width - 120, 150), 9, 0xffa8bad2, false);
        g.fill(LEFT - 2, TOP - 2, LEFT + cols * CELL_W + 2, TOP + rows * CELL_H + 2, BACKGROUND);
        g.enableScissor(LEFT, TOP, Math.min(width - 8, LEFT + cols * CELL_W), Math.min(height - 20, TOP + rows * CELL_H));
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
                    Style style = MONO.withBold((cell.modifiers() & 1) != 0)
                        .withItalic((cell.modifiers() & 4) != 0).withUnderlined((cell.modifiers() & 8) != 0)
                        .withStrikethrough((cell.modifiers() & 256) != 0);
                    g.text(font, Component.literal(cell.text()).setStyle(style), px, py, fg, false);
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
        g.text(font, "F8 close · Esc terminal · F6 mouse " + (mouseReporting ? "ON" : "off"), LEFT, height-24, 0xff8c9cb2, false);
        g.text(font, "Ctrl+Shift+V paste · Ctrl+Shift+C copy screen", LEFT, height-13, 0xff8c9cb2, false);
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
        int col = (int)((e.x()-LEFT)/CELL_W), row = (int)((e.y()-TOP)/CELL_H);
        if (e.x() < LEFT || e.y() < TOP || col >= cols || row >= rows || e.button() < 1 || e.button() > 3) return false;
        JsonObject event = TerminalConnection.event("mouse");
        event.addProperty("action", action);
        event.addProperty("button", e.button() == 3 ? 1 : e.button() == 2 ? 2 : 0);
        event.addProperty("col", col); event.addProperty("row", row);
        event.addProperty("modifiers", modifiers(e.modifiers()));
        connection.send(event); return true;
    }

    @Override public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) { return mouse(e,"down"); }
    @Override public boolean mouseReleased(MouseButtonEvent e) { return mouse(e,"up"); }
    @Override public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) { return mouse(e,"drag"); }
    @Override public boolean mouseScrolled(double x, double y, double sx, double sy) {
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
        connection.close();
    }
}
