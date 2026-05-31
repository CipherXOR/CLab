package me.cipher.clab.culling.blockentity;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.ProgramManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.*;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import me.cipher.clab.Constants;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL33C;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;

public class HardwareOcclusionBlockEntityCuller implements IBlockEntityCuller {

    public static final String ID = "hardware_occlusion_be";
    private static final int GL_QUERY_RESULT_AVAILABLE = 0x8867;
    private static final int GL_QUERY_RESULT = 0x8866;
    private static final int GL_SAMPLES_PASSED = 0x8914;
    private static final int VISIBILITY_STALE_FRAMES = 5;
    private static final int INITIAL_QUERY_POOL_SIZE = 1024;
    private static final int QUERY_READ_DELAY_FRAMES = 0;

    private static final double DISTANCE_NEAR_SQ = 64.0 * 64.0;
    private static final double DISTANCE_MID_SQ = 128.0 * 128.0;
    private static final int FREQUENCY_MID = 2;
    private static final int FREQUENCY_FAR = 3;

    private boolean enabled = true;
    private int frameCounter = 0;

    private final Deque<Integer> availableQueries = new ArrayDeque<>();
    private final Long2IntOpenHashMap pendingQueries = new Long2IntOpenHashMap();
    private final Long2BooleanOpenHashMap lastFrameVisible = new Long2BooleanOpenHashMap();
    private final Long2IntOpenHashMap lastFrameUpdated = new Long2IntOpenHashMap();
    private final Long2IntOpenHashMap querySubmitFrame = new Long2IntOpenHashMap();
    private final Long2IntOpenHashMap beLastQueriedFrame = new Long2IntOpenHashMap();
    private static final int MAX_AABB_CACHE_SIZE = 8192;
    private final Long2ObjectOpenHashMap<AABB> beAABBCache = new Long2ObjectOpenHashMap<>();

    private final LongArrayList readyBeIds = new LongArrayList();
    private final IntArrayList readyQueryIds = new IntArrayList();

    private boolean poolInitialized = false;
    private int currentPoolSize = 0;
    private VertexBuffer unitCubeVbo;

    private double cachedCamX, cachedCamY, cachedCamZ;
    private boolean queryBatchActive = false;

    private ShaderInstance cachedShader;
    private Matrix4f cachedProjMatrix;

    private final ArrayList<BlockEntity> renderedBlockEntities = new ArrayList<>();

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
        for (int i = 0; i < INITIAL_QUERY_POOL_SIZE; i++) {
            availableQueries.add(GL33C.glGenQueries());
        }
        currentPoolSize += INITIAL_QUERY_POOL_SIZE;
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
        readyBeIds.clear();
        readyQueryIds.clear();

        Long2IntMap.Entry entry;
        ObjectIterator<Long2IntMap.Entry> it = pendingQueries.long2IntEntrySet().iterator();
        while (it.hasNext()) {
            entry = it.next();
            long beId = entry.getLongKey();
            int queryId = entry.getIntValue();

            int submitFrame = querySubmitFrame.getOrDefault(beId, frameCounter);
            if (submitFrame > minFrame) {
                continue;
            }

            int available = GL33C.glGetQueryObjecti(queryId, GL_QUERY_RESULT_AVAILABLE);
            if (available == 1) {
                readyBeIds.add(beId);
                readyQueryIds.add(queryId);
                querySubmitFrame.remove(beId);
                it.remove();
            } else if (frameCounter - lastFrameUpdated.getOrDefault(beId, frameCounter) > VISIBILITY_STALE_FRAMES * 2) {
                availableQueries.add(queryId);
                querySubmitFrame.remove(beId);
                it.remove();
                lastFrameVisible.remove(beId);
                lastFrameUpdated.remove(beId);
                beLastQueriedFrame.remove(beId);
            }
        }

