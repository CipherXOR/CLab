package me.cipher.clab.culling.entity;

import me.cipher.clab.Constants;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class EntityCullingManager {

    private static final List<IEntityCuller> CULLERS = new ArrayList<>();
    private static volatile List<IEntityCuller> immutableView = Collections.emptyList();

    private EntityCullingManager() {
    }

    public static synchronized void register(IEntityCuller culler) {
        if (culler == null) {
            Constants.LOG.warn("Attempted to register null entity culler, ignoring.");
            return;
        }

        String id = culler.getId();
        CULLERS.removeIf(existing -> existing.getId().equals(id));
        CULLERS.add(culler);
        immutableView = List.copyOf(CULLERS);

        Constants.LOG.info("Registered entity culler: {}", id);
    }

    public static boolean shouldCull(Entity entity, Camera camera) {
        List<IEntityCuller> view = immutableView;
        for (IEntityCuller culler : view) {
            if (culler.isEnabled() && culler.shouldCull(entity, camera)) {
                return true;
            }
        }
        return false;
    }
}
