package net.sprocketgames.worldawakened.network.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

class WorldAwakenedPoolBrowserGroupingTest {
    @Test
    void standardPoolsAreGroupedOnceWithoutChangingTheirIds() {
        List<String> ids = List.of("overworld_intro", "s2_overworld", "s3_overworld", "s4_overworld",
                "s5_overworld", "s6_overworld", "nether_escalation", "s4_nether", "s5_nether", "s6_nether",
                "end_escalation", "s6_end", "invasion_restless_night", "invasion_nether_surge",
                "invasion_awakened_hunt").stream().map(id -> "worldawakened:defaults/" + id).toList();

        var groups = WorldAwakenedPoolBrowserGrouping.groups(ids, new JsonArray());

        assertEquals(List.of(6, 4, 2, 3), groups.stream().map(group -> group.entries().size()).toList());
        var groupedIds = groups.stream().flatMap(group -> group.entries().stream())
                .map(WorldAwakenedPoolBrowserGrouping.Entry::poolId).toList();
        assertEquals(new LinkedHashSet<>(ids), new LinkedHashSet<>(groupedIds));
        assertEquals(ids.size(), groupedIds.size(), "No pool may be duplicated in presentation grouping");
    }

    @Test
    void customEmptyTrackAndExternalPoolRemainVisible() {
        JsonObject track = new JsonObject(); track.addProperty("id", "worldawakened:world/tracks/test");
        track.addProperty("display_name", "TEST"); JsonArray tracks = new JsonArray(); tracks.add(track);

        var groups = WorldAwakenedPoolBrowserGrouping.groups(List.of("othermod:dungeon"), tracks);

        assertTrue(groups.stream().anyMatch(group -> group.name().equals("TEST") && group.entries().isEmpty()));
        assertTrue(groups.stream().anyMatch(group -> group.kind() == WorldAwakenedPoolBrowserGrouping.Kind.UNGROUPED
                && group.entries().getFirst().poolId().equals("othermod:dungeon")));
    }
}