        for (int i = 0; i < readyQueryIds.size(); i++) {
            int queryId = readyQueryIds.getInt(i);
            long beId = readyBeIds.getLong(i);
            int samplesPassed = GL33C.glGetQueryObjecti(queryId, GL_QUERY_RESULT);
            boolean visible = samplesPassed > 0;
            lastFrameVisible.put(beId, visible);
            lastFrameUpdated.put(beId, frameCounter);
            availableQueries.add(queryId);
        }
    }

    public void onBlockEntityRendered(BlockEntity blockEntity) {
        if (!enabled || !poolInitialized) {
            return;
        }
        renderedBlockEntities.add(blockEntity);
    }

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

    public void submitAllPendingQueries(Frustum frustum) {
        if (!enabled || !poolInitialized || renderedBlockEntities.isEmpty()) {
            return;
        }
        RenderSystem.assertOnRenderThread();

        for (BlockEntity blockEntity : renderedBlockEntities) {
            AABB aabb = getBlockEntityAABB(blockEntity);
            if (frustum != null && !frustum.isVisible(aabb)) {
                continue;
            }
            submitQueryInternal(blockEntity);
        }
        renderedBlockEntities.clear();
    }

    private void submitQueryInternal(BlockEntity blockEntity) {
        long beId = blockEntity.getBlockPos().asLong();
        if (pendingQueries.containsKey(beId)) {
            return;
        }

        AABB aabb = getBlockEntityAABB(blockEntity);
        if (aabb.hasNaN()) {
            return;
        }

        int frequency = getQueryFrequency(blockEntity);
        boolean wasInvisible = lastFrameVisible.containsKey(beId) && !lastFrameVisible.get(beId);
        if (wasInvisible) {
            frequency = 1;
        }

        int lastQueried = beLastQueriedFrame.getOrDefault(beId, -frequency);
        if (frameCounter - lastQueried < frequency) {
            return;
        }

        if (availableQueries.isEmpty()) {
            growPool();
            Constants.LOG.info("[HOC-BE] Query pool grown to {}", currentPoolSize);
        }

        Integer queryIdObj = availableQueries.poll();
        if (queryIdObj == null) {
            return;
        }
        int queryId = queryIdObj;

        GL33C.glBeginQuery(GL_SAMPLES_PASSED, queryId);
        renderBoundingBoxOcclusion(aabb);
        GL33C.glEndQuery(GL_SAMPLES_PASSED);

        pendingQueries.put(beId, queryId);
        querySubmitFrame.put(beId, frameCounter);
        beLastQueriedFrame.put(beId, frameCounter);
    }

    private AABB getBlockEntityAABB(BlockEntity blockEntity) {
        long beId = blockEntity.getBlockPos().asLong();
        AABB cached = beAABBCache.get(beId);
        if (cached != null) {
            return cached;
        }

        BlockPos pos = blockEntity.getBlockPos();
        var level = blockEntity.getLevel();
        AABB aabb;
        if (level == null) {
            aabb = new AABB(pos);
        } else {
            var state = blockEntity.getBlockState();
            VoxelShape shape = state.getShape(level, pos);
            if (shape.isEmpty()) {
                aabb = new AABB(pos);
            } else {
                aabb = shape.bounds().move(pos);
            }
        }

        if (beAABBCache.size() > MAX_AABB_CACHE_SIZE) {
            beAABBCache.clear();
        }
        beAABBCache.put(beId, aabb);
        return aabb;
    }

    private int getQueryFrequency(BlockEntity blockEntity) {
        BlockPos pos = blockEntity.getBlockPos();
        double dx = pos.getX() + 0.5 - cachedCamX;
        double dy = pos.getY() + 0.5 - cachedCamY;
        double dz = pos.getZ() + 0.5 - cachedCamZ;
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
    public boolean shouldCull(BlockEntity blockEntity, Camera camera) {
        if (!enabled) {
            return false;
        }

        long beId = blockEntity.getBlockPos().asLong();

        if (lastFrameVisible.containsKey(beId)) {
            int updated = lastFrameUpdated.getOrDefault(beId, frameCounter);
            if (frameCounter - updated > VISIBILITY_STALE_FRAMES) {
                lastFrameVisible.remove(beId);
                lastFrameUpdated.remove(beId);
                beLastQueriedFrame.remove(beId);
                return false;
            }
            boolean visible = lastFrameVisible.get(beId);
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
        beLastQueriedFrame.clear();
        beAABBCache.clear();
        poolInitialized = false;
    }
}
