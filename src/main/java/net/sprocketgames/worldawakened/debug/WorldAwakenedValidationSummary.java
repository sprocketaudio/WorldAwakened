package net.sprocketgames.worldawakened.debug;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.resources.ResourceLocation;
import net.sprocketgames.worldawakened.rules.runtime.WorldAwakenedRejectionReason;
import net.sprocketgames.worldawakened.rules.runtime.WorldAwakenedRuntimeLayer;

public record WorldAwakenedValidationSummary(
        Instant generatedAt,
        int errorCount,
        int warningCount,
        int infoCount,
        Map<String, Integer> loadedByType,
        Map<String, Integer> disabledByType,
        List<WorldAwakenedDiagnostic> diagnostics,
        List<WorldAwakenedTraceEvent> traceEvents) {
    public static WorldAwakenedValidationSummary empty() {
        return new WorldAwakenedValidationSummary(
                Instant.EPOCH,
                0,
                0,
                0,
                Collections.emptyMap(),
                Collections.emptyMap(),
                Collections.emptyList(),
                Collections.emptyList());
    }

    public String toCompactString() {
        return "errors=" + errorCount + ", warnings=" + warningCount + ", info=" + infoCount + ", loaded="
                + loadedByType + ", disabled=" + disabledByType + ", traces=" + traceEvents.size();
    }

    public static final class Builder {
        private int errors;
        private int warnings;
        private int infos;
        private final Map<String, Integer> loadedByType = new LinkedHashMap<>();
        private final Map<String, Integer> disabledByType = new LinkedHashMap<>();
        private final java.util.ArrayList<WorldAwakenedDiagnostic> diagnostics = new java.util.ArrayList<>();
        private final java.util.ArrayList<WorldAwakenedTraceEvent> traceEvents = new java.util.ArrayList<>();
        private final java.util.HashSet<String> errorKeys = new java.util.HashSet<>();

        public void addDiagnostic(WorldAwakenedDiagnostic diagnostic) {
            diagnostics.add(diagnostic);
            switch (diagnostic.severity()) {
                case ERROR -> {
                    errors++;
                    errorKeys.add(key(diagnostic.objectType(), diagnostic.objectId(), diagnostic.sourcePath()));
                }
                case WARNING -> warnings++;
                case INFO -> infos++;
            }
        }

        public boolean hasErrorFor(String objectType, net.minecraft.resources.ResourceLocation objectId, String sourcePath) {
            return errorKeys.contains(key(objectType, objectId, sourcePath));
        }

        public void addTrace(WorldAwakenedRuntimeLayer layer, String objectType, ResourceLocation objectId, String sourcePath, String detail) {
            traceEvents.add(new WorldAwakenedTraceEvent(
                    Instant.now(),
                    layer,
                    objectType,
                    objectId,
                    sourcePath,
                    detail,
                    Optional.empty()));
        }

        public void addRejectedTrace(
                WorldAwakenedRuntimeLayer layer,
                String objectType,
                ResourceLocation objectId,
                String sourcePath,
                WorldAwakenedRejectionReason reason,
                String detail) {
            traceEvents.add(new WorldAwakenedTraceEvent(
                    Instant.now(),
                    layer,
                    objectType,
                    objectId,
                    sourcePath,
                    detail,
                    Optional.of(reason)));
        }

        private static String key(String objectType, net.minecraft.resources.ResourceLocation objectId, String sourcePath) {
            String id = objectId == null ? "<none>" : objectId.toString();
            String source = sourcePath == null ? "<none>" : sourcePath;
            return objectType + "|" + id + "|" + source;
        }

        public void incrementLoaded(String type) {
            loadedByType.merge(type, 1, Integer::sum);
        }

        public void incrementDisabled(String type) {
            disabledByType.merge(type, 1, Integer::sum);
        }

        public WorldAwakenedValidationSummary build() {
            return new WorldAwakenedValidationSummary(
                    Instant.now(),
                    errors,
                    warnings,
                    infos,
                    Map.copyOf(loadedByType),
                    Map.copyOf(disabledByType),
                    List.copyOf(diagnostics),
                    List.copyOf(traceEvents));
        }
    }
}

