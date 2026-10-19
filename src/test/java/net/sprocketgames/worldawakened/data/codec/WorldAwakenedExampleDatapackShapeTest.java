package net.sprocketgames.worldawakened.data.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;

import net.sprocketgames.worldawakened.data.definition.MobMutatorDefinition;
import net.sprocketgames.worldawakened.data.definition.MutationPoolDefinition;
import net.sprocketgames.worldawakened.data.definition.AscensionOfferDefinition;
import net.sprocketgames.worldawakened.data.definition.AscensionRewardDefinition;
import net.sprocketgames.worldawakened.data.definition.InvasionProfileDefinition;
import net.sprocketgames.worldawakened.data.definition.LootProfileDefinition;
import net.sprocketgames.worldawakened.data.definition.StageDefinition;
import net.sprocketgames.worldawakened.data.definition.TriggerRuleDefinition;
import net.sprocketgames.worldawakened.rarity.RarityDefinition;
import net.sprocketgames.worldawakened.generation.ConfiguredEffectDefinition;
import net.sprocketgames.worldawakened.generation.EffectGenerationPoolDefinition;
import net.sprocketgames.worldawakened.mutator.component.WorldAwakenedMutationComponentValidation;
import net.sprocketgames.worldawakened.rules.runtime.WorldAwakenedRuleEngine;

class WorldAwakenedExampleDatapackShapeTest {
    private static final Path PROJECT_ROOT = findProjectRoot();
    private static final Path EMBEDDED_ROOT = PROJECT_ROOT.resolve(Path.of("src", "main", "resources", "data", "worldawakened"));
    private static final Path EXAMPLE_ROOT = PROJECT_ROOT.resolve(Path.of("example_datapacks", "worldawakened_example_pack", "data", "worldawakened"));
    private static final Path DEV_PHASE2_ROOT = PROJECT_ROOT.resolve(Path.of("dev_datapacks", "worldawakened_phase2_dev", "data", "worldawakened", "worldawakened"));
    private static final Path DEV_PHASE4_ROOT = PROJECT_ROOT.resolve(Path.of("dev_datapacks", "worldawakened_phase4_test", "data"));
    private static final Set<String> LIVE_DEFAULT_COMPONENTS = Set.of(
            "max_health_bonus",
            "max_health_multiplier",
            "attack_damage_multiplier",
            "armor_bonus",
            "movement_speed_multiplier",
            "follow_range_bonus",
            "target_range_bonus",
            "pursuit_speed_boost",
            "knockback_resistance_bonus",
            "temporary_shield",
            "on_hit_effect",
            "life_steal",
            "death_explosion",
            "glow_style",
            "effect_particles",
            "ambient_particles");
    private static final Map<String, Double> EXPECTED_STANDARD_POOL_CHANCES = Map.ofEntries(
            Map.entry("overworld_intro.json", 0.02D),
            Map.entry("s2_overworld.json", 0.04D),
            Map.entry("s3_overworld.json", 0.06D),
            Map.entry("nether_escalation.json", 0.08D),
            Map.entry("s4_overworld.json", 0.08D),
            Map.entry("s4_nether.json", 0.11D),
            Map.entry("s5_overworld.json", 0.10D),
            Map.entry("s5_nether.json", 0.13D),
            Map.entry("end_escalation.json", 0.14D),
            Map.entry("s6_overworld.json", 0.13D),
            Map.entry("s6_nether.json", 0.16D),
            Map.entry("s6_end.json", 0.18D),
            Map.entry("invasion_restless_night.json", 0.18D),
            Map.entry("invasion_nether_surge.json", 0.20D),
            Map.entry("invasion_awakened_hunt.json", 0.22D));

    private static final Set<String> TRIGGER_CONDITION_PATHS = Set.of(
            "stage_unlocked",
            "stage_locked",
            "current_dimension",
            "world_day_gte",
            "advancement_completed",
            "entity_type",
            "entity_tag",
            "manual_trigger",
            "boss_killed");

    private static final Set<String> TRIGGER_ACTION_PATHS = Set.of(
            "unlock_stage",
            "lock_stage",
            "emit_named_event",
            "increment_counter",
            "send_warning_message",
            "grant_ascension_offer");

