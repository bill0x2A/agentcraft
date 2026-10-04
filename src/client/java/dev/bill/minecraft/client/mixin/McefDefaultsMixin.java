package dev.bill.minecraft.client.mixin;

import java.util.Arrays;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Keep Chromium's normal origin checks; the pinned upstream disables them by default. */
@Pseudo
@Mixin(targets="net.dimaskama.mcef.impl.MCEFApiImpl", remap=false)
abstract class McefDefaultsMixin {
    @ModifyArg(method="<init>", at=@At(value="INVOKE", target="Lme/friwi/jcefmaven/CefAppBuilder;setInstallDir(Ljava/io/File;)V"), index=0, require=1)
    private java.io.File office$runtimeDirectory(java.io.File original) {
        String override=System.getProperty("modding.browser.runtimeDir");
        java.io.File directory=override==null?original:new java.io.File(override);
        dev.bill.minecraft.ModdingWorkspace.LOGGER.info("Browser engine directory: {}",directory.getAbsolutePath());
        return directory;
    }

    @ModifyArg(method="<init>", at=@At(value="INVOKE", target="Lme/friwi/jcefmaven/CefAppBuilder;addJcefArgs([Ljava/lang/String;)V"), index=0, require=1)
    private String[] office$browserArguments(String[] arguments) {
        var safe = new java.util.ArrayList<>(Arrays.stream(arguments).filter(value -> !value.equals("--disable-web-security")).toList());
        int port = Integer.getInteger("modding.browser.debugPort",0);
        if(port>1023 && port<65536) {
            safe.add("--remote-debugging-address=127.0.0.1");
            safe.add("--remote-debugging-port="+port);
        }
        return safe.toArray(String[]::new);
    }
}
