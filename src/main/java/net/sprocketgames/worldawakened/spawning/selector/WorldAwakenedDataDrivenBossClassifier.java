package net.sprocketgames.worldawakened.spawning.selector;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import net.minecraft.resources.ResourceLocation;
import net.sprocketgames.worldawakened.data.definition.EntityBossFlagMapDefinition;

public final class WorldAwakenedDataDrivenBossClassifier implements WorldAwakenedBossClassifier {
    private final Set<ResourceLocation> bossEntities;
    private final Set<String> bossTags;

    private WorldAwakenedDataDrivenBossClassifier(Set<ResourceLocation> bossEntities, Set<String> bossTags) {
        this.bossEntities = bossEntities;
        this.bossTags = bossTags;
    }

    public static WorldAwakenedDataDrivenBossClassifier fromMaps(List<EntityBossFlagMapDefinition> maps) {
        HashSet<ResourceLocation> ids = new HashSet<>();
        HashSet<String> tags = new HashSet<>();
        for (EntityBossFlagMapDefinition map : maps) {
            for (var entry : map.entries()) {
                if (entry.isBoss()) {
                    ids.add(entry.entity());
                }
            }
        }
        return new WorldAwakenedDataDrivenBossClassifier(Set.copyOf(ids), Set.copyOf(tags));
    }

    @Override
    public boolean isBoss(WorldAwakenedEntityContextView context) {
        if (bossEntities.contains(context.entityId())) {
            return true;
        }
        for (ResourceLocation tag : context.entityTags()) {
            if (bossTags.contains(tag.toString().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}

