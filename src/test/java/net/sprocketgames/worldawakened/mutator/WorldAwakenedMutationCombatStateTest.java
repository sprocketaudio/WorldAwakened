package net.sprocketgames.worldawakened.mutator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

class WorldAwakenedMutationCombatStateTest {
    @Test
    void compilesBoundedOnHitStateWithoutRuntimeJson() {
        CompoundTag tag = new CompoundTag();
        JsonObject parameters = new JsonObject();
        parameters.addProperty("effect", "minecraft:poison");
        parameters.addProperty("duration_ticks", 80);
        parameters.addProperty("amplifier", 1);
        parameters.addProperty("chance", 0.25D);

        assertTrue(WorldAwakenedMutationCombatState.appendOnHitEffect(tag, parameters).isEmpty());
        assertEquals(
                new WorldAwakenedMutationCombatState.OnHitEffect(
                        ResourceLocation.parse("minecraft:poison"), 80, 1, 0.25D),
                WorldAwakenedMutationCombatState.onHitEffects(tag).getFirst());
    }

    @Test
    void lifeStealAddsWithCapAndExplosionUsesStrongestValueOnce() {
        CompoundTag tag = new CompoundTag();

        assertTrue(WorldAwakenedMutationCombatState.appendLifeSteal(tag, amount(0.65D)).isEmpty());
        assertTrue(WorldAwakenedMutationCombatState.appendLifeSteal(tag, amount(0.65D)).isEmpty());
        assertEquals(1.0D, WorldAwakenedMutationCombatState.lifeStealRatio(tag));

        assertTrue(WorldAwakenedMutationCombatState.appendDeathExplosion(tag, amount(2.0D)).isEmpty());
        assertTrue(WorldAwakenedMutationCombatState.appendDeathExplosion(tag, amount(6.0D)).isEmpty());
        assertTrue(WorldAwakenedMutationCombatState.appendDeathExplosion(tag, amount(3.0D)).isEmpty());
        assertEquals(6.0D, WorldAwakenedMutationCombatState.deathExplosionPower(tag).orElseThrow());
        assertTrue(!WorldAwakenedMutationCombatState.deathExplosionTriggered(tag));
        assertEquals(6.0D, WorldAwakenedMutationCombatState.consumeDeathExplosionPower(tag).orElseThrow());
        assertTrue(WorldAwakenedMutationCombatState.deathExplosionTriggered(tag));
        assertEquals(6.0D, WorldAwakenedMutationCombatState.deathExplosionPower(tag).orElseThrow());
        assertTrue(WorldAwakenedMutationCombatState.consumeDeathExplosionPower(tag).isEmpty());
    }

    @Test
    void compiledCombatStateSurvivesEntityNbtRoundTrip() {
        CompoundTag original = new CompoundTag();
        JsonObject effect = new JsonObject();
        effect.addProperty("effect", "minecraft:slowness");
        assertTrue(WorldAwakenedMutationCombatState.appendOnHitEffect(original, effect).isEmpty());
        assertTrue(WorldAwakenedMutationCombatState.appendLifeSteal(original, amount(0.15D)).isEmpty());
        assertTrue(WorldAwakenedMutationCombatState.appendDeathExplosion(original, amount(2.5D)).isEmpty());

        CompoundTag reloaded = original.copy();
        assertEquals(ResourceLocation.parse("minecraft:slowness"),
                WorldAwakenedMutationCombatState.onHitEffects(reloaded).getFirst().effectId());
        assertEquals(0.15D, WorldAwakenedMutationCombatState.lifeStealRatio(reloaded), 0.000001D);
        assertEquals(2.5D, WorldAwakenedMutationCombatState.consumeDeathExplosionPower(reloaded).orElseThrow());
    }

    @Test
    void rejectsUnsafeCombatValues() {
        CompoundTag tag = new CompoundTag();
        assertTrue(WorldAwakenedMutationCombatState.appendLifeSteal(tag, amount(1.01D)).isPresent());
        assertTrue(WorldAwakenedMutationCombatState.appendDeathExplosion(tag, amount(8.01D)).isPresent());

        JsonObject invalidEffect = new JsonObject();
        invalidEffect.addProperty("effect", "minecraft:poison");
        invalidEffect.addProperty("duration_ticks", 0);
        assertTrue(WorldAwakenedMutationCombatState.parseOnHitEffect(invalidEffect).isEmpty());
        invalidEffect.addProperty("duration_ticks", "five seconds");
        assertTrue(WorldAwakenedMutationCombatState.parseOnHitEffect(invalidEffect).isEmpty());
        invalidEffect.addProperty("duration_ticks", 2.5D);
        assertTrue(WorldAwakenedMutationCombatState.parseOnHitEffect(invalidEffect).isEmpty());
    }

    private static JsonObject amount(double value) {
        JsonObject parameters = new JsonObject();
        parameters.addProperty("amount", value);
        return parameters;
    }
}
