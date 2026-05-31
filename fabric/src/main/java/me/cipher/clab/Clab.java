package me.cipher.clab;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public class Clab implements ModInitializer {

    @Override
    public void onInitialize() {
        CommonClass.init();

        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            ClientClass.init();
        }
    }
}
