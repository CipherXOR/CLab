# CLab (Culling Lab)

&gt; Bringing **Hardware Occlusion Culling (HOC)** to Minecraft entity and block rendering optimization.

CLab is a client-side rendering optimization mod. It leverages GPU OpenGL occlusion queries to determine entity visibility directly, eliminating the approximation errors and side effects inherent to traditional CPU-side raytracing.

## Core Features

- **Hardware Occlusion Culling (HOC)**  
  Uses GPU queries to test entity bounding boxes against the depth buffer, measuring real occlusion against actually rendered terrain rather than CPU-side approximations of the world model.

- **Zero False-Positive Culling**  
  Based on ground-truth depth buffer testing. An entity is never hidden if even a single pixel is visible. No whitelist needed, no per-mod patches required.

- **Render-Layer Only, Zero Intrusion**  
  Only decides whether to invoke the entity render method. Does not interfere with client-side ticks, animation states, position interpolation, or any logic. Trains, vehicles, and contraptions behave normally.

- **Built-in Leaf Face Culling**  
  Automatically culls leaf faces occluded by adjacent leaves, reducing terrain rendering overhead.

- **Zero-Config, Works Out of the Box**  
  No entity whitelist, no distance threshold, no tick-culling toggle. Install and play.

## Technical Overview

Traditional entity culling typically casts rays on the CPU, inferring occlusion from simplified block properties. This frequently produces false positives around glass, leaves, fences, and custom mod blocks, forcing maintainers to continually expand whitelists as a workaround.

CLab uses **Hardware Occlusion Culling**:

1. During entity rendering, submit the entity bounding box to the GPU as an occlusion query (OpenGL Query).
2. The GPU compares the bounding box against the already-rendered terrain depth buffer at the hardware level.
3. Read the previous frame's query result: if no pixels passed the depth test, skip rendering this entity.

This creates a fundamental difference:

| Aspect | CPU Raytracing | CLab HOC |
|--------|---------------|----------|
| Occlusion basis | Simplified assumptions about world model | Actual GPU-rendered depth buffer |
| False positives (hiding visible entities) | Common (near non-solid blocks) | Extremely unlikely |
| Entity tick/animation | Often frozen or skipped | Completely unaffected |
| CPU overhead | Continuous background traversal | Render thread submits AABB only; GPU handles the rest asynchronously |
| Configuration required | Whitelists, distance limits, thresholds as compensatory mechanisms | None required |

## Compatibility

- **Loaders**: Fabric / Quilt / Forge / NeoForge
- **Game Versions**: 1.20.1 / 1.21.1 (subject to actual release)
- **Content Mods**: Create, Immersive Vehicles, and other entity-heavy mods — no visual anomalies

&gt; ⚠️ **Client-side only**. Do not install on the server.

## Why No Configuration File?

CLab's design philosophy: **Rendering optimization should not shift compatibility burdens onto players.**

By moving occlusion determination from CPU approximation to GPU ground-truth testing, we eliminate false positives at the source. By restricting culling strictly to the rendering layer, we eliminate the need for compensatory whitelists against entity logic. Therefore, no configuration is necessary.

## Performance Expectations

- **Low entity density / open scenes**: Minimal FPS gain (vanilla is already efficient enough)
- **High entity density + terrain occlusion** (e.g., mob farms, dense trading halls, Create factories): Significant FPS improvement, with more stable frame times during rapid camera movement
- **CPU usage**: Compared to CPU raytracing solutions, HOC offloads occlusion computation to the GPU, significantly reducing load on both the client main thread and any background worker threads

## License

[LGPL-3.0](LICENSE)