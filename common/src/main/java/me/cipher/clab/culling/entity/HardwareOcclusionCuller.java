package me.cipher.clab.culling.entity;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.ProgramManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import it.unimi.dsi.fastutil.ints.Int2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import me.cipher.clab.Constants;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL33C;

import java.util.ArrayDeque;
import java.util.Deque;



public class HardwareOcclusionCuller implements IEntityCuller {

    public static final String ID = "hardware_occlusion";
    private static final int GL_QUERY_RESULT_AVAILABLE = 0x8867;
    private static final int GL_QUERY_RESULT = 0x8866;
    private static final int GL_SAMPLES_PASSED = 0x8914;
    private static final int VISIBILITY_STALE_FRAMES = 5;
    private static final int INITIAL_QUERY_POOL_SIZE = 1024;
    private static final int QUERY_READ_DELAY_FRAMES = 1;

    private static final double DISTANCE_NEAR_SQ = 64.0 * 64.0;      // < 64 blocks: every frame
    private static final double DISTANCE_MID_SQ = 128.0 * 128.0;     // < 128 blocks: every 2 frames
    private static final int FREQUENCY_MID = 2;
    private static final int FREQUENCY_FAR = 3;                      // >= 128 blocks: every 3 frames

    private boolean enabled = true;
    private int frameCounter = 0;

    private final Deque<Integer> availableQueries = new ArrayDeque<>();
    private final Int2IntOpenHashMap pendingQueries = new Int2IntOpenHashMap();
    private final Int2BooleanOpenHashMap lastFrameVisible = new Int2BooleanOpenHashMap();
    private final Int2IntOpenHashMap lastFrameUpdated = new Int2IntOpenHashMap();
    private final Int2IntOpenHashMap querySubmitFrame = new Int2IntOpenHashMap();
    private final Int2IntOpenHashMap entityLastQueriedFrame = new Int2IntOpenHashMap();

    private final IntArrayList readyEntityIds = new IntArrayList();
    private final IntArrayList readyQueryIds = new IntArrayList();

    private boolean poolInitialized = false;
    private int currentPoolSize = 0;
    private VertexBuffer unitCubeVbo;

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void ensurePool() {
        if (poolInitialized) {
            return;
       
        }
        RenderSystem.assertOnRenderThread();
        growPool();
        poolInitialized = true;
    }

    private void growPool() {
        RenderSystem.assertOnRenderThread();
        for (int i = 0; i < HardwareOcclusionCuller.INITIAL_QUERY_POOL_SIZE; i++) {
            availableQueries.add(GL33C.glGenQueries());
        }
        currentPoolSize += HardwareOcclusionCuller.INITIAL_QUERY_POOL_SIZE;
    }

    private void ensureMesh() {
        if (unitCubeVbo != null) return;
        RenderSystem.assertOnRenderThread();

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(
                VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION
        );

        buffer.addVertex(0, 0, 0); buffer.addVertex(1, 0, 0); buffer.addVertex(1, 0, 1); buffer.addVertex(0, 0, 1);
        buffer.addVertex(0, 1, 1); buffer.addVertex(1, 1, 1); buffer.addVertex(1, 1, 0); buffer.addVertex(0, 1, 0);
        buffer.addVertex(0, 0, 0); buffer.addVertex(0, 1, 0); buffer.addVertex(1, 1, 0); buffer.addVertex(1, 0, 0);
        buffer.addVertex(1, 0, 1); buffer.addVertex(1, 1, 1); buffer.addVertex(0, 1, 1); buffer.addVertex(0, 0, 1);
        buffer.addVertex(0, 0, 1); buffer.addVertex(0, 1, 1); buffer.addVertex(0, 1, 0); buffer.addVertex(0, 0, 0);
        buffer.addVertex(1, 0, 0); buffer.addVertex(1, 1, 0); buffer.addVertex(1, 1, 1); buffer.addVertex(1, 0, 1);

        MeshData mesh = buffer.build();
        if (mesh != null) {
            unitCubeVbo = new VertexBuffer(VertexBuffer.Usage.STATIC);
            unitCubeVbo.bind();
            unitCubeVbo.upload(mesh);
            VertexBuffer.unbind();
        }
    }

