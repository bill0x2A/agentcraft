package dev.bill.minecraft;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.*;

public final class OfficeFurnitureBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public enum Kind {
        DESK("agent_desk"), CHAIR("office_chair"), BOARD("project_board"), MONITOR("monitor"), CABINET("filing_cabinet");
        public final String id;
        Kind(String id) { this.id = id; }
    }
    public final Kind kind;
    public final String style;
    public OfficeFurnitureBlock(Kind kind, String style, BlockBehaviour.Properties properties) {
        super(properties); this.kind = kind; this.style = style;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return kind == Kind.BOARD ? new ProjectBoardEntity(pos, state) : null;
    }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape shape = switch (kind) {
            case DESK -> Shapes.or(box(0, 13, 0, 16, 16, 16), box(1, 0, 2, 4, 13, 14), box(12, 0, 2, 15, 13, 14));
            case CHAIR -> Shapes.or(box(2, 0, 2, 14, 8, 14), box(2, 8, 12, 14, 22, 15));
            case BOARD -> Shapes.or(box(1, 7, 7, 15, 24, 10), box(6, 0, 7, 10, 7, 10));
            case MONITOR -> Shapes.or(box(1, 3, 7, 15, 13, 10), box(4, 0, 5, 12, 3, 12));
            case CABINET -> box(1, 0, 1, 15, 16, 15);
        };
        int turns = switch (state.getValue(FACING)) { case EAST -> 1; case SOUTH -> 2; case WEST -> 3; default -> 0; };
        for (int i = 0; i < turns; i++) {
            VoxelShape rotated = Shapes.empty();
            for (var b : shape.toAabbs()) rotated = Shapes.or(rotated, Shapes.box(1-b.maxZ, b.minY, b.minX, 1-b.minZ, b.maxY, b.maxX));
            shape = rotated;
        }
        return shape;
    }
    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        // The seat supports mobs; its decorative back must not trap wide crab models.
        return kind == Kind.CHAIR ? box(0, 0, 0, 16, 8, 16) : getShape(state, level, pos, context);
    }
}
