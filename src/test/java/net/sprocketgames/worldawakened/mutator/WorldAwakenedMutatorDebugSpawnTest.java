package net.sprocketgames.worldawakened.mutator;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;

class WorldAwakenedMutatorDebugSpawnTest {
    @Test
    @SuppressWarnings("deprecation")
    void debugSpawnRunsVanillaCommandSpawnFinalization() {
        ServerLevel level = mock(ServerLevel.class);
        Mob mob = mock(Mob.class);
        BlockPos position = new BlockPos(4, 70, -3);
        DifficultyInstance difficulty = mock(DifficultyInstance.class);
        when(mob.blockPosition()).thenReturn(position);
        when(mob.getPersistentData()).thenReturn(new CompoundTag());
        when(level.getCurrentDifficultyAt(position)).thenReturn(difficulty);

        WorldAwakenedMutatorService.finalizeDebugSpawn(level, mob);

        verify(mob).finalizeSpawn(level, difficulty, MobSpawnType.COMMAND, null);
    }
}
