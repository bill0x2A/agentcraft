package dev.bill.minecraft.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;

/**
 * Herdr permits one attached client for each terminal, and a new attach resets the terminal to 100×32.
 * So the terminal screen, each terminal display and each memo share one connection for a terminal.
 * Client-thread owned.
 */
final class TerminalPool {
    private static final class Entry { TerminalConnection connection; int holders; }
    private static final Map<String, Entry> ENTRIES = new HashMap<>();
    private static final Map<String, int[]> SIZES = new HashMap<>();

    /** A null terminal is the bridge's default terminal. */
    static TerminalConnection acquire(String terminal) { return acquire(terminal, false); }

    static TerminalConnection acquire(String terminal, boolean takeover) {
        String key = terminal != null ? terminal : defaultTerminal();
        Entry entry = ENTRIES.get(key);
        if (entry == null || entry.connection.closed() || takeover && entry.connection.framesReceived == 0) {
            if(entry != null) entry.connection.close();
            entry = new Entry();
            entry.connection = new TerminalConnection(OfficeBridge.socket(), terminal, takeover);
            ENTRIES.put(key, entry);
            // Keep the size the player last used, in place of the 100×32 of a new attach.
            int[] size = SIZES.get(key);
            if (size != null) resize(entry.connection, size[0], size[1]);
        }
        entry.holders++;
        return entry.connection;
    }

    static void release(TerminalConnection connection) {
        for (var it = ENTRIES.entrySet().iterator(); it.hasNext();) {
            var item = it.next();
            Entry entry = item.getValue();
            if (entry.connection != connection) continue;
            if (connection.cols > 0) SIZES.put(item.getKey(), new int[]{connection.cols, connection.rows});
            if (--entry.holders <= 0) { it.remove(); connection.close(); }
            return;
        }
        connection.close(); // Already replaced after a failure.
    }

    /** Pastes text into a terminal, with Enter if asked. Reports null, or the reason of the failure. */
    static void paste(String terminal, String text, boolean enter, Consumer<String> done) {
        Minecraft client = Minecraft.getInstance();
        TerminalConnection connection = acquire(terminal);
        Thread.ofVirtual().name("office-memo").start(() -> {
            String failure = null;
            try {
                // Send only after the attach is live, and keep the socket open until the bridge forwards the input.
                long deadline = System.nanoTime() + 6_000_000_000L;
                while (!connection.attached() && !connection.closed() && System.nanoTime() < deadline) Thread.sleep(25);
                if (!connection.attached()) failure = connection.closed() ? connection.status : "The bridge did not answer";
                else {
                    connection.text(text, true);
                    // An application can read a paste and an Enter that arrive together as one paste.
                    Thread.sleep(150);
                    if (enter) connection.key("Enter", 0);
                    Thread.sleep(400);
                    if (connection.closed()) failure = connection.status;
                }
            } catch (InterruptedException e) { failure = "Interrupted"; }
            String result = failure;
            client.execute(() -> { release(connection); done.accept(result); });
        });
    }

    private static void resize(TerminalConnection connection, int cols, int rows) {
        JsonObject message = TerminalConnection.event("resize");
        message.addProperty("cols", cols); message.addProperty("rows", rows);
        connection.send(message);
    }

    /** The bridge records its default terminal beside its socket. Unknown is a key of its own. */
    private static String defaultTerminal() {
        try {
            return JsonParser.parseString(Files.readString(OfficeBridge.socket().resolveSibling("pane.json")))
                .getAsJsonObject().get("terminal_id").getAsString();
        } catch (Exception e) { return ""; }
    }
}
