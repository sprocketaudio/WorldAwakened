package net.sprocketgames.worldawakened.data.codec;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.sprocketgames.worldawakened.rules.runtime.WorldAwakenedRuleEngine;

class WorldAwakenedExampleDatapackShapeTest {
    private static final Path EMBEDDED_ROOT = Path.of("src", "main", "resources", "data", "worldawakened");
    private static final Path EXAMPLE_ROOT = Path.of("example_datapacks", "worldawakened_example_pack", "data", "worldawakened");
    private static final Path DEV_PHASE2_ROOT = Path.of("dev_datapacks", "worldawakened_phase2_dev", "data", "worldawakened", "worldawakened");
    private static final Path DEV_PHASE4_ROOT = Path.of("dev_datapacks", "worldawakened_phase4_test", "data");

    private static final Set<String> TRIGGER_CONDITION_PATHS = Set.of(
            "stage_unlocked",
            "stage_locked",
            "current_dimension",
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
    void modJarDoesNotEmbedGameplayDatapackContent() throws IOException {
        LinkedHashSet<Path> embeddedFiles = new LinkedHashSet<>();
        collectJsonFiles(EMBEDDED_ROOT, embeddedFiles);
        assertTrue(
                embeddedFiles.isEmpty(),
                "The framework jar must not embed gameplay datapack content under src/main/resources/data/worldawakened.\n"
                        + String.join("\n", embeddedFiles.stream().map(Path::toString).toList()));
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
        collectJsonFiles(EXAMPLE_ROOT, files);
        collectJsonFiles(DEV_PHASE2_ROOT, files);
        collectJsonFiles(DEV_PHASE4_ROOT, files);
        return List.copyOf(files);
    }

    private static void collectJsonFiles(Path root, Set<Path> out) throws IOException {
        if (!Files.isDirectory(root)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".json"))
                    .forEach(out::add);
        }
    }
}
