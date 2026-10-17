package net.sprocketgames.worldawakened.compat.apotheosis;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public final class WorldAwakenedApotheosisTierFilter {
    private final OptionalDouble min;
    private final OptionalDouble max;
    private final OptionalDouble exact;
    private final Optional<ComparisonOperator> op;
    private final OptionalDouble opValue;
    private final Set<Double> anyOf;

    private WorldAwakenedApotheosisTierFilter(
            OptionalDouble min,
            OptionalDouble max,
            OptionalDouble exact,
            Optional<ComparisonOperator> op,
            OptionalDouble opValue,
            Set<Double> anyOf) {
        this.min = min;
        this.max = max;
        this.exact = exact;
        this.op = op;
        this.opValue = opValue;
        this.anyOf = anyOf;
    }

    public static Optional<WorldAwakenedApotheosisTierFilter> compile(Optional<JsonElement> raw) {
        if (raw.isEmpty()) {
            return Optional.empty();
        }
        JsonElement element = raw.get();
        if (element == null || element.isJsonNull()) {
            return Optional.empty();
        }

        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            double value = element.getAsDouble();
            return Optional.of(exact(value));
        }
        if (element.isJsonArray()) {
            LinkedHashSet<Double> values = new LinkedHashSet<>();
            for (JsonElement entry : element.getAsJsonArray()) {
                if (entry != null && entry.isJsonPrimitive() && entry.getAsJsonPrimitive().isNumber()) {
                    values.add(entry.getAsDouble());
                }
            }
            if (values.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new WorldAwakenedApotheosisTierFilter(
                    OptionalDouble.empty(),
                    OptionalDouble.empty(),
                    OptionalDouble.empty(),
                    Optional.empty(),
                    OptionalDouble.empty(),
                    Set.copyOf(values)));
        }
        if (!element.isJsonObject()) {
            return Optional.empty();
        }

        JsonObject object = element.getAsJsonObject();
        OptionalDouble min = readDouble(object, "min", "minimum", "gte", "at_least");
        OptionalDouble max = readDouble(object, "max", "maximum", "lte", "at_most");
        OptionalDouble exact = readDouble(object, "eq", "equals", "exact", "tier");
        Optional<ComparisonOperator> operator = readString(object, "op")
                .flatMap(ComparisonOperator::fromRaw);
        OptionalDouble operatorValue = readDouble(object, "value");
        if (operator.isPresent() && operatorValue.isEmpty()) {
            operatorValue = readDouble(object, "tier");
        }

        LinkedHashSet<Double> anyOf = new LinkedHashSet<>();
        readDoubleArray(object, anyOf, "any_of", "one_of", "in", "tiers", "values");
        if (anyOf.isEmpty()) {
            readDouble(object, "any")
                    .ifPresent(anyOf::add);
        }

        boolean hasCriteria = min.isPresent()
                || max.isPresent()
                || exact.isPresent()
                || (operator.isPresent() && operatorValue.isPresent())
                || !anyOf.isEmpty();
        if (!hasCriteria) {
            return Optional.empty();
        }

        return Optional.of(new WorldAwakenedApotheosisTierFilter(
                min,
                max,
                exact,
                operator,
                operatorValue,
                Set.copyOf(anyOf)));
    }

    public boolean matches(double tierValue) {
        if (min.isPresent() && tierValue < min.getAsDouble()) {
            return false;
        }
        if (max.isPresent() && tierValue > max.getAsDouble()) {
            return false;
        }
        if (exact.isPresent() && tierValue != exact.getAsDouble()) {
            return false;
        }
        if (!anyOf.isEmpty() && !anyOf.contains(tierValue)) {
            return false;
        }
        if (op.isPresent() && opValue.isPresent() && !op.get().matches(tierValue, opValue.getAsDouble())) {
            return false;
        }
        return true;
    }

    public String describe() {
        List<String> parts = new ArrayList<>();
        if (min.isPresent()) {
            parts.add("min=" + min.getAsDouble());
        }
        if (max.isPresent()) {
            parts.add("max=" + max.getAsDouble());
        }
        if (exact.isPresent()) {
            parts.add("exact=" + exact.getAsDouble());
        }
        if (!anyOf.isEmpty()) {
            parts.add("any_of=" + anyOf);
        }
        if (op.isPresent() && opValue.isPresent()) {
            parts.add("op=" + op.get().serialized() + " value=" + opValue.getAsDouble());
        }
        return parts.isEmpty() ? "<none>" : String.join(", ", parts);
    }

    private static WorldAwakenedApotheosisTierFilter exact(double value) {
        return new WorldAwakenedApotheosisTierFilter(
                OptionalDouble.empty(),
                OptionalDouble.empty(),
                OptionalDouble.of(value),
                Optional.empty(),
                OptionalDouble.empty(),
                Set.of());
    }

    private static OptionalDouble readDouble(JsonObject object, String... keys) {
        for (String key : keys) {
            if (!object.has(key)) {
                continue;
            }
            JsonElement value = object.get(key);
            if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
                return OptionalDouble.of(value.getAsDouble());
            }
        }
        return OptionalDouble.empty();
    }

    private static Optional<String> readString(JsonObject object, String... keys) {
        for (String key : keys) {
            if (!object.has(key)) {
                continue;
            }
            JsonElement value = object.get(key);
            if (value != null && value.isJsonPrimitive()) {
                String raw = value.getAsString();
                if (!raw.isBlank()) {
                    return Optional.of(raw.trim());
                }
            }
        }
        return Optional.empty();
    }

    private static void readDoubleArray(JsonObject object, Set<Double> collector, String... keys) {
        for (String key : keys) {
            if (!object.has(key) || !object.get(key).isJsonArray()) {
                continue;
            }
            for (JsonElement entry : object.getAsJsonArray(key)) {
                if (entry != null && entry.isJsonPrimitive() && entry.getAsJsonPrimitive().isNumber()) {
                    collector.add(entry.getAsDouble());
                }
            }
        }
    }

    private enum ComparisonOperator {
        GREATER_THAN(">"),
        GREATER_THAN_OR_EQUAL(">="),
        LESS_THAN("<"),
        LESS_THAN_OR_EQUAL("<="),
        EQUAL("=="),
        NOT_EQUAL("!=");

        private final String serialized;

        ComparisonOperator(String serialized) {
            this.serialized = serialized;
        }

        private String serialized() {
            return serialized;
        }

        private boolean matches(double left, double right) {
            return switch (this) {
                case GREATER_THAN -> left > right;
                case GREATER_THAN_OR_EQUAL -> left >= right;
                case LESS_THAN -> left < right;
                case LESS_THAN_OR_EQUAL -> left <= right;
                case EQUAL -> left == right;
                case NOT_EQUAL -> left != right;
            };
        }

        private static Optional<ComparisonOperator> fromRaw(String raw) {
            String normalized = raw.trim().toLowerCase(Locale.ROOT);
            return switch (normalized) {
                case ">", "gt" -> Optional.of(GREATER_THAN);
                case ">=", "gte", "ge", "at_least" -> Optional.of(GREATER_THAN_OR_EQUAL);
                case "<", "lt" -> Optional.of(LESS_THAN);
                case "<=", "lte", "le", "at_most" -> Optional.of(LESS_THAN_OR_EQUAL);
                case "==", "=", "eq", "equal", "equals" -> Optional.of(EQUAL);
                case "!=", "<>", "ne", "not_equal" -> Optional.of(NOT_EQUAL);
                default -> Optional.empty();
            };
        }
    }
}
