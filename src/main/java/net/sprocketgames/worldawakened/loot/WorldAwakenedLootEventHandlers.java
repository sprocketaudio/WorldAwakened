package net.sprocketgames.worldawakened.loot;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.sprocketgames.worldawakened.config.WorldAwakenedCommonConfig;
import net.sprocketgames.worldawakened.config.WorldAwakenedFeatureGates;

public final class WorldAwakenedLootEventHandlers {
    private final WorldAwakenedLootService lootService;

    public WorldAwakenedLootEventHandlers(WorldAwakenedLootService lootService) {
        this.lootService = lootService;
    }

    public void onLivingDrops(LivingDropsEvent event) {
        if (!WorldAwakenedFeatureGates.modEnabled() || !WorldAwakenedCommonConfig.ENABLE_LOOT_EVOLUTION.get()) {
            return;
        }
        if (!(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        ServerPlayer killer = event.getSource().getEntity() instanceof ServerPlayer player ? player : null;
        lootService.onEntityKilledDrops(level, event.getEntity(), killer, event.getDrops());
    }
}

