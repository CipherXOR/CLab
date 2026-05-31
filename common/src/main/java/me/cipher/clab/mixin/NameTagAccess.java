package me.cipher.clab.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

public interface NameTagAccess {
    void clab$renderNameTag(Entity entity, Component name, PoseStack poseStack, MultiBufferSource buffer, int packedLight);
}
