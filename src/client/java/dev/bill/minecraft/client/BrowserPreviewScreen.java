package dev.bill.minecraft.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.*;
import dev.bill.minecraft.BrowserDisplay;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.*;
import net.minecraft.network.chat.Component;

/** Focused input for the exact same browser texture displayed in the world. */
final class BrowserPreviewScreen extends Screen {
    private final Screen previous;
    final BrowserSurface surface;
    private final BrowserDisplay display;
    private EditBox address;
    private boolean browserFocused;
    private boolean groupBrowser;
    private String notice="";
    private int left,top,viewWidth,viewHeight;
    BrowserPreviewScreen(Screen previous){this(previous,null);}
    BrowserPreviewScreen(Screen previous,BrowserDisplay display) {
        super(Component.literal("Office browser"));this.previous=previous;this.display=display;
        surface=display==null?new BrowserSurface(UUID.randomUUID(),"office:home"):BrowserEngine.surface(display);
    }
    @Override protected void init() {
        minecraft.textInputManager().startTextInput(this);
        String draft=address==null?surface.requestedUrl:address.getValue();
        address=new EditBox(font,90,10,Math.max(80,width-286),20,Component.literal("Web address"));
        address.setMaxLength(8192);address.setValue(draft);
        groupBrowser=surface.requestedUrl.matches("http://127\\.0\\.0\\.1:[0-9]+/[a-f0-9]{64}/view/group_[a-f0-9]+$");
        if(!groupBrowser){addRenderableWidget(address);
        addRenderableWidget(Button.builder(Component.literal("Back"),b->{if(surface.browser!=null)surface.browser.getCefBrowser().goBack();}).bounds(10,10,38,20).build());
        addRenderableWidget(Button.builder(Component.literal("Reload"),b->{if(surface.browser!=null)surface.browser.getCefBrowser().reload();}).bounds(50,10,38,20).build());
        addRenderableWidget(Button.builder(Component.literal("Go"),b->navigate()).bounds(width-192,10,32,20).build());
        addRenderableWidget(Button.builder(Component.literal("Home"),b->{address.setValue("office:home");navigate();}).bounds(width-156,10,40,20).build());
        }
        if(display!=null)addRenderableWidget(Button.builder(Component.literal("Remove"),b->{BrowserEngine.remove(display);onClose();}).bounds(width-112,10,52,20).build());
        addRenderableWidget(Button.builder(Component.literal("Close"),b->onClose()).bounds(width-56,10,46,20).build());
        viewWidth=Math.min(width-24,(height-64)*16/9);viewHeight=viewWidth*9/16;
        left=(width-viewWidth)/2;top=36+(height-64-viewHeight)/2;
    }
    void navigate() {
        try {
            String url=BrowserEngine.normalizeUrl(address.getValue());
            if(display!=null)BrowserEngine.saveUrl(display,url);
            surface.navigate(url);notice="";focusBrowser(true);
        }catch(Exception e){notice="Use an http:// or https:// address, or office:home";}
    }
    private void focusBrowser(boolean focused){browserFocused=focused;if(focused){setFocused(null);address.setFocused(false);}if(surface.browser!=null)surface.browser.setFocus(focused);}
    private boolean inside(double x,double y){return x>=left && x<left+viewWidth && y>=top && y<top+viewHeight;}
    double guiX(double x){return left+x*viewWidth/BrowserSurface.WIDTH;}
    double guiY(double y){return top+y*viewHeight/BrowserSurface.HEIGHT;}
    private int x(double x){return (int)((x-left)*BrowserSurface.WIDTH/viewWidth);}
    private int y(double y){return (int)((y-top)*BrowserSurface.HEIGHT/viewHeight);}
    private MouseButtonEvent mapped(MouseButtonEvent e){return new MouseButtonEvent(x(e.x()),y(e.y()),e.buttonInfo());}
    @Override public void tick(){surface.tick();if(display!=null && display.isRemoved())onClose();}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta) {
        g.fill(0,0,width,height,0xff090e15);
        if(groupBrowser)g.text(font,"Group browser · stays running outside Minecraft",12,16,0xff7cdbb6,false);
        if(surface.ready())g.blit(surface.browser.getTextureView(),RenderSystem.getSamplerCache().getSampler(AddressMode.CLAMP_TO_EDGE,AddressMode.CLAMP_TO_EDGE,FilterMode.LINEAR,FilterMode.LINEAR,false),left,top,left+viewWidth,top+viewHeight,0,1,0,1);
        else {
            g.text(font,font.plainSubstrByWidth(surface.status(),width-24),12,50,0xffbdcce0,false);
            if(surface.error==null)g.text(font,font.plainSubstrByWidth(BrowserEngine.loadingHint(),width-24),12,68,0xff8cbdba,false);
        }
        super.extractRenderState(g,mx,my,delta);
        g.text(font,notice.isEmpty()?(display==null?"F9 / Escape closes this preview":"Escape returns to the world · this browser keeps running"):notice,12,height-16,0xff8cbdba,false);
    }
    @Override public boolean keyPressed(KeyEvent e) {
        if(e.key()==InputConstants.KEY_ESCAPE){onClose();return true;}
        if(e.key()==InputConstants.KEY_F9)return true;
        if(address.isFocused() && e.key()==InputConstants.KEY_RETURN){navigate();return true;}
        if(browserFocused && surface.browser!=null){surface.browser.onKeyPressed(e);return true;}
        return super.keyPressed(e);
    }
    @Override public boolean keyReleased(KeyEvent e){if(browserFocused && surface.browser!=null){surface.browser.onKeyReleased(e);return true;}return super.keyReleased(e);}
    @Override public boolean charTyped(CharacterEvent e){if(browserFocused && surface.browser!=null){surface.browser.onCharTyped(e);return true;}return super.charTyped(e);}
    @Override public boolean mouseClicked(MouseButtonEvent e,boolean twice){
        focusBrowser(inside(e.x(),e.y()));
        if(browserFocused && surface.browser!=null){surface.browser.onMouseClicked(mapped(e),twice);return true;}
        return super.mouseClicked(e,twice);
    }
    @Override public boolean mouseReleased(MouseButtonEvent e){if(browserFocused && surface.browser!=null){surface.browser.onMouseReleased(mapped(e));return true;}return super.mouseReleased(e);}
    @Override public void mouseMoved(double x,double y){if(surface.browser!=null && inside(x,y))surface.browser.onMouseMoved(x(x),y(y));super.mouseMoved(x,y);}
    @Override public boolean mouseScrolled(double x,double y,double sx,double sy){if(surface.browser!=null && inside(x,y)){focusBrowser(true);
            // MCEF 0.3.3's AWT wheel path drops events on this runtime. Send the
            // wheel through Chromium's in-process input API (no debug port required).
            surface.browser.getCefBrowser().getDevToolsClient().executeDevToolsMethod("Input.dispatchMouseEvent",
                new com.google.gson.Gson().toJson(java.util.Map.of("type","mouseWheel","x",x(x),"y",y(y),"deltaX",-sx*60,"deltaY",-sy*60)))
                .exceptionally(error->{dev.bill.minecraft.ModdingWorkspace.LOGGER.warn("Browser scroll failed",error);return null;});
            return true;}return super.mouseScrolled(x,y,sx,sy);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){minecraft.gui.setScreen(previous);}
    @Override public void removed(){minecraft.textInputManager().stopTextInput(this);if(surface.browser!=null)surface.browser.setFocus(false);if(display==null)surface.close();}
}
