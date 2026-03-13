package net.sprocketgames.worldawakened.ascension;

import java.util.OptionalInt;

import net.minecraft.resources.ResourceLocation;
import net.sprocketgames.worldawakened.data.definition.AscensionComponentDefinition;

final class WorldAwakenedAscensionComponentKeys {
    private WorldAwakenedAscensionComponentKeys() {
    }

    static String componentKey(int index, AscensionComponentDefinition component) {
        return componentKey(index, component.type());
    }

    static String componentKey(int index, ResourceLocation componentType) {
        return index + "|" + componentType;
    }

    static OptionalInt parseIndex(String key) {
        if (key == null || key.isBlank()) {
            return OptionalInt.empty();
        }
        String value = key;
        int divider = key.indexOf('|');
        if (divider >= 0) {
            value = key.substring(0, divider);
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return OptionalInt.empty();
            }
        }
        try {
            return OptionalInt.of(Integer.parseInt(value));
        } catch (NumberFormatException ignored) {
            return OptionalInt.empty();
        }
    }
}
