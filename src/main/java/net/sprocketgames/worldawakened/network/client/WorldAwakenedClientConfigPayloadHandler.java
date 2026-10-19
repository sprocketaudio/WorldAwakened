package net.sprocketgames.worldawakened.network.client;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.function.DoubleConsumer;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.locale.Language;
import net.minecraft.core.registries.BuiltInRegistries;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.sprocketgames.worldawakened.ascension.WorldAwakenedAscensionRewardEffects;
import net.neoforged.neoforge.network.PacketDistributor;
import net.sprocketgames.worldawakened.config.WorldAwakenedEditorSettings;
import net.sprocketgames.worldawakened.config.WorldAwakenedEditorListLayout;
import net.sprocketgames.worldawakened.config.WorldAwakenedMutatorWeightList;
import net.sprocketgames.worldawakened.mutator.WorldAwakenedMutationProbability;
import net.sprocketgames.worldawakened.network.WorldAwakenedApplyConfigPayload;
import net.sprocketgames.worldawakened.network.WorldAwakenedApplyMutationPoolPayload;
import net.sprocketgames.worldawakened.network.WorldAwakenedApplyAuthoringPayload;
import net.sprocketgames.worldawakened.network.WorldAwakenedProfileOperationPayload;

public final class WorldAwakenedClientConfigPayloadHandler {
    private static final int PHASE_NAME_MAX_LENGTH = 128;
    private static final int PHASE_DESCRIPTION_MAX_LENGTH = 512;
    private static final long SUCCESS_NOTICE_MILLIS = 6_000L;
    private static final long ERROR_NOTICE_MILLIS = 10_000L;
    private static String reopenPoolId;
    private static boolean reopenEventPool;
    private static boolean reopenProgression;
    private static boolean reopenTracks;
    private static boolean reopenAscension;
    private static boolean reopenLoot;
    private static String reopenPoolAfterAuthoring;
    private static String reopenTierTrackId;
    private static String reopenTierStageId;
    private static StatusNotice statusNotice;

    private WorldAwakenedClientConfigPayloadHandler() {}

    public static void handleSnapshot(String payloadJson) {
        ClientModel model = parse(payloadJson);
        Minecraft minecraft = Minecraft.getInstance();
        if (model == null || minecraft == null) return;
        if (model.message().isBlank()) statusNotice = null;
        else statusNotice = new StatusNotice(model.status(), model.message(), Util.getMillis()
                + ("applied".equals(model.status()) ? SUCCESS_NOTICE_MILLIS : ERROR_NOTICE_MILLIS));
        if (reopenTierTrackId != null) {
            JsonObject track = track(model, reopenTierTrackId); String stageId = reopenTierStageId;
            reopenTierTrackId = reopenTierStageId = null;
            minecraft.setScreen(track == null ? new MutationPoolsScreen(model, null) : new AddTierScreen(model, track, stageId));
        } else if (reopenPoolAfterAuthoring != null) {
            PoolModel pool = model.pool(reopenPoolAfterAuthoring); reopenPoolAfterAuthoring = null;
            minecraft.setScreen(new MutationPoolsScreen(model, pool));
        } else if (reopenTracks) {
            reopenTracks = false;
            minecraft.setScreen(new MutationPoolsScreen(model, null));
        } else if (reopenAscension) {
            reopenAscension = false;
            minecraft.setScreen(new AscensionOffersScreen(model));
        } else if (reopenLoot) {
            reopenLoot = false;
            minecraft.setScreen(new LootProfilesScreen(model));
        } else if (reopenProgression) {
            reopenProgression = false;
            minecraft.setScreen(new ProgressionScreen(model));
        } else if (reopenPoolId != null) {
            PoolModel pool = model.pool(reopenPoolId);
            reopenPoolId = null;
            boolean event = reopenEventPool; reopenEventPool = false;
            minecraft.setScreen(new MutationPoolsScreen(model, pool, event));
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
                        Set.copyOf(exclusive), List.copyOf(components),
                        node.has("definition") ? node.getAsJsonObject("definition").deepCopy() : new JsonObject(),
                        node.has("world_authored") && node.get("world_authored").getAsBoolean()));
            }
            List<EntityModel> entities = new ArrayList<>();
            for (JsonElement value : root.getAsJsonArray("living_entities")) {
                JsonObject node = value.getAsJsonObject();
                entities.add(new EntityModel(node.get("id").getAsString(), node.get("name").getAsString(),
                        node.get("namespace").getAsString()));
            }
            List<String> dimensions = new ArrayList<>();
            root.getAsJsonArray("dimensions").forEach(value -> dimensions.add(value.getAsString()));
            List<StageModel> stages = new ArrayList<>();
            if (root.has("stages")) for (JsonElement value : root.getAsJsonArray("stages")) {
                JsonObject node = value.getAsJsonObject();
                JsonObject definition = node.getAsJsonObject("definition").deepCopy();
                List<String> uses = new ArrayList<>(); if (node.has("used_by")) node.getAsJsonArray("used_by").forEach(use -> uses.add(use.getAsString()));
                stages.add(new StageModel(definition.get("id").getAsString(),
                        jsonText(definition.get("display_name"), readableId(definition.get("id").getAsString())),
                        jsonText(definition.get("description"), ""), node.get("world_authored").getAsBoolean(),
                        node.has("source_backed") && node.get("source_backed").getAsBoolean(),
                        node.get("unlocked").getAsBoolean(), List.copyOf(uses), definition));
            }
            JsonArray authoredStages = copyArray(root, "world_authored_stages");
            JsonArray authoredTriggers = copyArray(root, "world_authored_triggers");
            JsonArray authoredPools = copyArray(root, "world_authored_pools");
            JsonArray authoredMutators = copyArray(root, "world_authored_mutators");
            JsonArray tracks = copyArray(root, "phase_mutation_configurations");
            JsonArray triggerRules = copyArray(root, "trigger_rules");
            JsonArray authoredOffers = copyArray(root, "world_authored_ascension_offers");
            JsonArray authoredRewards = copyArray(root, "world_authored_ascension_rewards");
            JsonArray lootProfiles = copyArray(root, "loot_profiles");
            JsonArray authoredLootProfiles = copyArray(root, "world_authored_loot_profiles");
            List<ItemModel> items = new ArrayList<>();
            if (root.has("items")) root.getAsJsonArray("items").forEach(value -> {
                JsonObject node = value.getAsJsonObject();
                items.add(new ItemModel(node.get("id").getAsString(), node.get("name").getAsString(),
                        node.get("namespace").getAsString()));
            });
            List<String> chestLootTables = new ArrayList<>();
            if (root.has("chest_loot_tables")) root.getAsJsonArray("chest_loot_tables")
                    .forEach(value -> chestLootTables.add(value.getAsString()));
            List<OfferModel> offers = new ArrayList<>();
            if (root.has("ascension_offers")) root.getAsJsonArray("ascension_offers").forEach(value -> {
                JsonObject definition = value.getAsJsonObject().deepCopy();
                String id = definition.get("id").getAsString();
                offers.add(new OfferModel(id, jsonText(definition.get("display_name"), readableId(id)),
                        jsonText(definition.get("description"), ""), definition));
            });
            List<RewardModel> rewards = new ArrayList<>();
            if (root.has("ascension_rewards")) root.getAsJsonArray("ascension_rewards").forEach(value -> {
                JsonObject definition = value.getAsJsonObject().deepCopy();
                String id = definition.get("id").getAsString();
                rewards.add(new RewardModel(id, jsonText(definition.get("display_name"), readableId(id)), definition));
            });
            List<AdvancementModel> advancements = new ArrayList<>();
            if (root.has("advancements")) root.getAsJsonArray("advancements").forEach(value -> {
                if (value.isJsonPrimitive()) {
                    String id = value.getAsString();
                    advancements.add(new AdvancementModel(id, readableId(id), namespace(id), false));
                } else {
                    JsonObject node = value.getAsJsonObject();
                    String id = node.get("id").getAsString();
                    advancements.add(new AdvancementModel(id, read(node, "name", readableId(id)),
                            read(node, "namespace", namespace(id)), node.has("technical") && node.get("technical").getAsBoolean()));
                }
            });
            return new ClientModel(root.get("revision").getAsLong(), root.get("profile_revision").getAsLong(),
                    read(root, "status", "ready"), read(root, "message", ""), root.get("editable").getAsBoolean(),
                    read(root, "active_profile", "Standard"), read(root, "profile_kind", "standard"), read(root, "profile_summary", ""),
                    read(root, "progression_mode", "global"), read(root, "progression_summary", ""),
                    read(root, "progression_note", ""), root.get("difficulty_intensity_available").getAsBoolean(),
                    root.get("pressure_events_available").getAsBoolean(), WorldAwakenedEditorSettings.fromJson(settings),
                    bounds.get("difficulty_min").getAsDouble(), bounds.get("difficulty_max").getAsDouble(),
                    bounds.get("mutation_frequency_min").getAsDouble(), bounds.get("mutation_frequency_max").getAsDouble(),
                    bounds.get("maximum_mutations_min").getAsInt(), bounds.get("maximum_mutations_max").getAsInt(),
                    bounds.get("event_frequency_min").getAsDouble(), bounds.get("event_frequency_max").getAsDouble(),
                    List.copyOf(pools), List.copyOf(mutators), List.copyOf(entities), List.copyOf(dimensions),
                    List.copyOf(stages), authoredStages, authoredTriggers, authoredPools, authoredMutators, tracks, triggerRules,
                    List.copyOf(offers), authoredOffers, List.copyOf(rewards), authoredRewards,
                    List.copyOf(advancements), lootProfiles, authoredLootProfiles,
                    List.copyOf(items), List.copyOf(chestLootTables));
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static String read(JsonObject root, String key, String fallback) {
        return root.has(key) ? root.get(key).getAsString() : fallback;
    }

    static String conciseStatusMessage(String status, String message) {
        String normalized = message == null ? "" : message.trim().replaceAll("\\s+", " ");
        if (normalized.isBlank()) return "";
        if ("applied".equals(status)) {
            if (normalized.toLowerCase(Locale.ROOT).contains("restored to built-in defaults")) return "Defaults restored.";
            if (normalized.toLowerCase(Locale.ROOT).contains("profile")) return "Profile updated.";
            return "Changes applied.";
        }
        if ("stale_revision".equals(status)) return "Settings changed elsewhere — review and retry.";
        if (normalized.length() <= 72) return normalized;
        if ("validation_failed".equals(status)) return "Could not apply changes — hover for details.";
        return "Operation failed — hover for details.";
    }

    private static JsonArray copyArray(JsonObject root, String key) {
        return root.has(key) ? root.getAsJsonArray(key).deepCopy() : new JsonArray();
    }

    private static String jsonText(JsonElement element, String fallback) {
        if (element == null || element.isJsonNull()) return fallback;
        if (element.isJsonPrimitive()) return element.getAsString();
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has("text")) return object.get("text").getAsString();
            if (object.has("translate")) {
                String key = object.get("translate").getAsString();
                return Language.getInstance().has(key) ? Component.translatable(key).getString() : fallback;
            }
        }
        return fallback;
    }

    private record ClientModel(long settingsRevision, long profileRevision, String status, String message,
            boolean editable, String activeProfile, String profileKind, String profileSummary, String progressionMode,
            String progressionSummary, String progressionNote, boolean difficultyAvailable, boolean eventsAvailable,
            WorldAwakenedEditorSettings settings, double difficultyMin, double difficultyMax,
            double mutationFrequencyMin, double mutationFrequencyMax, int maximumMutationsMin,
            int maximumMutationsMax, double eventFrequencyMin, double eventFrequencyMax,
            List<PoolModel> pools, List<MutatorModel> mutators, List<EntityModel> entities, List<String> dimensions,
            List<StageModel> stages, JsonArray authoredStages, JsonArray authoredTriggers, JsonArray authoredPools, JsonArray authoredMutators,
            JsonArray tracks, JsonArray triggerRules, List<OfferModel> offers, JsonArray authoredOffers,
            List<RewardModel> rewards, JsonArray authoredRewards,
            List<AdvancementModel> advancements, JsonArray lootProfiles, JsonArray authoredLootProfiles,
            List<ItemModel> items, List<String> chestLootTables) {
        PoolModel pool(String id) { return pools.stream().filter(pool -> pool.id().equals(id)).findFirst().orElse(null); }
        MutatorModel mutator(String id) { return mutators.stream().filter(mutator -> mutator.id().equals(id)).findFirst().orElse(null); }
    }
    private record PoolModel(String id, String name, String origin, boolean overridden, JsonObject definition) {}
    private record MutatorModel(String id, String name, String rarity, String stackingGroup,
            int maxStackCount, Set<String> exclusiveWith, List<String> components, JsonObject definition,
            boolean worldAuthored) {}
    private record EntityModel(String id, String name, String namespace) {}
    private record ItemModel(String id, String name, String namespace) {}
    private record AdvancementModel(String id, String name, String namespace, boolean technical) {}
    private record OfferModel(String id, String name, String description, JsonObject definition) {}
    private record RewardModel(String id, String name, JsonObject definition) {}
    private record StatusNotice(String status, String message, long expiresAt) {}
    private record StageModel(String id, String name, String description, boolean worldAuthored, boolean sourceBacked,
            boolean unlocked, List<String> usedBy, JsonObject definition) {}

    static final class UnlockConditionDraft {
        int type;
        String target;
        UnlockConditionDraft(int type, String target) { this.type = type; this.target = target; }
    }

    private static final class StageDraft {
        final StageModel source;
        final Map<String, Integer> orderChanges = new LinkedHashMap<>();
        final Set<String> mutationDimensions = new LinkedHashSet<>();
        final List<UnlockConditionDraft> unlockConditions = new ArrayList<>();
        String name, description, group, target;
        boolean enabled, dirty, requireAll = true, unlockEditable = true;
        int unlockType, policy, sort;

        StageDraft(ClientModel model, StageModel source) {
            this.source = source;
            this.name = source.name();
            this.description = source.description();
            JsonObject definition = source.definition();
            this.enabled = !definition.has("enabled") || definition.get("enabled").getAsBoolean();
            boolean starting = definition.has("default_unlocked") && definition.get("default_unlocked").getAsBoolean();
            this.unlockType = inferUnlockType(model, source.id(), starting);
            this.group = read(definition, "progression_group", "custom_progression");
            this.sort = existingStageSortIndex(definition);
            String policyName = read(definition, "unlock_policy", "cumulative");
            this.policy = "exclusive_group".equals(policyName) ? 1 : "replace_group".equals(policyName) ? 2 : 0;
            this.target = switch (unlockType) {
                case 1 -> existingTarget(model, source.id(), "world_day_gte", "value", "3");
                case 2 -> existingTarget(model, source.id(), "current_dimension", "dimension", "");
                case 3 -> existingTarget(model, source.id(), "advancement_completed", "advancement", "");
                case 4 -> existingTarget(model, source.id(), "entity_type", "entity", "");
                default -> null;
            };
            readEditorUnlockConditions(model, source.id(), starting, this);
            phaseIndex(model).configurations(source.id()).stream()
                    .filter(WorldAwakenedPhaseMutationIndex.Configuration::active)
                    .map(WorldAwakenedPhaseMutationIndex.Configuration::dimensionId)
                    .filter(value -> !value.isBlank()).forEach(mutationDimensions::add);
        }
    }

    private abstract static class EditorScreen extends Screen {
        protected final ClientModel model;
        private int noticeX, noticeY, noticeWidth;
        private StatusNotice renderedNotice;
        private StatusNotice localNotice;
        protected EditorScreen(String title, ClientModel model) { super(Component.literal(title)); this.model = model; }
        protected int panelWidth() { return Math.min(760, width - 20); }
        protected int panelLeft() { return (width - panelWidth()) / 2; }
        protected void header(GuiGraphics graphics, String heading, String breadcrumb) {
            int half = Math.max(40, panelWidth() / 2 - 8);
            drawMarqueeLeft(graphics, font, Component.literal("WorldAwakened").withStyle(ChatFormatting.BOLD), panelLeft(), 10, half, 0xFFFFFF);
            drawMarqueeLeft(graphics, font, Component.literal(heading), width / 2 + 8, 10, half, 0xFFFFFF);
            renderedNotice = activeStatusNotice();
            if (renderedNotice == null) {
                noticeX = noticeY = noticeWidth = 0;
                drawMarqueeLeft(graphics, font, Component.literal(breadcrumb), panelLeft(), 23, panelWidth(), 0x9A9A9A);
                return;
            }
            String summary = conciseStatusMessage(renderedNotice.status(), renderedNotice.message());
            int maximumNoticeWidth = Math.max(90, panelWidth() * 46 / 100);
            noticeWidth = Math.min(maximumNoticeWidth, Math.max(40, font.width(summary)));
            noticeX = panelLeft() + panelWidth() - noticeWidth;
            noticeY = 23;
            int breadcrumbWidth = Math.max(40, noticeX - panelLeft() - 12);
            drawMarqueeLeft(graphics, font, Component.literal(breadcrumb), panelLeft(), noticeY,
                    breadcrumbWidth, 0x9A9A9A);
            drawMarqueeLeft(graphics, font, Component.literal(summary), noticeX, noticeY, noticeWidth,
                    "applied".equals(renderedNotice.status()) ? 0x66FF66 : 0xFF7777);
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
        protected Button compactButton(int x, int y, int size, String label, Button.OnPress action, String tooltip) {
            Button button = addRenderableWidget(Button.builder(Component.literal(label), action)
                    .bounds(x, y, size, size).build());
            if (tooltip != null && !tooltip.isBlank()) button.setTooltip(Tooltip.create(Component.literal(tooltip)));
            return button;
        }
        protected void renderWidgets(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            for (Renderable renderable : renderables) {
                renderable.render(graphics, mouseX, mouseY, partialTick);
            }
            if (renderedNotice != null && mouseX >= noticeX && mouseX < noticeX + noticeWidth
                    && mouseY >= noticeY - 2 && mouseY < noticeY + font.lineHeight + 2) {
                graphics.renderTooltip(font, font.split(Component.literal(renderedNotice.message()),
                        Math.max(200, Math.min(420, width / 2))), mouseX, mouseY);
            }
        }
        protected void showLocalError(String message) {
            localNotice = new StatusNotice("validation_failed", message, Util.getMillis() + ERROR_NOTICE_MILLIS);
        }
        protected void clearLocalNotice() { localNotice = null; }
        private StatusNotice activeStatusNotice() {
            if (localNotice != null) {
                if (Util.getMillis() < localNotice.expiresAt()) return localNotice;
                localNotice = null;
            }
            if (statusNotice == null) return null;
            if (Util.getMillis() >= statusNotice.expiresAt()) {
                statusNotice = null;
                return null;
            }
            return statusNotice;
        }
        @Override public boolean isPauseScreen() { return false; }
    }

    private static final class DashboardScreen extends EditorScreen {
        DashboardScreen(ClientModel model) { super("WorldAwakened", model); }
        @Override protected void init() {
            clearWidgets();
            int left = panelLeft(); int w = panelWidth(); int gap = 8; int column = (w - gap) / 2;
            button(left, 50, w, "Active Profile: " + model.activeProfile() + "  —  Edit Profile >", ignored ->
                    minecraft.setScreen(new ProfileManagerScreen(model)),
                    "Choose Standard or create a custom authoring workspace.");
            tile(left, 92, column, "World Phases", "Timeline, milestones and mutation dimensions", true,
                    ignored -> minecraft.setScreen(new ProgressionScreen(model)));
            tile(left + column + gap, 92, column, "Mob Mutations", "Phase, dimension and named mob configurations", true,
                    ignored -> minecraft.setScreen(new MutationPoolsScreen(model, null)));
            tile(left, 132, column, "Loot", "Mob drops, chest rewards and bonus experience", true,
                    ignored -> minecraft.setScreen(new LootProfilesScreen(model)));
            tile(left + column + gap, 132, column, "Ascension", "Offers, availability and permanent choices", true,
                    ignored -> minecraft.setScreen(new AscensionOffersScreen(model)));
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
            renderWidgets(graphics, mx, my, tick);
        }
    }

    private static final class ProfileManagerScreen extends EditorScreen {
        ProfileManagerScreen(ClientModel model) { super("Active Profile", model); }
        @Override protected void init() {
            clearWidgets(); int left = panelLeft(), w = panelWidth(), x = left + 12;
            Button standard = button(x, 72, w - 24, "Use Bundled Standard", ignored -> confirm("standard",
                    "Return to bundled Standard?", "World-authored definitions are removed; saved stage history is retained."),
                    "Use the bundled gameplay graph plus external datapacks.");
            Button empty = button(x, 111, w - 24, "Create New Profile — Start Empty", ignored -> confirm("start_empty",
                    "Start an empty profile?", "Bundled gameplay and current world-authored definitions are removed. External datapacks remain."),
                    "Start with no Standard stages, pools, rules, or rewards.");
            Button copy = button(x, 150, w - 24, "Create New Profile — Copy Standard", ignored -> confirm("copy_standard",
                    "Copy Standard?", "Creates an independent world-owned copy and disables the bundled source."),
                    "Copy all bundled authored definitions into independent world storage.");
            standard.active = model.editable() && !"Standard".equals(model.activeProfile());
            empty.active = model.editable(); copy.active = model.editable();
            button(left, height - 25, 72, "Back", ignored -> minecraft.setScreen(new DashboardScreen(model)), null);
        }
        private void confirm(String operation, String prompt, String detail) {
            String actionLabel = switch (operation) {
                case "standard" -> "Use Standard";
                case "start_empty" -> "Start Empty";
                case "copy_standard" -> "Copy Standard";
                default -> "Confirm";
            };
            minecraft.setScreen(new ConfirmActionScreen(model, prompt, detail, actionLabel, () -> {
                PacketDistributor.sendToServer(new WorldAwakenedProfileOperationPayload(model.settingsRevision(),
                        model.profileRevision(), operation));
            }, () -> minecraft.setScreen(this)));
        }
        @Override public void render(GuiGraphics graphics, int mx, int my, float tick) {
            renderBackground(graphics, mx, my, tick); header(graphics, "Active Profile", "Profile Editor > Profile");
            panel(graphics, panelLeft(), 47, panelWidth(), height - 77);
            graphics.drawString(font, "CURRENT: " + model.activeProfile(), panelLeft() + 12, 55, 0xFFFFAA00);
            graphics.drawWordWrap(font, Component.literal("Profile changes are server-authoritative and keep persisted stage history. External datapacks remain additive and predictable."),
                    panelLeft() + 12, 188, panelWidth() - 24, 0xFFBEBEBE);
            renderWidgets(graphics, mx, my, tick);
        }
    }

    private static final class ProgressionScreen extends EditorScreen implements UnlockTargetOwner {
        private static final int CONTENT_TOP = 52;
        private static final int PHASE_ROW_HEIGHT = 36;
        private static final int SECTION_GAP = 6;
        private EditBox search;
        private EditBox name, description, day;
        private final List<EditBox> conditionValueFields = new ArrayList<>();
        private int offset;
        private String query = "";
        private int visibleRows, totalRows, browserWidth;
        private int phaseViewportX, phaseViewportY, phaseViewportWidth, phaseViewportHeight;
        private int detailSectionX, detailSectionW, generalSectionY;
        private int unlockSectionY, unlockSectionHeight, unlockExplanationY, mutationSectionY;
        private final List<Integer> unlockSeparatorYs = new ArrayList<>();
        private String unlockExplanationText = "";
        private int selectedConditionIndex;
        private boolean phasesExpanded = true;
        private String selectedId;
        private StageDraft draft;
        ProgressionScreen(ClientModel model) { super("World Phases", model); }
        @Override protected void init() {
            clearWidgets();
            conditionValueFields.clear();
            unlockSeparatorYs.clear();
            int left = panelLeft(), w = panelWidth(), browser = Math.min(280, Math.max(210, w * 38 / 100)); browserWidth = browser;
            int contentBottom = height - 31;
            search = new EditBox(font, left + 5, CONTENT_TOP + 5, browser - 92, 18, Component.literal("Search phases"));
            search.setHint(Component.literal("Search phases...").withStyle(ChatFormatting.DARK_GRAY));
            search.setValue(query);
            search.setResponder(value -> { query = value; offset = 0; rebuildWidgets(); });
            addRenderableWidget(search);
            setInitialFocus(search); setFocused(search); search.setFocused(true);
            button(left + browser - 82, CONTENT_TOP + 4, 77, "+ Phase", ignored ->
                    minecraft.setScreen(new StageEditorScreen(model, null)), "Create a shared progression phase.");
            List<StageModel> shown = orderedStages(model, draft).stream().filter(stage -> stage.name().toLowerCase(Locale.ROOT)
                    .contains(query.toLowerCase(Locale.ROOT)) || stage.id().contains(query.toLowerCase(Locale.ROOT))).toList();
            if (selectedId == null && !shown.isEmpty()) selectStage(shown.get(0));
            button(left + 5, CONTENT_TOP + 28, browser - 10,
                    (phasesExpanded ? "▼ " : "▶ ") + "PHASES (" + model.stages().size() + ")", ignored -> {
                        phasesExpanded = !phasesExpanded; offset = 0; rebuildWidgets();
                    }, "Show or hide the progression-phase list.");
            List<StageModel> listed = phasesExpanded || !query.isBlank() ? shown : List.of();
            phaseViewportX = left + 5;
            phaseViewportY = CONTENT_TOP + 52;
            phaseViewportWidth = browser - 10;
            phaseViewportHeight = Math.max(PHASE_ROW_HEIGHT, contentBottom - phaseViewportY - 5);
            visibleRows = WorldAwakenedEditorListLayout.visibleRows(phaseViewportHeight, PHASE_ROW_HEIGHT);
            totalRows = listed.size();
            offset = listOffset(offset, totalRows, visibleRows);
            boolean overflow = WorldAwakenedEditorListLayout.needsScrollbar(totalRows, visibleRows);
            int rowWidth = phaseViewportWidth - (overflow ? 8 : 0);
            for (int row = 0; row < Math.min(visibleRows, listed.size() - offset); row++) {
                StageModel stage = listed.get(offset + row);
                int rowY = phaseViewportY + row * PHASE_ROW_HEIGHT;
                boolean selectedRow = stage.id().equals(selectedId);
                boolean localDirty = selectedRow && draft != null && draft.dirty;
                boolean enabled = selectedRow && draft != null ? draft.enabled
                        : !stage.definition().has("enabled") || stage.definition().get("enabled").getAsBoolean();
                int arrowReserve = selectedRow ? 43 : 0;
                addRenderableWidget(new PhaseRowButton(phaseViewportX, rowY, rowWidth, PHASE_ROW_HEIGHT - 2,
                        selectedRow && draft != null ? draft.name : stage.name(),
                        selectedRow && draft != null ? draftUnlockSummary() : unlockSummary(model, stage.id()),
                        enabled, selectedRow, localDirty, arrowReserve, ignored -> {
                            if (draft != null && draft.dirty && !stage.id().equals(selectedId)) {
                                minecraft.setScreen(new ConfirmActionScreen(model, "Discard unsaved phase changes?",
                                        "The selected phase has local changes which have not been applied.",
                                        "Discard",
                                        () -> { selectStage(stage); minecraft.setScreen(this); }, () -> minecraft.setScreen(this)));
                            } else { selectStage(stage); rebuildWidgets(); }
                        }));
                if (selectedRow) {
                    List<StageModel> sequence = sequenceStages(model, draft.group, draft);
                    List<WorldAwakenedPhaseOrdering.Entry> order = phaseOrderEntries(model, sequence, draft);
                    Button up = compactButton(phaseViewportX + rowWidth - 39, rowY + 17, 16, "↑",
                            ignored -> moveStage(-1), "Move this phase one position earlier.");
                    Button down = compactButton(phaseViewportX + rowWidth - 20, rowY + 17, 16, "↓",
                            ignored -> moveStage(1), "Move this phase one position later.");
                    up.active = WorldAwakenedPhaseOrdering.canMove(order, stage.id(), -1);
                    down.active = WorldAwakenedPhaseOrdering.canMove(order, stage.id(), 1);
                }
            }
            StageModel selected = stage(model, selectedId);
            if (selected != null) {
                if (draft == null) draft = new StageDraft(model, selected);
                addPhaseDetails(left + browser + 10, CONTENT_TOP, w - browser - 10, selected);
            }
            button(left, height - 25, 72, "Back", ignored -> minecraft.setScreen(new DashboardScreen(model)), null);
        }
        private String draftUnlockSummary() {
            if (draft.unlockConditions.size() > 1) {
                return draft.unlockConditions.size() + " conditions · " + (draft.requireAll ? "All" : "Any");
            }
            UnlockConditionDraft condition = draft.unlockConditions.isEmpty() ? null : draft.unlockConditions.getFirst();
            if (condition == null) return "No automatic unlock";
            return switch (condition.type) {
                case 0 -> "Starting phase";
                case 1 -> "World day " + (condition.target == null || condition.target.isBlank() ? "?" : condition.target);
                case 2 -> "Enter " + targetLabel(model, 2, condition.target);
                case 3 -> "Complete " + targetLabel(model, 3, condition.target);
                case 4 -> "Defeat " + targetLabel(model, 4, condition.target);
                default -> "Manual unlock";
            };
        }
        private void selectStage(StageModel stage) { selectedId = stage.id(); draft = new StageDraft(model, stage); clearLocalNotice(); }
        private void addPhaseDetails(int x, int y, int w, StageModel selected) {
            detailSectionX = x + 5;
            detailSectionW = w - 10;
            int innerX = detailSectionX + 6, innerW = detailSectionW - 12, half = (innerW - 5) / 2;
            generalSectionY = y + 43;
            mutationSectionY = generalSectionY + 74 + SECTION_GAP;
            unlockSectionY = mutationSectionY + 62 + SECTION_GAP;
            unlockSectionHeight = 50;

            name = new EditBox(font, innerX, generalSectionY + 20, half, 18, Component.literal("Phase name"));
            name.setHint(Component.literal("Phase name").withStyle(ChatFormatting.DARK_GRAY));
            configurePhaseNameField(name);
            name.setValue(draft.name);
            name.setResponder(value -> { draft.name = value; draft.dirty = true; });
            addRenderableWidget(name);
            description = new EditBox(font, innerX + half + 5, generalSectionY + 20,
                    innerW - half - 5, 18, Component.literal("Description"));
            description.setHint(Component.literal("Player-facing description").withStyle(ChatFormatting.DARK_GRAY));
            configurePhaseDescriptionField(description, draft.description);
            description.setValue(draft.description);
            description.setResponder(value -> {
                draft.description = value; draft.dirty = true; updatePhaseDescriptionTooltip(description, value);
            });
            addRenderableWidget(description);
            int controlGap = 5;
            int controlWidth = (innerW - controlGap * 2) / 3;
            int startingX = innerX + controlWidth + controlGap;
            int policyX = startingX + controlWidth + controlGap;
            button(innerX, generalSectionY + 44, controlWidth, "Enabled: " + (draft.enabled ? "On" : "Off"), ignored -> {
                draft.enabled = !draft.enabled; draft.dirty = true; rebuildWidgets();
            }, "Disabled phases remain defined but cannot be newly unlocked.");
            button(startingX, generalSectionY + 44, controlWidth,
                    "Starting phase: " + (isStartingDraft(draft) ? "Yes" : "No"), ignored -> {
                        boolean wasStarting = isStartingDraft(draft);
                        draft.unlockConditions.clear();
                        draft.unlockConditions.add(new UnlockConditionDraft(wasStarting ? 5 : 0, null));
                        syncPrimaryCondition(); draft.dirty = true; rebuildWidgets();
                    }, "Starting phases begin unlocked. This does not change their list position. Multiple starting phases are valid in cumulative progression.");
            button(policyX, generalSectionY + 44, innerX + innerW - policyX,
                    "Policy: " + phasePolicyLabel(draft.policy), ignored -> {
                        draft.policy = nextPhasePolicy(draft.policy);
                        draft.dirty = true;
                        rebuildWidgets();
                    }, policyExplanation(draft.policy));

            button(innerX, mutationSectionY + 20, innerW,
                    "Mob mutation dimensions (" + draft.mutationDimensions.size() + ")", ignored ->
                            minecraft.setScreen(new PhaseDimensionSelectorScreen(model, this, draft.name,
                                    draft.mutationDimensions, selectedDimensions -> {
                                        if (!draft.mutationDimensions.equals(selectedDimensions)) {
                                            draft.mutationDimensions.clear();
                                            draft.mutationDimensions.addAll(selectedDimensions);
                                            draft.dirty = true;
                                        }
                                    })),
                    "Choose where this world phase has mob-mutation configurations. Empty means none.");
            if (!draft.unlockEditable) {
                Button preserved = button(innerX, unlockSectionY + 22, innerW,
                        "Advanced datapack conditions preserved", ignored -> {},
                        "This phase uses trigger logic the simple editor cannot represent safely. Its trigger definitions will not be rewritten.");
                preserved.active = false;
                unlockExplanationText = "Advanced trigger logic is preserved and remains editable through its datapack source.";
                unlockExplanationY = unlockSectionY + 45;
                unlockSectionHeight = wrappedSectionHeight(unlockExplanationText, innerW, unlockExplanationY);
            } else addUnlockConditionControls(innerX, innerW);
            addPhaseFooter(x, w, selected);
        }

        private void addUnlockConditionControls(int x, int width) {
            int rowY = unlockSectionY + 20;
            if (draft.unlockConditions.size() > 1) {
                button(x, rowY, Math.min(190, width), draft.requireAll ? "All conditions (AND)" : "Any condition (OR)", ignored -> {
                    draft.requireAll = !draft.requireAll; draft.dirty = true; rebuildWidgets();
                }, draft.requireAll ? "Every condition is remembered and must be satisfied, in any order."
                        : "The phase unlocks as soon as any one condition is satisfied.");
                rowY += 24;
            }
            for (int index = 0; index < draft.unlockConditions.size(); index++) {
                UnlockConditionDraft condition = draft.unlockConditions.get(index);
                final int conditionIndex = index;
                int typeWidth = Math.max(94, width * 34 / 100), removeWidth = 24;
                int targetWidth = width - typeWidth - removeWidth - 8;
                button(x, rowY, typeWidth, (index + 1) + ". " + StageEditorScreen.TYPES[condition.type], ignored -> {
                    int next = condition.type >= 1 && condition.type < 5 ? condition.type + 1 : 1;
                    condition.type = next; condition.target = next == 1 ? "3" : firstUnlockTarget(next);
                    syncPrimaryCondition(); draft.dirty = true; rebuildWidgets();
                }, "Choose how this phase can be unlocked.");
                if (condition.type == 1) {
                    EditBox value = new EditBox(font, x + typeWidth + 4, rowY, targetWidth, 18,
                            Component.literal("World day"));
                    value.setValue(condition.target == null ? "3" : condition.target);
                    value.setFilter(text -> text.matches("[0-9]{0,7}"));
                    value.setResponder(text -> { condition.target = text; syncPrimaryCondition(); draft.dirty = true; });
                    value.setTooltip(Tooltip.create(Component.literal("World day reached\nEnter a non-negative world day.")));
                    conditionValueFields.add(value); addRenderableWidget(value);
                } else if (condition.type >= 2 && condition.type <= 4) {
                    List<String> targets = targetsForUnlock(model, condition.type);
                    if ((condition.target == null || !targets.contains(condition.target)) && !targets.isEmpty())
                        condition.target = targets.getFirst();
                    button(x + typeWidth + 4, rowY, targetWidth,
                            targets.isEmpty() ? "No compatible entries" : targetLabel(model, condition.type, condition.target), ignored -> {
                                selectedConditionIndex = conditionIndex;
                                if (!targets.isEmpty()) minecraft.setScreen(new StageUnlockTargetScreen(model, this, this));
                            }, condition.target == null ? null : condition.target + "\nOpen searchable selector");
                } else {
                    Button special = button(x + typeWidth + 4, rowY, targetWidth,
                            condition.type == 0 ? "Available from the beginning" : "Operator command only", ignored -> {},
                            "Starting and Manual are exclusive single-condition modes.");
                    special.active = false;
                }
                Button remove = button(x + width - removeWidth, rowY, removeWidth, "×", ignored -> {
                    if (draft.unlockConditions.size() > 1) {
                        draft.unlockConditions.remove(conditionIndex); syncPrimaryCondition();
                        draft.dirty = true; rebuildWidgets();
                    }
                }, "Remove this condition.");
                remove.active = draft.unlockConditions.size() > 1;
                rowY += 20;
                if (index < draft.unlockConditions.size() - 1) {
                    unlockSeparatorYs.add(rowY + 3);
                    rowY += 15;
                }
            }
            int addY = rowY + 4;
            Button add = button(x, addY, Math.min(176, width),
                    "+ Add Condition (" + draft.unlockConditions.size() + "/3)", ignored -> {
                        int type = firstUnusedConditionType();
                        draft.unlockConditions.add(new UnlockConditionDraft(type, type == 1 ? "3" : firstUnlockTarget(type)));
                        syncPrimaryCondition(); draft.dirty = true; rebuildWidgets();
            }, "Add another remembered condition. Up to three may be combined.");
            add.active = draft.unlockConditions.size() < 3 && !isSpecialConditionMode();
            unlockExplanationText = currentUnlockExplanation();
            unlockExplanationY = addY + 25;
            unlockSectionHeight = wrappedSectionHeight(unlockExplanationText, width, unlockExplanationY);
        }

        private int wrappedSectionHeight(String text, int width, int textY) {
            int lines = Math.max(1, font.split(Component.literal(text), Math.max(20, width)).size());
            return Math.max(50, textY - unlockSectionY + lines * font.lineHeight + 8);
        }

        private String currentUnlockExplanation() {
            if (draft.unlockConditions.size() > 1) {
                return draft.requireAll
                        ? "Unlocks once all conditions have been satisfied, in any order."
                        : "Unlocks when any one condition is satisfied.";
            }
            return unlockExplanation(draft.unlockConditions.getFirst().type);
        }

        private void syncPrimaryCondition() {
            if (draft.unlockConditions.isEmpty()) draft.unlockConditions.add(new UnlockConditionDraft(5, null));
            draft.unlockType = draft.unlockConditions.getFirst().type;
            draft.target = draft.unlockConditions.getFirst().target;
        }

        private boolean isSpecialConditionMode() {
            return draft.unlockConditions.size() == 1
                    && (draft.unlockConditions.getFirst().type == 0 || draft.unlockConditions.getFirst().type == 5);
        }

        private int firstUnusedConditionType() {
            for (int type = 1; type <= 4; type++) {
                int candidate = type;
                if (draft.unlockConditions.stream().noneMatch(condition -> condition.type == candidate)) return type;
            }
            return 1;
        }

        private String firstUnlockTarget(int type) {
            List<String> targets = targetsForUnlock(model, type);
            return targets.isEmpty() ? "" : targets.getFirst();
        }

        private void addPhaseFooter(int x, int w, StageModel selected) {
            int footer = height - 25;
            boolean narrow = w < 430;
            int resetWidth = narrow ? Math.max(70, w * 26 / 100) : 126;
            int applyWidth = narrow ? Math.max(48, w * 18 / 100) : 72;
            int restoreWidth = narrow ? Math.max(70, w * 26 / 100) : 112;
            int discardWidth = narrow ? Math.max(1, w - resetWidth - applyWidth - restoreWidth - 12) : 104;
            int applyX = narrow ? x + resetWidth + 4 : x + Math.max(0, w - 296);
            int restoreX = narrow ? applyX + applyWidth + 4 : x + Math.max(76, w - 220);
            int discardX = narrow ? restoreX + restoreWidth + 4 : x + Math.max(192, w - 104);
            button(x, footer, resetWidth, "Reset All Defaults", ignored -> confirmResetAll(),
                    "Restore bundled Standard and every global editor setting to its built-in default.").active = model.editable();
            Button apply = button(applyX, footer, applyWidth, "Apply", ignored -> applyStage(selected),
                    "Validate and atomically save this phase and its direct unlock trigger.");
            apply.active = model.editable() && draft.dirty;
            boolean canRestore = selected.worldAuthored() && selected.sourceBacked();
            boolean custom = selected.worldAuthored() && !selected.sourceBacked();
            String restoreLabel = canRestore ? "Restore Source" : custom ? "Delete Phase" : "Using Source";
            Button restore = button(restoreX, footer, restoreWidth, restoreLabel, ignored -> {
                if (canRestore) minecraft.setScreen(new ConfirmActionScreen(model, "Restore source phase?",
                        "This removes the selected world override and reveals its bundled or datapack source.",
                        "Restore",
                        () -> revertStage(selected), () -> minecraft.setScreen(this)));
                else if (custom) deleteStage(selected);
            }, canRestore ? "Remove this world override and restore its source definition."
                    : custom ? "Delete this world-authored phase after dependency validation."
                    : "This phase already matches its bundled or datapack source.");
            restore.active = model.editable() && (canRestore || custom);
            Button discard = button(discardX, footer, discardWidth, "Discard Changes",
                    ignored -> discardStageDraft(selected), "Discard unsaved changes to this phase.");
            discard.active = draft.dirty;
        }

        private void discardStageDraft(StageModel selected) {
            draft = new StageDraft(model, selected);
            clearLocalNotice();
            rebuildWidgets();
        }

        private void confirmResetAll() {
            minecraft.setScreen(new ConfirmActionScreen(model,
                    "Reset all WorldAwakened settings?",
                    "This clears every world-authored profile setting and override, restores bundled Standard and all global settings to their built-in defaults, and cannot be undone. External datapack files and earned progression are not changed.",
                    "Reset All",
                    () -> {
                        reopenProgression = true;
                        PacketDistributor.sendToServer(new WorldAwakenedProfileOperationPayload(model.settingsRevision(),
                                model.profileRevision(), "reset_defaults"));
                    }, () -> minecraft.setScreen(this)));
        }
        private void moveStage(int direction) {
            List<StageModel> sequence = sequenceStages(model, draft.group, draft);
            WorldAwakenedPhaseOrdering.MoveResult moved = WorldAwakenedPhaseOrdering.move(
                    phaseOrderEntries(model, sequence, draft), selectedId, direction);
            if (!moved.moved()) return;
            for (Map.Entry<String, Integer> change : moved.sortIndexes().entrySet()) {
                if (change.getKey().equals(draft.source.id())) {
                    draft.sort = change.getValue();
                    continue;
                }
                StageModel changed = stage(model, change.getKey());
                if (changed == null) continue;
                if (change.getValue() == existingStageSortIndex(changed.definition())) {
                    draft.orderChanges.remove(change.getKey());
                } else {
                    draft.orderChanges.put(change.getKey(), change.getValue());
                }
            }
            draft.dirty = true;
            rebuildWidgets();
        }
        private void applyStage(StageModel selected) {
            if (draft.name.isBlank()) { showLocalError("Phase name is required."); return; }
            for (UnlockConditionDraft condition : draft.unlockConditions) {
                if (condition.type >= 1 && condition.type <= 4
                        && (condition.target == null || condition.target.isBlank())) {
                    showLocalError(condition.type == 1 ? "World day is required." : "Choose a condition target.");
                    return;
                }
            }
            Set<String> legacyDimensions = phaseIndex(model).configurations(selected.id()).stream()
                    .filter(WorldAwakenedPhaseMutationIndex.Configuration::active)
                    .filter(WorldAwakenedPhaseMutationIndex.Configuration::legacyReview)
                    .map(WorldAwakenedPhaseMutationIndex.Configuration::dimensionId)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            if (!draft.mutationDimensions.containsAll(legacyDimensions)) {
                showLocalError("A legacy mutation mapping must be reviewed before its dimension can be removed.");
                return;
            }
            JsonArray stages = model.authoredStages().deepCopy();
            for (Map.Entry<String, Integer> change : draft.orderChanges.entrySet()) {
                StageModel changed = stage(model, change.getKey()); if (changed == null) continue;
                JsonObject definition = changed.definition().deepCopy(); definition.addProperty("sort_index", change.getValue());
                stages = replaceById(stages, definition);
            }
            JsonObject definition = selected.definition().deepCopy();
            if (!draft.name.equals(selected.name())) definition.addProperty("display_name", draft.name);
            if (!draft.description.equals(selected.description())) definition.addProperty("description", draft.description);
            boolean starting = !draft.unlockConditions.isEmpty() && draft.unlockConditions.getFirst().type == 0;
            definition.addProperty("enabled", draft.enabled); definition.addProperty("default_unlocked", starting);
            definition.addProperty("sort_index", draft.sort); definition.addProperty("progression_group", draft.group);
            definition.addProperty("unlock_policy", draft.policy == 0 ? "cumulative" : draft.policy == 1 ? "exclusive_group" : "replace_group");
            stages = replaceById(stages, definition);
            JsonArray triggers = model.authoredTriggers().deepCopy();
            if (draft.unlockEditable) {
                triggers = removeDirectTriggersForStage(triggers, selected.id());
                triggers = appendDisabledDirectTriggerOverrides(triggers, model.triggerRules(), selected.id());
                JsonArray replacements = buildEditorUnlockTriggers(model, selected.id(), draft.unlockConditions,
                        draft.requireAll, draft.sort, draft.group, draft.policy == 0);
                List<StageModel> orderedSequence = sequenceStages(model, draft.group, draft);
                for (JsonElement replacement : replacements) {
                    JsonObject trigger = replacement.getAsJsonObject();
                    if (draft.policy == 0) trigger = withCumulativeUnlockActions(trigger, orderedSequence, selected.id());
                    triggers = replaceById(triggers, trigger);
                }
                for (String reorderedId : draft.orderChanges.keySet()) {
                    StageModel reordered = stage(model, reorderedId);
                    if (reordered == null || !"cumulative".equals(read(reordered.definition(), "unlock_policy", "cumulative"))) continue;
                    for (JsonElement raw : model.triggerRules()) {
                        if (!raw.isJsonObject()) continue;
                        JsonObject trigger = raw.getAsJsonObject();
                        if ((trigger.has("enabled") && !trigger.get("enabled").getAsBoolean())
                                || !isDirectTriggerForStage(trigger, reorderedId)
                                || !hasOnlySimpleUnlockConditions(trigger)) continue;
                        triggers = replaceById(triggers, withCumulativeUnlockActions(trigger, orderedSequence, reorderedId));
                    }
                }
            }
            PhaseAuthoring phase = synchronizePhaseDimensions(model, selected.id(), draft.mutationDimensions);
            sendAuthoring(model, stages, triggers, phase.pools(), phase.tracks());
        }
        private void revertStage(StageModel selected) {
            sendAuthoring(model, removeById(model.authoredStages(), selected.id()),
                    removeDirectTriggersForStage(model.authoredTriggers(), selected.id()), model.authoredPools(), model.tracks());
        }
        private void deleteStage(StageModel selected) {
            if (isStageReferenced(model, selected.id())) {
                showLocalError("This phase is still used. Remove its mutation configurations first."); return;
            }
            minecraft.setScreen(new ConfirmActionScreen(model, "Delete " + selected.name() + "?",
                    "Unlocked-stage history is retained, but the world-authored definition and direct trigger are removed.",
                    "Delete",
                    () -> sendAuthoring(model, removeById(model.authoredStages(), selected.id()),
                            removeDirectTriggersForStage(model.authoredTriggers(), selected.id()), model.authoredPools(), model.tracks()),
                    () -> minecraft.setScreen(this)));
        }
        @Override public boolean mouseScrolled(double mx, double my, double dx, double dy) {
            int rows = Math.max(1, visibleRows);
            int next = listOffset(offset + (dy < 0 ? 1 : -1), totalRows, rows);
            if (next != offset) { offset = next; rebuildWidgets(); return true; }
            return super.mouseScrolled(mx, my, dx, dy);
        }
        @Override public void render(GuiGraphics graphics, int mx, int my, float tick) {
            renderBackground(graphics, mx, my, tick);
            header(graphics, "World Phases", "Profile Editor > World Phases");
            int left = panelLeft(), w = panelWidth(), browser = Math.min(280, Math.max(210, w * 38 / 100));
            int contentBottom = height - 31;
            panel(graphics, left, CONTENT_TOP, browser, contentBottom - CONTENT_TOP);
            panel(graphics, left + browser + 10, CONTENT_TOP, w - browser - 10, contentBottom - CONTENT_TOP);
            StageModel selected = stage(model, selectedId);
            if (selected == null) graphics.drawWordWrap(font, Component.literal("Create the first shared progression phase."),
                    left + browser + 25, CONTENT_TOP + 34, w - browser - 35, 0xFFBEBEBE);
            else {
                int innerX = detailSectionX + 6, innerW = detailSectionW - 12;
                String source = selected.worldAuthored()
                        ? selected.sourceBacked() ? "World override" : "World authored"
                        : "Bundled or datapack source";
                drawScaledMarqueeLeft(graphics, font, Component.literal(draft.name).withStyle(ChatFormatting.BOLD),
                        innerX, CONTENT_TOP + 4, Math.max(30, innerW), 0xFFFFFF, 1.15F);
                drawMarqueeLeft(graphics, font, Component.literal(source), innerX, CONTENT_TOP + 18,
                        Math.max(30, innerW), 0xAAAAAA);

                sectionOutline(graphics, detailSectionX, generalSectionY, detailSectionW, 74);
                graphics.drawString(font, "GENERAL", innerX, generalSectionY + 5, 0xFFFFAA00);
                sectionOutline(graphics, detailSectionX, mutationSectionY, detailSectionW, 62);
                graphics.drawString(font, "MOB MUTATION DIMENSIONS", innerX, mutationSectionY + 5, 0xFFFFAA00);
                drawMarqueeLeft(graphics, font, Component.literal(draft.mutationDimensions.isEmpty() ? "None"
                        : draft.mutationDimensions.stream().map(WorldAwakenedClientConfigPayloadHandler::readableId)
                                .reduce((a, b) -> a + ", " + b).orElse("None")),
                        innerX, mutationSectionY + 44, innerW, 0xFFAAAAAA);
                sectionOutline(graphics, detailSectionX, unlockSectionY, detailSectionW, unlockSectionHeight);
                graphics.drawString(font, "UNLOCK CONDITIONS", innerX, unlockSectionY + 5, 0xFFFFAA00);
                for (int separatorY : unlockSeparatorYs) {
                    String operator = draft.requireAll ? "AND" : "OR";
                    graphics.drawString(font, operator, innerX + (innerW - font.width(operator)) / 2,
                            separatorY, 0xFFFFAA00, false);
                }
                graphics.drawWordWrap(font, Component.literal(unlockExplanationText), innerX,
                        unlockExplanationY, innerW, 0xFFAAAAAA);
            }
            if (totalRows > visibleRows) renderScrollbar(graphics,
                    phaseViewportX + phaseViewportWidth - 6, phaseViewportY,
                    phaseViewportHeight, totalRows, visibleRows, offset);
            renderWidgets(graphics, mx, my, tick);
        }
        @Override public List<String> unlockTargets() {
            return targetsForUnlock(model, selectedCondition() == null ? 5 : selectedCondition().type);
        }
        @Override public String unlockTarget() { return selectedCondition() == null ? null : selectedCondition().target; }
        @Override public void chooseUnlockTarget(String value) {
            UnlockConditionDraft condition = selectedCondition();
            if (condition != null) { condition.target = value; syncPrimaryCondition(); draft.dirty = true; }
        }
        @Override public int selectedUnlockType() { return selectedCondition() == null ? 5 : selectedCondition().type; }
        private UnlockConditionDraft selectedCondition() {
            if (draft == null || draft.unlockConditions.isEmpty()) return null;
            selectedConditionIndex = Math.max(0, Math.min(selectedConditionIndex, draft.unlockConditions.size() - 1));
            return draft.unlockConditions.get(selectedConditionIndex);
        }
    }

    private static final class StageEditorScreen extends EditorScreen implements UnlockTargetOwner {
        private final StageModel source;
        private final String returnTrackId;
        private EditBox name, description, day;
        private final List<UnlockConditionDraft> unlockConditions = new ArrayList<>();
        private final Set<String> mutationDimensions = new LinkedHashSet<>();
        private boolean enabled = true, startsUnlocked;
        private boolean requireAll = true;
        private int selectedConditionIndex;
        private int unlockType;
        private int policy;
        private String groupValue;
        private int sortValue;
        private String targetValue;
        private String nameValue, descriptionValue;
        private String error = "";
        private int generalSectionY, unlockSectionY, unlockControlsY, unlockExplanationY;
        private int mutationSectionY, scopeTextY;
        private static final String[] TYPES = {"Starting phase", "World day", "Enter dimension", "Advancement", "Defeat mob", "Manual"};
        StageEditorScreen(ClientModel model, StageModel source) { this(model, source, null); }
        StageEditorScreen(ClientModel model, StageModel source, String returnTrackId) {
            super(source == null ? "New Phase" : "Edit Phase", model);
            this.source = source;
            this.returnTrackId = returnTrackId;
            this.nameValue = source == null ? "" : source.name();
            this.descriptionValue = source == null ? "" : source.description();
            this.groupValue = source != null
                    ? read(source.definition(), "progression_group", "custom_progression") : mainProgressionGroup(model);
            this.sortValue = source == null ? nextSortIndex(model, groupValue) : existingStageSortIndex(source.definition());
            if (source != null) {
                enabled = !source.definition().has("enabled") || source.definition().get("enabled").getAsBoolean();
                startsUnlocked = source.definition().has("default_unlocked") && source.definition().get("default_unlocked").getAsBoolean();
                unlockType = inferUnlockType(model, source.id(), startsUnlocked);
                if (unlockType == 2) targetValue = existingTarget(model, source.id(), "current_dimension", "dimension", "");
                else if (unlockType == 3) targetValue = existingTarget(model, source.id(), "advancement_completed", "advancement", "");
                else if (unlockType == 4) targetValue = existingTarget(model, source.id(), "entity_type", "entity", "");
                String currentPolicy = read(source.definition(), "unlock_policy", "cumulative");
                policy = "exclusive_group".equals(currentPolicy) ? 1 : "replace_group".equals(currentPolicy) ? 2 : 0;
            }
            if (source == null) unlockConditions.add(new UnlockConditionDraft(1, "3"));
            else {
                StageDraft loaded = new StageDraft(model, source);
                unlockConditions.addAll(loaded.unlockConditions);
                mutationDimensions.addAll(loaded.mutationDimensions);
                requireAll = loaded.requireAll;
            }
            syncCreationPrimary();
        }
        @Override protected void init() {
            clearWidgets();
            int left = panelLeft(), w = panelWidth(), x = left + 14, fieldW = w - 28;
            generalSectionY = 57;
            unlockSectionY = generalSectionY + 116 + 6;
            unlockControlsY = unlockSectionY + 22;
            int conditionsHeight = unlockConditions.size() * 24 + (unlockConditions.size() > 1 ? 24 : 0);
            unlockExplanationY = unlockControlsY + conditionsHeight + 27;
            mutationSectionY = unlockExplanationY + 32;
            scopeTextY = mutationSectionY + 66;
            name = edit(x, generalSectionY + 30, fieldW, "Phase name", nameValue);
            description = edit(x, generalSectionY + 64, fieldW, "Description", descriptionValue);
            configurePhaseNameField(name);
            name.setValue(nameValue);
            name.setResponder(value -> nameValue = value);
            configurePhaseDescriptionField(description, descriptionValue);
            description.setValue(descriptionValue);
            description.setResponder(value -> { descriptionValue = value; updatePhaseDescriptionTooltip(description, value); });
            int controlGap = 4;
            int controlWidth = (fieldW - controlGap * 2) / 3;
            int startingX = x + controlWidth + controlGap;
            int policyX = startingX + controlWidth + controlGap;
            int controlY = generalSectionY + 90;
            button(x, controlY, controlWidth, "Enabled: " + (enabled ? "On" : "Off"), ignored -> { enabled = !enabled; rebuildWidgets(); },
                    "Disabled phases remain defined but cannot be newly unlocked.");
            button(startingX, controlY, controlWidth, "Starting phase: " + (startsUnlocked ? "Yes" : "No"), ignored -> {
                startsUnlocked = !startsUnlocked; unlockConditions.clear();
                unlockConditions.add(new UnlockConditionDraft(startsUnlocked ? 0 : 5, null));
                syncCreationPrimary(); rebuildWidgets();
            }, "Starting phases begin unlocked. This does not change their list position. Multiple starting phases are valid in cumulative progression.");
            button(policyX, controlY, x + fieldW - policyX, "Policy: " + phasePolicyLabel(policy), ignored -> {
                policy = nextPhasePolicy(policy);
                rebuildWidgets();
            }, policyExplanation());
            addCreationConditionControls(x, unlockControlsY, fieldW);
            button(x, mutationSectionY + 22, fieldW,
                    "Mob mutation dimensions (" + mutationDimensions.size() + ")", ignored ->
                            minecraft.setScreen(new PhaseDimensionSelectorScreen(model, this,
                                    nameValue.isBlank() ? "New Phase" : nameValue, mutationDimensions,
                                    selected -> { mutationDimensions.clear(); mutationDimensions.addAll(selected); })),
                    "Choose dimensions where this phase has mob-mutation configurations.");
            int footer = height - 25;
            button(left, footer, 82, "Cancel", ignored -> {
                JsonObject returnTrack = returnTrackId == null ? null : track(model, returnTrackId);
                minecraft.setScreen(returnTrackId == null ? new ProgressionScreen(model)
                        : returnTrack == null ? new MutationPoolsScreen(model, null)
                        : new AddTierScreen(model, returnTrack, null));
            }, null);
            Button save = button(left + w - 100, footer, 100, source == null ? "Create Phase" : "Apply", ignored -> save(),
                    "Validate and atomically save the stage and its unlock trigger.");
            save.active = model.editable();
            if (source != null && source.worldAuthored() && !source.sourceBacked()) button(left + w - 190, footer, 84, "Delete", ignored -> delete(),
                    "Delete this world-authored stage only when it has no remaining dependencies.");
            else if (source != null && source.worldAuthored()) button(left + w - 190, footer, 84, "Revert", ignored -> revert(),
                    "Remove world overrides and restore the source stage.");
        }
        private EditBox edit(int x, int y, int w, String hint, String value) {
            EditBox box = new EditBox(font, x, y, w, 20, Component.literal(hint));
            box.setHint(Component.literal(hint).withStyle(ChatFormatting.DARK_GRAY)); box.setValue(value); addRenderableWidget(box); return box;
        }
        private void addCreationConditionControls(int x, int y, int width) {
            if (unlockConditions.size() > 1) {
                button(x, y, Math.min(190, width), requireAll ? "All conditions (AND)" : "Any condition (OR)", ignored -> {
                    requireAll = !requireAll; rebuildWidgets();
                }, requireAll ? "All conditions are remembered and may be completed in any order."
                        : "Any one condition unlocks the phase.");
                y += 24;
            }
            for (int index = 0; index < unlockConditions.size(); index++) {
                UnlockConditionDraft condition = unlockConditions.get(index); final int conditionIndex = index;
                int typeWidth = Math.max(110, width * 32 / 100), removeWidth = 24;
                int valueWidth = width - typeWidth - removeWidth - 8;
                button(x, y, typeWidth, (index + 1) + ". " + TYPES[condition.type], ignored -> {
                    int next = condition.type >= 1 && condition.type < 5 ? condition.type + 1 : 1;
                    condition.type = next; condition.target = next == 1 ? "3" : firstCreationTarget(next);
                    syncCreationPrimary(); rebuildWidgets();
                }, "Choose how this phase can be unlocked.");
                if (condition.type == 1) {
                    EditBox value = edit(x + typeWidth + 4, y, valueWidth, "World day",
                            condition.target == null ? "3" : condition.target);
                    value.setFilter(text -> text.matches("[0-9]{0,7}"));
                    value.setResponder(text -> { condition.target = text; syncCreationPrimary(); });
                } else if (condition.type >= 2 && condition.type <= 4) {
                    List<String> options = targetsForUnlock(model, condition.type);
                    if ((condition.target == null || !options.contains(condition.target)) && !options.isEmpty())
                        condition.target = options.getFirst();
                    button(x + typeWidth + 4, y, valueWidth, options.isEmpty() ? "No compatible entries"
                            : targetLabel(model, condition.type, condition.target), ignored -> {
                        selectedConditionIndex = conditionIndex;
                        if (!options.isEmpty()) minecraft.setScreen(new StageUnlockTargetScreen(model, this, this));
                    }, options.isEmpty() ? "The server reported no compatible entries." : condition.target + "\nOpen searchable selector");
                } else {
                    Button special = button(x + typeWidth + 4, y, valueWidth,
                            condition.type == 0 ? "Available from the beginning" : "Operator command only",
                            ignored -> {}, "Starting and Manual are exclusive modes.");
                    special.active = false;
                }
                Button remove = button(x + width - removeWidth, y, removeWidth, "×", ignored -> {
                    if (unlockConditions.size() > 1) { unlockConditions.remove(conditionIndex); syncCreationPrimary(); rebuildWidgets(); }
                }, "Remove this condition.");
                remove.active = unlockConditions.size() > 1;
                y += 24;
            }
            Button add = button(x, y, Math.min(180, width), "+ Add Condition (" + unlockConditions.size() + "/3)", ignored -> {
                int type = firstCreationUnusedType();
                unlockConditions.add(new UnlockConditionDraft(type, type == 1 ? "3" : firstCreationTarget(type)));
                syncCreationPrimary(); rebuildWidgets();
            }, "Add another condition, up to three.");
            add.active = unlockConditions.size() < 3 && !(unlockConditions.size() == 1
                    && (unlockConditions.getFirst().type == 0 || unlockConditions.getFirst().type == 5));
        }
        private void syncCreationPrimary() {
            if (unlockConditions.isEmpty()) unlockConditions.add(new UnlockConditionDraft(5, null));
            unlockType = unlockConditions.getFirst().type; targetValue = unlockConditions.getFirst().target;
            startsUnlocked = unlockType == 0;
        }
        private int firstCreationUnusedType() {
            for (int type = 1; type <= 4; type++) { int candidate = type;
                if (unlockConditions.stream().noneMatch(condition -> condition.type == candidate)) return type; }
            return 1;
        }
        private String firstCreationTarget(int type) {
            List<String> values = targetsForUnlock(model, type); return values.isEmpty() ? "" : values.getFirst();
        }
        private List<String> targets() {
            if (unlockType == 2) return model.dimensions();
            if (unlockType == 3) return model.advancements().stream()
                    .sorted(Comparator.comparing(AdvancementModel::technical).thenComparing(AdvancementModel::name)
                            .thenComparing(AdvancementModel::id))
                    .map(AdvancementModel::id).toList();
            if (unlockType == 4) return model.entities().stream().map(EntityModel::id).toList();
            return List.of();
        }
        @Override public List<String> unlockTargets() { return targetsForUnlock(model, selectedCreationCondition().type); }
        @Override public String unlockTarget() { return selectedCreationCondition().target; }
        @Override public void chooseUnlockTarget(String value) {
            selectedCreationCondition().target = value; syncCreationPrimary();
        }
        @Override public int selectedUnlockType() { return selectedCreationCondition().type; }
        private UnlockConditionDraft selectedCreationCondition() {
            selectedConditionIndex = Math.max(0, Math.min(selectedConditionIndex, unlockConditions.size() - 1));
            return unlockConditions.get(selectedConditionIndex);
        }
        private String stageId() { return source == null ? "" : source.id(); }
        private void save() {
            if (nameValue.isBlank()) { error = "Phase name is required."; return; }
            for (UnlockConditionDraft condition : unlockConditions) {
                if (condition.type == 1 && (condition.target == null || condition.target.isBlank())) {
                    error = "World day must be zero or greater."; return;
                }
                if (condition.type >= 2 && condition.type <= 4
                        && (condition.target == null || condition.target.isBlank())) {
                    error = "Choose a target for every condition."; return;
                }
            }
            String id = source == null ? uniqueId(model, nameValue, "stage") : source.id();
            JsonObject stage = source == null ? new JsonObject() : source.definition().deepCopy();
            stage.addProperty("schema_version", 1); stage.addProperty("id", id);
            stage.addProperty("display_name", nameValue); stage.addProperty("description", descriptionValue);
            stage.addProperty("sort_index", sortValue); stage.addProperty("visible_to_players", true);
            stage.addProperty("enabled", enabled); stage.addProperty("progression_group", groupValue);
            stage.addProperty("unlock_policy", policy == 0 ? "cumulative" : policy == 1 ? "exclusive_group" : "replace_group");
            stage.addProperty("default_unlocked", startsUnlocked);
            JsonArray stages = replaceById(model.authoredStages(), stage);
            JsonArray triggers = removeDirectTriggersForStage(model.authoredTriggers(), id);
            triggers = appendDisabledDirectTriggerOverrides(triggers, model.triggerRules(), id);
            JsonArray replacements = buildEditorUnlockTriggers(model, id, unlockConditions, requireAll,
                    sortValue, groupValue, policy == 0);
            for (JsonElement replacement : replacements) triggers = replaceById(triggers, replacement.getAsJsonObject());
            PhaseAuthoring phase = synchronizePhaseDimensions(model, id, mutationDimensions);
            sendAuthoring(model, stages, triggers, phase.pools(), phase.tracks());
            if (returnTrackId != null) { reopenProgression = false; reopenTierTrackId = returnTrackId; reopenTierStageId = id; }
        }
        private void delete() {
            if (isStageReferenced(model, source.id())) { error = "This stage is still used by a difficulty tier or mutation pool. Remove those references first."; return; }
            sendAuthoring(model, removeById(model.authoredStages(), source.id()),
                    removeDirectTriggersForStage(model.authoredTriggers(), source.id()), model.authoredPools(), model.tracks());
        }
        private void revert() {
            sendAuthoring(model, removeById(model.authoredStages(), source.id()),
                    removeDirectTriggersForStage(model.authoredTriggers(), source.id()), model.authoredPools(), model.tracks());
        }
        @Override public void render(GuiGraphics graphics, int mx, int my, float tick) {
            renderBackground(graphics, mx, my, tick); header(graphics, source == null ? "Create Phase" : "Phase Details", "Profile Editor > World Phases");
            int left = panelLeft(), w = panelWidth(); panel(graphics, left, 47, w, height - 77);
            int sectionX = left + 10, sectionW = w - 20;
            editorSection(graphics, sectionX, generalSectionY, sectionW, 116);
            editorSection(graphics, sectionX, unlockSectionY, sectionW,
                    mutationSectionY - unlockSectionY - 6);
            editorSection(graphics, sectionX, mutationSectionY, sectionW, 58);
            graphics.drawString(font, "GENERAL", left + 14, generalSectionY + 6, 0xFFFFAA00);
            graphics.drawString(font, "Name", left + 14, generalSectionY + 19, 0xFFBEBEBE);
            graphics.drawString(font, "Description", left + 14, generalSectionY + 53, 0xFFBEBEBE);
            graphics.drawString(font, "UNLOCK CONDITIONS", left + 14, unlockSectionY + 6, 0xFFFFAA00);
            String explanation = unlockConditions.size() > 1
                    ? requireAll ? "Unlocks once all conditions have been satisfied, in any order."
                            : "Unlocks when any one condition is satisfied."
                    : unlockExplanation();
            graphics.drawWordWrap(font, Component.literal(explanation), left + 14, unlockExplanationY, w - 28, 0xFFBEBEBE);
            graphics.drawString(font, "MOB MUTATION DIMENSIONS", left + 14, mutationSectionY + 6, 0xFFFFAA00);
            String scopeText = "per_player".equals(model.progressionMode())
                    ? "Per-player progression: each player advances when they complete this milestone. Unattributed spawn contexts fail closed."
                    : "World progression: when any player completes this milestone, the shared world progression advances.";
            graphics.drawWordWrap(font, Component.literal(scopeText), left + 14, scopeTextY, w - 28, 0xFF8FB8D8);
            if (!error.isBlank()) graphics.drawWordWrap(font, Component.literal(error), left + 14, height - 48, w - 220, 0xFFFF6666);
            renderWidgets(graphics, mx, my, tick);
        }
        private void editorSection(GuiGraphics graphics, int x, int y, int width, int height) {
            graphics.fill(x, y, x + width, y + height, 0x50101010);
            graphics.fill(x, y, x + width, y + 1, 0xFF666666);
            graphics.fill(x, y + height - 1, x + width, y + height, 0xFF333333);
            graphics.fill(x, y, x + 1, y + height, 0xFF666666);
            graphics.fill(x + width - 1, y, x + width, y + height, 0xFF333333);
        }
        private String unlockExplanation() {
            return switch (unlockType) {
                case 0 -> "Starts unlocked. No trigger rule is needed.";
                case 1 -> "World progression advances when the configured day is reached.";
                case 2 -> "Advances when a player enters the selected dimension.";
                case 3 -> "Advances when a player completes the selected advancement; existing completions reconcile on login.";
                case 4 -> "Advances when a player defeats the selected living entity.";
                default -> "Only an operator using the existing stage command can unlock this milestone.";
            };
        }
        private String policyExplanation() {
            return switch (policy) {
                case 1 -> "Exclusive: this stage unlocks only if no other stage in the sequence is active.";
                case 2 -> "Replace: unlocking this stage locks the currently active stage in the sequence.";
                default -> "Cumulative: this milestone and earlier stages remain unlocked; out-of-order milestones catch up.";
            };
        }
    }

    private interface UnlockTargetOwner {
        List<String> unlockTargets();
        String unlockTarget();
        void chooseUnlockTarget(String value);
        int selectedUnlockType();
    }

    private static final class StageUnlockTargetScreen extends EditorScreen {
        private final Screen parent; private final UnlockTargetOwner owner;
        private EditBox search; private String query = ""; private int offset;
        private String selectedId;
        private List<String> filtered = List.of();
        private int totalRows, visibleRows;
        StageUnlockTargetScreen(ClientModel model, Screen parent, UnlockTargetOwner owner) {
            super("Select Unlock Target", model); this.parent = parent; this.owner = owner;
            this.selectedId = owner.unlockTarget();
        }
        @Override protected void init() {
            clearWidgets(); int left = panelLeft(), w = panelWidth();
            search = new EditBox(font, left + 8, 53, w - 16, 18, Component.literal("Search"));
            search.setHint(Component.literal("Search installed content...").withStyle(ChatFormatting.DARK_GRAY)); search.setValue(query);
            search.setResponder(value -> { query = value; offset = 0; rebuildWidgets(); }); addRenderableWidget(search);
            setInitialFocus(search); setFocused(search); search.setFocused(true);
            filtered = owner.unlockTargets().stream().filter(value -> targetSearchText(model,
                    owner.selectedUnlockType(), value).contains(query.toLowerCase(Locale.ROOT))).toList();
            totalRows = filtered.size(); visibleRows = Math.max(1, (height - 137) / 30);
            offset = listOffset(offset, totalRows, visibleRows);
            for (int row = 0; row < Math.min(visibleRows, filtered.size() - offset); row++) {
                String id = filtered.get(offset + row); addRenderableWidget(new SelectionRowButton(left + 8, 77 + row * 30,
                        w - 16, 28, targetLabel(model, owner.selectedUnlockType(), id), targetSecondary(model,
                        owner.selectedUnlockType(), id), id.equals(selectedId), ignored -> {
                            selectedId = id; rebuildWidgets();
                        }, false));
            }
            button(left, height - 25, 82, "Cancel", ignored -> minecraft.setScreen(parent), "Leave the existing target unchanged.");
            Button confirm = button(left + w - 92, height - 25, 92, "Confirm", ignored -> {
                owner.chooseUnlockTarget(selectedId); minecraft.setScreen(parent);
            }, "Use the highlighted target for this stage draft.");
            confirm.active = selectedId != null && owner.unlockTargets().contains(selectedId);
        }
        @Override public boolean mouseScrolled(double mx, double my, double dx, double dy) {
            int next = listOffset(offset + (dy < 0 ? 1 : -1), totalRows, visibleRows);
            if (next != offset) { offset = next; rebuildWidgets(); return true; }
            return super.mouseScrolled(mx, my, dx, dy);
        }
        @Override public void render(GuiGraphics graphics, int mx, int my, float tick) {
            renderBackground(graphics, mx, my, tick); header(graphics, "Select Unlock Target", "Progression > Stage");
            panel(graphics, panelLeft(), 47, panelWidth(), height - 77);
            graphics.drawString(font, owner.selectedUnlockType() == 3 ? "ADVANCEMENTS"
                    : owner.selectedUnlockType() == 4 ? "MOBS AND BOSSES" : "DIMENSIONS",
                    panelLeft() + 8, 39, 0xFFFFAA00);
            if (filtered.isEmpty()) graphics.drawCenteredString(font, "No entries match this search.", width / 2, 91, 0xFFAAAAAA);
            if (totalRows > visibleRows) renderScrollbar(graphics, panelLeft() + panelWidth() - 6, 77,
                    Math.max(1, height - 137), totalRows, visibleRows, offset);
            renderWidgets(graphics, mx, my, tick);
        }
    }

    private static final class PhaseDimensionSelectorScreen extends EditorScreen {
        private final Screen parent;
        private final String phaseName;
        private final Consumer<Set<String>> onDone;
        private final Set<String> selected = new LinkedHashSet<>();
        private EditBox search; private String query = ""; private int offset, totalRows, visibleRows;
        PhaseDimensionSelectorScreen(ClientModel model, Screen parent, String phaseName,
                Set<String> current, Consumer<Set<String>> onDone) {
            super("Mob Mutation Dimensions", model); this.parent = parent; this.phaseName = phaseName;
            this.onDone = onDone; selected.addAll(current);
        }
        @Override protected void init() {
            clearWidgets(); int left = panelLeft(), w = panelWidth();
            search = new EditBox(font, left + 8, 66, w - 16, 18, Component.literal("Search dimensions"));
            search.setHint(Component.literal("Search installed dimensions...").withStyle(ChatFormatting.DARK_GRAY));
            search.setValue(query); search.setResponder(value -> { query = value; offset = 0; rebuildWidgets(); });
            addRenderableWidget(search); setInitialFocus(search); setFocused(search); search.setFocused(true);
            List<String> shown = model.dimensions().stream().filter(value -> value.toLowerCase(Locale.ROOT)
                    .contains(query.toLowerCase(Locale.ROOT))).toList();
            totalRows = shown.size(); visibleRows = Math.max(1, (height - 129) / 30); offset = listOffset(offset, totalRows, visibleRows);
            for (int row = 0; row < Math.min(visibleRows, shown.size() - offset); row++) {
                String id = shown.get(offset + row); boolean checked = selected.contains(id);
                addRenderableWidget(new SelectionRowButton(left + 8, 90 + row * 30, w - 16, 28,
                        readableId(id), id, checked, ignored -> { if (!selected.add(id)) selected.remove(id); rebuildWidgets(); }, true));
            }
            button(left, height - 25, 82, "Cancel", ignored -> minecraft.setScreen(parent), "Discard dimension selection changes.");
            button(left + w - 82, height - 25, 82, "Done", ignored -> {
                onDone.accept(Set.copyOf(selected));
                minecraft.setScreen(parent);
            }, "Keep this selection in the phase draft; Apply persists it atomically.");
        }
        @Override public boolean mouseScrolled(double mx, double my, double dx, double dy) {
            int next = listOffset(offset + (dy < 0 ? 1 : -1), totalRows, visibleRows);
            if (next != offset) { offset = next; rebuildWidgets(); return true; }
            return super.mouseScrolled(mx, my, dx, dy);
        }
        @Override public void render(GuiGraphics graphics, int mx, int my, float tick) {
            renderBackground(graphics, mx, my, tick); header(graphics, "Mob Mutation Dimensions", "World Phases > " + phaseName);
            panel(graphics, panelLeft(), 47, panelWidth(), height - 77);
            if (totalRows > visibleRows) renderScrollbar(graphics, panelLeft() + panelWidth() - 6, 77,
                    Math.max(1, height - 116), totalRows, visibleRows, offset);
            renderWidgets(graphics, mx, my, tick);
        }
    }

    private static final class DifficultyTracksScreen extends EditorScreen {
        private EditBox search;
        private String query = "";
        private String expandedId;
        private int offset;
        private int totalTracks, visibleTracks;
        private int tierOffset, tierTotalRows, tierVisibleRows, tierScrollbarY, tierScrollbarHeight;
        DifficultyTracksScreen(ClientModel model) { super("Mob Mutations", model); }
        @Override protected void init() {
            clearWidgets();
            tierTotalRows = 0; tierVisibleRows = 0; tierScrollbarY = 0; tierScrollbarHeight = 0;
            int left = panelLeft(), w = panelWidth();
            search = new EditBox(font, left + 5, 52, w - 126, 18, Component.literal("Search tracks"));
            search.setHint(Component.literal("Search difficulty tracks...").withStyle(ChatFormatting.DARK_GRAY));
            search.setValue(query); search.setResponder(value -> { query = value; offset = 0; rebuildWidgets(); });
            addRenderableWidget(search); setInitialFocus(search); setFocused(search); search.setFocused(true);
            button(left + w - 116, 51, 111, "+ New Track", ignored ->
                    minecraft.setScreen(new TrackEditorScreen(model, null)), "Create an explicit difficulty track.");
            List<JsonObject> tracks = trackObjects(model).stream().filter(track ->
                    read(track, "display_name", readableId(read(track, "id", "track"))).toLowerCase(Locale.ROOT)
                            .contains(query.toLowerCase(Locale.ROOT))).toList();
            totalTracks = tracks.size(); visibleTracks = Math.max(1, (height - 111) / 25);
            offset = listOffset(offset, totalTracks, visibleTracks);
            int y = 76;
            for (JsonObject track : tracks.stream().skip(offset).toList()) {
                if (y + 32 > height - 35) break;
                String id = track.get("id").getAsString(); boolean expanded = id.equals(expandedId);
                JsonArray tiers = WorldAwakenedEditorTrackJson.tiers(track);
                String name = read(track, "display_name", readableId(id));
                button(left + 5, y, w - 96, (expanded ? "▼ " : "▶ ") + name + "  (" + tiers.size() + " tiers)", ignored -> {
                    expandedId = expanded ? null : id; tierOffset = 0; rebuildWidgets();
                }, trackContext(track));
                button(left + w - 86, y, 81, "Edit", ignored -> minecraft.setScreen(new TrackEditorScreen(model, track)),
                        "Rename, enable, duplicate, or delete this track.");
                y += 25;
                if (expanded) {
                    tierTotalRows = tiers.size();
                    tierVisibleRows = Math.max(0, (height - 35 - y - 29) / 25);
                    tierOffset = listOffset(tierOffset, tierTotalRows, tierVisibleRows);
                    tierScrollbarY = y;
                    tierScrollbarHeight = Math.max(1, tierVisibleRows * 25);
                    for (int tierIndex = tierOffset;
                         tierIndex < Math.min(tiers.size(), tierOffset + tierVisibleRows); tierIndex++) {
                        JsonElement tierElement = tiers.get(tierIndex);
                        JsonObject tier = tierElement.getAsJsonObject();
                        String stageId = tier.get("stage_id").getAsString();
                        String poolId = tier.get("pool_id").getAsString();
                        PoolModel pool = model.pool(poolId); StageModel stage = stage(model, stageId);
                        String label = (stage == null ? readableId(stageId) : stage.name()) + "   "
                                + (pool == null ? "Missing pool" : String.format(Locale.ROOT, "%.0f%%",
                                        poolChance(pool.definition()) * 100.0D));
                        Button tierButton = button(left + 21, y, w - 76, label, ignored -> {
                            if (pool != null) minecraft.setScreen(new MutationPoolsScreen(model, pool));
                        }, poolId + "\nOnly the highest unlocked tier in this track is active.");
                        tierButton.active = pool != null && (!track.has("enabled") || track.get("enabled").getAsBoolean());
                        button(left + w - 50, y, 45, "x", ignored -> minecraft.setScreen(new ConfirmActionScreen(model,
                                "Remove this tier?", "The shared stage is kept. The world-owned pool is removed only from this track.",
                                "Remove",
                                () -> removeTier(model, track, stageId, poolId), () -> minecraft.setScreen(this))),
                                "Remove tier; do not delete its shared progression stage.");
                        y += 25;
                    }
                    if (y + 25 <= height - 35) {
                        button(left + 21, y, w - 26, "+ Add Tier", ignored ->
                                minecraft.setScreen(new AddTierScreen(model, track)), "Use an existing shared stage and create a mutation pool.");
                        y += 29;
                    }
                }
            }
            Set<String> trackedPools = trackedPoolIds(model);
            long ungrouped = model.pools().stream().filter(pool -> !trackedPools.contains(pool.id())).count();
            if (y + 25 <= height - 35) button(left + 5, y, w - 10, "Datapack Pools (" + ungrouped + ")  >", ignored ->
                    minecraft.setScreen(new MutationPoolsScreen(model, null)),
                    "Existing Standard and external pools are left ungrouped unless their relationship is explicitly authored.");
            button(left, height - 25, 72, "Back", ignored -> minecraft.setScreen(new DashboardScreen(model)), null);
        }
        @Override public void render(GuiGraphics graphics, int mx, int my, float tick) {
            renderBackground(graphics, mx, my, tick); header(graphics, "Mob Mutations", "Profile Editor > Difficulty Tracks");
            panel(graphics, panelLeft(), 47, panelWidth(), height - 77);
            graphics.drawString(font, "DIFFICULTY TRACKS", panelLeft() + 7, 37, 0xFFFFAA00);
            if (totalTracks > visibleTracks) renderScrollbar(graphics, panelLeft() + panelWidth() - 6, 76,
                    Math.max(1, height - 111), totalTracks, visibleTracks, offset);
            if (tierTotalRows > tierVisibleRows && tierVisibleRows > 0) renderScrollbar(graphics,
                    panelLeft() + panelWidth() - 10, tierScrollbarY, tierScrollbarHeight,
                    tierTotalRows, tierVisibleRows, tierOffset);
            renderWidgets(graphics, mx, my, tick);
        }
        @Override public boolean mouseScrolled(double mx, double my, double dx, double dy) {
            if (expandedId != null && tierTotalRows > tierVisibleRows && tierVisibleRows > 0) {
                int nextTier = listOffset(tierOffset + (dy < 0 ? 1 : -1), tierTotalRows, tierVisibleRows);
                if (nextTier != tierOffset) { tierOffset = nextTier; rebuildWidgets(); return true; }
            }
            int next = listOffset(offset + (dy < 0 ? 1 : -1), totalTracks, visibleTracks);
            if (next != offset) { offset = next; rebuildWidgets(); return true; }
            return super.mouseScrolled(mx, my, dx, dy);
        }
    }

    private static final class TrackEditorScreen extends EditorScreen {
        private final JsonObject source;
        private EditBox name, description;
        private String draftName, draftDescription;
        private boolean enabled = true;
        private final Set<String> dimensions = new LinkedHashSet<>();
        private String error = "";
        TrackEditorScreen(ClientModel model, JsonObject source) {
            super(source == null ? "New Difficulty Track" : "Edit Difficulty Track", model);
            this.source = source == null ? null : source.deepCopy();
            draftName = source == null ? "" : read(source, "display_name", "");
            draftDescription = source == null ? "" : read(source, "description", "");
            if (source != null) {
                enabled = !source.has("enabled") || source.get("enabled").getAsBoolean();
                if (source.has("eligible_dimensions")) source.getAsJsonArray("eligible_dimensions").forEach(value -> dimensions.add(value.getAsString()));
            }
        }
        @Override protected void init() {
            clearWidgets(); int left = panelLeft(), w = panelWidth(), x = left + 14, fieldW = w - 28;
            name = edit(x, 70, fieldW, "Track name", draftName); name.setResponder(value -> draftName = value);
            description = edit(x, 108, fieldW, "Description", draftDescription); description.setResponder(value -> draftDescription = value);
            button(x, 146, fieldW * 2 / 3, "Dimensions (" + dimensions.size() + ")", ignored ->
                    minecraft.setScreen(new TrackDimensionSelectorScreen(model, this)),
                    "Choose zero for unrestricted, or any number of installed server dimensions.");
            button(x + fieldW * 2 / 3 + 4, 146, fieldW / 3 - 4, "Enabled: " + (enabled ? "On" : "Off"), ignored -> {
                enabled = !enabled; rebuildWidgets();
            }, "Disabling a track disables all of its tier pools at compile time.");
            int footer = height - 25;
            button(left, footer, 82, "Cancel", ignored -> minecraft.setScreen(new MutationPoolsScreen(model, null)), null);
            Button save = button(left + w - 94, footer, 94, source == null ? "Create" : "Apply", ignored -> save(),
                    "Validate and persist this track."); save.active = model.editable();
            if (source != null) {
                button(left + w - 280, footer, 86, "Duplicate", ignored -> duplicate(), "Copy this track and independent copies of its tier pools.");
                button(left + w - 188, footer, 88, "Delete", ignored -> minecraft.setScreen(new ConfirmActionScreen(model,
                        "Delete this track?", "Its shared progression stages are kept. World-owned tier pools are removed.",
                        "Delete",
                        this::delete, () -> minecraft.setScreen(this))), "Delete this custom track after confirmation.");
            }
        }
        private EditBox edit(int x, int y, int w, String hint, String value) {
            EditBox box = new EditBox(font, x, y, w, 20, Component.literal(hint)); box.setHint(Component.literal(hint)
                    .withStyle(ChatFormatting.DARK_GRAY)); box.setValue(value); addRenderableWidget(box); return box;
        }
        private void save() {
            if (name.getValue().isBlank()) { error = "Track name is required."; return; }
            JsonObject track = source == null ? new JsonObject() : source.deepCopy();
            track.addProperty("id", source == null ? uniqueTrackId(model, name.getValue()) : source.get("id").getAsString());
            track.addProperty("display_name", name.getValue()); track.addProperty("description", description.getValue());
            track.addProperty("enabled", enabled); JsonArray dimensionJson = new JsonArray();
            dimensions.forEach(dimensionJson::add); track.add("eligible_dimensions", dimensionJson);
            WorldAwakenedEditorTrackJson.mutableTiers(track);
            sendTracks(model, replaceById(model.tracks(), track), model.authoredPools(), null);
        }
        private void duplicate() {
            JsonObject copy = source.deepCopy(); String id = uniqueTrackId(model, read(source, "display_name", "Track") + " Copy");
            copy.addProperty("id", id); copy.addProperty("display_name", read(source, "display_name", "Track") + " Copy");
            JsonArray pools = model.authoredPools().deepCopy(), tiers = new JsonArray();
            for (JsonElement element : WorldAwakenedEditorTrackJson.tiers(source)) {
                JsonObject tier = element.getAsJsonObject(); PoolModel original = model.pool(tier.get("pool_id").getAsString());
                if (original == null) continue;
                JsonObject pool = original.definition().deepCopy(); String poolId = id + "/" + slug(readableId(tier.get("stage_id").getAsString()));
                pool.addProperty("id", poolId); pools.add(pool);
                JsonObject copiedTier = new JsonObject(); copiedTier.addProperty("stage_id", tier.get("stage_id").getAsString());
                copiedTier.addProperty("pool_id", poolId); tiers.add(copiedTier);
            }
            copy.add("tiers", tiers); sendTracks(model, append(model.tracks(), copy), pools, null);
        }
        private void delete() {
            JsonArray pools = model.authoredPools().deepCopy();
            for (JsonElement tier : WorldAwakenedEditorTrackJson.tiers(source)) pools = removeById(pools, tier.getAsJsonObject().get("pool_id").getAsString());
            sendTracks(model, removeById(model.tracks(), source.get("id").getAsString()), pools, null);
        }
        @Override public void render(GuiGraphics graphics, int mx, int my, float tick) {
            renderBackground(graphics, mx, my, tick); header(graphics, source == null ? "New Track" : "Track Details", "Profile Editor > Mob Mutations");
            panel(graphics, panelLeft(), 47, panelWidth(), height - 77);
            graphics.drawString(font, "GENERAL", panelLeft() + 10, 55, 0xFFFFAA00);
            graphics.drawString(font, "Name", panelLeft() + 14, 60, 0xFFBEBEBE);
            graphics.drawString(font, "Description", panelLeft() + 14, 98, 0xFFBEBEBE);
            graphics.drawWordWrap(font, Component.literal("A track organises stage-specific mutation pools. It does not create separate progression state."),
                    panelLeft() + 14, 180, panelWidth() - 28, 0xFFBEBEBE);
            if (!error.isBlank()) graphics.drawString(font, error, panelLeft() + 14, height - 45, 0xFFFF6666);
            renderWidgets(graphics, mx, my, tick);
        }
    }

    private static final class TrackDimensionSelectorScreen extends EditorScreen {
        private final TrackEditorScreen parent;
        private EditBox search; private String query = ""; private int offset;
        private int totalRows, visibleRows;
        TrackDimensionSelectorScreen(ClientModel model, TrackEditorScreen parent) {
            super("Track Dimensions", model); this.parent = parent;
        }
        @Override protected void init() {
            clearWidgets(); int left = panelLeft(), w = panelWidth();
            search = new EditBox(font, left + 8, 53, w - 16, 18, Component.literal("Search dimensions"));
            search.setHint(Component.literal("Search installed dimensions...").withStyle(ChatFormatting.DARK_GRAY));
            search.setValue(query); search.setResponder(value -> { query = value; offset = 0; rebuildWidgets(); });
            addRenderableWidget(search); setInitialFocus(search); setFocused(search); search.setFocused(true);
            List<String> shown = model.dimensions().stream().filter(value -> value.toLowerCase(Locale.ROOT)
                    .contains(query.toLowerCase(Locale.ROOT))).toList();
            totalRows = shown.size(); visibleRows = Math.max(1, (height - 112) / 28);
            offset = listOffset(offset, totalRows, visibleRows);
            for (int row = 0; row < Math.min(visibleRows, shown.size() - offset); row++) {
                String id = shown.get(offset + row); boolean selected = parent.dimensions.contains(id);
                button(left + 8, 77 + row * 28, w - 16, (selected ? "[x] " : "[ ] ") + readableId(id), ignored -> {
                    if (!parent.dimensions.add(id)) parent.dimensions.remove(id); rebuildWidgets();
                }, id);
            }
            button(left, height - 25, 100, "Any / Clear", ignored -> { parent.dimensions.clear(); rebuildWidgets(); },
                    "An empty selection does not restrict the track by dimension.");
            button(left + w - 82, height - 25, 82, "Done", ignored -> minecraft.setScreen(parent), null);
        }
        @Override public boolean mouseScrolled(double mx, double my, double dx, double dy) {
            int next = listOffset(offset + (dy < 0 ? 1 : -1), totalRows, visibleRows);
            if (next != offset) { offset = next; rebuildWidgets(); return true; }
            return super.mouseScrolled(mx, my, dx, dy);
        }
        @Override public void render(GuiGraphics graphics, int mx, int my, float tick) {
            renderBackground(graphics, mx, my, tick); header(graphics, "Select Dimensions", "Mob Mutations > Track");
            panel(graphics, panelLeft(), 47, panelWidth(), height - 77);
            if (totalRows > visibleRows) renderScrollbar(graphics, panelLeft() + panelWidth() - 6, 77,
                    Math.max(1, height - 112), totalRows, visibleRows, offset);
            renderWidgets(graphics, mx, my, tick);
        }
    }

    private static final class AddTierScreen extends EditorScreen {
        private final JsonObject track;
        private EditBox search;
        private String query = "";
        private String selectedStage;
        private boolean copyPrevious = true;
        private String starterMutator;
        private String error = "";
        private int offset, totalRows, visibleRows;
        AddTierScreen(ClientModel model, JsonObject track) { this(model, track, null); }
        AddTierScreen(ClientModel model, JsonObject track, String selectedStage) {
            super("Add Difficulty Tier", model); this.track = track.deepCopy(); this.selectedStage = selectedStage;
            this.starterMutator = model.mutators().isEmpty() ? null : model.mutators().get(0).id();
            this.copyPrevious = !WorldAwakenedEditorTrackJson.tiers(this.track).isEmpty();
        }
        @Override protected void init() {
            clearWidgets(); int left = panelLeft(), w = panelWidth();
            search = new EditBox(font, left + 12, 68, w - 24, 18, Component.literal("Search shared stages"));
            search.setHint(Component.literal("Search shared stages...").withStyle(ChatFormatting.DARK_GRAY)); search.setValue(query);
            search.setResponder(value -> { query = value; offset = 0; rebuildWidgets(); }); addRenderableWidget(search);
            setInitialFocus(search); setFocused(search); search.setFocused(true);
            Set<String> used = new LinkedHashSet<>(); WorldAwakenedEditorTrackJson.tiers(track)
                    .forEach(value -> used.add(value.getAsJsonObject().get("stage_id").getAsString()));
            List<StageModel> choices = model.stages().stream().filter(stage -> !used.contains(stage.id()))
                    .filter(stage -> (stage.name() + " " + stage.description() + " " + stage.id()).toLowerCase(Locale.ROOT)
                            .contains(query.toLowerCase(Locale.ROOT))).toList();
            totalRows = choices.size(); visibleRows = Math.max(1, (height - 205) / 34);
            offset = listOffset(offset, totalRows, visibleRows);
            for (int row = 0; row < Math.min(visibleRows, Math.max(0, choices.size() - offset)); row++) {
                StageModel stage = choices.get(offset + row); int rowY = 91 + row * 34;
                addRenderableWidget(new SelectionRowButton(left + 12, rowY, w - 24, 32, stage.name(),
                        unlockSummary(model, stage.id()), stage.id().equals(selectedStage), ignored -> {
                    selectedStage = stage.id(); rebuildWidgets();
                }, false));
            }
            int optionsY = Math.min(height - 82, 95 + visibleRows * 34);
            button(left + 12, optionsY, (w - 28) / 2, "Create New Stage", ignored -> minecraft.setScreen(
                    new StageEditorScreen(model, null, track.get("id").getAsString())),
                    "Uses the same shared stage creation screen. Return here after creation.");
            Button sourceMode = button(left + 16 + (w - 28) / 2, optionsY, (w - 28) / 2,
                    copyPrevious ? "Copy Previous Tier" : "Start Empty", ignored -> {
                copyPrevious = !copyPrevious; rebuildWidgets();
            }, "Copied settings are independent. Start Empty creates the smallest valid pool with one chosen starter mutator.");
            sourceMode.active = !WorldAwakenedEditorTrackJson.tiers(track).isEmpty();
            if (!copyPrevious) button(left + 12, optionsY + 24, w - 24,
                    starterMutator == null ? "No mutators available" : "Starter Mutator: " + readableId(starterMutator), ignored -> {
                        if (starterMutator != null) minecraft.setScreen(new StarterMutatorScreen(model, this));
                    }, "Select the first mutator explicitly; add or remove more in the pool editor.");
            int footer = height - 25; button(left, footer, 82, "Cancel", ignored -> minecraft.setScreen(new MutationPoolsScreen(model, null)), null);
            Button create = button(left + w - 100, footer, 100, "Create Tier", ignored -> create(),
                    "Atomically create the linked mutation pool, then open the existing pool editor.");
            create.active = model.editable() && selectedStage != null
                    && (copyPrevious && !WorldAwakenedEditorTrackJson.tiers(track).isEmpty() || starterMutator != null);
        }
        @Override public boolean mouseScrolled(double mx, double my, double dx, double dy) {
            int next = listOffset(offset + (dy < 0 ? 1 : -1), totalRows, visibleRows);
            if (next != offset) { offset = next; rebuildWidgets(); return true; }
            return super.mouseScrolled(mx, my, dx, dy);
        }
        private void create() {
            if (selectedStage == null) { error = "Choose a progression stage."; return; }
            String poolId = track.get("id").getAsString() + "/" + slug(readableId(selectedStage));
            int suffix = 2; String base = poolId;
            while (model.pool(poolId) != null) poolId = base + "_" + suffix++;
            JsonObject pool;
            JsonArray tiers = WorldAwakenedEditorTrackJson.mutableTiers(track);
            if (copyPrevious && !tiers.isEmpty()) {
                PoolModel previous = model.pool(tiers.get(tiers.size() - 1).getAsJsonObject().get("pool_id").getAsString());
                pool = previous == null ? defaultPool(poolId, track, starterMutator)
                        : WorldAwakenedEditorTierDrafts.copyPool(previous.definition(), poolId);
            } else pool = defaultPool(poolId, track, starterMutator);
            WorldAwakenedEditorTierDrafts.appendTier(track, selectedStage, poolId);
            JsonArray pools = replaceById(model.authoredPools(), pool);
            sendTracks(model, replaceById(model.tracks(), track), pools, poolId);
        }
        @Override public void render(GuiGraphics graphics, int mx, int my, float tick) {
            renderBackground(graphics, mx, my, tick); header(graphics, "Add Difficulty Tier", "Mob Mutations > " + read(track, "display_name", "Track"));
            panel(graphics, panelLeft(), 47, panelWidth(), height - 77);
            graphics.drawString(font, "CHOOSE A SHARED PROGRESSION STAGE", panelLeft() + 12, 55, 0xFFFFAA00);
            if (totalRows == 0) graphics.drawCenteredString(font, "No available stages match this search.", width / 2, 102, 0xFFAAAAAA);
            if (totalRows > visibleRows) renderScrollbar(graphics, panelLeft() + panelWidth() - 7, 91,
                    Math.max(1, visibleRows * 34), totalRows, visibleRows, offset);
            if (!error.isBlank()) graphics.drawString(font, error, panelLeft() + 12, height - 45, 0xFFFF6666);
            renderWidgets(graphics, mx, my, tick);
        }
    }

    private static final class StarterMutatorScreen extends EditorScreen {
        private final AddTierScreen parent; private EditBox search; private String query = ""; private int offset;
        private int totalRows, visibleRows;
        StarterMutatorScreen(ClientModel model, AddTierScreen parent) { super("Select Starter Mutator", model); this.parent = parent; }
        @Override protected void init() {
            clearWidgets(); int left = panelLeft(), w = panelWidth();
            search = new EditBox(font, left + 8, 53, w - 16, 18, Component.literal("Search mutators"));
            search.setHint(Component.literal("Search existing mutators...").withStyle(ChatFormatting.DARK_GRAY)); search.setValue(query);
            search.setResponder(value -> { query = value; offset = 0; rebuildWidgets(); }); addRenderableWidget(search);
            setInitialFocus(search); setFocused(search); search.setFocused(true);
            List<MutatorModel> shown = model.mutators().stream().filter(value -> value.name().toLowerCase(Locale.ROOT)
                    .contains(query.toLowerCase(Locale.ROOT)) || value.id().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))).toList();
            totalRows = shown.size(); visibleRows = Math.max(1, (height - 112) / 32);
            offset = listOffset(offset, totalRows, visibleRows);
            for (int row = 0; row < Math.min(visibleRows, shown.size() - offset); row++) {
                MutatorModel mutator = shown.get(offset + row); addRenderableWidget(new SelectionRowButton(left + 8, 77 + row * 32,
                        w - 16, 30, mutator.name(), mutator.id(),
                        mutator.id().equals(parent.starterMutator), ignored -> { parent.starterMutator = mutator.id(); minecraft.setScreen(parent); }));
            }
            button(left, height - 25, 82, "Cancel", ignored -> minecraft.setScreen(parent), null);
        }
        @Override public boolean mouseScrolled(double mx, double my, double dx, double dy) {
            int next = listOffset(offset + (dy < 0 ? 1 : -1), totalRows, visibleRows);
            if (next != offset) { offset = next; rebuildWidgets(); return true; }
            return super.mouseScrolled(mx, my, dx, dy);
        }
        @Override public void render(GuiGraphics graphics, int mx, int my, float tick) {
            renderBackground(graphics, mx, my, tick); header(graphics, "Select Starter Mutator", "Mob Mutations > Add Tier");
            panel(graphics, panelLeft(), 47, panelWidth(), height - 77);
            if (totalRows > visibleRows) renderScrollbar(graphics, panelLeft() + panelWidth() - 6, 77,
                    Math.max(1, height - 112), totalRows, visibleRows, offset);
            renderWidgets(graphics, mx, my, tick);
        }
    }

    private static final class ConfirmActionScreen extends EditorScreen {
        private final String prompt, detail, actionLabel; private final Runnable confirm, cancel;
        ConfirmActionScreen(ClientModel model, String prompt, String detail, String actionLabel,
                            Runnable confirm, Runnable cancel) {
            super("Confirm", model);
            this.prompt = prompt;
            this.detail = detail;
            this.actionLabel = actionLabel;
            this.confirm = confirm;
            this.cancel = cancel;
        }
        @Override protected void init() {
            clearWidgets();
            int cx = width / 2;
            int buttonY = confirmationButtonY();
            button(cx - 102, buttonY, 96, "Cancel", ignored -> cancel.run(), null);
            button(cx + 6, buttonY, 96, actionLabel, ignored -> confirm.run(), null);
        }
        private int detailWidth() {
            return Math.max(80, Math.min(420, width - 32));
        }
        private List<FormattedCharSequence> detailLines() {
            return font.split(Component.literal(detail), detailWidth());
        }
        private int detailY() {
            return height / 2 - 10;
        }
        private int confirmationButtonY() {
            int contentBottom = detailY() + Math.max(1, detailLines().size()) * font.lineHeight;
            return Math.min(height - 24, Math.max(height / 2 + 24, contentBottom + 12));
        }
        @Override public void render(GuiGraphics graphics, int mx, int my, float tick) {
            renderBackground(graphics, mx, my, tick);
            graphics.drawCenteredString(font, prompt, width / 2, height / 2 - 28, 0xFFFFFFFF);
            List<FormattedCharSequence> lines = detailLines();
            for (int line = 0; line < lines.size(); line++) {
                graphics.drawCenteredString(font, lines.get(line), width / 2,
                        detailY() + line * font.lineHeight, 0xFFBEBEBE);
            }
            renderWidgets(graphics, mx, my, tick);
        }
    }

    private static final class AscensionOffersScreen extends EditorScreen implements UnlockTargetOwner {
        private static final int MAX_OFFER_CHOICES = 5;
        private static final int TOP = 52;
        private static final int ROW = 38;
        private static final int OFFER_CONDITION_ROW_SPACING = 36;
        private EditBox search, name, description;
        private String query = "", selectedId;
        private int offset, selectedConditionIndex;
        private boolean offersExpanded = true;
        private int browserX, browserY, browserW, browserH, browserVisible, browserTotal;
        private int generalSectionY, choicesSectionY, choicesSectionHeight, availabilitySectionY, availabilitySectionHeight;
        private OfferDraft draft;

        AscensionOffersScreen(ClientModel model) { super("Ascension", model); }

        @Override protected void init() {
            clearWidgets();
            int left = panelLeft(), width = panelWidth(), browser = Math.min(270, Math.max(210, width * 36 / 100));
            int bottom = height - 31;
            search = new EditBox(font, left + 5, TOP + 5, browser - 92, 18, Component.literal("Search offers"));
            search.setHint(Component.literal("Search Ascension offers...").withStyle(ChatFormatting.DARK_GRAY));
            search.setValue(query);
            search.setResponder(value -> { query = value; offset = 0; rebuildWidgets(); });
            addRenderableWidget(search);
            setInitialFocus(search); setFocused(search); search.setFocused(true);
            button(left + browser - 82, TOP + 4, 77, "+ Offer", ignored -> createOffer(),
                    "Create a new personal Ascension offer.");
            button(left + 5, TOP + 28, browser - 10,
                    (offersExpanded ? "▼ " : "▶ ") + "ASCENSION OFFERS (" + browserOfferCount() + ")",
                    ignored -> { offersExpanded = !offersExpanded; offset = 0; rebuildWidgets(); },
                    "Expand or collapse personal permanent-reward choices.");
            List<OfferModel> shown = offersExpanded ? filteredOffers() : List.of();
            if (selectedId == null && !shown.isEmpty()) select(shown.getFirst());
            int viewportY = TOP + 52, viewportH = Math.max(ROW, bottom - viewportY - 5);
            int visible = WorldAwakenedEditorListLayout.visibleRows(viewportH, ROW);
            browserX = left; browserY = viewportY; browserW = browser; browserH = viewportH;
            browserVisible = visible; browserTotal = shown.size();
            offset = listOffset(offset, shown.size(), visible);
            boolean overflow = WorldAwakenedEditorListLayout.needsScrollbar(shown.size(), visible);
            int rowWidth = browser - 10 - (overflow ? 8 : 0);
            for (int row = 0; row < Math.min(visible, shown.size() - offset); row++) {
                OfferModel offer = shown.get(offset + row);
                boolean selected = offer.id().equals(selectedId);
                PoolTierRowButton entry = new PoolTierRowButton(left + 5, viewportY + row * ROW, rowWidth, ROW - 2,
                        offer.name(), availabilitySummary(model, offer.definition()), offerEnabled(offer.definition()) ? "On" : "Off",
                        selected, selected && draft != null && draft.dirty, ignored -> changeSelection(offer));
                entry.setTooltip(Tooltip.create(Component.literal(offer.description().isBlank()
                        ? "Configure when this personal Ascension choice becomes available." : offer.description())));
                addRenderableWidget(entry);
            }
            if (overflow) addRenderableWidget(new EditorScrollbar(left + browser - 11, viewportY, 6, viewportH,
                    shown.size(), visible, offset, value -> { offset = value; rebuildWidgets(); }));
            if (draft != null) addDetails(left + browser + 10, TOP, width - browser - 10);
            button(left, height - 25, 72, "Back", ignored -> leave(() -> minecraft.setScreen(new DashboardScreen(model))), null);
            button(left + browser + 10, height - 25, 126, "Reset All Defaults", ignored ->
                    minecraft.setScreen(new ConfirmActionScreen(model, "Reset every editor setting?",
                            "This removes all world-authored changes and restores the built-in Standard profile. This cannot be undone.",
                            "Reset All", () -> {
                                PacketDistributor.sendToServer(new WorldAwakenedProfileOperationPayload(
                                        model.settingsRevision(), model.profileRevision(), "reset_defaults"));
                            }, () -> minecraft.setScreen(this))), "Restore the complete built-in profile and clear world-authored changes.");
        }

        private void addDetails(int x, int y, int width) {
            int sx = x + 5, sw = width - 10, ix = sx + 6, iw = sw - 12;
            generalSectionY = y + 43;
            availabilitySectionHeight = draft.phaseLinked ? 80 : offerAvailabilityHeight();
            choicesSectionY = generalSectionY + 98;
            int contentBottom = height - 36;
            // Reserve the complete five-choice footprint so the availability controls do not move as choices are edited.
            int preferredChoicesHeight = 30 + MAX_OFFER_CHOICES * 32;
            choicesSectionHeight = Math.max(56, Math.min(preferredChoicesHeight,
                    contentBottom - choicesSectionY - availabilitySectionHeight - 6));
            availabilitySectionY = choicesSectionY + choicesSectionHeight + 6;
            int generalY = generalSectionY, availabilityY = availabilitySectionY;
            int half = (iw - 5) / 2;
            name = new EditBox(font, ix, generalY + 21, half, 18, Component.literal("Offer name"));
            name.setMaxLength(PHASE_NAME_MAX_LENGTH); name.setValue(draft.name);
            name.setTooltip(Tooltip.create(Component.literal("The player-facing name of this Ascension offer.")));
            name.setResponder(value -> { draft.name = value; draft.dirty = true; }); addRenderableWidget(name);
            description = new EditBox(font, ix + half + 5, generalY + 21, iw - half - 5, 18, Component.literal("Description"));
            description.setMaxLength(PHASE_DESCRIPTION_MAX_LENGTH); description.setValue(draft.description);
            description.setTooltip(Tooltip.create(Component.literal("The player-facing explanation shown with this offer.")));
            description.setResponder(value -> { draft.description = value; draft.dirty = true; }); addRenderableWidget(description);
            button(ix, generalY + 47, half, "Enabled: " + (draft.enabled ? "On" : "Off"), ignored -> {
                draft.enabled = !draft.enabled; draft.dirty = true; rebuildWidgets();
            }, "Disabled offers remain defined but cannot become newly available.");
            button(ix + half + 5, generalY + 47, iw - half - 5,
                    "Repeat: " + (draft.oncePerPlayer ? "Once per player" : "Once per source"), ignored -> {
                        draft.oncePerPlayer = !draft.oncePerPlayer; draft.dirty = true; rebuildWidgets();
                    }, "Once per player is recommended. Once per source is an advanced repeatable mode.");

            button(ix, availabilityY + 21, iw,
                    draft.phaseLinked ? "When World Phase unlocks" : "When custom conditions are met", ignored -> {
                        draft.phaseLinked = !draft.phaseLinked; draft.dirty = true; rebuildWidgets();
                    }, "Choose the simple phase-linked workflow or independent custom conditions.");
            if (draft.phaseLinked) {
                button(ix, availabilityY + 47, iw, "World Phase: " + stageName(draft.phaseId), ignored ->
                        minecraft.setScreen(new OfferPhasePickerScreen(model, this, draft.phaseId, selected -> {
                            draft.phaseId = selected; draft.dirty = true;
                        })), "The offer follows this phase automatically if its unlock conditions change.");
            } else {
                int operatorW = (iw - 5) / 2;
                button(ix, availabilityY + 47, operatorW,
                        draft.requireAll ? "All conditions (AND)" : "Any condition (OR)", ignored -> {
                            draft.requireAll = !draft.requireAll; draft.dirty = true; rebuildWidgets();
                        }, draft.requireAll ? "Every condition must be completed, in any order."
                                : "Any one condition makes the offer available.");
                button(ix + operatorW + 5, availabilityY + 47, iw - operatorW - 5, "Copy from World Phase", ignored ->
                        minecraft.setScreen(new OfferPhasePickerScreen(model, this, draft.phaseId, this::copyPhaseConditions)),
                        "Copy a phase's current conditions. The copied conditions then become independent.");
                addOfferConditionControls(ix, availabilityY + 69, iw);
            }

            int choiceButtonWidth = Math.min(150, Math.max(108, iw / 4));
            button(ix + iw - choiceButtonWidth, choicesSectionY + 5, choiceButtonWidth,
                    "Edit Choices (" + draft.rewardIds.size() + ")", ignored ->
                    minecraft.setScreen(new AscensionRewardSelectorScreen(model, this, draft)),
                    "Choose, create, edit, or remove the permanent rewards offered to the player.");

            int footerY = height - 25;
            Button apply = button(x + width - 300, footerY, 92, "Apply", ignored -> apply(),
                    "Validate and atomically save this offer and its availability rules.");
            apply.active = model.editable() && draft.dirty;
            boolean overridden = containsId(model.authoredOffers(), draft.source.id());
            Button restore = button(x + width - 203, footerY, 98, overridden ? "Restore Source" : "Using Source", ignored ->
                    minecraft.setScreen(new ConfirmActionScreen(model, "Restore source offer?",
                            "This discards the world-authored version and restores the built-in definition.", "Restore",
                            this::restore, () -> minecraft.setScreen(this))), "Restore the built-in offer definition.");
            restore.active = overridden;
            Button discard = button(x + width - 100, footerY, 100, "Discard Changes", ignored -> discard(),
                    "Discard unsaved changes to this offer.");
            discard.active = draft.dirty;
        }

        private void addOfferConditionControls(int x, int y, int width) {
            for (int index = 0; index < draft.conditions.size(); index++) {
                UnlockConditionDraft condition = draft.conditions.get(index); final int conditionIndex = index;
                int typeW = Math.max(105, width * 30 / 100), removeW = 24, valueW = width - typeW - removeW - 8;
                button(x, y, typeW, (index + 1) + ". " + conditionName(condition.type), ignored -> {
                    condition.type = condition.type >= 1 && condition.type < 4 ? condition.type + 1 : 1;
                    condition.target = condition.type == 1 ? "3" : firstTarget(condition.type);
                    draft.dirty = true; rebuildWidgets();
                }, "Choose how this offer becomes available.");
                if (condition.type == 1) {
                    EditBox day = new EditBox(font, x + typeW + 4, y, valueW, 20, Component.literal("World day"));
                    day.setFilter(text -> text.matches("[0-9]{0,7}")); day.setValue(condition.target == null ? "3" : condition.target);
                    day.setTooltip(Tooltip.create(Component.literal(
                            "World day when this offer becomes available. Enter a whole number from 0 onward.")));
                    day.setResponder(value -> { condition.target = value; draft.dirty = true; }); addRenderableWidget(day);
                } else {
                    button(x + typeW + 4, y, valueW, targetLabel(model, condition.type, condition.target), ignored -> {
                        selectedConditionIndex = conditionIndex;
                        minecraft.setScreen(new StageUnlockTargetScreen(model, this, this));
                    }, conditionTargetTooltip(condition.type, condition.target));
                }
                Button remove = button(x + width - removeW, y, removeW, "×", ignored -> {
                    if (draft.conditions.size() > 1) { draft.conditions.remove(conditionIndex); draft.dirty = true; rebuildWidgets(); }
                }, "Remove this condition.");
                remove.active = draft.conditions.size() > 1;
                y += index < draft.conditions.size() - 1 ? OFFER_CONDITION_ROW_SPACING : 24;
            }
            Button add = button(x, y, Math.min(180, width), "+ Add Condition (" + draft.conditions.size() + "/3)", ignored -> {
                int type = firstUnusedConditionType(); draft.conditions.add(new UnlockConditionDraft(type, type == 1 ? "3" : firstTarget(type)));
                draft.dirty = true; rebuildWidgets();
            }, "Add another condition, up to three.");
            add.active = draft.conditions.size() < 3;
        }

        private int offerAddConditionY(int availabilityY) {
            return availabilityY + 69 + Math.max(0, draft.conditions.size() - 1) * OFFER_CONDITION_ROW_SPACING + 24;
        }

        private int offerAvailabilityHeight() {
            return offerAddConditionY(0) + 25 + font.lineHeight + 8;
        }

        private void apply() {
            if (draft.name.isBlank()) { showLocalError("Offer name is required."); return; }
            if (draft.rewardIds.isEmpty()) { showLocalError("Choose at least one reward."); return; }
            if (draft.phaseLinked && (draft.phaseId == null || draft.phaseId.isBlank())) {
                showLocalError("Choose a World Phase."); return;
            }
            for (UnlockConditionDraft condition : draft.conditions) if (condition.target == null || condition.target.isBlank()) {
                showLocalError("Choose a value for every custom condition."); return;
            }
            JsonObject definition = draft.source.definition().deepCopy();
            definition.addProperty("display_name", draft.name); definition.addProperty("description", draft.description);
            definition.addProperty("enabled", draft.enabled);
            definition.addProperty("offer_repeat_policy", draft.oncePerPlayer ? "once_per_player" : "once_per_source");
            JsonArray rewards = new JsonArray(); draft.rewardIds.forEach(rewards::add); definition.add("candidate_rewards", rewards);
            definition.addProperty("choice_count", Math.min(MAX_OFFER_CHOICES, draft.rewardIds.size())); definition.addProperty("selection_count", 1);
            definition.add("trigger_conditions", new JsonArray());
            if (draft.phaseLinked) {
                JsonObject filters = new JsonObject(); JsonArray all = new JsonArray(); all.add(draft.phaseId);
                filters.add("all_of", all); definition.add("stage_filters", filters);
            } else definition.remove("stage_filters");
            JsonArray offers = replaceById(model.authoredOffers(), definition);
            JsonArray triggers = removeOfferTriggers(model.authoredTriggers(), draft.source.id());
            if (!draft.phaseLinked) for (JsonElement trigger : buildOfferTriggers(draft.source.id(), draft.conditions, draft.requireAll))
                triggers = replaceById(triggers, trigger.getAsJsonObject());
            JsonArray authoredRewards = model.authoredRewards().deepCopy();
            for (String rewardId : draft.deletedRewardIds) authoredRewards = removeById(authoredRewards, rewardId);
            for (JsonObject reward : draft.newRewards.values()) authoredRewards = replaceById(authoredRewards, reward);
            sendAscensionAuthoring(model, triggers, offers, authoredRewards); draft.dirty = false;
        }

        private void restore() {
            JsonArray offers = removeById(model.authoredOffers(), draft.source.id());
            JsonArray triggers = removeOfferTriggers(model.authoredTriggers(), draft.source.id());
            sendAscensionAuthoring(model, triggers, offers, model.authoredRewards());
        }

        private void discard() {
            if (isNewDraft()) { draft = null; selectedId = null; }
            else draft = new OfferDraft(model, draft.source);
            clearLocalNotice(); rebuildWidgets();
        }
        private void createOffer() {
            leave(() -> {
                String id = "worldawakened:world/ascension_offer_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
                JsonObject definition = new JsonObject(); definition.addProperty("schema_version", 1); definition.addProperty("id", id);
                definition.addProperty("display_name", "New Ascension Offer"); definition.addProperty("description", "Choose a permanent adaptation.");
                definition.addProperty("enabled", true); definition.add("trigger_conditions", new JsonArray());
                definition.add("candidate_rewards", new JsonArray()); definition.add("candidate_reward_tags", new JsonArray());
                definition.addProperty("offer_mode", "explicit_list"); definition.add("weighting_rules", new JsonObject());
                definition.addProperty("choice_count", 1); definition.addProperty("selection_count", 1); definition.addProperty("ui_priority", 0);
                definition.addProperty("allow_duplicates_across_players", true); definition.addProperty("offer_repeat_policy", "once_per_player");
                definition.addProperty("reward_repeat_policy", "block_all");
                OfferModel offer = new OfferModel(id, "New Ascension Offer", "Choose a permanent adaptation.", definition);
                selectedId = id; draft = new OfferDraft(model, offer); draft.dirty = true; rebuildWidgets();
            });
        }
        private void select(OfferModel offer) { selectedId = offer.id(); draft = new OfferDraft(model, offer); clearLocalNotice(); }
        private void changeSelection(OfferModel offer) { leave(() -> { select(offer); rebuildWidgets(); }); }
        private void leave(Runnable action) {
            if (draft != null && draft.dirty) minecraft.setScreen(new ConfirmActionScreen(model,
                    "Discard unsaved Ascension changes?", "The selected offer has changes which have not been applied.",
                    "Discard", () -> {
                        minecraft.setScreen(this);
                        action.run();
                    }, () -> minecraft.setScreen(this))); else action.run();
        }
        private List<OfferModel> filteredOffers() {
            String needle = query.toLowerCase(Locale.ROOT);
            LinkedHashMap<String, OfferModel> offers = new LinkedHashMap<>();
            model.offers().forEach(offer -> offers.put(offer.id(), offer));
            // The selected browser row is a live summary of its local draft, not its last saved snapshot.
            if (draft != null) offers.put(draft.source.id(), draftBrowserOffer());
            return offers.values().stream().filter(offer -> offer.name().toLowerCase(Locale.ROOT).contains(needle)
                    || offer.id().toLowerCase(Locale.ROOT).contains(needle)).sorted(Comparator.comparing(OfferModel::name)).toList();
        }
        private int browserOfferCount() { return model.offers().size() + (isNewDraft() ? 1 : 0); }
        private boolean isNewDraft() { return draft != null && model.offers().stream().noneMatch(offer -> offer.id().equals(draft.source.id())); }
        private OfferModel draftBrowserOffer() {
            JsonObject definition = draft.source.definition().deepCopy();
            definition.addProperty("display_name", draft.name);
            definition.addProperty("description", draft.description);
            definition.addProperty("enabled", draft.enabled);
            if (draft.phaseLinked && draft.phaseId != null && !draft.phaseId.isBlank()) {
                JsonObject filters = new JsonObject(); JsonArray all = new JsonArray(); all.add(draft.phaseId);
                filters.add("all_of", all); definition.add("stage_filters", filters);
            } else definition.remove("stage_filters");
            return new OfferModel(draft.source.id(), draft.name, draft.description, definition);
        }
        private String stageName(String id) { StageModel stage = stage(model, id); return stage == null ? "Choose..." : stage.name(); }
        private String firstTarget(int type) { List<String> values = targetsForUnlock(model, type); return values.isEmpty() ? "" : values.getFirst(); }
        private int firstUnusedConditionType() { for (int type = 1; type <= 4; type++) { int candidate = type;
            if (draft.conditions.stream().noneMatch(value -> value.type == candidate)) return type; } return 1; }
        private void copyPhaseConditions(String stageId) {
            StageModel stage = stage(model, stageId); if (stage == null) return;
            StageDraft source = new StageDraft(model, stage); draft.phaseId = stageId; draft.conditions.clear();
            source.unlockConditions.stream().filter(condition -> condition.type >= 1 && condition.type <= 4).limit(3)
                    .forEach(condition -> draft.conditions.add(new UnlockConditionDraft(condition.type, condition.target)));
            if (draft.conditions.isEmpty()) draft.conditions.add(new UnlockConditionDraft(1, "3"));
            draft.requireAll = source.requireAll; draft.dirty = true;
        }
        @Override public List<String> unlockTargets() { return targetsForUnlock(model, selectedUnlockType()); }
        @Override public String unlockTarget() { return draft.conditions.get(selectedConditionIndex).target; }
        @Override public void chooseUnlockTarget(String value) { draft.conditions.get(selectedConditionIndex).target = value; draft.dirty = true; }
        @Override public int selectedUnlockType() { return draft.conditions.get(selectedConditionIndex).type; }

        @Override public void render(GuiGraphics graphics, int mx, int my, float tick) {
            renderBackground(graphics, mx, my, tick); header(graphics, "Ascension", "Profile Editor > Ascension");
            int left = panelLeft(), width = panelWidth(), browser = Math.min(270, Math.max(210, width * 36 / 100));
            panel(graphics, left, TOP, browser, height - TOP - 31);
            if (draft != null) {
                int x = left + browser + 10, w = width - browser - 10, sx = x + 5, sw = w - 10;
                panel(graphics, x, TOP, w, height - TOP - 31);
                graphics.drawString(font, Component.literal(draft.name).withStyle(ChatFormatting.BOLD), x + 10, TOP + 7, 0xFFFFFF);
                graphics.drawString(font, containsId(model.authoredOffers(), draft.source.id()) ? "World override" : "Bundled or datapack source", x + 10, TOP + 20, 0xBEBEBE);
                ascensionSection(graphics, sx, generalSectionY, sw, 92, "GENERAL");
                ascensionSection(graphics, sx, choicesSectionY, sw, choicesSectionHeight, "ASCENSION CHOICES");
                int ry = choicesSectionY + 27, shown = 0;
                for (String rewardId : draft.rewardIds) {
                    RewardModel reward = draft.reward(model, rewardId);
                    if (ry + 29 > choicesSectionY + choicesSectionHeight - 4) break;
                    graphics.fill(sx + 7, ry, sx + sw - 7, ry + 28, 0x40333333);
                    sectionOutline(graphics, sx + 7, ry, sw - 14, 28);
                    drawMarqueeLeft(graphics, font, Component.literal(reward == null ? readableId(rewardId) : reward.name()),
                            sx + 13, ry + 4, sw - 26, 0xFFFFFFFF);
                    drawMarqueeLeft(graphics, font, Component.literal(reward == null ? "Missing reward" : rewardEffectSummary(reward.definition())),
                            sx + 13, ry + 16, sw - 26, 0xFFBEBEBE);
                    ry += 32; shown++;
                }
                if (shown < draft.rewardIds.size() && ry + font.lineHeight <= choicesSectionY + choicesSectionHeight - 3)
                    graphics.drawString(font, "+ " + (draft.rewardIds.size() - shown) + " more choices", sx + 10, ry, 0xFFBEBEBE);
                ascensionSection(graphics, sx, availabilitySectionY, sw,
                        Math.max(56, height - 31 - availabilitySectionY - 5), "WHEN TO OFFER");
                if (!draft.phaseLinked && draft.conditions.size() > 1) {
                    int separatorY = availabilitySectionY + 94;
                    for (int index = 0; index < draft.conditions.size() - 1; index++) {
                        graphics.drawCenteredString(font, draft.requireAll ? "AND" : "OR", sx + sw / 2,
                                separatorY + index * OFFER_CONDITION_ROW_SPACING, 0xFFFFAA00);
                    }
                }
                if (!draft.phaseLinked) {
                    int explanationY = offerAddConditionY(availabilitySectionY) + 25;
                    graphics.drawString(font, draft.requireAll
                                    ? "Every condition must be completed, in any order."
                                    : "Any one condition makes this offer available.",
                            sx + 8, explanationY, 0xFFBEBEBE);
                }
            }
            renderWidgets(graphics, mx, my, tick);
        }
        @Override public boolean mouseScrolled(double mx, double my, double dx, double dy) {
            if (mx >= browserX && mx < browserX + browserW && my >= browserY && my < browserY + browserH
                    && browserTotal > browserVisible) {
                int next = listOffset(offset + (dy < 0 ? 1 : -1), browserTotal, browserVisible);
                if (next != offset) { offset = next; rebuildWidgets(); return true; }
            }
            return super.mouseScrolled(mx, my, dx, dy);
        }
        private void ascensionSection(GuiGraphics graphics, int x, int y, int w, int h, String title) {
            graphics.fill(x, y, x + w, y + h, 0x50101010); graphics.fill(x, y, x + w, y + 1, 0xFF666666);
            graphics.fill(x, y + h - 1, x + w, y + h, 0xFF333333); graphics.fill(x, y, x + 1, y + h, 0xFF666666);
            graphics.fill(x + w - 1, y, x + w, y + h, 0xFF333333); graphics.drawString(font, title, x + 6, y + 6, 0xFFFFAA00);
        }
    }

    private static final class OfferDraft {
        final OfferModel source; final List<UnlockConditionDraft> conditions = new ArrayList<>();
        final LinkedHashSet<String> rewardIds = new LinkedHashSet<>();
        final LinkedHashMap<String, JsonObject> newRewards = new LinkedHashMap<>();
        final LinkedHashSet<String> deletedRewardIds = new LinkedHashSet<>();
        String name, description, phaseId; boolean enabled, phaseLinked, requireAll = true, oncePerPlayer, dirty;
        OfferDraft(ClientModel model, OfferModel source) {
            this.source = source; name = source.name(); description = source.description();
            JsonObject definition = source.definition(); enabled = offerEnabled(definition);
            rewardIds.addAll(offerRewardIds(definition));
            oncePerPlayer = !"once_per_source".equals(read(definition, "offer_repeat_policy", "once_per_player"));
            phaseId = offerPhaseId(definition); phaseLinked = phaseId != null;
            if (!phaseLinked) readOfferConditions(model, source.id(), conditions);
            if (conditions.isEmpty()) conditions.add(new UnlockConditionDraft(1, "3"));
        }
        RewardModel reward(ClientModel model, String id) {
            JsonObject created = newRewards.get(id);
            if (created != null) return new RewardModel(id,
                    jsonText(created.get("display_name"), readableId(id)), created);
            return model.rewards().stream().filter(value -> value.id().equals(id)).findFirst().orElse(null);
        }
    }

    private static final class OfferPhasePickerScreen extends EditorScreen {
        private final Screen parent; private final String selected; private final Consumer<String> callback;
        private EditBox search; private String query = ""; private int offset;
        private int visibleRows, totalRows;
        OfferPhasePickerScreen(ClientModel model, Screen parent, String selected, Consumer<String> callback) {
            super("Select World Phase", model); this.parent = parent; this.selected = selected; this.callback = callback;
        }
        @Override protected void init() {
            clearWidgets(); int left = panelLeft(), width = panelWidth(), top = 52, bottom = height - 61;
            search = new EditBox(font, left + 5, top + 18, width - 10, 18, Component.literal("Search phases"));
            search.setHint(Component.literal("Search World Phases...").withStyle(ChatFormatting.DARK_GRAY)); search.setValue(query);
            search.setResponder(value -> { query = value; offset = 0; rebuildWidgets(); }); addRenderableWidget(search);
            setInitialFocus(search); setFocused(search); search.setFocused(true);
            List<StageModel> shown = model.stages().stream().filter(stage -> stage.name().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))).toList();
            int visible = Math.max(1, (bottom - top - 47) / 34); visibleRows = visible; totalRows = shown.size();
            offset = listOffset(offset, shown.size(), visible);
            for (int row = 0; row < Math.min(visible, shown.size() - offset); row++) {
                StageModel stage = shown.get(offset + row);
                int rowWidth = width - 10 - (WorldAwakenedEditorListLayout.needsScrollbar(shown.size(), visible) ? 8 : 0);
                PoolTierRowButton button = new PoolTierRowButton(left + 5, top + 42 + row * 34, rowWidth, 32,
                        stage.name(), unlockSummary(model, stage.id()), stage.id().equals(selected) ? "Selected" : "", stage.id().equals(selected), false,
                        ignored -> { callback.accept(stage.id()); minecraft.setScreen(parent); });
                addRenderableWidget(button);
            }
            if (WorldAwakenedEditorListLayout.needsScrollbar(shown.size(), visible))
                addRenderableWidget(new EditorScrollbar(left + width - 11, top + 42, 6, bottom - top - 47,
                        shown.size(), visible, offset, value -> { offset = value; rebuildWidgets(); }));
            button(left, height - 25, 72, "Cancel", ignored -> minecraft.setScreen(parent), null);
        }
        @Override public void render(GuiGraphics graphics, int mx, int my, float tick) {
            renderBackground(graphics, mx, my, tick); header(graphics, "Select World Phase", "Ascension > Availability");
            panel(graphics, panelLeft(), 52, panelWidth(), height - 83); renderWidgets(graphics, mx, my, tick);
            graphics.drawString(font, "WORLD PHASES", panelLeft() + 6, 58, 0xD8B84A, false);
        }
        @Override public boolean mouseScrolled(double mx, double my, double dx, double dy) {
            int next = listOffset(offset + (dy < 0 ? 1 : -1), totalRows, visibleRows);
            if (next != offset) { offset = next; rebuildWidgets(); return true; }
            return super.mouseScrolled(mx, my, dx, dy);
        }
    }

    private record AscensionChoiceEffect(String id, String label, boolean amount, String defaultAmount, double maximum, String help, boolean live) {
        AscensionChoiceEffect(String id, String label, boolean amount, String defaultAmount, double maximum, String help) {
            this(id, label, amount, defaultAmount, maximum, help, true);
        }
    }
    private static final List<AscensionChoiceEffect> ASCENSION_CHOICE_EFFECTS = List.of(
            new AscensionChoiceEffect("worldawakened:max_health_bonus", "Maximum Health", true, "2", 40, "Adds maximum health in half-heart points (maximum 40)."),
            new AscensionChoiceEffect("worldawakened:armor_bonus", "Armor", true, "1", 20, "Adds permanent armor points (maximum 20)."),
            new AscensionChoiceEffect("worldawakened:armor_toughness_bonus", "Armor Toughness", true, "1", 20, "Adds permanent armor toughness (maximum 20)."),
            new AscensionChoiceEffect("worldawakened:attack_damage_bonus", "Attack Damage", true, "1", 20, "Adds permanent attack damage (maximum 20)."),
            new AscensionChoiceEffect("worldawakened:movement_speed_bonus", "Movement Speed", true, "0.01", 0.5, "Adds to the movement-speed attribute (maximum 0.5)."),
            new AscensionChoiceEffect("worldawakened:knockback_resistance_bonus", "Knockback Resistance", true, "0.08", 1, "Adds permanent knockback resistance (maximum 1)."),
            new AscensionChoiceEffect("worldawakened:luck_bonus", "Luck", true, "0.5", 10, "Adds permanent luck (maximum 10)."),
            new AscensionChoiceEffect("worldawakened:fire_resistance_passive", "Fire Resistance", false, "", 0, "Grants WorldAwakened's persistent fire-resistance passive."),
            new AscensionChoiceEffect("worldawakened:night_vision_passive", "Night Vision", false, "", 0, "Grants WorldAwakened's persistent night-vision passive."),
            new AscensionChoiceEffect("worldawakened:fall_damage_reduction", "Fall Damage Reduction", true, "0.1", 1, "Reduces fall damage.", false),
            new AscensionChoiceEffect("worldawakened:debuff_resistance", "Debuff Resistance", true, "0.1", 1, "Reduces harmful effect duration.", false),
            new AscensionChoiceEffect("worldawakened:damage_type_resistance", "Damage Type Resistance", false, "", 0, "Reduces one chosen damage type.", false),
            new AscensionChoiceEffect("worldawakened:healing_efficiency_bonus", "Healing Efficiency", true, "0.1", 1, "Improves healing received.", false),
            new AscensionChoiceEffect("worldawakened:extra_revival_buffer", "Extra Revival Buffer", true, "1", 20, "Adds a revival safety buffer.", false),
            new AscensionChoiceEffect("worldawakened:step_height_bonus", "Step Height", true, "0.1", 2, "Raises step height.", false),
            new AscensionChoiceEffect("worldawakened:sprint_efficiency", "Sprint Efficiency", true, "0.1", 1, "Improves sprint efficiency.", false),
            new AscensionChoiceEffect("worldawakened:jump_bonus", "Jump Bonus", true, "0.1", 5, "Improves jump ability.", false),
            new AscensionChoiceEffect("worldawakened:water_mobility_bonus", "Water Mobility", true, "0.1", 1, "Improves movement in water.", false),
            new AscensionChoiceEffect("worldawakened:hostile_wall_sense", "Hostile Wall Sense", false, "", 0, "Helps detect hostile mobs through walls.", false),
            new AscensionChoiceEffect("worldawakened:loot_detection", "Loot Detection", false, "", 0, "Helps detect valuable loot.", false),
            new AscensionChoiceEffect("worldawakened:crit_bonus", "Critical Hit Bonus", true, "0.1", 1, "Improves critical hits.", false),
            new AscensionChoiceEffect("worldawakened:attack_reach_bonus", "Attack Reach", true, "0.1", 5, "Increases attack reach.", false),
            new AscensionChoiceEffect("worldawakened:on_hit_effect_passive", "On-Hit Effect", false, "", 0, "Applies a chosen effect when attacking.", false),
            new AscensionChoiceEffect("worldawakened:life_steal_minor", "Minor Life Steal", true, "0.05", 1, "Restores health from attacks.", false),
            new AscensionChoiceEffect("worldawakened:xp_gain_bonus", "Experience Gain", true, "0.1", 10, "Increases experience gained.", false),
            new AscensionChoiceEffect("worldawakened:loot_quality_bonus", "Loot Quality", true, "0.1", 10, "Improves WorldAwakened loot quality.", false),
            new AscensionChoiceEffect("worldawakened:invasion_reward_bonus", "Pressure Event Rewards", true, "0.1", 10, "Improves pressure-event rewards.", false),
            new AscensionChoiceEffect("worldawakened:mutation_resistance_bonus", "Mutation Resistance", true, "0.1", 1, "Reduces mutation pressure.", false),
            new AscensionChoiceEffect("worldawakened:elite_detection", "Elite Detection", false, "", 0, "Helps identify elite enemies.", false));

    private static AscensionChoiceEffect ascensionChoiceEffect(String id) {
        return ASCENSION_CHOICE_EFFECTS.stream().filter(effect -> effect.id().equals(id)).findFirst().orElse(null);
    }

    static int ascensionChoiceEffectCount() {
        return ASCENSION_CHOICE_EFFECTS.size();
    }

    static Set<String> ascensionChoiceEffectIds() {
        Set<String> ids = new LinkedHashSet<>();
        for (AscensionChoiceEffect effect : ASCENSION_CHOICE_EFFECTS) ids.add(effect.id());
        return Set.copyOf(ids);
    }

    static boolean ascensionChoiceEffectHasLiveRuntimeHandler(String id) {
        AscensionChoiceEffect effect = ascensionChoiceEffect(id);
        return effect != null && effect.live() && WorldAwakenedAscensionRewardEffects.hasLiveRuntimeHandler(ResourceLocation.parse(id));
    }

    private static String ascensionChoiceEffectCategory(String id) {
        return switch (id) {
            case "worldawakened:max_health_bonus", "worldawakened:armor_bonus",
                    "worldawakened:armor_toughness_bonus", "worldawakened:attack_damage_bonus",
                    "worldawakened:movement_speed_bonus", "worldawakened:knockback_resistance_bonus",
                    "worldawakened:luck_bonus" -> "STATS";
            case "worldawakened:fire_resistance_passive", "worldawakened:fall_damage_reduction",
                    "worldawakened:debuff_resistance", "worldawakened:damage_type_resistance",
                    "worldawakened:healing_efficiency_bonus", "worldawakened:extra_revival_buffer" -> "SURVIVAL";
            case "worldawakened:step_height_bonus", "worldawakened:sprint_efficiency",
                    "worldawakened:jump_bonus", "worldawakened:water_mobility_bonus" -> "MOBILITY";
            case "worldawakened:crit_bonus", "worldawakened:attack_reach_bonus",
                    "worldawakened:on_hit_effect_passive", "worldawakened:life_steal_minor" -> "COMBAT";
            case "worldawakened:night_vision_passive", "worldawakened:hostile_wall_sense",
                    "worldawakened:loot_detection", "worldawakened:elite_detection" -> "EXPLORATION";
            default -> "PROGRESSION";
        };
    }

    private static String rewardEffectSummary(JsonObject definition) {
        if (!definition.has("components") || !definition.get("components").isJsonArray()) return "No permanent effects";
        List<String> effects = new ArrayList<>();
        for (JsonElement raw : definition.getAsJsonArray("components")) {
            if (!raw.isJsonObject()) continue;
            JsonObject component = raw.getAsJsonObject();
            String id = read(component, "type", "");
            AscensionChoiceEffect effect = ascensionChoiceEffect(id);
            String label = effect == null ? readableId(id) : effect.label();
            if (effect != null && effect.amount() && component.has("parameters")
                    && component.get("parameters").isJsonObject()
                    && component.getAsJsonObject("parameters").has("amount"))
                label = "+" + component.getAsJsonObject("parameters").get("amount").getAsString() + " " + label;
            effects.add(label);
        }
        return effects.isEmpty() ? "No permanent effects" : String.join("  ·  ", effects);
    }

    private static boolean compactRewardEditable(JsonObject definition) {
        if (!definition.has("components") || !definition.get("components").isJsonArray()) return false;
        JsonArray components = definition.getAsJsonArray("components");
        if (components.isEmpty() || components.size() > 3) return false;
        Set<String> seen = new LinkedHashSet<>();
        for (JsonElement raw : components) {
            if (!raw.isJsonObject()) return false;
            String type = read(raw.getAsJsonObject(), "type", "");
            if (ascensionChoiceEffect(type) == null || !seen.add(type)) return false;
        }
        return true;
    }

    private static final class ChoiceEffectDraft {
        int effectIndex;
        String amount;
        ChoiceEffectDraft(int effectIndex, String amount) { this.effectIndex = effectIndex; this.amount = amount; }
    }

    private static final class AscensionChoiceEditorScreen extends EditorScreen {
        private final Screen parent; private final OfferDraft draft; private final RewardModel existing;
        private final List<ChoiceEffectDraft> effects = new ArrayList<>();
        private EditBox name, description;
        private String choiceName = "", choiceDescription = "";
        private boolean enabled = true;
        AscensionChoiceEditorScreen(ClientModel model, Screen parent, OfferDraft draft, RewardModel existing) {
            super(existing == null ? "Create Ascension Choice" : "Edit Ascension Choice", model);
            this.parent = parent; this.draft = draft; this.existing = existing;
            if (existing == null) effects.add(new ChoiceEffectDraft(0, ASCENSION_CHOICE_EFFECTS.getFirst().defaultAmount()));
            else {
                choiceName = existing.name();
                choiceDescription = jsonText(existing.definition().get("description"), "");
                enabled = !existing.definition().has("enabled") || existing.definition().get("enabled").getAsBoolean();
                for (JsonElement raw : existing.definition().getAsJsonArray("components")) {
                    JsonObject component = raw.getAsJsonObject();
                    String type = read(component, "type", "");
                    int index = 0;
                    for (int candidate = 0; candidate < ASCENSION_CHOICE_EFFECTS.size(); candidate++)
                        if (ASCENSION_CHOICE_EFFECTS.get(candidate).id().equals(type)) { index = candidate; break; }
                    AscensionChoiceEffect effect = ASCENSION_CHOICE_EFFECTS.get(index);
                    String amount = effect.defaultAmount();
                    if (component.has("parameters") && component.get("parameters").isJsonObject()
                            && component.getAsJsonObject("parameters").has("amount"))
                        amount = component.getAsJsonObject("parameters").get("amount").getAsString();
                    effects.add(new ChoiceEffectDraft(index, amount));
                }
            }
        }
        @Override protected void init() {
            clearWidgets(); int left = panelLeft(), width = panelWidth(), top = 52;
            int innerX = left + 12, innerW = width - 24, half = (innerW - 6) / 2;
            name = new EditBox(font, innerX, top + 31, half, 18, Component.literal("Choice name"));
            name.setMaxLength(PHASE_NAME_MAX_LENGTH); name.setHint(Component.literal("Choice name").withStyle(ChatFormatting.DARK_GRAY));
            name.setValue(choiceName); name.setResponder(value -> choiceName = value);
            name.setTooltip(Tooltip.create(Component.literal("The player-facing name of this permanent choice.")));
            addRenderableWidget(name);
            description = new EditBox(font, innerX + half + 6, top + 31, innerW - half - 6, 18, Component.literal("Description"));
            description.setMaxLength(PHASE_DESCRIPTION_MAX_LENGTH);
            description.setHint(Component.literal("Describe the permanent benefit").withStyle(ChatFormatting.DARK_GRAY));
            description.setValue(choiceDescription); description.setResponder(value -> choiceDescription = value);
            description.setTooltip(Tooltip.create(Component.literal("The player-facing explanation. Long text is preserved and scrolls in the field.")));
            addRenderableWidget(description);
            button(innerX, top + 55, innerW, "Enabled: " + (enabled ? "On" : "Off"), ignored -> {
                enabled = !enabled; rebuildWidgets();
            }, "Disabled choices remain authored but cannot be offered to players.");
            int rowY = top + 120;
            for (int row = 0; row < effects.size(); row++) {
                ChoiceEffectDraft effectDraft = effects.get(row); final int effectRow = row;
                AscensionChoiceEffect effect = ASCENSION_CHOICE_EFFECTS.get(effectDraft.effectIndex);
                int removeW = effects.size() > 1 ? 24 : 0;
                int amountW = effect.amount() ? Math.max(72, innerW / 4) : 0;
                int effectW = innerW - removeW - amountW - (removeW > 0 ? 5 : 0) - (amountW > 0 ? 5 : 0);
                button(innerX, rowY, effectW, (row + 1) + ". " + effect.label(), ignored ->
                        minecraft.setScreen(new AscensionEffectPickerScreen(model, this, effectDraft.effectIndex,
                                selected -> { effectDraft.effectIndex = selected;
                                    effectDraft.amount = ASCENSION_CHOICE_EFFECTS.get(selected).defaultAmount(); })),
                        ascensionEffectTooltip(effect));
                if (effect.amount()) {
                    EditBox amount = new EditBox(font, innerX + effectW + 5, rowY, amountW, 20, Component.literal("Amount"));
                    amount.setFilter(value -> value.matches("[0-9]{0,4}(\\.[0-9]{0,4})?"));
                    amount.setValue(effectDraft.amount);
                    amount.setResponder(value -> effectDraft.amount = value);
                    amount.setTooltip(Tooltip.create(Component.literal(ascensionEffectTooltip(effect))));
                    addRenderableWidget(amount);
                }
                if (removeW > 0) button(innerX + innerW - removeW, rowY, removeW, "×", ignored -> {
                    effects.remove(effectRow); rebuildWidgets();
                }, "Remove this effect from the choice.");
                rowY += 28;
            }
            Button add = button(innerX, rowY, Math.min(190, innerW), "+ Add Effect (" + effects.size() + "/3)", ignored -> {
                int next = firstUnusedEffect();
                AscensionChoiceEffect effect = ASCENSION_CHOICE_EFFECTS.get(next);
                effects.add(new ChoiceEffectDraft(next, effect.defaultAmount())); rebuildWidgets();
            }, "Combine up to three different permanent effects in this reward choice.");
            add.active = effects.size() < 3;
            button(left, height - 25, 92, "Cancel", ignored -> minecraft.setScreen(parent),
                    "Return without saving this choice.");
            button(left + width - 130, height - 25, 130, "Create Choice", ignored -> create(),
                    "Save this choice to the offer draft. It reaches the server only when the offer is applied.")
                    .setMessage(Component.literal(existing == null ? "Create Choice" : "Save Choice"));
            setInitialFocus(name); setFocused(name); name.setFocused(true);
        }
        private int firstUnusedEffect() {
            for (int candidate = 0; candidate < ASCENSION_CHOICE_EFFECTS.size(); candidate++) {
                int value = candidate;
                if (ASCENSION_CHOICE_EFFECTS.get(candidate).live()
                        && effects.stream().noneMatch(effect -> effect.effectIndex == value)) return candidate;
            }
            return 0;
        }
        private void create() {
            String finalName = name.getValue().trim();
            if (finalName.isBlank()) { showLocalError("Choice name is required."); return; }
            if (effects.isEmpty()) { showLocalError("Add at least one permanent effect."); return; }
            String id = existing == null ? "worldawakened:world/ascension_reward_"
                    + UUID.randomUUID().toString().replace("-", "").substring(0, 8) : existing.id();
            JsonObject reward = existing == null ? new JsonObject() : existing.definition().deepCopy();
            reward.addProperty("schema_version", 1); reward.addProperty("id", id);
            reward.addProperty("display_name", finalName); reward.addProperty("description", description.getValue());
            reward.addProperty("enabled", enabled); JsonArray components = new JsonArray();
            for (ChoiceEffectDraft effectDraft : effects) {
                AscensionChoiceEffect effect = ASCENSION_CHOICE_EFFECTS.get(effectDraft.effectIndex);
                if (!effect.live() || !WorldAwakenedAscensionRewardEffects.hasLiveRuntimeHandler(ResourceLocation.parse(effect.id()))) {
                    showLocalError(effect.label() + " is not available yet because it has no live player runtime handler.");
                    return;
                }
                double numericAmount = 0;
                if (effect.amount()) {
                    try { numericAmount = Double.parseDouble(effectDraft.amount); }
                    catch (NumberFormatException exception) { showLocalError("Enter a valid amount for " + effect.label() + "."); return; }
                    if (!Double.isFinite(numericAmount) || numericAmount <= 0 || numericAmount > effect.maximum()) {
                        showLocalError(effect.label() + " must be greater than 0 and no more than " + effect.maximum() + "."); return;
                    }
                }
                JsonObject component = new JsonObject(); component.addProperty("type", effect.id());
                JsonObject parameters = new JsonObject(); if (effect.amount()) parameters.addProperty("amount", numericAmount);
                component.add("parameters", parameters); components.add(component);
            }
            reward.add("components", components);
            reward.add("tags", new JsonArray()); reward.addProperty("offer_weight", 1D);
            reward.add("requires_conditions", new JsonArray()); reward.add("forbidden_conditions", new JsonArray());
            reward.addProperty("max_rank", 1);
            draft.deletedRewardIds.remove(id); draft.newRewards.put(id, reward); draft.rewardIds.add(id); draft.dirty = true;
            minecraft.setScreen(parent);
        }
        @Override public void render(GuiGraphics graphics, int mx, int my, float tick) {
            String title = existing == null ? "Create Ascension Choice" : "Edit Ascension Choice";
            renderBackground(graphics, mx, my, tick); header(graphics, title, "Ascension > Reward Choices");
            int left = panelLeft(), width = panelWidth(), top = 52;
            panel(graphics, left, top, width, height - top - 31);
            ascensionChoiceSection(graphics, left + 6, top + 6, width - 12, 84, "GENERAL");
            int effectHeight = 58 + effects.size() * 28;
            ascensionChoiceSection(graphics, left + 6, top + 96, width - 12, effectHeight, "PERMANENT EFFECTS");
            graphics.drawString(font, "A choice may combine up to three different live effects.",
                    left + 12, top + 96 + effectHeight + 6, 0xFFBEBEBE);
            renderWidgets(graphics, mx, my, tick);
        }
        private void ascensionChoiceSection(GuiGraphics graphics, int x, int y, int w, int h, String title) {
            graphics.fill(x, y, x + w, y + h, 0x50101010); graphics.fill(x, y, x + w, y + 1, 0xFF666666);
            graphics.fill(x, y + h - 1, x + w, y + h, 0xFF333333); graphics.fill(x, y, x + 1, y + h, 0xFF666666);
            graphics.fill(x + w - 1, y, x + w, y + h, 0xFF333333); graphics.drawString(font, title, x + 6, y + 6, 0xFFFFAA00);
        }
    }

    private static final class AscensionEffectPickerScreen extends EditorScreen {
        private static final int ROW_HEIGHT = 38;
        private final Screen parent; private final int selected; private final Consumer<Integer> callback;
        private final ScrollableRows list = new ScrollableRows();
        private EditBox search;
        private String query = "";
        private List<AscensionPickerEntry> entries = List.of();
        AscensionEffectPickerScreen(ClientModel model, Screen parent, int selected, Consumer<Integer> callback) {
            super("Select Effect", model); this.parent = parent; this.selected = selected; this.callback = callback;
        }
        @Override protected void init() {
            clearWidgets(); int left = panelLeft(), width = panelWidth();
            search = new EditBox(font, left + 6, 69, width - 12, 20, Component.literal("Search permanent effects"));
            search.setHint(Component.literal("Search permanent effects...").withStyle(ChatFormatting.DARK_GRAY));
            search.setValue(query); search.setResponder(value -> { query = value; rebuildWidgets(); });
            addRenderableWidget(search); setInitialFocus(search); setFocused(search); search.setFocused(true);
            List<AscensionChoiceEffect> shown = ASCENSION_CHOICE_EFFECTS.stream().filter(effect ->
                    (effect.label() + " " + effect.help()).toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))).toList();
            entries = new ArrayList<>();
            for (String category : List.of("STATS", "SURVIVAL", "MOBILITY", "COMBAT", "EXPLORATION", "PROGRESSION")) {
                List<AscensionChoiceEffect> categoryEffects = shown.stream()
                        .filter(effect -> category.equals(ascensionChoiceEffectCategory(effect.id())))
                        .toList();
                if (categoryEffects.isEmpty()) continue;
                entries.add(new AscensionPickerEntry(category, null));
                for (AscensionChoiceEffect effect : categoryEffects) entries.add(new AscensionPickerEntry(null, effect));
            }
            list.configure(left + 6, 96, width - 12, height - 151, ROW_HEIGHT, entries.size());
            for (int row = 0; row < Math.min(list.visible, Math.max(0, entries.size() - list.offset)); row++) {
                AscensionPickerEntry pickerEntry = entries.get(list.offset + row);
                if (pickerEntry.effect() == null) continue;
                AscensionChoiceEffect effect = pickerEntry.effect(); int index = ASCENSION_CHOICE_EFFECTS.indexOf(effect);
                SelectionRowButton entry = new SelectionRowButton(list.x, list.y + row * ROW_HEIGHT,
                        list.rowWidth(), ROW_HEIGHT - 2, effect.label(), effect.help(), index == selected, ignored -> {
                    callback.accept(index); minecraft.setScreen(parent);
                }, false);
                entry.active = effect.live();
                entry.setTooltip(Tooltip.create(Component.literal(effect.live()
                        ? ascensionEffectTooltip(effect)
                        : "Not available yet: this reward effect has no live player runtime handler.")));
                addRenderableWidget(entry);
            }
            button(left, height - 25, 72, "Cancel", ignored -> minecraft.setScreen(parent), null);
        }
        @Override public boolean mouseScrolled(double mx, double my, double dx, double dy) {
            if (list.contains(mx, my) && list.scroll(dy)) { rebuildWidgets(); return true; }
            return super.mouseScrolled(mx, my, dx, dy);
        }
        @Override public boolean mouseClicked(double mx, double my, int button) { if (button == 0 && list.beginDrag(mx, my)) { rebuildWidgets(); return true; } return super.mouseClicked(mx, my, button); }
        @Override public boolean mouseDragged(double mx, double my, int button, double dx, double dy) { if (button == 0 && list.dragging) { if (list.drag(my)) rebuildWidgets(); return true; } return super.mouseDragged(mx, my, button, dx, dy); }
        @Override public boolean mouseReleased(double mx, double my, int button) { list.release(); return super.mouseReleased(mx, my, button); }
        @Override public void render(GuiGraphics graphics, int mx, int my, float tick) {
            renderBackground(graphics, mx, my, tick); header(graphics, "Select Effect", "Ascension > Reward Choice");
            panel(graphics, panelLeft(), 39, panelWidth(), height - 84); list.render(graphics);
            for (int row = 0; row < Math.min(list.visible, Math.max(0, entries.size() - list.offset)); row++) {
                AscensionPickerEntry entry = entries.get(list.offset + row);
                if (entry.effect() == null) {
                    int y = list.y + row * ROW_HEIGHT;
                    graphics.fill(list.x, y, list.x + list.rowWidth(), y + ROW_HEIGHT - 2, 0xA0181818);
                    graphics.drawString(font, entry.heading(), list.x + 8, y + 14, 0xD8B84A, false);
                }
            }
            renderWidgets(graphics, mx, my, tick);
            graphics.drawString(font, "PERMANENT EFFECTS", panelLeft() + 6, 53, 0xD8B84A, false);
        }
    }
    private record AscensionPickerEntry(String heading, AscensionChoiceEffect effect) { }

    private static String ascensionEffectTooltip(AscensionChoiceEffect effect) {
        if (!effect.amount()) return effect.help();
        return effect.help() + "\nEnter a value greater than 0 and no more than "
                + formatEditorNumber(effect.maximum()) + ". Default: " + effect.defaultAmount() + ".";
    }

    private static String formatEditorNumber(double value) {
        return value == Math.rint(value) ? Integer.toString((int) value) : Double.toString(value);
    }

    private static final class AscensionRewardSelectorScreen extends EditorScreen {
        private final Screen parent; private final OfferDraft draft;
        private EditBox search; private String query = ""; private int offset, visibleRows, totalRows;
        private String focusedRewardId;
        AscensionRewardSelectorScreen(ClientModel model, Screen parent, OfferDraft draft) {
            super("Select Rewards", model); this.parent = parent; this.draft = draft;
        }
        @Override protected void init() {
            clearWidgets(); int left = panelLeft(), width = panelWidth(), top = 52, bottom = height - 31;
            search = new EditBox(font, left + 5, top + 18, width - 116, 18, Component.literal("Search rewards"));
            search.setHint(Component.literal("Search Ascension rewards...").withStyle(ChatFormatting.DARK_GRAY)); search.setValue(query);
            search.setResponder(value -> { query = value; offset = 0; rebuildWidgets(); }); addRenderableWidget(search);
            setInitialFocus(search); setFocused(search); search.setFocused(true);
            button(left + width - 106, top + 17, 101, "+ Choice", ignored ->
                    minecraft.setScreen(new AscensionChoiceEditorScreen(model, this, draft, null)),
                    "Create a reward choice with up to three permanent effects.");
            String needle = query.toLowerCase(Locale.ROOT);
            LinkedHashMap<String, RewardModel> available = new LinkedHashMap<>();
            model.rewards().forEach(reward -> available.put(reward.id(), reward));
            draft.newRewards.forEach((id, definition) -> available.put(id,
                    new RewardModel(id, jsonText(definition.get("display_name"), readableId(id)), definition)));
            List<RewardModel> shown = available.values().stream()
                    .filter(reward -> !draft.deletedRewardIds.contains(reward.id())
                            || !reward.id().startsWith("worldawakened:world/ascension_reward_"))
                    .filter(reward -> reward.name().toLowerCase(Locale.ROOT).contains(needle)
                            || reward.id().toLowerCase(Locale.ROOT).contains(needle)
                            || rewardEffectSummary(reward.definition()).toLowerCase(Locale.ROOT).contains(needle))
                    .sorted(Comparator.comparing(RewardModel::name)).toList();
            visibleRows = Math.max(1, (bottom - top - 47) / 40); totalRows = shown.size();
            offset = listOffset(offset, totalRows, visibleRows);
            boolean overflow = WorldAwakenedEditorListLayout.needsScrollbar(totalRows, visibleRows);
            int rowWidth = width - 10 - (overflow ? 8 : 0), controlsWidth = 86, controlsRightInset = 10;
            for (int row = 0; row < Math.min(visibleRows, totalRows - offset); row++) {
                RewardModel reward = shown.get(offset + row);
                int rowY = top + 42 + row * 40;
                int rowX = left + 5;
                SelectionRowButton entry = new SelectionRowButton(rowX, rowY, rowWidth, 38,
                        reward.name(), rewardEffectSummary(reward.definition()), draft.rewardIds.contains(reward.id()), ignored -> {
                            focusedRewardId = reward.id();
                            if (draft.rewardIds.contains(reward.id())) draft.rewardIds.remove(reward.id());
                            else if (draft.rewardIds.size() < AscensionOffersScreen.MAX_OFFER_CHOICES) draft.rewardIds.add(reward.id());
                            else { showLocalError("An offer can present at most five choices."); return; }
                            draft.dirty = true; rebuildWidgets();
                        }, true, controlsWidth + controlsRightInset);
                entry.setTooltip(Tooltip.create(Component.literal(reward.id())));
                addRenderableWidget(entry);
                Button edit = button(rowX + rowWidth - controlsWidth - controlsRightInset, rowY + 9, 56, "Edit", ignored ->
                        minecraft.setScreen(new AscensionChoiceEditorScreen(model, this, draft, reward)),
                        compactRewardEditable(reward.definition())
                                ? "Edit this reward. Built-in choices become world overrides when saved."
                                : "This choice uses advanced effects which the compact editor cannot safely edit.");
                edit.active = compactRewardEditable(reward.definition());
                boolean authored = draft.newRewards.containsKey(reward.id()) || containsId(model.authoredRewards(), reward.id());
                Button remove = button(rowX + rowWidth - 24 - controlsRightInset, rowY + 9, 24, "×", ignored -> confirmDelete(reward),
                        authored ? "Delete this world-authored reward, or restore its source definition."
                                : "Bundled and datapack choices cannot be deleted here; remove them from this offer by clicking the row.");
                remove.active = authored;
            }
            if (overflow) addRenderableWidget(new EditorScrollbar(left + width - 11, top + 42, 6,
                    bottom - top - 47, totalRows, visibleRows, offset, value -> { offset = value; rebuildWidgets(); }));
            button(left, height - 25, 72, "Done", ignored -> minecraft.setScreen(parent), null);
        }
        private RewardModel focusedReward() {
            if (focusedRewardId == null) return null;
            JsonObject created = draft.newRewards.get(focusedRewardId);
            if (created != null) return new RewardModel(focusedRewardId,
                    jsonText(created.get("display_name"), readableId(focusedRewardId)), created);
            return model.rewards().stream().filter(reward -> reward.id().equals(focusedRewardId)).findFirst().orElse(null);
        }
        private void confirmDelete(RewardModel reward) {
            boolean worldOnly = reward.id().startsWith("worldawakened:world/ascension_reward_");
            minecraft.setScreen(new ConfirmActionScreen(model,
                    worldOnly ? "Delete this Ascension choice?" : "Restore this Ascension choice's source?",
                    worldOnly ? "This removes the world-authored reward and removes it from this offer. This cannot be undone after Apply."
                            : "This discards the edited world override and restores the bundled or datapack reward after Apply.",
                    worldOnly ? "Delete" : "Restore", () -> {
                        draft.newRewards.remove(reward.id()); draft.deletedRewardIds.add(reward.id());
                        if (worldOnly) draft.rewardIds.remove(reward.id());
                        draft.dirty = true; minecraft.setScreen(new AscensionRewardSelectorScreen(model, parent, draft));
                    }, () -> minecraft.setScreen(this)));
        }
        @Override public boolean mouseScrolled(double mx, double my, double dx, double dy) {
            int next = listOffset(offset + (dy < 0 ? 1 : -1), totalRows, visibleRows);
            if (next != offset) { offset = next; rebuildWidgets(); return true; }
            return super.mouseScrolled(mx, my, dx, dy);
        }
        @Override public void render(GuiGraphics graphics, int mx, int my, float tick) {
            renderBackground(graphics, mx, my, tick); header(graphics, "Select Rewards", "Ascension > Reward Choices");
            int panelBottom = height - 61;
            panel(graphics, panelLeft(), 52, panelWidth(), panelBottom - 52);
            graphics.drawString(font, "ASCENSION REWARDS", panelLeft() + 6, 58, 0xD8B84A, false);
            graphics.drawString(font, draft.rewardIds.size() + " rewards selected", panelLeft(), panelBottom + 7, 0xFFBEBEBE);
            renderWidgets(graphics, mx, my, tick);
        }
    }

    private static final class MutationPoolsScreen extends EditorScreen {
        private static final int CONTENT_TOP = 52;
        private static final int POOL_ROW_HEIGHT = 34;
        private static final int CATEGORY_ROW_HEIGHT = 24;
        private static final int MUTATOR_ROW_HEIGHT = 30;
        private static final int COMBINATION_ROW_HEIGHT = 38;
        private static final int DETAIL_HEADER_HEIGHT = 67;
        private static final int SECTION_GAP = 6;
        private static final int GENERAL_SECTION_HEIGHT = 64;
        private static final int ELIGIBILITY_SECTION_HEIGHT = 62;
        private PoolDraft draft;
        private EditBox search;
        private int offset;
        private boolean compactDetail;
        private String query = "";
        private String selectedPhaseId;
        private String selectedDimensionId;
        private WorldAwakenedPhaseMutationIndex.Index phaseIndex;
        private int mutatorOffset;
        private int poolViewportX, poolViewportY, poolViewportWidth, poolViewportHeight;
        private int poolVisibleRows, poolTotalRows;
        private int mutatorViewportX, mutatorViewportY, mutatorViewportWidth, mutatorViewportHeight;
        private int mutatorVisibleRows, mutatorTotalRows;
        private int mutatorRowWidth, mutatorLabelWidth;
        private int combinationViewportX, combinationViewportY, combinationViewportWidth, combinationViewportHeight;
        private int combinationVisibleRows, combinationTotalRows, combinationOffset;
        private int configStatusX, configStatusY, configStatusWidth;
        private boolean draggingPoolScrollbar, draggingMutatorScrollbar, draggingCombinationScrollbar;
        private double scrollbarGrabOffset;
        private boolean eventMode;
        private boolean phasesExpanded = true;
        private boolean eventsExpanded = true;
        MutationPoolsScreen(ClientModel model, PoolModel selected) {
            this(model, selected, false);
        }
        MutationPoolsScreen(ClientModel model, PoolModel selected, boolean eventMode) {
            super("Mob Mutations", model);
            this.eventMode = eventMode;
            if (selected != null) draft = new PoolDraft(selected);
        }
        @Override protected void init() {
            clearWidgets();
            configStatusX = configStatusY = configStatusWidth = 0;
            boolean compact = width < 620;
            int left = panelLeft();
            int w = panelWidth();
            int browserW = compact ? w : Math.min(290, Math.max(230, w * 36 / 100));
            int contentBottom = height - 31;
            phaseIndex = phaseIndex(model);
            resolvePhaseSelection();
            if (!compact || !compactDetail) {
                search = new EditBox(font, left + 5, CONTENT_TOP + 5, browserW - 10, 18, Component.literal("Search phases and events"));
                search.setHint(Component.literal("Search phases and pressure events...").withStyle(ChatFormatting.DARK_GRAY));
                search.setValue(query);
                search.setResponder(value -> { query = value; offset = 0; rebuildWidgets(); });
                addRenderableWidget(search);
                setInitialFocus(search); setFocused(search); search.setFocused(true);
                int filterY = CONTENT_TOP + 27;
                List<MutationBrowserRow> shown = browserRows();
                poolViewportX = left + 5;
                poolViewportY = filterY;
                poolViewportWidth = browserW - 10;
                poolViewportHeight = Math.max(POOL_ROW_HEIGHT, contentBottom - poolViewportY - 5);
                poolVisibleRows = WorldAwakenedEditorListLayout.visibleRows(poolViewportHeight, POOL_ROW_HEIGHT);
                poolTotalRows = shown.size();
                offset = clampOffset(offset, poolTotalRows, poolVisibleRows);
                boolean poolOverflow = WorldAwakenedEditorListLayout.needsScrollbar(poolTotalRows, poolVisibleRows);
                int rowWidth = poolViewportWidth - (poolOverflow ? 8 : 0);
                int rowY = poolViewportY;
                for (int row = 0; row < Math.min(poolVisibleRows, Math.max(0, shown.size() - offset)); row++) {
                    MutationBrowserRow item = shown.get(offset + row);
                    if (item.kind() == MutationBrowserKind.CATEGORY) {
                        boolean expanded = item.category().equals("phases") ? phasesExpanded : eventsExpanded;
                        button(poolViewportX, rowY, rowWidth, (expanded ? "▼ " : "▶ ") + item.primary(), ignored -> {
                            if (item.category().equals("phases")) phasesExpanded = !phasesExpanded;
                            else eventsExpanded = !eventsExpanded;
                            offset = 0; rebuildWidgets();
                        }, item.secondary());
                    } else {
                        boolean selected = item.kind() == MutationBrowserKind.PHASE
                                ? !eventMode && item.id().equals(selectedPhaseId)
                                : eventMode && draft != null && item.id().equals(draft.id);
                        PoolTierRowButton entry = new PoolTierRowButton(poolViewportX, rowY, rowWidth,
                                POOL_ROW_HEIGHT - 2, item.primary(), item.secondary(), item.state(), selected,
                                selected ? () -> draft != null && draft.dirty : () -> false, ignored -> {
                                    if (item.kind() == MutationBrowserKind.PHASE) selectPhase(item.id(), compact);
                                    else selectEvent(item.id(), compact);
                                });
                        entry.setTooltip(Tooltip.create(Component.literal(item.tooltip())));
                        addRenderableWidget(entry);
                    }
                    rowY += item.kind() == MutationBrowserKind.CATEGORY ? CATEGORY_ROW_HEIGHT : POOL_ROW_HEIGHT;
                }
            }
            if (!compact && draft != null) addDetails(left + browserW + 10, CONTENT_TOP, w - browserW - 10);
            if (compact && compactDetail && draft != null) addDetails(left, CONTENT_TOP, w);
            if (!compact && draft == null) addEmptyState(left + browserW + 10, CONTENT_TOP, w - browserW - 10);
            button(left, height - 25, 72, "Back", ignored -> back(), null);
        }
        private void selectPhase(String stageId, boolean compact) {
            Runnable change = () -> { eventMode = false; selectedPhaseId = stageId; selectedDimensionId = null; draft = null;
                compactDetail = compact; mutatorOffset = combinationOffset = 0; rebuildWidgets(); };
            if (draft != null && draft.dirty && (eventMode || !stageId.equals(selectedPhaseId)))
                minecraft.setScreen(new LeaveDraftScreen(this, change));
            else change.run();
        }
        private void selectEvent(String poolId, boolean compact) {
            PoolModel pool = model.pool(poolId); if (pool == null) return;
            Runnable change = () -> { eventMode = true; selectedPhaseId = null; selectedDimensionId = null;
                draft = new PoolDraft(pool); compactDetail = compact; mutatorOffset = combinationOffset = 0; rebuildWidgets(); };
            if (draft != null && draft.dirty && (!eventMode || !draft.id.equals(poolId)))
                minecraft.setScreen(new LeaveDraftScreen(this, change));
            else change.run();
        }
        private void chooseDimension(String dimension) {
            if (dimension == null || dimension.equals(selectedDimensionId)) { minecraft.setScreen(this); return; }
            Runnable change = () -> { selectedDimensionId = dimension; draft = null; resolvePhaseSelection();
                mutatorOffset = combinationOffset = 0; minecraft.setScreen(this); };
            if (draft != null && draft.dirty) minecraft.setScreen(new LeaveDraftScreen(this, change)); else change.run();
        }
        private void chooseConfiguration(String poolId) {
            PoolModel pool = model.pool(poolId);
            if (pool == null || draft != null && draft.id.equals(poolId)) { minecraft.setScreen(this); return; }
            Runnable change = () -> { draft = new PoolDraft(pool); mutatorOffset = combinationOffset = 0;
                minecraft.setScreen(this); };
            if (draft != null && draft.dirty) minecraft.setScreen(new LeaveDraftScreen(this, change)); else change.run();
        }
        private void acceptPickerSelection(String poolId, Boolean enabledOverride) {
            PoolModel pool = model.pool(poolId);
            if (pool == null) { minecraft.setScreen(this); return; }
            Runnable change = () -> {
                if (draft == null || !draft.id.equals(poolId)) draft = new PoolDraft(pool);
                if (enabledOverride != null && draft.enabled != enabledOverride) {
                    draft.enabled = enabledOverride;
                    draft.dirty = true;
                }
                mutatorOffset = combinationOffset = 0;
                minecraft.setScreen(this);
            };
            if (draft != null && draft.dirty && !draft.id.equals(poolId))
                minecraft.setScreen(new LeaveDraftScreen(this, change));
            else change.run();
        }
        private void renameFromPicker(WorldAwakenedPhaseMutationIndex.Configuration configuration) {
            PoolModel pool = model.pool(configuration.poolId());
            if (pool == null) { minecraft.setScreen(this); return; }
            Runnable change = () -> {
                if (draft == null || !draft.id.equals(pool.id())) draft = new PoolDraft(pool);
                minecraft.setScreen(new PhaseConfigurationNameScreen(model, this, configuration.stageId(),
                        configuration.dimensionId(), configuration));
            };
            if (draft != null && draft.dirty && !draft.id.equals(pool.id()))
                minecraft.setScreen(new LeaveDraftScreen(this, change));
            else change.run();
        }
        private void createFromPicker() {
            minecraft.setScreen(new PhaseConfigurationNameScreen(model, this, selectedPhaseId,
                    selectedDimensionId, null));
        }
        private void removeFromPicker(WorldAwakenedPhaseMutationIndex.Configuration configuration) {
            if (configuration == null) { minecraft.setScreen(this); return; }
            Runnable confirm = () -> minecraft.setScreen(new ConfirmActionScreen(model,
                    "Remove " + configuration.name() + "?", sourceBacked(model.pool(configuration.poolId()))
                            ? "The phase mapping is deactivated; its datapack source remains available to restore."
                            : "This deletes the world-authored Mob Config. No built-in source exists to restore.",
                    "Remove",
                    () -> removeConfiguration(configuration), () -> minecraft.setScreen(this)));
            if (draft != null && draft.dirty && !draft.id.equals(configuration.poolId()))
                minecraft.setScreen(new LeaveDraftScreen(this, confirm));
            else confirm.run();
        }
        private void restoreFromPicker(WorldAwakenedPhaseMutationIndex.Configuration configuration) {
            if (configuration == null) { minecraft.setScreen(this); return; }
            minecraft.setScreen(new ConfirmActionScreen(model,
                    "Restore " + configuration.name() + "?",
                    "The source definition will be reactivated without changing unrelated custom Mob Configs.",
                    "Restore",
                    () -> restoreConfiguration(configuration), () -> minecraft.setScreen(this)));
        }
        private List<StageModel> phaseRows() {
            String lowered = query.toLowerCase(Locale.ROOT);
            return model.stages().stream().filter(stage -> stage.name().toLowerCase(Locale.ROOT).contains(lowered)
                    || stage.id().toLowerCase(Locale.ROOT).contains(lowered))
                    .sorted(Comparator.comparingInt((StageModel stage) -> existingStageSortIndex(stage.definition()))
                            .thenComparing(StageModel::id)).toList();
        }
        private List<MutationBrowserRow> browserRows() {
            String lowered = query.toLowerCase(Locale.ROOT); List<MutationBrowserRow> rows = new ArrayList<>();
            List<StageModel> phases = phaseRows();
            List<PoolModel> events = phaseIndex.eventPoolIds().stream().map(model::pool).filter(java.util.Objects::nonNull)
                    .filter(pool -> pressureEventName(pool).toLowerCase(Locale.ROOT).contains(lowered)
                            || pool.id().toLowerCase(Locale.ROOT).contains(lowered)).toList();
            if (!phases.isEmpty() || lowered.isBlank() || "world phases".contains(lowered)) {
                rows.add(MutationBrowserRow.category("phases", "WORLD PHASES (" + model.stages().size() + ")",
                        "Shared progression phases in authored order."));
                if (phasesExpanded || !lowered.isBlank()) for (StageModel phase : phases)
                    rows.add(new MutationBrowserRow(MutationBrowserKind.PHASE, "phases", phase.id(), phase.name(),
                            phaseSummary(phase.id()), phaseState(phase.id()), phase.description().isBlank()
                                    ? "Configure mob mutations for this world phase." : phase.description()));
            }
            if (!events.isEmpty() || lowered.isBlank() || "pressure events".contains(lowered)) {
                rows.add(MutationBrowserRow.category("events", "PRESSURE EVENTS (" + phaseIndex.eventPoolIds().size() + ")",
                        "Event-only mutation configurations; scheduler settings are edited elsewhere."));
                if (eventsExpanded || !lowered.isBlank()) for (PoolModel pool : events) {
                    boolean selectedEventDraft = eventMode && draft != null && pool.id().equals(draft.id);
                    boolean enabled = selectedEventDraft ? draft.enabled
                            : !pool.definition().has("enabled") || pool.definition().get("enabled").getAsBoolean();
                    rows.add(new MutationBrowserRow(MutationBrowserKind.EVENT, "events", pool.id(), pressureEventName(pool),
                            String.format(Locale.ROOT, "%.0f%% mutation chance",
                                    (selectedEventDraft ? draft.chance : poolChance(pool.definition())) * 100.0D),
                            enabled ? "On" : "Off", "Pressure-event-only mutation settings. " + pool.origin()));
                }
            }
            return rows;
        }
        private String phaseSummary(String stageId) {
            List<WorldAwakenedPhaseMutationIndex.Configuration> configs = phaseIndex.configurations(stageId).stream()
                    .filter(WorldAwakenedPhaseMutationIndex.Configuration::active)
                    .filter(config -> !config.legacyReview()).toList();
            long dimensions = configs.stream().map(WorldAwakenedPhaseMutationIndex.Configuration::dimensionId)
                    .filter(value -> !value.isBlank()).distinct().count();
            if (configs.isEmpty()) return "No mob mutations configured";
            String dimensionSummary = dimensions == 1 ? readableId(configs.getFirst().dimensionId())
                    : dimensions + " dimensions";
            return dimensionSummary + "  ·  " + configs.size()
                    + (configs.size() == 1 ? " Mob Config" : " Mob Configs");
        }
        private String phaseState(String stageId) {
            List<WorldAwakenedPhaseMutationIndex.Configuration> configs = phaseIndex.configurations(stageId).stream()
                    .filter(WorldAwakenedPhaseMutationIndex.Configuration::active)
                    .filter(config -> !config.legacyReview()).toList();
            if (configs.isEmpty()) return "";
            long enabled = configs.stream().filter(config -> {
                if (!eventMode && draft != null && config.poolId().equals(draft.id)) return draft.enabled;
                PoolModel pool = model.pool(config.poolId());
                return pool != null && (!pool.definition().has("enabled") || pool.definition().get("enabled").getAsBoolean());
            }).count();
            return enabled == configs.size() ? "All active" : enabled == 0 ? "All disabled" : "Mixed";
        }
        private String pressureEventName(PoolModel pool) {
            String value = readableId(pool.id());
            return value.startsWith("Invasion ") ? value.substring("Invasion ".length()) : value;
        }
        private String compactDimensions(Set<String> values) {
            if (values.isEmpty()) return "Any";
            if (values.size() == 1) return readableId(values.iterator().next());
            return values.size() + " selected";
        }
        private boolean sourceBacked(PoolModel pool) {
            return pool != null && !"World authored".equals(pool.origin());
        }
        private String eligibleMobSummary() {
            if (draft.entities.isEmpty()) return "All eligible living mobs";
            List<String> names = draft.entities.stream().map(id -> {
                EntityModel entity = model.entities().stream().filter(candidate -> candidate.id().equals(id))
                        .findFirst().orElse(null);
                return entity == null ? readableId(id) : entity.name();
            }).toList();
            if (names.size() <= 3) return String.join(", ", names);
            return String.join(", ", names.subList(0, 3)) + " and " + (names.size() - 3) + " more";
        }
        private WorldAwakenedPhaseMutationIndex.Configuration restorableConfiguration() {
            if (eventMode || selectedPhaseId == null) return null;
            return phaseIndex.configurations(selectedPhaseId).stream()
                    .filter(config -> !config.active() && !config.legacyReview())
                    .filter(config -> selectedDimensionId == null || config.dimensionId().equals(selectedDimensionId))
                    .filter(config -> sourceBacked(model.pool(config.poolId()))).findFirst().orElse(null);
        }
        private void resolvePhaseSelection() {
            if (eventMode) return;
            if (selectedPhaseId == null && draft != null) {
                outer: for (StageModel stage : model.stages()) {
                    for (WorldAwakenedPhaseMutationIndex.Configuration config : phaseIndex.configurations(stage.id())) {
                        if (config.poolId().equals(draft.id)) { selectedPhaseId = stage.id();
                            selectedDimensionId = config.dimensionId(); break outer; }
                    }
                }
            }
            if (selectedPhaseId == null && !model.stages().isEmpty()) selectedPhaseId = model.stages().getFirst().id();
            List<WorldAwakenedPhaseMutationIndex.Configuration> navigable = phaseIndex.configurations(selectedPhaseId).stream()
                    .filter(config -> !config.legacyReview())
                    .filter(config -> config.active() || sourceBacked(model.pool(config.poolId()))).toList();
            if (selectedDimensionId == null || navigable.stream().noneMatch(config -> config.dimensionId().equals(selectedDimensionId)))
                selectedDimensionId = navigable.isEmpty() ? null : navigable.getFirst().dimensionId();
            List<WorldAwakenedPhaseMutationIndex.Configuration> dimensionConfigs = navigable.stream()
                    .filter(WorldAwakenedPhaseMutationIndex.Configuration::active)
                    .filter(config -> config.dimensionId().equals(selectedDimensionId)).toList();
            if (draft == null || dimensionConfigs.stream().noneMatch(config -> config.poolId().equals(draft.id))) {
                WorldAwakenedPhaseMutationIndex.Configuration chosen = dimensionConfigs.stream()
                        .filter(WorldAwakenedPhaseMutationIndex.Configuration::defaultConfiguration).findFirst()
                        .orElse(dimensionConfigs.isEmpty() ? null : dimensionConfigs.getFirst());
                PoolModel pool = chosen == null ? null : model.pool(chosen.poolId());
                draft = pool == null ? null : new PoolDraft(pool);
            }
        }
        private void addEmptyState(int x, int y, int w) {
            List<WorldAwakenedPhaseMutationIndex.Configuration> restorable = selectedPhaseId == null ? List.of()
                    : phaseIndex.configurations(selectedPhaseId).stream()
                    .filter(config -> !config.active() && !config.legacyReview())
                    .filter(config -> selectedDimensionId == null || config.dimensionId().equals(selectedDimensionId))
                    .filter(config -> sourceBacked(model.pool(config.poolId()))).toList();
            int buttonY = y + 58;
            if (!restorable.isEmpty()) {
                button(x + 14, buttonY, w - 28, "Restore Built-in Mob Settings", ignored ->
                                minecraft.setScreen(new ConfirmActionScreen(model,
                                        "Restore " + restorable.getFirst().name() + "?",
                                        "The source definition will be reactivated without changing unrelated custom Mob Configs.",
                                        "Restore",
                                        () -> restoreConfiguration(restorable.getFirst()), () -> minecraft.setScreen(this))),
                        "Restore the source-backed configuration previously removed from this world phase.");
                buttonY += 25;
            }
            button(x + 14, buttonY, w - 28, "Add Mob Settings", ignored ->
                            minecraft.setScreen(new AddPhaseDimensionScreen(model, this)),
                    "Choose a dimension and create this phase's first Default Mobs configuration.");
        }
        private void addDetails(int x, int y, int w) {
            int sectionX = x + 5;
            int sectionW = w - 10;
            int innerX = sectionX + 6;
            int innerW = sectionW - 12;
            int headerX = sectionX;
            int headerW = sectionW;
            int headerY = y + 38;
            int statusWidth = 56;
            int selectorWidth = Math.max(130, headerW - statusWidth - 5);
            int dimensionWidth = Math.max(70, Math.min(selectorWidth - 65, selectorWidth * 42 / 100));
            int configWidth = Math.max(60, selectorWidth - dimensionWidth - 5);
            int third = (innerW - 8) / 3;
            int generalSectionY = y + DETAIL_HEADER_HEIGHT;
            int generalSectionHeight = generalSectionHeight(innerW);
            int generalControlsY = generalSectionY + 18;
            if (eventMode) {
                button(headerX, headerY, dimensionWidth, "Dimensions: " + compactDimensions(draft.dimensions), ignored ->
                        minecraft.setScreen(new DimensionSelectorScreen(model, this, draft)),
                        "Edit the dimensions eligible for this pressure-event mutation configuration.");
                button(headerX + dimensionWidth + 5, headerY, configWidth,
                        "Mob Config: " + pressureEventName(draft.source), ignored ->
                                minecraft.setScreen(new MobConfigPickerScreen(model, this, draft.source)),
                        "Select this event configuration or change its enabled state.");
                configStatusX = headerX + headerW - statusWidth;
                configStatusY = headerY;
                configStatusWidth = statusWidth;
            } else {
            List<String> phaseDimensions = phaseIndex.configurations(selectedPhaseId).stream()
                    .filter(config -> !config.legacyReview())
                    .filter(config -> config.active() || sourceBacked(model.pool(config.poolId())))
                    .map(WorldAwakenedPhaseMutationIndex.Configuration::dimensionId).filter(value -> !value.isBlank())
                    .distinct().toList();
            List<WorldAwakenedPhaseMutationIndex.Configuration> dimensionChoices = phaseIndex.configurations(selectedPhaseId).stream()
                    .filter(config -> !config.legacyReview())
                    .filter(config -> config.dimensionId().equals(selectedDimensionId))
                    .filter(config -> config.active() || sourceBacked(model.pool(config.poolId()))).toList();
            List<WorldAwakenedPhaseMutationIndex.Configuration> dimensionConfigs = dimensionChoices.stream()
                    .filter(WorldAwakenedPhaseMutationIndex.Configuration::active).toList();
            WorldAwakenedPhaseMutationIndex.Configuration currentConfig = dimensionConfigs.stream()
                    .filter(config -> config.poolId().equals(draft.id)).findFirst().orElse(null);
            String configurationName = currentConfig == null ? draft.name : currentConfig.name();
            button(headerX, headerY, dimensionWidth,
                    "Dimension: " + readableId(selectedDimensionId == null ? "None" : selectedDimensionId), ignored ->
                            minecraft.setScreen(new PhaseDimensionPickerScreen(model, this, phaseDimensions)),
                    "Choose which configured dimension to edit for this World Phase.");
            button(headerX + dimensionWidth + 5, headerY, configWidth, "Mob Config: " + configurationName, ignored ->
                    minecraft.setScreen(new MobConfigPickerScreen(model, this, dimensionChoices)),
                    "Choose or manage a named Mob Config for this dimension. Specialised configs take precedence over Default Mobs.");
            configStatusX = headerX + headerW - statusWidth;
            configStatusY = headerY;
            configStatusWidth = statusWidth;
            }
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
            button(innerX, eligibilityControlsY, Math.min(180, innerW),
                    "Edit Eligible Mobs (" + draft.entities.size() + ")", ignored ->
                    minecraft.setScreen(new EntitySelectorScreen(model, this, draft)),
                    "Choose which mobs can receive mutations. Other authored conditions remain preserved.");

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
            boolean narrowFooter = w < 430;
            int resetWidth = narrowFooter ? Math.max(70, w * 26 / 100) : 126;
            int applyWidth = narrowFooter ? Math.max(48, w * 18 / 100) : 72;
            int restoreWidth = narrowFooter ? Math.max(70, w * 26 / 100) : 112;
            int discardWidth = narrowFooter ? Math.max(1, w - resetWidth - applyWidth - restoreWidth - 12) : 104;
            int applyX = narrowFooter ? x + resetWidth + 4 : x + Math.max(0, w - 296);
            int restoreX = narrowFooter ? applyX + applyWidth + 4 : x + Math.max(76, w - 220);
            int discardX = narrowFooter ? restoreX + restoreWidth + 4 : x + Math.max(192, w - 104);
            Button apply = button(applyX, footer, applyWidth, "Apply", ignored -> apply(), "Validate, persist and publish this pool override.");
            apply.active = model.editable() && draft.dirty;
            WorldAwakenedPhaseMutationIndex.Configuration removedSource = restorableConfiguration();
            boolean canRestoreOverride = sourceBacked(draft.source) && draft.source.overridden();
            String restoreLabel = removedSource != null || canRestoreOverride ? "Restore Source"
                    : !sourceBacked(draft.source) ? "Custom Config"
                    : draft.source.origin().contains("Bundled") ? "Using Built-in" : "Using Source";
            Button revert = button(restoreX, footer, restoreWidth, restoreLabel, ignored -> {
                if (removedSource != null) minecraft.setScreen(new ConfirmActionScreen(model,
                        "Restore " + removedSource.name() + "?",
                        "The source definition will be reactivated without changing unrelated custom Mob Configs.",
                        "Restore",
                        () -> restoreConfiguration(removedSource), () -> minecraft.setScreen(this)));
                else if (canRestoreOverride) minecraft.setScreen(new ConfirmActionScreen(model, "Restore Built-in settings?",
                        "This removes the selected world override and reveals its datapack source definition.",
                        "Restore",
                        this::revert, () -> minecraft.setScreen(this)));
            }, removedSource != null ? "Restore a source-backed Mob Config removed from this dimension."
                    : canRestoreOverride ? "Remove this world override and restore its source definition."
                    : sourceBacked(draft.source)
                            ? "This Mob Config already matches its bundled or datapack source."
                            : "This custom Mob Config has no bundled or datapack source to restore.");
            revert.active = model.editable() && (canRestoreOverride || removedSource != null);
            Button discard = button(discardX, footer, discardWidth, "Discard Changes", ignored -> discardDraft(),
                    "Discard unsaved changes to this Mob Config without leaving the editor.");
            discard.active = draft.dirty;
            Button resetAll = button(x, footer, resetWidth, "Reset All Defaults", ignored -> confirmResetAll(),
                    "Restore bundled Standard and every global editor setting to its built-in default.");
            resetAll.active = model.editable();
        }
        private void confirmResetAll() {
            minecraft.setScreen(new ConfirmActionScreen(model,
                    "Reset all WorldAwakened settings?",
                    "This clears every world-authored profile setting and override, restores bundled Standard and all global settings to their built-in defaults, and cannot be undone. External datapack files and earned progression are not changed.",
                    "Reset All",
                    () -> {
                        reopenTracks = true;
                        PacketDistributor.sendToServer(new WorldAwakenedProfileOperationPayload(model.settingsRevision(),
                                model.profileRevision(), "reset_defaults"));
                    }, () -> minecraft.setScreen(this)));
        }
        private void discardDraft() {
            if (draft == null) return;
            String poolId = draft.id;
            PoolModel current = model.pool(poolId);
            if (current != null) draft = new PoolDraft(current);
            mutatorOffset = combinationOffset = 0;
            rebuildWidgets();
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
        private void saveConfigurationName(WorldAwakenedPhaseMutationIndex.Configuration existing, String name) {
            JsonObject phaseTrack = phaseTrack(model);
            JsonArray pools = model.authoredPools().deepCopy();
            String poolId;
            boolean defaultConfiguration;
            if (existing == null) {
                String base = "worldawakened:world/phases/" + slug(readableId(selectedPhaseId)) + "/"
                        + slug(readableId(selectedDimensionId)) + "/" + slug(name);
                poolId = uniquePoolId(model, base);
                JsonObject pool = defaultPool(poolId, phaseTrack, null);
                pool.addProperty("id", poolId);
                pool.addProperty("enabled", false);
                pools = replaceById(pools, pool);
                defaultConfiguration = false;
            } else {
                poolId = existing.poolId();
                defaultConfiguration = existing.defaultConfiguration();
            }
            upsertPhaseTier(phaseTrack, selectedPhaseId, selectedDimensionId, name, poolId,
                    defaultConfiguration, true);
            sendTracks(model, replaceById(model.tracks(), phaseTrack), pools, poolId);
        }
        private void removeConfiguration(WorldAwakenedPhaseMutationIndex.Configuration configuration) {
            JsonObject phaseTrack = phaseTrack(model);
            PoolModel pool = model.pool(configuration.poolId());
            JsonArray pools = model.authoredPools();
            if (sourceBacked(pool)) {
                upsertPhaseTier(phaseTrack, configuration.stageId(), configuration.dimensionId(), configuration.name(),
                        configuration.poolId(), configuration.defaultConfiguration(), false);
            } else {
                JsonArray kept = new JsonArray();
                for (JsonElement tierValue : WorldAwakenedEditorTrackJson.tiers(phaseTrack)) {
                    JsonObject tier = tierValue.getAsJsonObject();
                    if (!configuration.poolId().equals(read(tier, "pool_id", ""))) kept.add(tier.deepCopy());
                }
                phaseTrack.add("tiers", kept);
                pools = removeById(pools, configuration.poolId());
            }
            sendTracks(model, replaceById(model.tracks(), phaseTrack), pools, null);
        }
        private void restoreConfiguration(WorldAwakenedPhaseMutationIndex.Configuration configuration) {
            JsonObject phaseTrack = phaseTrack(model);
            upsertPhaseTier(phaseTrack, configuration.stageId(), configuration.dimensionId(), configuration.name(),
                    configuration.poolId(), configuration.defaultConfiguration(), true);
            PoolModel pool = model.pool(configuration.poolId());
            JsonArray pools = pool != null && pool.overridden()
                    ? removeById(model.authoredPools(), configuration.poolId()) : model.authoredPools();
            sendTracks(model, replaceById(model.tracks(), phaseTrack), pools, configuration.poolId());
        }
        private void addPhaseDimension(String dimensionId) {
            Set<String> dimensions = phaseIndex.configurations(selectedPhaseId).stream()
                    .filter(WorldAwakenedPhaseMutationIndex.Configuration::active)
                    .filter(config -> !config.legacyReview())
                    .map(WorldAwakenedPhaseMutationIndex.Configuration::dimensionId)
                    .filter(value -> !value.isBlank()).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            dimensions.add(dimensionId);
            PhaseAuthoring authoring = synchronizePhaseDimensions(model, selectedPhaseId, dimensions);
            String poolId = null;
            for (JsonElement trackValue : authoring.tracks()) {
                JsonObject candidate = trackValue.getAsJsonObject();
                if (!WorldAwakenedPhaseMutationIndex.PHASE_TRACK_ID.equals(read(candidate, "id", ""))) continue;
                for (JsonElement tierValue : WorldAwakenedEditorTrackJson.tiers(candidate)) {
                    JsonObject tier = tierValue.getAsJsonObject();
                    if (selectedPhaseId.equals(read(tier, "stage_id", ""))
                            && dimensionId.equals(read(tier, "dimension", ""))
                            && (!tier.has("default_configuration") || tier.get("default_configuration").getAsBoolean())
                            && (!tier.has("active") || tier.get("active").getAsBoolean())) {
                        poolId = read(tier, "pool_id", null); break;
                    }
                }
            }
            sendTracks(model, authoring.tracks(), authoring.pools(), poolId);
        }
        private void apply() {
            if (!draft.newMutators.isEmpty() || !draft.deletedMutatorIds.isEmpty()) {
                JsonArray mutators = model.authoredMutators().deepCopy();
                for (String id : draft.deletedMutatorIds) mutators = removeById(mutators, id);
                for (JsonObject mutator : draft.newMutators.values()) mutators = replaceById(mutators, mutator);
                sendPoolAndMutatorAuthoring(model, replaceById(model.authoredPools(), draft.json()), mutators, draft.id);
                return;
            }
            reopenPoolId = draft.id;
            reopenEventPool = eventMode;
            PacketDistributor.sendToServer(new WorldAwakenedApplyMutationPoolPayload(model.profileRevision(), draft.json().toString(), false));
        }
        private void revert() {
            reopenPoolId = draft.id;
            reopenEventPool = eventMode;
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
            if (!compact || !compactDetail) panel(graphics, left, CONTENT_TOP, browserW, contentBottom - CONTENT_TOP);
            if (!compact && draft == null) {
                int x = left + browserW + 10, dw = w - browserW - 10;
                panel(graphics, x, CONTENT_TOP, dw, contentBottom - CONTENT_TOP);
                String phase = selectedPhaseId == null ? "this phase" : stageName(model, selectedPhaseId);
                graphics.drawCenteredString(font, "No mob mutations configured for " + phase, x + dw / 2, 82, 0xFFBEBEBE);
                graphics.drawCenteredString(font, "Add settings or restore a removed built-in configuration.",
                        x + dw / 2, 96, 0xFF888888);
            }
            if (draft != null && (!compact || compactDetail)) {
                int x = compact ? left : left + browserW + 10; int dw = compact ? w : w - browserW - 10;
                panel(graphics, x, CONTENT_TOP, dw, contentBottom - CONTENT_TOP);
                int innerX = x + 6; int innerW = dw - 12;
                String detailTitle = eventMode ? pressureEventName(draft.source) : stageName(model, selectedPhaseId);
                drawScaledMarqueeLeft(graphics, font, Component.literal(
                                detailTitle + (draft.dirty ? "  • Unsaved" : "")).withStyle(ChatFormatting.BOLD),
                        innerX, CONTENT_TOP + 4, Math.max(30, innerW - 110), 0xFFFFFF, 1.15F);
                drawMarqueeLeft(graphics, font, Component.literal(draft.source.origin()
                        + (draft.source.overridden() && !draft.source.origin().contains("override")
                                ? "  • World override" : "")),
                        innerX, CONTENT_TOP + 18, Math.max(30, innerW - 110), 0xAAAAAA);
                if (configStatusX > 0) {
                    sectionPanel(graphics, configStatusX, configStatusY, configStatusWidth, 20);
                    graphics.drawCenteredString(font, draft.enabled ? "On" : "Off",
                            configStatusX + configStatusWidth / 2, configStatusY + 6,
                            draft.enabled ? 0xFF66FF66 : 0xFF999999);
                }
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
                drawMarqueeLeft(graphics, font, Component.literal(draft.enabled
                                ? "Configured probabilities; active after eligibility and compatibility."
                                : "This Mob Config is disabled. Its settings are preserved; gameplay chance is 0%."),
                        sectionInnerX, generalSectionY + 57 + previewLines.size() * font.lineHeight,
                        sectionInnerW, draft.enabled ? 0x888888 : 0xD8B84A);
                drawSectionLabel(graphics, "ELIGIBLE MOBS", sectionInnerX, eligibilitySectionY + 5, sectionInnerW);
                drawMarqueeLeft(graphics, font, Component.literal(eligibleMobSummary()), sectionInnerX,
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

        private enum PoolBrowserRowKind { GROUP, POOL, ADD_TIER }
        private record PoolBrowserRow(PoolBrowserRowKind kind, WorldAwakenedPoolBrowserGrouping.Group group,
                String stageId, PoolModel pool) {}
        private enum MutationBrowserKind { CATEGORY, PHASE, EVENT }
        private record MutationBrowserRow(MutationBrowserKind kind, String category, String id, String primary,
                String secondary, String state, String tooltip) {
            static MutationBrowserRow category(String id, String title, String tooltip) {
                return new MutationBrowserRow(MutationBrowserKind.CATEGORY, id, id, title, "", "", tooltip);
            }
        }
    }

    private static final class PhaseConfigurationNameScreen extends EditorScreen {
        private final MutationPoolsScreen parent;
        private final String stageId;
        private final String dimensionId;
        private final WorldAwakenedPhaseMutationIndex.Configuration existing;
        private EditBox name;
        private String error = "";

        PhaseConfigurationNameScreen(ClientModel model, MutationPoolsScreen parent, String stageId, String dimensionId,
                WorldAwakenedPhaseMutationIndex.Configuration existing) {
            super(existing == null ? "New Mob Configuration" : "Rename Mob Configuration", model);
            this.parent = parent; this.stageId = stageId; this.dimensionId = dimensionId; this.existing = existing;
        }

        @Override protected void init() {
            clearWidgets(); int left = panelLeft(), w = panelWidth();
            name = new EditBox(font, left + 14, 89, w - 28, 20, Component.literal("Configuration name"));
            name.setHint(Component.literal("e.g. Dangerous Skeletons").withStyle(ChatFormatting.DARK_GRAY));
            name.setValue(existing == null ? "" : existing.name()); addRenderableWidget(name);
            setInitialFocus(name); setFocused(name); name.setFocused(true);
            button(left, height - 25, 82, "Cancel", ignored -> minecraft.setScreen(parent), null);
            button(left + w - 82, height - 25, 82, existing == null ? "Create" : "Rename", ignored -> save(),
                    "Save through the server-authoritative authoring bridge.");
        }

        private void save() {
            String value = name.getValue().trim();
            if (value.isBlank()) { error = "A configuration name is required."; return; }
            boolean duplicate = parent.phaseIndex.configurations(stageId).stream()
                    .filter(WorldAwakenedPhaseMutationIndex.Configuration::active)
                    .filter(config -> config.dimensionId().equals(dimensionId))
                    .filter(config -> existing == null || !config.poolId().equals(existing.poolId()))
                    .anyMatch(config -> config.name().equalsIgnoreCase(value));
            if (duplicate) { error = "That name is already used in this phase and dimension."; return; }
            parent.saveConfigurationName(existing, value);
        }

        @Override public void render(GuiGraphics graphics, int mx, int my, float tick) {
            renderBackground(graphics, mx, my, tick);
            header(graphics, existing == null ? "New Mob Configuration" : "Rename Mob Configuration",
                    "Mob Mutations > " + stageName(model, stageId));
            panel(graphics, panelLeft(), 47, panelWidth(), height - 77);
            graphics.drawString(font, "NAME", panelLeft() + 14, 72, 0xFFFFAA00);
            graphics.drawWordWrap(font, Component.literal("Dimension: " + readableId(dimensionId)
                    + ". Specialised configurations override Default Mobs only for their explicitly selected mobs."),
                    panelLeft() + 14, 122, panelWidth() - 28, 0xFFBEBEBE);
            if (!error.isBlank()) graphics.drawString(font, error, panelLeft() + 14, height - 43, 0xFFFF6666);
            renderWidgets(graphics, mx, my, tick);
        }
    }

    private static final class PhaseDimensionPickerScreen extends EditorScreen {
        private final MutationPoolsScreen parent; private final List<String> dimensions;
        private EditBox search; private String query = ""; private int offset, totalRows, visibleRows;
        PhaseDimensionPickerScreen(ClientModel model, MutationPoolsScreen parent, List<String> dimensions) {
            super("Select Dimension", model); this.parent = parent; this.dimensions = List.copyOf(dimensions);
        }
        @Override protected void init() {
            clearWidgets(); int left = panelLeft(), w = panelWidth();
            search = new EditBox(font, left + 8, 53, w - 16, 18, Component.literal("Search dimensions"));
            search.setHint(Component.literal("Search configured dimensions...").withStyle(ChatFormatting.DARK_GRAY));
            search.setValue(query); search.setResponder(value -> { query = value; offset = 0; rebuildWidgets(); });
            addRenderableWidget(search); setInitialFocus(search); setFocused(search); search.setFocused(true);
            List<String> shown = dimensions.stream().filter(value -> readableId(value).toLowerCase(Locale.ROOT)
                    .contains(query.toLowerCase(Locale.ROOT)) || value.toLowerCase(Locale.ROOT)
                    .contains(query.toLowerCase(Locale.ROOT))).toList();
            totalRows = shown.size(); visibleRows = Math.max(1, (height - 116) / 30); offset = listOffset(offset, totalRows, visibleRows);
            for (int row = 0; row < Math.min(visibleRows, shown.size() - offset); row++) {
                String id = shown.get(offset + row);
                addRenderableWidget(new SelectionRowButton(left + 8, 77 + row * 30, w - 16, 28,
                        readableId(id), id, id.equals(parent.selectedDimensionId), ignored -> parent.chooseDimension(id), false));
            }
            button(left, height - 25, 82, "Cancel", ignored -> minecraft.setScreen(parent), null);
        }
        @Override public boolean mouseScrolled(double mx, double my, double dx, double dy) {
            int next = listOffset(offset + (dy < 0 ? 1 : -1), totalRows, visibleRows);
            if (next != offset) { offset = next; rebuildWidgets(); return true; } return super.mouseScrolled(mx, my, dx, dy);
        }
        @Override public void render(GuiGraphics graphics, int mx, int my, float tick) {
            renderBackground(graphics, mx, my, tick); header(graphics, "Select Dimension", "Mob Mutations");
            panel(graphics, panelLeft(), 47, panelWidth(), height - 77);
            graphics.drawString(font, "DIMENSIONS", panelLeft() + 8, 53, 0xD8B84A, false);
            if (totalRows > visibleRows) renderScrollbar(graphics, panelLeft() + panelWidth() - 6, 90,
                    Math.max(1, height - 129), totalRows, visibleRows, offset);
            renderWidgets(graphics, mx, my, tick);
        }
    }

    private static final class AddPhaseDimensionScreen extends EditorScreen {
        private final MutationPoolsScreen parent;
        private EditBox search; private String query = ""; private int offset, totalRows, visibleRows;
        AddPhaseDimensionScreen(ClientModel model, MutationPoolsScreen parent) {
            super("Add Mob Settings", model); this.parent = parent;
        }
        @Override protected void init() {
            clearWidgets(); int left = panelLeft(), w = panelWidth();
            search = new EditBox(font, left + 8, 66, w - 16, 18, Component.literal("Search dimensions"));
            search.setHint(Component.literal("Choose a dimension...").withStyle(ChatFormatting.DARK_GRAY));
            search.setValue(query); search.setResponder(value -> { query = value; offset = 0; rebuildWidgets(); });
            addRenderableWidget(search); setInitialFocus(search); setFocused(search); search.setFocused(true);
            Set<String> existing = parent.phaseIndex.configurations(parent.selectedPhaseId).stream()
                    .filter(WorldAwakenedPhaseMutationIndex.Configuration::active)
                    .filter(config -> !config.legacyReview())
                    .map(WorldAwakenedPhaseMutationIndex.Configuration::dimensionId).collect(java.util.stream.Collectors.toSet());
            List<String> shown = model.dimensions().stream().filter(id -> !existing.contains(id))
                    .filter(id -> (readableId(id) + " " + id).toLowerCase(Locale.ROOT)
                            .contains(query.toLowerCase(Locale.ROOT))).toList();
            totalRows = shown.size(); visibleRows = Math.max(1, (height - 129) / 30);
            offset = listOffset(offset, totalRows, visibleRows);
            for (int row = 0; row < Math.min(visibleRows, shown.size() - offset); row++) {
                String id = shown.get(offset + row);
                addRenderableWidget(new SelectionRowButton(left + 8, 90 + row * 30, w - 16, 28,
                        readableId(id), id, false, ignored -> parent.addPhaseDimension(id), false));
            }
            button(left, height - 25, 82, "Cancel", ignored -> minecraft.setScreen(parent), null);
        }
        @Override public boolean mouseScrolled(double mx, double my, double dx, double dy) {
            int next = listOffset(offset + (dy < 0 ? 1 : -1), totalRows, visibleRows);
            if (next != offset) { offset = next; rebuildWidgets(); return true; }
            return super.mouseScrolled(mx, my, dx, dy);
        }
        @Override public void render(GuiGraphics graphics, int mx, int my, float tick) {
            renderBackground(graphics, mx, my, tick);
            header(graphics, "Add Mob Settings", "Mob Mutations > " + stageName(model, parent.selectedPhaseId));
            panel(graphics, panelLeft(), 47, panelWidth(), height - 77);
            graphics.drawString(font, "DIMENSIONS", panelLeft() + 8, 53, 0xD8B84A, false);
            if (totalRows == 0) graphics.drawCenteredString(font, "No unconfigured dimensions match.", width / 2, 104, 0xFFAAAAAA);
            if (totalRows > visibleRows) renderScrollbar(graphics, panelLeft() + panelWidth() - 6, 90,
                    Math.max(1, height - 129), totalRows, visibleRows, offset);
            renderWidgets(graphics, mx, my, tick);
        }
    }

    private static final class MobConfigPickerScreen extends EditorScreen {
        private final MutationPoolsScreen parent;
        private final List<ConfigChoice> configurations;
        private EditBox search; private String query = ""; private int offset, totalRows, visibleRows;
        private String selectedPoolId;
        private Boolean pendingEnabled;
        MobConfigPickerScreen(ClientModel model, MutationPoolsScreen parent,
                List<WorldAwakenedPhaseMutationIndex.Configuration> configurations) {
            super("Select Mob Config", model); this.parent = parent;
            this.configurations = configurations.stream().map(config -> new ConfigChoice(config.poolId(), config.name(),
                    config.defaultConfiguration() ? "Default for other eligible mobs" : "Specialised mob selection", config))
                    .toList();
            this.selectedPoolId = parent.draft == null ? null : parent.draft.id;
        }
        MobConfigPickerScreen(ClientModel model, MutationPoolsScreen parent, PoolModel eventPool) {
            super("Select Mob Config", model); this.parent = parent;
            this.configurations = List.of(new ConfigChoice(eventPool.id(), parent.pressureEventName(eventPool),
                    "Pressure-event mutation configuration", null));
            this.selectedPoolId = eventPool.id();
        }
        @Override protected void init() {
            clearWidgets(); int left = panelLeft(), w = panelWidth();
            search = new EditBox(font, left + 8, 66, w - 16, 18, Component.literal("Search Mob Configs"));
            search.setHint(Component.literal("Search named Mob Configs...").withStyle(ChatFormatting.DARK_GRAY));
            search.setValue(query); search.setResponder(value -> { query = value; offset = 0; rebuildWidgets(); });
            addRenderableWidget(search); setInitialFocus(search); setFocused(search); search.setFocused(true);
            List<ConfigChoice> shown = configurations.stream()
                    .filter(config -> config.name().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)))
                    .toList();
            boolean phaseChoices = configurations.stream().anyMatch(choice -> choice.configuration() != null);
            int controlsTop = phaseChoices ? height - 73 : height - 49;
            totalRows = shown.size(); visibleRows = Math.max(1, (controlsTop - 94) / 34);
            offset = listOffset(offset, totalRows, visibleRows);
            for (int row = 0; row < Math.min(visibleRows, shown.size() - offset); row++) {
                var config = shown.get(offset + row); PoolModel pool = model.pool(config.poolId());
                boolean enabled = effectiveEnabled(config.poolId(), pool);
                boolean removed = config.configuration() != null && !config.configuration().active();
                addRenderableWidget(new PoolTierRowButton(left + 8, 90 + row * 34, w - 16, 32,
                        config.name(), config.description(), removed ? "Removed" : enabled ? "On" : "Off",
                        config.poolId().equals(selectedPoolId),
                        config.poolId().equals(selectedPoolId) && pendingEnabled != null, ignored -> {
                            selectedPoolId = config.poolId(); pendingEnabled = null; rebuildWidgets();
                        }));
            }
            PoolModel selected = selectedPoolId == null ? null : model.pool(selectedPoolId);
            boolean enabled = selected != null && effectiveEnabled(selectedPoolId, selected);
            ConfigChoice selectedChoice = configurations.stream().filter(value -> value.poolId().equals(selectedPoolId))
                    .findFirst().orElse(null);
            boolean removed = selectedChoice != null && selectedChoice.configuration() != null
                    && !selectedChoice.configuration().active();
            Button toggle = button(left, controlsTop, phaseChoices ? (w - 4) / 2 : w,
                    enabled ? "Disable Selected" : "Enable Selected", ignored -> {
                pendingEnabled = !enabled; rebuildWidgets();
            }, "Change only the selected Mob Config's gameplay participation. Confirm keeps this as a local draft until Apply.");
            toggle.active = model.editable() && selected != null && !removed;
            if (phaseChoices) {
                int half = (w - 4) / 2;
                Button rename = button(left + half + 4, controlsTop, w - half - 4,
                        "Rename Selected", ignored -> {
                            if (selectedChoice != null && selectedChoice.configuration() != null)
                                parent.renameFromPicker(selectedChoice.configuration());
                        }, "Rename the selected Mob Config without changing its stable ID.");
                rename.active = model.editable() && selectedChoice != null && selectedChoice.configuration() != null
                        && !removed && pendingEnabled == null;
                button(left, height - 49, half, "Create Mob Config", ignored -> parent.createFromPicker(),
                        "Create a specialised Mob Config for this phase and dimension.").active = model.editable();
                Button remove = button(left + half + 4, height - 49, w - half - 4,
                        removed ? "Restore Selected" : "Remove Selected", ignored -> {
                            if (selectedChoice == null || selectedChoice.configuration() == null) return;
                            if (removed) parent.restoreFromPicker(selectedChoice.configuration());
                            else parent.removeFromPicker(selectedChoice.configuration());
                        }, removed ? "Restore this source-backed Mob Config to the world profile."
                                : "Remove the selected Mob Config from this phase and dimension.");
                remove.active = model.editable() && selectedChoice != null
                        && selectedChoice.configuration() != null && pendingEnabled == null
                        && (!removed || parent.sourceBacked(selected));
            }
            button(left + w - 170, height - 25, 82, "Confirm", ignored -> confirm(),
                    "Return to the editor with this selection and any pending enabled-state change.");
            button(left + w - 82, height - 25, 82, "Cancel", ignored -> minecraft.setScreen(parent),
                    "Return without changing selection or enabled state.");
        }
        private boolean effectiveEnabled(String poolId, PoolModel pool) {
            boolean actual = parent.draft != null && parent.draft.id.equals(poolId) ? parent.draft.enabled
                    : pool != null && (!pool.definition().has("enabled") || pool.definition().get("enabled").getAsBoolean());
            return poolId.equals(selectedPoolId) && pendingEnabled != null ? pendingEnabled : actual;
        }
        private void confirm() {
            ConfigChoice selected = configurations.stream().filter(value -> value.poolId().equals(selectedPoolId))
                    .findFirst().orElse(null);
            if (selectedPoolId != null && (selected == null || selected.configuration() == null
                    || selected.configuration().active())) parent.acceptPickerSelection(selectedPoolId, pendingEnabled);
            else minecraft.setScreen(parent);
        }
        @Override public boolean mouseScrolled(double mx, double my, double dx, double dy) {
            int next = listOffset(offset + (dy < 0 ? 1 : -1), totalRows, visibleRows);
            if (next != offset) { offset = next; rebuildWidgets(); return true; } return super.mouseScrolled(mx, my, dx, dy);
        }
        @Override public void render(GuiGraphics graphics, int mx, int my, float tick) {
            renderBackground(graphics, mx, my, tick); header(graphics, "Select Mob Config", "Mob Mutations");
            panel(graphics, panelLeft(), 47, panelWidth(), height - 77);
            graphics.drawString(font, "MOB CONFIGURATIONS", panelLeft() + 8, 53, 0xD8B84A, false);
            if (pendingEnabled != null) graphics.drawString(font,
                    "Pending: selected Mob Config will be " + (pendingEnabled ? "enabled" : "disabled") + " after Apply.",
                    panelLeft() + 8, configurations.stream().anyMatch(choice -> choice.configuration() != null)
                            ? height - 87 : height - 63, 0xFFFFCC66, false);
            if (totalRows > visibleRows) renderScrollbar(graphics, panelLeft() + panelWidth() - 6, 90,
                    Math.max(1, (configurations.stream().anyMatch(choice -> choice.configuration() != null)
                            ? height - 73 : height - 49) - 94), totalRows, visibleRows, offset);
            renderWidgets(graphics, mx, my, tick);
        }
        private record ConfigChoice(String poolId, String name, String description,
                WorldAwakenedPhaseMutationIndex.Configuration configuration) {}
    }

    private static final class PoolTierRowButton extends AbstractButton {
        private final String primary, secondary, state; private final boolean selected; private final BooleanSupplier dirty; private final Button.OnPress onPress;
        PoolTierRowButton(int x, int y, int width, int height, String primary, String secondary,
                boolean selected, boolean dirty, Button.OnPress onPress) {
            this(x, y, width, height, primary, secondary, "", selected, () -> dirty, onPress);
        }
        PoolTierRowButton(int x, int y, int width, int height, String primary, String secondary, String state,
                boolean selected, boolean dirty, Button.OnPress onPress) {
            this(x, y, width, height, primary, secondary, state, selected, () -> dirty, onPress);
        }
        PoolTierRowButton(int x, int y, int width, int height, String primary, String secondary, String state,
                boolean selected, BooleanSupplier dirty, Button.OnPress onPress) {
            super(x, y, width, height, Component.literal(primary)); this.primary = primary; this.secondary = secondary;
            this.state = state; this.selected = selected; this.dirty = dirty; this.onPress = onPress;
        }
        @Override public void onPress() { onPress.onPress(null); }
        @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int background = selected ? 0xE0446688 : isHoveredOrFocused() ? 0xD04A4A4A : 0xB02A2A2A;
            int border = selected ? 0xFF77AAFF : isHoveredOrFocused() ? 0xFF888888 : 0xFF505050;
            graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), background);
            graphics.fill(getX(), getY(), getX() + getWidth(), getY() + 1, border);
            graphics.fill(getX(), getY() + getHeight() - 1, getX() + getWidth(), getY() + getHeight(), border);
            graphics.fill(getX(), getY(), getX() + 1, getY() + getHeight(), border);
            graphics.fill(getX() + getWidth() - 1, getY(), getX() + getWidth(), getY() + getHeight(), border);
            if (selected) graphics.fill(getX(), getY(), getX() + 3, getY() + getHeight(), 0xFF77AAFF);
            Font font = Minecraft.getInstance().font;
            int stateWidth = state.isBlank() ? 0 : font.width(state) + 8;
            boolean localDirty = dirty.getAsBoolean();
            drawMarqueeLeft(graphics, font, Component.literal(primary + (localDirty ? "  • Unsaved" : "")),
                    getX() + 7, getY() + 5, Math.max(20, getWidth() - 14 - stateWidth), localDirty ? 0xFFFFDD66 : 0xFFFFFFFF);
            if (!state.isBlank()) graphics.drawString(font, state, getX() + getWidth() - stateWidth + 2, getY() + 5,
                    state.contains("disabled") || state.equals("Off") ? 0xFF999999
                            : state.equals("Mixed") ? 0xFFFFCC66 : 0xFF66FF66, false);
            drawMarqueeLeft(graphics, font, Component.literal(secondary), getX() + 7, getY() + 18,
                    getWidth() - 14, 0xFFAAAAAA);
        }
        @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
    }

    private static final class PhaseRowButton extends AbstractButton {
        private final String primary, secondary;
        private final boolean enabled, selected, dirty;
        private final int rightReserve;
        private final Button.OnPress onPress;

        PhaseRowButton(int x, int y, int width, int height, String primary, String secondary,
                boolean enabled, boolean selected, boolean dirty, int rightReserve, Button.OnPress onPress) {
            super(x, y, width, height, Component.literal(primary));
            this.primary = primary; this.secondary = secondary; this.enabled = enabled;
            this.selected = selected; this.dirty = dirty; this.rightReserve = rightReserve; this.onPress = onPress;
            setTooltip(Tooltip.create(Component.literal(primary + "\n" + secondary)));
        }

        @Override public void onPress() { onPress.onPress(null); }

        @Override public boolean isMouseOver(double mouseX, double mouseY) {
            // The selected row is rendered before its compact ordering controls. Do not let the
            // row's full-width hitbox consume clicks intended for those visible buttons.
            return WorldAwakenedPhaseOrdering.rowOwnsPoint(getX(), getY(), getWidth(), getHeight(),
                    rightReserve, mouseX, mouseY);
        }

        @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!WorldAwakenedPhaseOrdering.rowOwnsPoint(getX(), getY(), getWidth(), getHeight(),
                    rightReserve, mouseX, mouseY)) return false;
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int background = selected ? 0xE0446688 : isHoveredOrFocused() ? 0xD04A4A4A : 0xB02A2A2A;
            int border = selected ? 0xFF77AAFF : isHoveredOrFocused() ? 0xFF888888 : 0xFF505050;
            graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), background);
            graphics.fill(getX(), getY(), getX() + getWidth(), getY() + 1, border);
            graphics.fill(getX(), getY() + getHeight() - 1, getX() + getWidth(), getY() + getHeight(), border);
            graphics.fill(getX(), getY(), getX() + 1, getY() + getHeight(), border);
            graphics.fill(getX() + getWidth() - 1, getY(), getX() + getWidth(), getY() + getHeight(), border);
            if (selected) graphics.fill(getX(), getY(), getX() + 3, getY() + getHeight(), 0xFF77AAFF);
            Font font = Minecraft.getInstance().font;
            String state = enabled ? "On" : "Off";
            int stateWidth = font.width(state) + 8;
            drawMarqueeLeft(graphics, font, Component.literal(primary + (dirty ? "  • Unsaved" : "")),
                    getX() + 9, getY() + 5, Math.max(20, getWidth() - stateWidth - 20),
                    dirty ? 0xFFFFDD66 : 0xFFFFFFFF);
            graphics.drawString(font, state, getX() + getWidth() - stateWidth + 2, getY() + 5,
                    enabled ? 0xFF66FF66 : 0xFF999999, false);
            drawMarqueeLeft(graphics, font, Component.literal(secondary), getX() + 9, getY() + 18,
                    Math.max(20, getWidth() - 18 - rightReserve), 0xFFAAAAAA);
        }

        @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
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
        private String primary;
        private final String secondary;
        private final boolean selected;
        private final boolean checkbox;
        private final int trailingClickExclusion;
        private final Button.OnPress onPress;
        private String trailingStatus = "";
        private int trailingStatusColor = 0xFFFFFF;
        private String primaryTag = "";
        private int primaryTagColor = 0xFFFFD34A;
        private int primaryColor = 0xFFFFFF;
        private Supplier<String> livePrimary;
        private BooleanSupplier liveDirty;

        SelectionRowButton(int x, int y, int width, int height, String primary, String secondary,
                boolean selected, Button.OnPress onPress) {
            this(x, y, width, height, primary, secondary, selected, onPress, true);
        }

        SelectionRowButton(int x, int y, int width, int height, String primary, String secondary,
                boolean selected, Button.OnPress onPress, boolean checkbox) {
            this(x, y, width, height, primary, secondary, selected, onPress, checkbox, 0);
        }

        SelectionRowButton(int x, int y, int width, int height, String primary, String secondary,
                boolean selected, Button.OnPress onPress, boolean checkbox, int trailingClickExclusion) {
            super(x, y, width, height, Component.literal(primary));
            if (primary.endsWith("Unsaved")) {
                int unsavedStart = Math.max(0, primary.lastIndexOf("Unsaved") - 4);
                this.primary = primary.substring(0, unsavedStart).stripTrailing();
                this.primaryTag = " · Unsaved";
            } else this.primary = primary;
            this.secondary = secondary;
            this.selected = selected;
            this.onPress = onPress;
            this.checkbox = checkbox;
            this.trailingClickExclusion = trailingClickExclusion;
            setTooltip(Tooltip.create(Component.literal(primary + "\n" + secondary)));
        }

        @Override public void onPress() { onPress.onPress(null); }

        SelectionRowButton withTrailingStatus(String status, int color) {
            trailingStatus = status;
            trailingStatusColor = color;
            return this;
        }

        SelectionRowButton withPrimaryTag(String tag, int color) {
            primaryTag = tag;
            primaryTagColor = color;
            return this;
        }

        SelectionRowButton withPrimaryColor(int color) {
            primaryColor = color;
            return this;
        }

        SelectionRowButton withLivePrimary(Supplier<String> value, BooleanSupplier dirty) {
            livePrimary = value;
            liveDirty = dirty;
            return this;
        }

        @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (trailingClickExclusion > 0 && mouseX >= getX() + getWidth() - trailingClickExclusion) return false;
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override public boolean isMouseOver(double mouseX, double mouseY) {
            return super.isMouseOver(mouseX, mouseY)
                    && (trailingClickExclusion <= 0 || mouseX < getX() + getWidth() - trailingClickExclusion);
        }

        @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            boolean unavailable = !active;
            int background = unavailable ? 0xB0222222 : selected ? 0xD03B5268 : isHoveredOrFocused() ? 0xD0454545 : 0xB02A2A2A;
            int border = unavailable ? 0xFF464646 : selected ? 0xFF77AAFF : isHoveredOrFocused() ? 0xFF888888 : 0xFF505050;
            graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), background);
            graphics.fill(getX(), getY(), getX() + getWidth(), getY() + 1, border);
            graphics.fill(getX(), getY() + getHeight() - 1, getX() + getWidth(), getY() + getHeight(), border);
            graphics.fill(getX(), getY(), getX() + 1, getY() + getHeight(), border);
            graphics.fill(getX() + getWidth() - 1, getY(), getX() + getWidth(), getY() + getHeight(), border);
            if (selected && !unavailable) graphics.fill(getX(), getY(), getX() + 3, getY() + getHeight(), 0xFF77AAFF);
            Font font = Minecraft.getInstance().font;
            if (checkbox) graphics.drawString(font, selected ? "[x]" : "[ ]", getX() + 7, getY() + 6,
                    unavailable ? 0x777777 : 0xFFFFFF, false);
            int textX = getX() + (checkbox ? 34 : 9);
            int statusWidth = trailingStatus.isBlank() ? 0 : font.width(trailingStatus) + 8;
            int textWidth = Math.max(20, getWidth() - (checkbox ? 41 : 16) - trailingClickExclusion - statusWidth);
            String displayedPrimary = livePrimary == null ? primary : livePrimary.get();
            boolean displayedDirty = liveDirty != null && liveDirty.getAsBoolean();
            String displayedTag = displayedDirty ? " · Unsaved" : primaryTag;
            int displayedPrimaryColor = displayedDirty ? 0xFFFFDD66 : primaryColor;
            int primaryWidth = font.width(displayedPrimary);
            int tagWidth = font.width(displayedTag);
            if (!displayedTag.isBlank() && primaryWidth + tagWidth <= textWidth) {
                graphics.drawString(font, displayedPrimary, textX, getY() + 5, unavailable ? 0x777777 : displayedPrimaryColor, false);
                graphics.drawString(font, displayedTag, textX + primaryWidth, getY() + 5,
                        unavailable ? 0x777777 : primaryTagColor, false);
            } else {
                drawMarqueeLeft(graphics, font, Component.literal(displayedPrimary), textX, getY() + 5, textWidth,
                        unavailable ? 0x777777 : displayedPrimaryColor);
            }
            drawMarqueeLeft(graphics, font, Component.literal(secondary), textX, getY() + 18,
                    textWidth, unavailable ? 0x696969 : 0xAAAAAA);
            if (!trailingStatus.isBlank()) graphics.drawString(font, trailingStatus,
                    getX() + getWidth() - 8 - font.width(trailingStatus), getY() + 5,
                    unavailable ? 0x777777 : trailingStatusColor, false);
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
        final LinkedHashMap<String, JsonObject> newMutators = new LinkedHashMap<>();
        final Set<String> deletedMutatorIds = new LinkedHashSet<>();
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
            setInitialFocus(search); setFocused(search); search.setFocused(true);
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
            setInitialFocus(search); setFocused(search); search.setFocused(true);
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
            search = new EditBox(font, left + 6, panelTop + 18, w - 120, 20, Component.literal("Search mutators"));
            search.setHint(Component.literal("Search names, rarity or components...").withStyle(ChatFormatting.DARK_GRAY));
            search.setValue(query); search.setResponder(value -> { query = value; list.offset = 0; rebuildWidgets(); }); addRenderableWidget(search);
            button(left + w - 108, panelTop + 18, 102, "+ Mutator", ignored ->
                    minecraft.setScreen(new MutatorEditorScreen(model, parent, draft, null)),
                    "Create a reusable mutation definition.");
            setInitialFocus(search); setFocused(search); search.setFocused(true);
            List<MutatorModel> shown = filtered();
            list.configure(left + 6, panelTop + 43, w - 12, panelBottom - panelTop - 48, ROW_HEIGHT, shown.size());
            for (int row = 0; row < Math.min(list.visible, Math.max(0, shown.size() - list.offset)); row++) {
                MutatorModel mutator = shown.get(list.offset + row); boolean checked = selected.containsKey(mutator.id());
                String secondary = mutator.components().isEmpty() ? "No components" : String.join(", ", mutator.components());
                SelectionRowButton entry = new SelectionRowButton(list.x, list.y + row * ROW_HEIGHT,
                        list.rowWidth(), ROW_HEIGHT - 2, mutator.name(), secondary, checked, ignored -> {
                    if (selected.containsKey(mutator.id())) selected.remove(mutator.id()); else selected.put(mutator.id(), 1); rebuildWidgets();
                }, true, 116);
                entry.setTooltip(Tooltip.create(Component.literal(mutatorTooltip(mutator))));
                addRenderableWidget(entry);
                int editX = list.x + list.rowWidth() - 112;
                button(editX, list.y + row * ROW_HEIGHT + 7, 72, "Edit", ignored ->
                        minecraft.setScreen(new MutatorEditorScreen(model, parent, draft, mutator)),
                        "Edit this reusable mutator. Changes are saved as a world override.");
                boolean authored = mutator.worldAuthored() || draft.newMutators.containsKey(mutator.id());
                Button remove = button(editX + 78, list.y + row * ROW_HEIGHT + 7, 28, "×", ignored ->
                        removeMutator(mutator), authored
                                ? "Delete this world-authored mutator, or restore its bundled source definition."
                                : "Bundled mutators cannot be deleted here. Remove them from this pool by clicking the row.");
                remove.active = authored;
            }
            button(width / 2 - 104, height - 25, 100, "Confirm", ignored -> { draft.weights.clear(); draft.weights.putAll(selected); draft.dirty = true; minecraft.setScreen(parent); }, null);
            button(width / 2 + 4, height - 25, 100, "Cancel", ignored -> minecraft.setScreen(parent), null);
        }
        private List<MutatorModel> filtered() {
            String q = query.toLowerCase(Locale.ROOT);
            LinkedHashMap<String, MutatorModel> available = new LinkedHashMap<>(); model.mutators().forEach(value -> available.put(value.id(), value));
            draft.newMutators.forEach((id, definition) -> available.put(id, mutatorModel(definition, true)));
            return available.values().stream().filter(m -> !draft.deletedMutatorIds.contains(m.id()))
                    .filter(m -> (m.name() + " " + m.id() + " " + String.join(" ", m.components())).toLowerCase(Locale.ROOT).contains(q)).toList();
        }
        private void removeMutator(MutatorModel mutator) {
            if (draft.newMutators.remove(mutator.id()) == null) draft.deletedMutatorIds.add(mutator.id());
            selected.remove(mutator.id()); draft.weights.remove(mutator.id()); draft.dirty = true; rebuildWidgets();
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

    /** Small, deliberately bounded authoring screen for reusable native mutators. */
    private static final class MutatorEditorScreen extends EditorScreen {
        private static final int MAX_EFFECTS = 6;
        private final MutationPoolsScreen parent; private final PoolDraft pool; private final MutatorModel source;
        private final List<MutatorEffectDraft> effects = new ArrayList<>();
        private EditBox name; private String error = "";
        MutatorEditorScreen(ClientModel model, MutationPoolsScreen parent, PoolDraft pool, MutatorModel source) {
            super(source == null ? "Create Mutator" : "Edit Mutator", model);
            this.parent = parent; this.pool = pool; this.source = source;
            if (source == null) effects.add(MutatorEffectDraft.simple("max_health_bonus"));
            else if (source.definition().has("components")) source.definition().getAsJsonArray("components")
                    .forEach(value -> effects.add(new MutatorEffectDraft(value.getAsJsonObject())));
        }
        @Override protected void init() {
            clearWidgets(); int left = panelLeft(), w = panelWidth();
            int generalY = 50;
            name = new EditBox(font, left + 8, generalY + 31, w - 16, 20, Component.literal("Mutator name"));
            name.setMaxLength(128); name.setValue(source == null ? "New Mutator" : source.name()); addRenderableWidget(name);
            int effectsY = generalY + 74;
            for (int index = 0; index < effects.size(); index++) {
                MutatorEffectDraft effect = effects.get(index); final int effectIndex = index;
                int rowY = effectsY + 24 + index * 25;
                int amountW = 118, removeW = 24, pickerW = w - amountW - removeW - 24;
                button(left + 8, rowY, pickerW, (index + 1) + ". " + effect.name(), ignored ->
                        minecraft.setScreen(new MutatorEffectPickerScreen(model, this, effectIndex)),
                        effect.supported() ? "Choose a live effect." : "This existing effect is preserved. Choose a supported effect to replace it.");
                if (effect.hasDirectAmount()) {
                    EditBox amount = new EditBox(font, left + 12 + pickerW, rowY, amountW, 20, Component.literal("Amount"));
                    amount.setFilter(value -> value.isEmpty() || value.matches("-?[0-9]*([.][0-9]*)?")); amount.setValue(effect.amount);
                    amount.setTooltip(Tooltip.create(Component.literal(directAmountTooltip(effect.type))));
                    amount.setResponder(value -> effect.amount = value); addRenderableWidget(amount);
                } else {
                    button(left + 12 + pickerW, rowY, amountW, effect.hasParameters() ? "Configure..." : "No settings", ignored -> {
                        if (effect.hasParameters()) minecraft.setScreen(new MutatorComponentSettingsScreen(model, this, effect));
                    }, effect.hasParameters() ? "Edit this component's parameters." : "This component has no parameters.");
                }
                button(left + w - 8 - removeW, rowY, removeW, "×", ignored -> {
                    effects.remove(effectIndex); rebuildWidgets();
                }, "Remove this effect.").active = effects.size() > 1;
            }
            int addY = effectsY + 24 + effects.size() * 25;
            Button add = button(left + 8, addY, Math.min(180, w - 16), "+ Add Effect (" + effects.size() + "/" + MAX_EFFECTS + ")", ignored -> {
                effects.add(MutatorEffectDraft.simple("max_health_bonus")); rebuildWidgets();
            }, "Add another supported effect, up to six."); add.active = effects.size() < MAX_EFFECTS;
            button(width / 2 - 104, height - 25, 100, source == null ? "Create Mutator" : "Save Mutator", ignored -> save(),
                    "Validate and retain this reusable mutator in the local pool draft.");
            button(width / 2 + 4, height - 25, 100, "Cancel", ignored -> minecraft.setScreen(new MutatorSelectorScreen(model, parent, pool)),
                    "Discard changes to this mutator.");
        }
        void chooseEffect(int index, String type) { effects.set(index, MutatorEffectDraft.simple(type)); minecraft.setScreen(this); }
        private void save() {
            String label = name.getValue().trim(); if (label.isBlank()) { error = "A mutator needs a name."; return; }
            if (effects.isEmpty()) { error = "A mutator needs at least one effect."; return; }
            JsonObject definition = source == null ? defaultMutator(uniqueMutatorId(model, label), label, "max_health_bonus", 4D) : source.definition().deepCopy();
            definition.add("display_name", textJson(label));
            JsonArray components = new JsonArray();
            for (MutatorEffectDraft effect : effects) {
                try { components.add(effect.toJson()); } catch (NumberFormatException exception) { error = effect.name() + " needs a valid number."; return; }
            }
            definition.add("components", components);
            pool.newMutators.put(definition.get("id").getAsString(), definition);
            pool.deletedMutatorIds.remove(definition.get("id").getAsString()); pool.dirty = true;
            minecraft.setScreen(new MutatorSelectorScreen(model, parent, pool));
        }
        @Override public void render(GuiGraphics graphics, int mx, int my, float tick) {
            renderBackground(graphics, mx, my, tick); header(graphics, title.getString(), "Mob Mutations > " + pool.name + " > Mutator");
            panel(graphics, panelLeft(), 39, panelWidth(), height - 84);
            sectionOutline(graphics, panelLeft() + 6, 49, panelWidth() - 12, 68);
            graphics.drawString(font, "GENERAL", panelLeft() + 12, 55, 0xD8B84A, false);
            graphics.drawString(font, "Name", panelLeft() + 12, 70, 0xBBBBBB, false);
            int effectsY = 124, effectsHeight = Math.max(84, effects.size() * 25 + 63);
            sectionOutline(graphics, panelLeft() + 6, effectsY, panelWidth() - 12, effectsHeight);
            graphics.drawString(font, "EFFECTS", panelLeft() + 12, effectsY + 6, 0xD8B84A, false);
            graphics.drawString(font, "Choose up to six runtime-supported effects. Amounts apply to the effect on the same row.", panelLeft() + 12, effectsY + effectsHeight - 15, 0xBBBBBB, false);
            if (!error.isBlank()) graphics.drawString(font, error, panelLeft() + 12, effectsY + effectsHeight + 8, 0xFF7777, false);
            renderWidgets(graphics, mx, my, tick);
        }
    }

    private static final class MutatorEffectPickerScreen extends EditorScreen {
        private static final int ROW_HEIGHT = 38;
        private final MutatorEditorScreen parent; private final int index; private final ScrollableRows list = new ScrollableRows(); private EditBox search; private String query = "";
        private List<MutatorPickerEntry> entries = List.of();
        MutatorEffectPickerScreen(ClientModel model, MutatorEditorScreen parent, int index) { super("Select Effect", model); this.parent = parent; this.index = index; }
        @Override protected void init() {
            clearWidgets(); int left = panelLeft(), w = panelWidth();
            search = new EditBox(font, left + 6, 69, w - 12, 20, Component.literal("Search effects")); search.setHint(Component.literal("Search supported effects...").withStyle(ChatFormatting.DARK_GRAY));
            search.setValue(query); search.setResponder(value -> { query = value; rebuildWidgets(); }); addRenderableWidget(search); setInitialFocus(search); setFocused(search);
            List<String> shown = mutatorEffectOptions().stream().filter(type -> (readableId(type) + " " + effectDescription(type)).toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))).toList();
            entries = new ArrayList<>(); String category = "";
            for (String type : shown) {
                String nextCategory = mutatorEffectCategory(type);
                if (!nextCategory.equals(category)) { entries.add(new MutatorPickerEntry(nextCategory, null)); category = nextCategory; }
                entries.add(new MutatorPickerEntry(null, type));
            }
            list.configure(left + 6, 96, w - 12, height - 151, ROW_HEIGHT, entries.size());
            for (int row = 0; row < Math.min(list.visible, Math.max(0, entries.size() - list.offset)); row++) {
                MutatorPickerEntry entry = entries.get(list.offset + row); int y = list.y + row * ROW_HEIGHT;
                if (entry.type() != null) {
                    SelectionRowButton item = new SelectionRowButton(list.x, y, list.rowWidth(), ROW_HEIGHT - 2,
                            readableId(entry.type()), effectDescription(entry.type()), false,
                            ignored -> parent.chooseEffect(index, entry.type()), false);
                    boolean live = isLiveMutatorComponent(entry.type());
                    item.active = live;
                    item.setTooltip(Tooltip.create(Component.literal(live
                            ? effectDescription(entry.type())
                            : "Not available yet: this component has no safe live runtime handler.")));
                    addRenderableWidget(item);
                }
            }
            button(width / 2 - 50, height - 25, 100, "Cancel", ignored -> minecraft.setScreen(parent), "Keep the current effect.");
        }
        @Override public boolean mouseScrolled(double mx, double my, double dx, double dy) { if (list.contains(mx, my) && list.scroll(dy)) { rebuildWidgets(); return true; } return super.mouseScrolled(mx, my, dx, dy); }
        @Override public boolean mouseClicked(double mx, double my, int button) { if (button == 0 && list.beginDrag(mx, my)) { rebuildWidgets(); return true; } return super.mouseClicked(mx, my, button); }
        @Override public boolean mouseDragged(double mx, double my, int button, double dx, double dy) { if (button == 0 && list.dragging) { if (list.drag(my)) rebuildWidgets(); return true; } return super.mouseDragged(mx, my, button, dx, dy); }
        @Override public boolean mouseReleased(double mx, double my, int button) { list.release(); return super.mouseReleased(mx, my, button); }
        @Override public void render(GuiGraphics graphics, int mx, int my, float tick) {
            renderBackground(graphics, mx, my, tick); header(graphics, "Select Effect", "Mob Mutations > Mutator > Effects"); panel(graphics, panelLeft(), 39, panelWidth(), height - 84); graphics.drawString(font, "EFFECTS", panelLeft() + 6, 51, 0xD8B84A, false); list.render(graphics);
            for (int row = 0; row < Math.min(list.visible, Math.max(0, entries.size() - list.offset)); row++) {
                MutatorPickerEntry entry = entries.get(list.offset + row);
                if (entry.type() == null) {
                    int y = list.y + row * ROW_HEIGHT;
                    graphics.fill(list.x, y, list.x + list.rowWidth(), y + ROW_HEIGHT - 2, 0xA0181818);
                    graphics.drawString(font, entry.heading(), list.x + 8, y + 14, 0xD8B84A, false);
                }
            }
            renderWidgets(graphics, mx, my, tick);
        }
    }
    private record MutatorPickerEntry(String heading, String type) { }

    private static final class MutatorEffectDraft {
        final String type; final JsonObject raw; String amount;
        MutatorEffectDraft(JsonObject raw) { this.raw = raw.deepCopy(); type = componentTemplate(raw); JsonObject parameters = raw.has("parameters") ? raw.getAsJsonObject("parameters") : new JsonObject(); amount = parameters.has(type.endsWith("multiplier") ? "multiplier" : "amount") ? parameters.get(type.endsWith("multiplier") ? "multiplier" : "amount").getAsString() : "—"; }
        static MutatorEffectDraft simple(String type) { JsonObject raw = new JsonObject(); raw.addProperty("type", "worldawakened:" + type); raw.add("parameters", defaultMutatorParameters(type)); return new MutatorEffectDraft(raw); }
        boolean supported() { return mutatorEffectOptions().contains(type); }
        boolean hasDirectAmount() { return directParameter(type) != null; }
        boolean hasParameters() { return !mutatorParameterSpecs(type).isEmpty(); }
        String name() { return readableId(type); }
        JsonObject toJson() {
            String key = directParameter(type);
            if (key == null) return raw.deepCopy();
            double value = Double.parseDouble(amount);
            if (!Double.isFinite(value)) throw new NumberFormatException();
            raw.getAsJsonObject("parameters").addProperty(key, value);
            return raw.deepCopy();
        }
    }

    /** Typed parameter editor for components that are not a single numeric scalar. */
    private static final class MutatorComponentSettingsScreen extends EditorScreen {
        private final MutatorEditorScreen parent;
        private final MutatorEffectDraft effect;
        private final Map<MutatorParameter, String> values = new LinkedHashMap<>();
        private String error = "";

        MutatorComponentSettingsScreen(ClientModel model, MutatorEditorScreen parent, MutatorEffectDraft effect) {
            super("Configure " + effect.name(), model);
            this.parent = parent;
            this.effect = effect;
        }

        @Override protected void init() {
            clearWidgets();
            int left = panelLeft(), w = panelWidth(), y = 72;
            JsonObject parameters = effect.raw.getAsJsonObject("parameters");
            for (MutatorParameter parameter : mutatorParameterSpecs(effect.type)) {
                String value = values.containsKey(parameter) ? values.get(parameter)
                        : parameters.has(parameter.key()) ? parameters.get(parameter.key()).getAsString()
                        : parameter.defaultValue();
                values.put(parameter, value);
                if (parameter.kind().isRegistry()) {
                    button(left + w / 3, y, w * 2 / 3 - 8, parameter.kind() == ParameterKind.EQUIPMENT_SLOT
                                    ? readableEquipmentSlot(value) : value, ignored -> minecraft.setScreen(
                            new MutatorRegistryPickerScreen(model, this, parameter, value,
                                    equipmentSlot(),
                                    selected -> { values.put(parameter, selected); rebuildWidgets(); })),
                            componentParameterTooltip(effect.type, parameter));
                    y += 28;
                    continue;
                }
                EditBox input = new EditBox(font, left + w / 3, y, w * 2 / 3 - 8, 20,
                        Component.literal(parameter.label()));
                input.setMaxLength(256);
                input.setValue(value);
                input.setTooltip(Tooltip.create(Component.literal(componentParameterTooltip(effect.type, parameter))));
                if (parameter.kind() == ParameterKind.NUMBER) {
                    input.setFilter(candidate -> candidate.isEmpty() || candidate.matches("-?[0-9]*([.][0-9]*)?"));
                }
                input.setResponder(next -> values.put(parameter, next)); addRenderableWidget(input);
                y += 28;
            }
            button(width / 2 - 104, height - 25, 100, "Save", ignored -> save(), "Keep these component settings in the local mutator draft.");
            button(width / 2 + 4, height - 25, 100, "Cancel", ignored -> minecraft.setScreen(parent), "Discard changes to this component.");
        }

        private String equipmentSlot() {
            return values.entrySet().stream()
                    .filter(entry -> entry.getKey().key().equals("slot"))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse("auto");
        }

        private static String readableEquipmentSlot(String value) {
            return switch (value == null ? "auto" : value.toLowerCase(Locale.ROOT)) {
                case "mainhand", "main_hand" -> "Main Hand";
                case "offhand", "off_hand" -> "Off Hand";
                case "head", "helmet" -> "Head";
                case "chest", "chestplate" -> "Chest";
                case "legs", "leggings" -> "Legs";
                case "feet", "boots" -> "Feet";
                case "body" -> "Body";
                default -> "Automatic";
            };
        }

        private void save() {
            JsonObject parameters = effect.raw.getAsJsonObject("parameters");
            for (Map.Entry<MutatorParameter, String> entry : values.entrySet()) {
                MutatorParameter parameter = entry.getKey();
                String value = entry.getValue().trim();
                if (value.isBlank()) { parameters.remove(parameter.key()); continue; }
                try {
                    switch (parameter.kind()) {
                        case NUMBER -> parameters.addProperty(parameter.key(), Double.parseDouble(value));
                        case BOOLEAN -> {
                            if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) throw new IllegalArgumentException();
                            parameters.addProperty(parameter.key(), Boolean.parseBoolean(value));
                        }
                        case TEXT, MOB_EFFECT, PARTICLE, ITEM, ENTITY, EQUIPMENT_SLOT -> parameters.addProperty(parameter.key(), value);
                    }
                } catch (IllegalArgumentException exception) { error = parameter.label() + " is not valid."; return; }
            }
            minecraft.setScreen(parent);
        }

        @Override public void render(GuiGraphics graphics, int mx, int my, float tick) {
            renderBackground(graphics, mx, my, tick);
            header(graphics, title.getString(), "Mob Mutations > Mutator > Component settings");
            panel(graphics, panelLeft(), 39, panelWidth(), height - 84);
            graphics.drawString(font, "COMPONENT SETTINGS", panelLeft() + 8, 51, 0xD8B84A, false);
            int y = 78;
            for (MutatorParameter parameter : values.keySet()) {
                graphics.drawString(font, parameter.label(), panelLeft() + 8, y + 6, 0xFFFFFF, false);
                y += 28;
            }
            if (!error.isBlank()) graphics.drawString(font, error, panelLeft() + 8, height - 47, 0xFF7777, false);
            renderWidgets(graphics, mx, my, tick);
        }
    }

    private enum ParameterKind {
        NUMBER, TEXT, BOOLEAN, MOB_EFFECT, PARTICLE, ITEM, ENTITY, EQUIPMENT_SLOT;
        boolean isRegistry() { return this == MOB_EFFECT || this == PARTICLE || this == ITEM || this == ENTITY || this == EQUIPMENT_SLOT; }
        String displayName() { return switch (this) {
            case MOB_EFFECT -> "Mob Effect"; case PARTICLE -> "Particle"; case ITEM -> "Item"; case ENTITY -> "Mob";
            case EQUIPMENT_SLOT -> "Equipment Slot";
            default -> name();
        }; }
    }
    private record MutatorParameter(String label, String key, String defaultValue, ParameterKind kind) { }

    private static final class MutatorRegistryPickerScreen extends EditorScreen {
        private static final int ROW_HEIGHT = 38;
        private final Screen parent; private final MutatorParameter parameter; private final String selected;
        private final String equipmentSlot;
        private final Consumer<String> callback; private final ScrollableRows list = new ScrollableRows();
        private EditBox search; private String query = ""; private List<RegistryPickerEntry> entries = List.of();
        MutatorRegistryPickerScreen(ClientModel model, Screen parent, MutatorParameter parameter, String selected,
                String equipmentSlot,
                Consumer<String> callback) {
            super("Select " + parameter.kind().displayName(), model);
            this.parent = parent; this.parameter = parameter; this.selected = selected; this.equipmentSlot = equipmentSlot;
            this.callback = callback;
        }
        @Override protected void init() {
            clearWidgets(); int left = panelLeft(), width = panelWidth();
            search = new EditBox(font, left + 6, 69, width - 12, 20, Component.literal("Search " + parameter.kind().displayName()));
            search.setHint(Component.literal("Search " + parameter.kind().displayName().toLowerCase(Locale.ROOT) + "s...").withStyle(ChatFormatting.DARK_GRAY));
            search.setValue(query); search.setResponder(value -> { query = value; rebuildWidgets(); });
            addRenderableWidget(search); setInitialFocus(search); setFocused(search); search.setFocused(true);
            entries = registryPickerEntries(parameter.kind(), query, equipmentSlot);
            list.configure(left + 6, 96, width - 12, height - 151, ROW_HEIGHT, entries.size());
            for (int row = 0; row < Math.min(list.visible, Math.max(0, entries.size() - list.offset)); row++) {
                RegistryPickerEntry entry = entries.get(list.offset + row);
                SelectionRowButton item = new SelectionRowButton(list.x, list.y + row * ROW_HEIGHT,
                        list.rowWidth(), ROW_HEIGHT - 2, entry.label(), entry.id(), entry.id().equals(selected), ignored -> {
                    callback.accept(entry.id()); minecraft.setScreen(parent);
                }, false);
                item.setTooltip(Tooltip.create(Component.literal(entry.id()))); addRenderableWidget(item);
            }
            button(width / 2 - 50, height - 25, 100, "Cancel", ignored -> minecraft.setScreen(parent), "Keep the current ID.");
        }
        @Override public boolean mouseScrolled(double mx, double my, double dx, double dy) { if (list.contains(mx, my) && list.scroll(dy)) { rebuildWidgets(); return true; } return super.mouseScrolled(mx, my, dx, dy); }
        @Override public boolean mouseClicked(double mx, double my, int button) { if (button == 0 && list.beginDrag(mx, my)) { rebuildWidgets(); return true; } return super.mouseClicked(mx, my, button); }
        @Override public boolean mouseDragged(double mx, double my, int button, double dx, double dy) { if (button == 0 && list.dragging) { if (list.drag(my)) rebuildWidgets(); return true; } return super.mouseDragged(mx, my, button, dx, dy); }
        @Override public boolean mouseReleased(double mx, double my, int button) { list.release(); return super.mouseReleased(mx, my, button); }
        @Override public void render(GuiGraphics graphics, int mx, int my, float tick) {
            renderBackground(graphics, mx, my, tick); header(graphics, "Select " + parameter.kind().displayName(), "Mob Mutations > Mutator > Component settings");
            panel(graphics, panelLeft(), 39, panelWidth(), height - 84); graphics.drawString(font, parameter.kind().displayName().toUpperCase(Locale.ROOT), panelLeft() + 6, 51, 0xD8B84A, false);
            list.render(graphics); renderWidgets(graphics, mx, my, tick);
        }
    }
    private record RegistryPickerEntry(String label, String id) { }

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
                        ? "No components" : "Components: " + String.join(", ", mutator.components());
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

    private static final class LootItemWeightScreen extends EditorScreen {
        private final LootProfilesScreen parent;
        private final LootEntry entry;
        private EditBox input;
        private String error = "";

        LootItemWeightScreen(ClientModel model, LootProfilesScreen parent, LootEntry entry) {
            super("Edit Item Weight", model);
            this.parent = parent;
            this.entry = entry;
        }

        @Override protected void init() {
            clearWidgets();
            int centerX = width / 2, top = Math.max(54, height / 2 - 48);
            input = new EditBox(font, centerX - 80, top + 34, 160, 20, Component.literal("Item weight"));
            input.setMaxLength(10);
            input.setFilter(value -> value.isEmpty() || value.chars().allMatch(Character::isDigit));
            input.setValue(Integer.toString(entry.weight));
            input.setResponder(ignored -> error = "");
            input.setTooltip(Tooltip.create(Component.literal("Positive whole-number relative selection weight. Higher values make this item more likely when the profile awards an item.")));
            addRenderableWidget(input);
            input.setFocused(true);
            button(centerX - 104, top + 62, 100, "Confirm", ignored -> confirm(),
                    "Apply this relative item-selection weight to the local draft.");
            button(centerX + 4, top + 62, 100, "Cancel", ignored -> minecraft.setScreen(parent),
                    "Return without changing this item weight.");
        }

        private void confirm() {
            try {
                int weight = Integer.parseInt(input.getValue());
                if (weight < 1) { error = "Weight must be at least 1."; return; }
                if (entry.weight != weight) { entry.weight = weight; parent.draft.dirty = true; }
                minecraft.setScreen(parent);
            } catch (NumberFormatException exception) { error = "Enter a whole number from 1 to 2,147,483,647."; }
        }

        @Override public void onClose() { minecraft.setScreen(parent); }
        @Override public void render(GuiGraphics graphics, int mx, int my, float tick) {
            renderBackground(graphics, mx, my, tick);
            int centerX = width / 2, top = Math.max(54, height / 2 - 48);
            graphics.drawCenteredString(font, "Edit " + readableId(entry.item) + " weight", centerX, top, 0xFFFFFF);
            graphics.drawCenteredString(font, "Relative selection weight (positive whole number)", centerX, top + 14, 0xAAAAAA);
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

    private static final class LootProfilesScreen extends EditorScreen {
        private static final int ROW = 40;
        private static final int CONTENT_TOP = 52;
        private EditBox search, name, description, itemChance, xpMinimum, xpMaximum, xpChance, xpMultiplier;
        private String query = "", selectedId;
        private int offset;
        private final List<LootDraft> drafts = new ArrayList<>();
        private LootDraft draft;

        LootProfilesScreen(ClientModel model) {
            super("Loot", model);
            model.lootProfiles().forEach(value -> drafts.add(new LootDraft(value.getAsJsonObject())));
            drafts.sort(Comparator.comparing(value -> value.name.toLowerCase(Locale.ROOT)));
            if (!drafts.isEmpty()) select(drafts.getFirst().id);
        }

        private List<LootDraft> filtered() {
            String q = query.toLowerCase(Locale.ROOT);
            return drafts.stream().filter(value -> value.name.toLowerCase(Locale.ROOT).contains(q)
                    || value.id.toLowerCase(Locale.ROOT).contains(q)
                    || value.type.toLowerCase(Locale.ROOT).contains(q)).toList();
        }
        private void select(String id) {
            selectedId = id;
            draft = drafts.stream().filter(value -> value.id.equals(id)).findFirst().orElse(null);
        }
        @Override protected void init() {
            clearWidgets();
            int left = panelLeft(), panelW = panelWidth(), browserW = Math.max(205, panelW * 34 / 100);
            int detailX = left + browserW + 10, detailW = panelW - browserW - 10;
            search = new EditBox(font, left + 6, CONTENT_TOP + 5, browserW - 12 - 82, 20, Component.literal("Search loot profiles"));
            search.setHint(Component.literal("Search loot profiles...").withStyle(ChatFormatting.DARK_GRAY));
            search.setValue(query); search.setResponder(value -> { query = value; offset = 0; rebuildWidgets(); });
            addRenderableWidget(search);
            button(left + browserW - 82, CONTENT_TOP + 5, 76, "+ Profile", ignored -> minecraft.setScreen(new LootProfileTypeScreen(model, this)),
                    "Create a reusable Mob Loot or Chest Loot profile.");
            List<LootDraft> shown = filtered(); int listY = CONTENT_TOP + 29, available = Math.max(1, (height - 36 - listY) / ROW);
            offset = listOffset(offset, shown.size(), available);
            for (int row = 0; row < Math.min(available, shown.size() - offset); row++) {
                LootDraft value = shown.get(offset + row); boolean selected = value.id.equals(selectedId);
                SelectionRowButton button = new SelectionRowButton(left + 6, listY + row * ROW, browserW - 12,
                        ROW - 2, value.name + (value.dirty ? "  · Unsaved" : ""),
                        value.rowSummary(), selected,
                        ignored -> { select(value.id); rebuildWidgets(); }, false);
                button.withTrailingStatus(value.enabled ? "On" : "Off", value.enabled ? 0xFF66FF66 : 0xFF999999);
                if (value.dirty) button.withPrimaryColor(0xFFFFDD66);
                button.withLivePrimary(() -> value.name, () -> value.dirty);
                button.setTooltip(Tooltip.create(Component.literal(value.description.isBlank() ? value.id : value.description)));
                addRenderableWidget(button);
            }
            if (draft != null) buildDetails(detailX, detailW);
            button(left, height - 25, 80, "Back", ignored -> minecraft.setScreen(new DashboardScreen(model)), null);
            button(detailX, height - 25, 126, "Reset All Defaults", ignored -> minecraft.setScreen(new ConfirmActionScreen(model,
                    "Reset all Loot profiles?", "This removes every world-authored Loot profile change and cannot be undone.",
                    "Reset Defaults", this::resetAllLootProfiles,
                    () -> minecraft.setScreen(this))), "Restore bundled profile content.");
            Button apply = button(detailX + detailW - 322, height - 25, 100, "Apply", ignored -> apply(),
                    "Validate and apply all Loot profile changes to the server.");
            apply.active = model.editable() && drafts.stream().anyMatch(value -> value.dirty);
            boolean sourceOverride = draft != null && containsId(model.authoredLootProfiles(), draft.id);
            boolean worldAuthored = draft != null && draft.isWorldAuthored();
            String restoreLabel = sourceOverride && !worldAuthored ? "Restore Source"
                    : worldAuthored ? "Delete Profile"
                    : draft != null && draft.id.startsWith("worldawakened:defaults/") ? "Using Built-in" : "Using Source";
            Button restore = button(detailX + detailW - 214, height - 25, 100, restoreLabel,
                    ignored -> restoreSelectedProfile(), sourceOverride && !worldAuthored
                            ? "Discard this profile's world override and restore its bundled or datapack source."
                            : worldAuthored ? "Delete this world-authored Loot profile."
                            : "This profile already matches its bundled or datapack source.");
            restore.active = model.editable() && (sourceOverride || worldAuthored);
            Button discard = button(detailX + detailW - 106, height - 25, 106, "Discard Changes", ignored -> discard(),
                    "Discard local un-applied Loot edits.");
            discard.active = drafts.stream().anyMatch(value -> value.dirty);
        }
        private void buildDetails(int x, int w) {
            int innerX = x + 10, innerW = w - 20;
            name = new EditBox(font, innerX, CONTENT_TOP + 67, (innerW - 6) / 2, 20, Component.literal("Profile Name"));
            name.setValue(draft.name); name.setMaxLength(PHASE_NAME_MAX_LENGTH); name.setResponder(value -> { draft.name = value; draft.dirty = true; });
            name.setTooltip(Tooltip.create(Component.literal("Player-facing Loot profile name."))); addRenderableWidget(name);
            description = new EditBox(font, innerX, CONTENT_TOP + 93, innerW, 20, Component.literal("Description"));
            description.setValue(draft.description); description.setMaxLength(PHASE_DESCRIPTION_MAX_LENGTH);
            updateLootDescriptionTooltip();
            description.setResponder(value -> { draft.description = value; draft.dirty = true; updateLootDescriptionTooltip(); });
            addRenderableWidget(description);
            button(innerX, CONTENT_TOP + 119, (innerW - 6) / 2, "Profile Type: " + draft.typeLabel(),
                    ignored -> confirmProfileTypeSwitch(),
                    "Switch between Mob Loot and Chest Loot. This asks for confirmation before clearing incompatible settings.");
            button(innerX + (innerW + 6) / 2, CONTENT_TOP + 119, (innerW - 6) / 2, "Enabled: " + (draft.enabled ? "On" : "Off"), ignored -> { draft.enabled = !draft.enabled; draft.dirty = true; rebuildWidgets(); },
                    "Disabled profiles remain saved but do not affect gameplay.");
            int generalY = CONTENT_TOP + 149; int generalH = "mob".equals(draft.type) ? 91 : 62;
            button(innerX, generalY + 20, innerW / 2 - 3, ("mob".equals(draft.type) ? "Eligible Mobs: " : "Chest Loot Tables: ") + draft.targets.size(), ignored -> minecraft.setScreen(new LootSelectionScreen(model, this, draft, LootSelectorKind.TARGET)),
                    "Choose the exact live targets for this profile.");
            itemChance = new EditBox(font, innerX + innerW / 2 + 3, generalY + 20, innerW / 2 - 3, 20, Component.literal("Bonus Item Chance (%)"));
            itemChance.setValue(formatDecimal(draft.itemChance * 100D)); itemChance.setFilter(value -> value.matches("[0-9]{0,3}(\\.[0-9]{0,2})?"));
            itemChance.setResponder(value -> { draft.itemChance = parseBounded(value, draft.itemChance * 100D, 0D, 100D) / 100D; draft.dirty = true; });
            itemChance.setTooltip(Tooltip.create(Component.literal("Chance to add one weighted bonus item. Enter 10 for 10%."))); addRenderableWidget(itemChance);
            if ("mob".equals(draft.type)) {
                button(innerX, generalY + 46, innerW, "Only mutated mobs: " + (draft.onlyMutated ? "On" : "Off"), ignored -> { draft.onlyMutated = !draft.onlyMutated; draft.dirty = true; rebuildWidgets(); },
                        "When on, this Mob Loot profile applies only to mobs with WorldAwakened mutation provenance.");
            }
            int rewardY = generalY + generalH + 8;
            buildRewards(innerX, innerW, rewardY);
            int xpY = rewardY + rewardSectionHeight() + 12;
            if ("mob".equals(draft.type)) buildXp(xpY, innerX, innerW);
            int rulesY = xpY + ("mob".equals(draft.type) ? 98 : 0);
            buildRules(innerX, innerW, rulesY);
        }
        private void updateLootDescriptionTooltip() {
            String detail = draft.description == null || draft.description.isBlank()
                    ? "No description entered."
                    : "Full description:\n" + draft.description;
            description.setTooltip(Tooltip.create(Component.literal(
                    "Player-facing description. The full-width field keeps ordinary descriptions visible.\n\n" + detail)));
        }
        private void buildRewards(int x, int w, int y) {
            int h = 112; // fixed height keeps the whole editor stable when entries change.
            int listY = y + 28;
            button(x + w - 138, y + 4, 138, "Edit Items (" + draft.entries.size() + ")", ignored ->
                    minecraft.setScreen(new LootSelectionScreen(model, this, draft, LootSelectorKind.ITEM)),
                    "Select items. Each selected item has a relative weight and count range.");
            if (false) for (int index = 0; index < Math.min(3, draft.entries.size()); index++) {
                LootEntry entry = draft.entries.get(index); final int row = index;
                int controls = 126;
                button(x, listY + index * 24, w - controls - 4, readableId(entry.item) + " · Weight " + entry.weight + " · " + entry.minimum + "–" + entry.maximum,
                        ignored -> { entry.weight = entry.weight >= 99 ? 1 : entry.weight + 1; draft.dirty = true; rebuildWidgets(); },
                        "Click to increase this item's relative selection weight. Larger weights are more likely.");
                compactButton(x + w - controls, listY + index * 24, 20, "−", ignored -> { entry.weight = Math.max(1, entry.weight - 1); draft.dirty = true; rebuildWidgets(); }, "Lower relative item weight.");
                compactButton(x + w - 102, listY + index * 24, 20, "+", ignored -> { entry.weight++; draft.dirty = true; rebuildWidgets(); }, "Raise relative item weight.");
                compactButton(x + w - 78, listY + index * 24, 20, "×", ignored -> { draft.entries.remove(row); draft.dirty = true; rebuildWidgets(); }, "Remove this item reward.");
            }
            // Replaced by the ordered weight controls below.
            List<LootEntry> ordered = orderedRewardEntries();
            long totalWeight = totalRewardWeight();
            int visible = Math.min(3, ordered.size());
            int rowY = y + 36;
            int weightButtonWidth = Math.max(60, Math.min(96, w / 4));
            int labelWidth = Math.max(40, w - weightButtonWidth - 91);
            for (int index = 0; index < visible; index++) {
                final int entryIndex = index;
                LootEntry entry = ordered.get(index);
                int currentY = rowY + index * 30;
                int controlsX = x + labelWidth + 3;
                Button moveUp = button(controlsX, currentY + 3, 24, "↑", ignored -> swapRewardWeights(ordered, entryIndex, entryIndex - 1),
                        "Swap this item's weight with the entry above.");
                moveUp.active = index > 0;
                Button moveDown = button(controlsX + 27, currentY + 3, 24, "↓", ignored -> swapRewardWeights(ordered, entryIndex, entryIndex + 1),
                        "Swap this item's weight with the entry below.");
                moveDown.active = index < ordered.size() - 1;
                button(controlsX + 54, currentY + 3, weightButtonWidth, "Weight: " + entry.weight, ignored ->
                        minecraft.setScreen(new LootItemWeightScreen(model, this, entry)),
                        "Relative selection share: " + String.format(Locale.ROOT, "%.1f%%", rewardShare(entry.weight, totalWeight))
                                + ". Profile chance and eligibility still affect the final result.");
                button(x + w - 31, currentY + 3, 28, "x", ignored -> {
                    draft.entries.remove(entry);
                    draft.dirty = true;
                    rebuildWidgets();
                }, "Remove this item reward.");
            }
        }
        private List<LootEntry> orderedRewardEntries() {
            return draft.entries.stream().sorted(Comparator.comparingInt((LootEntry entry) -> entry.weight).reversed()).toList();
        }
        private long totalRewardWeight() {
            return draft.entries.stream().filter(entry -> entry.weight > 0).mapToLong(entry -> entry.weight).sum();
        }
        private static double rewardShare(int weight, long totalWeight) {
            return weight > 0 && totalWeight > 0 ? weight * 100.0D / totalWeight : 0.0D;
        }
        private void swapRewardWeights(List<LootEntry> entries, int first, int second) {
            if (first < 0 || second < 0 || first >= entries.size() || second >= entries.size()) return;
            LootEntry left = entries.get(first), right = entries.get(second);
            if (left.weight == right.weight) return;
            int weight = left.weight;
            left.weight = right.weight;
            right.weight = weight;
            draft.dirty = true;
            rebuildWidgets();
        }
        private int rewardSectionHeight() {
            return Math.max(48, 42 + Math.min(3, draft.entries.size()) * 30);
        }
        private void resetAllLootProfiles() {
            JsonObject root = authoringRoot(model);
            root.add("loot_profiles", new JsonArray());
            reopenLoot = true;
            PacketDistributor.sendToServer(new WorldAwakenedApplyAuthoringPayload(model.profileRevision(), root.toString()));
        }
        private void confirmProfileTypeSwitch() {
            if (draft == null) return;
            String targetType = "mob".equals(draft.type) ? "chest" : "mob";
            String targetLabel = "mob".equals(targetType) ? "Mob Loot" : "Chest Loot";
            minecraft.setScreen(new ConfirmActionScreen(model, "Switch to " + targetLabel + "?",
                    "This clears incompatible target selections and type-specific settings. Mob targets, the mutated-mob filter, and Bonus XP are cleared when switching to Chest Loot. Item rewards, quantities, weights, chances, World Phase filters, and dimension filters are kept.",
                    "Switch", () -> switchProfileType(targetType), () -> minecraft.setScreen(this)));
        }
        private void switchProfileType(String targetType) {
            if (draft == null || targetType.equals(draft.type)) {
                minecraft.setScreen(this);
                return;
            }
            int index = drafts.indexOf(draft);
            LootDraft converted = new LootDraft(convertLootProfileTypeForEditor(draft.toJson(), targetType));
            converted.dirty = true;
            drafts.set(index, converted);
            draft = converted;
            minecraft.setScreen(this);
        }
        private void restoreSelectedProfile() {
            if (draft == null) return;
            boolean worldAuthored = draft.isWorldAuthored();
            boolean sourceOverride = containsId(model.authoredLootProfiles(), draft.id);
            if (!worldAuthored && !sourceOverride) return;
            String action = worldAuthored ? "Delete" : "Restore";
            String title = worldAuthored ? "Delete Loot profile?" : "Restore source profile?";
            String message = worldAuthored
                    ? "This removes the selected world-authored Loot profile. This cannot be undone."
                    : "This removes the selected world override and reveals its bundled or datapack source.";
            minecraft.setScreen(new ConfirmActionScreen(model, title, message, action, () -> {
                JsonArray profiles = model.authoredLootProfiles().deepCopy();
                for (int index = profiles.size() - 1; index >= 0; index--) {
                    if (profiles.get(index).isJsonObject()
                            && draft.id.equals(read(profiles.get(index).getAsJsonObject(), "id", ""))) profiles.remove(index);
                }
                if (worldAuthored && !sourceOverride) {
                    drafts.removeIf(value -> value.id.equals(draft.id));
                    select(drafts.isEmpty() ? null : drafts.getFirst().id);
                    rebuildWidgets();
                    return;
                }
                JsonObject root = authoringRoot(model);
                root.add("loot_profiles", profiles);
                reopenLoot = true;
                PacketDistributor.sendToServer(new WorldAwakenedApplyAuthoringPayload(model.profileRevision(), root.toString()));
            }, () -> minecraft.setScreen(this)));
        }
        private void buildRules(int x, int w, int y) {
            Button phaseButton = button(x, y + 20, w / 2 - 3, "World Phases: " + phaseSelectionSummary(), ignored -> minecraft.setScreen(new LootSelectionScreen(model, this, draft, LootSelectorKind.PHASE)),
                    draft.stageFilterEditable
                            ? "No selection means every effective World Phase can qualify. Multiple selections use any selected active phase."
                            : draft.stageFilterDetail);
            phaseButton.active = draft.stageFilterEditable;
            button(x + w / 2 + 3, y + 20, w / 2 - 3, "Dimensions: " + selectionSummary(draft.dimensions.size()), ignored -> minecraft.setScreen(new LootSelectionScreen(model, this, draft, LootSelectorKind.DIMENSION)),
                    "No selection means every dimension can qualify. Multiple selections allow any selected dimension.");
        }
        private String phaseSelectionSummary() {
            if (draft.selectedStages.isEmpty()) return draft.stageFilterEditable ? "Any" : "Custom filter";
            return draft.selectedStages.stream().map(id -> stageName(model, id)).collect(Collectors.joining(", "));
        }
        private void buildXp(int y, int x, int w) {
            button(x, y + 20, w, "Award Bonus XP: " + (draft.xpEnabled ? "On" : "Off"), ignored -> { draft.xpEnabled = !draft.xpEnabled; draft.dirty = true; rebuildWidgets(); },
                    "Optional XP reward, independently rolled after vanilla XP is calculated. It never suppresses vanilla XP.");
            if (!draft.xpEnabled) return;
            xpMinimum = new EditBox(font, x, y + 60, w / 3 - 3, 20, Component.literal("Minimum Bonus XP")); xpMinimum.setValue(String.valueOf(draft.xpMinimum)); xpMinimum.setFilter(value -> value.matches("[0-9]{0,6}")); xpMinimum.setResponder(value -> { try { draft.xpMinimum = Integer.parseInt(value); draft.dirty = true; } catch (NumberFormatException ignored) {} }); xpMinimum.setTooltip(Tooltip.create(Component.literal("Lowest additional XP awarded on a successful roll. Enter a whole number, zero or higher."))); addRenderableWidget(xpMinimum);
            xpMaximum = new EditBox(font, x + w / 3 + 3, y + 60, w / 3 - 3, 20, Component.literal("Maximum Bonus XP")); xpMaximum.setValue(String.valueOf(draft.xpMaximum)); xpMaximum.setFilter(value -> value.matches("[0-9]{0,6}")); xpMaximum.setResponder(value -> { try { draft.xpMaximum = Integer.parseInt(value); draft.dirty = true; } catch (NumberFormatException ignored) {} }); xpMaximum.setTooltip(Tooltip.create(Component.literal("Highest additional XP awarded on a successful roll. Must be at least Minimum Bonus XP."))); addRenderableWidget(xpMaximum);
            xpChance = new EditBox(font, x + 2 * w / 3 + 6, y + 60, w / 3 - 6, 20, Component.literal("Bonus XP Chance (%)")); xpChance.setValue(formatDecimal(draft.xpChance * 100D)); xpChance.setFilter(value -> value.matches("[0-9]{0,3}(\\.[0-9]{0,2})?")); xpChance.setResponder(value -> { draft.xpChance = parseBounded(value, draft.xpChance * 100D, 0D, 100D) / 100D; draft.dirty = true; }); xpChance.setTooltip(Tooltip.create(Component.literal("Chance to award the configured bonus XP. Enter 50 for 50%."))); addRenderableWidget(xpChance);
        }
        private void createProfile(String type) {
            String id = "worldawakened:world/loot_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            LootDraft value = LootDraft.create(id); value.type = type; drafts.add(value); select(id); rebuildWidgets();
        }
        private void discard() {
            boolean dirty = drafts.stream().anyMatch(value -> value.dirty);
            if (!dirty) { minecraft.setScreen(new DashboardScreen(model)); return; }
            minecraft.setScreen(new ConfirmActionScreen(model, "Discard unsaved Loot changes?",
                    "All local Loot profile edits since opening this editor will be lost.", "Discard",
                    () -> minecraft.setScreen(new LootProfilesScreen(model)), () -> minecraft.setScreen(this)));
        }
        private void apply() {
            if (drafts.stream().anyMatch(value -> value.name.isBlank())) { showLocalError("Each Loot profile needs a name."); return; }
            JsonObject root = authoringRoot(model);
            JsonArray values = new JsonArray(); drafts.forEach(value -> values.add(value.toJson())); root.add("loot_profiles", values);
            reopenLoot = true;
            PacketDistributor.sendToServer(new WorldAwakenedApplyAuthoringPayload(model.profileRevision(), root.toString()));
        }
        private String phaseLabel(String id) { return id.isBlank() ? "Any phase" : stageName(model, id); }
        private static String selectionSummary(int count) { return count == 0 ? "Any" : count + " selected"; }
        private String cycleStage(String current) { List<String> ids = new ArrayList<>(); ids.add(""); model.stages().forEach(stage -> ids.add(stage.id())); return ids.get((ids.indexOf(current) + 1 + ids.size()) % ids.size()); }
        private String cycleDimension(String current) { List<String> ids = new ArrayList<>(); ids.add(""); ids.addAll(model.dimensions()); return ids.get((ids.indexOf(current) + 1 + ids.size()) % ids.size()); }
        @Override public boolean mouseScrolled(double mx, double my, double dx, double dy) { List<LootDraft> shown = filtered(); int available = Math.max(1, (height - 36 - (CONTENT_TOP + 29)) / ROW); int before = offset; offset = listOffset(offset + (dy < 0 ? 1 : -1), shown.size(), available); if (before != offset) { rebuildWidgets(); return true; } return super.mouseScrolled(mx, my, dx, dy); }
        @Override public void render(GuiGraphics graphics, int mx, int my, float tick) {
            renderBackground(graphics, mx, my, tick); header(graphics, "Loot", "Profile Editor > Loot");
            int left = panelLeft(), w = panelWidth(), browserW = Math.max(205, w * 34 / 100); panel(graphics, left, CONTENT_TOP, browserW, height - CONTENT_TOP - 31); panel(graphics, left + browserW + 10, CONTENT_TOP, w - browserW - 10, height - CONTENT_TOP - 31);
            List<LootDraft> shown = filtered();
            int listY = CONTENT_TOP + 29;
            int available = Math.max(1, (height - 36 - listY) / ROW);
            if (shown.size() > available) renderScrollbar(graphics, left + browserW - 8, listY,
                    available * ROW - 2, shown.size(), available, offset);
            if (draft != null) {
                int x = left + browserW + 20, detailW = w - browserW - 30;
                graphics.drawString(font, Component.literal(draft.name).withStyle(ChatFormatting.BOLD), x, CONTENT_TOP + 4, 0xFFFFFF, false);
                graphics.drawString(font, draft.profileSubtitle(), x, CONTENT_TOP + 18, 0xAAAAAA, false);
                sectionOutline(graphics, x - 4, CONTENT_TOP + 33, detailW + 8, "mob".equals(draft.type) ? 203 : 174);
                graphics.drawString(font, "GENERAL", x, CONTENT_TOP + 37, 0xFFFFAA00, false);
                graphics.drawString(font, "Profile Name", x, CONTENT_TOP + 55, 0xAAAAAA, false);
                graphics.drawString(font, "Description", x, CONTENT_TOP + 81, 0xAAAAAA, false);
                int generalY = CONTENT_TOP + 149;
                graphics.drawString(font, "Bonus Item Chance (%)", x + detailW / 2 + 3, generalY + 12, 0xAAAAAA, false);
                int rewardY = generalY + ("mob".equals(draft.type) ? 91 : 62) + 8;
                int rewardHeight = rewardSectionHeight();
                sectionOutline(graphics, x - 4, rewardY, detailW + 8, rewardHeight);
                graphics.drawString(font, "ITEM REWARDS", x, rewardY + 5, 0xFFFFAA00, false);
                drawMarqueeLeft(graphics, font, Component.literal("Higher entries have a larger relative selection share."),
                        x, rewardY + 18, Math.max(30, detailW - 146), 0xAAAAAA);
                renderRewardRows(graphics, x, detailW, rewardY + 36);
                int xpY = rewardY + rewardHeight + 12;
                if ("mob".equals(draft.type)) {
                    sectionOutline(graphics, x - 4, xpY, detailW + 8, 90);
                    graphics.drawString(font, "BONUS XP", x, xpY + 5, 0xFFFFAA00, false);
                    if (draft.xpEnabled) {
                        graphics.drawString(font, "Minimum Bonus XP", x, xpY + 50, 0xAAAAAA, false);
                        graphics.drawString(font, "Maximum Bonus XP", x + detailW / 3 + 3, xpY + 50, 0xAAAAAA, false);
                        graphics.drawString(font, "Bonus XP Chance (%)", x + 2 * detailW / 3 + 6, xpY + 50, 0xAAAAAA, false);
                    }
                }
                int rulesY = xpY + ("mob".equals(draft.type) ? 98 : 0); sectionOutline(graphics, x - 4, rulesY, detailW + 8, 52); graphics.drawString(font, "AVAILABILITY", x, rulesY + 5, 0xFFFFAA00, false);
            }
            renderWidgets(graphics, mx, my, tick);
        }
        private void renderRewardRows(GuiGraphics graphics, int x, int width, int startY) {
            List<LootEntry> entries = orderedRewardEntries();
            long totalWeight = totalRewardWeight();
            int rowWidth = width;
            int weightButtonWidth = Math.max(60, Math.min(96, rowWidth / 4));
            int labelWidth = Math.max(40, rowWidth - weightButtonWidth - 91);
            for (int index = 0; index < Math.min(3, entries.size()); index++) {
                LootEntry entry = entries.get(index);
                int rowY = startY + index * 30;
                graphics.fill(x, rowY, x + rowWidth, rowY + 28, 0xB02A2A2A);
                graphics.fill(x, rowY, x + rowWidth, rowY + 1, 0xFF606060);
                graphics.fill(x, rowY + 27, x + rowWidth, rowY + 28, 0xFF383838);
                graphics.fill(x, rowY, x + 1, rowY + 28, 0xFF606060);
                graphics.fill(x + rowWidth - 1, rowY, x + rowWidth, rowY + 28, 0xFF383838);
                String summary = (index + 1) + ". " + readableId(entry.item) + "   "
                        + String.format(Locale.ROOT, "%.1f%%", rewardShare(entry.weight, totalWeight))
                        + "   " + entry.minimum + "–" + entry.maximum;
                drawMarqueeLeft(graphics, font, Component.literal(summary), x + 7, rowY + 9,
                        Math.max(30, labelWidth - 10), 0xFFFFFF);
            }
        }
        private static String itemPreview(LootDraft value) {
            if (value.entries.isEmpty()) return "No item rewards selected.";
            int total = value.entries.stream().mapToInt(entry -> entry.weight).sum();
            if (total <= 0) return "Item weights need review.";
            LootEntry first = value.entries.getFirst();
            double selected = value.itemChance * first.weight / total * 100D;
            double none = (1D - value.itemChance) * 100D;
            return "Expected: " + formatDecimal(none) + "% no item; " + readableId(first.item)
                    + " " + formatDecimal(selected) + "% (eligible events only).";
        }
    }

    private static final class LootProfileTypeScreen extends EditorScreen {
        private final LootProfilesScreen parent;
        LootProfileTypeScreen(ClientModel model, LootProfilesScreen parent) { super("Create Loot Profile", model); this.parent = parent; }
        @Override protected void init() {
            clearWidgets(); int w=Math.min(420,width-24), x=(width-w)/2;
            button(x,height/2-24,w,"Mob Loot", ignored->{parent.createProfile("mob");minecraft.setScreen(parent);}, "Adds items and optional bonus XP when an eligible mob dies.");
            button(x,height/2+2,w,"Chest Loot", ignored->{parent.createProfile("chest");minecraft.setScreen(parent);}, "Adds items when an eligible chest loot table is generated.");
            button(width/2-50,height-25,100,"Cancel",ignored->minecraft.setScreen(parent),null);
        }
        @Override public void render(GuiGraphics graphics,int mx,int my,float tick){renderBackground(graphics,mx,my,tick);header(graphics,"Create Loot Profile","Loot > Choose Profile Type");panel(graphics,(width-Math.min(420,width-24))/2,height/2-54,Math.min(420,width-24),90);graphics.drawCenteredString(font,"Choose the runtime target for this profile.",width/2,height/2-44,0xBBBBBB);renderWidgets(graphics,mx,my,tick);}
    }

    private enum LootSelectorKind { TARGET, ITEM, PHASE, DIMENSION }

    private static final class LootSelectionScreen extends EditorScreen {
        private final LootProfilesScreen parent; private final LootDraft draft; private final LootSelectorKind kind;
        private final Set<String> selected = new LinkedHashSet<>(); private final ScrollableRows list = new ScrollableRows();
        private EditBox search; private String query = "";
        LootSelectionScreen(ClientModel model, LootProfilesScreen parent, LootDraft draft, LootSelectorKind kind) {
            super(kind == LootSelectorKind.ITEM ? "Select Loot Items" : "Select Loot Targets", model); this.parent = parent; this.draft = draft; this.kind = kind;
            if (kind == LootSelectorKind.TARGET) selected.addAll(draft.targets);
            else if (kind == LootSelectorKind.ITEM) draft.entries.forEach(entry -> selected.add(entry.item));
            else if (kind == LootSelectorKind.PHASE) selected.addAll(draft.selectedStages);
            else selected.addAll(draft.dimensions);
        }
        private List<ItemModel> itemValues() { String q=query.toLowerCase(Locale.ROOT); return model.items().stream().filter(value -> value.name().toLowerCase(Locale.ROOT).contains(q)||value.id().toLowerCase(Locale.ROOT).contains(q)).toList(); }
        private List<EntityModel> mobValues() { String q=query.toLowerCase(Locale.ROOT); return model.entities().stream().filter(value -> value.name().toLowerCase(Locale.ROOT).contains(q)||value.id().toLowerCase(Locale.ROOT).contains(q)).toList(); }
        private List<String> chestValues() { String q=query.toLowerCase(Locale.ROOT); return model.chestLootTables().stream().filter(value -> value.toLowerCase(Locale.ROOT).contains(q)||readableId(value).toLowerCase(Locale.ROOT).contains(q)).toList(); }
        @Override protected void init() {
            clearWidgets(); int left=panelLeft(), w=panelWidth(), top=39, bottom=height-45;
            search=new EditBox(font,left+6,top+18,w-12,20,Component.literal("Search")); search.setHint(Component.literal(kind==LootSelectorKind.ITEM?"Search items...":"Search names or IDs...").withStyle(ChatFormatting.DARK_GRAY)); search.setValue(query); search.setResponder(value->{query=value;list.offset=0;rebuildWidgets();});addRenderableWidget(search);setInitialFocus(search);setFocused(search);
            List<String> ids = ids(); list.configure(left+6,top+43,w-12,bottom-top-48,36,ids.size());
            for(int row=0;row<Math.min(list.visible,Math.max(0,ids.size()-list.offset));row++){String id=ids.get(list.offset+row); boolean checked=selected.contains(id); String name=display(id); SelectionRowButton entry=new SelectionRowButton(list.x,list.y+row*36,list.rowWidth(),34,name,id,checked,ignored->{if(!selected.add(id))selected.remove(id);rebuildWidgets();});entry.setTooltip(Tooltip.create(Component.literal(id)));addRenderableWidget(entry);}
            button(width/2-104,height-25,100,"Confirm",ignored->{if(kind==LootSelectorKind.TARGET){draft.targets.clear();draft.targets.addAll(selected);}else if(kind==LootSelectorKind.ITEM){Map<String,LootEntry> existing=new LinkedHashMap<>();draft.entries.forEach(value->existing.put(value.item,value));draft.entries.clear();selected.forEach(id->draft.entries.add(existing.getOrDefault(id,new LootEntry(id,1,1,1))));}else if(kind==LootSelectorKind.PHASE){draft.selectedStages.clear();draft.selectedStages.addAll(selected);draft.stagesEdited=true;}else{draft.dimensions.clear();draft.dimensions.addAll(selected);draft.dimensionsEdited=true;}draft.dirty=true;minecraft.setScreen(parent);},null);button(width/2+4,height-25,100,"Cancel",ignored->minecraft.setScreen(parent),null);
        }
        private List<String> ids(){if(kind==LootSelectorKind.ITEM)return itemValues().stream().map(ItemModel::id).toList();if(kind==LootSelectorKind.PHASE)return model.stages().stream().map(StageModel::id).toList();if(kind==LootSelectorKind.DIMENSION)return model.dimensions();if("mob".equals(draft.type))return mobValues().stream().map(EntityModel::id).toList();return chestValues();}
        private String display(String id){if(kind==LootSelectorKind.ITEM)return itemValues().stream().filter(v->v.id().equals(id)).map(ItemModel::name).findFirst().orElse(readableId(id));if(kind==LootSelectorKind.PHASE)return stageName(model,id);if(kind==LootSelectorKind.DIMENSION)return readableId(id);if("mob".equals(draft.type))return mobValues().stream().filter(v->v.id().equals(id)).map(EntityModel::name).findFirst().orElse(readableId(id));return readableId(id);}
        @Override public boolean mouseScrolled(double mx,double my,double dx,double dy){if(list.contains(mx,my)&&list.scroll(dy)){rebuildWidgets();return true;}return super.mouseScrolled(mx,my,dx,dy);}
        @Override public boolean mouseClicked(double mx,double my,int button){if(button==0&&list.beginDrag(mx,my)){rebuildWidgets();return true;}return super.mouseClicked(mx,my,button);}
        @Override public boolean mouseDragged(double mx,double my,int button,double dx,double dy){if(button==0&&list.dragging){if(list.drag(my))rebuildWidgets();return true;}return super.mouseDragged(mx,my,button,dx,dy);}
        @Override public boolean mouseReleased(double mx,double my,int button){list.release();return super.mouseReleased(mx,my,button);}
        @Override public void onClose(){minecraft.setScreen(parent);}
        @Override public void render(GuiGraphics graphics,int mx,int my,float tick){renderBackground(graphics,mx,my,tick);String title=kind==LootSelectorKind.ITEM?"Select Loot Items":kind==LootSelectorKind.PHASE?"Select World Phases":kind==LootSelectorKind.DIMENSION?"Select Dimensions":"Select Loot Targets";header(graphics,title,"Loot > "+draft.name);panel(graphics,panelLeft(),39,panelWidth(),height-84);graphics.drawString(font,kind==LootSelectorKind.ITEM?"ITEMS":kind==LootSelectorKind.PHASE?"WORLD PHASES":kind==LootSelectorKind.DIMENSION?"DIMENSIONS":"TARGETS",panelLeft()+6,45,0xD8B84A,false);graphics.drawString(font,selected.size()+" selected",panelLeft(),height-39,0xBBBBBB,false);list.render(graphics);renderWidgets(graphics,mx,my,tick);}
    }

    private static JsonObject authoringRoot(ClientModel model) {
        JsonObject root = new JsonObject(); root.add("stages", model.authoredStages().deepCopy()); root.add("trigger_rules", model.authoredTriggers().deepCopy()); root.add("mutation_pools", model.authoredPools().deepCopy()); root.add("phase_mutation_configurations", model.tracks().deepCopy()); root.add("ascension_offers", model.authoredOffers().deepCopy()); root.add("ascension_rewards", model.authoredRewards().deepCopy()); root.add("mob_mutators", model.authoredMutators().deepCopy()); return root;
    }
    static JsonObject convertLootProfileTypeForEditor(JsonObject source, String targetType) {
        if (!"mob".equals(targetType) && !"chest".equals(targetType)) {
            throw new IllegalArgumentException("Unsupported Loot profile type: " + targetType);
        }
        JsonObject result = source.deepCopy();
        JsonObject authoring = result.has("authoring") && result.get("authoring").isJsonObject()
                ? result.getAsJsonObject("authoring").deepCopy() : new JsonObject();
        result.addProperty("profile_type", targetType);
        result.remove("target_loot_tables");
        authoring.remove("target_entities");
        if ("chest".equals(targetType)) {
            authoring.remove("bonus_xp");
            if (result.has("conditions") && result.get("conditions").isJsonArray()) {
                JsonArray retained = new JsonArray();
                for (JsonElement condition : result.getAsJsonArray("conditions")) {
                    if (condition.isJsonObject()
                            && read(condition.getAsJsonObject(), "type", "").endsWith("entity_is_mutated")) continue;
                    retained.add(condition);
                }
                result.add("conditions", retained);
            }
        }
        result.add("authoring", authoring);
        return result;
    }
    private static double parseBounded(String value, double fallback, double minimum, double maximum) { try { double parsed=Double.parseDouble(value); return Double.isFinite(parsed)?Math.max(minimum,Math.min(maximum,parsed)):fallback;}catch(NumberFormatException ignored){return fallback;} }
    private static String formatDecimal(double value) { return String.format(Locale.ROOT, "%.3f", value).replaceAll("0+$", "").replaceAll("\\.$", ""); }
    private static final class LootEntry { final String item; int weight, minimum, maximum; LootEntry(String item,int weight,int minimum,int maximum){this.item=item;this.weight=weight;this.minimum=minimum;this.maximum=maximum;} }
    private static final class LootDraft {
        final String id; String type="mob",name,description="",stageFilterDetail="Custom World Phase filter is preserved and cannot be edited by the simplified selector."; boolean enabled=true,onlyMutated=false,dirty=false,xpEnabled=false,xpMultiplier=false,stagesEdited=false,dimensionsEdited=false,stageFilterEditable=true; double itemChance=1D,xpChance=1D,xpMultiplierValue=1D; int xpMinimum=0,xpMaximum=0; final Set<String> targets=new LinkedHashSet<>(); final Set<String> selectedStages=new LinkedHashSet<>(); final Set<String> dimensions=new LinkedHashSet<>(); final List<LootEntry> entries=new ArrayList<>(); final JsonObject source;
        LootDraft(JsonObject json){source=json.deepCopy();id=read(json,"id","");enabled=!json.has("enabled")||json.get("enabled").getAsBoolean();type=read(json,"profile_type","mob");JsonObject authoring=json.has("authoring")&&json.get("authoring").isJsonObject()?json.getAsJsonObject("authoring"):new JsonObject();name=read(authoring,"display_name",readableId(id));description=read(authoring,"description","");itemChance=authoring.has("item_chance")?authoring.get("item_chance").getAsDouble():1D;if("mob".equals(type)&&authoring.has("target_entities"))authoring.getAsJsonArray("target_entities").forEach(v->targets.add(v.getAsString()));if("chest".equals(type)&&json.has("target_loot_tables"))json.getAsJsonArray("target_loot_tables").forEach(v->targets.add(v.getAsString()));if(json.has("entries"))json.getAsJsonArray("entries").forEach(v->{JsonObject e=v.getAsJsonObject();entries.add(new LootEntry(read(e,"item","minecraft:iron_nugget"),e.has("weight")?e.get("weight").getAsInt():1,e.has("min")?e.get("min").getAsInt():1,e.has("max")?e.get("max").getAsInt():1));});if(authoring.has("bonus_xp")&&authoring.get("bonus_xp").isJsonObject()){JsonObject xp=authoring.getAsJsonObject("bonus_xp");xpEnabled=!xp.has("enabled")||xp.get("enabled").getAsBoolean();xpMultiplier="multiplier".equals(read(xp,"mode","extra"));xpMinimum=xp.has("min")?xp.get("min").getAsInt():0;xpMaximum=xp.has("max")?xp.get("max").getAsInt():xpMinimum;xpMultiplierValue=xp.has("multiplier")?xp.get("multiplier").getAsDouble():1;xpChance=xp.has("chance")?xp.get("chance").getAsDouble():1;}readConditions(json);}
        static LootDraft create(String id){JsonObject json=new JsonObject();json.addProperty("id",id);json.addProperty("profile_type","mob");JsonObject authoring=new JsonObject();authoring.addProperty("display_name","New Loot Profile");json.add("authoring",authoring);return new LootDraft(json);}
        void readConditions(JsonObject json){if(json.has("authoring")&&json.get("authoring").isJsonObject()){JsonObject authoring=json.getAsJsonObject("authoring");if(authoring.has("eligible_dimensions")&&authoring.get("eligible_dimensions").isJsonArray())authoring.getAsJsonArray("eligible_dimensions").forEach(value->dimensions.add(value.getAsString()));}if(json.has("conditions"))for(JsonElement raw:json.getAsJsonArray("conditions")){if(!raw.isJsonObject())continue;String type=read(raw.getAsJsonObject(),"type","");if(type.endsWith("entity_is_mutated"))onlyMutated=true;if(type.endsWith("stage_unlocked")||type.endsWith("stage_locked")){stageFilterEditable=false;stageFilterDetail="Legacy World Phase conditions are preserved and cannot be edited by the simplified selector.";}}if(json.has("stage_filters")&&json.get("stage_filters").isJsonObject())readStageFilter(json.getAsJsonObject("stage_filters"));}
        private void readStageFilter(JsonObject filters){
            Set<String> supported=Set.of("required_any","any_of","one_of");
            boolean simple=filters.entrySet().stream().allMatch(entry->supported.contains(entry.getKey()));
            Set<String> positiveStageLists=Set.of("required_any","any_of","one_of","all_of","required_all","stages","required_stages");
            for(var entry:filters.entrySet()){
                if(!positiveStageLists.contains(entry.getKey()))continue;
                JsonElement value=entry.getValue();
                if(!value.isJsonArray()){simple=false;continue;}
                for(JsonElement phase:value.getAsJsonArray()){
                    if(phase.isJsonPrimitive())selectedStages.add(phase.getAsString());
                    else simple=false;
                }
            }
            if(!simple){stageFilterEditable=false;stageFilterDetail="Custom World Phase filter is preserved and cannot be edited by the simplified selector.";}
        }
        String typeLabel(){return "chest".equals(type)?"Chest Loot":"Mob Loot";}
        boolean isWorldAuthored(){return id.startsWith("worldawakened:world/");}
        String rowSummary(){return typeLabel()+" · "+targets.size()+("mob".equals(type)?" mobs":" loot tables");}
        String profileSubtitle(){
            if (dirty) return "World override \u00b7 Unsaved changes";
            return isWorldAuthored() ? "World-authored profile" : "Bundled or datapack source";
        }
        String listSummary(){return typeLabel()+" · "+targets.size()+("mob".equals(type)?" mobs":" loot tables")+(dirty?" · Unsaved":"")+(enabled?" · On":" · Off");}
        JsonObject toJson(){JsonObject value=source.deepCopy();value.remove("target_loot_tables");value.addProperty("schema_version",1);value.addProperty("id",id);value.addProperty("enabled",enabled);value.addProperty("profile_type",type);value.addProperty("replace_mode","inject");JsonObject authoring=new JsonObject();authoring.addProperty("display_name",name);authoring.addProperty("description",description);authoring.addProperty("item_chance",itemChance);JsonArray targetArray=new JsonArray();targets.forEach(targetArray::add);if("mob".equals(type))authoring.add("target_entities",targetArray);else value.add("target_loot_tables",targetArray);JsonArray dimensionArray=new JsonArray();dimensions.forEach(dimensionArray::add);authoring.add("eligible_dimensions",dimensionArray);if("mob".equals(type)&&xpEnabled){JsonObject xp=new JsonObject();xp.addProperty("enabled",true);xp.addProperty("mode","extra");xp.addProperty("min",xpMinimum);xp.addProperty("max",xpMaximum);xp.addProperty("multiplier",1D);xp.addProperty("chance",xpChance);authoring.add("bonus_xp",xp);}value.add("authoring",authoring);JsonArray itemEntries=new JsonArray();entries.forEach(entry->{JsonObject e=new JsonObject();e.addProperty("type","item");e.addProperty("item",entry.item);e.addProperty("weight",entry.weight);e.addProperty("min",entry.minimum);e.addProperty("max",entry.maximum);itemEntries.add(e);});value.add("entries",itemEntries);if(stagesEdited){if(selectedStages.isEmpty())value.remove("stage_filters");else{JsonObject filters=new JsonObject();JsonArray any=new JsonArray();selectedStages.forEach(any::add);filters.add("required_any",any);value.add("stage_filters",filters);}}return value;}
    }

    private static final class GlobalSettingsScreen extends EditorScreen {
        private boolean defaults, mutations, multiple, events, ascension; private double difficulty, mutationFrequency, eventFrequency; private int maximum; private net.sprocketgames.worldawakened.progression.WorldAwakenedProgressionMode progressionMode;
        GlobalSettingsScreen(ClientModel model) { super("Global Settings", model); load(model.settings()); }
        private void load(WorldAwakenedEditorSettings s) { defaults=s.defaultGameplayEnabled(); difficulty=s.difficultyIntensity(); mutations=s.mutationsEnabled(); mutationFrequency=s.mutationFrequencyMultiplier(); multiple=s.allowMultipleMutations(); maximum=s.maximumMutationsPerMob(); events=s.pressureEventsEnabled(); eventFrequency=s.eventFrequencyMultiplier(); ascension=s.ascensionEnabled(); progressionMode=s.progressionMode(); }
        @Override protected void init() {
            clearWidgets(); int w = Math.min(520, width - 20); int left=(width-w)/2; int col=(w-8)/2; int right=left+col+8;
            button(left, 42, col, toggle("Default gameplay", defaults), b->{defaults=!defaults; rebuildWidgets();}, "Enables bundled Standard definitions; external datapacks remain active.");
            ValueSlider intensity = addRenderableWidget(new ValueSlider(left,66,col,"Difficulty intensity",difficulty,model.difficultyMin(),model.difficultyMax(),.05,v->difficulty=v,true)); intensity.active=model.editable()&&model.difficultyAvailable();
            button(left,90,col,"Progression: "+readableMode(progressionMode.serializedName()),b->{progressionMode=progressionMode==net.sprocketgames.worldawakened.progression.WorldAwakenedProgressionMode.PER_PLAYER ? net.sprocketgames.worldawakened.progression.WorldAwakenedProgressionMode.PER_SERVER : net.sprocketgames.worldawakened.progression.WorldAwakenedProgressionMode.PER_PLAYER; rebuildWidgets();},"Per Player keeps phases independent and automatically shares them with FTB Teams members when installed. Per Server shares every phase. Changing mode is blocked after phase progress exists.");
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
        private void apply() { PacketDistributor.sendToServer(new WorldAwakenedApplyConfigPayload(model.settingsRevision(), new WorldAwakenedEditorSettings(defaults,difficulty,mutations,mutationFrequency,multiple,maximum,events,eventFrequency,ascension,progressionMode).toJson().toString())); }
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

    private static final class EditorScrollbar extends AbstractWidget {
        private final int total, visible;
        private final Consumer<Integer> consumer;
        private int offset;
        EditorScrollbar(int x, int y, int width, int height, int total, int visible, int offset,
                Consumer<Integer> consumer) {
            super(x, y, width, height, Component.literal("Scroll list"));
            this.total = Math.max(1, total); this.visible = Math.max(1, visible);
            this.offset = listOffset(offset, total, visible); this.consumer = consumer;
        }
        @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0x70000000);
            int thumbH = Math.max(12, getHeight() * visible / total);
            int travel = Math.max(1, getHeight() - thumbH);
            int maximum = Math.max(1, total - visible);
            int thumbY = getY() + travel * offset / maximum;
            graphics.fill(getX() + 1, thumbY, getX() + getWidth() - 1, thumbY + thumbH,
                    isHoveredOrFocused() ? 0xFFD0D0D0 : 0xFF9A9A9A);
        }
        @Override public void onClick(double mouseX, double mouseY) { update(mouseY); }
        @Override protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) { update(mouseY); }
        private void update(double mouseY) {
            int thumbH = Math.max(12, getHeight() * visible / total);
            double fraction = Math.max(0.0D, Math.min(1.0D,
                    (mouseY - getY() - thumbH / 2.0D) / Math.max(1.0D, getHeight() - thumbH)));
            int next = (int)Math.round(fraction * Math.max(0, total - visible));
            if (next != offset) { offset = next; consumer.accept(next); }
        }
        @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
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

    private static void drawScaledMarqueeLeft(
            GuiGraphics graphics,
            Font font,
            Component text,
            int x,
            int y,
            int availableWidth,
            int color,
            float scale) {
        int scaledTextWidth = Math.round(font.width(text) * scale);
        int overflow = Math.max(0, scaledTextWidth - availableWidth);
        double offset = 0.0D;
        if (overflow > 0) {
            long startPause = 1_200L;
            long scrollDuration = Math.max(600L, Math.round(overflow / 28.0D * 1_000.0D));
            long endPause = 1_000L;
            long elapsed = Math.floorMod(Util.getMillis(), startPause + scrollDuration + endPause);
            if (elapsed >= startPause && elapsed < startPause + scrollDuration) {
                double progress = (elapsed - startPause) / (double)scrollDuration;
                progress = progress * progress * (3.0D - 2.0D * progress);
                offset = overflow * progress;
            } else if (elapsed >= startPause + scrollDuration) offset = overflow;
            graphics.enableScissor(x, y - 1, x + availableWidth,
                    y + Math.round(font.lineHeight * scale) + 1);
        }
        graphics.pose().pushPose();
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, text, (int)Math.round((x - offset) / scale), Math.round(y / scale), color, true);
        graphics.pose().popPose();
        if (overflow > 0) graphics.disableScissor();
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
    private static String mutatorTooltip(MutatorModel mutator) { return mutator==null?"Definition unavailable":"Stacking: "+mutator.stackingGroup()+"\nComponents: "+String.join(", ",mutator.components())+"\nWeight is relative, not final spawn probability."; }
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
    private static boolean offerEnabled(JsonObject definition) {
        return !definition.has("enabled") || definition.get("enabled").getAsBoolean();
    }
    private static String offerPhaseId(JsonObject definition) {
        if (!definition.has("stage_filters") || !definition.get("stage_filters").isJsonObject()) return null;
        JsonObject filters = definition.getAsJsonObject("stage_filters");
        if (!filters.has("all_of") || !filters.get("all_of").isJsonArray() || filters.getAsJsonArray("all_of").size() != 1) return null;
        return filters.getAsJsonArray("all_of").get(0).getAsString();
    }
    private static String availabilitySummary(ClientModel model, JsonObject definition) {
        String phase = offerPhaseId(definition);
        if (phase != null) return "World Phase · " + stageName(model, phase);
        return "Custom conditions";
    }
    private static List<String> offerRewardIds(JsonObject definition) {
        List<String> result = new ArrayList<>();
        if (definition.has("candidate_rewards") && definition.get("candidate_rewards").isJsonArray())
            definition.getAsJsonArray("candidate_rewards").forEach(value -> result.add(value.getAsString()));
        return List.copyOf(result);
    }
    private static boolean containsId(JsonArray values, String id) {
        for (JsonElement value : values) if (value.isJsonObject() && id.equals(read(value.getAsJsonObject(), "id", ""))) return true;
        return false;
    }
    private static String conditionName(int type) {
        return switch (type) { case 1 -> "World day"; case 2 -> "Enter dimension";
            case 3 -> "Advancement"; case 4 -> "Defeat mob"; default -> "Manual"; };
    }
    private static boolean grantsOffer(JsonObject trigger, String offerId) {
        if (!trigger.has("actions") || !trigger.get("actions").isJsonArray()) return false;
        for (JsonElement raw : trigger.getAsJsonArray("actions")) {
            if (!raw.isJsonObject()) continue; JsonObject action = raw.getAsJsonObject();
            if (!read(action, "type", "").endsWith("grant_ascension_offer") || !action.has("parameters")) continue;
            JsonObject parameters = action.getAsJsonObject("parameters");
            if (parameters.has("offer") && offerId.equals(parameters.get("offer").getAsString())) return true;
        }
        return false;
    }
    private static JsonArray removeOfferTriggers(JsonArray source, String offerId) {
        JsonArray result = new JsonArray();
        source.forEach(value -> { if (!value.isJsonObject() || !grantsOffer(value.getAsJsonObject(), offerId)) result.add(value.deepCopy()); });
        return result;
    }
    private static void readOfferConditions(ClientModel model, String offerId, List<UnlockConditionDraft> target) {
        for (JsonElement raw : model.triggerRules()) {
            if (!raw.isJsonObject()) continue; JsonObject trigger = raw.getAsJsonObject();
            if (!grantsOffer(trigger, offerId)) continue;
            UnlockConditionDraft condition = simpleUnlockCondition(trigger);
            if (condition != null && target.size() < 3) target.add(condition);
        }
    }
    private static JsonArray buildOfferTriggers(String offerId, List<UnlockConditionDraft> conditions, boolean requireAll) {
        JsonArray result = new JsonArray();
        List<String> tokens = conditions.stream().map(WorldAwakenedClientConfigPayloadHandler::conditionToken).toList();
        List<String> canonical = new ArrayList<>(tokens); canonical.sort(String::compareTo);
        String mode = requireAll ? "all" : "any";
        String groupToken = stableToken(offerId + "|offer|" + mode + "|" + String.join("|", canonical));
        for (int index = 0; index < conditions.size(); index++) {
            UnlockConditionDraft condition = conditions.get(index);
            JsonObject trigger = new JsonObject(); trigger.addProperty("schema_version", 1);
            trigger.addProperty("id", offerId + "_offer_" + tokens.get(index)); trigger.addProperty("enabled", true);
            trigger.addProperty("priority", 90);
            String event = switch (condition.type) { case 1 -> "world_day_milestone"; case 2 -> "player_enter_dimension";
                case 3 -> "advancement_completed"; case 4 -> "entity_killed"; default -> "manual_debug"; };
            trigger.addProperty("trigger_type", "worldawakened:" + event); trigger.addProperty("source_scope", "player");
            JsonArray triggerConditions = new JsonArray();
            if (condition.type == 1) triggerConditions.add(node("world_day_gte", "value", Integer.parseInt(condition.target)));
            else if (condition.type == 2) triggerConditions.add(node("current_dimension", "dimension", condition.target));
            else if (condition.type == 3) triggerConditions.add(node("advancement_completed", "advancement", condition.target));
            else if (condition.type == 4) triggerConditions.add(node("entity_type", "entity", condition.target));
            trigger.add("conditions", triggerConditions);
            JsonArray actions = new JsonArray();
            if (conditions.size() > 1) {
                JsonObject gate = node("record_unlock_condition", "group_token", groupToken);
                JsonObject parameters = gate.getAsJsonObject("parameters"); parameters.addProperty("condition_token", tokens.get(index));
                JsonArray all = new JsonArray(); canonical.forEach(all::add); parameters.add("condition_tokens", all); parameters.addProperty("mode", mode);
                actions.add(gate);
            }
            actions.add(node("grant_ascension_offer", "offer", offerId)); trigger.add("actions", actions);
            trigger.addProperty("one_shot", conditions.size() == 1); result.add(trigger);
        }
        return result;
    }
    private static void sendAscensionAuthoring(ClientModel model, JsonArray triggers, JsonArray offers, JsonArray rewards) {
        JsonObject root = new JsonObject(); root.add("stages", model.authoredStages().deepCopy());
        root.add("trigger_rules", triggers.deepCopy()); root.add("mutation_pools", model.authoredPools().deepCopy());
        root.add("phase_mutation_configurations", model.tracks().deepCopy()); root.add("ascension_offers", offers.deepCopy());
        root.add("ascension_rewards", rewards.deepCopy());
        root.add("mob_mutators", model.authoredMutators().deepCopy());
        root.add("loot_profiles", model.authoredLootProfiles().deepCopy());
        reopenAscension = true;
        PacketDistributor.sendToServer(new WorldAwakenedApplyAuthoringPayload(model.profileRevision(), root.toString()));
    }
    private static String readableMode(String value){return "per_player".equals(value) ? "Per Player" : "Per Server";}
    private static void sendAuthoring(ClientModel model, JsonArray stages, JsonArray triggers,
            JsonArray pools, JsonArray tracks) {
        JsonObject root = new JsonObject();
        root.add("stages", stages.deepCopy()); root.add("trigger_rules", triggers.deepCopy());
        root.add("mutation_pools", pools.deepCopy()); root.add("phase_mutation_configurations", tracks.deepCopy());
        root.add("ascension_offers", model.authoredOffers().deepCopy());
        root.add("ascension_rewards", model.authoredRewards().deepCopy());
        root.add("mob_mutators", model.authoredMutators().deepCopy());
        root.add("loot_profiles", model.authoredLootProfiles().deepCopy());
        reopenProgression = true;
        PacketDistributor.sendToServer(new WorldAwakenedApplyAuthoringPayload(model.profileRevision(), root.toString()));
    }

    private static WorldAwakenedPhaseMutationIndex.Index phaseIndex(ClientModel model) {
        Map<String, JsonObject> pools = new LinkedHashMap<>();
        model.pools().forEach(pool -> pools.put(pool.id(), pool.definition()));
        return WorldAwakenedPhaseMutationIndex.build(model.stages().stream().map(StageModel::id).toList(),
                pools, model.tracks());
    }

    private static void sendTracks(ClientModel model, JsonArray tracks, JsonArray pools, String reopenPool) {
        Set<String> availablePoolIds = new LinkedHashSet<>();
        model.pools().forEach(pool -> availablePoolIds.add(pool.id()));
        pools.forEach(pool -> {
            if (pool.isJsonObject() && pool.getAsJsonObject().has("id"))
                availablePoolIds.add(pool.getAsJsonObject().get("id").getAsString());
        });
        JsonArray repairedTracks = WorldAwakenedEditorTrackJson.withoutMissingPoolReferences(tracks, availablePoolIds);
        JsonObject root = new JsonObject(); root.add("stages", model.authoredStages().deepCopy());
        root.add("trigger_rules", model.authoredTriggers().deepCopy()); root.add("mutation_pools", pools.deepCopy());
        root.add("phase_mutation_configurations", repairedTracks);
        root.add("ascension_offers", model.authoredOffers().deepCopy());
        root.add("ascension_rewards", model.authoredRewards().deepCopy());
        root.add("mob_mutators", model.authoredMutators().deepCopy());
        root.add("loot_profiles", model.authoredLootProfiles().deepCopy());
        if (reopenPool != null) reopenPoolAfterAuthoring = reopenPool; else reopenTracks = true;
        PacketDistributor.sendToServer(new WorldAwakenedApplyAuthoringPayload(model.profileRevision(), root.toString()));
    }

    private static List<JsonObject> trackObjects(ClientModel model) {
        List<JsonObject> result = new ArrayList<>(); model.tracks().forEach(value -> result.add(value.getAsJsonObject()));
        result.sort(Comparator.comparing(value -> read(value, "display_name", read(value, "id", "")))); return result;
    }

    private static JsonObject track(ClientModel model, String id) {
        return trackObjects(model).stream().filter(track -> id.equals(read(track, "id", ""))).findFirst().orElse(null);
    }

    private static Set<String> trackedPoolIds(ClientModel model) {
        Set<String> ids = new LinkedHashSet<>();
        for (JsonObject track : trackObjects(model)) WorldAwakenedEditorTrackJson.tiers(track)
                .forEach(value -> ids.add(value.getAsJsonObject().get("pool_id").getAsString()));
        return ids;
    }

    private static StageModel stage(ClientModel model, String id) {
        return model.stages().stream().filter(stage -> stage.id().equals(id)).findFirst().orElse(null);
    }

    private static String stageName(ClientModel model, String id) {
        StageModel stage = stage(model, id);
        return stage == null ? readableId(id) : stage.name();
    }

    private static String trackContext(JsonObject track) {
        JsonArray dimensions = track.has("eligible_dimensions") ? track.getAsJsonArray("eligible_dimensions") : new JsonArray();
        String where = dimensions.isEmpty() ? "Any configured dimension" : dimensions.size() == 1
                ? readableId(dimensions.get(0).getAsString()) : dimensions.size() + " dimensions";
        return where + " • " + ((!track.has("enabled") || track.get("enabled").getAsBoolean()) ? "Enabled" : "Disabled");
    }

    private static void removeTier(ClientModel model, JsonObject original, String stageId, String poolId) {
        JsonObject track = original.deepCopy(); JsonArray tiers = new JsonArray();
        for (JsonElement value : WorldAwakenedEditorTrackJson.tiers(track)) {
            JsonObject tier = value.getAsJsonObject();
            if (!stageId.equals(tier.get("stage_id").getAsString()) || !poolId.equals(tier.get("pool_id").getAsString()))
                tiers.add(tier.deepCopy());
        }
        track.add("tiers", tiers); JsonArray pools = removeById(model.authoredPools(), poolId);
        sendTracks(model, replaceById(model.tracks(), track), pools, null);
    }

    private static JsonArray append(JsonArray source, JsonObject value) {
        JsonArray result = source.deepCopy(); result.add(value.deepCopy()); return result;
    }

    private static String uniqueTrackId(ClientModel model, String name) {
        String base = "worldawakened:world/tracks/" + slug(name), candidate = base; int suffix = 2;
        Set<String> ids = new LinkedHashSet<>(); trackObjects(model).forEach(track -> ids.add(read(track, "id", "")));
        while (ids.contains(candidate)) candidate = base + "_" + suffix++;
        return candidate;
    }

    private static String uniquePoolId(ClientModel model, String base) {
        String candidate = base; int suffix = 2;
        Set<String> ids = new LinkedHashSet<>(); model.pools().forEach(pool -> ids.add(pool.id()));
        while (ids.contains(candidate)) candidate = base + "_" + suffix++;
        return candidate;
    }

    private static JsonObject phaseTrack(ClientModel model) {
        JsonObject existing = track(model, WorldAwakenedPhaseMutationIndex.PHASE_TRACK_ID);
        if (existing != null) return existing.deepCopy();
        JsonObject track = new JsonObject();
        track.addProperty("id", WorldAwakenedPhaseMutationIndex.PHASE_TRACK_ID);
        track.addProperty("display_name", "World Phase Mob Configurations");
        track.addProperty("description", "Native editor phase and dimension mapping.");
        track.addProperty("enabled", true);
        track.add("eligible_dimensions", new JsonArray());
        track.add("tiers", new JsonArray());
        return track;
    }

    private static void upsertPhaseTier(JsonObject track, String stageId, String dimensionId, String name,
            String poolId, boolean defaultConfiguration, boolean active) {
        JsonArray tiers = WorldAwakenedEditorTrackJson.mutableTiers(track);
        JsonObject replacement = new JsonObject(); replacement.addProperty("stage_id", stageId);
        replacement.addProperty("pool_id", poolId); replacement.addProperty("dimension", dimensionId);
        replacement.addProperty("configuration_name", name);
        replacement.addProperty("default_configuration", defaultConfiguration);
        replacement.addProperty("active", active);
        for (int index = 0; index < tiers.size(); index++) {
            JsonObject tier = tiers.get(index).getAsJsonObject();
            if (poolId.equals(read(tier, "pool_id", ""))) { tiers.set(index, replacement); return; }
        }
        tiers.add(replacement);
    }

    private static PhaseAuthoring synchronizePhaseDimensions(ClientModel model, String stageId,
            Set<String> selectedDimensions) {
        WorldAwakenedPhaseMutationIndex.Index index = phaseIndex(model);
        JsonObject track = phaseTrack(model); JsonArray pools = model.authoredPools().deepCopy();
        Set<String> existingDimensions = new LinkedHashSet<>();
        for (WorldAwakenedPhaseMutationIndex.Configuration configuration : index.configurations(stageId)) {
            if (configuration.dimensionId().isBlank()) continue;
            existingDimensions.add(configuration.dimensionId());
            if (configuration.legacyReview()) continue;
            upsertPhaseTier(track, stageId, configuration.dimensionId(), configuration.name(), configuration.poolId(),
                    configuration.defaultConfiguration(), selectedDimensions.contains(configuration.dimensionId()));
        }
        for (String dimension : selectedDimensions) {
            if (existingDimensions.contains(dimension)) continue;
            String base = "worldawakened:world/phases/" + slug(readableId(stageId)) + "/"
                    + slug(readableId(dimension)) + "/default_mobs";
            String poolId = uniquePoolId(model, base);
            JsonObject pool = defaultPool(poolId, track, model.mutators().isEmpty() ? null : model.mutators().getFirst().id());
            pool.addProperty("enabled", false); pools = replaceById(pools, pool);
            upsertPhaseTier(track, stageId, dimension, "Default Mobs", poolId, true, true);
        }
        return new PhaseAuthoring(pools, replaceById(model.tracks(), track));
    }

    private record PhaseAuthoring(JsonArray pools, JsonArray tracks) {}

    private static String slug(String name) {
        String value = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        return value.isBlank() ? "entry" : value;
    }

    static JsonObject defaultPool(String id, JsonObject track, String starterMutator) {
        JsonObject pool = new JsonObject(); pool.addProperty("schema_version", 1); pool.addProperty("id", id);
        pool.addProperty("enabled", true); pool.addProperty("weight", 1);
        pool.addProperty("mutation_chance", 0.05D); pool.addProperty("additional_mutation_chance", 0.0D);
        pool.addProperty("max_mutators_per_entity", 1); pool.addProperty("combination_mode", "any_compatible");
        pool.add("rare_combinations", new JsonArray());
        pool.addProperty("allow_from_spawner", false); pool.addProperty("allow_from_trial_spawner", false);
        pool.add("conditions", new JsonArray());
        pool.add("eligible_dimensions", track.has("eligible_dimensions") ? track.getAsJsonArray("eligible_dimensions").deepCopy() : new JsonArray());
        pool.add("eligible_biomes", new JsonArray()); pool.add("eligible_entities", new JsonArray());
        JsonArray mutators = new JsonArray();
        if (starterMutator != null) { JsonObject entry = new JsonObject(); entry.addProperty("id", starterMutator);
            entry.addProperty("weight", 1); mutators.add(entry); }
        pool.add("mutators", mutators); return pool;
    }

    private static void sendMutatorAuthoring(ClientModel model, JsonArray mutators, String reopenPool) {
        JsonObject root = new JsonObject();
        root.add("stages", model.authoredStages().deepCopy());
        root.add("trigger_rules", model.authoredTriggers().deepCopy());
        root.add("mutation_pools", model.authoredPools().deepCopy());
        root.add("phase_mutation_configurations", model.tracks().deepCopy());
        root.add("ascension_offers", model.authoredOffers().deepCopy());
        root.add("ascension_rewards", model.authoredRewards().deepCopy());
        root.add("mob_mutators", mutators.deepCopy());
        root.add("loot_profiles", model.authoredLootProfiles().deepCopy());
        reopenPoolAfterAuthoring = reopenPool;
        PacketDistributor.sendToServer(new WorldAwakenedApplyAuthoringPayload(model.profileRevision(), root.toString()));
    }

    private static void sendPoolAndMutatorAuthoring(ClientModel model, JsonArray pools, JsonArray mutators, String reopenPool) {
        JsonObject root = new JsonObject(); root.add("stages", model.authoredStages().deepCopy());
        root.add("trigger_rules", model.authoredTriggers().deepCopy()); root.add("mutation_pools", pools.deepCopy());
        root.add("phase_mutation_configurations", model.tracks().deepCopy()); root.add("ascension_offers", model.authoredOffers().deepCopy());
        root.add("ascension_rewards", model.authoredRewards().deepCopy()); root.add("mob_mutators", mutators.deepCopy());
        root.add("loot_profiles", model.authoredLootProfiles().deepCopy());
        reopenPoolAfterAuthoring = reopenPool;
        PacketDistributor.sendToServer(new WorldAwakenedApplyAuthoringPayload(model.profileRevision(), root.toString()));
    }

    private static MutatorModel mutatorModel(JsonObject definition, boolean authored) {
        String id = definition.get("id").getAsString(); List<String> components = new ArrayList<>();
        if (definition.has("components")) definition.getAsJsonArray("components").forEach(value ->
                components.add(readableId(value.getAsJsonObject().get("type").getAsString())));
        Set<String> exclusive = new LinkedHashSet<>(); if (definition.has("exclusive_with")) definition.getAsJsonArray("exclusive_with").forEach(value -> exclusive.add(value.getAsString()));
        return new MutatorModel(id, jsonText(definition.get("display_name"), readableId(id)),
                read(definition, "rarity", "common"), read(definition, "stacking_group", "none"),
                definition.has("max_stack_count") ? definition.get("max_stack_count").getAsInt() : 1,
                Set.copyOf(exclusive), List.copyOf(components), definition.deepCopy(), authored);
    }

    private static JsonObject defaultMutator(String id, String name, String template, double amount) {
        JsonObject result = new JsonObject(); result.addProperty("schema_version", 1); result.addProperty("id", id);
        result.add("display_name", textJson(name)); result.addProperty("enabled", true); result.addProperty("rarity", "uncommon");
        result.addProperty("weight", 1); result.addProperty("max_stack_count", 1);
        JsonArray components = new JsonArray(); JsonObject component = new JsonObject();
        component.addProperty("type", "worldawakened:" + template); JsonObject parameters = new JsonObject();
        parameters.addProperty(template.endsWith("multiplier") ? "multiplier" : "amount", amount);
        component.add("parameters", parameters); components.add(component); result.add("components", components);
        result.add("eligible_entities", new JsonArray()); result.add("eligible_entity_tags", new JsonArray());
        result.add("excluded_entities", new JsonArray()); result.add("excluded_entity_tags", new JsonArray());
        result.add("required_conditions", new JsonArray()); result.addProperty("applies_to_bosses", false);
        result.addProperty("applies_to_invaders", false); JsonArray contexts = new JsonArray(); contexts.add("on_spawn"); result.add("application_contexts", contexts);
        return result;
    }

    private static JsonObject textJson(String value) { JsonObject text = new JsonObject(); text.addProperty("text", value); return text; }
    private static String componentTemplate(JsonObject definition) {
        if (definition.has("type")) {
            String type = definition.get("type").getAsString();
            return type.contains(":") ? type.substring(type.indexOf(':') + 1) : type;
        }
        if (definition.has("components") && !definition.getAsJsonArray("components").isEmpty()) {
            String type = read(definition.getAsJsonArray("components").get(0).getAsJsonObject(), "type", "worldawakened:max_health_bonus");
            String path = type.contains(":") ? type.substring(type.indexOf(':') + 1) : type;
            if (List.of("max_health_bonus", "armor_bonus", "attack_damage_multiplier", "movement_speed_multiplier").contains(path)) return path;
        }
        return "max_health_bonus";
    }
    private static String componentAmount(JsonObject definition, String template) {
        if (definition.has("components") && !definition.getAsJsonArray("components").isEmpty()) {
            JsonObject parameters = definition.getAsJsonArray("components").get(0).getAsJsonObject().getAsJsonObject("parameters");
            if (parameters != null) return read(parameters, template.endsWith("multiplier") ? "multiplier" : "amount", defaultAmount(template));
        }
        return defaultAmount(template);
    }
    private static String defaultAmount(String template) { return switch (template) {
        case "attack_damage_multiplier", "movement_speed_multiplier" -> "1.1";
        case "armor_bonus" -> "2"; default -> "4";
    }; }
    private static String nextTemplate(String template) { List<String> values = List.of("max_health_bonus", "armor_bonus", "attack_damage_multiplier", "movement_speed_multiplier"); return values.get((values.indexOf(template) + 1) % values.size()); }
    private static List<String> mutatorEffectOptions() { return List.of(
            "max_health_bonus", "max_health_multiplier", "attack_damage_bonus", "attack_damage_multiplier",
            "armor_bonus", "armor_multiplier", "armor_toughness_bonus", "movement_speed_bonus",
            "movement_speed_multiplier", "follow_range_bonus", "knockback_resistance_bonus", "target_range_bonus",
            "wall_sense", "pursuit_speed_boost", "anti_kite_behavior", "debuff_resistance", "damage_type_resistance",
            "temporary_shield", "equip_item", "projectile_resistance", "on_hit_effect", "life_steal",
            "reinforcement_summon", "summon_cooldown", "summon_cap", "death_spawn", "burst_movement",
            "projectile_modifier", "fire_package", "frost_package", "lightning_package", "poison_package",
            "damage_aura", "death_explosion", "retaliation_thorns", "glow_style", "effect_particles", "ambient_particles"); }

    private static String mutatorEffectCategory(String type) { return switch (type) {
        case "max_health_bonus", "max_health_multiplier", "attack_damage_bonus", "attack_damage_multiplier",
                "armor_bonus", "armor_multiplier", "armor_toughness_bonus", "movement_speed_bonus",
                "movement_speed_multiplier", "follow_range_bonus", "knockback_resistance_bonus", "target_range_bonus" -> "STATS AND DEFENCE";
        case "wall_sense", "pursuit_speed_boost", "anti_kite_behavior", "burst_movement" -> "MOB BEHAVIOUR";
        case "debuff_resistance", "damage_type_resistance", "temporary_shield", "projectile_resistance",
                "on_hit_effect", "life_steal", "damage_aura", "death_explosion", "retaliation_thorns" -> "COMBAT";
        case "reinforcement_summon", "summon_cooldown", "summon_cap", "death_spawn" -> "SUMMONING";
        case "equip_item" -> "EQUIPMENT";
        case "projectile_modifier", "fire_package", "frost_package", "lightning_package", "poison_package" -> "ELEMENTAL PACKAGES";
        default -> "VISUALS";
    }; }

    /** Components may have a valid datapack schema before they have a safe runtime handler. */
    private static boolean isLiveMutatorComponent(String type) {
        return switch (type) {
            case "max_health_bonus", "max_health_multiplier", "attack_damage_bonus", "attack_damage_multiplier",
                    "armor_bonus", "armor_multiplier", "armor_toughness_bonus", "movement_speed_bonus",
                    "movement_speed_multiplier", "follow_range_bonus", "target_range_bonus",
                    "knockback_resistance_bonus", "pursuit_speed_boost", "temporary_shield", "equip_item",
                    "on_hit_effect", "life_steal", "death_explosion", "glow_style", "effect_particles",
                    "ambient_particles" -> true;
            default -> false;
        };
    }

    private static String directParameter(String type) {
        return switch (type) {
            case "max_health_bonus", "attack_damage_bonus", "armor_bonus", "armor_toughness_bonus", "movement_speed_bonus",
                    "follow_range_bonus", "knockback_resistance_bonus", "target_range_bonus", "pursuit_speed_boost",
                    "debuff_resistance", "temporary_shield", "projectile_resistance", "burst_movement", "damage_aura",
                    "retaliation_thorns", "life_steal", "death_explosion" -> "amount";
            case "max_health_multiplier", "attack_damage_multiplier", "armor_multiplier", "movement_speed_multiplier" -> "multiplier";
            default -> null;
        };
    }

    /** Concise author-facing unit help for the scalar field shown directly beside a component. */
    private static String directAmountTooltip(String type) {
        return switch (type) {
            case "max_health_bonus" -> "Adds this many maximum-health points. 2 points = one heart.";
            case "attack_damage_bonus" -> "Adds this many raw attack-damage points.";
            case "armor_bonus" -> "Adds this many armour points.";
            case "armor_toughness_bonus" -> "Adds this many armour-toughness points.";
            case "movement_speed_bonus" -> "Adds raw movement-speed attribute units; this is not a percentage.";
            case "follow_range_bonus", "target_range_bonus" -> "Adds this many blocks of target/follow range.";
            case "knockback_resistance_bonus" -> "Adds raw knockback resistance. 1.0 means complete resistance.";
            case "pursuit_speed_boost" -> "Adds raw movement-speed attribute units; this is not a percentage. 0.018 means a zombie with 0.23 base speed becomes about 0.248 (roughly 8% faster).";
            case "temporary_shield" -> "Adds this many absorption-health points. 2 points = one absorption heart.";
            case "debuff_resistance" -> "Numeric debuff-resistance value. This component is not available in live gameplay yet.";
            case "projectile_resistance" -> "Numeric projectile-resistance value. This component is not available in live gameplay yet.";
            case "burst_movement" -> "Numeric burst-movement value. This component is not available in live gameplay yet.";
            case "damage_aura" -> "Raw damage dealt by the aura. This component is not available in live gameplay yet.";
            case "retaliation_thorns" -> "Raw damage returned to an attacker. This component is not available in live gameplay yet.";
            case "life_steal" -> "Fraction of post-mitigation damage healed. 0.25 heals 25% of damage dealt. Valid range: greater than 0 to 1.";
            case "death_explosion" -> "Explosion power, not a percentage. Valid range: greater than 0 to 8; block damage remains disabled.";
            case "max_health_multiplier", "attack_damage_multiplier", "armor_multiplier", "movement_speed_multiplier" -> "Multiplier of the original attribute. 1.0 is unchanged; 1.08 means 8% more. Must be greater than 0.";
            default -> "Enter a number for this component. The server validates it when you apply changes.";
        };
    }

    /** Help for fields in a component's detailed Configure screen. */
    private static String componentParameterTooltip(String type, MutatorParameter parameter) {
        if (type.equals("equip_item") && parameter.key().equals("item")) {
            return "Choose the item to equip. For armour and body slots, the picker shows only items whose vanilla equipment data fits the selected slot.";
        }
        if (type.equals("equip_item") && parameter.key().equals("slot")) {
            return "Choose where the item is placed. Automatic uses the item's normal equipment slot, or Main Hand when it has none.";
        }
        if (type.equals("equip_item") && parameter.key().equals("drop_chance")) {
            return "Chance that this equipped item drops when the mob dies. 0 = never, 0.5 = 50%, 1 = always.";
        }
        return switch (parameter.key()) {
            case "damage_type" -> "Enter a damage-type ID, for example minecraft:generic. This component is not available in live gameplay yet.";
            case "entity_tag" -> "Optional entity tag ID, for example minecraft:undead. Leave blank to use the chosen entity instead.";
            case "effect", "effect_type" -> "Choose the status effect. Effect particles use its colour only; they do not apply the effect.";
            case "particle" -> "Choose the particle to emit. Only particle types supported by the live visual emitter are listed.";
            case "entity" -> "Choose the entity type to summon or spawn. This component is not available in live gameplay yet.";
            case "duration_ticks" -> "Duration in game ticks. 20 ticks = one second. Valid range: 1 to 12,000.";
            case "amplifier" -> "Potion amplifier: 0 is level I, 1 is level II. Valid range: 0 to 255.";
            case "chance" -> "Chance per eligible hit. 0 = never, 0.5 = 50%, 1 = always.";
            case "seconds" -> "Seconds between summon attempts. Must be greater than 0. This component is not available in live gameplay yet.";
            case "max" -> "Maximum simultaneous summoned reinforcements. Enter a whole number of at least 1. This component is not available in live gameplay yet.";
            case "color" -> type.equals("effect_particles")
                    ? "Optional #RRGGBB particle-colour override. Leave blank to use the selected effect's normal colour."
                    : type.equals("ambient_particles")
                    ? "Optional #RRGGBB dust colour. Used only with minecraft:dust; leave blank for its default red."
                    : "Glow colour in #RRGGBB format, for example #66ff66.";
            case "brightness" -> "Glow brightness from 0 (dim) to 1 (bright). Values outside this range are clamped.";
            case "see_through_walls" -> "Enter true to show this glow through walls, or false to hide it behind walls.";
            case "pulse" -> "Enter true to make the glow pulse, or false to keep it steady.";
            case "pulse_speed" -> "Pulse cycles per second. Valid range: 0.25 to 3.0.";
            case "pulse_strength" -> "How much glow brightness changes during a pulse. Valid range: 0.05 to 0.35.";
            case "count" -> "Particles emitted each time. Enter a whole number from 1 to 32; higher counts cost more client rendering work.";
            case "interval_ticks" -> "Ticks between emissions. 20 ticks = one second. Enter a whole number from 1 to 200; higher values emit less often.";
            case "offset_x", "offset_y", "offset_z" -> "Particle spread from the mob centre on this axis, in blocks. Valid range: 0 to 4.";
            case "speed" -> "Particle motion speed. 0 keeps particles still; valid range: 0 to 4.";
            case "size" -> "Dust-particle size. Used only with minecraft:dust; valid range: 0.01 to 4.";
            default -> parameter.kind() == ParameterKind.NUMBER
                    ? "Enter the numeric value used by this component. The server validates it when you apply changes."
                    : "Enter the value used by this component.";
        };
    }

    private static JsonObject defaultMutatorParameters(String type) {
        JsonObject parameters = new JsonObject();
        String direct = directParameter(type);
        if (direct != null) { parameters.addProperty(direct, defaultAmount(type)); return parameters; }
        switch (type) {
            case "damage_type_resistance" -> parameters.addProperty("damage_type", "minecraft:generic");
            case "equip_item" -> { parameters.addProperty("item", "minecraft:iron_sword"); parameters.addProperty("slot", "auto"); parameters.addProperty("drop_chance", 0.0D); }
            case "on_hit_effect" -> { parameters.addProperty("effect", "minecraft:poison"); parameters.addProperty("duration_ticks", 100); parameters.addProperty("amplifier", 0); parameters.addProperty("chance", 1.0D); }
            case "reinforcement_summon", "death_spawn" -> parameters.addProperty("entity", "minecraft:zombie");
            case "summon_cooldown" -> parameters.addProperty("seconds", 10D);
            case "summon_cap" -> parameters.addProperty("max", 1);
            case "effect_particles" -> { parameters.addProperty("effect_type", "minecraft:poison"); parameters.addProperty("count", 4); parameters.addProperty("interval_ticks", 12); }
            case "ambient_particles" -> { parameters.addProperty("particle", "minecraft:flame"); parameters.addProperty("count", 4); parameters.addProperty("offset_x", .25D); parameters.addProperty("offset_y", .4D); parameters.addProperty("offset_z", .25D); parameters.addProperty("speed", .01D); parameters.addProperty("interval_ticks", 12); }
            default -> { }
        }
        return parameters;
    }

    /** Lists only registry values that the selected component can safely consume at runtime. */
    private static List<RegistryPickerEntry> registryPickerEntries(ParameterKind kind, String query, String equipmentSlot) {
        if (kind == ParameterKind.EQUIPMENT_SLOT) {
            String needle = query == null ? "" : query.toLowerCase(Locale.ROOT);
            return List.of(
                            new RegistryPickerEntry("Automatic", "auto"),
                            new RegistryPickerEntry("Main Hand", "mainhand"),
                            new RegistryPickerEntry("Off Hand", "offhand"),
                            new RegistryPickerEntry("Head", "head"),
                            new RegistryPickerEntry("Chest", "chest"),
                            new RegistryPickerEntry("Legs", "legs"),
                            new RegistryPickerEntry("Feet", "feet"),
                            new RegistryPickerEntry("Body", "body"))
                    .stream()
                    .filter(entry -> (entry.label() + " " + entry.id()).toLowerCase(Locale.ROOT).contains(needle))
                    .toList();
        }
        List<ResourceLocation> ids = switch (kind) {
            case MOB_EFFECT -> new ArrayList<>(BuiltInRegistries.MOB_EFFECT.keySet());
            case ITEM -> BuiltInRegistries.ITEM.keySet().stream()
                    .filter(id -> itemFitsEquipmentSlot(id, equipmentSlot))
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
            case ENTITY -> new ArrayList<>(BuiltInRegistries.ENTITY_TYPE.keySet());
            case PARTICLE -> BuiltInRegistries.PARTICLE_TYPE.keySet().stream()
                    .filter(id -> {
                        ParticleType<?> type = BuiltInRegistries.PARTICLE_TYPE.getOptional(id).orElse(null);
                        return type instanceof SimpleParticleType || id.equals(ResourceLocation.withDefaultNamespace("dust"));
                    }).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
            default -> List.of();
        };
        String needle = query == null ? "" : query.toLowerCase(Locale.ROOT);
        return ids.stream().sorted(Comparator.comparing(ResourceLocation::toString))
                .map(ResourceLocation::toString)
                .filter(id -> (readableId(id) + " " + id).toLowerCase(Locale.ROOT).contains(needle))
                .map(id -> new RegistryPickerEntry(readableId(id), id)).toList();
    }

    /**
     * Mirrors the equipment component used by the runtime's automatic-slot resolver.  A held item
     * may be anything, while armour/body selections are restricted to items whose vanilla
     * {@link Equipable} data declares that exact slot.
     */
    private static boolean itemFitsEquipmentSlot(ResourceLocation id, String requestedSlot) {
        String slot = requestedSlot == null ? "auto" : requestedSlot.toLowerCase(Locale.ROOT);
        if (slot.equals("auto") || slot.equals("mainhand") || slot.equals("main_hand")
                || slot.equals("offhand") || slot.equals("off_hand")) {
            return true;
        }
        Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        if (item == null) return false;
        Equipable equipable = Equipable.get(new ItemStack(item));
        if (equipable == null) return false;
        return switch (slot) {
            case "head", "helmet" -> equipable.getEquipmentSlot() == EquipmentSlot.HEAD;
            case "chest", "chestplate" -> equipable.getEquipmentSlot() == EquipmentSlot.CHEST;
            case "legs", "leggings" -> equipable.getEquipmentSlot() == EquipmentSlot.LEGS;
            case "feet", "boots" -> equipable.getEquipmentSlot() == EquipmentSlot.FEET;
            case "body" -> equipable.getEquipmentSlot() == EquipmentSlot.BODY;
            default -> false;
        };
    }

    private static List<MutatorParameter> mutatorParameterSpecs(String type) {
        return switch (type) {
            case "damage_type_resistance" -> List.of(new MutatorParameter("Damage type", "damage_type", "minecraft:generic", ParameterKind.TEXT));
            case "equip_item" -> List.of(new MutatorParameter("Item", "item", "minecraft:iron_sword", ParameterKind.ITEM), new MutatorParameter("Slot", "slot", "auto", ParameterKind.EQUIPMENT_SLOT), new MutatorParameter("Drop chance", "drop_chance", "0", ParameterKind.NUMBER));
            case "on_hit_effect" -> List.of(new MutatorParameter("Effect", "effect", "minecraft:poison", ParameterKind.MOB_EFFECT), new MutatorParameter("Duration (ticks)", "duration_ticks", "100", ParameterKind.NUMBER), new MutatorParameter("Amplifier", "amplifier", "0", ParameterKind.NUMBER), new MutatorParameter("Chance (0–1)", "chance", "1", ParameterKind.NUMBER));
            case "reinforcement_summon", "death_spawn" -> List.of(new MutatorParameter("Entity", "entity", "minecraft:zombie", ParameterKind.ENTITY), new MutatorParameter("Entity tag (optional)", "entity_tag", "", ParameterKind.TEXT));
            case "summon_cooldown" -> List.of(new MutatorParameter("Seconds", "seconds", "10", ParameterKind.NUMBER));
            case "summon_cap" -> List.of(new MutatorParameter("Maximum summons", "max", "1", ParameterKind.NUMBER));
            case "glow_style" -> List.of(new MutatorParameter("Colour (#RRGGBB)", "color", "#66ff66", ParameterKind.TEXT), new MutatorParameter("Brightness (0–1)", "brightness", "1", ParameterKind.NUMBER), new MutatorParameter("See through walls", "see_through_walls", "false", ParameterKind.BOOLEAN), new MutatorParameter("Pulse", "pulse", "false", ParameterKind.BOOLEAN), new MutatorParameter("Pulse speed", "pulse_speed", "1", ParameterKind.NUMBER), new MutatorParameter("Pulse strength", "pulse_strength", "0.12", ParameterKind.NUMBER));
            case "effect_particles" -> List.of(new MutatorParameter("Effect", "effect_type", "minecraft:poison", ParameterKind.MOB_EFFECT), new MutatorParameter("Colour (#RRGGBB, optional)", "color", "", ParameterKind.TEXT), new MutatorParameter("Count", "count", "4", ParameterKind.NUMBER), new MutatorParameter("Interval (ticks)", "interval_ticks", "12", ParameterKind.NUMBER));
            case "ambient_particles" -> List.of(new MutatorParameter("Particle", "particle", "minecraft:flame", ParameterKind.PARTICLE), new MutatorParameter("Colour (#RRGGBB, dust only)", "color", "", ParameterKind.TEXT), new MutatorParameter("Size (dust only)", "size", "", ParameterKind.NUMBER), new MutatorParameter("Count", "count", "4", ParameterKind.NUMBER), new MutatorParameter("Offset X", "offset_x", "0.25", ParameterKind.NUMBER), new MutatorParameter("Offset Y", "offset_y", "0.4", ParameterKind.NUMBER), new MutatorParameter("Offset Z", "offset_z", "0.25", ParameterKind.NUMBER), new MutatorParameter("Speed", "speed", "0.01", ParameterKind.NUMBER), new MutatorParameter("Interval (ticks)", "interval_ticks", "12", ParameterKind.NUMBER));
            default -> List.of();
        };
    }
    private static String effectDescription(String type) { return switch (type) {
        case "max_health_bonus" -> "Adds maximum health.";
        case "max_health_multiplier" -> "Multiplies maximum health.";
        case "attack_damage_bonus" -> "Adds attack damage.";
        case "attack_damage_multiplier" -> "Multiplies attack damage.";
        case "armor_bonus", "armor_multiplier" -> "Adds or multiplies armour.";
        case "armor_toughness_bonus" -> "Adds armour toughness.";
        case "movement_speed_bonus", "movement_speed_multiplier" -> "Adds or multiplies movement speed.";
        case "follow_range_bonus", "target_range_bonus" -> "Increases detection range.";
        case "knockback_resistance_bonus" -> "Reduces knockback.";
        case "pursuit_speed_boost" -> "Adds speed while pursuing a target.";
        case "debuff_resistance" -> "Reduces incoming debuff duration.";
        case "temporary_shield" -> "Adds temporary absorption shielding.";
        case "wall_sense" -> "Lets the mob sense targets through walls.";
        case "anti_kite_behavior" -> "Improves pursuit against retreating targets.";
        case "damage_type_resistance" -> "Reduces damage from one chosen damage type.";
        case "equip_item" -> "Equips a registered item, with an optional slot and drop chance.";
        case "on_hit_effect" -> "Applies a chosen status effect when the mob hits.";
        case "life_steal" -> "Heals the mob for a portion of inflicted damage.";
        case "reinforcement_summon" -> "Allows the mob to call a chosen reinforcement.";
        case "summon_cooldown", "summon_cap" -> "Limits how often or how many reinforcements can be summoned.";
        case "death_spawn" -> "Spawns a chosen entity when this mob dies.";
        case "projectile_resistance" -> "Reduces projectile damage.";
        case "projectile_modifier" -> "Modifies projectile behaviour.";
        case "fire_package", "frost_package", "lightning_package", "poison_package" -> "Applies an elemental combat package.";
        case "burst_movement" -> "Gives the mob sudden movement bursts.";
        case "damage_aura" -> "Damages nearby targets.";
        case "death_explosion" -> "Creates a bounded explosion when the mob dies.";
        case "retaliation_thorns" -> "Returns damage to attackers.";
        case "glow_style" -> "Controls the mutation glow colour and pulse.";
        case "effect_particles" -> "Emits particles coloured by a chosen status effect; it does not apply that effect.";
        case "ambient_particles" -> "Emits a chosen particle type for atmosphere; it does not apply a status effect.";
        default -> "Applies this mutation component.";
    }; }
    private static String uniqueMutatorId(ClientModel model, String name) {
        String base = "worldawakened:world/mutators/" + slug(name), candidate = base; int suffix = 2;
        Set<String> ids = new LinkedHashSet<>(); model.mutators().forEach(mutator -> ids.add(mutator.id()));
        while (ids.contains(candidate)) candidate = base + "_" + suffix++;
        return candidate;
    }

    private static int listOffset(int value, int total, int visible) {
        return Math.max(0, Math.min(value, Math.max(0, total - visible)));
    }

    static double poolChance(JsonObject pool) {
        if (pool.has("mutation_chance") && pool.get("mutation_chance").isJsonPrimitive())
            return pool.get("mutation_chance").getAsDouble();
        return 1.0D;
    }

    private static void sectionOutline(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + 1, 0xFF666666);
        graphics.fill(x, y + height - 1, x + width, y + height, 0xFF383838);
        graphics.fill(x, y, x + 1, y + height, 0xFF666666);
        graphics.fill(x + width - 1, y, x + width, y + height, 0xFF383838);
    }

    private static List<String> progressionGroups(ClientModel model) {
        LinkedHashSet<String> groups = new LinkedHashSet<>();
        model.stages().stream()
                .sorted(Comparator.comparingInt(stage -> existingStageSortIndex(stage.definition())))
                .map(stage -> read(stage.definition(), "progression_group", "custom_progression"))
                .forEach(groups::add);
        groups.add("custom_progression");
        return List.copyOf(groups);
    }

    private static String mainProgressionGroup(ClientModel model) {
        for (StageModel stage : model.stages()) {
            if (stage.definition().has("default_unlocked") && stage.definition().get("default_unlocked").getAsBoolean())
                return read(stage.definition(), "progression_group", "custom_progression");
        }
        return model.stages().isEmpty() ? "worldawakened:default_progression"
                : read(model.stages().getFirst().definition(), "progression_group", "custom_progression");
    }

    private static String readableSequence(String group) {
        if (group == null || group.isBlank() || "custom_progression".equals(group)) return "Custom Progression";
        if (group.endsWith("default_progression")) return "Main Progression";
        return readableId(group);
    }

    private static int effectiveSort(StageModel stage, StageDraft draft) {
        if (stage.id().equals(draft.source.id())) return draft.sort;
        Integer changed = draft.orderChanges.get(stage.id());
        if (changed != null) return changed;
        return existingStageSortIndex(stage.definition());
    }

    static int existingStageSortIndex(JsonObject definition) {
        return definition.has("sort_index") ? definition.get("sort_index").getAsInt() : 0;
    }

    private static void configurePhaseNameField(EditBox field) {
        field.setMaxLength(PHASE_NAME_MAX_LENGTH);
        field.setTooltip(Tooltip.create(Component.literal(
                "Phase name\nThe readable name shown in the World Phases editor.")));
    }

    private static void configurePhaseDescriptionField(EditBox field, String value) {
        field.setMaxLength(PHASE_DESCRIPTION_MAX_LENGTH);
        updatePhaseDescriptionTooltip(field, value);
    }

    private static void updatePhaseDescriptionTooltip(EditBox field, String value) {
        String fullText = value == null || value.isBlank() ? "No description entered."
                : "Full description:\n" + value;
        field.setTooltip(Tooltip.create(Component.literal(
                "Phase description\nThe player-facing summary of this phase.\n\n" + fullText)));
    }

    private static List<StageModel> sequenceStages(ClientModel model, String group, StageDraft draft) {
        return model.stages().stream()
                .filter(stage -> stage.id().equals(draft.source.id())
                        || group.equals(read(stage.definition(), "progression_group", "custom_progression")))
                .sorted(Comparator.comparingInt((StageModel stage) -> effectiveSort(stage, draft))
                        .thenComparing(StageModel::id))
          .toList();
    }

    private static List<WorldAwakenedPhaseOrdering.Entry> phaseOrderEntries(
            ClientModel model, List<StageModel> sequence, StageDraft draft) {
        return sequence.stream().map(stage -> new WorldAwakenedPhaseOrdering.Entry(
                stage.id(), effectiveSort(stage, draft), isStartingPhase(stage, draft),
                draft.unlockEditable && simplePhaseOrderEditable(model, stage))).toList();
    }

    private static List<StageModel> orderedStages(ClientModel model, StageDraft draft) {
        if (draft == null) {
            return model.stages().stream()
                    .sorted(Comparator.comparingInt((StageModel stage) -> existingStageSortIndex(stage.definition()))
                            .thenComparing(StageModel::id))
                    .toList();
        }
        List<StageModel> result = new ArrayList<>(model.stages());
        List<StageModel> sequence = sequenceStages(model, draft.group, draft);
        int sequenceIndex = 0;
        for (int index = 0; index < result.size(); index++) {
            StageModel stage = result.get(index);
            String group = read(stage.definition(), "progression_group", "custom_progression");
            if (stage.id().equals(draft.source.id()) || draft.group.equals(group)) {
                result.set(index, sequence.get(sequenceIndex++));
            }
        }
        return List.copyOf(result);
    }

    private static boolean isStartingDraft(StageDraft draft) {
        return draft != null && !draft.unlockConditions.isEmpty() && draft.unlockConditions.getFirst().type == 0;
    }

    private static boolean isStartingPhase(StageModel stage, StageDraft draft) {
        if (draft != null && stage.id().equals(draft.source.id())) return isStartingDraft(draft);
        return stage.definition().has("default_unlocked") && stage.definition().get("default_unlocked").getAsBoolean();
    }

    private static boolean simplePhaseOrderEditable(ClientModel model, StageModel stage) {
        return simplePhaseOrderEditable(model.triggerRules(), stage.id());
    }

    static boolean simplePhaseOrderEditable(JsonArray triggerRules, String stageId) {
        for (JsonElement raw : triggerRules) {
            if (!raw.isJsonObject()) continue;
            JsonObject trigger = raw.getAsJsonObject();
            if ((trigger.has("enabled") && !trigger.get("enabled").getAsBoolean())
                    || !isDirectTriggerForStage(trigger, stageId)) continue;
            if (!hasOnlySimpleUnlockConditions(trigger)) return false;
        }
        // Starting and Manual phases intentionally have no direct trigger. Their sort metadata is
        // still safe to edit, and converting a Starting phase to Manual must not strand it in place.
        return true;
    }

    private static int sequencePosition(List<StageModel> sequence, String stageId, StageDraft draft) {
        for (int index = 0; index < sequence.size(); index++) if (sequence.get(index).id().equals(stageId)) return index;
        return -1;
    }

    private static String policyExplanation(int policy) {
        return switch (policy) {
            case 1 -> "Exclusive: this stage unlocks only if no other stage in the sequence is active.";
            case 2 -> "Replace: unlocking this stage locks the currently active stage in the sequence.";
            default -> "Cumulative: this milestone and earlier stages remain unlocked; later milestones can catch up missed stages.";
        };
    }

    static String phasePolicyLabel(int policy) {
        return switch (policy) {
            case 1 -> "Exclusive";
            case 2 -> "Replace";
            default -> "Cumulative";
        };
    }

    static int nextPhasePolicy(int policy) {
        return (Math.floorMod(policy, 3) + 1) % 3;
    }

    private static String unlockExplanation(int unlockType) {
        return switch (unlockType) {
            case 0 -> "Available from the beginning.";
            case 1 -> "Unlocks when the configured world day is reached.";
            case 2 -> "Unlocks when a player enters the selected dimension.";
            case 3 -> "Unlocks when a player completes the selected advancement.";
            case 4 -> "Unlocks when a player defeats the selected mob.";
            default -> "Unlocked manually by an operator.";
        };
    }

    private static List<String> targetsForUnlock(ClientModel model, int unlockType) {
        if (unlockType == 2) return model.dimensions();
        if (unlockType == 3) return model.advancements().stream()
                .sorted(Comparator.comparing(AdvancementModel::technical).thenComparing(AdvancementModel::name)
                        .thenComparing(AdvancementModel::id))
                .map(AdvancementModel::id).toList();
        if (unlockType == 4) return model.entities().stream().map(EntityModel::id).toList();
        return List.of();
    }

    private static String targetLabel(ClientModel model, int unlockType, String id) {
        if (id == null || id.isBlank()) return "Choose a target";
        if (unlockType == 3) return model.advancements().stream().filter(advancement -> advancement.id().equals(id))
                .map(AdvancementModel::name).findFirst().orElseGet(() -> readableId(id));
        if (unlockType == 4) return model.entities().stream().filter(entity -> entity.id().equals(id))
                .map(EntityModel::name).findFirst().orElseGet(() -> readableId(id));
        return readableId(id);
    }

    private static String conditionTargetTooltip(int unlockType, String id) {
        String action = switch (unlockType) {
            case 2 -> "Choose the dimension a player must enter.";
            case 3 -> "Choose the advancement a player must complete.";
            case 4 -> "Choose the mob a player must defeat.";
            default -> "Choose this condition's target.";
        };
        return id == null || id.isBlank() ? action : action + "\nCurrent ID: " + id;
    }

    private static String targetSecondary(ClientModel model, int unlockType, String id) {
        if (unlockType == 3) return model.advancements().stream().filter(advancement -> advancement.id().equals(id))
                .map(advancement -> advancement.id() + "  ·  " + readableId(advancement.namespace())
                        + (advancement.technical() ? "  ·  Technical" : ""))
                .findFirst().orElse(id);
        if (unlockType == 4) return model.entities().stream().filter(entity -> entity.id().equals(id))
                .map(entity -> entity.id() + "  ·  " + readableId(entity.namespace()))
                .findFirst().orElse(id);
        int colon = id.indexOf(':');
        String namespace = colon > 0 ? readableId(id.substring(0, colon)) : "Minecraft";
        return id + "  ·  " + namespace;
    }

    private static String targetSearchText(ClientModel model, int unlockType, String id) {
        return (targetLabel(model, unlockType, id) + " " + targetSecondary(model, unlockType, id))
                .toLowerCase(Locale.ROOT);
    }

    private static String namespace(String id) {
        int colon = id.indexOf(':');
        return colon > 0 ? id.substring(0, colon) : "minecraft";
    }

    private static JsonArray replaceById(JsonArray source, JsonObject replacement) {
        JsonArray result = new JsonArray(); String id = replacement.get("id").getAsString();
        boolean replaced = false;
        for (JsonElement element : source) {
            if (element.isJsonObject() && element.getAsJsonObject().has("id")
                    && id.equals(element.getAsJsonObject().get("id").getAsString())) {
                result.add(replacement.deepCopy()); replaced = true;
            } else result.add(element.deepCopy());
        }
        if (!replaced) result.add(replacement.deepCopy());
        return result;
    }

    private static JsonArray removeById(JsonArray source, String id) {
        JsonArray result = new JsonArray();
        for (JsonElement element : source) if (!element.isJsonObject() || !element.getAsJsonObject().has("id")
                || !id.equals(element.getAsJsonObject().get("id").getAsString())) result.add(element.deepCopy());
        return result;
    }

    private static JsonArray removeDirectTriggersForStage(JsonArray source, String stageId) {
        JsonArray result = new JsonArray();
        for (JsonElement element : source) if (!isDirectTriggerForStage(element.getAsJsonObject(), stageId)) result.add(element.deepCopy());
        return result;
    }

    private static JsonArray appendDisabledDirectTriggerOverrides(JsonArray authored, JsonArray compiled, String stageId) {
        JsonArray result = authored.deepCopy();
        for (JsonElement element : compiled) {
            if (!element.isJsonObject() || !isDirectTriggerForStage(element.getAsJsonObject(), stageId)) continue;
            JsonObject disabled = element.getAsJsonObject().deepCopy();
            disabled.addProperty("enabled", false);
            result = replaceById(result, disabled);
        }
        return result;
    }

    private static boolean triggerTargetsStage(JsonObject trigger, String stageId) {
        if (!trigger.has("actions")) return false;
        for (JsonElement action : trigger.getAsJsonArray("actions")) {
            if (!action.isJsonObject()) continue;
            JsonObject object = action.getAsJsonObject();
            if (object.has("parameters") && object.getAsJsonObject("parameters").has("stage")
                    && stageId.equals(object.getAsJsonObject("parameters").get("stage").getAsString())) return true;
        }
        return false;
    }

    static boolean isDirectTriggerForStage(JsonObject trigger, String stageId) {
        if (!triggerTargetsStage(trigger, stageId) || !trigger.has("conditions")
                || !trigger.get("conditions").isJsonArray()) return false;
        for (JsonElement condition : trigger.getAsJsonArray("conditions")) {
            if (!condition.isJsonObject()) continue;
            JsonObject object = condition.getAsJsonObject();
            if (!read(object, "type", "").endsWith("stage_locked") || !object.has("parameters")
                    || !object.get("parameters").isJsonObject()) continue;
            JsonObject parameters = object.getAsJsonObject("parameters");
            if (parameters.has("stage") && stageId.equals(parameters.get("stage").getAsString())) return true;
        }
        return false;
    }

    private static void readEditorUnlockConditions(ClientModel model, String stageId, boolean starting, StageDraft draft) {
        draft.unlockConditions.clear();
        if (starting) {
            draft.unlockConditions.add(new UnlockConditionDraft(0, null));
            return;
        }
        List<JsonObject> direct = new ArrayList<>();
        for (JsonElement element : model.triggerRules()) {
            if (element.isJsonObject()
                    && (!element.getAsJsonObject().has("enabled") || element.getAsJsonObject().get("enabled").getAsBoolean())
                    && isDirectTriggerForStage(element.getAsJsonObject(), stageId)) {
                direct.add(element.getAsJsonObject());
            }
        }
        if (direct.isEmpty()) {
            draft.unlockConditions.add(new UnlockConditionDraft(5, null));
            return;
        }
        boolean grouped = direct.stream().allMatch(WorldAwakenedClientConfigPayloadHandler::hasEditorConditionGate);
        if (direct.size() > 1 && !grouped) {
            draft.unlockEditable = false;
            draft.unlockConditions.add(new UnlockConditionDraft(draft.unlockType, draft.target));
            return;
        }
        String groupToken = null;
        for (JsonObject trigger : direct) {
            UnlockConditionDraft condition = simpleUnlockCondition(trigger);
            if (condition == null || !hasOnlySimpleUnlockConditions(trigger)) {
                draft.unlockEditable = false;
                draft.unlockConditions.clear();
                draft.unlockConditions.add(new UnlockConditionDraft(draft.unlockType, draft.target));
                return;
            }
            if (grouped) {
                JsonObject parameters = editorConditionGate(trigger);
                String candidateGroup = read(parameters, "group_token", "");
                if (groupToken == null) groupToken = candidateGroup;
                else if (!groupToken.equals(candidateGroup)) {
                    draft.unlockEditable = false;
                    return;
                }
                draft.requireAll = !"any".equalsIgnoreCase(read(parameters, "mode", "all"));
            }
            if (draft.unlockConditions.size() < 3) draft.unlockConditions.add(condition);
        }
        if (direct.size() > 3) draft.unlockEditable = false;
        if (draft.unlockConditions.isEmpty()) draft.unlockConditions.add(new UnlockConditionDraft(5, null));
        draft.unlockType = draft.unlockConditions.getFirst().type;
        draft.target = draft.unlockConditions.getFirst().target;
    }

    private static boolean hasOnlySimpleUnlockConditions(JsonObject trigger) {
        if (!trigger.has("conditions") || !trigger.get("conditions").isJsonArray()) return false;
        int eventConditions = 0;
        for (JsonElement raw : trigger.getAsJsonArray("conditions")) {
            if (!raw.isJsonObject()) return false;
            String path = read(raw.getAsJsonObject(), "type", "");
            if (path.endsWith("stage_locked")) continue;
            if (path.endsWith("world_day_gte") || path.endsWith("current_dimension")
                    || path.endsWith("advancement_completed") || path.endsWith("entity_type")) eventConditions++;
            else return false;
        }
        return eventConditions == 1;
    }

    private static UnlockConditionDraft simpleUnlockCondition(JsonObject trigger) {
        String event = read(trigger, "trigger_type", "");
        int type = event.endsWith("world_day_milestone") ? 1
                : event.endsWith("player_enter_dimension") ? 2
                : event.endsWith("advancement_completed") ? 3
                : event.endsWith("entity_killed") || event.endsWith("boss_killed") ? 4 : 5;
        if (type == 5) return null;
        String condition = type == 1 ? "world_day_gte" : type == 2 ? "current_dimension"
                : type == 3 ? "advancement_completed" : "entity_type";
        String parameter = type == 1 ? "value" : type == 2 ? "dimension" : type == 3 ? "advancement" : "entity";
        if (!trigger.has("conditions")) return null;
        for (JsonElement raw : trigger.getAsJsonArray("conditions")) {
            if (!raw.isJsonObject()) continue;
            JsonObject node = raw.getAsJsonObject();
            if (!read(node, "type", "").endsWith(condition) || !node.has("parameters")) continue;
            JsonObject parameters = node.getAsJsonObject("parameters");
            if (parameters.has(parameter)) return new UnlockConditionDraft(type, parameters.get(parameter).getAsString());
        }
        return null;
    }

    private static boolean hasEditorConditionGate(JsonObject trigger) {
        return editorConditionGate(trigger) != null;
    }

    private static JsonObject editorConditionGate(JsonObject trigger) {
        if (!trigger.has("actions") || !trigger.get("actions").isJsonArray()) return null;
        for (JsonElement raw : trigger.getAsJsonArray("actions")) {
            if (!raw.isJsonObject()) continue;
            JsonObject action = raw.getAsJsonObject();
            if (read(action, "type", "").endsWith("record_unlock_condition")
                    && action.has("parameters") && action.get("parameters").isJsonObject()) {
                return action.getAsJsonObject("parameters");
            }
        }
        return null;
    }

    private static int inferUnlockType(ClientModel model, String stageId, boolean defaultUnlocked) {
        if (defaultUnlocked) return 0;
        for (JsonElement element : model.triggerRules()) if (isDirectTriggerForStage(element.getAsJsonObject(), stageId)) {
            if (element.getAsJsonObject().has("enabled") && !element.getAsJsonObject().get("enabled").getAsBoolean()) continue;
            String type = read(element.getAsJsonObject(), "trigger_type", "");
            if (type.endsWith("world_day_milestone")) return 1;
            if (type.endsWith("player_enter_dimension")) return 2;
            if (type.endsWith("advancement_completed")) return 3;
            if (type.endsWith("entity_killed") || type.endsWith("boss_killed")) return 4;
            return 5;
        }
        return 5;
    }

    private static String existingTarget(ClientModel model, String stageId, String conditionType,
            String parameter, String fallback) {
        for (JsonElement element : model.triggerRules()) {
            JsonObject trigger = element.getAsJsonObject();
            if (trigger.has("enabled") && !trigger.get("enabled").getAsBoolean()) continue;
            if (!isDirectTriggerForStage(trigger, stageId) || !trigger.has("conditions")) continue;
            for (JsonElement condition : trigger.getAsJsonArray("conditions")) {
                JsonObject object = condition.getAsJsonObject();
                if (read(object, "type", "").endsWith(conditionType) && object.has("parameters")
                        && object.getAsJsonObject("parameters").has(parameter))
                    return object.getAsJsonObject("parameters").get(parameter).getAsString();
            }
        }
        return fallback;
    }

    private static JsonObject buildTrigger(ClientModel model, String stageId, int unlockType, String target,
            int sortIndex, String group, boolean catchUp) {
        JsonObject trigger = new JsonObject();
        String existingId = null;
        if (model != null) for (JsonElement element : model.triggerRules()) if (isDirectTriggerForStage(element.getAsJsonObject(), stageId)) {
            existingId = element.getAsJsonObject().get("id").getAsString(); break;
        }
        trigger.addProperty("schema_version", 1);
        trigger.addProperty("id", existingId == null ? stageId + "_unlock" : existingId);
        trigger.addProperty("enabled", true); trigger.addProperty("priority", 100);
        String event = switch (unlockType) { case 1 -> "world_day_milestone"; case 2 -> "player_enter_dimension";
            case 3 -> "advancement_completed"; case 4 -> "entity_killed"; default -> "manual_debug"; };
        trigger.addProperty("trigger_type", "worldawakened:" + event);
        trigger.addProperty("source_scope", unlockType == 1 || unlockType == 5 ? "world" : "player");
        JsonArray conditions = new JsonArray();
        if (unlockType == 1) conditions.add(node("world_day_gte", "value", Integer.parseInt(target)));
        else if (unlockType == 2) conditions.add(node("current_dimension", "dimension", target));
        else if (unlockType == 3) conditions.add(node("advancement_completed", "advancement", target));
        else if (unlockType == 4) conditions.add(node("entity_type", "entity", target));
        conditions.add(node("stage_locked", "stage", stageId)); trigger.add("conditions", conditions);
        JsonArray actions = new JsonArray();
        String actualGroup = group == null || group.isBlank() ? "custom_progression" : group;
        (model == null ? java.util.stream.Stream.<StageModel>empty() : model.stages().stream())
                .filter(stage -> catchUp && actualGroup.equals(read(stage.definition(), "progression_group", "")))
                .filter(stage -> stage.definition().has("sort_index") && stage.definition().get("sort_index").getAsInt() <= sortIndex)
                .sorted(Comparator.comparingInt(stage -> stage.definition().get("sort_index").getAsInt()))
                .forEach(stage -> actions.add(node("unlock_stage", "stage", stage.id())));
        boolean hasCurrent = false;
        for (JsonElement action : actions) if (triggerTargetsStage(actionWrapper(action), stageId)) hasCurrent = true;
        if (!hasCurrent) actions.add(node("unlock_stage", "stage", stageId));
        trigger.add("actions", actions); trigger.addProperty("one_shot", true);
        return trigger;
    }

    static JsonArray buildEditorUnlockTriggers(ClientModel model, String stageId,
            List<UnlockConditionDraft> conditions, boolean requireAll,
            int sortIndex, String group, boolean catchUp) {
        JsonArray result = new JsonArray();
        if (conditions == null || conditions.isEmpty()) return result;
        UnlockConditionDraft first = conditions.getFirst();
        if (first.type == 0 || first.type == 5) return result;
        if (conditions.size() == 1) {
            result.add(buildTrigger(model, stageId, first.type, first.target, sortIndex, group, catchUp));
            return result;
        }

        List<String> tokens = conditions.stream().map(WorldAwakenedClientConfigPayloadHandler::conditionToken).toList();
        List<String> canonicalTokens = new ArrayList<>(tokens);
        canonicalTokens.sort(String::compareTo);
        String mode = requireAll ? "all" : "any";
        String groupToken = stableToken(stageId + "|" + mode + "|" + String.join("|", canonicalTokens));
        for (int index = 0; index < conditions.size(); index++) {
            UnlockConditionDraft condition = conditions.get(index);
            JsonObject trigger = buildTrigger(model, stageId, condition.type, condition.target,
                    sortIndex, group, catchUp);
            trigger.addProperty("id", stageId + "_unlock_" + tokens.get(index));
            // Player source provides the event actor when progression is per-player. The gate
            // itself writes to world state in GLOBAL mode and player state in PER_PLAYER mode.
            trigger.addProperty("source_scope", "player");
            trigger.addProperty("one_shot", false);
            JsonObject gate = node("record_unlock_condition", "group_token", groupToken);
            gate.addProperty("priority", 1000);
            JsonObject parameters = gate.getAsJsonObject("parameters");
            parameters.addProperty("condition_token", tokens.get(index));
            JsonArray allTokens = new JsonArray();
            canonicalTokens.forEach(allTokens::add);
            parameters.add("condition_tokens", allTokens);
            parameters.addProperty("mode", mode);
            JsonArray actions = trigger.getAsJsonArray("actions");
            JsonArray gatedActions = new JsonArray();
            gatedActions.add(gate);
            actions.forEach(action -> gatedActions.add(action.deepCopy()));
            trigger.add("actions", gatedActions);
            result.add(trigger);
        }
        return result;
    }

    private static JsonObject withCumulativeUnlockActions(JsonObject source, List<StageModel> sequence, String stageId) {
        JsonObject trigger = source.deepCopy();
        if (!trigger.has("actions") || !trigger.get("actions").isJsonArray()) return trigger;
        Set<String> sequenceIds = sequence.stream().map(StageModel::id)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        JsonArray actions = new JsonArray();
        for (JsonElement raw : trigger.getAsJsonArray("actions")) {
            if (!raw.isJsonObject()) { actions.add(raw.deepCopy()); continue; }
            JsonObject action = raw.getAsJsonObject();
            JsonObject parameters = action.has("parameters") && action.get("parameters").isJsonObject()
                    ? action.getAsJsonObject("parameters") : new JsonObject();
            boolean sequenceUnlock = read(action, "type", "").endsWith("unlock_stage")
                    && parameters.has("stage") && sequenceIds.contains(parameters.get("stage").getAsString());
            if (!sequenceUnlock) actions.add(action.deepCopy());
        }
        for (StageModel stage : sequence) {
            actions.add(node("unlock_stage", "stage", stage.id()));
            if (stage.id().equals(stageId)) break;
        }
        trigger.add("actions", actions);
        return trigger;
    }

    private static String conditionToken(UnlockConditionDraft condition) {
        return stableToken(condition.type + "|" + (condition.target == null ? "" : condition.target));
    }

    private static String stableToken(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)).toString().replace("-", "");
    }

    private static JsonObject actionWrapper(JsonElement action) {
        JsonObject wrapper = new JsonObject(); JsonArray actions = new JsonArray(); actions.add(action.deepCopy()); wrapper.add("actions", actions); return wrapper;
    }

    private static JsonObject node(String type, String parameter, Object value) {
        JsonObject node = new JsonObject(); node.addProperty("type", "worldawakened:" + type);
        JsonObject parameters = new JsonObject();
        if (value instanceof Number number) parameters.addProperty(parameter, number);
        else parameters.addProperty(parameter, String.valueOf(value));
        node.add("parameters", parameters); return node;
    }

    private static int nextSortIndex(ClientModel model) {
        return model.stages().stream().mapToInt(stage -> stage.definition().has("sort_index")
                ? stage.definition().get("sort_index").getAsInt() : 0).max().orElse(0) + 10;
    }

    private static int nextSortIndex(ClientModel model, String group) {
        return model.stages().stream()
                .filter(stage -> group.equals(read(stage.definition(), "progression_group", "custom_progression")))
                .mapToInt(stage -> stage.definition().has("sort_index")
                        ? stage.definition().get("sort_index").getAsInt() : 0)
                .max().orElse(0) + 10;
    }

    private static String uniqueId(ClientModel model, String name, String kind) {
        String slug = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (slug.isBlank()) slug = kind;
        String base = "worldawakened:world/" + slug, candidate = base; int suffix = 2;
        Set<String> ids = new java.util.HashSet<>(); model.stages().forEach(stage -> ids.add(stage.id()));
        model.pools().forEach(pool -> ids.add(pool.id()));
        while (ids.contains(candidate)) candidate = base + "_" + suffix++;
        return candidate;
    }

    private static boolean isStageReferenced(ClientModel model, String stageId) {
        if (model.tracks().toString().contains(stageId)) return true;
        for (JsonElement pool : model.authoredPools()) if (pool.toString().contains(stageId)) return true;
        return false;
    }

    private static String unlockSummary(ClientModel model, String stageId) {
        List<JsonObject> grouped = new ArrayList<>();
        for (JsonElement element : model.triggerRules()) if (element.isJsonObject()) {
            JsonObject trigger = element.getAsJsonObject();
            if ((!trigger.has("enabled") || trigger.get("enabled").getAsBoolean())
                    && isDirectTriggerForStage(trigger, stageId) && hasEditorConditionGate(trigger)) grouped.add(trigger);
        }
        if (grouped.size() > 1) {
            String mode = read(editorConditionGate(grouped.getFirst()), "mode", "all");
            return grouped.size() + " conditions · " + ("any".equals(mode) ? "Any" : "All");
        }
        for (JsonElement element : model.triggerRules()) if (isDirectTriggerForStage(element.getAsJsonObject(), stageId)) {
            JsonObject trigger = element.getAsJsonObject(); String type = read(trigger, "trigger_type", "");
            if (trigger.has("enabled") && !trigger.get("enabled").getAsBoolean()) continue;
            if (type.endsWith("world_day_milestone")) return "World day " + existingTarget(model, stageId, "world_day_gte", "value", "?");
            if (type.endsWith("player_enter_dimension")) return "Enter " + targetLabel(model, 2,
                    existingTarget(model, stageId, "current_dimension", "dimension", "dimension"));
            if (type.endsWith("advancement_completed")) return "Complete " + targetLabel(model, 3,
                    existingTarget(model, stageId, "advancement_completed", "advancement", "advancement"));
            if (type.endsWith("entity_killed") || type.endsWith("boss_killed")) return "Defeat " + targetLabel(model, 4,
                    existingTarget(model, stageId, "entity_type", "entity", "mob"));
            return "Manual unlock";
        }
        StageModel stage = model.stages().stream().filter(value -> value.id().equals(stageId)).findFirst().orElse(null);
        return stage != null && stage.definition().has("default_unlocked") && stage.definition().get("default_unlocked").getAsBoolean()
                ? "Starting stage" : "No automatic unlock";
    }

    private static String readableId(String id){int colon=id.indexOf(':');String path=colon>=0?id.substring(colon+1):id;int slash=path.lastIndexOf('/');if(slash>=0)path=path.substring(slash+1);StringBuilder out=new StringBuilder();for(String word:path.split("_")){if(!out.isEmpty())out.append(' ');if(!word.isEmpty())out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));}return out.toString();}
}
