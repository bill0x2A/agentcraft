package dev.bill.minecraft;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ModdingWorkspace implements ModInitializer {
    public static final String MOD_ID = "modding_workspace";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        OfficeEntities.initialize();
        OfficeItems.initialize();
        OfficeBlocks.initialize();
        Studio.register();
        LOGGER.info("Minecraft modding workspace ready.");
    }
}
