package net.sprocketgames.worldawakened.network.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Shared ordering model used by the phase browser, movement controls, and saved draft. */
final class WorldAwakenedPhaseOrdering {
    private static final Comparator<Entry> ORDER = Comparator.comparingInt(Entry::sortIndex).thenComparing(Entry::id);

    private WorldAwakenedPhaseOrdering() {}

    record Entry(String id, int sortIndex, boolean starting, boolean editable) {}

    record MoveResult(List<Entry> order, Map<String, Integer> sortIndexes, boolean moved) {}

    static List<Entry> ordered(List<Entry> entries) {
        return entries.stream().sorted(ORDER).toList();
    }

    static boolean canMove(List<Entry> entries, String selectedId, int direction) {
        if (direction != -1 && direction != 1) return false;
        List<Entry> ordered = ordered(entries);
        int index = indexOf(ordered, selectedId);
        int target = index + direction;
        if (index < 0 || target < 0 || target >= ordered.size()) return false;
        Entry selected = ordered.get(index);
        Entry adjacent = ordered.get(target);
        return selected.editable() && adjacent.editable();
    }

    static MoveResult move(List<Entry> entries, String selectedId, int direction) {
        List<Entry> ordered = new ArrayList<>(ordered(entries));
        if (!canMove(ordered, selectedId, direction)) return unchanged(ordered);

        int index = indexOf(ordered, selectedId);
        int target = index + direction;
        List<Integer> slots = stableSortSlots(ordered);
        Entry selected = ordered.get(index);
        ordered.set(index, ordered.get(target));
        ordered.set(target, selected);

        Map<String, Integer> sortIndexes = new LinkedHashMap<>();
        List<Entry> moved = new ArrayList<>(ordered.size());
        for (int position = 0; position < ordered.size(); position++) {
            Entry entry = ordered.get(position);
            int sortIndex = slots.get(position);
            sortIndexes.put(entry.id(), sortIndex);
            moved.add(new Entry(entry.id(), sortIndex, entry.starting(), entry.editable()));
        }
        return new MoveResult(List.copyOf(moved), Map.copyOf(sortIndexes), true);
    }

    static boolean rowOwnsPoint(int x, int y, int width, int height, int rightReserve,
            double mouseX, double mouseY) {
        boolean inside = mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
        if (!inside) return false;
        return rightReserve <= 0 || mouseY < y + 17 || mouseX < x + width - rightReserve;
    }

    private static MoveResult unchanged(List<Entry> ordered) {
        Map<String, Integer> sortIndexes = new LinkedHashMap<>();
        ordered.forEach(entry -> sortIndexes.put(entry.id(), entry.sortIndex()));
        return new MoveResult(List.copyOf(ordered), Map.copyOf(sortIndexes), false);
    }

    private static List<Integer> stableSortSlots(List<Entry> ordered) {
        List<Integer> slots = ordered.stream().map(Entry::sortIndex).toList();
        boolean strictlyIncreasing = true;
        for (int index = 1; index < slots.size(); index++) {
            if (slots.get(index) <= slots.get(index - 1)) {
                strictlyIncreasing = false;
                break;
            }
        }
        if (strictlyIncreasing) return slots;

        List<Integer> normalized = new ArrayList<>(ordered.size());
        for (int index = 0; index < ordered.size(); index++) normalized.add(index * 10);
        return List.copyOf(normalized);
    }

    private static int indexOf(List<Entry> ordered, String id) {
        for (int index = 0; index < ordered.size(); index++) {
            if (ordered.get(index).id().equals(id)) return index;
        }
        return -1;
    }
}
