package dev.bill.minecraft;

import java.util.*;
import java.util.stream.Collectors;
import net.minecraft.world.level.block.state.BlockState;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class OfficeBlocks {
    private static final Map<String, OfficeFurnitureBlock> FURNITURE = new LinkedHashMap<>();
    public static final List<String> STYLES = List.of("classic", "birch", "dark_oak", "industrial");
    public static final OfficeFurnitureBlock DESK = register("agent_desk", OfficeFurnitureBlock.Kind.DESK);
    public static final OfficeFurnitureBlock CHAIR = register("office_chair", OfficeFurnitureBlock.Kind.CHAIR);
    public static final OfficeFurnitureBlock BOARD = register("project_board", OfficeFurnitureBlock.Kind.BOARD);
    public static final OfficeFurnitureBlock MONITOR = register("monitor", OfficeFurnitureBlock.Kind.MONITOR);
    public static final OfficeFurnitureBlock CABINET = register("filing_cabinet", OfficeFurnitureBlock.Kind.CABINET);
    private static final List<OfficeFurnitureBlock> VARIANTS = registerVariants();
    public static final BlockEntityType<ProjectBoardEntity> PROJECT_BOARD = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
        Identifier.fromNamespaceAndPath(ModdingWorkspace.MOD_ID, "project_board"), new BlockEntityType<>(ProjectBoardEntity::new, FURNITURE.values().stream().filter(b -> b.kind == OfficeFurnitureBlock.Kind.BOARD).collect(Collectors.toSet())));

    private static List<OfficeFurnitureBlock> registerVariants() {
        var blocks = new ArrayList<OfficeFurnitureBlock>();
        for (String style : STYLES.subList(1, STYLES.size())) for (var kind : OfficeFurnitureBlock.Kind.values())
            blocks.add(register(style+"_"+kind.id, kind, style));
        return List.copyOf(blocks);
    }
    public static OfficeFurnitureBlock variant(String style, OfficeFurnitureBlock.Kind kind) {
        return Objects.requireNonNull(FURNITURE.get((style.equals("classic") ? "" : style+"_")+kind.id));
    }
    public static boolean is(BlockState state, OfficeFurnitureBlock.Kind kind) {
        return state.getBlock() instanceof OfficeFurnitureBlock furniture && furniture.kind == kind;
    }
    public static List<OfficeFurnitureBlock> all() { return List.copyOf(FURNITURE.values()); }
    private static OfficeFurnitureBlock register(String name, OfficeFurnitureBlock.Kind kind) { return register(name, kind, "classic"); }
    private static OfficeFurnitureBlock register(String name, OfficeFurnitureBlock.Kind kind, String style) {
        var id = Identifier.fromNamespaceAndPath(ModdingWorkspace.MOD_ID, name);
        var key = ResourceKey.create(Registries.BLOCK, id);
        var block = Registry.register(BuiltInRegistries.BLOCK, key, new OfficeFurnitureBlock(kind, style,
            BlockBehaviour.Properties.of().setId(key).strength(2.0F).sound(SoundType.WOOD).noOcclusion()));
        var itemKey = ResourceKey.create(Registries.ITEM, id);
        Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
        FURNITURE.put(name, block);
        return block;
    }
    public static void initialize() {
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> {
            for (var block : all()) output.accept(block);
        });
    }
}
