package dev.tide.tbsdamagemultiplied.hook;

import dev.tide.tbsdamagemultiplied.AttackModifierApplier;
import dev.tide.tbsdamagemultiplied.ModifierPolicy;
import dev.tide.tbsdamagemultiplied.TbsDamageMultiplied;
import dev.tide.tbsdamagemultiplied.TbsDamageMultipliedConfig;
import dev.tide.tbsdamagemultiplied.integration.tbs.TbsDamagePath;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/** Path-aware target for the narrow TBS 0.5.0-hotfix2 coremod injections. */
public final class TbsProjectileDamageHook {
    private static final ResourceLocation PLAYER_ATTACK =
        ResourceLocation.fromNamespaceAndPath("minecraft", "player_attack");
    private static final ResourceLocation NO_TRIGGER_ATTACK =
        ResourceLocation.fromNamespaceAndPath("torchesbecomesunlight", "no_trigger_attack");
    private static final ResourceLocation NO_TRIGGER_NO_ARMOR_ATTACK =
        ResourceLocation.fromNamespaceAndPath(
            "torchesbecomesunlight", "no_trigger_no_armor_attack");

    private TbsProjectileDamageHook() {
    }

    /**
     * Applies one path policy before TBS calls hurt/actuallyHurt. When an ordinary hurt
     * call will synchronously emit LivingHurtEvent, a provenance frame makes the event layer
     * skip that same hit. The transformer balances the frame immediately after hurt returns.
     */
    public static float scaleWithOwner(
        float amount,
        Entity owner,
        Entity target,
        String pathId,
        boolean emitsLivingHurtEvent) {
        TbsDamageProvenance.Frame frame = null;
        try {
            TbsDamagePath path = TbsDamagePath.byId(pathId);
            if (emitsLivingHurtEvent) {
                // The existing projectile/range hooks do not always have the eventual
                // victim in scope. They still carry owner + path and are stacked safely.
                frame = TbsDamageProvenance.push(
                    path, owner, target, false, expectedDamageType(path), false);
            }
            if (!TbsDamageMultipliedConfig.COMMON.enabled.get()
                || !(owner instanceof ServerPlayer player)) {
                return amount;
            }

            ModifierPolicy policy = TbsDamageMultipliedConfig.COMMON.policy(path);
            if (!path.configurable() || !policy.enabled()) {
                return amount;
            }

            double result = AttackModifierApplier.applyAttackModifiers(player, amount, policy);
            if (!Double.isFinite(result) || result < 0.0) {
                return amount;
            }

            if (emitsLivingHurtEvent) {
                frame.setAlreadyScaled(true);
            }
            float scaled = (float) result;
            if (TbsDamageMultipliedConfig.COMMON.debug.get()) {
                TbsDamageMultiplied.LOG.info(
                    "[TBSDamageMultiplied] path={} base={} addition={} multiplyBase={} multiplyTotal={} result={} player={}",
                    path.id(), amount, policy.addition(), policy.multiplyBase(),
                    policy.multiplyTotal(), scaled, player.getGameProfile().getName());
            }
            return scaled;
        } catch (Throwable ignored) {
            // Keep the frame for the injected post-call balance. If this hook could
            // not scale, the event layer may still safely process its unscaled frame.
            return amount;
        }
    }

    /** Marks an audited direct fixed hit; called immediately before TBS invokes hurt. */
    public static void markFixedPath(Entity target, Entity owner, String pathId) {
        TbsDamageProvenance.push(
            TbsDamagePath.byId(pathId), owner, target, false, PLAYER_ATTACK, true);
    }

    /** Called at HIGHEST priority before unrelated listeners can create nested damage. */
    public static TbsDamageProvenance.Frame observeMatchingFrame(
        ServerPlayer player,
        Entity victim,
        net.minecraft.world.damagesource.DamageSource source) {
        return TbsDamageProvenance.observeMatching(player, victim, source);
    }

    /** Called by injected bytecode after the audited call returns, including no-event hits. */
    public static void finishFrame() {
        TbsDamageProvenance.finishTop();
    }

    public static void clearProvenance() {
        TbsDamageProvenance.clear();
    }

    private static ResourceLocation expectedDamageType(TbsDamagePath path) {
        return switch (path) {
            case BULLET_DIRECT_HIT -> PLAYER_ATTACK;
            case ICE_CRYSTAL_HIT -> NO_TRIGGER_NO_ARMOR_ATTACK;
            case ROSMONTIS_EMBRACE_ASSIST -> NO_TRIGGER_ATTACK;
            default -> null;
        };
    }
}
