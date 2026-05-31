package me.cipher.clab.mixin;

import me.cipher.clab.culling.entity.EntityCullingState;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public class MixinLivingEntityRenderer<T extends LivingEntity, M extends EntityModel<T>> {

    @Invoker("shouldShowName")
    private boolean clab$shouldShowName(T entity) { throw new AssertionError(); }

    @Inject(
        method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onRender(
        T entity,
        float entityYaw,
        float partialTick,
        PoseStack poseStack,
        MultiBufferSource buffer,
        int packedLight,
        CallbackInfo ci
    ) {
        if (!EntityCullingState.isOccluded(entity.getId())) {
            return;
        }

        if (clab$shouldShowName(entity)) {
            ((NameTagAccess) this).clab$renderNameTag(entity, entity.getDisplayName(), poseStack, buffer, packedLight, partialTick);
        }

        ci.cancel();
    }
}
