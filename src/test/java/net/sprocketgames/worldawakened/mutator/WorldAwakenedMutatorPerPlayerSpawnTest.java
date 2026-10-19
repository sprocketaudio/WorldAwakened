package net.sprocketgames.worldawakened.mutator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.sprocketgames.worldawakened.config.WorldAwakenedConfigService;
import net.sprocketgames.worldawakened.config.WorldAwakenedEditorSettings;
import net.sprocketgames.worldawakened.data.load.WorldAwakenedDatapackService;
import net.sprocketgames.worldawakened.data.load.WorldAwakenedDatapackSnapshot;
import net.sprocketgames.worldawakened.debug.WorldAwakenedDiagnosticCodes;
import net.sprocketgames.worldawakened.progression.WorldAwakenedStageService;

/**
 * Per-player mutation spawning deliberately fails closed when no nearby eligible
 * player can own the spawn context. Server-wide tests use
 * {@link WorldAwakenedMutatorTestSupport}; this test protects the distinct
 * per-player boundary instead of relying on the global default.
 */
class WorldAwakenedMutatorPerPlayerSpawnTest {
    @Test
    void unattributedPerPlayerSpawnIsSkippedBeforeAnyPoolCanApply() {
        WorldAwakenedEditorSettings perPlayer = WorldAwakenedEditorSettings.builtInDefaults();
        try (WorldAwakenedConfigService.PreviewSettingsScope ignored =
                WorldAwakenedConfigService.pushPreviewSettings(perPlayer)) {
            WorldAwakenedDatapackSnapshot snapshot = WorldAwakenedDatapackSnapshot.empty();
            WorldAwakenedDatapackService datapacks = mock(WorldAwakenedDatapackService.class);
            when(datapacks.currentSnapshot()).thenReturn(snapshot);
            when(datapacks.pinSnapshot(eq(snapshot), anyString())).thenReturn(snapshot);

            WorldAwakenedStageService stages = mock(WorldAwakenedStageService.class);
            WorldAwakenedMutatorService service = new WorldAwakenedMutatorService(datapacks, stages);
            ServerLevel level = mock(ServerLevel.class);
            ResourceKey<Level> overworld = ResourceKey.create(
                    Registries.DIMENSION,
                    ResourceLocation.withDefaultNamespace("overworld"));
            when(level.dimension()).thenReturn(overworld);
            when(level.players()).thenReturn(List.of());

            Mob zombie = mock(Mob.class);
            @SuppressWarnings("unchecked")
            EntityType<? extends Entity> zombieType = (EntityType<? extends Entity>) EntityType.ZOMBIE;
            doReturn(zombieType).when(zombie).getType();
            when(zombie.blockPosition()).thenReturn(BlockPos.ZERO);
            when(zombie.getPersistentData()).thenReturn(new CompoundTag());

            WorldAwakenedMutatorService.MutatorRunResult result =
                    service.onMobSpawn(level, zombie, WorldAwakenedMobSpawnOrigin.OTHER);

            assertTrue(result.skipped());
            assertFalse(result.liveApplied());
            assertEquals(WorldAwakenedDiagnosticCodes.SPAWN_CONTEXT_INVALIDATED, result.skipCode());
            assertEquals(perPlayer.progressionMode(), result.progressionMode());
            assertTrue(result.selectedPoolId().isEmpty(), "unattributed spawns must not reach pool selection");
        }
    }
}