    @Test
    void modJarEmbedsAConservativeStandardGameplayProfile() throws IOException {
        LinkedHashSet<Path> embeddedFiles = new LinkedHashSet<>();
        collectJsonFiles(EMBEDDED_ROOT, embeddedFiles);
        assertEquals(66, embeddedFiles.size(), "The bundled Standard profile object count must remain intentional");
        assertEquals(7, countFilesUnder(embeddedFiles, "/stages/"));
        assertEquals(6, countFilesUnder(embeddedFiles, "/trigger_rules/"));
        assertEquals(11, countFilesUnder(embeddedFiles, "/mob_mutators/"));
        assertEquals(15, countFilesUnder(embeddedFiles, "/mutation_pools/"));
        assertEquals(9, countFilesUnder(embeddedFiles, "/ascension_rewards/"));
        assertEquals(3, countFilesUnder(embeddedFiles, "/ascension_offers/"));
        assertEquals(3, countFilesUnder(embeddedFiles, "/loot_profiles/"));
        assertEquals(3, countFilesUnder(embeddedFiles, "/invasion_profiles/"));
        assertEquals(5, countFilesUnder(embeddedFiles, "/rarities/"));
        assertEquals(3, countFilesUnder(embeddedFiles, "/configured_effects/"));
        assertEquals(1, countFilesUnder(embeddedFiles, "/effect_generation_pools/"));

        for (Path file : embeddedFiles) {
            JsonObject root = parseRoot(file);
            assertTrue(root != null && root.has("id"), () -> "Bundled object must have an id: " + file);
            assertTrue(root.get("id").getAsString().startsWith("worldawakened:defaults/"),
                    () -> "Bundled gameplay IDs must remain in the replaceable defaults family: " + file);
            assertBundledObjectDecodes(file, root);
            if (file.toString().replace('\\', '/').contains("/mutation_pools/")) {
                double mutationChance = root.get("mutation_chance").getAsDouble();
                assertEquals(
                        EXPECTED_STANDARD_POOL_CHANCES.get(file.getFileName().toString()).doubleValue(),
                        mutationChance,
                        0.0000001D,
                        () -> "Bundled pool must use its one final stage/dimension rate: " + file);
                boolean multiPool = root.get("max_mutators_per_entity").getAsInt() > 1;
                boolean invasionPool = file.getFileName().toString().startsWith("invasion_");
                assertTrue(root.has("additional_mutation_chance"),
                        () -> "Unified pools must define their conditional additional roll: " + file);
                if (multiPool && !invasionPool) {
                    assertEquals("curated", root.get("combination_mode").getAsString());
                    assertEquals(1, root.getAsJsonArray("rare_combinations").size());
                }
                if (!invasionPool) {
                    assertEquals(100, mutatorWeightTotal(root),
                            () -> "Each ranked unified pool keeps an explicit 100-point rarity table: " + file);
                }
                assertTrue(!root.get("allow_from_spawner").getAsBoolean()
                                && !root.get("allow_from_trial_spawner").getAsBoolean(),
                        () -> "Bundled pools must exclude farm spawn sources: " + file);
                JsonArray conditions = root.getAsJsonArray("conditions");
                assertEquals(invasionPool ? 0 : 1, countTypedNodes(conditions, "stage_unlocked"),
                        () -> "Ranked pools identify one stage; invasion pools identify active invasion state: " + file);
                assertEquals(invasionPool ? 1 : 0, countTypedNodes(conditions, "invasion_active"),
                        () -> "Only invasion pools should be gated by active invasion profile: " + file);
                if (!invasionPool) {
                    boolean s6 = file.getFileName().toString().startsWith("s6_");
                    assertEquals(s6 ? 0 : 1, countTypedNodes(conditions, "stage_locked"),
                            () -> "Pre-S6 pools must be excluded by the next ranked stage: " + file);
                }
                assertEquals(0, countTypedNodes(conditions, "random_chance"),
                        () -> "Pool mutation_chance must be the only probability gate: " + file);
            }
            if (file.toString().replace('\\', '/').contains("/mob_mutators/")) {
                for (JsonElement componentElement : root.getAsJsonArray("components")) {
                    String componentPath = typePath(componentElement.getAsJsonObject().get("type").getAsString());
                    assertTrue(LIVE_DEFAULT_COMPONENTS.contains(componentPath),
                            () -> "Bundled defaults may only use verified live component surfaces: " + componentPath);
                }
            }
        }
    }

