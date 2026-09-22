package dev.tide.tbsdamagemultiplied.integration.tbs;

import java.util.Map;
import dev.tide.tbsdamagemultiplied.DamageContext;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.registries.ForgeRegistries;

/** Closed, conservative decision table for the audited TBS 0.5.0-hotfix2 paths. */
public final class TbsDamageClassifier {
    public static final ResourceLocation PLAYER_ATTACK =
        ResourceLocation.fromNamespaceAndPath("minecraft", "player_attack");
    private static final ResourceLocation ICE_DAMAGE =
        ResourceLocation.fromNamespaceAndPath(
            "torchesbecomesunlight", "no_trigger_no_armor_attack");
    private static final ResourceLocation BULLET = tbs("bullet");

    private static final Map<ResourceLocation, TbsDamagePath> NATIVE_ENTITIES = Map.of(
        tbs("drone_projectile"), TbsDamagePath.DRONE_PROJECTILE,
        tbs("stomp_entity"), TbsDamagePath.STOMP,
        tbs("grave_stone_projectile"), TbsDamagePath.GRAVESTONE_PROJECTILE,
        tbs("ros_multi_block"), TbsDamagePath.ROS_MULTI_BLOCK,
        tbs("rosmontis_installation"), TbsDamagePath.ROSMONTIS_INSTALLATION,
        tbs("rosmontis_living_installation"), TbsDamagePath.ROSMONTIS_LIVING_INSTALLATION,
        tbs("halberd_of_the_infected"), TbsDamagePath.HALBERD_GROUND);

    private TbsDamageClassifier() {
    }

    public static TbsDamagePath classify(DamageContext ctx) {
        TbsDamagePath nativePath = nativePath(ctx.directEntityId());
        if (nativePath == TbsDamagePath.UNKNOWN) {
            nativePath = nativePath(ctx.causingEntityId());
        }
        if (nativePath != TbsDamagePath.UNKNOWN) {
            return nativePath;
        }

        if (BULLET.equals(ctx.directEntityId()) || BULLET.equals(ctx.causingEntityId())) {
            return PLAYER_ATTACK.equals(ctx.damageTypeId())
                ? TbsDamagePath.BULLET_DIRECT_HIT
                : TbsDamagePath.BULLET_AOE;
        }

        if (ICE_DAMAGE.equals(ctx.damageTypeId())) {
            return TbsDamagePath.ICE_CRYSTAL_HIT;
        }

        return TbsDamagePath.UNKNOWN;
    }

    public static ServerPlayer resolvePlayer(DamageSource source) {
        if (source == null) {
            return null;
        }
        Entity direct = source.getDirectEntity();
        Entity causing = source.getEntity();
        if (causing instanceof ServerPlayer player) {
            return player;
        }
        if (direct instanceof ServerPlayer player) {
            return player;
        }
        ServerPlayer owner = ownerOf(direct);
        return owner != null ? owner : ownerOf(causing);
    }

    private static ServerPlayer ownerOf(Entity entity) {
        if (entity instanceof Projectile projectile
            && projectile.getOwner() instanceof ServerPlayer player) {
            return player;
        }
        return null;
    }

    public static DamageContext buildContext(DamageSource source, ServerPlayer player) {
        Entity direct = source.getDirectEntity();
        Entity causing = source.getEntity();
        return new DamageContext(
            source, player, direct, causing, damageTypeId(source),
            entityId(direct), entityId(causing));
    }

    public static ResourceLocation damageTypeId(DamageSource source) {
        return source.typeHolder().unwrapKey().map(key -> key.location()).orElse(null);
    }

    private static ResourceLocation entityId(Entity entity) {
        return entity == null ? null : ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
    }

    private static TbsDamagePath nativePath(ResourceLocation id) {
        return id == null ? TbsDamagePath.UNKNOWN
            : NATIVE_ENTITIES.getOrDefault(id, TbsDamagePath.UNKNOWN);
    }

    private static ResourceLocation tbs(String path) {
        return ResourceLocation.fromNamespaceAndPath("torchesbecomesunlight", path);
    }

    /** Retained as a no-op config event target; the closed decision table has no caches. */
    public static void invalidateCaches() {
    }
}
