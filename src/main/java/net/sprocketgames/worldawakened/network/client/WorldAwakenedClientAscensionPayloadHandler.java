package net.sprocketgames.worldawakened.network.client;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import net.sprocketgames.worldawakened.network.WorldAwakenedSelectAscensionRewardPayload;

public final class WorldAwakenedClientAscensionPayloadHandler {
    private WorldAwakenedClientAscensionPayloadHandler() {
    }

    public static void handleOpenOffer(String payloadJson) {
        ClientOfferModel model = parse(payloadJson);
        if (model == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        minecraft.setScreen(new WorldAwakenedAscensionOfferScreen(model));
    }

    private static ClientOfferModel parse(String payloadJson) {
        JsonElement rootElement = JsonParser.parseString(payloadJson);
        if (!rootElement.isJsonObject()) {
            return null;
        }
        JsonObject root = rootElement.getAsJsonObject();
        String instanceId = root.has("instance_id") ? root.get("instance_id").getAsString() : "";
        String offerId = root.has("offer_id") ? root.get("offer_id").getAsString() : "";
        String title = root.has("title") ? root.get("title").getAsString() : offerId;
        String description = root.has("description") ? root.get("description").getAsString() : "";
        if (instanceId.isBlank() || offerId.isBlank()) {
            return null;
        }

        List<ClientRewardChoice> rewards = new ArrayList<>();
        if (root.has("rewards") && root.get("rewards").isJsonArray()) {
            JsonArray rewardsArray = root.getAsJsonArray("rewards");
            for (JsonElement rewardElement : rewardsArray) {
                if (!rewardElement.isJsonObject()) {
                    continue;
                }
                JsonObject rewardNode = rewardElement.getAsJsonObject();
                String rewardIdRaw = rewardNode.has("id") ? rewardNode.get("id").getAsString() : "";
                ResourceLocation rewardId = ResourceLocation.tryParse(rewardIdRaw);
                if (rewardId == null) {
                    continue;
                }
                String rewardTitle = rewardNode.has("title") ? rewardNode.get("title").getAsString() : rewardIdRaw;
                String rewardDescription = rewardNode.has("description") ? rewardNode.get("description").getAsString() : "";
                String rarity = rewardNode.has("rarity") ? rewardNode.get("rarity").getAsString() : "";
                rewards.add(new ClientRewardChoice(rewardId, rewardTitle, rewardDescription, rarity));
            }
        }

        if (rewards.isEmpty()) {
            return null;
        }
        return new ClientOfferModel(instanceId, offerId, title, description, List.copyOf(rewards));
    }

    private record ClientOfferModel(
            String instanceId,
            String offerId,
            String title,
            String description,
            List<ClientRewardChoice> rewards) {
    }

    private record ClientRewardChoice(
            ResourceLocation rewardId,
            String title,
            String description,
            String rarity) {
    }

    private static final class WorldAwakenedAscensionOfferScreen extends Screen {
        private final ClientOfferModel model;
        private Button confirmButton;
        private ResourceLocation selectedRewardId;

        private WorldAwakenedAscensionOfferScreen(ClientOfferModel model) {
            super(Component.literal(model.title()));
            this.model = model;
        }

        @Override
        protected void init() {
            clearWidgets();
            int centerX = width / 2;
            int buttonWidth = 240;
            int startY = Math.max(48, height / 2 - 70);

            int y = startY;
            for (ClientRewardChoice reward : model.rewards()) {
                String label = reward.rarity().isBlank() ? reward.title() : reward.title() + " [" + reward.rarity() + "]";
                addRenderableWidget(Button.builder(
                        Component.literal(label),
                        button -> selectedRewardId = reward.rewardId())
                        .bounds(centerX - buttonWidth / 2, y, buttonWidth, 20)
                        .build());
                y += 24;
            }

            confirmButton = addRenderableWidget(Button.builder(
                    Component.literal("Confirm Selection"),
                    button -> confirmSelection())
                    .bounds(centerX - 120, y + 8, 115, 20)
                    .build());
            confirmButton.active = false;
            addRenderableWidget(Button.builder(
                    Component.literal("Cancel"),
                    button -> onClose())
                    .bounds(centerX + 5, y + 8, 115, 20)
                    .build());
        }

        @Override
        public void tick() {
            super.tick();
            if (confirmButton != null) {
                confirmButton.active = selectedRewardId != null;
            }
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            renderBackground(graphics, mouseX, mouseY, partialTick);
            graphics.drawCenteredString(font, title, width / 2, 16, 0xFFFFFF);
            graphics.drawCenteredString(font, Component.literal("Choose one reward"), width / 2, 30, 0xA0A0A0);
            if (!model.description().isBlank()) {
                graphics.drawWordWrap(font, Component.literal(model.description()), width / 2 - 130, 40, 260, 0xC0C0C0);
            }
            if (selectedRewardId != null) {
                graphics.drawCenteredString(font, Component.literal("Selected: " + selectedRewardTitle()), width / 2, height - 38, 0xFFD060);
            }
            super.render(graphics, mouseX, mouseY, partialTick);
        }

        private String selectedRewardTitle() {
            if (selectedRewardId == null) {
                return "";
            }
            return model.rewards().stream()
                    .filter(reward -> reward.rewardId().equals(selectedRewardId))
                    .map(ClientRewardChoice::title)
                    .findFirst()
                    .orElse(selectedRewardId.toString());
        }

        private void confirmSelection() {
            if (selectedRewardId == null) {
                return;
            }
            PacketDistributor.sendToServer(new WorldAwakenedSelectAscensionRewardPayload(
                    model.instanceId(),
                    selectedRewardId.toString()));
            onClose();
        }
    }
}
