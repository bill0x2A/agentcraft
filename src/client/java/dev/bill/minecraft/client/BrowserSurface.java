package dev.bill.minecraft.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.*;
import dev.bill.minecraft.ModdingWorkspace;
import java.util.UUID;
import net.dimaskama.mcef.api.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;

/** One browser shared by the in-world monitor and its focused control view. Render-thread owned. */
final class BrowserSurface implements AutoCloseable {
    static final int WIDTH=1280, HEIGHT=720;
    final Identifier textureId;
    final MCEFApi.Initialization initialization=BrowserEngine.initialize();
    MCEFBrowser browser;
    String requestedUrl, error;
    private boolean registered, closed;

    BrowserSurface(UUID id,String url) {
        textureId=Identifier.fromNamespaceAndPath(ModdingWorkspace.MOD_ID,"browser/"+id);
        requestedUrl=url;
    }
    void tick() {
        if(closed || error!=null)return;
        if(browser==null && initialization.getFuture().isDone()) {
            try {
                browser=initialization.getFuture().join().createBrowser(BrowserEngine.resolveUrl(requestedUrl),false);
                browser.resize(WIDTH,HEIGHT); browser.setFocus(false);
            } catch(Exception e) { error="Browser failed to start; see game log"; ModdingWorkspace.LOGGER.error(error,e); }
        }
        if(browser!=null && !registered && browser.getTextureView()!=null) {
            Minecraft.getInstance().getTextureManager().register(textureId,new AbstractTexture() {
                { sampler=RenderSystem.getSamplerCache().getSampler(AddressMode.CLAMP_TO_EDGE,AddressMode.CLAMP_TO_EDGE,FilterMode.LINEAR,FilterMode.LINEAR,false); }
                @Override public GpuTexture getTexture(){return browser.getTexture();}
                @Override public GpuTextureView getTextureView(){return browser.getTextureView();}
                @Override public void close(){ /* MCEF owns the GPU allocation. */ }
            }); registered=true;
        }
    }
    boolean ready(){return registered && !closed;}
    String status(){return error!=null?error:BrowserEngine.loadingStatus();}
    void navigate(String url) {
        requestedUrl=url;
        if(browser!=null)browser.getCefBrowser().loadURL(BrowserEngine.resolveUrl(url));
    }
    @Override public void close() {
        if(closed)return;
        closed=true;
        if(registered)Minecraft.getInstance().getTextureManager().release(textureId);
        if(browser!=null)browser.close();
    }
}
