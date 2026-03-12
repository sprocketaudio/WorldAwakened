package net.sprocketgames.worldawakened.debug;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;
import net.sprocketgames.worldawakened.rules.runtime.WorldAwakenedRejectionReason;
import net.sprocketgames.worldawakened.rules.runtime.WorldAwakenedRuntimeLayer;

class WorldAwakenedValidationSummaryTest {
    @Test
    void tracksSeverityAndTypeCounts() {
        WorldAwakenedValidationSummary.Builder builder = new WorldAwakenedValidationSummary.Builder();
        builder.incrementLoaded("stages");
        builder.incrementDisabled("rules");
        builder.addDiagnostic(new WorldAwakenedDiagnostic(
                WorldAwakenedDiagnosticSeverity.ERROR,
                WorldAwakenedDiagnosticCodes.CODEC_PARSE_FAILED,
                "stages",
                ResourceLocation.fromNamespaceAndPath("testpack", "broken_stage"),
                "testpath",
                "Decode failed",
                "disabled_object"));
        builder.addDiagnostic(new WorldAwakenedDiagnostic(
                WorldAwakenedDiagnosticSeverity.WARNING,
                WorldAwakenedDiagnosticCodes.INVALID_REFERENCE,
                "rules",
                ResourceLocation.fromNamespaceAndPath("testpack", "rule"),
                "testpath2",
                "Warning",
                "retained"));
        builder.addTrace(
                WorldAwakenedRuntimeLayer.STATIC_DATA_LOAD,
                "stages",
                ResourceLocation.fromNamespaceAndPath("testpack", "broken_stage"),
                "testpath",
                "load_begin");
        builder.addRejectedTrace(
                WorldAwakenedRuntimeLayer.STATIC_DATA_LOAD,
                "stages",
                ResourceLocation.fromNamespaceAndPath("testpack", "broken_stage"),
                "testpath",
                WorldAwakenedRejectionReason.INVALID_REFERENCED_OBJECT,
                "validation_failed");

        WorldAwakenedValidationSummary summary = builder.build();
        assertEquals(1, summary.errorCount());
        assertEquals(1, summary.warningCount());
        assertEquals(1, summary.loadedByType().get("stages"));
        assertEquals(1, summary.disabledByType().get("rules"));
        assertEquals(2, summary.traceEvents().size());
        assertTrue(summary.toCompactString().contains("errors=1"));
        assertTrue(summary.toCompactString().contains("traces=2"));
    }
}

