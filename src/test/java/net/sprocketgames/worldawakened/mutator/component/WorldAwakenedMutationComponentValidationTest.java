package net.sprocketgames.worldawakened.mutator.component;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

import net.minecraft.resources.ResourceLocation;
import net.sprocketgames.worldawakened.data.definition.MutationComponentDefinition;

class WorldAwakenedMutationComponentValidationTest {
    @Test
    void rejectsEmptyComponents() {
        WorldAwakenedMutationComponentValidation.Result result =
                WorldAwakenedMutationComponentValidation.validate(List.of());
        assertHasIssue(result, WorldAwakenedMutationComponentValidation.IssueKind.EMPTY_COMPONENT_LIST);
    }

    @Test
    void rejectsUnknownComponentTypes() {
        MutationComponentDefinition unknown = component("testpack:unknown_component", true, params(), List.of(), List.of());
        WorldAwakenedMutationComponentValidation.Result result =
                WorldAwakenedMutationComponentValidation.validate(List.of(unknown));
        assertHasIssue(result, WorldAwakenedMutationComponentValidation.IssueKind.UNKNOWN_COMPONENT_TYPE);
    }

    @Test
    void rejectsIncompatibleComposition() {
        MutationComponentDefinition fire = component("worldawakened:fire_package", true, params(), List.of(), List.of());
        MutationComponentDefinition frost = component("worldawakened:frost_package", true, params(), List.of(), List.of());
        WorldAwakenedMutationComponentValidation.Result result =
                WorldAwakenedMutationComponentValidation.validate(List.of(fire, frost));
        assertHasIssue(result, WorldAwakenedMutationComponentValidation.IssueKind.INCOMPATIBLE_COMPONENT_COMPOSITION);
    }

    @Test
    void rejectsDuplicateComponentTypesWhenUnsupported() {
        MutationComponentDefinition first = component("worldawakened:max_health_bonus", true, params("amount", 3.0D), List.of(), List.of());
        MutationComponentDefinition second = component("worldawakened:max_health_bonus", true, params("amount", 2.0D), List.of(), List.of());
        WorldAwakenedMutationComponentValidation.Result result =
                WorldAwakenedMutationComponentValidation.validate(List.of(first, second));
        assertHasIssue(result, WorldAwakenedMutationComponentValidation.IssueKind.DUPLICATE_COMPONENT_TYPE);
    }

    @Test
    void rejectsImpossibleSummonComposition() {
        MutationComponentDefinition summonCooldown = component("worldawakened:summon_cooldown", true, params("seconds", 10.0D), List.of(), List.of());
        WorldAwakenedMutationComponentValidation.Result result =
                WorldAwakenedMutationComponentValidation.validate(List.of(summonCooldown));
        assertHasIssue(result, WorldAwakenedMutationComponentValidation.IssueKind.IMPOSSIBLE_COMPONENT_COMPOSITION);
    }

    @Test
    void rejectsNoRuntimeResultWhenAllComponentsDisabled() {
        MutationComponentDefinition disabled = component("worldawakened:max_health_bonus", false, params("amount", 4.0D), List.of(), List.of());
        WorldAwakenedMutationComponentValidation.Result result =
                WorldAwakenedMutationComponentValidation.validate(List.of(disabled));
        assertHasIssue(result, WorldAwakenedMutationComponentValidation.IssueKind.NO_RUNTIME_RESULT);
    }

    @Test
    void allowsDuplicateEquipItemComponents() {
        MutationComponentDefinition weapon = component(
                "worldawakened:equip_item",
                true,
                equipParams("minecraft:iron_sword", "mainhand"),
                List.of(),
                List.of());
        MutationComponentDefinition helmet = component(
                "worldawakened:equip_item",
                true,
                equipParams("minecraft:iron_helmet", "head"),
                List.of(),
                List.of());

        WorldAwakenedMutationComponentValidation.Result result =
                WorldAwakenedMutationComponentValidation.validate(List.of(weapon, helmet));

        assertTrue(result.issues().isEmpty(), () -> "Expected no issues but got " + result.issues());
    }

