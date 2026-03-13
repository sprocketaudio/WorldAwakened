package net.sprocketgames.worldawakened.data.load;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import net.sprocketgames.worldawakened.debug.WorldAwakenedValidationSummary;

class WorldAwakenedDatapackServiceTest {
    @Test
    void publishAssignsMonotonicGenerations() {
        WorldAwakenedDatapackService service = new WorldAwakenedDatapackService();

        service.publish(snapshotAt("2026-03-13T10:00:00Z"), "test:first");
        WorldAwakenedDatapackSnapshot first = service.currentSnapshot();
        assertEquals(1L, first.generation());

        service.publish(snapshotAt("2026-03-13T10:00:01Z"), "test:second");
        WorldAwakenedDatapackSnapshot second = service.currentSnapshot();
        assertEquals(2L, second.generation());
    }

    @Test
    void pinSnapshotBlocksMixedGraphByKeepingPinnedSnapshot() {
        WorldAwakenedDatapackService service = new WorldAwakenedDatapackService();

        service.publish(snapshotAt("2026-03-13T10:00:00Z"), "test:baseline");
        WorldAwakenedDatapackSnapshot pinned = service.currentSnapshot();

        service.publish(snapshotAt("2026-03-13T10:00:01Z"), "test:replacement");
        WorldAwakenedDatapackSnapshot resolved = service.pinSnapshot(pinned, "test:mixed_graph");

        assertSame(pinned, resolved);
        assertNotEquals(resolved.generation(), service.currentSnapshot().generation());
    }

    private static WorldAwakenedDatapackSnapshot snapshotAt(String instant) {
        return new WorldAwakenedDatapackSnapshot(
                0L,
                Instant.parse(instant),
                WorldAwakenedCompiledData.empty(),
                WorldAwakenedValidationSummary.empty());
    }
}

