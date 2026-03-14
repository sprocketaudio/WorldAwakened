package net.sprocketgames.worldawakened.client.render;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;

public final class WorldAwakenedClientGlowStyleEventHandlers {
    public void onRenderLevelStage(RenderLevelStageEvent event) {
        WorldAwakenedClientGlowStyleService.renderGlowStyles(event);
    }

    public void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide()) {
            return;
        }
        WorldAwakenedClientGlowStyleService.removeGlowStyle(event.getEntity().getId());
    }

    public void onClientTickPost(ClientTickEvent.Post event) {
        if (Minecraft.getInstance().level == null) {
            WorldAwakenedClientGlowStyleService.clearAll();
        }
    }
}