    @Test
    void standardCombatAbilitiesEnterAtIntendedStagesAndCuratedCombinations() throws IOException {
        JsonObject s2 = bundledPool("s2_overworld.json");
        assertTrue(hasMutator(s2, "worldawakened:defaults/hindering"));
        assertTrue(!hasMutator(s2, "worldawakened:defaults/vampiric"));
        assertTrue(!hasMutator(s2, "worldawakened:defaults/volatile"));

        JsonObject s3 = bundledPool("s3_overworld.json");
        assertTrue(hasMutator(s3, "worldawakened:defaults/hindering"));
        assertTrue(hasMutator(s3, "worldawakened:defaults/vampiric"));
        assertTrue(!hasMutator(s3, "worldawakened:defaults/volatile"));

        JsonObject s4 = bundledPool("s4_overworld.json");
        assertTrue(hasMutator(s4, "worldawakened:defaults/volatile"));
        assertEquals(2, mutatorWeight(s4, "worldawakened:defaults/volatile"));

        assertCuratedCombination("s4_overworld.json", 0.02D,
                "worldawakened:defaults/hunter", "worldawakened:defaults/bulwark");
        assertCuratedCombination("s4_nether.json", 0.02D,
                "worldawakened:defaults/hunter", "worldawakened:defaults/nether_hardened");
        assertCuratedCombination("s5_overworld.json", 0.05D,
                "worldawakened:defaults/vampiric", "worldawakened:defaults/bulwark");
        assertCuratedCombination("s5_nether.json", 0.05D,
                "worldawakened:defaults/volatile", "worldawakened:defaults/nether_hardened");
        assertCuratedCombination("end_escalation.json", 0.05D,
                "worldawakened:defaults/hindering", "worldawakened:defaults/void_hardened");
        assertCuratedCombination("s6_overworld.json", 0.09D,
                "worldawakened:defaults/hindering", "worldawakened:defaults/vampiric");
        assertCuratedCombination("s6_nether.json", 0.09D,
                "worldawakened:defaults/hunter", "worldawakened:defaults/vampiric");
        assertCuratedCombination("s6_end.json", 0.09D,
                "worldawakened:defaults/volatile", "worldawakened:defaults/void_hardened");
    }

    @Test
    void standardLootProfilesUseTheirIntendedActivePhaseBands() throws IOException {
        assertLootPhaseBand("early_mutation_bonus.json", 0.10D,
                "worldawakened:defaults/stirring", "worldawakened:defaults/unease");
        assertLootPhaseBand("midgame_mutation_bonus.json", 0.15D,
                "worldawakened:defaults/nether_reached", "worldawakened:defaults/gathering_storm");
        assertLootPhaseBand("late_mutation_bonus.json", 0.20D,
                "worldawakened:defaults/end_reached", "worldawakened:defaults/world_awakened");
    }

    @Test
    void everyBundledExampleAndDevMutationPoolUsesTheUnifiedSchema() throws IOException {
        List<Path> roots = List.of(
                PROJECT_ROOT.resolve("src/main/resources/data"),
                PROJECT_ROOT.resolve("example_datapacks"),
                PROJECT_ROOT.resolve("dev_datapacks"));
        List<Path> pools = new ArrayList<>();
        for (Path root : roots) {
            if (!Files.exists(root)) continue;
            try (Stream<Path> files = Files.walk(root)) {
                files.filter(Files::isRegularFile)
                        .filter(path -> path.toString().replace('\\', '/').contains("/mutation_pools/"))
                        .filter(path -> path.toString().endsWith(".json"))
                        .forEach(pools::add);
            }
        }
        assertTrue(!pools.isEmpty());
        for (Path pool : pools) {
            JsonObject root = parseRoot(pool);
            assertTrue(root.has("mutation_chance"), () -> "Missing required initial chance: " + pool);
            assertTrue(root.has("additional_mutation_chance"), () -> "Missing required additional chance: " + pool);
            assertTrue(root.has("max_mutators_per_entity"), () -> "Missing required maximum: " + pool);
            var decoded = MutationPoolDefinition.CODEC.parse(JsonOps.INSTANCE, root);
            assertTrue(decoded.result().isPresent(), () -> "Unified mutation pool must decode: " + pool + " -> "
                    + decoded.error().map(error -> error.message()).orElse("unknown codec failure"));
        }
    }

