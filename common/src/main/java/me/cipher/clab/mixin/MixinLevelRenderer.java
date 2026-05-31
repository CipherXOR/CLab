package me.cipher.clab.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import me.cipher.clab.ClientClass;
import me.cipher.clab.culling.entity.EntityCullingState;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class MixinLevelRenderer {

    @Shadow
    private ClientLevel level;

    @Shadow
    private Frustum cullingFrustum;

    @Unique
    private ClientLevel clab$lastLevel;

    @Inject(
        method = "renderLevel(Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V",
        at = @At("HEAD")
    )
    private void onRenderLevelStart(
        DeltaTracker deltaTracker,
        boolean renderBlockOutline,
        Camera camera,
        GameRenderer gameRenderer,
        LightTexture lightTexture,
        Matrix4f frustumMatrix,
        Matrix4f projectionMatrix,
        CallbackInfo ci
    ) {
        EntityCullingState.clear();
        if (this.level != this.clab$lastLevel) {
            this.clab$lastLevel = this.level;
            ClientClass.HARDWARE_OCCLUSION_CULLER.cleanup();
            ClientClass.HARDWARE_OCCLUSION_BE_CULLER.cleanup();
        }
        ClientClass.HARDWARE_OCCLUSION_CULLER.ensurePool();
        ClientClass.HARDWARE_OCCLUSION_BE_CULLER.ensurePool();
    }

    @Inject(
        method = "renderLevel(Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;renderSectionLayer(Lnet/minecraft/client/renderer/RenderType;DDDLorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V",
            ordinal = 2,
            shift = At.Shift.AFTER
        )
    )
    private void afterTerrainBeforeEntities(
        DeltaTracker deltaTracker,
        boolean renderBlockOutline,
        Camera camera,
        GameRenderer gameRenderer,
        LightTexture lightTexture,
        Matrix4f frustumMatrix,
        Matrix4f projectionMatrix,
        CallbackInfo ci
    ) {
        ClientClass.HARDWARE_OCCLUSION_CULLER.processPendingQueries();
        ClientClass.HARDWARE_OCCLUSION_BE_CULLER.processPendingQueries();
    }

    @Inject(
        method = "renderLevel(Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V",
        slice = @Slice(
            from = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;endLastBatch()V")
        ),
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;endBatch(Lnet/minecraft/client/renderer/RenderType;)V",
            ordinal = 3,
            shift = At.Shift.AFTER
        )
    )
    private void afterEntityRendering(
        DeltaTracker deltaTracker,
        boolean renderBlockOutline,
        Camera camera,
        GameRenderer gameRenderer,
        LightTexture lightTexture,
        Matrix4f frustumMatrix,
        Matrix4f projectionMatrix,
        CallbackInfo ci
    ) {
        if (this.level == null) {
            return;
        }
        var modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.set(new Matrix4f().rotation(camera.rotation()));
        RenderSystem.applyModelViewMatrix();
        try {
            ClientClass.HARDWARE_OCCLUSION_CULLER.beginQueryBatch();
            try {
                for (Entity entity : this.level.entitiesForRendering()) {
                    if (this.cullingFrustum != null && !this.cullingFrustum.isVisible(entity.getBoundingBox())) {
                        continue;
                    }
                    ClientClass.HARDWARE_OCCLUSION_CULLER.submitQuery(entity);
                }
            } finally {
                ClientClass.HARDWARE_OCCLUSION_CULLER.endQueryBatch();
            }
        } finally {
            modelViewStack.popMatrix();
            RenderSystem.applyModelViewMatrix();
        }
    }

    @Inject(
        method = "renderLevel(Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;endBatch(Lnet/minecraft/client/renderer/RenderType;)V",
            ordinal = 0,
            shift = At.Shift.AFTER
        )
    )
    private void afterBlockEntityRendering(
        DeltaTracker deltaTracker,
        boolean renderBlockOutline,
        Camera camera,
        GameRenderer gameRenderer,
        LightTexture lightTexture,
        Matrix4f frustumMatrix,
        Matrix4f projectionMatrix,
        CallbackInfo ci
    ) {
        var modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.set(new Matrix4f().rotation(camera.rotation()));
        RenderSystem.applyModelViewMatrix();
        try {
            ClientClass.HARDWARE_OCCLUSION_BE_CULLER.beginQueryBatch();
            try {
                ClientClass.HARDWARE_OCCLUSION_BE_CULLER.submitAllPendingQueries(this.cullingFrustum);
            } finally {
                ClientClass.HARDWARE_OCCLUSION_BE_CULLER.endQueryBatch();
            }
        } finally {
            modelViewStack.popMatrix();
            RenderSystem.applyModelViewMatrix();
        }
    }

    @Inject(
            method = "close()V",
            at = @At("RETURN")
    )
    private void onClose(CallbackInfo ci) {
        ClientClass.HARDWARE_OCCLUSION_CULLER.cleanup();
        ClientClass.HARDWARE_OCCLUSION_BE_CULLER.cleanup();
    }
}
