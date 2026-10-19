package net.sprocketgames.worldawakened.loot;

import com.mojang.serialization.MapCodec;

import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.sprocketgames.worldawakened.WorldAwakenedConstants;

public final class WorldAwakenedLootModifiers {
    public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> SERIALIZERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS,
                    WorldAwakenedConstants.MOD_ID);
    public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>, MapCodec<WorldAwakenedChestLootModifier>>
            PROFILE_LOOT = SERIALIZERS.register("profile_loot", () -> WorldAwakenedChestLootModifier.CODEC);

    private WorldAwakenedLootModifiers() {
    }
}
