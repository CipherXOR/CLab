package me.cipher.clab.culling.blockentity;

import net.minecraft.client.Camera;
import net.minecraft.world.level.block.entity.BlockEntity;

public interface IBlockEntityCuller {

    String getId();

    boolean isEnabled();

    boolean shouldCull(BlockEntity blockEntity, Camera camera);
}
