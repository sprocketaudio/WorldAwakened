package net.sprocketgames.worldawakened.spawning.selector;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import net.minecraft.resources.ResourceLocation;

public final class WorldAwakenedCompiledEntitySelector {
    private final Set<ResourceLocation> eligibleEntities;
    private final Set<String> eligibleEntityTags;
    private final Set<String> eligibleNamespaces;
    private final Set<String> eligibleMobCategories;
    private final Set<ResourceLocation> excludedEntities;
    private final Set<String> excludedEntityTags;

    private WorldAwakenedCompiledEntitySelector(
            Set<ResourceLocation> eligibleEntities,
            Set<String> eligibleEntityTags,
            Set<String> eligibleNamespaces,
            Set<String> eligibleMobCategories,
            Set<ResourceLocation> excludedEntities,
            Set<String> excludedEntityTags) {
        this.eligibleEntities = eligibleEntities;
        this.eligibleEntityTags = eligibleEntityTags;
        this.eligibleNamespaces = eligibleNamespaces;
        this.eligibleMobCategories = eligibleMobCategories;
        this.excludedEntities = excludedEntities;
        this.excludedEntityTags = excludedEntityTags;
    }

    public static WorldAwakenedCompiledEntitySelector compile(WorldAwakenedEntitySelectorDefinition definition) {
        return new WorldAwakenedCompiledEntitySelector(
                Set.copyOf(definition.eligibleEntities()),
                normalize(definition.eligibleEntityTags()),
                normalize(definition.eligibleNamespaces()),
                normalize(definition.eligibleMobCategories()),
                Set.copyOf(definition.excludedEntities()),
                normalize(definition.excludedEntityTags()));
    }

    public boolean matches(WorldAwakenedEntityContextView context) {
        if (excludedEntities.contains(context.entityId())) {
            return false;
        }
        for (ResourceLocation tagId : context.entityTags()) {
            if (excludedEntityTags.contains(tagId.toString().toLowerCase(Locale.ROOT))) {
                return false;
            }
        }

        if (eligibleEntities.isEmpty() && eligibleEntityTags.isEmpty() && eligibleNamespaces.isEmpty()
                && eligibleMobCategories.isEmpty()) {
            return true;
        }

        if (eligibleEntities.contains(context.entityId())) {
            return true;
        }
        if (eligibleNamespaces.contains(context.entityId().getNamespace().toLowerCase(Locale.ROOT))) {
            return true;
        }
        if (eligibleMobCategories.contains(context.mobCategory().toLowerCase(Locale.ROOT))) {
            return true;
        }
        for (ResourceLocation tagId : context.entityTags()) {
            if (eligibleEntityTags.contains(tagId.toString().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }

        return false;
    }

    private static Set<String> normalize(Set<String> values) {
        HashSet<String> set = new HashSet<>(values.size());
        for (String value : values) {
            set.add(value.toLowerCase(Locale.ROOT));
        }
        return Set.copyOf(set);
    }

    private static Set<String> normalize(java.util.List<String> values) {
        HashSet<String> set = new HashSet<>(values.size());
        for (String value : values) {
            set.add(value.toLowerCase(Locale.ROOT));
        }
        return Set.copyOf(set);
    }
}

