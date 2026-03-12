package net.sprocketgames.worldawakened.progression.event;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import net.sprocketgames.worldawakened.data.definition.SourceScope;

public final class WorldAwakenedNamedTriggerEvent extends Event {
    private final ServerLevel level;
    private final ServerPlayer player;
    private final ResourceLocation triggerRuleId;
    private final ResourceLocation eventId;
    private final SourceScope scope;

    public WorldAwakenedNamedTriggerEvent(
            ServerLevel level,
            ServerPlayer player,
            ResourceLocation triggerRuleId,
            ResourceLocation eventId,
            SourceScope scope) {
        this.level = level;
        this.player = player;
        this.triggerRuleId = triggerRuleId;
        this.eventId = eventId;
        this.scope = scope;
    }

    public ServerLevel level() {
        return level;
    }

    public ServerPlayer player() {
        return player;
    }

    public ResourceLocation triggerRuleId() {
        return triggerRuleId;
    }

    public ResourceLocation eventId() {
        return eventId;
    }

    public SourceScope scope() {
        return scope;
    }
}
