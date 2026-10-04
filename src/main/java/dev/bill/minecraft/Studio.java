package dev.bill.minecraft;

import dev.agentcraft.hq.StudioHqBuilder;
import dev.agentcraft.layout.Anchors;
import java.nio.file.Files;
import java.util.Set;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

/** The starter studio lives in its own dimension, away from the player's existing builds. */
public final class Studio {
    public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath(ModdingWorkspace.MOD_ID,"studio"));
    public static void register() {
        dev.agentcraft.block.ModBlocks.init();
        dev.agentcraft.block.entity.ModBlockEntities.init();
        dev.agentcraft.block.ModItems.init();
        Anchors.init();
        CommandRegistrationCallback.EVENT.register((dispatcher,access,environment) -> dispatcher.register(Commands.literal("office").requires(source -> {
            // Command-tree sync probes permissions with a serverless source during login.
            var server = source.getServer();
            return server != null && server.isSingleplayer() && !server.isPublished();
        })
            .then(Commands.literal("studio").executes(ctx -> { try { enter(ctx.getSource().getPlayerOrException()); return 1; } catch(Exception e) { ctx.getSource().sendFailure(Component.literal(e.getMessage())); return 0; } }))
            .then(Commands.literal("overworld").executes(ctx -> {
                var player=ctx.getSource().getPlayerOrException(); var level=player.level().getServer().overworld();
                var spawn=level.getRespawnData().pos();
                player.teleportTo(level,spawn.getX()+.5,spawn.getY()+1,spawn.getZ()+.5,Set.of(),0,0,true); return 1;
            }))));
    }
    public static void enter(ServerPlayer player) throws Exception {
        var server=player.level().getServer();
        if(server.isDedicatedServer() || server.isPublished()) throw new IllegalStateException("Open a private single-player world first");
        var level=server.getLevel(DIMENSION);
        if(level==null) throw new IllegalStateException("Restart the world to enable the studio dimension");
        var marker=server.getWorldPath(LevelResource.ROOT).resolve("data/office-studio-ready");
        if(!Files.exists(marker)) {
            var anchors=Anchors.builder("studio");
            new StudioHqBuilder().build(level,anchors);
            Anchors.publish(server,anchors.build());
            // Real interactive furniture alongside the imported studio stations.
            level.setBlock(new BlockPos(7,66,3),OfficeBlocks.BOARD.defaultBlockState(),3);
            for(int x:new int[]{-15,-11,-7,7,11,15}) {
                level.setBlock(new BlockPos(x,66,-7),OfficeBlocks.DESK.defaultBlockState().setValue(OfficeFurnitureBlock.FACING,Direction.SOUTH),3);
                level.setBlock(new BlockPos(x,66,-5),OfficeBlocks.CHAIR.defaultBlockState().setValue(OfficeFurnitureBlock.FACING,Direction.NORTH),3);
            }
            Files.createDirectories(marker.getParent()); Files.writeString(marker,"AgentCraft studio imported; player edits are preserved.\n");
        }
        player.teleportTo(level,.5,66,22.5,Set.of(),180,0,true);
        player.sendSystemMessage(Component.literal("Welcome to the studio. F6 opens working groups; F7 opens sessions and displays."));
    }
}
