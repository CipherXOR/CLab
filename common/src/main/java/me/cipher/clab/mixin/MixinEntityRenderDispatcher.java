package me.cipher.clab.mixin;

import me.cipher.clab.culling.entity.EntityCullingManager;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public class MixinEntityRenderDispatcher {

    @Inject(
        method = "shouldRender(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/culling/Frustum;DDD)Z",
        at = @At("RETURN"),
        cancellable = true
    )
    private <E extends Entity> void onShouldRender(
        E entity,
        Frustum frustum,
        double camX,
        double camY,
        double camZ,
        CallbackInfoReturnable<Boolean> cir
    ) {
        if (!cir.getReturnValueZ()) {
            return;
        }

        Camera camera = ((EntityRenderDispatcher) (Object) this).camera;
        if (camera == null) {
            return;
        }

        if (EntityCullingManager.shouldCull(entity, camera)) {
            cir.setReturnValue(false);
        }
    }
}