    @Test
    void strictTypedNodesUseCanonicalTypePlusParametersShape() throws IOException {
        List<String> issues = new ArrayList<>();
        for (Path file : jsonFiles()) {
            JsonObject root = parseRoot(file);
            if (root == null) {
                continue;
            }

            validateTypedArrayShape(file, "conditions", root.get("conditions"), issues);
            validateTypedArrayShape(file, "actions", root.get("actions"), issues);
            validateTypedArrayShape(file, "required_conditions", root.get("required_conditions"), issues);
            validateTypedArrayShape(file, "forbidden_conditions", root.get("forbidden_conditions"), issues);
            validateTypedArrayShape(file, "trigger_conditions", root.get("trigger_conditions"), issues);

            JsonElement componentsElement = root.get("components");
            if (componentsElement != null && componentsElement.isJsonArray()) {
                JsonArray components = componentsElement.getAsJsonArray();
                for (int componentIndex = 0; componentIndex < components.size(); componentIndex++) {
                    JsonElement rawComponent = components.get(componentIndex);
                    if (!rawComponent.isJsonObject()) {
                        continue;
                    }
                    JsonObject component = rawComponent.getAsJsonObject();
                    validateTypedArrayShape(
                            file,
                            "components[" + componentIndex + "].conditions",
                            component.get("conditions"),
                            issues);
                }
            }
        }

        assertTrue(
                issues.isEmpty(),
                "Example/dev datapack typed nodes must use canonical `type` + `parameters` object shape.\n"
                        + String.join("\n", issues));
    }

    @Test
    void strictValidationObjectTypesUseSupportedConditionAndActionIds() throws IOException {
        Set<String> ruleConditionPaths = WorldAwakenedRuleEngine.supportedConditionPaths();
        Set<String> ruleActionPaths = WorldAwakenedRuleEngine.supportedActionPaths();
        List<String> issues = new ArrayList<>();

        for (Path file : jsonFiles()) {
            JsonObject root = parseRoot(file);
            if (root == null) {
                continue;
            }

            String normalizedPath = "/" + file.toString().replace('\\', '/').toLowerCase() + "/";
            if (normalizedPath.contains("/rules/")) {
                validateTypedArrayPaths(file, "conditions", root.get("conditions"), ruleConditionPaths, issues);
                validateTypedArrayPaths(file, "actions", root.get("actions"), ruleActionPaths, issues);
            }
            if (normalizedPath.contains("/trigger_rules/")) {
                validateTypedArrayPaths(file, "conditions", root.get("conditions"), TRIGGER_CONDITION_PATHS, issues);
                validateTypedArrayPaths(file, "actions", root.get("actions"), TRIGGER_ACTION_PATHS, issues);
            }
            if (normalizedPath.contains("/mob_mutators/")) {
                validateTypedArrayPaths(file, "required_conditions", root.get("required_conditions"), ruleConditionPaths, issues);
                JsonElement componentsElement = root.get("components");
                if (componentsElement != null && componentsElement.isJsonArray()) {
                    JsonArray components = componentsElement.getAsJsonArray();
                    for (int componentIndex = 0; componentIndex < components.size(); componentIndex++) {
                        JsonElement rawComponent = components.get(componentIndex);
                        if (!rawComponent.isJsonObject()) {
                            continue;
                        }
                        JsonObject component = rawComponent.getAsJsonObject();
                        validateTypedArrayPaths(
                                file,
                                "components[" + componentIndex + "].conditions",
                                component.get("conditions"),
                                ruleConditionPaths,
                                issues);
                    }
                }
            }
            if (normalizedPath.contains("/ascension_rewards/")) {
                validateTypedArrayPaths(file, "requires_conditions", root.get("requires_conditions"), ruleConditionPaths, issues);
                validateTypedArrayPaths(file, "forbidden_conditions", root.get("forbidden_conditions"), ruleConditionPaths, issues);
                JsonElement componentsElement = root.get("components");
                if (componentsElement != null && componentsElement.isJsonArray()) {
                    JsonArray components = componentsElement.getAsJsonArray();
                    for (int componentIndex = 0; componentIndex < components.size(); componentIndex++) {
                        JsonElement rawComponent = components.get(componentIndex);
                        if (!rawComponent.isJsonObject()) {
                            continue;
                        }
                        JsonObject component = rawComponent.getAsJsonObject();
                        validateTypedArrayPaths(
                                file,
                                "components[" + componentIndex + "].conditions",
                                component.get("conditions"),
                                ruleConditionPaths,
                                issues);
                    }
                }
            }
        }

        assertTrue(
                issues.isEmpty(),
                "Example/dev datapack strict-validation object types must only use supported condition/action IDs.\n"
                        + String.join("\n", issues));
    }