    @Test
    void rejectsEquipItemWithUnknownEnchantment() {
        MutationComponentDefinition invalid = component(
                "worldawakened:equip_item",
                true,
                equipParamsWithEnchantment("minecraft:iron_sword", "minecraft:not_real", 2),
                List.of(),
                List.of());

        WorldAwakenedMutationComponentValidation.Result result =
                WorldAwakenedMutationComponentValidation.validate(List.of(invalid));

        assertHasIssue(result, WorldAwakenedMutationComponentValidation.IssueKind.INVALID_COMPONENT_PARAMETERS);
    }

    @Test
    void acceptsMovementSpeedMultiplier() {
        MutationComponentDefinition multiplier = component(
                "worldawakened:movement_speed_multiplier",
                true,
                params("multiplier", 1.15D),
                List.of(),
                List.of());

        WorldAwakenedMutationComponentValidation.Result result =
                WorldAwakenedMutationComponentValidation.validate(List.of(multiplier));

        assertTrue(result.issues().isEmpty(), () -> "Expected no issues but got " + result.issues());
    }

    @Test
    void acceptsGlowStyleWithExplicitParameters() {
        MutationComponentDefinition glow = component(
                "worldawakened:glow_style",
                true,
                glowStyleParams("#66ff66", 0.85D, false, true, 1.2D, 0.2D),
                List.of(),
                List.of());

        WorldAwakenedMutationComponentValidation.Result result =
                WorldAwakenedMutationComponentValidation.validate(List.of(glow));

        assertTrue(result.issues().isEmpty(), () -> "Expected no issues but got " + result.issues());
    }

    @Test
    void acceptsGlowStyleWithOutOfRangeBrightnessAndClampsAtRuntime() {
        MutationComponentDefinition glow = component(
                "worldawakened:glow_style",
                true,
                glowStyleParams("#66ff66", 4.0D, false, false, 1.0D, 0.12D),
                List.of(),
                List.of());

        WorldAwakenedMutationComponentValidation.Result result =
                WorldAwakenedMutationComponentValidation.validate(List.of(glow));

        assertTrue(result.issues().isEmpty(), () -> "Expected no issues but got " + result.issues());
    }

    @Test
    void acceptsEffectParticlesUsingVanillaEffectVisual() {
        MutationComponentDefinition particles = component(
                "worldawakened:effect_particles",
                true,
                effectParticleParams("minecraft:strength"),
                List.of(),
                List.of());

        WorldAwakenedMutationComponentValidation.Result result =
                WorldAwakenedMutationComponentValidation.validate(List.of(particles));

        assertTrue(result.issues().isEmpty(), () -> "Expected no issues but got " + result.issues());
    }

    @Test
    void acceptsEffectParticlesWithColorOverride() {
        MutationComponentDefinition particles = component(
                "worldawakened:effect_particles",
                true,
                effectParticleParamsWithColor("minecraft:strength", "#66ff66"),
                List.of(),
                List.of());

        WorldAwakenedMutationComponentValidation.Result result =
                WorldAwakenedMutationComponentValidation.validate(List.of(particles));

        assertTrue(result.issues().isEmpty(), () -> "Expected no issues but got " + result.issues());
    }

    @Test
    void rejectsEffectParticlesUsingLegacyEffectKey() {
        JsonObject legacy = new JsonObject();
        legacy.addProperty("effect", "minecraft:strength");
        legacy.addProperty("count", 3);
        legacy.addProperty("interval_ticks", 10);
        MutationComponentDefinition particles = component(
                "worldawakened:effect_particles",
                true,
                legacy,
                List.of(),
                List.of());

        WorldAwakenedMutationComponentValidation.Result result =
                WorldAwakenedMutationComponentValidation.validate(List.of(particles));

        assertHasIssue(result, WorldAwakenedMutationComponentValidation.IssueKind.INVALID_COMPONENT_PARAMETERS);
    }

