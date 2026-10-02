package dev.bill.minecraft.client;

import dev.bill.minecraft.ModdingWorkspace;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import com.mojang.blaze3d.platform.InputConstants;

public final class ModdingWorkspaceClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ModdingWorkspace.LOGGER.info("Minecraft modding client ready.");
        OfficeClient.register();
        AgentRenderer.register();
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(dev.bill.minecraft.OfficeEntities.BROWSER, BrowserDisplayRenderer::new);
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("mcef-modern")) BrowserEngine.register();
        if (Boolean.getBoolean("modding.office.verify")) OfficeVerification.register();
        if (Boolean.getBoolean("modding.browser.verify")) BrowserVerification.register();
        boolean[] held = {false};
        boolean[] autoOpened = {false};
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            boolean down = InputConstants.isKeyDown(InputConstants.KEY_F8);
            if (down && !held[0] && !(client.gui.screen() instanceof BrowserPreviewScreen)) {
                if (client.gui.screen() instanceof TerminalScreen terminal) terminal.onClose();
                else client.gui.setScreen(new TerminalScreen(client.gui.screen()));
            }
            held[0] = down;
            if (!autoOpened[0] && Boolean.getBoolean("modding.terminal.autoOpen") && client.gui.screen() instanceof net.minecraft.client.gui.screens.TitleScreen) {
                autoOpened[0] = true;
                client.gui.setScreen(new TerminalScreen(client.gui.screen()));
            }
        });
    }
}
