package dev.bill.minecraft.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.bill.minecraft.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.dimaskama.mcef.api.MCEFApi;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;

final class BrowserEngine {
    private static boolean held, previewOpened;
    private static MCEFApi.Initialization initialization;
    private static ClientLevel world;
    private static long initializationStarted, lastProgressAt;
    private static MCEFApi.Initialization.Stage lastStage;
    private static int lastPercent=-2;
    private static final Map<UUID,BrowserSurface> SURFACES=new HashMap<>();
    static boolean available(){return FabricLoader.getInstance().isModLoaded("mcef-modern");}
    static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if(world!=client.level || (world!=null && !OfficeClient.localWorld(client))) {
                SURFACES.values().forEach(BrowserSurface::close); SURFACES.clear(); world=client.level;
            }
            if(OfficeClient.localWorld(client)) {
                for(var entity:client.level.entitiesForRendering())if(entity instanceof BrowserDisplay display)surface(display);
                // Keep a loaded world's browsers alive offscreen; remove unloaded/removed monitors.
                SURFACES.entrySet().removeIf(entry -> {
                    if(client.level.getEntity(entry.getKey())==null){entry.getValue().close();return true;}return false;
                });
                SURFACES.values().forEach(BrowserSurface::tick);
            }
            boolean down=InputConstants.isKeyDown(InputConstants.KEY_F9);
            if(down && !held && !(client.gui.screen() instanceof TerminalScreen)) {
                if(client.gui.screen() instanceof BrowserPreviewScreen browser)browser.onClose();
                else client.gui.setScreen(new BrowserPreviewScreen(client.gui.screen()));
            }
            held=down;
            if(!previewOpened && Boolean.getBoolean("modding.browser.verify") && client.gui.screen() instanceof TitleScreen) {
                previewOpened=true;client.gui.setScreen(new BrowserPreviewScreen(client.gui.screen()));
            }
        });
        UseEntityCallback.EVENT.register((player,level,hand,entity,hit)-> {
            if(!(entity instanceof BrowserDisplay display))return InteractionResult.PASS;
            Minecraft client=Minecraft.getInstance();
            if(!OfficeClient.localWorld(client))return InteractionResult.PASS;
            if(level.isClientSide())client.gui.setScreen(new BrowserPreviewScreen(client.gui.screen(),display));
            return InteractionResult.SUCCESS;
        });
    }
    static BrowserSurface surface(BrowserDisplay display) {
        if(!OfficeClient.localWorld(Minecraft.getInstance()))return null;
        BrowserSurface surface=SURFACES.computeIfAbsent(display.getUUID(),id->new BrowserSurface(id,display.url()));
        if(!surface.requestedUrl.equals(display.url()))surface.navigate(display.url());
        return surface;
    }
    static MCEFApi.Initialization initialize() {
        if(initialization==null) {
            initializationStarted=lastProgressAt=System.nanoTime();
            initialization=MCEFApi.initialize();
            initialization.getFuture().whenComplete((api,error)-> {
                if(error==null)ModdingWorkspace.LOGGER.info("MCEF browser engine ready");
                else ModdingWorkspace.LOGGER.error("MCEF browser initialization failed",error);
            });
        }
        return initialization;
    }
    static String loadingStatus() {
        if(initialization==null)return "Preparing browser…";
        var stage=initialization.getStage();
        int percent=Math.round(initialization.getPercentage());
        long now=System.nanoTime();
        if(stage!=lastStage || percent!=lastPercent) {
            if(stage!=lastStage)ModdingWorkspace.LOGGER.info("Browser initialization: {} ({}%)",stage,percent);
            lastStage=stage;lastPercent=percent;lastProgressAt=now;
        }
        long seconds=(now-initializationStarted)/1_000_000_000L;
        String label=switch(stage) {
            case NOT_STARTED -> "Checking installed browser";
            case DOWNLOADING -> "Downloading browser engine (first use)";
            case EXTRACTING -> "Unpacking browser engine";
            case INSTALL -> "Installing browser engine";
            case INITIALIZING -> "Starting Chromium";
            case DONE -> "Opening page";
        };
        return label+(percent>=0 && stage!=MCEFApi.Initialization.Stage.DONE?" · "+percent+"%":"")+" · "+seconds+"s";
    }
    static String loadingHint() {
        if(initialization==null)return "";
        if(System.nanoTime()-lastProgressAt>60_000_000_000L)return "No progress reported for over a minute. Check your connection; you can close this view while it finishes.";
        return initialization.getStage()==MCEFApi.Initialization.Stage.DOWNLOADING
            ?"The first download is large. Later launches reuse the installed engine. You can close this view while it finishes."
            :"You can close this view and return when the browser is ready.";
    }

    static String normalizeUrl(String input) {
        String url=input.strip();
        if(url.equals("office:home"))return url;
        if(!url.contains(":"))url="https://"+url;
        URI uri=URI.create(url);
        if(!Set.of("http","https").contains(uri.getScheme().toLowerCase(Locale.ROOT)) || uri.getHost()==null || url.length()>8192)
            throw new IllegalArgumentException("Use an http:// or https:// address");
        return uri.toASCIIString();
    }
    static String resolveUrl(String url) {
        if(url.equals("office:home"))return "data:text/html;base64,"+Base64.getEncoder().encodeToString(PAGE.getBytes(StandardCharsets.UTF_8));
        try{return normalizeUrl(url);}catch(Exception e){return "about:blank";}
    }
    static void saveUrl(BrowserDisplay display,String url) {
        Minecraft client=Minecraft.getInstance(); var server=client.getSingleplayerServer();
        if(!OfficeClient.localWorld(client))return;
        var dimension=display.level().dimension();UUID id=display.getUUID();
        // Update client data immediately, then persist on the integrated server thread.
        display.url(url);
        server.execute(()-> {var level=server.getLevel(dimension);if(level!=null && level.getEntity(id) instanceof BrowserDisplay monitor)monitor.url(url);});
    }
    static BrowserDisplay place(ServerPlayer player) {
        var level=player.level();var direction=player.getDirection();
        BlockPos pos=player.blockPosition().relative(direction,4);
        // Require the full monitor face and a little breathing room to be clear.
        for(int side=-2;side<=2;side++)for(int y=0;y<3;y++) {
            BlockPos check=pos.relative(direction.getClockWise(),side).above(y);
            if(!level.getBlockState(check).getCollisionShape(level,check).isEmpty())throw new IllegalStateException("Leave a clear 5 × 3 area four blocks ahead");
        }
        BrowserDisplay display=OfficeEntities.BROWSER.create(level,EntitySpawnReason.COMMAND);
        if(display==null)throw new IllegalStateException("Could not create monitor");
        display.snapTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,direction.toYRot(),0);
        display.setCustomName(Component.literal("Office browser · right-click"));
        if(!level.addFreshEntity(display))throw new IllegalStateException("Could not place monitor");
        return display;
    }
    static BrowserDisplay placeMonitor(ServerPlayer player, BlockPos pos) {
        var level = player.level();
        var state = level.getBlockState(pos);
        if (!OfficeBlocks.is(state, OfficeFurnitureBlock.Kind.MONITOR)) throw new IllegalStateException("The monitor was removed");
        for (var display : level.getEntitiesOfClass(BrowserDisplay.class, new net.minecraft.world.phys.AABB(pos).inflate(1)))
            if (pos.equals(display.anchor())) return display;
        BrowserDisplay display = OfficeEntities.MONITOR.create(level, EntitySpawnReason.COMMAND);
        if (display == null) throw new IllegalStateException("Could not start monitor");
        display.anchor(pos);
        display.snapTo(pos.getX()+.5, pos.getY()+.2, pos.getZ()+.5, state.getValue(OfficeFurnitureBlock.FACING).getOpposite().toYRot(), 0);
        if (!level.addFreshEntity(display)) throw new IllegalStateException("Could not start monitor");
        return display;
    }

    static void place(Minecraft client,OfficeScreen screen) {
        if(!OfficeClient.localWorld(client))return;
        var server=client.getSingleplayerServer();UUID player=client.player.getUUID();
        server.execute(()-> {
            try {
                var owner=server.getPlayerList().getPlayer(player);
                if(owner==null || server.isPublished())throw new IllegalStateException("Open a private single-player world first");
                place(owner);
                client.execute(()-> {if(client.gui.screen()==screen)screen.onClose();OfficeClient.message(client,"Placed browser. Right-click to browse; Remove in its controls deletes the screen.");});
            }catch(Exception e){client.execute(()->screen.finished(e.getMessage()));}
        });
    }
    static void remove(BrowserDisplay display) {
        var client=Minecraft.getInstance();var server=client.getSingleplayerServer();
        if(!OfficeClient.localWorld(client))return;
        var dimension=display.level().dimension();UUID id=display.getUUID();
        server.execute(()->{var level=server.getLevel(dimension);if(level!=null && level.getEntity(id) instanceof BrowserDisplay monitor) {
            if (monitor.anchor() != null && OfficeBlocks.is(level.getBlockState(monitor.anchor()), OfficeFurnitureBlock.Kind.MONITOR)) level.removeBlock(monitor.anchor(), false);
            monitor.discard();
        }});
    }
    static final String PAGE="""
        <!doctype html><html><meta charset="utf-8"><title>Agent Office</title><style>
        *{box-sizing:border-box}body{margin:0;background:#101923;color:#e8f1f0;font:20px system-ui;padding:48px}
        small{color:#67d9b7;letter-spacing:3px}h1{font-size:40px;margin:22px 0 12px}p{color:#aebecb;max-width:660px;line-height:1.5}
        button,input{font:inherit;border:1px solid #385663;border-radius:8px;padding:12px 18px;margin:12px 12px 12px 0}
        button{background:#67d9b7;color:#102019;cursor:pointer}input{background:#1b2a36;color:white;width:320px}
        #pulse{display:inline-block;width:12px;height:12px;border-radius:50%;background:#67d9b7;animation:p 1s infinite alternate}
        @keyframes p{to{opacity:.25}} #result{color:#67d9b7}
        </style><small>AGENT OFFICE / CHROMIUM</small><h1>Browser engine ready.</h1>
        <p>This is a real browser rendered inside Minecraft. Right-click a world screen to browse. Close the controls and the same page keeps running in your office.</p>
        <p><span id="pulse"></span> <span id="js">Starting JavaScript…</span></p>
        <button id="button" onclick="this.textContent='Clicks: '+(++window.clicks)">Clicks: 0</button>
        <input aria-label="Keyboard test" placeholder="Type here to test input" oninput="document.getElementById('result').textContent=this.value">
        <p id="result"></p><script>window.clicks=0;setInterval(()=>document.getElementById('js').textContent='JavaScript running · '+new Date().toLocaleTimeString(),500)</script></html>
        """;


}
