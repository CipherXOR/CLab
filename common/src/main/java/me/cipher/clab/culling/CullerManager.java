package me.cipher.clab.culling;

import me.cipher.clab.Constants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CullerManager {

    private static final List<ICuller> CULLERS = new ArrayList<>();
    private static volatile List<ICuller> immutableView = Collections.emptyList();

    private CullerManager() {
    }

    public static synchronized void register(ICuller culler) {
        if (culler == null) {
            Constants.LOG.warn("Attempted to register null culler, ignoring.");
            return;
        }

        String id = culler.getId();
        CULLERS.removeIf(existing -> existing.getId().equals(id));
        CULLERS.add(culler);
        immutableView = List.copyOf(CULLERS);

        Constants.LOG.info("Registered culler: {}", id);
    }

    public static boolean shouldCull(BlockGetter level,
                                     BlockState state,
                                     BlockPos pos,
                                     Direction direction,
                                     BlockPos neighbor) {
        List<ICuller> view = immutableView;
        for (ICuller culler : view) {
            if (culler.isEnabled() && culler.shouldCull(level, state, pos, direction, neighbor)) {
                return true;
            }
        }
        return false;
    }
}
