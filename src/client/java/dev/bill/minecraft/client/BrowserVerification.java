package dev.bill.minecraft.client;

import dev.bill.minecraft.ModdingWorkspace;
import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import com.google.gson.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Screenshot;

/** Opt-in focused regression check, limited to the separate browser test directory. */
final class BrowserVerification {
    private static int phase,ticks;
    private static CompletableFuture<String> result;
    private static final Path REPORT=Path.of("../.runtime/browser-verification.txt");
    private static final long DEADLINE=System.nanoTime()+180_000_000_000L;
    static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            if(phase==99)return;
            try {
                if(!client.gameDirectory.toPath().getFileName().toString().equals("run-browser-test"))throw new IllegalStateException("Wrong browser verification directory");
                if(System.nanoTime()>DEADLINE)throw new IllegalStateException("Browser verification timeout phase "+phase);
                if(!(client.gui.screen() instanceof BrowserPreviewScreen screen) || !screen.surface.ready())return;
                switch(phase) {
                    case 0 -> {
                        if(++ticks<60)return;
                        Files.writeString(REPORT,"START focused browser verification\n");
                        result=eval(screen,"document.body.style.height='3000px'; document.title");phase=1;
                    }
                    case 1 -> {
                        if(!result.isDone())return;
                        result.join();screen.mouseScrolled(screen.guiX(900),screen.guiY(500),0,-3);
                        phase=2;ticks=0;
                    }
                    case 2 -> {
                        if(++ticks<30)return;
                        result=eval(screen,"window.scrollY");phase=3;
                    }
                    case 3 -> {
                        if(!result.isDone())return;
                        double y=JsonParser.parseString(result.join()).getAsJsonObject().getAsJsonObject("result").get("value").getAsDouble();
                        if(y<=0)throw new IllegalStateException("Browser wheel did not scroll down");
                        pass("Minecraft mouse wheel scrolls the actual Chromium page in the expected direction");
                        result=eval(screen,"document.body.style.height='auto';window.scrollTo(0,0);document.documentElement.dataset.awaitControl='true'");phase=4;ticks=0;
                    }
                    case 4 -> {
                        if(++ticks<20 || !result.isDone())return;
                        result=eval(screen,"document.documentElement.dataset.officeControl || ''");phase=5;
                    }
                    case 5 -> {
                        if(!result.isDone())return;
                        String marker=JsonParser.parseString(result.join()).getAsJsonObject().getAsJsonObject("result").get("value").getAsString();
                        if(!marker.equals("verified")){phase=4;ticks=0;return;}
                        phase=6;ticks=0;
                    }
                    case 6 -> {
                        if(++ticks<30)return;
                        Screenshot.grab(client.gameDirectory,"browser-controlled.png",client.gameRenderer.mainRenderTarget(),1,m->ModdingWorkspace.LOGGER.info(m.getString()));
                        pass("external CDP helper changed this same embedded browser; ALL PASS");phase=99;
                    }
                }
            }catch(Exception e){phase=99;ModdingWorkspace.LOGGER.error("Browser verification failed",e);try{Files.writeString(REPORT,"FAIL "+e+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}catch(Exception ignored){}}
        });
    }
    private static CompletableFuture<String> eval(BrowserPreviewScreen screen,String js) {
        return screen.surface.browser.getCefBrowser().getDevToolsClient().executeDevToolsMethod("Runtime.evaluate",new Gson().toJson(Map.of("expression",js,"returnByValue",true)));
    }
    private static void pass(String message)throws Exception {Files.writeString(REPORT,"PASS "+message+"\n",StandardOpenOption.APPEND);ModdingWorkspace.LOGGER.info("BROWSER PASS {}",message);}
}
