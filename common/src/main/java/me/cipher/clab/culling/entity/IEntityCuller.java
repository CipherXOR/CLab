package me.cipher.clab.culling.entity;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;

public interface IEntityCuller {

    String getId();

    boolean isEnabled();

    boolean shouldCull(Entity entity, Camera camera);
}
