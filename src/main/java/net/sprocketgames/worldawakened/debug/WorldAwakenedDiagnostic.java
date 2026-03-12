package net.sprocketgames.worldawakened.debug;

import net.minecraft.resources.ResourceLocation;

public record WorldAwakenedDiagnostic(
        WorldAwakenedDiagnosticSeverity severity,
        String code,
        String objectType,
        ResourceLocation objectId,
        String sourcePath,
        String message,
        String resolutionPath) {
    public String asLogLine() {
        String id = objectId == null ? "<none>" : objectId.toString();
        String source = sourcePath == null ? "<unknown>" : sourcePath;
        String resolution = resolutionPath == null ? "none" : resolutionPath;
        return "[" + severity + "] " + code + " type=" + objectType + " id=" + id + " source=" + source
                + " message=\"" + message + "\" resolution=\"" + resolution + "\"";
    }
}

