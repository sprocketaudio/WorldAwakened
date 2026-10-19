package net.sprocketgames.worldawakened.loot;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.LootModifier;
import net.sprocketgames.worldawakened.WorldAwakenedMod;

/** Adds authored chest-profile rewards after vanilla/modded table generation. */
public final class WorldAwakenedChestLootModifier extends LootModifier {
    public static final MapCodec<WorldAwakenedChestLootModifier> CODEC = RecordCodecBuilder.mapCodec(instance ->
            codecStart(instance).apply(instance, WorldAwakenedChestLootModifier::new));
    private static final ThreadLocal<Boolean> APPLYING = ThreadLocal.withInitial(() -> false);

    private WorldAwakenedChestLootModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        if (APPLYING.get()) {
            return generatedLoot;
        }
        APPLYING.set(true);
        try {
            WorldAwakenedMod.LOOT_SERVICE.onChestLootGenerated(context, generatedLoot);
            return generatedLoot;
        } finally {
            APPLYING.remove();
        }
    }

    @Override
    public MapCodec<? extends WorldAwakenedChestLootModifier> codec() {
        return CODEC;
    }
}
