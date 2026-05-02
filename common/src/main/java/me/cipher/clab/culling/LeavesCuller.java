package me.cipher.clab.culling;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

public class LeavesCuller implements ICuller {

    public static final String ID = "leaves";

    private boolean enabled = true;

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean shouldCull(BlockGetter level, BlockState state, BlockPos pos, Direction direction, BlockPos neighbor) {
        if (!(state.getBlock() instanceof LeavesBlock)) {
            return false;
        }

        BlockState neighborState = level.getBlockState(neighbor);
        return neighborState.getBlock() instanceof LeavesBlock;
    }
}
