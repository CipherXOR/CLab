package me.cipher.clab;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public class Clab implements ModInitializer {

    @Override
    public void onInitialize() {
        CommonClass.init();

        if (FabricLoader.getInstance().getEnvironmentType() == net.fabricmc.api.EnvType.CLIENT) {
            ClientClass.init();
        }
    }
}