    private static void validateTypedArrayShape(Path file, String field, JsonElement rawArray, List<String> issues) {
        if (rawArray == null || !rawArray.isJsonArray()) {
            return;
        }
        JsonArray array = rawArray.getAsJsonArray();
        for (int index = 0; index < array.size(); index++) {
            JsonElement rawNode = array.get(index);
            if (!rawNode.isJsonObject()) {
                continue;
            }
            JsonObject node = rawNode.getAsJsonObject();
            if (!node.has("type")) {
                continue;
            }
            if (!node.has("parameters") || !node.get("parameters").isJsonObject()) {
                issues.add(formatIssue(file, field, index, "missing or non-object `parameters`"));
            }
        }
    }

    private static void validateTypedArrayPaths(
            Path file,
            String field,
            JsonElement rawArray,
            Set<String> supportedPaths,
            List<String> issues) {
        if (rawArray == null || !rawArray.isJsonArray()) {
            return;
        }
        JsonArray array = rawArray.getAsJsonArray();
        for (int index = 0; index < array.size(); index++) {
            JsonElement rawNode = array.get(index);
            if (!rawNode.isJsonObject()) {
                continue;
            }
            JsonObject node = rawNode.getAsJsonObject();
            if (!node.has("type") || !node.get("type").isJsonPrimitive()) {
                continue;
            }
            String path = typePath(node.get("type").getAsString());
            if (!supportedPaths.contains(path)) {
                issues.add(formatIssue(file, field, index, "unsupported type path `" + path + "`"));
            }
        }
    }

    private static String typePath(String typeId) {
        int separator = typeId.indexOf(':');
        if (separator < 0 || separator == typeId.length() - 1) {
            return typeId.toLowerCase();
        }
        return typeId.substring(separator + 1).toLowerCase();
    }

    private static String formatIssue(Path file, String field, int index, String detail) {
        return file + " -> " + field + "[" + index + "]: " + detail;
    }

    private static JsonObject parseRoot(Path file) throws IOException {
        JsonElement root = JsonParser.parseString(Files.readString(file));
        return root.isJsonObject() ? root.getAsJsonObject() : null;
    }

    private static List<Path> jsonFiles() throws IOException {
        LinkedHashSet<Path> files = new LinkedHashSet<>();
        collectJsonFiles(EMBEDDED_ROOT, files);
        collectJsonFiles(EXAMPLE_ROOT, files);
        collectJsonFiles(DEV_PHASE2_ROOT, files);
        collectJsonFiles(DEV_PHASE4_ROOT, files);
        return List.copyOf(files);
    }

    private static long countFilesUnder(Set<Path> files, String normalizedSegment) {
        return files.stream()
                .filter(file -> file.toString().replace('\\', '/').contains(normalizedSegment))
                .count();
    }

    private static long countTypedNodes(JsonArray nodes, String expectedPath) {
        long count = 0L;
        for (JsonElement node : nodes) {
            if (node.isJsonObject()
                    && node.getAsJsonObject().has("type")
                    && expectedPath.equals(typePath(node.getAsJsonObject().get("type").getAsString()))) {
                count++;
            }
        }
        return count;
    }

    private static JsonObject bundledPool(String fileName) throws IOException {
        return parseRoot(EMBEDDED_ROOT.resolve(Path.of("mutation_pools", "defaults", fileName)));
    }

    private static void assertLootPhaseBand(String fileName, double chance, String... phaseIds) throws IOException {
        JsonObject profile = parseRoot(EMBEDDED_ROOT.resolve(Path.of("loot_profiles", "defaults", fileName)));
        assertEquals(chance, profile.getAsJsonObject("authoring").get("item_chance").getAsDouble(), 0.0000001D);
        assertEquals(List.of(phaseIds), profile.getAsJsonObject("stage_filters").getAsJsonArray("required_any")
                .asList().stream().map(JsonElement::getAsString).toList());
        assertEquals(0, countTypedNodes(profile.getAsJsonArray("conditions"), "stage_unlocked"));
        assertEquals(0, countTypedNodes(profile.getAsJsonArray("conditions"), "stage_locked"));
    }

