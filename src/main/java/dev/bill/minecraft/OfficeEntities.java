package dev.bill.minecraft;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;

public final class OfficeEntities {
    public static final EntityType<AgentMob> CODEX = register("codex", .8F, 1.9F);
    public static final EntityType<AgentMob> CLAUDE = register("claude", 1.4F, 1.4F);
    private static EntityType<AgentMob> register(String name, float width, float height) {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(ModdingWorkspace.MOD_ID,name));
        return Registry.register(BuiltInRegistries.ENTITY_TYPE,key,
            EntityType.Builder.of(AgentMob::new, MobCategory.MISC).sized(width,height).clientTrackingRange(10).build(key));
    }
    public static final EntityType<BrowserDisplay> BROWSER = registerBrowser();
    private static EntityType<BrowserDisplay> registerBrowser() {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(ModdingWorkspace.MOD_ID,"browser"));
        return Registry.register(BuiltInRegistries.ENTITY_TYPE,key,
            EntityType.Builder.of(BrowserDisplay::new,MobCategory.MISC).sized(4.2F,2.5F).clientTrackingRange(12).build(key));
    }
    public static EntityType<AgentMob> type(String character) { return "claude".equals(character) ? CLAUDE : CODEX; }
    public static void initialize() {
        FabricDefaultAttributeRegistry.register(BROWSER, Mob.createMobAttributes());
        FabricDefaultAttributeRegistry.register(CODEX, Mob.createMobAttributes());
        FabricDefaultAttributeRegistry.register(CLAUDE, Mob.createMobAttributes());
    }
}
