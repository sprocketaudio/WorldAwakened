package net.sprocketgames.worldawakened.ascension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.OptionalInt;

import org.junit.jupiter.api.Test;

class WorldAwakenedAscensionComponentKeysTest {
    @Test
    void parseIndexSupportsLegacyAndIndexOnlyFormats() {
        OptionalInt legacy = WorldAwakenedAscensionComponentKeys.parseIndex("1|worldawakened:max_health_bonus");
        OptionalInt indexOnly = WorldAwakenedAscensionComponentKeys.parseIndex("2");

        assertTrue(legacy.isPresent());
        assertTrue(indexOnly.isPresent());
        assertEquals(1, legacy.getAsInt());
        assertEquals(2, indexOnly.getAsInt());
    }

    @Test
    void parseIndexRejectsMissingLeadingDigits() {
        OptionalInt missingLeadingIndex = WorldAwakenedAscensionComponentKeys.parseIndex("worldawakened:max_health_bonus");
        assertFalse(missingLeadingIndex.isPresent());
    }
}
