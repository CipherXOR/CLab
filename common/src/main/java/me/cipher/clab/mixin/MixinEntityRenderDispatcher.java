package me.cipher.clab.mixin;

import me.cipher.clab.culling.entity.EntityCullingManager;
import me.cipher.clab.culling.entity.EntityCullingState;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public class MixinEntityRenderDispatcher {

    @Inject(
        method = "shouldRender(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/culling/Frustum;DDD)Z",
        at = @At("RETURN")
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
            EntityCullingState.markOccluded(entity.getId());
        }
    }

    @Redirect(
        method = "render",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;displayFireAnimation()Z")
    )
    private boolean skipFireIfOccluded(Entity entity) {
        if (EntityCullingState.isOccluded(entity.getId())) return false;
        return entity.displayFireAnimation();
    }

    @Redirect(
        method = "render",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;isInvisible()Z")
    )
    private boolean skipShadowAndHitboxIfOccluded(Entity entity) {
        if (EntityCullingState.isOccluded(entity.getId())) return true;
        return entity.isInvisible();
    }
}
