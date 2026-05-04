package me.cipher.clab.culling.blockentity;

import me.cipher.clab.Constants;
import net.minecraft.client.Camera;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class BlockEntityCullingManager {

    private static final List<IBlockEntityCuller> CULLERS = new ArrayList<>();
    private static volatile List<IBlockEntityCuller> immutableView = Collections.emptyList();

    private BlockEntityCullingManager() {
    }

    public static synchronized void register(IBlockEntityCuller culler) {
        if (culler == null) {
            Constants.LOG.warn("Attempted to register null block entity culler, ignoring.");
            return;
        }

        String id = culler.getId();
        CULLERS.removeIf(existing -> existing.getId().equals(id));
        CULLERS.add(culler);
        immutableView = List.copyOf(CULLERS);

        Constants.LOG.info("Registered block entity culler: {}", id);
    }

    public static boolean shouldCull(BlockEntity blockEntity, Camera camera) {
        List<IBlockEntityCuller> view = immutableView;
        for (IBlockEntityCuller culler : view) {
            if (culler.isEnabled() && culler.shouldCull(blockEntity, camera)) {
                return true;
            }
        }
        return false;
    }
}
