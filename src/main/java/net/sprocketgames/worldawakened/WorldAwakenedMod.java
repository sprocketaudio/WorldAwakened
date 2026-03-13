package net.sprocketgames.worldawakened;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.sprocketgames.worldawakened.ascension.WorldAwakenedAscensionEventHandlers;
import net.sprocketgames.worldawakened.ascension.WorldAwakenedAscensionService;
import net.sprocketgames.worldawakened.carrier.WorldAwakenedOwnedCarrierAttachments;
import net.sprocketgames.worldawakened.carrier.WorldAwakenedOwnedCarrierEventHandlers;
import net.sprocketgames.worldawakened.command.WorldAwakenedCommands;
import net.sprocketgames.worldawakened.config.WorldAwakenedClientConfig;
import net.sprocketgames.worldawakened.config.WorldAwakenedCommonConfig;
import net.sprocketgames.worldawakened.data.load.WorldAwakenedDataReloadListener;
import net.sprocketgames.worldawakened.data.load.WorldAwakenedDatapackService;
import net.sprocketgames.worldawakened.debug.WorldAwakenedLog;
import net.sprocketgames.worldawakened.debug.WorldAwakenedLogCategory;
import net.sprocketgames.worldawakened.progression.WorldAwakenedStageService;
import net.sprocketgames.worldawakened.progression.trigger.WorldAwakenedTriggerEventHandlers;
import net.sprocketgames.worldawakened.progression.trigger.WorldAwakenedTriggerService;
import net.sprocketgames.worldawakened.rules.WorldAwakenedRuleService;
import net.sprocketgames.worldawakened.network.WorldAwakenedNetwork;

@Mod(WorldAwakenedConstants.MOD_ID)
public final class WorldAwakenedMod {
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final WorldAwakenedDatapackService DATAPACK_SERVICE = new WorldAwakenedDatapackService();
    public static final WorldAwakenedStageService STAGE_SERVICE = new WorldAwakenedStageService(DATAPACK_SERVICE);
    public static final WorldAwakenedAscensionService ASCENSION_SERVICE = new WorldAwakenedAscensionService(DATAPACK_SERVICE, STAGE_SERVICE);
    public static final WorldAwakenedRuleService RULE_SERVICE = new WorldAwakenedRuleService(DATAPACK_SERVICE, STAGE_SERVICE, ASCENSION_SERVICE);
    public static final WorldAwakenedTriggerService TRIGGER_SERVICE = new WorldAwakenedTriggerService(DATAPACK_SERVICE, STAGE_SERVICE, RULE_SERVICE, ASCENSION_SERVICE);

    public WorldAwakenedMod(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, WorldAwakenedCommonConfig.SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, WorldAwakenedClientConfig.SPEC);
        WorldAwakenedOwnedCarrierAttachments.ATTACHMENT_TYPES.register(modEventBus);

        WorldAwakenedTriggerEventHandlers triggerHandlers = new WorldAwakenedTriggerEventHandlers(TRIGGER_SERVICE);
        WorldAwakenedAscensionEventHandlers ascensionHandlers = new WorldAwakenedAscensionEventHandlers(ASCENSION_SERVICE);
        WorldAwakenedOwnedCarrierEventHandlers carrierHandlers = new WorldAwakenedOwnedCarrierEventHandlers();
        modEventBus.addListener(WorldAwakenedNetwork::registerPayloadHandlers);
        NeoForge.EVENT_BUS.addListener(this::onAddReloadListener);
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(triggerHandlers::onPlayerChangedDimension);
        NeoForge.EVENT_BUS.addListener(triggerHandlers::onAdvancementCompleted);
        NeoForge.EVENT_BUS.addListener(triggerHandlers::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(triggerHandlers::onItemCrafted);
        NeoForge.EVENT_BUS.addListener(triggerHandlers::onBlockPlaced);
        NeoForge.EVENT_BUS.addListener(triggerHandlers::onBlockBroken);
        NeoForge.EVENT_BUS.addListener(ascensionHandlers::onStageUnlocked);
        NeoForge.EVENT_BUS.addListener(ascensionHandlers::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(ascensionHandlers::onPlayerRespawn);
        NeoForge.EVENT_BUS.addListener(carrierHandlers::onLivingIncomingDamage);

        WorldAwakenedLog.info(LOGGER, WorldAwakenedLogCategory.CORE, "Initialized {}", WorldAwakenedConstants.MOD_NAME);
    }

    private void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(new WorldAwakenedDataReloadListener(DATAPACK_SERVICE));
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        WorldAwakenedCommands.register(event.getDispatcher(), DATAPACK_SERVICE, STAGE_SERVICE, TRIGGER_SERVICE, RULE_SERVICE, ASCENSION_SERVICE);
    }
}

