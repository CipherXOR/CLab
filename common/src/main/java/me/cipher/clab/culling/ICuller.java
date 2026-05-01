package me.cipher.clab.culling;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

public interface ICuller {

    String getId();

    boolean isEnabled();

    boolean shouldCull(BlockGetter level, BlockState state, BlockPos pos, Direction direction, BlockPos neighbor);
}
