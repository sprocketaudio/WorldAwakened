package net.sprocketgames.worldawakened.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;

import com.mojang.authlib.GameProfile;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

class WorldAwakenedConfigServiceTest {
    @AfterEach
    void resetRuntimeSettings() {
        WorldAwakenedConfigService.resetForTests();
    }

    @Test
    void applyCommitsMatchingRevisionAndRejectsStaleDraft() {
        ServerLevel level = transientLevel();
        WorldAwakenedConfigSavedData data = WorldAwakenedConfigSavedData.get(level);
        data.initialize(WorldAwakenedEditorSettings.builtInDefaults());
        WorldAwakenedEditorSettings changed = new WorldAwakenedEditorSettings(
                true, 1.25D, true, 1.5D, false, 4, true, 2.0D, false);

        WorldAwakenedConfigService.ApplyResult accepted =
                WorldAwakenedConfigService.apply(level, data.revision(), changed, "test");
        WorldAwakenedConfigService.ApplyResult stale =
                WorldAwakenedConfigService.apply(level, data.revision() - 1L, WorldAwakenedEditorSettings.builtInDefaults(), "stale");

        assertTrue(accepted.accepted());
        assertEquals(changed, accepted.current().settings());
        assertEquals(WorldAwakenedConfigService.ApplyStatus.STALE_REVISION, stale.status());
        assertEquals(changed, stale.current().settings());
    }

    @Test
    void invalidDraftDoesNotAdvanceRevision() {
        ServerLevel level = transientLevel();
        WorldAwakenedConfigSavedData data = WorldAwakenedConfigSavedData.get(level);
        data.initialize(WorldAwakenedEditorSettings.builtInDefaults());
        long revision = data.revision();
        WorldAwakenedEditorSettings invalid = new WorldAwakenedEditorSettings(
                true, 99.0D, true, 1.0D, true, 2, true, 1.0D, true);

        WorldAwakenedConfigService.ApplyResult result =
                WorldAwakenedConfigService.apply(level, revision, invalid, "test");

        assertFalse(result.accepted());
        assertEquals(WorldAwakenedConfigService.ApplyStatus.VALIDATION_FAILED, result.status());
        assertEquals(revision, result.current().revision());
    }

    @Test
    void authorizationAcceptsOperatorsAndIntegratedServerOwnerOnly() {
        MinecraftServer server = mock(MinecraftServer.class);
        ServerPlayer operator = mock(ServerPlayer.class);
        when(operator.hasPermissions(2)).thenReturn(true);

        ServerPlayer owner = mock(ServerPlayer.class);
        GameProfile ownerProfile = new GameProfile(java.util.UUID.randomUUID(), "Owner");
        when(owner.hasPermissions(2)).thenReturn(false);
        when(owner.getServer()).thenReturn(server);
        when(owner.getGameProfile()).thenReturn(ownerProfile);
        when(server.isSingleplayerOwner(ownerProfile)).thenReturn(true);

        ServerPlayer ordinary = mock(ServerPlayer.class);
        GameProfile ordinaryProfile = new GameProfile(java.util.UUID.randomUUID(), "Ordinary");
        when(ordinary.hasPermissions(2)).thenReturn(false);
        when(ordinary.getServer()).thenReturn(server);
        when(ordinary.getGameProfile()).thenReturn(ordinaryProfile);
        when(server.isSingleplayerOwner(ordinaryProfile)).thenReturn(false);

        assertTrue(WorldAwakenedConfigService.isAuthorized(operator));
        assertTrue(WorldAwakenedConfigService.isAuthorized(owner));
        assertFalse(WorldAwakenedConfigService.isAuthorized(ordinary));
    }

    private static ServerLevel transientLevel() {
        ServerLevel level = mock(ServerLevel.class);
        when(level.getServer()).thenReturn(null);
        when(level.getDataStorage()).thenReturn(null);
        return level;
    }
}
