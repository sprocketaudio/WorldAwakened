package net.sprocketgames.worldawakened.data.codec;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;

import net.minecraft.resources.ResourceLocation;

public final class WorldAwakenedJsonCodecs {
    private WorldAwakenedJsonCodecs() {
    }

    public static final int SUPPORTED_SCHEMA_VERSION = 1;

    public static final Codec<JsonElement> JSON_ELEMENT = Codec.PASSTHROUGH.xmap(
            dynamic -> dynamic.convert(JsonOps.INSTANCE).getValue(),
            element -> new Dynamic<>(JsonOps.INSTANCE, element));

    public static final Codec<JsonObject> JSON_OBJECT = JSON_ELEMENT.flatXmap(
            element -> element != null && element.isJsonObject()
                    ? DataResult.success(element.getAsJsonObject())
                    : DataResult.error(() -> "Expected JSON object"),
            DataResult::success);

    public static final Codec<ResourceLocation> RESOURCE_LOCATION = ResourceLocation.CODEC;
    public static final Codec<List<ResourceLocation>> RESOURCE_LOCATION_LIST = RESOURCE_LOCATION.listOf()
            .xmap(List::copyOf, List::copyOf);
    public static final Codec<List<String>> STRING_LIST = Codec.STRING.listOf()
            .xmap(list -> Collections.unmodifiableList(list.stream().map(String::trim).toList()), List::copyOf);

    public static <E extends Enum<E>> Codec<E> enumCodec(Class<E> enumClass) {
        return Codec.STRING.flatXmap(
                value -> {
                    try {
                        return DataResult.success(Enum.valueOf(enumClass, value.toUpperCase(Locale.ROOT)));
                    } catch (IllegalArgumentException ex) {
                        return DataResult.error(() -> "Invalid " + enumClass.getSimpleName() + " value: " + value);
                    }
                },
                value -> DataResult.success(value.name().toLowerCase(Locale.ROOT)));
    }
}