    private static boolean hasMutator(JsonObject pool, String id) {
        return mutatorWeight(pool, id) > 0;
    }

    private static int mutatorWeight(JsonObject pool, String id) {
        for (JsonElement raw : pool.getAsJsonArray("mutators")) {
            JsonObject mutator = raw.getAsJsonObject();
            if (id.equals(mutator.get("id").getAsString())) {
                return mutator.get("weight").getAsInt();
            }
        }
        return 0;
    }

    private static int mutatorWeightTotal(JsonObject pool) {
        int total = 0;
        for (JsonElement raw : pool.getAsJsonArray("mutators")) {
            total += raw.getAsJsonObject().get("weight").getAsInt();
        }
        return total;
    }

    private static void assertCuratedCombination(
            String fileName, double additionalChance, String first, String second) throws IOException {
        JsonObject pool = bundledPool(fileName);
        assertEquals(2, pool.get("max_mutators_per_entity").getAsInt());
        assertEquals(additionalChance, pool.get("additional_mutation_chance").getAsDouble(), 0.0000001D);
        JsonObject combination = pool.getAsJsonArray("rare_combinations").get(0).getAsJsonObject();
        List<String> mutators = new ArrayList<>();
        combination.getAsJsonArray("mutators").forEach(element -> mutators.add(element.getAsString()));
        assertEquals(List.of(first, second), mutators,
                () -> fileName + " must preserve its authored curated combination");
        assertTrue(combination.getAsJsonArray("eligible_entities").size() > 0,
                () -> fileName + " must retain the former pair pool's narrower mob eligibility");
    }

    private static void assertBundledObjectDecodes(Path file, JsonObject root) {
        String normalized = file.toString().replace('\\', '/');
        Codec<?> codec;
        if (normalized.contains("/stages/")) {
            codec = StageDefinition.CODEC;
        } else if (normalized.contains("/trigger_rules/")) {
            codec = TriggerRuleDefinition.CODEC;
        } else if (normalized.contains("/mob_mutators/")) {
            codec = MobMutatorDefinition.CODEC;
        } else if (normalized.contains("/mutation_pools/")) {
            codec = MutationPoolDefinition.CODEC;
        } else if (normalized.contains("/ascension_rewards/")) {
            codec = AscensionRewardDefinition.CODEC;
        } else if (normalized.contains("/ascension_offers/")) {
            codec = AscensionOfferDefinition.CODEC;
        } else if (normalized.contains("/loot_profiles/")) {
            codec = LootProfileDefinition.CODEC;
        } else if (normalized.contains("/invasion_profiles/")) {
            codec = InvasionProfileDefinition.CODEC;
        } else if (normalized.contains("/rarities/")) {
            codec = RarityDefinition.CODEC;
        } else if (normalized.contains("/configured_effects/")) {
            codec = ConfiguredEffectDefinition.CODEC;
        } else if (normalized.contains("/effect_generation_pools/")) {
            codec = EffectGenerationPoolDefinition.CODEC;
        } else {
            throw new AssertionError("Unexpected bundled object path: " + file);
        }
        var result = codec.parse(JsonOps.INSTANCE, root);
        assertTrue(result.result().isPresent(), () -> "Bundled object must decode: " + file + " -> "
                + result.error().map(error -> error.message()).orElse("unknown codec failure"));
        if (normalized.contains("/mob_mutators/") && result.result().isPresent()) {
            MobMutatorDefinition definition = MobMutatorDefinition.CODEC.parse(JsonOps.INSTANCE, root)
                    .result().orElseThrow();
            var validation = WorldAwakenedMutationComponentValidation.validate(definition.components());
            assertTrue(validation.issues().isEmpty(), () -> "Bundled mutator components must validate: " + file
                    + " -> " + validation.issues());
        }
    }

    private static void collectJsonFiles(Path root, Set<Path> out) throws IOException {
        if (!Files.isDirectory(root)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".json"))
                    .filter(path -> !path.toString().contains("loot_modifiers"))
                    .forEach(out::add);
        }
    }

    private static Path findProjectRoot() {
        Path candidate = Path.of("").toAbsolutePath().normalize();
        while (candidate != null) {
            if (Files.isRegularFile(candidate.resolve("settings.gradle"))
                    || Files.isRegularFile(candidate.resolve("settings.gradle.kts"))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException("Could not locate the World Awakened project root from "
                + Path.of("").toAbsolutePath());
    }
}
