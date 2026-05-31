package me.cipher.clab.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(EntityRenderer.class)
public class MixinEntityRenderer<T extends Entity> implements NameTagAccess {

    @Invoker("renderNameTag")
    @Override
    public void clab$renderNameTag(Entity entity, Component name, PoseStack poseStack, MultiBufferSource buffer, int packedLight) { throw new AssertionError(); }
}
