package me.cipher.clab;

import me.cipher.clab.culling.CullerManager;
import me.cipher.clab.culling.LeavesCuller;
import me.cipher.clab.culling.entity.EntityCullingManager;
import me.cipher.clab.culling.entity.HardwareOcclusionCuller;

public class ClientClass {

    public static final HardwareOcclusionCuller HARDWARE_OCCLUSION_CULLER = new HardwareOcclusionCuller();

    public static void init() {
        Constants.LOG.info("CLab client initializing.");

        CullerManager.register(new LeavesCuller());
        EntityCullingManager.register(HARDWARE_OCCLUSION_CULLER);

        Constants.LOG.info("CLab client initialized.");
    }
}
