package dev.bill.minecraft.client;

import com.google.gson.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.*;

/** Session control requests never run on the render or integrated-server threads. */
final class OfficeBridge {
    record Session(String terminal, String label, String agent, String status, String reportedStatus) {}
    private static final Gson JSON = new Gson();
    private static final ExecutorService IO = Executors.newVirtualThreadPerTaskExecutor();
    private static final ScheduledExecutorService TIMEOUT = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "office-request-timeouts"); t.setDaemon(true); return t;
    });

    static Path socket() {
        return Path.of(System.getProperty("modding.terminal.socket", "../.runtime/terminal.sock")).toAbsolutePath().normalize();
    }

    static CompletableFuture<List<Session>> sessions() {
        return request(TerminalConnection.event("sessions")).thenApply(response ->
            List.of(JSON.fromJson(response.get("sessions"), Session[].class)));
    }

    static CompletableFuture<Session> create(String label) {
        JsonObject request = TerminalConnection.event("create"); request.addProperty("label", label);
        return request(request).thenApply(response -> JSON.fromJson(response.get("session"), Session.class));
    }

    static CompletableFuture<JsonObject> request(JsonObject message) {
        return CompletableFuture.supplyAsync(() -> {
            try (SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX)) {
                var timeout = TIMEOUT.schedule(() -> { try { channel.close(); } catch (IOException ignored) {} }, 12, TimeUnit.SECONDS);
                try {
                    channel.connect(UnixDomainSocketAddress.of(socket()));
                    ByteBuffer data = StandardCharsets.UTF_8.encode(message + "\n");
                    while (data.hasRemaining()) channel.write(data);
                    var line = new ByteArrayOutputStream();
                    ByteBuffer buffer = ByteBuffer.allocate(4096);
                    while (channel.read(buffer) != -1) {
                        buffer.flip();
                        while (buffer.hasRemaining()) {
                            byte b = buffer.get();
                            if (b == '\n') {
                                JsonObject response = JsonParser.parseString(line.toString(StandardCharsets.UTF_8)).getAsJsonObject();
                                if (response.get("type").getAsString().equals("error")) throw new IOException(response.get("message").getAsString());
                                return response;
                            }
                            line.write(b);
                            if (line.size() > 16 * 1024 * 1024) throw new IOException("Office response too large");
                        }
                        buffer.clear();
                    }
                    throw new IOException("Bridge disconnected");
                } finally { timeout.cancel(false); }
            } catch (Exception e) { throw new CompletionException(e); }
        }, IO);
    }
}
