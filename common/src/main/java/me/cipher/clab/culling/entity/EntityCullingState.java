package me.cipher.clab.culling.entity;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

public final class EntityCullingState {
    private static final IntSet OCCLUDED = new IntOpenHashSet();

    public static void markOccluded(int entityId) {
        OCCLUDED.add(entityId);
    }

    public static boolean isOccluded(int entityId) {
        return OCCLUDED.contains(entityId);
    }

    public static void clear() {
        OCCLUDED.clear();
    }

    private EntityCullingState() {}
}
