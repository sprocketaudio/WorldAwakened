package net.sprocketgames.worldawakened.network.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class WorldAwakenedClientStatusMessageTest {
    @Test
    void successfulApplyUsesCompactFeedback() {
        assertEquals("Changes applied.", WorldAwakenedClientConfigPayloadHandler.conciseStatusMessage(
                "applied", "Mutation pool applied successfully."));
    }

    @Test
    void completeResetIsDistinguishedFromAnOrdinaryApply() {
        assertEquals("Defaults restored.", WorldAwakenedClientConfigPayloadHandler.conciseStatusMessage(
                "applied", "All WorldAwakened editor settings were restored to built-in defaults."));
    }

    @Test
    void staleRevisionHasAConciseRecoveryInstruction() {
        assertEquals("Settings changed elsewhere — review and retry.",
                WorldAwakenedClientConfigPayloadHandler.conciseStatusMessage(
                        "stale_revision", "The profile changed on the server. Review it and try again."));
    }

    @Test
    void longValidationFailureMovesDetailToTooltip() {
        String longError = "Track World Phase Mob Configuration references a missing mutation pool and cannot be applied until the invalid mapping is repaired.";

        assertEquals("Could not apply changes — hover for details.",
                WorldAwakenedClientConfigPayloadHandler.conciseStatusMessage("validation_failed", longError));
    }
}