    public void processPendingQueries() {
        if (!enabled || !poolInitialized) {
            return;
        }
        RenderSystem.assertOnRenderThread();
        frameCounter++;

        int minFrame = frameCounter - QUERY_READ_DELAY_FRAMES;
        readyEntityIds.clear();
        readyQueryIds.clear();

        Int2IntMap.Entry entry;
        ObjectIterator<Int2IntMap.Entry> it = pendingQueries.int2IntEntrySet().iterator();
        while (it.hasNext()) {
            entry = it.next();
            int entityId = entry.getIntKey();
            int queryId = entry.getIntValue();

            int submitFrame = querySubmitFrame.getOrDefault(queryId, frameCounter);
            if (submitFrame > minFrame) {
                continue;
            }

            int available = GL33C.glGetQueryObjecti(queryId, GL_QUERY_RESULT_AVAILABLE);
            if (available == 1) {
                readyEntityIds.add(entityId);
                readyQueryIds.add(queryId);
                querySubmitFrame.remove(queryId);
                it.remove();
            } else if (frameCounter - lastFrameUpdated.getOrDefault(entityId, frameCounter) > VISIBILITY_STALE_FRAMES * 2) {
                availableQueries.add(queryId);
                querySubmitFrame.remove(queryId);
                it.remove();
                lastFrameVisible.remove(entityId);
                lastFrameUpdated.remove(entityId);
                entityLastQueriedFrame.remove(entityId);
            }
        }

        for (int i = 0; i < readyQueryIds.size(); i++) {
            int queryId = readyQueryIds.getInt(i);
            int entityId = readyEntityIds.getInt(i);
            int samplesPassed = GL33C.glGetQueryObjecti(queryId, GL_QUERY_RESULT);
            boolean visible = samplesPassed > 0;
            lastFrameVisible.put(entityId, visible);
            lastFrameUpdated.put(entityId, frameCounter);
            availableQueries.add(queryId);
        }
    }

    private boolean queryBatchActive = false;
    private double cachedCamX, cachedCamY, cachedCamZ;

    private ShaderInstance cachedShader;
    private Matrix4f cachedProjMatrix;

    public void beginQueryBatch() {
        if (!enabled || !poolInitialized) {
            return;
        }
        RenderSystem.assertOnRenderThread();
        queryBatchActive = true;

        Vec3 camPos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        cachedCamX = camPos.x;
        cachedCamY = camPos.y;
        cachedCamZ = camPos.z;

        GlStateManager._enableDepthTest();
        GlStateManager._depthMask(false);
        GlStateManager._colorMask(false, false, false, false);
        GlStateManager._disableBlend();
        GlStateManager._disableCull();

        cachedShader = GameRenderer.getPositionShader();
        cachedProjMatrix = RenderSystem.getProjectionMatrix();
        if (cachedShader != null) {
            ProgramManager.glUseProgram(cachedShader.getId());
            RenderSystem.applyModelViewMatrix();
            if (cachedShader.PROJECTION_MATRIX != null) {
                cachedShader.PROJECTION_MATRIX.set(cachedProjMatrix);
                cachedShader.PROJECTION_MATRIX.upload();
            }
        }
    }

    public void endQueryBatch() {
        if (!queryBatchActive) {
            return;
        }
        queryBatchActive = false;
        cachedShader = null;
        cachedProjMatrix = null;

        GlStateManager._depthMask(true);
        GlStateManager._colorMask(true, true, true, true);
        GlStateManager._enableBlend();
        GlStateManager._enableCull();
    }

