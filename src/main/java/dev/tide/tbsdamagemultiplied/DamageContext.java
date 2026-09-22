package dev.tide.tbsdamagemultiplied;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;

/**
 * Everything the classifier needs about one {@code LivingHurtEvent}, resolved once so
 * the classification rules read as a flat, side-effect-free decision table.
 */
public record DamageContext(
    DamageSource source,
    ServerPlayer player,
    Entity directEntity,
    Entity causingEntity,
    ResourceLocation damageTypeId,
    ResourceLocation directEntityId,
    ResourceLocation causingEntityId
) {
}