    @Test
    void rejectsAmbientParticlesUsingComplexParticleIdDirectly() {
        MutationComponentDefinition particles = component(
                "worldawakened:ambient_particles",
                true,
                rawAmbientParticleParams("minecraft:entity_effect"),
                List.of(),
                List.of());

        WorldAwakenedMutationComponentValidation.Result result =
                WorldAwakenedMutationComponentValidation.validate(List.of(particles));

        assertHasIssue(result, WorldAwakenedMutationComponentValidation.IssueKind.INVALID_COMPONENT_PARAMETERS);
    }

    @Test
    void acceptsAmbientParticlesUsingSimpleParticleId() {
        MutationComponentDefinition particles = component(
                "worldawakened:ambient_particles",
                true,
                rawAmbientParticleParams("minecraft:flame"),
                List.of(),
                List.of());

        WorldAwakenedMutationComponentValidation.Result result =
                WorldAwakenedMutationComponentValidation.validate(List.of(particles));

        assertTrue(result.issues().isEmpty(), () -> "Expected no issues but got " + result.issues());
    }

    @Test
    void acceptsAmbientDustWithColorAndSize() {
        MutationComponentDefinition particles = component(
                "worldawakened:ambient_particles",
                true,
                rawAmbientDustParams("#33ff66", 0.8D),
                List.of(),
                List.of());

        WorldAwakenedMutationComponentValidation.Result result =
                WorldAwakenedMutationComponentValidation.validate(List.of(particles));

        assertTrue(result.issues().isEmpty(), () -> "Expected no issues but got " + result.issues());
    }

    @Test
    void rejectsAmbientDustWithInvalidColor() {
        MutationComponentDefinition particles = component(
                "worldawakened:ambient_particles",
                true,
                rawAmbientDustParams("green", 0.8D),
                List.of(),
                List.of());

        WorldAwakenedMutationComponentValidation.Result result =
                WorldAwakenedMutationComponentValidation.validate(List.of(particles));

        assertHasIssue(result, WorldAwakenedMutationComponentValidation.IssueKind.INVALID_COMPONENT_PARAMETERS);
    }

    @Test
    void rejectsAmbientDustWithInvalidSize() {
        MutationComponentDefinition particles = component(
                "worldawakened:ambient_particles",
                true,
                rawAmbientDustParams("#33ff66", 5.0D),
                List.of(),
                List.of());

        WorldAwakenedMutationComponentValidation.Result result =
                WorldAwakenedMutationComponentValidation.validate(List.of(particles));

        assertHasIssue(result, WorldAwakenedMutationComponentValidation.IssueKind.INVALID_COMPONENT_PARAMETERS);
    }

    @Test
    void ignoresAmbientColorAndSizeForNonDustParticles() {
        JsonObject params = rawAmbientParticleParams("minecraft:flame");
        params.addProperty("color", "not_a_hex_color");
        params.addProperty("size", 9.0D);
        MutationComponentDefinition particles = component(
                "worldawakened:ambient_particles",
                true,
                params,
                List.of(),
                List.of());

        WorldAwakenedMutationComponentValidation.Result result =
                WorldAwakenedMutationComponentValidation.validate(List.of(particles));

        assertTrue(result.issues().isEmpty(), () -> "Expected no issues but got " + result.issues());
    }

    @Test
    void rejectsAmbientParticlesUsingEffectTypeField() {
        MutationComponentDefinition particles = component(
                "worldawakened:ambient_particles",
                true,
                effectParticleParams("minecraft:strength"),
                List.of(),
                List.of());

        WorldAwakenedMutationComponentValidation.Result result =
                WorldAwakenedMutationComponentValidation.validate(List.of(particles));

        assertHasIssue(result, WorldAwakenedMutationComponentValidation.IssueKind.INVALID_COMPONENT_PARAMETERS);
    }

