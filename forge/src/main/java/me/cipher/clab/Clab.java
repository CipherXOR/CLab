package me.cipher.clab;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(Constants.MOD_ID)
public class Clab {

    public Clab() {
        CommonClass.init();

        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientClass.init();
        }
    }
}