    public void submitQuery(Entity entity) {
        if (!enabled || !poolInitialized) {
            return;
        }
        RenderSystem.assertOnRenderThread();

        if (entity instanceof Player) {
            return;
        }
        if (entity.isCurrentlyGlowing()) {
            return;
        }
        if (entity.isPassenger()) {
            return;
        }

        int entityId = entity.getId();
        if (pendingQueries.containsKey(entityId)) {
            return;
        }

        int frequency = getQueryFrequency(entity);
        int lastQueried = entityLastQueriedFrame.getOrDefault(entityId, -frequency);
        if (frameCounter - lastQueried < frequency) {
            return;
        }

        if (availableQueries.isEmpty()) {
            growPool();
            Constants.LOG.info("[HOC] Query pool grown to {}", currentPoolSize);
        }

        Integer queryIdObj = availableQueries.poll();
        if (queryIdObj == null) {
            return;
        }
        int queryId = queryIdObj;

        AABB aabb = entity.getBoundingBoxForCulling();
        if (aabb.hasNaN()) {
            aabb = entity.getBoundingBox();
        }

        if (aabb.minX == aabb.maxX || aabb.minY == aabb.maxY || aabb.minZ == aabb.maxZ) {
            return;
        }

        GL33C.glBeginQuery(GL_SAMPLES_PASSED, queryId);
        renderBoundingBoxOcclusion(aabb);
        GL33C.glEndQuery(GL_SAMPLES_PASSED);

        pendingQueries.put(entityId, queryId);
        querySubmitFrame.put(queryId, frameCounter);
        entityLastQueriedFrame.put(entityId, frameCounter);
    }

    private int getQueryFrequency(Entity entity) {
        double dx = entity.getX() - cachedCamX;
        double dy = entity.getY() - cachedCamY;
        double dz = entity.getZ() - cachedCamZ;
        double distSq = dx * dx + dy * dy + dz * dz;

        if (distSq < DISTANCE_NEAR_SQ) {
            return 1;
        } else if (distSq < DISTANCE_MID_SQ) {
            return FREQUENCY_MID;
        } else {
            return FREQUENCY_FAR;
        }
    }

    private void renderBoundingBoxOcclusion(AABB aabb) {
        ensureMesh();
        if (unitCubeVbo == null) return;

        float tx = (float)(aabb.minX - cachedCamX);
        float ty = (float)(aabb.minY - cachedCamY);
        float tz = (float)(aabb.minZ - cachedCamZ);
        float sx = (float)(aabb.maxX - aabb.minX);
        float sy = (float)(aabb.maxY - aabb.minY);
        float sz = (float)(aabb.maxZ - aabb.minZ);

        RenderSystem.getModelViewStack().pushMatrix();
        RenderSystem.getModelViewStack().translate(tx, ty, tz);
        RenderSystem.getModelViewStack().scale(sx, sy, sz);
        RenderSystem.applyModelViewMatrix();

        if (cachedShader != null && cachedShader.MODEL_VIEW_MATRIX != null) {
            cachedShader.MODEL_VIEW_MATRIX.set(RenderSystem.getModelViewMatrix());
            cachedShader.MODEL_VIEW_MATRIX.upload();
        }

        unitCubeVbo.bind();
        unitCubeVbo.draw();

        RenderSystem.getModelViewStack().popMatrix();
        RenderSystem.applyModelViewMatrix();
    }

    @Override
    public boolean shouldCull(Entity entity, Camera camera) {
        if (!enabled) {
            return false;
        }

        if (entity instanceof Player) {
            return false;
        }
        if (entity.isCurrentlyGlowing()) {
            return false;
        }
        if (entity.isPassenger()) {
            return false;
        }
        if (entity == camera.getEntity()) {
            return false;
        }

        int entityId = entity.getId();

        if (lastFrameVisible.containsKey(entityId)) {
            int updated = lastFrameUpdated.getOrDefault(entityId, frameCounter);
            if (frameCounter - updated > VISIBILITY_STALE_FRAMES) {
                lastFrameVisible.remove(entityId);
                lastFrameUpdated.remove(entityId);
                entityLastQueriedFrame.remove(entityId);
                return false;
            }
            boolean visible = lastFrameVisible.get(entityId);
            return !visible;
        }

        return false;
    }

    public void cleanup() {
        if (!poolInitialized) {
            return;
        }
        RenderSystem.assertOnRenderThread();
        if (unitCubeVbo != null) {
            unitCubeVbo.close();
            unitCubeVbo = null;
        }
        for (int queryId : availableQueries) {
            GL33C.glDeleteQueries(queryId);
        }
        for (int queryId : pendingQueries.values()) {
            GL33C.glDeleteQueries(queryId);
        }
        availableQueries.clear();
        pendingQueries.clear();
        lastFrameVisible.clear();
        lastFrameUpdated.clear();
        querySubmitFrame.clear();
        entityLastQueriedFrame.clear();
        poolInitialized = false;
    }
}