    @Test
    void rejectsEffectParticlesUsingRawEmitterFields() {
        MutationComponentDefinition particles = component(
                "worldawakened:effect_particles",
                true,
                effectParticleParamsWithUnsupportedOffset("minecraft:strength"),
                List.of(),
                List.of());

        WorldAwakenedMutationComponentValidation.Result result =
                WorldAwakenedMutationComponentValidation.validate(List.of(particles));

        assertHasIssue(result, WorldAwakenedMutationComponentValidation.IssueKind.INVALID_COMPONENT_PARAMETERS);
    }

    private static MutationComponentDefinition component(
            String type,
            boolean enabled,
            JsonObject parameters,
            List<ResourceLocation> exclusions,
            List<ResourceLocation> conflictsWith) {
        return new MutationComponentDefinition(
                ResourceLocation.parse(type),
                enabled,
                0,
                parameters,
                List.of(),
                exclusions,
                conflictsWith);
    }

    private static JsonObject params() {
        return new JsonObject();
    }

    private static JsonObject params(String key, String value) {
        JsonObject object = new JsonObject();
        object.addProperty(key, value);
        return object;
    }

    private static JsonObject params(String key, Number value) {
        JsonObject object = new JsonObject();
        object.addProperty(key, value);
        return object;
    }

    private static JsonObject equipParams(String item, String slot) {
        JsonObject object = new JsonObject();
        object.addProperty("item", item);
        object.addProperty("slot", slot);
        return object;
    }

    private static JsonObject equipParamsWithEnchantment(String item, String enchantmentId, int level) {
        JsonObject object = new JsonObject();
        object.addProperty("item", item);
        JsonObject enchantment = new JsonObject();
        enchantment.addProperty("id", enchantmentId);
        enchantment.addProperty("level", level);
        com.google.gson.JsonArray enchantments = new com.google.gson.JsonArray();
        enchantments.add(enchantment);
        object.add("enchantments", enchantments);
        return object;
    }

    private static JsonObject effectParticleParams(String effectType) {
        JsonObject object = new JsonObject();
        object.addProperty("effect_type", effectType);
        object.addProperty("count", 3);
        object.addProperty("interval_ticks", 10);
        return object;
    }

    private static JsonObject effectParticleParamsWithColor(String effectType, String color) {
        JsonObject object = effectParticleParams(effectType);
        object.addProperty("color", color);
        return object;
    }

    private static JsonObject effectParticleParamsWithUnsupportedOffset(String effect) {
        JsonObject object = effectParticleParams(effect);
        object.addProperty("offset_x", 0.5D);
        return object;
    }

    private static JsonObject rawAmbientParticleParams(String particle) {
        JsonObject object = new JsonObject();
        object.addProperty("particle", particle);
        return object;
    }

    private static JsonObject rawAmbientDustParams(String color, double size) {
        JsonObject object = new JsonObject();
        object.addProperty("particle", "minecraft:dust");
        object.addProperty("color", color);
        object.addProperty("size", size);
        return object;
    }

    private static JsonObject glowStyleParams(
            String color,
            double brightness,
            boolean seeThroughWalls,
            boolean pulse,
            double pulseSpeed,
            double pulseStrength) {
        JsonObject object = new JsonObject();
        object.addProperty("color", color);
        object.addProperty("brightness", brightness);
        object.addProperty("see_through_walls", seeThroughWalls);
        object.addProperty("pulse", pulse);
        object.addProperty("pulse_speed", pulseSpeed);
        object.addProperty("pulse_strength", pulseStrength);
        return object;
    }

    private static void assertHasIssue(
            WorldAwakenedMutationComponentValidation.Result result,
            WorldAwakenedMutationComponentValidation.IssueKind expected) {
        assertTrue(result.issues().stream().anyMatch(issue -> issue.kind() == expected),
                () -> "Expected issue " + expected + " but got " + result.issues());
    }
}
