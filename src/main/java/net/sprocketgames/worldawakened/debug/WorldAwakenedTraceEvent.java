package net.sprocketgames.worldawakened.debug;

import java.time.Instant;
import java.util.Optional;

import net.minecraft.resources.ResourceLocation;
import net.sprocketgames.worldawakened.rules.runtime.WorldAwakenedRejectionReason;
import net.sprocketgames.worldawakened.rules.runtime.WorldAwakenedRuntimeLayer;

public record WorldAwakenedTraceEvent(
        Instant timestamp,
        WorldAwakenedRuntimeLayer layer,
        String objectType,
        ResourceLocation objectId,
        String sourcePath,
        String detail,
        Optional<WorldAwakenedRejectionReason> rejectionReason) {
    public String asLogLine() {
        String id = objectId == null ? "<none>" : objectId.toString();
        String source = sourcePath == null ? "<unknown>" : sourcePath;
        String reason = rejectionReason.map(Enum::name).orElse("none");
        return "layer=" + layer.name() + " type=" + objectType + " id=" + id + " source=" + source
                + " reason=" + reason + " detail=\"" + detail + "\"";
    }
}

