package net.sprocketgames.worldawakened.client.render;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.sprocketgames.worldawakened.mutator.WorldAwakenedGlowStyleState;

public final class WorldAwakenedClientGlowStyleService {
    private static final Map<Integer, WorldAwakenedGlowStyleState.GlowStyleDefinition> ACTIVE_GLOW_STYLES = new HashMap<>();

    private WorldAwakenedClientGlowStyleService() {
    }

    public static void upsertGlowStyle(int entityId, WorldAwakenedGlowStyleState.GlowStyleDefinition style) {
        ACTIVE_GLOW_STYLES.put(entityId, style);
    }

    public static void removeGlowStyle(int entityId) {
        ACTIVE_GLOW_STYLES.remove(entityId);
    }

    public static void clearAll() {
        ACTIVE_GLOW_STYLES.clear();
    }

    public static void renderGlowStyles(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || ACTIVE_GLOW_STYLES.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            clearAll();
            return;
        }
        EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
        OutlineBufferSource outlineBuffer = minecraft.renderBuffers().outlineBufferSource();
        PoseStack poseStack = event.getPoseStack();
        Vec3 cameraPosition = event.getCamera().getPosition();
        Entity cameraEntity = minecraft.getCameraEntity();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        boolean renderedAny = false;

        Iterator<Map.Entry<Integer, WorldAwakenedGlowStyleState.GlowStyleDefinition>> iterator =
                ACTIVE_GLOW_STYLES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, WorldAwakenedGlowStyleState.GlowStyleDefinition> entry = iterator.next();
            Entity entity = minecraft.level.getEntity(entry.getKey());
            // Keep state when the client entity is temporarily unresolved (chunk/stream transition).
            // A leave-level event or explicit inactive payload is the authoritative clear path.
            if (entity == null) {
                continue;
            }
            if (!entity.isAlive()) {
                iterator.remove();
                continue;
            }
            WorldAwakenedGlowStyleState.GlowStyleDefinition style = entry.getValue();

            double renderX = Mth.lerp(partialTick, entity.xOld, entity.getX()) - cameraPosition.x();
            double renderY = Mth.lerp(partialTick, entity.yOld, entity.getY()) - cameraPosition.y();
            double renderZ = Mth.lerp(partialTick, entity.zOld, entity.getZ()) - cameraPosition.z();
            // shouldRender expects camera world coordinates, not entity-relative render offsets.
            if (!dispatcher.shouldRender(
                    entity,
                    event.getFrustum(),
                    cameraPosition.x(),
                    cameraPosition.y(),
                    cameraPosition.z())) {
                continue;
            }

            // Vanilla outline is naturally through-wall. For non-through-wall mode, skip fully occluded targets.
            if (!style.seeThroughWalls() && isFullyOccluded(minecraft, cameraEntity, cameraPosition, entity)) {
                continue;
            }

            int pulsedColor = resolvePulsedColor(entity, partialTick, style);
            outlineBuffer.setColor((pulsedColor >> 16) & 0xFF, (pulsedColor >> 8) & 0xFF, pulsedColor & 0xFF, 255);

            renderOutlinePasses(
                    dispatcher,
                    entity,
                    renderX,
                    renderY,
                    renderZ,
                    partialTick,
                    poseStack,
                    outlineBuffer);
            renderedAny = true;
        }

        if (renderedAny) {
            event.getLevelRenderer().requestOutlineEffect();
        }
    }

    private static void renderOutlinePasses(
            EntityRenderDispatcher dispatcher,
            Entity entity,
            double renderX,
            double renderY,
            double renderZ,
            float partialTick,
            PoseStack poseStack,
            OutlineBufferSource outlineBuffer) {
        renderEntityOutline(
                dispatcher,
                entity,
                renderX,
                renderY,
                renderZ,
                partialTick,
                poseStack,
                outlineBuffer);
    }

    private static void renderEntityOutline(
            EntityRenderDispatcher dispatcher,
            Entity entity,
            double renderX,
            double renderY,
            double renderZ,
            float partialTick,
            PoseStack poseStack,
            OutlineBufferSource outlineBuffer) {
        float yaw = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());

        poseStack.pushPose();
        dispatcher.render(
                entity,
                renderX,
                renderY,
                renderZ,
                yaw,
                partialTick,
                poseStack,
                outlineBuffer,
                LightTexture.FULL_BRIGHT);
        poseStack.popPose();
    }

    private static boolean isFullyOccluded(
            Minecraft minecraft,
            Entity cameraEntity,
            Vec3 cameraPosition,
            Entity target) {
        if (minecraft.level == null || cameraEntity == null) {
            return false;
        }
        Vec3 center = target.getBoundingBox().getCenter();
        double verticalOffset = target.getBbHeight() * 0.45D;
        Vec3 top = center.add(0.0D, verticalOffset, 0.0D);
        Vec3 bottom = center.add(0.0D, -verticalOffset, 0.0D);

        return !hasClearLineOfSight(minecraft, cameraEntity, cameraPosition, center)
                && !hasClearLineOfSight(minecraft, cameraEntity, cameraPosition, top)
                && !hasClearLineOfSight(minecraft, cameraEntity, cameraPosition, bottom);
    }

    private static boolean hasClearLineOfSight(
            Minecraft minecraft,
            Entity cameraEntity,
            Vec3 start,
            Vec3 end) {
        ClipContext context = new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, cameraEntity);
        HitResult hitResult = minecraft.level.clip(context);
        return hitResult.getType() == HitResult.Type.MISS;
    }

    private static int resolvePulsedColor(
            Entity entity,
            float partialTick,
            WorldAwakenedGlowStyleState.GlowStyleDefinition style) {
        int baseColor = style.colorRgb() & 0x00FFFFFF;
        float brightnessScale = Mth.clamp(
                style.brightness(),
                WorldAwakenedGlowStyleState.MIN_BRIGHTNESS,
                WorldAwakenedGlowStyleState.MAX_BRIGHTNESS);
        if (style.pulse()) {
            float phase = (entity.tickCount + partialTick) * (0.15F * style.pulseSpeed());
            float pulseSwing = Mth.clamp(style.pulseStrength() * 1.8F, 0.12F, 0.55F);
            brightnessScale *= 1.0F + (Mth.sin(phase) * pulseSwing);
            brightnessScale = Mth.clamp(
                    brightnessScale,
                    WorldAwakenedGlowStyleState.MIN_BRIGHTNESS,
                    WorldAwakenedGlowStyleState.MAX_BRIGHTNESS);
        }

        int red = Mth.clamp(Math.round(((baseColor >> 16) & 0xFF) * brightnessScale), 0, 255);
        int green = Mth.clamp(Math.round(((baseColor >> 8) & 0xFF) * brightnessScale), 0, 255);
        int blue = Mth.clamp(Math.round((baseColor & 0xFF) * brightnessScale), 0, 255);
        return (red << 16) | (green << 8) | blue;
    }
}
