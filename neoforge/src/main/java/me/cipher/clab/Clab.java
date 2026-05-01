package me.cipher.clab;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

@Mod(Constants.MOD_ID)
public class Clab {

    public Clab(IEventBus eventBus) {
        CommonClass.init();

        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientClass.init();
        }

    }
}
