package net.sprocketgames.worldawakened.progression.trigger;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

public final class WorldAwakenedTriggerEventHandlers {
    private final WorldAwakenedTriggerService triggerService;

    public WorldAwakenedTriggerEventHandlers(WorldAwakenedTriggerService triggerService) {
        this.triggerService = triggerService;
    }

    public void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ResourceLocation toDimension = event.getTo().location();
        triggerService.onPlayerEnteredDimension(player, toDimension);
    }

    public void onAdvancementCompleted(AdvancementEvent.AdvancementEarnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        triggerService.onAdvancementCompleted(player, event.getAdvancement().id());
    }

    public void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        Entity attacker = event.getSource().getEntity();
        ServerPlayer killer = attacker instanceof ServerPlayer serverPlayer ? serverPlayer : null;
        triggerService.onEntityKilled(level, killer, event.getEntity());
    }

    public void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        triggerService.onItemCrafted(player);
    }

    public void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        triggerService.onBlockPlaced(player);
    }

    public void onBlockBroken(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        triggerService.onBlockBroken(player);
    }
}
