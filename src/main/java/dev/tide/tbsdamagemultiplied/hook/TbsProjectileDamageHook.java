package dev.tide.tbsdamagemultiplied.hook;

import dev.tide.tbsdamagemultiplied.AttackModifierApplier;
import dev.tide.tbsdamagemultiplied.ModifierPolicy;
import dev.tide.tbsdamagemultiplied.TbsDamageMultiplied;
import dev.tide.tbsdamagemultiplied.TbsDamageMultipliedConfig;
import dev.tide.tbsdamagemultiplied.integration.tbs.TbsDamagePath;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/** Path-aware target for the narrow TBS 0.5.0-hotfix2 coremod injections. */
public final class TbsProjectileDamageHook {
    private static final ThreadLocal<TbsDamagePath> EVENT_SCALED_PATH = new ThreadLocal<>();

    private TbsProjectileDamageHook() {
    }

    /**
     * Applies one path policy before TBS calls hurt/actuallyHurt. When an ordinary hurt
     * call will synchronously emit LivingHurtEvent, the path marker makes the event layer
     * skip that same hit. The transformer clears the marker immediately after hurt returns.
     */
    public static float scaleWithOwner(
        float amount, Entity owner, String pathId, boolean emitsLivingHurtEvent) {
        try {
            if (!TbsDamageMultipliedConfig.COMMON.enabled.get()
                || !(owner instanceof ServerPlayer player)) {
                return amount;
            }

            TbsDamagePath path = TbsDamagePath.byId(pathId);
            ModifierPolicy policy = TbsDamageMultipliedConfig.COMMON.policy(path);
            if (!path.configurable() || !policy.enabled()) {
                return amount;
            }

            double result = AttackModifierApplier.applyAttackModifiers(player, amount, policy);
            if (!Double.isFinite(result) || result < 0.0) {
                return amount;
            }

            if (emitsLivingHurtEvent) {
                EVENT_SCALED_PATH.set(path);
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
            EVENT_SCALED_PATH.remove();
            return amount;
        }
    }

    /** Called by the event handler while hurt is still on the same server thread. */
    public static TbsDamagePath consumeEventScaledPath() {
        TbsDamagePath path = EVENT_SCALED_PATH.get();
        EVENT_SCALED_PATH.remove();
        return path;
    }

    /** Called by injected bytecode after hurt returns, including cancelled/no-event hits. */
    public static void clearEventScaledPath() {
        EVENT_SCALED_PATH.remove();
    }
}
