package net.sprocketgames.worldawakened.network.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.DoubleConsumer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import net.sprocketgames.worldawakened.config.WorldAwakenedEditorSettings;
import net.sprocketgames.worldawakened.config.WorldAwakenedEditorListLayout;
import net.sprocketgames.worldawakened.config.WorldAwakenedMutatorWeightList;
import net.sprocketgames.worldawakened.mutator.WorldAwakenedMutationProbability;
import net.sprocketgames.worldawakened.network.WorldAwakenedApplyConfigPayload;
import net.sprocketgames.worldawakened.network.WorldAwakenedApplyMutationPoolPayload;

public final class WorldAwakenedClientConfigPayloadHandler {
    private static String reopenPoolId;

    private WorldAwakenedClientConfigPayloadHandler() {}

    public static void handleSnapshot(String payloadJson) {
        ClientModel model = parse(payloadJson);
        Minecraft minecraft = Minecraft.getInstance();
        if (model == null || minecraft == null) return;
        if (reopenPoolId != null) {
            PoolModel pool = model.pool(reopenPoolId);
            reopenPoolId = null;
            minecraft.setScreen(new MutationPoolsScreen(model, pool));
        } else {
            minecraft.setScreen(new DashboardScreen(model));
        }
    }

    private static ClientModel parse(String payloadJson) {
        try {
            JsonObject root = JsonParser.parseString(payloadJson).getAsJsonObject();
            JsonObject settings = root.getAsJsonObject("settings");
            JsonObject bounds = root.getAsJsonObject("bounds");
            List<PoolModel> pools = new ArrayList<>();
            for (JsonElement value : root.getAsJsonArray("mutation_pools")) {
                JsonObject node = value.getAsJsonObject();
                JsonObject definition = node.getAsJsonObject("definition").deepCopy();
                pools.add(new PoolModel(definition.get("id").getAsString(), readableId(definition.get("id").getAsString()),
                        node.get("origin").getAsString(), node.get("overridden").getAsBoolean(), definition));
            }
            List<MutatorModel> mutators = new ArrayList<>();
            for (JsonElement value : root.getAsJsonArray("mutators")) {
                JsonObject node = value.getAsJsonObject();
                List<String> components = new ArrayList<>();
                node.getAsJsonArray("components").forEach(item -> components.add(readableId(item.getAsString())));
                Set<String> exclusive = new LinkedHashSet<>();
                if (node.has("exclusive_with")) node.getAsJsonArray("exclusive_with")
                        .forEach(item -> exclusive.add(item.getAsString()));
                mutators.add(new MutatorModel(node.get("id").getAsString(), node.get("name").getAsString(),
                        node.get("rarity").getAsString(), node.get("stacking_group").getAsString(),
                        node.has("max_stack_count") ? node.get("max_stack_count").getAsInt() : 1,
                        Set.copyOf(exclusive), List.copyOf(components)));
            }
            List<EntityModel> entities = new ArrayList<>();
            for (JsonElement value : root.getAsJsonArray("living_entities")) {
                JsonObject node = value.getAsJsonObject();
                entities.add(new EntityModel(node.get("id").getAsString(), node.get("name").getAsString(),
                        node.get("namespace").getAsString()));
            }
            List<String> dimensions = new ArrayList<>();
            root.getAsJsonArray("dimensions").forEach(value -> dimensions.add(value.getAsString()));
            return new ClientModel(root.get("revision").getAsLong(), root.get("profile_revision").getAsLong(),
                    read(root, "status", "ready"), read(root, "message", ""), root.get("editable").getAsBoolean(),
                    read(root, "active_profile", "Standard"), read(root, "profile_summary", ""),
                    read(root, "progression_mode", "global"), read(root, "progression_summary", ""),
                    read(root, "progression_note", ""), root.get("difficulty_intensity_available").getAsBoolean(),
                    root.get("pressure_events_available").getAsBoolean(), WorldAwakenedEditorSettings.fromJson(settings),
                    bounds.get("difficulty_min").getAsDouble(), bounds.get("difficulty_max").getAsDouble(),
                    bounds.get("mutation_frequency_min").getAsDouble(), bounds.get("mutation_frequency_max").getAsDouble(),
                    bounds.get("maximum_mutations_min").getAsInt(), bounds.get("maximum_mutations_max").getAsInt(),
                    bounds.get("event_frequency_min").getAsDouble(), bounds.get("event_frequency_max").getAsDouble(),
                    List.copyOf(pools), List.copyOf(mutators), List.copyOf(entities), List.copyOf(dimensions));
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static String read(JsonObject root, String key, String fallback) {
        return root.has(key) ? root.get(key).getAsString() : fallback;
    }

    private record ClientModel(long settingsRevision, long profileRevision, String status, String message,
            boolean editable, String activeProfile, String profileSummary, String progressionMode,
            String progressionSummary, String progressionNote, boolean difficultyAvailable, boolean eventsAvailable,
            WorldAwakenedEditorSettings settings, double difficultyMin, double difficultyMax,
            double mutationFrequencyMin, double mutationFrequencyMax, int maximumMutationsMin,
            int maximumMutationsMax, double eventFrequencyMin, double eventFrequencyMax,
            List<PoolModel> pools, List<MutatorModel> mutators, List<EntityModel> entities, List<String> dimensions) {
        PoolModel pool(String id) { return pools.stream().filter(pool -> pool.id().equals(id)).findFirst().orElse(null); }
        MutatorModel mutator(String id) { return mutators.stream().filter(mutator -> mutator.id().equals(id)).findFirst().orElse(null); }
    }
    private record PoolModel(String id, String name, String origin, boolean overridden, JsonObject definition) {}
    private record MutatorModel(String id, String name, String rarity, String stackingGroup,
            int maxStackCount, Set<String> exclusiveWith, List<String> components) {}
    private record EntityModel(String id, String name, String namespace) {}

    private abstract static class EditorScreen extends Screen {
        protected final ClientModel model;
        protected EditorScreen(String title, ClientModel model) { super(Component.literal(title)); this.model = model; }
        protected int panelWidth() { return Math.min(760, width - 20); }
        protected int panelLeft() { return (width - panelWidth()) / 2; }
        protected void header(GuiGraphics graphics, String heading, String breadcrumb) {
            int half = Math.max(40, panelWidth() / 2 - 8);
            drawMarqueeLeft(graphics, font, Component.literal("WorldAwakened").withStyle(ChatFormatting.BOLD), panelLeft(), 10, half, 0xFFFFFF);
            drawMarqueeLeft(graphics, font, Component.literal(breadcrumb), panelLeft(), 23, panelWidth(), 0x9A9A9A);
            drawMarqueeLeft(graphics, font, Component.literal(heading), width / 2 + 8, 10, half, 0xFFFFFF);
        }
        protected void panel(GuiGraphics graphics, int x, int y, int w, int h) {
            graphics.fill(x, y, x + w, y + h, 0xB0101010);
            graphics.fill(x, y, x + w, y + 1, 0xFF555555);
            graphics.fill(x, y + h - 1, x + w, y + h, 0xFF282828);
            graphics.fill(x, y, x + 1, y + h, 0xFF555555);
            graphics.fill(x + w - 1, y, x + w, y + h, 0xFF282828);
        }
        protected Button button(int x, int y, int w, String label, Button.OnPress action, String tooltip) {
            Button button = addRenderableWidget(new MarqueeButton(x, y, w, 20, Component.literal(label), action));
            if (tooltip != null && !tooltip.isBlank()) button.setTooltip(Tooltip.create(Component.literal(tooltip)));
            return button;
        }
        protected void renderWidgets(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            for (Renderable renderable : renderables) {
                renderable.render(graphics, mouseX, mouseY, partialTick);
            }
        }
        @Override public boolean isPauseScreen() { return false; }
    }

    private static final class DashboardScreen extends EditorScreen {
        DashboardScreen(ClientModel model) { super("WorldAwakened", model); }
        @Override protected void init() {
            clearWidgets();
            int left = panelLeft(); int w = panelWidth(); int gap = 8; int column = (w - gap) / 2;
            button(left, 50, w, "Active Profile: " + model.activeProfile() + "  —  Edit Profile >", ignored ->
                    minecraft.setScreen(new MutationPoolsScreen(model, null)),
                    "Edit the active profile's mutation pools. Alternative presets are not yet available.");
            tile(left, 92, column, "Progression", "Stages, milestones and difficulty evolution", false, ignored -> {});
            tile(left + column + gap, 92, column, "Mob Mutations", "Pools, rarity, eligible mobs and combinations", true,
                    ignored -> minecraft.setScreen(new MutationPoolsScreen(model, null)));
            tile(left, 132, column, "Abilities & Mutators", "Effects and archetypes — coming later", false, ignored -> {});
            tile(left + column + gap, 132, column, "Rewards", "Loot and Ascension — coming later", false, ignored -> {});
            tile(left, 172, column, "World Events", "Pressure events — coming later", false, ignored -> {});
            tile(left + column + gap, 172, column, "Global Settings", "Basic live runtime controls", true,
                    ignored -> minecraft.setScreen(new GlobalSettingsScreen(model)));
            button(width / 2 - 50, Math.min(height - 26, 222), 100, "Close", ignored -> onClose(), null);
        }
        private void tile(int x, int y, int w, String title, String description, boolean available, Button.OnPress press) {
            Button tile = button(x, y, w, title + (available ? "  >" : "  [Unavailable]"), press, description);
            tile.active = available;
        }
        @Override public void render(GuiGraphics graphics, int mx, int my, float tick) {
            renderBackground(graphics, mx, my, tick);
            header(graphics, "Gameplay Profile Editor", "Administration");
            graphics.drawString(font, "ACTIVE PROFILE", panelLeft(), 38, 0xAAAAAA);
            drawMarqueeLeft(graphics, font, Component.literal(model.profileSummary()), panelLeft() + 4, 74,
                    panelWidth() - 8, 0xBEBEBE);
            if (!model.message().isBlank()) drawMarqueeLeft(graphics, font, Component.literal(model.message()),
                    panelLeft(), Math.min(height - 39, 211), panelWidth(),
                    "applied".equals(model.status()) ? 0x66FF66 : 0xFF7777);
            renderWidgets(graphics, mx, my, tick);
        }
    }

    private static final class MutationPoolsScreen extends EditorScreen {
        private static final int CONTENT_TOP = 52;
        private static final int POOL_ROW_HEIGHT = 34;
        private static final int MUTATOR_ROW_HEIGHT = 30;
        private static final int COMBINATION_ROW_HEIGHT = 38;
        private static final int DETAIL_HEADER_HEIGHT = 29;
        private static final int SECTION_GAP = 6;
        private static final int GENERAL_SECTION_HEIGHT = 64;
        private static final int ELIGIBILITY_SECTION_HEIGHT = 65;
        private PoolDraft draft;
        private EditBox search;
        private int offset;
        private boolean compactDetail;
        private String query = "";
        private String stageFilter = "All stages";
        private String dimensionFilter = "All dimensions";
        private int enabledFilter;
        private int mutatorOffset;
        private int poolViewportX, poolViewportY, poolViewportWidth, poolViewportHeight;
        private int poolVisibleRows, poolTotalRows;
        private int mutatorViewportX, mutatorViewportY, mutatorViewportWidth, mutatorViewportHeight;
        private int mutatorVisibleRows, mutatorTotalRows;
        private int mutatorRowWidth, mutatorLabelWidth;
        private int combinationViewportX, combinationViewportY, combinationViewportWidth, combinationViewportHeight;
        private int combinationVisibleRows, combinationTotalRows, combinationOffset;
        private boolean draggingPoolScrollbar, draggingMutatorScrollbar, draggingCombinationScrollbar;
        private double scrollbarGrabOffset;
        MutationPoolsScreen(ClientModel model, PoolModel selected) {
            super("Mob Mutations", model);
            if (selected != null) draft = new PoolDraft(selected);
        }
        @Override protected void init() {
            clearWidgets();
            boolean compact = width < 620;
            int left = panelLeft();
            int w = panelWidth();
            int browserW = compact ? w : Math.min(290, Math.max(230, w * 36 / 100));
            int contentBottom = height - 31;
            if (!compact || !compactDetail) {
                search = new EditBox(font, left + 5, CONTENT_TOP + 5, browserW - 10, 18, Component.literal("Search mutation pools"));
                search.setHint(Component.literal("Search pools...").withStyle(ChatFormatting.DARK_GRAY));
                search.setValue(query);
                search.setResponder(value -> { query = value; offset = 0; rebuildWidgets(); });
                addRenderableWidget(search);
                setInitialFocus(search);
                int filterY = CONTENT_TOP + 27;
                int third = (browserW - 16) / 3;
                button(left + 5, filterY, third, filterLabel(stageFilter), ignored -> { stageFilter = next(stageOptions(), stageFilter); offset = 0; rebuildWidgets(); }, "Filter by discovered stage context");
                button(left + 8 + third, filterY, third, filterLabel(dimensionFilter), ignored -> { dimensionFilter = next(dimensionOptions(), dimensionFilter); offset = 0; rebuildWidgets(); }, "Filter by eligible dimension");
                String enabledLabel = enabledFilter == 0 ? "All states" : enabledFilter == 1 ? "Enabled" : "Disabled";
                button(left + 11 + third * 2, filterY, third, enabledLabel, ignored -> { enabledFilter = (enabledFilter + 1) % 3; offset = 0; rebuildWidgets(); }, "Filter enabled or disabled pools");
                List<PoolModel> shown = filteredPools();
                poolViewportX = left + 5;
                poolViewportY = filterY + 24;
                poolViewportWidth = browserW - 10;
                poolViewportHeight = Math.max(POOL_ROW_HEIGHT, contentBottom - poolViewportY - 5);
                poolVisibleRows = WorldAwakenedEditorListLayout.visibleRows(poolViewportHeight, POOL_ROW_HEIGHT);
                poolTotalRows = shown.size();
                offset = clampOffset(offset, poolTotalRows, poolVisibleRows);
                boolean poolOverflow = WorldAwakenedEditorListLayout.needsScrollbar(poolTotalRows, poolVisibleRows);
                int rowWidth = poolViewportWidth - (poolOverflow ? 8 : 0);
                for (int row = 0; row < Math.min(poolVisibleRows, Math.max(0, shown.size() - offset)); row++) {
                    PoolModel pool = shown.get(offset + row);
                    boolean selected = draft != null && draft.id.equals(pool.id());
                    PoolRowButton entry = new PoolRowButton(poolViewportX, poolViewportY + row * POOL_ROW_HEIGHT,
                            rowWidth, POOL_ROW_HEIGHT - 2, pool, selected, selected && draft.dirty, ignored -> {
                        if (draft != null && draft.dirty && !draft.id.equals(pool.id())) {
                            minecraft.setScreen(new LeaveDraftScreen(this, () -> select(pool, compact)));
                        } else select(pool, compact);
                    });
                    entry.setTooltip(Tooltip.create(Component.literal(pool.id() + "\n" + pool.origin())));
                    addRenderableWidget(entry);
                }
            }
            if (!compact && draft != null) addDetails(left + browserW + 10, CONTENT_TOP, w - browserW - 10);
            if (compact && compactDetail && draft != null) addDetails(left, CONTENT_TOP, w);
            button(left, height - 25, 72, "Back", ignored -> back(), null);
        }
        private void select(PoolModel pool, boolean compact) { draft = new PoolDraft(pool); mutatorOffset = 0; combinationOffset = 0; compactDetail = compact; rebuildWidgets(); }
        private List<PoolModel> filteredPools() {
            String lowered = query.toLowerCase(Locale.ROOT);
            return model.pools().stream().filter(pool -> pool.name().toLowerCase(Locale.ROOT).contains(lowered)
                    || pool.id().toLowerCase(Locale.ROOT).contains(lowered) || context(pool.definition()).toLowerCase(Locale.ROOT).contains(lowered))
                    .filter(pool -> "All stages".equals(stageFilter) || stageContexts(pool.definition()).contains(stageFilter))
                    .filter(pool -> "All dimensions".equals(dimensionFilter) || dimensions(pool.definition()).contains(dimensionFilter))
                    .filter(pool -> enabledFilter == 0 || (enabledFilter == 1) == (!pool.definition().has("enabled") || pool.definition().get("enabled").getAsBoolean()))
                    .toList();
        }
        private List<String> stageOptions() { List<String> values = new ArrayList<>(); values.add("All stages"); model.pools().forEach(pool -> stageContexts(pool.definition()).forEach(stage -> { if (!values.contains(stage)) values.add(stage); })); return values; }
        private List<String> dimensionOptions() { List<String> values = new ArrayList<>(); values.add("All dimensions"); model.pools().forEach(pool -> dimensions(pool.definition()).forEach(dimension -> { if (!values.contains(dimension)) values.add(dimension); })); return values; }
        private void addDetails(int x, int y, int w) {
            int sectionX = x + 5;
            int sectionW = w - 10;
            int innerX = sectionX + 6;
            int innerW = sectionW - 12;
            int third = (innerW - 8) / 3;
            int generalSectionY = y + DETAIL_HEADER_HEIGHT;
            int generalSectionHeight = generalSectionHeight(innerW);
            int generalControlsY = generalSectionY + 18;
            button(innerX + innerW - 104, y + 4, 104, toggle("Enabled", draft.enabled), ignored -> {
                draft.enabled = !draft.enabled;
                draft.dirty = true;
                rebuildWidgets();
            }, "Enabled controls participation. Pool-selection weight controls which matching pool is chosen.");
            ValueSlider probability = new ValueSlider(innerX, generalControlsY, third,
                    "Mutation probability", draft.chance, 0, 1, .01,
                    value -> { draft.chance = value; draft.dirty = true; }, false);
            probability.setTooltip(Tooltip.create(Component.literal(
                    "Chance that the selected pool actually mutates the mob. Displayed as a percentage.")));
            addRenderableWidget(probability);
            ValueSlider additional = new ValueSlider(innerX + third + 4, generalControlsY, third,
                    "Additional chance", draft.additionalChance, 0, 1, .01,
                    value -> { draft.additionalChance = value; draft.dirty = true; }, false);
            additional.active = draft.maximum > 1;
            additional.setTooltip(Tooltip.create(Component.literal(draft.maximum > 1
                    ? "After the first mutation, this chance is repeated for each further compatible mutation up to the maximum."
                    : "Maximum Mutations is 1, so additional mutation attempts are disabled.")));
            addRenderableWidget(additional);
            ValueSlider maximum = new ValueSlider(innerX + (third + 4) * 2, generalControlsY, third,
                    "Maximum mutations", draft.maximum, 1, 8, 1,
                    value -> {
                        draft.maximum = (int)Math.round(value);
                        additional.active = draft.maximum > 1;
                        if (draft.maximum == 1) {
                            draft.additionalChance = 0.0D;
                            additional.setCurrent(0.0D);
                        }
                        draft.dirty = true;
                    }, false);
            maximum.setTooltip(Tooltip.create(Component.literal(
                    "Maximum mutators selected from this pool, still bounded by global safety limits.")));
            addRenderableWidget(maximum);

            int eligibilitySectionY = generalSectionY + generalSectionHeight + SECTION_GAP;
            int eligibilityControlsY = eligibilitySectionY + 18;
            button(innerX, eligibilityControlsY, third, "Mobs (" + draft.entities.size() + ")", ignored ->
                    minecraft.setScreen(new EntitySelectorScreen(model, this, draft)), "Choose which mobs can receive mutations.");
            button(innerX + third + 4, eligibilityControlsY, third, "Dimensions (" + draft.dimensions.size() + ")", ignored ->
                    minecraft.setScreen(new DimensionSelectorScreen(model, this, draft)), "Select dimensions available on this server.");
            button(innerX + (third + 4) * 2, eligibilityControlsY, third,
                    "Stages (" + stageContexts(draft.source.definition()).size() + ")", ignored -> {},
                    "Stage applicability is derived from the pool's existing conditions and is read-only here.").active = false;

            int mutatorSectionY = eligibilitySectionY + ELIGIBILITY_SECTION_HEIGHT + SECTION_GAP;
            int availableSections = Math.max(92, height - 31 - mutatorSectionY - 5);
            int mutatorSectionHeight = Math.max(48, availableSections * 55 / 100);
            int combinationSectionY = mutatorSectionY + mutatorSectionHeight + SECTION_GAP;
            int combinationSectionHeight = Math.max(38, height - 31 - combinationSectionY - 5);
            Button editMutators = button(innerX + innerW - 116, mutatorSectionY + 5, 116,
                    "Add / Remove (" + draft.weights.size() + ")", ignored ->
                            minecraft.setScreen(new MutatorSelectorScreen(model, this, draft)),
                    "Add or remove existing mutators. Their weights are relative selection weights, not spawn probabilities.");
            editMutators.active = model.editable();

            mutatorViewportX = innerX;
            mutatorViewportY = mutatorSectionY + 36;
            mutatorViewportWidth = innerW;
            mutatorViewportHeight = Math.max(1, mutatorSectionY + mutatorSectionHeight - mutatorViewportY - 6);
            mutatorVisibleRows = WorldAwakenedEditorListLayout.visibleRows(mutatorViewportHeight, MUTATOR_ROW_HEIGHT);
            mutatorTotalRows = draft.weights.size();
            mutatorOffset = clampOffset(mutatorOffset, mutatorTotalRows, mutatorVisibleRows);
            boolean overflow = WorldAwakenedEditorListLayout.needsScrollbar(mutatorTotalRows, mutatorVisibleRows);
            mutatorRowWidth = mutatorViewportWidth - (overflow ? 8 : 0);
            int weightButtonWidth = Math.max(50, Math.min(96, mutatorRowWidth / 4));
            // Keep the complete control cluster inside the outlined row instead of allowing
            // the remove button to cover its right-hand border.
            mutatorLabelWidth = Math.max(40, mutatorRowWidth - weightButtonWidth - 91);
            List<WorldAwakenedMutatorWeightList.Entry> entries = WorldAwakenedMutatorWeightList.ordered(draft.weights);
            long totalWeight = WorldAwakenedMutatorWeightList.totalWeight(draft.weights);
            for (int row = 0; row < Math.min(mutatorVisibleRows, mutatorTotalRows - mutatorOffset); row++) {
                int entryIndex = mutatorOffset + row;
                WorldAwakenedMutatorWeightList.Entry entry = entries.get(entryIndex);
                MutatorModel mutator = model.mutator(entry.id());
                int rowY = mutatorViewportY + row * MUTATOR_ROW_HEIGHT;
                int controlsX = mutatorViewportX + mutatorLabelWidth + 3;
                Button moveUp = button(controlsX, rowY + 3, 24, "\u2191", ignored ->
                        swapMutatorWeights(entries, entryIndex, entryIndex - 1),
                        "Swap this mutator's weight with the entry above");
                moveUp.active = entryIndex > 0;
                Button moveDown = button(controlsX + 27, rowY + 3, 24, "\u2193", ignored ->
                        swapMutatorWeights(entries, entryIndex, entryIndex + 1),
                        "Swap this mutator's weight with the entry below");
                moveDown.active = entryIndex < entries.size() - 1;
                button(controlsX + 54, rowY + 3, weightButtonWidth, "Weight: " + entry.weight(), ignored ->
                        minecraft.setScreen(new MutatorWeightScreen(model, this, draft, entry.id())),
                        mutatorTooltip(mutator) + "\nRelative selection share: "
                                + String.format(Locale.ROOT, "%.1f%%",
                                        WorldAwakenedMutatorWeightList.relativeShare(entry.weight(), totalWeight))
                                + "\nActual outcomes can differ after eligibility, compatibility, stacking, and multi-selection checks.");
                button(mutatorViewportX + mutatorRowWidth - 31, rowY + 3, 28, "x", ignored -> {
                    draft.weights.remove(entry.id());
                    draft.dirty = true;
                    mutatorOffset = clampOffset(mutatorOffset, draft.weights.size(), mutatorVisibleRows);
                    rebuildWidgets();
                }, "Remove this mutator from the pool draft");
            }

            int combinationInnerY = combinationSectionY + 18;
            button(innerX, combinationInnerY, Math.min(150, innerW / 3),
                    "Mode: " + (draft.curated ? "Curated" : "Any Compatible"), ignored -> {
                        draft.curated = !draft.curated;
                        draft.dirty = true;
                        rebuildWidgets();
                    }, combinationModeTooltip(draft.curated));
            if (draft.curated) {
                Button addCombination = button(innerX + innerW - 116, combinationInnerY, 116,
                        "Add Combination", ignored -> minecraft.setScreen(
                                new CombinationEditorScreen(model, this, draft, -1)),
                        "Create a weighted combination from this pool's mutators and eligible mobs.");
                addCombination.active = model.editable() && draft.weights.size() >= 2;
                combinationViewportX = innerX;
                combinationViewportY = combinationInnerY + 38;
                combinationViewportWidth = innerW;
                combinationViewportHeight = Math.max(1,
                        combinationSectionY + combinationSectionHeight - combinationViewportY - 5);
                combinationTotalRows = draft.combinations.size();
                combinationVisibleRows = WorldAwakenedEditorListLayout.visibleRows(
                        combinationViewportHeight, COMBINATION_ROW_HEIGHT);
                combinationOffset = clampOffset(combinationOffset, combinationTotalRows, combinationVisibleRows);
                boolean combinationOverflow = WorldAwakenedEditorListLayout.needsScrollbar(
                        combinationTotalRows, combinationVisibleRows);
                int combinationRowWidth = combinationViewportWidth - (combinationOverflow ? 8 : 0);
                for (int row = 0; row < Math.min(combinationVisibleRows,
                        Math.max(0, combinationTotalRows - combinationOffset)); row++) {
                    int index = combinationOffset + row;
                    CombinationDraft combination = draft.combinations.get(index);
                    int rowY = combinationViewportY + row * COMBINATION_ROW_HEIGHT;
                    button(combinationViewportX + combinationRowWidth - 84, rowY + 8, 50, "Edit", ignored ->
                            minecraft.setScreen(new CombinationEditorScreen(model, this, draft, index)),
                            "Edit mutators, eligible mobs, and relative combination weight.");
                    button(combinationViewportX + combinationRowWidth - 31, rowY + 8, 28, "x", ignored -> {
                        draft.combinations.remove(index);
                        draft.dirty = true;
                        rebuildWidgets();
                    }, "Remove this curated combination from the local draft.");
                }
            } else {
                combinationViewportX = combinationViewportY = combinationViewportWidth = combinationViewportHeight = 0;
                combinationVisibleRows = combinationTotalRows = combinationOffset = 0;
            }
            int footer = height - 25;
            Button apply = button(x + Math.max(0, w - 250), footer, 78, "Apply", ignored -> apply(), "Validate, persist and publish this pool override.");
            apply.active = model.editable() && draft.dirty;
            Button revert = button(x + Math.max(84, w - 166), footer, 78, "Revert", ignored -> revert(), "Remove this world override and restore its source definition.");
            revert.active = model.editable() && draft.source.overridden();
            button(x + Math.max(168, w - 82), footer, 82, compactDetail ? "Pool List" : "Cancel", ignored -> {
                if (compactDetail && draft.dirty) {
                    minecraft.setScreen(new LeaveDraftScreen(this, this::discardToPoolList));
                } else if (compactDetail) {
                    discardToPoolList();
                }
                else back();
            }, null);
        }
        private void discardToPoolList() {
            if (draft != null) draft.dirty = false;
            compactDetail = false;
            draft = null;
            rebuildWidgets();
        }
        private void swapMutatorWeights(List<WorldAwakenedMutatorWeightList.Entry> entries, int first, int second) {
            if (first < 0 || second < 0 || first >= entries.size() || second >= entries.size()) return;
            if (WorldAwakenedMutatorWeightList.swapWeights(draft.weights,
                    entries.get(first).id(), entries.get(second).id())) {
                draft.dirty = true;
                rebuildWidgets();
            }
        }
        private void apply() {
            reopenPoolId = draft.id;
            PacketDistributor.sendToServer(new WorldAwakenedApplyMutationPoolPayload(model.profileRevision(), draft.json().toString(), false));
        }
        private void revert() {
            reopenPoolId = draft.id;
            PacketDistributor.sendToServer(new WorldAwakenedApplyMutationPoolPayload(model.profileRevision(), draft.json().toString(), true));
        }
        private void back() {
            if (draft != null && draft.dirty) minecraft.setScreen(new LeaveDraftScreen(this,
                    () -> { draft.dirty = false; minecraft.setScreen(new DashboardScreen(model)); }));
            else minecraft.setScreen(new DashboardScreen(model));
        }
        @Override public void onClose() { back(); }
        @Override public boolean mouseScrolled(double mx, double my, double dx, double dy) {
            if (contains(poolViewportX, poolViewportY, poolViewportWidth, poolViewportHeight, mx, my)
                    && poolTotalRows > poolVisibleRows) {
                offset = clampOffset(offset + (dy < 0 ? 1 : -1), poolTotalRows, poolVisibleRows);
                rebuildWidgets(); return true;
            }
            if (contains(mutatorViewportX, mutatorViewportY, mutatorViewportWidth, mutatorViewportHeight, mx, my)
                    && mutatorTotalRows > mutatorVisibleRows) {
                mutatorOffset = clampOffset(mutatorOffset + (dy < 0 ? 1 : -1), mutatorTotalRows, mutatorVisibleRows);
                rebuildWidgets(); return true;
            }
            if (contains(combinationViewportX, combinationViewportY, combinationViewportWidth,
                    combinationViewportHeight, mx, my) && combinationTotalRows > combinationVisibleRows) {
                combinationOffset = clampOffset(combinationOffset + (dy < 0 ? 1 : -1),
                        combinationTotalRows, combinationVisibleRows);
                rebuildWidgets(); return true;
            }
            return super.mouseScrolled(mx, my, dx, dy);
        }
        @Override public boolean mouseClicked(double mx, double my, int button) {
            if (button == 0 && beginScrollbarDrag(true, mx, my)) return true;
            if (button == 0 && beginScrollbarDrag(false, mx, my)) return true;
            if (button == 0 && beginCombinationScrollbarDrag(mx, my)) return true;
            return super.mouseClicked(mx, my, button);
        }
        @Override public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
            if (button == 0 && draggingPoolScrollbar) { updateScrollbarFromMouse(true, my); return true; }
            if (button == 0 && draggingMutatorScrollbar) { updateScrollbarFromMouse(false, my); return true; }
            if (button == 0 && draggingCombinationScrollbar) { updateCombinationScrollbarFromMouse(my); return true; }
            return super.mouseDragged(mx, my, button, dx, dy);
        }
        @Override public boolean mouseReleased(double mx, double my, int button) {
            draggingPoolScrollbar = false;
            draggingMutatorScrollbar = false;
            draggingCombinationScrollbar = false;
            return super.mouseReleased(mx, my, button);
        }
        private boolean beginScrollbarDrag(boolean pool, double mx, double my) {
            int total = pool ? poolTotalRows : mutatorTotalRows;
            int visible = pool ? poolVisibleRows : mutatorVisibleRows;
            if (total <= visible) return false;
            int x = pool ? poolViewportX + poolViewportWidth - 6 : mutatorViewportX + mutatorViewportWidth - 6;
            int y = pool ? poolViewportY : mutatorViewportY;
            int h = pool ? poolViewportHeight : mutatorViewportHeight;
            if (!contains(x - 2, y, 8, h, mx, my)) return false;
            ScrollbarMetrics metrics = scrollbarMetrics(y, h, total, visible, pool ? offset : mutatorOffset);
            scrollbarGrabOffset = my >= metrics.thumbY() && my <= metrics.thumbY() + metrics.thumbHeight()
                    ? my - metrics.thumbY() : metrics.thumbHeight() / 2.0D;
            draggingPoolScrollbar = pool;
            draggingMutatorScrollbar = !pool;
            updateScrollbarFromMouse(pool, my);
            return true;
        }
        private void updateScrollbarFromMouse(boolean pool, double mouseY) {
            int total = pool ? poolTotalRows : mutatorTotalRows;
            int visible = pool ? poolVisibleRows : mutatorVisibleRows;
            int y = pool ? poolViewportY : mutatorViewportY;
            int h = pool ? poolViewportHeight : mutatorViewportHeight;
            ScrollbarMetrics metrics = scrollbarMetrics(y, h, total, visible, pool ? offset : mutatorOffset);
            int travel = Math.max(1, h - metrics.thumbHeight());
            double position = Math.max(0, Math.min(travel, mouseY - y - scrollbarGrabOffset));
            int next = (int)Math.round(position / travel * Math.max(0, total - visible));
            if (pool && next != offset) { offset = next; rebuildWidgets(); }
            else if (!pool && next != mutatorOffset) { mutatorOffset = next; rebuildWidgets(); }
        }
        private boolean beginCombinationScrollbarDrag(double mx, double my) {
            if (combinationTotalRows <= combinationVisibleRows) return false;
            int x = combinationViewportX + combinationViewportWidth - 6;
            if (!contains(x - 2, combinationViewportY, 8, combinationViewportHeight, mx, my)) return false;
            ScrollbarMetrics metrics = scrollbarMetrics(combinationViewportY, combinationViewportHeight,
                    combinationTotalRows, combinationVisibleRows, combinationOffset);
            scrollbarGrabOffset = my >= metrics.thumbY() && my <= metrics.thumbY() + metrics.thumbHeight()
                    ? my - metrics.thumbY() : metrics.thumbHeight() / 2.0D;
            draggingCombinationScrollbar = true;
            updateCombinationScrollbarFromMouse(my);
            return true;
        }
        private void updateCombinationScrollbarFromMouse(double mouseY) {
            ScrollbarMetrics metrics = scrollbarMetrics(combinationViewportY, combinationViewportHeight,
                    combinationTotalRows, combinationVisibleRows, combinationOffset);
            int travel = Math.max(1, combinationViewportHeight - metrics.thumbHeight());
            double position = Math.max(0, Math.min(travel,
                    mouseY - combinationViewportY - scrollbarGrabOffset));
            int next = (int)Math.round(position / travel
                    * Math.max(0, combinationTotalRows - combinationVisibleRows));
            if (next != combinationOffset) { combinationOffset = next; rebuildWidgets(); }
        }
        private static int clampOffset(int value, int total, int visible) {
            return WorldAwakenedEditorListLayout.clampOffset(value, total, visible);
        }
        private static boolean contains(int x, int y, int w, int h, double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
        @Override public void render(GuiGraphics graphics, int mx, int my, float tick) {
            renderBackground(graphics, mx, my, tick); header(graphics, "Mob Mutations", "Profile Editor > Mob Mutations");
            boolean compact = width < 620;
            int left = panelLeft(); int w = panelWidth();
            int browserW = compact ? w : Math.min(290, Math.max(230, w * 36 / 100));
            int contentBottom = height - 31;
            if (!model.message().isBlank()) {
                drawMarqueeLeft(graphics, font, Component.literal(model.message()), panelLeft(), 38, panelWidth(),
                        "applied".equals(model.status()) ? 0x66FF66 : 0xFF7777);
            }
            if (!compact || !compactDetail) panel(graphics, left, CONTENT_TOP, browserW, contentBottom - CONTENT_TOP);
            if (draft != null && (!compact || compactDetail)) {
                int x = compact ? left : left + browserW + 10; int dw = compact ? w : w - browserW - 10;
                panel(graphics, x, CONTENT_TOP, dw, contentBottom - CONTENT_TOP);
                int innerX = x + 6; int innerW = dw - 12;
                drawMarqueeLeft(graphics, font, Component.literal(draft.name + (draft.dirty ? "  • Unsaved" : "")),
                        innerX, CONTENT_TOP + 5, Math.max(30, innerW - 110), 0xFFFFFF);
                drawMarqueeLeft(graphics, font, Component.literal(draft.source.origin()
                        + (draft.source.overridden() ? "  • World override" : "")
                        + "  • Pool selection weight " + draft.selectionWeight),
                        innerX, CONTENT_TOP + 16, Math.max(30, innerW - 110), 0xAAAAAA);
                int sectionX = x + 5;
                int sectionW = dw - 10;
                int sectionInnerX = sectionX + 6;
                int sectionInnerW = sectionW - 12;
                int generalSectionY = CONTENT_TOP + DETAIL_HEADER_HEIGHT;
                int generalSectionHeight = generalSectionHeight(sectionInnerW);
                int eligibilitySectionY = generalSectionY + generalSectionHeight + SECTION_GAP;
                int mutatorSectionY = eligibilitySectionY + ELIGIBILITY_SECTION_HEIGHT + SECTION_GAP;
                int availableSections = Math.max(92, contentBottom - mutatorSectionY - 5);
                int mutatorSectionHeight = Math.max(48, availableSections * 55 / 100);
                int combinationSectionY = mutatorSectionY + mutatorSectionHeight + SECTION_GAP;
                int combinationSectionHeight = Math.max(38, contentBottom - combinationSectionY - 5);
                sectionPanel(graphics, sectionX, generalSectionY, sectionW, generalSectionHeight);
                sectionPanel(graphics, sectionX, eligibilitySectionY, sectionW, ELIGIBILITY_SECTION_HEIGHT);
                sectionPanel(graphics, sectionX, mutatorSectionY, sectionW, mutatorSectionHeight);
                sectionPanel(graphics, sectionX, combinationSectionY, sectionW, combinationSectionHeight);
                drawSectionLabel(graphics, "GENERAL", sectionInnerX, generalSectionY + 5, sectionInnerW);
                graphics.drawString(font, "EXPECTED MUTATIONS", sectionInnerX, generalSectionY + 43, 0xD8B84A, false);
                var previewLines = font.split(Component.literal(compactDistributionSummary(
                        draft.chance, draft.additionalChance, draft.maximum)), sectionInnerW);
                for (int line = 0; line < previewLines.size(); line++) {
                    graphics.drawString(font, previewLines.get(line), sectionInnerX,
                            generalSectionY + 55 + line * font.lineHeight, 0xC8C8C8, false);
                }
                graphics.drawString(font, "Theoretical before eligibility and compatibility.",
                        sectionInnerX, generalSectionY + 57 + previewLines.size() * font.lineHeight,
                        0x888888, false);
                drawSectionLabel(graphics, "ELIGIBILITY", sectionInnerX, eligibilitySectionY + 5, sectionInnerW);
                String stages = stageContexts(draft.source.definition()).stream()
                        .map(WorldAwakenedClientConfigPayloadHandler::readableId)
                        .reduce((a, b) -> a + ", " + b).orElse("Any stage");
                drawMarqueeLeft(graphics, font, Component.literal("Applicable stages: " + stages), sectionInnerX,
                        eligibilitySectionY + 43, sectionInnerW, 0xAAAAAA);
                drawSectionLabel(graphics, "MUTATORS", sectionInnerX, mutatorSectionY + 7,
                        Math.max(40, sectionInnerW - 122));
                drawMarqueeLeft(graphics, font,
                        Component.literal("Higher entries have a larger relative selection share."),
                        sectionInnerX, mutatorSectionY + 20, Math.max(40, sectionInnerW - 122), 0xAAAAAA);
                renderMutatorRows(graphics);
                drawSectionLabel(graphics, "RARE COMBINATIONS", sectionInnerX,
                        combinationSectionY + 5, Math.max(40, sectionInnerW - 160));
                if (!draft.curated) {
                    drawMarqueeLeft(graphics, font,
                            Component.literal("Additional mutation attempts choose any compatible weighted mutator from this pool."),
                            sectionInnerX, combinationSectionY + 43, sectionInnerW, 0xAAAAAA);
                } else {
                    drawMarqueeLeft(graphics, font,
                            Component.literal("Only listed combinations are used; shares vary with mob eligibility."),
                            sectionInnerX, combinationSectionY + 43, Math.max(30, sectionInnerW - 122), 0xAAAAAA);
                    renderCombinationRows(graphics);
                }
            }
            if (poolTotalRows > poolVisibleRows) renderScrollbar(graphics,
                    poolViewportX + poolViewportWidth - 6, poolViewportY, poolViewportHeight,
                    poolTotalRows, poolVisibleRows, offset);
            if (mutatorTotalRows > mutatorVisibleRows) renderScrollbar(graphics,
                    mutatorViewportX + mutatorViewportWidth - 6, mutatorViewportY, mutatorViewportHeight,
                    mutatorTotalRows, mutatorVisibleRows, mutatorOffset);
            if (combinationTotalRows > combinationVisibleRows) renderScrollbar(graphics,
                    combinationViewportX + combinationViewportWidth - 6, combinationViewportY,
                    combinationViewportHeight, combinationTotalRows, combinationVisibleRows, combinationOffset);
            renderWidgets(graphics, mx, my, tick);
        }
        private void drawSectionLabel(GuiGraphics graphics, String label, int x, int y, int width) {
            drawMarqueeLeft(graphics, font, Component.literal(label).withStyle(ChatFormatting.GOLD), x, y, width, 0xFFFFFF);
        }
        private void renderMutatorRows(GuiGraphics graphics) {
            List<WorldAwakenedMutatorWeightList.Entry> entries = WorldAwakenedMutatorWeightList.ordered(draft.weights);
            long totalWeight = WorldAwakenedMutatorWeightList.totalWeight(draft.weights);
            int visible = Math.min(mutatorVisibleRows, Math.max(0, entries.size() - mutatorOffset));
            for (int row = 0; row < visible; row++) {
                int entryIndex = mutatorOffset + row;
                WorldAwakenedMutatorWeightList.Entry entry = entries.get(entryIndex);
                MutatorModel mutator = model.mutator(entry.id());
                String name = mutator == null ? readableId(entry.id()) : mutator.name();
                double share = WorldAwakenedMutatorWeightList.relativeShare(entry.weight(), totalWeight);
                int rowY = mutatorViewportY + row * MUTATOR_ROW_HEIGHT;
                int rowHeight = MUTATOR_ROW_HEIGHT - 3;
                graphics.fill(mutatorViewportX, rowY, mutatorViewportX + mutatorRowWidth, rowY + rowHeight, 0xB02A2A2A);
                graphics.fill(mutatorViewportX, rowY, mutatorViewportX + mutatorRowWidth, rowY + 1, 0xFF606060);
                graphics.fill(mutatorViewportX, rowY + rowHeight - 1,
                        mutatorViewportX + mutatorRowWidth, rowY + rowHeight, 0xFF383838);
                graphics.fill(mutatorViewportX, rowY, mutatorViewportX + 1, rowY + rowHeight, 0xFF606060);
                graphics.fill(mutatorViewportX + mutatorRowWidth - 1, rowY,
                        mutatorViewportX + mutatorRowWidth, rowY + rowHeight, 0xFF383838);
                String summary = String.format(Locale.ROOT, "%d. %s   %.1f%%", entryIndex + 1, name, share);
                drawMarqueeLeft(graphics, font, Component.literal(summary), mutatorViewportX + 7, rowY + 9,
                        Math.max(20, mutatorLabelWidth - 12), 0xFFFFFF);
            }
        }
        private void renderCombinationRows(GuiGraphics graphics) {
            long totalWeight = draft.combinations.stream().mapToLong(combination -> combination.weight).sum();
            int visible = Math.min(combinationVisibleRows,
                    Math.max(0, draft.combinations.size() - combinationOffset));
            for (int row = 0; row < visible; row++) {
                CombinationDraft combination = draft.combinations.get(combinationOffset + row);
                int rowY = combinationViewportY + row * COMBINATION_ROW_HEIGHT;
                int rowWidth = combinationViewportWidth
                        - (combinationTotalRows > combinationVisibleRows ? 8 : 0);
                int rowHeight = COMBINATION_ROW_HEIGHT - 3;
                graphics.fill(combinationViewportX, rowY, combinationViewportX + rowWidth,
                        rowY + rowHeight, 0xB02A2A2A);
                graphics.fill(combinationViewportX, rowY, combinationViewportX + rowWidth,
                        rowY + 1, 0xFF606060);
                graphics.fill(combinationViewportX, rowY + rowHeight - 1,
                        combinationViewportX + rowWidth, rowY + rowHeight, 0xFF383838);
                graphics.fill(combinationViewportX, rowY,
                        combinationViewportX + 1, rowY + rowHeight, 0xFF606060);
                graphics.fill(combinationViewportX + rowWidth - 1, rowY,
                        combinationViewportX + rowWidth, rowY + rowHeight, 0xFF383838);
                double share = totalWeight <= 0 ? 0.0D : combination.weight * 100.0D / totalWeight;
                String names = combination.mutators.stream().map(id -> {
                    MutatorModel mutator = model.mutator(id);
                    return mutator == null ? readableId(id) : mutator.name();
                }).reduce((a, b) -> a + " + " + b).orElse("Empty combination");
                int labelWidth = Math.max(20, rowWidth - 95);
                drawMarqueeLeft(graphics, font, Component.literal(names), combinationViewportX + 7,
                        rowY + 6, labelWidth, 0xFFFFFF);
                String mobCount = combination.entities.isEmpty() ? "Parent pool mobs"
                        : combination.entities.size() + (combination.entities.size() == 1 ? " eligible mob" : " eligible mobs");
                String details = String.format(Locale.ROOT, "%.1f%% relative share  ·  %s  ·  Weight %d",
                        share, mobCount, combination.weight);
                drawMarqueeLeft(graphics, font, Component.literal(details), combinationViewportX + 7,
                        rowY + 19, labelWidth, 0xAAAAAA);
            }
        }

        private int generalSectionHeight(int availableWidth) {
            int lines = font.split(Component.literal(compactDistributionSummary(
                    draft.chance, draft.additionalChance, draft.maximum)), Math.max(40, availableWidth)).size();
            return Math.max(GENERAL_SECTION_HEIGHT, 70 + lines * font.lineHeight);
        }
        private void sectionPanel(GuiGraphics graphics, int x, int y, int width, int height) {
            graphics.fill(x, y, x + width, y + height, 0x50101010);
            graphics.fill(x, y, x + width, y + 1, 0xFF666666);
            graphics.fill(x, y + height - 1, x + width, y + height, 0xFF333333);
            graphics.fill(x, y, x + 1, y + height, 0xFF666666);
            graphics.fill(x + width - 1, y, x + width, y + height, 0xFF333333);
        }
    }

    private static final class PoolRowButton extends AbstractButton {
        private final PoolModel pool;
        private final boolean selected;
        private final boolean dirty;
        private final Button.OnPress onPress;

        PoolRowButton(int x, int y, int width, int height, PoolModel pool, boolean selected, boolean dirty,
                Button.OnPress onPress) {
            super(x, y, width, height, Component.literal(pool.name()));
            this.pool = pool;
            this.selected = selected;
            this.dirty = dirty;
            this.onPress = onPress;
        }

        @Override
        public void onPress() {
            onPress.onPress(null);
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int background = selected ? 0xE0446688 : isHoveredOrFocused() ? 0xD04A4A4A : 0xB02A2A2A;
            int border = selected ? 0xFF77AAFF : isHoveredOrFocused() ? 0xFF888888 : 0xFF505050;
            graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), background);
            graphics.fill(getX(), getY(), getX() + getWidth(), getY() + 1, border);
            graphics.fill(getX(), getY() + getHeight() - 1, getX() + getWidth(), getY() + getHeight(), border);
            graphics.fill(getX(), getY(), getX() + 1, getY() + getHeight(), border);
            graphics.fill(getX() + getWidth() - 1, getY(), getX() + getWidth(), getY() + getHeight(), border);
            if (selected) graphics.fill(getX(), getY(), getX() + 3, getY() + getHeight(), 0xFF77AAFF);

            boolean enabled = !pool.definition().has("enabled") || pool.definition().get("enabled").getAsBoolean();
            String state = (dirty ? "Unsaved  " : "") + (enabled ? "On" : "Off");
            int stateWidth = Minecraft.getInstance().font.width(state) + 8;
            drawMarqueeLeft(graphics, Minecraft.getInstance().font, Component.literal(pool.name()),
                    getX() + 7, getY() + 5, Math.max(20, getWidth() - stateWidth - 13), 0xFFFFFF);
            graphics.drawString(Minecraft.getInstance().font, state,
                    getX() + getWidth() - stateWidth + 2, getY() + 5,
                    dirty ? 0xFFDD66 : enabled ? 0x77DD77 : 0xDD7777, false);
            drawMarqueeLeft(graphics, Minecraft.getInstance().font,
                    Component.literal(context(pool.definition()).replace(" | ", " · ")),
                    getX() + 7, getY() + 18, getWidth() - 14, 0xAAAAAA);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    private record ScrollbarMetrics(int thumbY, int thumbHeight) {}

    private static ScrollbarMetrics scrollbarMetrics(int y, int height, int total, int visible, int offset) {
        int thumbHeight = Math.max(16, Math.min(height, Math.round(height * visible / (float)Math.max(1, total))));
        int travel = Math.max(0, height - thumbHeight);
        int maximumOffset = Math.max(1, total - visible);
        int thumbY = y + Math.round(travel * offset / (float)maximumOffset);
        return new ScrollbarMetrics(thumbY, thumbHeight);
    }

    private static void renderScrollbar(GuiGraphics graphics, int x, int y, int height,
            int total, int visible, int offset) {
        ScrollbarMetrics metrics = scrollbarMetrics(y, height, total, visible, offset);
        graphics.fill(x, y, x + 5, y + height, 0xA0202020);
        graphics.fill(x + 1, metrics.thumbY(), x + 4, metrics.thumbY() + metrics.thumbHeight(), 0xFFD0D0D0);
    }

    /** Small shared viewport state for the editor's compact selectable lists. */
    private static final class ScrollableRows {
        int x, y, width, height, rowHeight, total, visible, offset;
        boolean dragging;
        double grabOffset;

        void configure(int x, int y, int width, int height, int rowHeight, int total) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = Math.max(1, height);
            this.rowHeight = rowHeight;
            this.total = total;
            this.visible = WorldAwakenedEditorListLayout.visibleRows(this.height, rowHeight);
            this.offset = WorldAwakenedEditorListLayout.clampOffset(offset, total, visible);
        }

        boolean overflows() { return WorldAwakenedEditorListLayout.needsScrollbar(total, visible); }
        int rowWidth() { return Math.max(1, width - (overflows() ? 8 : 0)); }
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
        boolean scroll(double delta) {
            if (!overflows()) return false;
            int next = WorldAwakenedEditorListLayout.clampOffset(
                    offset + (delta < 0 ? 1 : -1), total, visible);
            if (next == offset) return false;
            offset = next;
            return true;
        }
        boolean beginDrag(double mouseX, double mouseY) {
            if (!overflows()) return false;
            int scrollbarX = x + width - 6;
            if (mouseX < scrollbarX - 2 || mouseX >= scrollbarX + 6 || mouseY < y || mouseY >= y + height) {
                return false;
            }
            ScrollbarMetrics metrics = scrollbarMetrics(y, height, total, visible, offset);
            grabOffset = mouseY >= metrics.thumbY() && mouseY <= metrics.thumbY() + metrics.thumbHeight()
                    ? mouseY - metrics.thumbY() : metrics.thumbHeight() / 2.0D;
            dragging = true;
            drag(mouseY);
            return true;
        }
        boolean drag(double mouseY) {
            if (!dragging) return false;
            ScrollbarMetrics metrics = scrollbarMetrics(y, height, total, visible, offset);
            int travel = Math.max(1, height - metrics.thumbHeight());
            double position = Math.max(0, Math.min(travel, mouseY - y - grabOffset));
            int next = (int)Math.round(position / travel * Math.max(0, total - visible));
            if (next == offset) return false;
            offset = next;
            return true;
        }
        void release() { dragging = false; }
        void render(GuiGraphics graphics) {
            if (overflows()) renderScrollbar(graphics, x + width - 6, y, height, total, visible, offset);
        }
    }

    private static final class SelectionRowButton extends AbstractButton {
        private final String primary;
        private final String secondary;
        private final boolean selected;
        private final Button.OnPress onPress;

        SelectionRowButton(int x, int y, int width, int height, String primary, String secondary,
                boolean selected, Button.OnPress onPress) {
            super(x, y, width, height, Component.literal(primary));
            this.primary = primary;
            this.secondary = secondary;
            this.selected = selected;
            this.onPress = onPress;
            setTooltip(Tooltip.create(Component.literal(primary + "\n" + secondary)));
        }

        @Override public void onPress() { onPress.onPress(null); }

        @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int background = selected ? 0xD03B5268 : isHoveredOrFocused() ? 0xD0454545 : 0xB02A2A2A;
            int border = selected ? 0xFF77AAFF : isHoveredOrFocused() ? 0xFF888888 : 0xFF505050;
            graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), background);
            graphics.fill(getX(), getY(), getX() + getWidth(), getY() + 1, border);
            graphics.fill(getX(), getY() + getHeight() - 1, getX() + getWidth(), getY() + getHeight(), border);
            graphics.fill(getX(), getY(), getX() + 1, getY() + getHeight(), border);
            graphics.fill(getX() + getWidth() - 1, getY(), getX() + getWidth(), getY() + getHeight(), border);
            if (selected) graphics.fill(getX(), getY(), getX() + 3, getY() + getHeight(), 0xFF77AAFF);
            Font font = Minecraft.getInstance().font;
            graphics.drawString(font, selected ? "[x]" : "[ ]", getX() + 7, getY() + 6, 0xFFFFFF, false);
            int textX = getX() + 34;
            drawMarqueeLeft(graphics, font, Component.literal(primary), textX, getY() + 5,
                    Math.max(20, getWidth() - 41), 0xFFFFFF);
            drawMarqueeLeft(graphics, font, Component.literal(secondary), textX, getY() + 18,
                    Math.max(20, getWidth() - 41), 0xAAAAAA);
        }

        @Override protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    private static final class PoolDraft {
        final PoolModel source; final String id; final String name; final int selectionWeight;
        boolean enabled; double chance; double additionalChance; int maximum; boolean curated; boolean dirty;
        final Set<String> entities = new LinkedHashSet<>();
        final Set<String> dimensions = new LinkedHashSet<>();
        final LinkedHashMap<String,Integer> weights = new LinkedHashMap<>();
        final List<CombinationDraft> combinations = new ArrayList<>();
        PoolDraft(PoolModel source) {
            this.source = source; id = source.id(); name = source.name(); JsonObject json = source.definition();
            enabled = !json.has("enabled") || json.get("enabled").getAsBoolean();
            chance = json.has("mutation_chance") ? json.get("mutation_chance").getAsDouble() : 1;
            additionalChance = json.has("additional_mutation_chance")
                    ? json.get("additional_mutation_chance").getAsDouble() : 0;
            maximum = json.has("max_mutators_per_entity") ? json.get("max_mutators_per_entity").getAsInt() : 1;
            curated = json.has("combination_mode") && "curated".equals(json.get("combination_mode").getAsString());
            selectionWeight = json.has("weight") ? json.get("weight").getAsInt() : 1;
            if (json.has("eligible_entities")) json.getAsJsonArray("eligible_entities").forEach(v -> entities.add(v.getAsString()));
            if (json.has("eligible_dimensions")) json.getAsJsonArray("eligible_dimensions").forEach(v -> dimensions.add(v.getAsString()));
            json.getAsJsonArray("mutators").forEach(v -> {
                if (v.isJsonPrimitive()) weights.put(v.getAsString(), 1);
                else weights.put(v.getAsJsonObject().get("id").getAsString(), v.getAsJsonObject().has("weight") ? v.getAsJsonObject().get("weight").getAsInt() : 1);
            });
            if (json.has("rare_combinations")) json.getAsJsonArray("rare_combinations").forEach(value ->
                    combinations.add(CombinationDraft.fromJson(value.getAsJsonObject())));
        }
        JsonObject json() {
            JsonObject json = source.definition().deepCopy();
            json.addProperty("enabled", enabled); json.addProperty("mutation_chance", chance);
            json.addProperty("additional_mutation_chance", maximum > 1 ? additionalChance : 0.0D);
            json.addProperty("max_mutators_per_entity", maximum);
            json.addProperty("combination_mode", curated ? "curated" : "any_compatible");
            JsonArray entityArray = new JsonArray(); entities.forEach(entityArray::add); json.add("eligible_entities", entityArray);
            JsonArray dimensionArray = new JsonArray(); dimensions.forEach(dimensionArray::add); json.add("eligible_dimensions", dimensionArray);
            JsonArray mutatorArray = new JsonArray(); weights.forEach((id, weight) -> { JsonObject node = new JsonObject(); node.addProperty("id", id); node.addProperty("weight", weight); mutatorArray.add(node); });
            json.add("mutators", mutatorArray);
            JsonArray combinationArray = new JsonArray();
            combinations.forEach(combination -> combinationArray.add(combination.toJson()));
            json.add("rare_combinations", combinationArray);
            return json;
        }
    }

    private static final class CombinationDraft {
        String id;
        int weight;
        final LinkedHashSet<String> mutators = new LinkedHashSet<>();
        final LinkedHashSet<String> entities = new LinkedHashSet<>();

        CombinationDraft(String id, int weight) { this.id = id; this.weight = weight; }

        static CombinationDraft fromJson(JsonObject json) {
            CombinationDraft draft = new CombinationDraft(json.get("id").getAsString(),
                    json.has("weight") ? json.get("weight").getAsInt() : 1);
            json.getAsJsonArray("mutators").forEach(value -> draft.mutators.add(value.getAsString()));
            if (json.has("eligible_entities")) json.getAsJsonArray("eligible_entities")
                    .forEach(value -> draft.entities.add(value.getAsString()));
            return draft;
        }

        CombinationDraft copy() {
            CombinationDraft copy = new CombinationDraft(id, weight);
            copy.mutators.addAll(mutators);
            copy.entities.addAll(entities);
            return copy;
        }

        JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("id", id);
            json.addProperty("weight", weight);
            JsonArray mutatorArray = new JsonArray(); mutators.forEach(mutatorArray::add);
            JsonArray entityArray = new JsonArray(); entities.forEach(entityArray::add);
            json.add("mutators", mutatorArray);
            json.add("eligible_entities", entityArray);
            return json;
        }
    }

    private static final class EntitySelectorScreen extends EditorScreen {
        private final MutationPoolsScreen parent; private final PoolDraft draft; private final Set<String> selected;
        private static final int ROW_HEIGHT = 36;
        private final ScrollableRows list = new ScrollableRows();
        private EditBox search; private String query = "";
        EntitySelectorScreen(ClientModel model, MutationPoolsScreen parent, PoolDraft draft) {
            super("Select Mobs", model); this.parent = parent; this.draft = draft; selected = new LinkedHashSet<>(draft.entities);
        }
        @Override protected void init() {
            clearWidgets(); int left = panelLeft(); int w = panelWidth();
            int panelTop = 39; int panelBottom = height - 45;
            search = new EditBox(font, left + 6, panelTop + 18, w - 12, 20, Component.literal("Search mobs or mod IDs"));
            search.setHint(Component.literal("Search mobs or mod IDs...").withStyle(ChatFormatting.DARK_GRAY));
            search.setValue(query); search.setResponder(value -> { query = value; list.offset = 0; rebuildWidgets(); }); addRenderableWidget(search);
            setInitialFocus(search);
            List<EntityModel> shown = filtered();
            list.configure(left + 6, panelTop + 43, w - 12, panelBottom - panelTop - 48, ROW_HEIGHT, shown.size());
            for (int row = 0; row < Math.min(list.visible, Math.max(0, shown.size() - list.offset)); row++) {
                EntityModel entity = shown.get(list.offset + row); boolean checked = selected.contains(entity.id());
                SelectionRowButton entry = new SelectionRowButton(list.x, list.y + row * ROW_HEIGHT,
                        list.rowWidth(), ROW_HEIGHT - 2, entity.name(), entitySecondary(entity), checked, ignored -> {
                    if (!selected.add(entity.id())) selected.remove(entity.id()); rebuildWidgets();
                });
                entry.setTooltip(Tooltip.create(Component.literal("Choose whether " + entity.name()
                        + " can receive mutations.\n" + entity.id())));
                addRenderableWidget(entry);
            }
            button(width / 2 - 104, height - 25, 100, "Confirm", ignored -> { draft.entities.clear(); draft.entities.addAll(selected); draft.dirty = true; minecraft.setScreen(parent); }, null);
            button(width / 2 + 4, height - 25, 100, "Cancel", ignored -> minecraft.setScreen(parent), null);
        }
        private List<EntityModel> filtered() {
            String q = query.toLowerCase(Locale.ROOT);
            return model.entities().stream().filter(entity -> entity.name().toLowerCase(Locale.ROOT).contains(q)
                    || entity.id().toLowerCase(Locale.ROOT).contains(q) || entity.namespace().toLowerCase(Locale.ROOT).contains(q)).toList();
        }
        @Override public boolean mouseScrolled(double mx, double my, double dx, double dy) {
            if (list.contains(mx, my) && list.scroll(dy)) { rebuildWidgets(); return true; }
            return super.mouseScrolled(mx, my, dx, dy);
        }
        @Override public boolean mouseClicked(double mx, double my, int button) {
            if (button == 0 && list.beginDrag(mx, my)) { rebuildWidgets(); return true; }
            return super.mouseClicked(mx, my, button);
        }
        @Override public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
            if (button == 0 && list.dragging) { if (list.drag(my)) rebuildWidgets(); return true; }
            return super.mouseDragged(mx, my, button, dx, dy);
        }
        @Override public boolean mouseReleased(double mx, double my, int button) {
            list.release(); return super.mouseReleased(mx, my, button);
        }
        @Override public void onClose() { minecraft.setScreen(parent); }
        @Override public void render(GuiGraphics graphics, int mx, int my, float tick) {
            renderBackground(graphics, mx, my, tick); header(graphics, "Select Mobs", "Mob Mutations > " + draft.name + " > Eligible Mobs");
            panel(graphics, panelLeft(), 39, panelWidth(), height - 84);
            graphics.drawString(font, "MOBS", panelLeft() + 6, 45, 0xD8B84A, false);
            graphics.drawString(font, selected.size() + (selected.size() == 1 ? " mob selected" : " mobs selected"),
                    panelLeft(), height - 39, 0xBBBBBB, false);
            list.render(graphics); renderWidgets(graphics, mx, my, tick);
        }
    }

    private static final class DimensionSelectorScreen extends EditorScreen {
        private final MutationPoolsScreen parent; private final PoolDraft draft; private final Set<String> selected;
        private static final int ROW_HEIGHT = 36;
        private final ScrollableRows list = new ScrollableRows();
        private EditBox search;
        private String query = "";
        DimensionSelectorScreen(ClientModel model, MutationPoolsScreen parent, PoolDraft draft) {
            super("Select Dimensions", model); this.parent = parent; this.draft = draft;
            selected = new LinkedHashSet<>(draft.dimensions);
        }
        @Override protected void init() {
            clearWidgets(); int left = panelLeft(); int w = panelWidth();
            int panelTop = 39; int panelBottom = height - 45;
            search = new EditBox(font, left + 6, panelTop + 18, w - 12, 20, Component.literal("Search dimensions"));
            search.setHint(Component.literal("Search dimensions...").withStyle(ChatFormatting.DARK_GRAY));
            search.setValue(query); search.setResponder(value -> { query = value; list.offset = 0; rebuildWidgets(); });
            addRenderableWidget(search);
            setInitialFocus(search);
            List<String> shown = filtered();
            list.configure(left + 6, panelTop + 43, w - 12, panelBottom - panelTop - 48, ROW_HEIGHT, shown.size());
            for (int row = 0; row < Math.min(list.visible, Math.max(0, shown.size() - list.offset)); row++) {
                String dimension = shown.get(list.offset + row);
                boolean checked = selected.contains(dimension);
                SelectionRowButton entry = new SelectionRowButton(list.x, list.y + row * ROW_HEIGHT,
                        list.rowWidth(), ROW_HEIGHT - 2, readableId(dimension), dimension, checked, ignored -> {
                    if (!selected.add(dimension)) selected.remove(dimension); rebuildWidgets();
                });
                entry.setTooltip(Tooltip.create(Component.literal("Choose whether this pool applies in "
                        + readableId(dimension) + ".\n" + dimension)));
                addRenderableWidget(entry);
            }
            button(width / 2 - 104, height - 25, 100, "Confirm", ignored -> {
                draft.dimensions.clear(); draft.dimensions.addAll(selected); draft.dirty = true; minecraft.setScreen(parent);
            }, null);
            button(width / 2 + 4, height - 25, 100, "Cancel", ignored -> minecraft.setScreen(parent), null);
        }
        private List<String> filtered() {
            String lowered = query.toLowerCase(Locale.ROOT);
            return model.dimensions().stream().filter(id -> id.toLowerCase(Locale.ROOT).contains(lowered)
                    || readableId(id).toLowerCase(Locale.ROOT).contains(lowered)).toList();
        }
        @Override public boolean mouseScrolled(double mx, double my, double dx, double dy) {
            if (list.contains(mx, my) && list.scroll(dy)) { rebuildWidgets(); return true; }
            return super.mouseScrolled(mx, my, dx, dy);
        }
        @Override public boolean mouseClicked(double mx, double my, int button) {
            if (button == 0 && list.beginDrag(mx, my)) { rebuildWidgets(); return true; }
            return super.mouseClicked(mx, my, button);
        }
        @Override public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
            if (button == 0 && list.dragging) { if (list.drag(my)) rebuildWidgets(); return true; }
            return super.mouseDragged(mx, my, button, dx, dy);
        }
        @Override public boolean mouseReleased(double mx, double my, int button) {
            list.release(); return super.mouseReleased(mx, my, button);
        }
        @Override public void onClose() { minecraft.setScreen(parent); }
        @Override public void render(GuiGraphics graphics, int mx, int my, float tick) {
            renderBackground(graphics, mx, my, tick); header(graphics, "Select Dimensions", "Mob Mutations > " + draft.name + " > Eligible Dimensions");
            panel(graphics, panelLeft(), 39, panelWidth(), height - 84);
            graphics.drawString(font, "DIMENSIONS", panelLeft() + 6, 45, 0xD8B84A, false);
            graphics.drawString(font, selected.size() + (selected.size() == 1 ? " dimension selected" : " dimensions selected"),
                    panelLeft(), height - 39, 0xBBBBBB, false);
            list.render(graphics); renderWidgets(graphics, mx, my, tick);
        }
    }

    private static final class MutatorSelectorScreen extends EditorScreen {
        private final MutationPoolsScreen parent; private final PoolDraft draft; private final LinkedHashMap<String,Integer> selected;
        private static final int ROW_HEIGHT = 36;
        private final ScrollableRows list = new ScrollableRows();
        private EditBox search; private String query = "";
        MutatorSelectorScreen(ClientModel model, MutationPoolsScreen parent, PoolDraft draft) {
            super("Select Mutators", model); this.parent = parent; this.draft = draft; selected = new LinkedHashMap<>(draft.weights);
        }
        @Override protected void init() {
            clearWidgets(); int left = panelLeft(); int w = panelWidth();
            int panelTop = 39; int panelBottom = height - 45;
            search = new EditBox(font, left + 6, panelTop + 18, w - 12, 20, Component.literal("Search mutators"));
            search.setHint(Component.literal("Search names, rarity or components...").withStyle(ChatFormatting.DARK_GRAY));
            search.setValue(query); search.setResponder(value -> { query = value; list.offset = 0; rebuildWidgets(); }); addRenderableWidget(search);
            setInitialFocus(search);
            List<MutatorModel> shown = filtered();
            list.configure(left + 6, panelTop + 43, w - 12, panelBottom - panelTop - 48, ROW_HEIGHT, shown.size());
            for (int row = 0; row < Math.min(list.visible, Math.max(0, shown.size() - list.offset)); row++) {
                MutatorModel mutator = shown.get(list.offset + row); boolean checked = selected.containsKey(mutator.id());
                String secondary = readableId(mutator.rarity()) + (mutator.components().isEmpty() ? ""
                        : " · " + String.join(", ", mutator.components()));
                SelectionRowButton entry = new SelectionRowButton(list.x, list.y + row * ROW_HEIGHT,
                        list.rowWidth(), ROW_HEIGHT - 2, mutator.name(), secondary, checked, ignored -> {
                    if (selected.containsKey(mutator.id())) selected.remove(mutator.id()); else selected.put(mutator.id(), 1); rebuildWidgets();
                });
                entry.setTooltip(Tooltip.create(Component.literal(mutatorTooltip(mutator))));
                addRenderableWidget(entry);
            }
            button(width / 2 - 104, height - 25, 100, "Confirm", ignored -> { draft.weights.clear(); draft.weights.putAll(selected); draft.dirty = true; minecraft.setScreen(parent); }, null);
            button(width / 2 + 4, height - 25, 100, "Cancel", ignored -> minecraft.setScreen(parent), null);
        }
        private List<MutatorModel> filtered() {
            String q = query.toLowerCase(Locale.ROOT);
            return model.mutators().stream().filter(m -> (m.name() + " " + m.id() + " " + m.rarity() + " " + String.join(" ", m.components())).toLowerCase(Locale.ROOT).contains(q)).toList();
        }
        @Override public boolean mouseScrolled(double mx, double my, double dx, double dy) {
            if (list.contains(mx, my) && list.scroll(dy)) { rebuildWidgets(); return true; }
            return super.mouseScrolled(mx, my, dx, dy);
        }
        @Override public boolean mouseClicked(double mx, double my, int button) {
            if (button == 0 && list.beginDrag(mx, my)) { rebuildWidgets(); return true; }
            return super.mouseClicked(mx, my, button);
        }
        @Override public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
            if (button == 0 && list.dragging) { if (list.drag(my)) rebuildWidgets(); return true; }
            return super.mouseDragged(mx, my, button, dx, dy);
        }
        @Override public boolean mouseReleased(double mx, double my, int button) {
            list.release(); return super.mouseReleased(mx, my, button);
        }
        @Override public void onClose() { minecraft.setScreen(parent); }
        @Override public void render(GuiGraphics graphics, int mx, int my, float tick) {
            renderBackground(graphics, mx, my, tick); header(graphics, "Select Mutators", "Mob Mutations > " + draft.name + " > Available Mutators");
            panel(graphics, panelLeft(), 39, panelWidth(), height - 84);
            graphics.drawString(font, "MUTATORS", panelLeft() + 6, 45, 0xD8B84A, false);
            graphics.drawString(font, selected.size() + (selected.size() == 1 ? " mutator selected" : " mutators selected"),
                    panelLeft(), height - 39, 0xBBBBBB, false);
            list.render(graphics); renderWidgets(graphics, mx, my, tick);
        }
    }

    private static final class CombinationEditorScreen extends EditorScreen {
        private static final int ROW_HEIGHT = 36;
        private final MutationPoolsScreen parent;
        private final PoolDraft pool;
        private final int editIndex;
        private final CombinationDraft draft;
        private final ScrollableRows mutatorList = new ScrollableRows();
        private final ScrollableRows entityList = new ScrollableRows();
        private EditBox weightInput;
        private String error = "";
        private boolean compactEntities;
        private boolean tabbed;
        private int generalY, generalHeight, mutatorSectionY, mutatorSectionHeight;
        private int entitySectionY, entitySectionHeight;

        CombinationEditorScreen(ClientModel model, MutationPoolsScreen parent, PoolDraft pool, int editIndex) {
            super(editIndex < 0 ? "Add Rare Combination" : "Edit Rare Combination", model);
            this.parent = parent;
            this.pool = pool;
            this.editIndex = editIndex;
            this.draft = editIndex < 0
                    ? new CombinationDraft(nextCombinationId(pool), 1)
                    : pool.combinations.get(editIndex).copy();
        }

        @Override protected void init() {
            clearWidgets();
            int left = panelLeft();
            int w = panelWidth();
            generalY = 39;
            generalHeight = 59;
            weightInput = new EditBox(font, left + w - 104, generalY + 18, 96, 20,
                    Component.literal("Combination weight"));
            weightInput.setMaxLength(10);
            weightInput.setFilter(value -> value.isEmpty() || value.chars().allMatch(Character::isDigit));
            weightInput.setValue(Integer.toString(draft.weight));
            weightInput.setResponder(ignored -> error = "");
            addRenderableWidget(weightInput);

            int sectionsTop = generalY + generalHeight + 6;
            int sectionsBottom = height - 45;
            int available = Math.max(1, sectionsBottom - sectionsTop);
            tabbed = available < 154;
            if (tabbed) {
                int tabWidth = Math.min(120, (w - 4) / 2);
                button(left, sectionsTop, tabWidth, "Mutators (" + draft.mutators.size() + ")", ignored -> {
                    compactEntities = false; rebuildWidgets();
                }, "Choose at least two mutations to combine.");
                button(left + tabWidth + 4, sectionsTop, tabWidth, "Eligible Mobs (" + draft.entities.size() + ")", ignored -> {
                    compactEntities = true; rebuildWidgets();
                }, "Leave empty to inherit the parent pool's eligible mobs.");
                if (compactEntities) {
                    entitySectionY = sectionsTop + 24; entitySectionHeight = Math.max(1, sectionsBottom - entitySectionY);
                    configureEntityRows(left, w);
                    mutatorSectionHeight = 0;
                } else {
                    mutatorSectionY = sectionsTop + 24; mutatorSectionHeight = Math.max(1, sectionsBottom - mutatorSectionY);
                    configureMutatorRows(left, w);
                    entitySectionHeight = 0;
                }
            } else {
                int firstHeight = (available - 6) / 2;
                mutatorSectionY = sectionsTop; mutatorSectionHeight = firstHeight;
                entitySectionY = sectionsTop + firstHeight + 6;
                entitySectionHeight = available - firstHeight - 6;
                configureMutatorRows(left, w);
                configureEntityRows(left, w);
            }
            button(width / 2 - 104, height - 25, 100, "Confirm", ignored -> confirm(),
                    "Validate and store this combination in the local pool draft.");
            button(width / 2 + 4, height - 25, 100, "Cancel", ignored -> minecraft.setScreen(parent),
                    "Discard changes to this combination.");
        }

        private void configureMutatorRows(int left, int width) {
            List<String> ids = new ArrayList<>(pool.weights.keySet());
            mutatorList.configure(left + 6, mutatorSectionY + 31, width - 12,
                    mutatorSectionHeight - 36, ROW_HEIGHT, ids.size());
            for (int row = 0; row < Math.min(mutatorList.visible,
                    Math.max(0, ids.size() - mutatorList.offset)); row++) {
                String id = ids.get(mutatorList.offset + row);
                MutatorModel mutator = model.mutator(id);
                String label = mutator == null ? readableId(id) : mutator.name();
                String secondary = mutator == null ? id : mutator.components().isEmpty()
                        ? readableId(mutator.rarity()) + " mutation"
                        : "Components: " + String.join(", ", mutator.components());
                SelectionRowButton entry = new SelectionRowButton(mutatorList.x,
                        mutatorList.y + row * ROW_HEIGHT, mutatorList.rowWidth(), ROW_HEIGHT - 2,
                        label, secondary, draft.mutators.contains(id), ignored -> toggleMutator(id));
                entry.setTooltip(Tooltip.create(Component.literal(mutatorTooltip(mutator))));
                addRenderableWidget(entry);
            }
        }

        private void configureEntityRows(int left, int width) {
            List<String> ids = new ArrayList<>(pool.entities);
            entityList.configure(left + 6, entitySectionY + 31, width - 12,
                    entitySectionHeight - 36, ROW_HEIGHT, ids.size());
            for (int row = 0; row < Math.min(entityList.visible,
                    Math.max(0, ids.size() - entityList.offset)); row++) {
                String id = ids.get(entityList.offset + row);
                EntityModel entity = model.entities().stream().filter(candidate -> candidate.id().equals(id))
                        .findFirst().orElse(null);
                String label = entity == null ? readableId(id) : entity.name();
                String secondary = entity == null ? id : entitySecondary(entity);
                SelectionRowButton entry = new SelectionRowButton(entityList.x,
                        entityList.y + row * ROW_HEIGHT, entityList.rowWidth(), ROW_HEIGHT - 2,
                        label, secondary, draft.entities.contains(id), ignored -> toggleEntity(id));
                addRenderableWidget(entry);
            }
        }

        private void toggleMutator(String id) {
            if (!draft.mutators.add(id)) draft.mutators.remove(id);
            error = "";
            rebuildWidgets();
        }

        private void toggleEntity(String id) {
            if (!draft.entities.add(id)) draft.entities.remove(id);
            error = "";
            rebuildWidgets();
        }

        private void confirm() {
            try {
                draft.weight = Integer.parseInt(weightInput.getValue());
            } catch (NumberFormatException exception) {
                error = "Weight must be a positive whole number.";
                return;
            }
            if (draft.weight < 1) { error = "Weight must be at least 1."; return; }
            if (draft.mutators.size() < 2) { error = "Choose at least two mutators."; return; }
            if (draft.mutators.size() > pool.maximum) {
                error = "This combination exceeds Maximum Mutations (" + pool.maximum + ").";
                return;
            }
            String conflict = combinationConflict(model, draft.mutators);
            if (!conflict.isBlank()) { error = conflict; return; }
            if (editIndex < 0) pool.combinations.add(draft.copy());
            else pool.combinations.set(editIndex, draft.copy());
            pool.dirty = true;
            minecraft.setScreen(parent);
        }

        @Override public boolean mouseScrolled(double mx, double my, double dx, double dy) {
            if (mutatorSectionHeight > 0 && mutatorList.contains(mx, my) && mutatorList.scroll(dy)) {
                rebuildWidgets(); return true;
            }
            if (entitySectionHeight > 0 && entityList.contains(mx, my) && entityList.scroll(dy)) {
                rebuildWidgets(); return true;
            }
            return super.mouseScrolled(mx, my, dx, dy);
        }

        @Override public boolean mouseClicked(double mx, double my, int button) {
            if (button == 0 && mutatorSectionHeight > 0 && mutatorList.beginDrag(mx, my)) {
                rebuildWidgets(); return true;
            }
            if (button == 0 && entitySectionHeight > 0 && entityList.beginDrag(mx, my)) {
                rebuildWidgets(); return true;
            }
            return super.mouseClicked(mx, my, button);
        }

        @Override public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
            if (button == 0 && mutatorList.dragging) { if (mutatorList.drag(my)) rebuildWidgets(); return true; }
            if (button == 0 && entityList.dragging) { if (entityList.drag(my)) rebuildWidgets(); return true; }
            return super.mouseDragged(mx, my, button, dx, dy);
        }

        @Override public boolean mouseReleased(double mx, double my, int button) {
            mutatorList.release(); entityList.release(); return super.mouseReleased(mx, my, button);
        }

        @Override public void onClose() { minecraft.setScreen(parent); }

        @Override public void render(GuiGraphics graphics, int mx, int my, float tick) {
            renderBackground(graphics, mx, my, tick);
            header(graphics, title.getString(), "Mob Mutations > " + pool.name + " > Rare Combinations");
            panel(graphics, panelLeft(), generalY, panelWidth(), generalHeight);
            graphics.drawString(font, "GENERAL", panelLeft() + 6, generalY + 6, 0xD8B84A, false);
            graphics.drawString(font, "Relative weight", panelLeft() + panelWidth() - 202,
                    generalY + 24, 0xFFFFFF, false);
            drawMarqueeLeft(graphics, font,
                    Component.literal("Higher weights make this combination more likely than other eligible combinations."),
                    panelLeft() + 6, generalY + 43, Math.max(40, panelWidth() - 12), 0xAAAAAA);
            if (mutatorSectionHeight > 0) renderChoiceSection(graphics, mutatorSectionY, mutatorSectionHeight,
                    "MUTATORS", "Choose at least two mutations to combine.", mutatorList);
            if (entitySectionHeight > 0) renderChoiceSection(graphics, entitySectionY, entitySectionHeight,
                    "ELIGIBLE MOBS", "Leave empty to inherit the parent pool's eligible mobs.", entityList);
            String status = error.isBlank()
                    ? draft.mutators.size() + " mutators selected  ·  "
                            + (draft.entities.isEmpty() ? "Mobs inherit parent pool" : draft.entities.size()
                                    + (draft.entities.size() == 1 ? " eligible mob" : " eligible mobs"))
                    : error;
            drawMarqueeLeft(graphics, font, Component.literal(status), panelLeft(), height - 39,
                    panelWidth(), error.isBlank() ? 0xBBBBBB : 0xFF7777);
            renderWidgets(graphics, mx, my, tick);
        }

        private void renderChoiceSection(GuiGraphics graphics, int y, int sectionHeight,
                String heading, String help, ScrollableRows list) {
            panel(graphics, panelLeft(), y, panelWidth(), sectionHeight);
            graphics.drawString(font, heading, panelLeft() + 6, y + 6, 0xD8B84A, false);
            drawMarqueeLeft(graphics, font, Component.literal(help), panelLeft() + 6, y + 18,
                    panelWidth() - 12, 0xAAAAAA);
            list.render(graphics);
        }

        private static String nextCombinationId(PoolDraft pool) {
            int suffix = pool.combinations.size() + 1;
            while (true) {
                String candidate = pool.id + "_combination_" + suffix++;
                boolean used = pool.combinations.stream()
                        .anyMatch(combination -> combination.id.equals(candidate));
                if (!used) return candidate;
            }
        }
    }

    private static final class MutatorWeightScreen extends EditorScreen {
        private final MutationPoolsScreen parent;
        private final PoolDraft draft;
        private final String mutatorId;
        private EditBox input;
        private String error = "";

        MutatorWeightScreen(ClientModel model, MutationPoolsScreen parent, PoolDraft draft, String mutatorId) {
            super("Edit Mutator Weight", model);
            this.parent = parent;
            this.draft = draft;
            this.mutatorId = mutatorId;
        }

        @Override
        protected void init() {
            clearWidgets();
            int centerX = width / 2;
            int top = Math.max(54, height / 2 - 48);
            input = new EditBox(font, centerX - 80, top + 34, 160, 20, Component.literal("Mutator weight"));
            input.setMaxLength(10);
            input.setFilter(value -> value.isEmpty() || value.chars().allMatch(Character::isDigit));
            input.setValue(Integer.toString(draft.weights.getOrDefault(mutatorId, 1)));
            input.setResponder(ignored -> error = "");
            addRenderableWidget(input);
            input.setFocused(true);
            button(centerX - 104, top + 62, 100, "Confirm", ignored -> confirm(),
                    "Apply this weight to the local draft. The server validates it again when the pool is applied.");
            button(centerX + 4, top + 62, 100, "Cancel", ignored -> minecraft.setScreen(parent),
                    "Return without changing the weight.");
        }

        private void confirm() {
            try {
                int weight = Integer.parseInt(input.getValue());
                if (weight < 1) {
                    error = "Weight must be at least 1.";
                    return;
                }
                Integer previous = draft.weights.put(mutatorId, weight);
                if (previous == null || previous != weight) draft.dirty = true;
                minecraft.setScreen(parent);
            } catch (NumberFormatException exception) {
                error = "Enter a whole number from 1 to 2,147,483,647.";
            }
        }

        @Override
        public void onClose() {
            minecraft.setScreen(parent);
        }

        @Override
        public void render(GuiGraphics graphics, int mx, int my, float tick) {
            renderBackground(graphics, mx, my, tick);
            int centerX = width / 2;
            int top = Math.max(54, height / 2 - 48);
            MutatorModel mutator = model.mutator(mutatorId);
            String name = mutator == null ? readableId(mutatorId) : mutator.name();
            graphics.drawCenteredString(font, "Edit " + name + " weight", centerX, top, 0xFFFFFF);
            graphics.drawCenteredString(font, "Relative selection weight (positive whole number)",
                    centerX, top + 14, 0xAAAAAA);
            if (!error.isBlank()) graphics.drawCenteredString(font, error, centerX, top + 88, 0xFF7777);
            renderWidgets(graphics, mx, my, tick);
        }
    }

    private static final class LeaveDraftScreen extends EditorScreen {
        private final Screen returnTo; private final Runnable discard;
        LeaveDraftScreen(Screen returnTo, Runnable discard) { super("Unsaved Changes", ((EditorScreen)returnTo).model); this.returnTo = returnTo; this.discard = discard; }
        @Override protected void init() {
            int y = height / 2 + 12;
            button(width / 2 - 154, y, 98, "Continue Editing", ignored -> minecraft.setScreen(returnTo), null);
            button(width / 2 - 50, y, 100, "Discard", ignored -> {
                if (returnTo instanceof MutationPoolsScreen pools && pools.draft != null) {
                    pools.draft.dirty = false;
                }
                discard.run();
                // Some discard actions update the underlying editor in place (for example,
                // switching pools). If they did not explicitly navigate elsewhere, close this
                // confirmation screen and reveal that updated editor.
                if (minecraft.screen == this) {
                    minecraft.setScreen(returnTo);
                }
            }, null);
            button(width / 2 + 56, y, 98, "Apply", ignored -> {
                if (returnTo instanceof MutationPoolsScreen pools) pools.apply(); else minecraft.setScreen(returnTo);
            }, "Validate and save this draft before leaving.");
        }
        @Override public void render(GuiGraphics graphics, int mx, int my, float tick) {
            renderBackground(graphics, mx, my, tick); graphics.drawCenteredString(font, "Unsaved mutation-pool changes", width / 2, height / 2 - 18, 0xFFFFFF);
            graphics.drawCenteredString(font, "Apply, discard, or continue editing.", width / 2, height / 2 - 4, 0xBBBBBB); renderWidgets(graphics, mx, my, tick);
        }
    }

    private static final class GlobalSettingsScreen extends EditorScreen {
        private boolean defaults, mutations, multiple, events, ascension; private double difficulty, mutationFrequency, eventFrequency; private int maximum;
        GlobalSettingsScreen(ClientModel model) { super("Global Settings", model); load(model.settings()); }
        private void load(WorldAwakenedEditorSettings s) { defaults=s.defaultGameplayEnabled(); difficulty=s.difficultyIntensity(); mutations=s.mutationsEnabled(); mutationFrequency=s.mutationFrequencyMultiplier(); multiple=s.allowMultipleMutations(); maximum=s.maximumMutationsPerMob(); events=s.pressureEventsEnabled(); eventFrequency=s.eventFrequencyMultiplier(); ascension=s.ascensionEnabled(); }
        @Override protected void init() {
            clearWidgets(); int w = Math.min(520, width - 20); int left=(width-w)/2; int col=(w-8)/2; int right=left+col+8;
            button(left, 42, col, toggle("Default gameplay", defaults), b->{defaults=!defaults; rebuildWidgets();}, "Enables bundled Standard definitions; external datapacks remain active.");
            ValueSlider intensity = addRenderableWidget(new ValueSlider(left,66,col,"Difficulty intensity",difficulty,model.difficultyMin(),model.difficultyMax(),.05,v->difficulty=v,true)); intensity.active=model.editable()&&model.difficultyAvailable();
            button(left,90,col,"Progression: "+readableMode(model.progressionMode()),b->{},model.progressionNote()).active=false;
            button(right,42,col,toggle("Mutation system",mutations),b->{mutations=!mutations; rebuildWidgets();},"Pauses new mutations and WA mutation handlers while preserving state.");
            addRenderableWidget(new ValueSlider(right,66,col,"Mutation frequency",mutationFrequency,model.mutationFrequencyMin(),model.mutationFrequencyMax(),.05,v->mutationFrequency=v,true));
            button(right,90,col,toggle("Multiple mutations",multiple),b->{multiple=!multiple; rebuildWidgets();},"Caps all pools at one mutation when disabled.");
            addRenderableWidget(new ValueSlider(right,114,col,"Maximum per mob",maximum,model.maximumMutationsMin(),model.maximumMutationsMax(),1,v->maximum=(int)Math.round(v),false));
            Button eventToggle=button(left,146,col,toggle("Pressure events",events),b->{events=!events; rebuildWidgets();},"Stops the active event when disabled."); eventToggle.active=model.editable()&&model.eventsAvailable();
            ValueSlider frequency=addRenderableWidget(new ValueSlider(left,170,col,"Event frequency",eventFrequency,model.eventFrequencyMin(),model.eventFrequencyMax(),.05,v->eventFrequency=v,true)); frequency.active=model.editable()&&model.eventsAvailable();
            button(right,146,col,toggle("Ascension offers",ascension),b->{ascension=!ascension; rebuildWidgets();},"Existing choices and rewards are preserved.");
            Button apply=button(width/2-154,height-25,98,"Apply",b->apply(),null); apply.active=model.editable();
            button(width/2-50,height-25,100,"Reset Defaults",b->{load(WorldAwakenedEditorSettings.builtInDefaults()); rebuildWidgets();},"Changes the local draft; press Apply to save.");
            button(width/2+56,height-25,98,"Cancel",b->minecraft.setScreen(new DashboardScreen(model)),null);
        }
        private void apply() { PacketDistributor.sendToServer(new WorldAwakenedApplyConfigPayload(model.settingsRevision(), new WorldAwakenedEditorSettings(defaults,difficulty,mutations,mutationFrequency,multiple,maximum,events,eventFrequency,ascension).toJson().toString())); }
        @Override public void onClose() { minecraft.setScreen(new DashboardScreen(model)); }
        @Override public void render(GuiGraphics graphics,int mx,int my,float tick) { renderBackground(graphics,mx,my,tick); header(graphics,"Global Settings","Profile Editor > Global Settings"); drawMarqueeLeft(graphics,font,Component.literal("Runtime controls"),panelLeft(),30,panelWidth(),0xAAAAAA); renderWidgets(graphics,mx,my,tick); }
    }

    private static final class ValueSlider extends AbstractSliderButton {
        private final String label; private final double min,max,step; private final DoubleConsumer consumer; private final boolean multiplier;
        ValueSlider(int x,int y,int w,String label,double initial,double min,double max,double step,DoubleConsumer consumer,boolean multiplier) {
            super(x,y,w,20,Component.empty(),normalize(initial,min,max)); this.label=label;this.min=min;this.max=max;this.step=step;this.consumer=consumer;this.multiplier=multiplier;updateMessage();
        }
        @Override protected void updateMessage() { double current=current(); String value=step>=1?Integer.toString((int)Math.round(current)):multiplier?String.format(Locale.ROOT,"%.2fx",current):String.format(Locale.ROOT,"%.0f%%",current*100); setMessage(Component.literal(label+": "+value)); }
        @Override protected void applyValue() { consumer.accept(step>=1?Math.round(current()):current()); }
        @Override public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            Component message = getMessage();
            setMessage(Component.empty());
            super.renderWidget(graphics, mouseX, mouseY, partialTick);
            setMessage(message);
            drawMarquee(graphics, Minecraft.getInstance().font, message, getX() + 4,
                    getY() + (getHeight() - 8) / 2, getWidth() - 8, getFGColor());
        }
        private double current(){double raw=min+value*(max-min);double snapped=min+Math.round((raw-min)/step)*step;return Math.max(min,Math.min(max,snapped));}
        private void setCurrent(double current) { value = normalize(current, min, max); updateMessage(); }
        private static double normalize(double v,double min,double max){return !Double.isFinite(v)||max<=min?0:Math.max(0,Math.min(1,(v-min)/(max-min)));}
    }

    private static final class MarqueeButton extends Button {
        MarqueeButton(int x, int y, int width, int height, Component message, OnPress onPress) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        }

        @Override
        public void renderString(GuiGraphics graphics, Font font, int color) {
            drawMarquee(graphics, font, getMessage(), getX() + 4, getY() + (getHeight() - 8) / 2,
                    getWidth() - 8, color);
        }
    }

    private static void drawMarquee(
            GuiGraphics graphics,
            Font font,
            Component text,
            int x,
            int y,
            int availableWidth,
            int color) {
        drawMarquee(graphics, font, text, x, y, availableWidth, color, true);
    }

    private static void drawMarqueeLeft(
            GuiGraphics graphics,
            Font font,
            Component text,
            int x,
            int y,
            int availableWidth,
            int color) {
        drawMarquee(graphics, font, text, x, y, availableWidth, color, false);
    }

    private static void drawMarquee(
            GuiGraphics graphics,
            Font font,
            Component text,
            int x,
            int y,
            int availableWidth,
            int color,
            boolean centerWhenFits) {
        int textWidth = font.width(text);
        if (textWidth <= availableWidth) {
            int drawX = centerWhenFits ? x + Math.max(0, (availableWidth - textWidth) / 2) : x;
            graphics.drawString(font, text, drawX, y, color, true);
            return;
        }
        int overflow = textWidth - availableWidth;
        long startPause = 1_200L;
        long scrollDuration = Math.max(600L, Math.round(overflow / 28.0D * 1_000.0D));
        long endPause = 1_000L;
        long cycle = startPause + scrollDuration + endPause;
        long elapsed = Math.floorMod(Util.getMillis(), cycle);
        double offset;
        if (elapsed < startPause) {
            offset = 0.0D;
        } else if (elapsed < startPause + scrollDuration) {
            double progress = (elapsed - startPause) / (double) scrollDuration;
            // Smoothstep avoids a harsh start/stop while retaining a predictable reading speed.
            progress = progress * progress * (3.0D - 2.0D * progress);
            offset = overflow * progress;
        } else {
            offset = overflow;
        }
        graphics.enableScissor(x, y - 1, x + availableWidth, y + font.lineHeight + 1);
        graphics.drawString(font, text, (int) Math.round(x - offset), y, color, true);
        graphics.disableScissor();
    }

    private static String context(JsonObject pool) {
        List<String> parts = new ArrayList<>();
        if (pool.has("eligible_dimensions")) pool.getAsJsonArray("eligible_dimensions").forEach(v -> parts.add(readableId(v.getAsString())));
        if (pool.has("conditions")) for (JsonElement condition : pool.getAsJsonArray("conditions")) {
            JsonObject node=condition.getAsJsonObject(); if (node.has("parameters") && node.getAsJsonObject("parameters").has("stage")) parts.add(readableId(node.getAsJsonObject("parameters").get("stage").getAsString()));
        }
        double chance=pool.has("mutation_chance")?pool.get("mutation_chance").getAsDouble():1;
        parts.add(String.format(Locale.ROOT,"%.0f%% chance",chance*100)); return String.join(" | ",parts.stream().distinct().limit(3).toList());
    }
    private static List<String> stageContexts(JsonObject pool) {
        List<String> stages = new ArrayList<>();
        if (pool.has("conditions")) for (JsonElement condition : pool.getAsJsonArray("conditions")) {
            JsonObject node = condition.getAsJsonObject();
            if (node.has("parameters") && node.getAsJsonObject("parameters").has("stage")) {
                stages.add(node.getAsJsonObject("parameters").get("stage").getAsString());
            }
        }
        if (pool.has("stage_filters")) stages.add(pool.get("stage_filters").toString());
        return stages.stream().distinct().toList();
    }
    private static List<String> dimensions(JsonObject pool) {
        List<String> dimensions = new ArrayList<>();
        if (pool.has("eligible_dimensions")) pool.getAsJsonArray("eligible_dimensions").forEach(value -> dimensions.add(value.getAsString()));
        return dimensions;
    }
    private static String next(List<String> values, String current) { int index = values.indexOf(current); return values.get((Math.max(0, index) + 1) % values.size()); }
    static String filterLabel(String value) { return readableId(value); }
    private static String mutatorTooltip(MutatorModel mutator) { return mutator==null?"Definition unavailable":"Rarity: "+mutator.rarity()+"\nStacking: "+mutator.stackingGroup()+"\nComponents: "+String.join(", ",mutator.components())+"\nWeight is relative, not final spawn probability."; }
    static String entitySecondary(String id, String namespace) {
        return id + " · " + ("minecraft".equals(namespace) ? "Minecraft" : readableId(namespace));
    }
    private static String entitySecondary(EntityModel entity) {
        return entitySecondary(entity.id(), entity.namespace());
    }
    static String combinationModeTooltip(boolean curated) {
        String current = curated ? "Current mode: Curated.\n\n" : "Current mode: Any Compatible.\n\n";
        return current
                + "Any Compatible\nMobs can receive any compatible combination of mutations from this pool. "
                + "Mutations are chosen using their individual weights.\n\n"
                + "Curated\nOnly combinations you've created can occur. Each combination has its own eligible mobs "
                + "and selection weight.\n\nAdditional mutation chance controls extra mutation attempts in either mode.";
    }
    static String compactDistributionSummary(double initialChance, double additionalChance, int maximum) {
        List<Double> distribution = WorldAwakenedMutationProbability.theoreticalDistribution(
                initialChance, maximum > 1 ? additionalChance : 0.0D, maximum);
        List<String> entries = new ArrayList<>();
        entries.add("None: " + formatProbability(distribution.get(0)));
        for (int count = 1; count < distribution.size(); count++) {
            entries.add(count + ": " + formatProbability(distribution.get(count)));
        }
        return String.join("    ", entries);
    }
    private static String formatProbability(double probability) {
        double percentage = probability * 100.0D;
        return percentage > 0.0D && percentage < 0.01D
                ? "<0.01%" : String.format(Locale.ROOT, "%.2f%%", percentage);
    }
    static String distributionSummary(double initialChance, double additionalChance, int maximum) {
        List<Double> distribution = WorldAwakenedMutationProbability.theoreticalDistribution(
                initialChance, maximum > 1 ? additionalChance : 0.0D, maximum);
        StringBuilder summary = new StringBuilder("Theoretical maximum: none ")
                .append(String.format(Locale.ROOT, "%.2f%%", distribution.get(0) * 100.0D));
        for (int count = 1; count < distribution.size(); count++) {
            summary.append("  |  ").append(count).append(count == 1 ? " mutation " : " mutations ")
                    .append(String.format(Locale.ROOT, "%.2f%%", distribution.get(count) * 100.0D));
        }
        return summary.append(" (before runtime scaling, eligibility, and compatibility)").toString();
    }
    private static String combinationConflict(ClientModel model, Set<String> selected) {
        Map<String, Integer> groupCounts = new LinkedHashMap<>();
        Map<String, Integer> groupLimits = new LinkedHashMap<>();
        for (String id : selected) {
            MutatorModel mutator = model.mutator(id);
            if (mutator == null) return "Mutator definition unavailable: " + id;
            for (String other : selected) {
                MutatorModel otherMutator = model.mutator(other);
                if (!id.equals(other) && (mutator.exclusiveWith().contains(other)
                        || otherMutator != null && otherMutator.exclusiveWith().contains(id))) {
                    return mutator.name() + " is exclusive with "
                            + (otherMutator == null ? readableId(other) : otherMutator.name()) + ".";
                }
            }
            if (!"none".equalsIgnoreCase(mutator.stackingGroup())) {
                String group = mutator.stackingGroup().toLowerCase(Locale.ROOT);
                groupCounts.merge(group, 1, Integer::sum);
                groupLimits.merge(group, Math.max(1, mutator.maxStackCount()), Math::min);
            }
        }
        for (Map.Entry<String, Integer> entry : groupCounts.entrySet()) {
            if (entry.getValue() > groupLimits.getOrDefault(entry.getKey(), 1)) {
                return "Stacking group " + entry.getKey() + " exceeds its compatibility limit.";
            }
        }
        return "";
    }
    private static String toggle(String label,boolean value){return label+": "+(value?"On":"Off");}
    private static String readableMode(String value){return switch(value){case "per_player"->"Per Player";case "hybrid"->"Hybrid";default->"Global";};}
    private static String readableId(String id){int colon=id.indexOf(':');String path=colon>=0?id.substring(colon+1):id;int slash=path.lastIndexOf('/');if(slash>=0)path=path.substring(slash+1);StringBuilder out=new StringBuilder();for(String word:path.split("_")){if(!out.isEmpty())out.append(' ');if(!word.isEmpty())out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));}return out.toString();}
}
