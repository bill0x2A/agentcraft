package dev.bill.minecraft.client;

import com.google.gson.*;
import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Socket work is isolated from Minecraft's render/game thread. */
final class TerminalConnection implements AutoCloseable {
    record Cell(String text, int foreground, int background, int modifiers, boolean skip) {}
    record Frame(int width, int height, Cell[] cells, int cursorX, int cursorY,
                 boolean cursorVisible, int cursorShape) {}

    private final ArrayBlockingQueue<String> outgoing = new ArrayBlockingQueue<>(256);
    private volatile boolean closed;
    private volatile SocketChannel channel;
    volatile String status = "Connecting to herdr…";
    volatile Frame frame;
    volatile long framesReceived;

    TerminalConnection(Path socket, String terminal) {
        JsonObject attach = event("attach");
        if (terminal != null) attach.addProperty("terminal", terminal);
        send(attach);
        Thread.ofVirtual().name("minecraft-herdr-read").start(() -> connect(socket));
    }

    private void connect(Path socket) {
        try (SocketChannel connection = SocketChannel.open(StandardProtocolFamily.UNIX)) {
            channel = connection;
            connection.connect(UnixDomainSocketAddress.of(socket));
            Thread.ofVirtual().name("minecraft-herdr-write").start(this::writeLoop);
            ByteBuffer buffer = ByteBuffer.allocate(65536);
            var pending = new java.io.ByteArrayOutputStream();
            while (!closed && connection.read(buffer) != -1) {
                buffer.flip();
                while (buffer.hasRemaining()) {
                    byte b = buffer.get();
                    if (b == '\n') {
                        receive(JsonParser.parseString(pending.toString(StandardCharsets.UTF_8)).getAsJsonObject());
                        pending.reset();
                    } else {
                        pending.write(b);
                        if (pending.size() > 8 * 1024 * 1024) throw new IOException("Oversized terminal frame");
                    }
                }
                buffer.clear();
            }
            if (!closed) status = "Disconnected — F8 to close; F8 again to reconnect";
        } catch (Exception e) {
            if (!closed) status = "Connection failed: " + e.getMessage() + " — run scripts/terminal";
        } finally {
            closed = true;
        }
    }

    private void receive(JsonObject message) throws IOException {
        switch (message.get("type").getAsString()) {
            case "connected" -> status = "LIVE  ·  " + message.get("terminal").getAsString();
            case "error" -> status = message.get("message").getAsString();
            case "notice" -> {
                String value = message.get("message").getAsString();
                if (value.toLowerCase().contains("attach") || value.toLowerCase().contains("control")) status = value;
            }
            case "frame" -> {
                int w = message.get("width").getAsInt(), h = message.get("height").getAsInt();
                if (w < 1 || h < 1 || w > 300 || h > 150) throw new IOException("Invalid terminal dimensions");
                JsonArray raw = message.getAsJsonArray("cells");
                if (raw.size() != w * h) throw new IOException("Incomplete terminal frame");
                Cell[] cells = new Cell[raw.size()];
                for (int i = 0; i < cells.length; i++) {
                    JsonArray c = raw.get(i).getAsJsonArray();
                    cells[i] = new Cell(c.get(0).getAsString(), c.get(1).getAsInt(), c.get(2).getAsInt(),
                                       c.get(3).getAsInt(), c.get(4).getAsInt() != 0);
                }
                JsonArray cursor = message.get("cursor").isJsonNull() ? null : message.getAsJsonArray("cursor");
                frame = new Frame(w, h, cells, cursor == null ? 0 : cursor.get(0).getAsInt(),
                    cursor == null ? 0 : cursor.get(1).getAsInt(), cursor != null && cursor.get(2).getAsInt() != 0,
                    cursor == null ? 0 : cursor.get(3).getAsInt());
                framesReceived++;
            }
            default -> { }
        }
    }

    void send(JsonObject message) {
        if (!closed && !outgoing.offer(message.toString() + "\n")) {
            status = "Input queue full — disconnected to prevent missing keystrokes";
            close();
        }
    }

    void text(String value, boolean paste) {
        JsonObject message = event(paste ? "paste" : "text");
        message.addProperty("text", value);
        send(message);
    }

    void key(String key, int modifiers) {
        JsonObject message = event("key");
        message.addProperty("key", key);
        message.addProperty("modifiers", modifiers);
        send(message);
    }

    static JsonObject event(String type) {
        JsonObject object = new JsonObject();
        object.addProperty("type", type);
        return object;
    }

    private void writeLoop() {
        try {
            while (!closed) {
                String message = outgoing.poll(500, TimeUnit.MILLISECONDS);
                if (message == null) continue;
                ByteBuffer data = StandardCharsets.UTF_8.encode(message);
                while (data.hasRemaining()) channel.write(data);
            }
        } catch (Exception e) {
            if (!closed) status = "Input disconnected: " + e.getMessage();
            close();
        }
    }

    @Override public void close() {
        closed = true;
        if (channel != null) try { channel.close(); } catch (IOException ignored) { }
    }
}
