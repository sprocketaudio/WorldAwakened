package net.sprocketgames.worldawakened.config;

public final class WorldAwakenedEditorListLayout {
    private WorldAwakenedEditorListLayout() {}

    public static int visibleRows(int viewportHeight, int rowHeight) {
        if (rowHeight <= 0) throw new IllegalArgumentException("rowHeight must be positive");
        return Math.max(1, viewportHeight / rowHeight);
    }

    public static int clampOffset(int offset, int totalRows, int visibleRows) {
        return Math.max(0, Math.min(Math.max(0, totalRows - visibleRows), offset));
    }

    public static boolean needsScrollbar(int totalRows, int visibleRows) {
        return totalRows > visibleRows;
    }
}
