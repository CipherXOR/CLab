package me.cipher.clab.mixin;

import me.cipher.clab.ClientClass;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class MixinLevelRenderer {

    @Shadow
    private ClientLevel level;

    @Inject(
            method = "renderLevel",
            at = @At("HEAD")
    )
    private void onRenderLevelStart(
            PoseStack poseStack,
            float partialTick,
            long finishNanoTime,
            boolean renderBlockOutline,
            Camera camera,
            GameRenderer gameRenderer,
            LightTexture lightTexture,
            Matrix4f projectionMatrix,
            CallbackInfo ci
    ) {
        ClientClass.HARDWARE_OCCLUSION_CULLER.ensurePool();
    }

    @Inject(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;renderChunkLayer(Lnet/minecraft/client/renderer/RenderType;Lcom/mojang/blaze3d/vertex/PoseStack;DDDLorg/joml/Matrix4f;)V",
                    ordinal = 2,
                    shift = At.Shift.AFTER
            )
    )
    private void afterTerrainBeforeEntities(
            PoseStack poseStack,
            float partialTick,
            long finishNanoTime,
            boolean renderBlockOutline,
            Camera camera,
            GameRenderer gameRenderer,
            LightTexture lightTexture,
            Matrix4f projectionMatrix,
            CallbackInfo ci
    ) {
        ClientClass.HARDWARE_OCCLUSION_CULLER.processPendingQueries();

        if (this.level == null) {
            return;
        }

        Matrix4f modelView = poseStack.last().pose();
        ClientClass.HARDWARE_OCCLUSION_CULLER.beginQueryBatch(modelView, projectionMatrix);
        try {
            for (Entity entity : this.level.entitiesForRendering()) {
                ClientClass.HARDWARE_OCCLUSION_CULLER.submitQuery(entity);
            }
        } finally {
            ClientClass.HARDWARE_OCCLUSION_CULLER.endQueryBatch();
        }
    }

    @Inject(
            method = "close",
            at = @At("RETURN")
    )
    private void onClose(CallbackInfo ci) {
        ClientClass.HARDWARE_OCCLUSION_CULLER.cleanup();
    }
}