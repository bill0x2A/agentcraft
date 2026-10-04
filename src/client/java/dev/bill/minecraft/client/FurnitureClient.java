package dev.bill.minecraft.client;

import dev.bill.minecraft.*;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;

final class FurnitureClient {
    interface Action { void run(ServerPlayer player) throws Exception; }
    private static String restoreTerminal;
    private static UUID monitorToOpen;
    private static net.minecraft.client.multiplayer.ClientLevel world;

    static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (world != client.level) { world = client.level; restoreTerminal = null; monitorToOpen = null; }
            if (monitorToOpen != null && client.level != null && client.level.getEntity(monitorToOpen) instanceof BrowserDisplay monitor) {
                monitorToOpen = null;
                client.gui.setScreen(new BrowserPreviewScreen(client.gui.screen(), monitor));
            }
        });
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            BlockPos pos = hit.getBlockPos();
            if (!(level.getBlockState(pos).getBlock() instanceof OfficeFurnitureBlock furniture)) return InteractionResult.PASS;
            if (player.isShiftKeyDown() && player.getItemInHand(hand).getItem() instanceof BlockItem) return InteractionResult.PASS;
            var client = Minecraft.getInstance();
            if (!OfficeClient.localWorld(client)) return InteractionResult.PASS;
            if (!level.isClientSide()) return InteractionResult.SUCCESS;
            switch (furniture.kind) {
                case DESK -> {
                    var binding = OfficeWorld.atDesk(level.dimension().identifier().toString(), pos);
                    if (restoreTerminal != null) {
                        String terminal = restoreTerminal;
                        mutate(null, pos, furniture.kind, owner -> OfficeWorld.assignDesk(owner.level().getServer(), owner, pos, terminal), () -> {
                            restoreTerminal = null; OfficeClient.message(client, "Agent restored to this desk.");
                        });
                    } else if (binding != null && !player.isShiftKeyDown()) OfficeClient.openMob(client, binding.mob());
                    else client.gui.setScreen(new FurnitureScreen(client.gui.screen(), pos, furniture.kind));
                }
                case BOARD -> {
                    var board=(ProjectBoardEntity)level.getBlockEntity(pos);
                    client.gui.setScreen(player.isShiftKeyDown() ? new FurnitureScreen(client.gui.screen(),pos,furniture.kind) : new WorkspaceScreen(client.gui.screen(),pos,board==null?"":board.group()));
                }
                case CHAIR -> {
                    var occupant = OfficeWorld.bindings().stream().filter(b -> !b.archived()
                        && b.dimension().equals(level.dimension().identifier().toString()) && BlockPos.containing(b.x(), b.y(), b.z()).equals(pos)).findFirst();
                    if (occupant.isPresent()) OfficeClient.openMob(client, occupant.get().mob());
                    else OfficeClient.message(client, "Place the chair at the work spot: one block directly in front of an agent desk, facing the desk.");
                }
                case MONITOR -> {
                    if (!BrowserEngine.available()) OfficeClient.message(client, "Install MCEF Modern to use the monitor.");
                    else mutate(null, pos, furniture.kind, owner -> {
                        UUID id = BrowserEngine.placeMonitor(owner, pos).getUUID();
                        client.execute(() -> monitorToOpen = id);
                    }, () -> {});
                }
                default -> client.gui.setScreen(new FurnitureScreen(client.gui.screen(), pos, furniture.kind));
            }
            return InteractionResult.SUCCESS;
        });
    }

    static void chooseRestore(String terminal) {
        restoreTerminal = terminal;
        var client = Minecraft.getInstance(); client.gui.setScreen(null);
        OfficeClient.message(client, "Right-click an empty agent desk to restore this session. Sneak-click the cabinet to choose another.");
    }

    static void mutate(FurnitureScreen screen, BlockPos pos, OfficeFurnitureBlock.Kind kind, Action action, Runnable success) {
        var client = Minecraft.getInstance(); var server = client.getSingleplayerServer();
        if (!OfficeClient.localWorld(client)) return;
        UUID player = client.player.getUUID(); var dimension = client.player.level().dimension();
        server.execute(() -> {
            try {
                var owner = server.getPlayerList().getPlayer(player);
                if (owner == null || server.isPublished() || !owner.level().dimension().equals(dimension)
                    || owner.distanceToSqr(pos.getX()+.5, pos.getY()+.5, pos.getZ()+.5) > 64
                    || !(owner.level().getBlockState(pos).getBlock() instanceof OfficeFurnitureBlock block) || block.kind != kind)
                    throw new IllegalStateException("Return to the furniture to make this change");
                action.run(owner);
                client.execute(() -> {
                    if (client.getSingleplayerServer() != server) return;
                    if (screen != null) screen.finished("Saved");
                    success.run();
                });
            } catch (Exception e) {
                client.execute(() -> { if (screen != null) screen.finished(e.getMessage()); else OfficeClient.message(client, e.getMessage()); });
            }
        });
    }
}
