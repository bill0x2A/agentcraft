package dev.bill.minecraft;

import java.util.List;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Plain items. The client decides what a right-click does, because every office action is client-owned. */
public final class OfficeItems {
    /** Opens the in-game documentation of the items below. */
    public static final Item HANDBOOK = register("handbook");
    /** Hold it and right-click an agent mob to change the name on its nameplate. */
    public static final Item NAME_BADGE = register("name_badge");
    /** Removes an agent mob with its binding, or a terminal display. The session continues. */
    public static final Item DISMISSAL_SLIP = register("dismissal_slip");
    /** Right-click an agent mob, then a block, to move its home. */
    public static final Item HOME_FLAG = register("home_flag");
    /** Sends a short text to the terminal of an agent mob. */
    public static final Item MEMO = register("memo");
    /** Shows each agent mob and its status while held; a right-click opens the office screen. */
    public static final Item STATUS_TABLET = register("status_tablet");
    /** Use on a block to place a live view of a terminal; the display then asks which terminal to show. */
    public static final Item TERMINAL_DISPLAY = register("terminal_display");
    /** Use on a block to create a shell session and link it there as a crab or a robot. */
    public static final Item CLAUDE_EGG = register("claude_egg"), CODEX_EGG = register("codex_egg");
    public static final List<Item> ALL = List.of(HANDBOOK, CLAUDE_EGG, CODEX_EGG, NAME_BADGE, HOME_FLAG, MEMO,
        STATUS_TABLET, TERMINAL_DISPLAY, DISMISSAL_SLIP);

    private static Item register(String name) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(ModdingWorkspace.MOD_ID,name));
        return Registry.register(BuiltInRegistries.ITEM,key,new Item(new Item.Properties().stacksTo(1).setId(key)));
    }
    public static void initialize() {
        ResourceKey<CreativeModeTab> key = ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.fromNamespaceAndPath(ModdingWorkspace.MOD_ID,"office"));
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB,key,FabricCreativeModeTab.builder()
            .title(Component.translatable("itemGroup.modding_workspace.office")).icon(() -> new ItemStack(HANDBOOK))
            .displayItems((parameters,output) -> ALL.forEach(item -> output.accept(item))).build());
    }
}
