package me.cipher.clab.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import me.cipher.clab.ClientClass;
import me.cipher.clab.culling.blockentity.BlockEntityCullingManager;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntityRenderDispatcher.class)
public class MixinBlockEntityRenderDispatcher {

    @Shadow
    public Camera camera;

    @Inject(
            method = "render(Lnet/minecraft/world/level/block/entity/BlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private <E extends BlockEntity> void onRender(
            E blockEntity,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            CallbackInfo ci
    ) {
        if (BlockEntityCullingManager.shouldCull(blockEntity, this.camera)) {
            ci.cancel();
        } else {
            ClientClass.HARDWARE_OCCLUSION_BE_CULLER.onBlockEntityRendered(blockEntity);
        }
    }
}
