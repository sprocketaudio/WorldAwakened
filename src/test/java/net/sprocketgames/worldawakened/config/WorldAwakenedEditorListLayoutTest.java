package net.sprocketgames.worldawakened.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WorldAwakenedEditorListLayoutTest {
    @Test
    void sixRowsUseAvailableHeightWithoutScrolling() {
        int visible = WorldAwakenedEditorListLayout.visibleRows(180, 30);
        assertEquals(6, visible);
        assertFalse(WorldAwakenedEditorListLayout.needsScrollbar(1, visible));
        assertFalse(WorldAwakenedEditorListLayout.needsScrollbar(3, visible));
        assertFalse(WorldAwakenedEditorListLayout.needsScrollbar(6, visible));
    }

    @Test
    void largeListsExposeEveryOffsetAndOnlyThenNeedScrollbar() {
        int visible = WorldAwakenedEditorListLayout.visibleRows(180, 30);
        assertTrue(WorldAwakenedEditorListLayout.needsScrollbar(15, visible));
        assertTrue(WorldAwakenedEditorListLayout.needsScrollbar(30, visible));
        assertEquals(9, WorldAwakenedEditorListLayout.clampOffset(99, 15, visible));
        assertEquals(24, WorldAwakenedEditorListLayout.clampOffset(99, 30, visible));
        assertEquals(0, WorldAwakenedEditorListLayout.clampOffset(-4, 30, visible));
    }

    @Test
    void smallerViewportStillKeepsOneReachableRow() {
        assertEquals(1, WorldAwakenedEditorListLayout.visibleRows(12, 30));
        assertEquals(29, WorldAwakenedEditorListLayout.clampOffset(50, 30, 1));
    }

    @Test
    void selectorSizedRowsScaleFromShortToLargeCatalogues() {
        int visible = WorldAwakenedEditorListLayout.visibleRows(324, 36);
        assertEquals(9, visible);
        assertFalse(WorldAwakenedEditorListLayout.needsScrollbar(1, visible));
        assertTrue(WorldAwakenedEditorListLayout.needsScrollbar(10, visible));
        assertEquals(1, WorldAwakenedEditorListLayout.clampOffset(50, 10, visible));
        assertEquals(41, WorldAwakenedEditorListLayout.clampOffset(50, 50, visible));
        assertEquals(491, WorldAwakenedEditorListLayout.clampOffset(999, 500, visible));
    }
}
