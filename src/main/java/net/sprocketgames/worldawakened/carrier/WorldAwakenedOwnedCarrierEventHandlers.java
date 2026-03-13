package net.sprocketgames.worldawakened.carrier;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

public final class WorldAwakenedOwnedCarrierEventHandlers {
    public void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity entity = event.getEntity();
        if (!WorldAwakenedOwnedCarrierService.hasCarrier(entity, WorldAwakenedOwnedCarrierIds.FIRE_RESISTANCE_PASSIVE)) {
            return;
        }
        if (event.getSource().is(DamageTypeTags.IS_FIRE)) {
            event.setCanceled(true);
        }
    }
}
