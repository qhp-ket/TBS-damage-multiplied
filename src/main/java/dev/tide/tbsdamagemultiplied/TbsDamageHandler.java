package dev.tide.tbsdamagemultiplied;

import dev.tide.tbsdamagemultiplied.integration.tbs.TbsDamageClassifier;
import dev.tide.tbsdamagemultiplied.integration.tbs.TbsDamagePath;
import dev.tide.tbsdamagemultiplied.hook.TbsProjectileDamageHook;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Server-side glue. On {@link LivingHurtEvent} it classifies the hit; for FIXED TBS
 * paths it treats the original amount as an ATTACK_DAMAGE base and re-applies the
 * player's modifiers via {@link AttackModifierApplier}. NATIVE and IGNORE paths, as
 * well as genuine left-clicks (recorded via {@link AttackEntityEvent}), are untouched.
 */
public final class TbsDamageHandler {
    /**
     * A real melee swing routes through {@code Player.attack}; record it so the same-tick
     * hurt against the same target is treated as native melee and left alone.
     */
    @SubscribeEvent
    public void onAttackEntity(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (player == null || player.level().isClientSide()) {
            return;
        }
        Entity target = event.getTarget();
        if (target == null) {
            return;
        }
        LeftClickTracker.record(player.getUUID(), target.getId(), player.level().getGameTime());
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public void onLivingHurt(LivingHurtEvent event) {
        if (!TbsDamageMultipliedConfig.COMMON.enabled.get()) {
            return;
        }

        Entity victim = event.getEntity();
        if (victim == null || victim.level().isClientSide()) {
            return;
        }

        DamageSource source = event.getSource();
        TbsDamagePath hookPath = TbsProjectileDamageHook.consumeEventScaledPath();
        if (hookPath != null) {
            debug(() -> "path=" + hookPath.id() + " reason=already_scaled_by_hook");
            return;
        }

        ServerPlayer player = TbsDamageClassifier.resolvePlayer(source);
        if (player == null) {
            debug(() -> "path=tbs:unknown reason=non_player_owner");
            return;
        }

        // Exclude genuine left-click melee: that hit already uses full player attributes.
        if (LeftClickTracker.isGenuineLeftClick(
                player.getUUID(), victim.getId(), player.level().getGameTime())) {
            debug(() -> "skip left-click | victim=" + victim.getId());
            return;
        }

        DamageContext ctx = TbsDamageClassifier.buildContext(source, player);
        TbsDamagePath path = TbsDamageClassifier.classify(ctx);
        ScalingMode mode = path.mode();

        switch (mode) {
            case FIXED -> {
                float baseDamage = event.getAmount();
                ModifierPolicy policy = TbsDamageMultipliedConfig.COMMON.policy(path);
                if (!policy.enabled()) {
                    debug(() -> "path=" + path.id() + " reason=disabled");
                    return;
                }
                double result = AttackModifierApplier.applyAttackModifiers(
                    player, baseDamage, policy);
                if (!Double.isFinite(result) || result < 0.0) {
                    return;
                }
                float newAmount = (float) result;
                event.setAmount(newAmount);
                debug(() -> String.format(
                    "path=%s base=%.3f addition=%s multiplyBase=%s multiplyTotal=%s result=%.3f player=%s victim=%d",
                    path.id(), baseDamage, policy.addition(), policy.multiplyBase(),
                    policy.multiplyTotal(), newAmount, player.getGameProfile().getName(),
                    victim.getId()));
            }
            case NATIVE_ATTACK_SCALED -> debug(() -> String.format(
                "path=%s reason=native_attack_scaled victim=%d", path.id(), victim.getId()));
            case IGNORE -> {
                debug(() -> String.format(
                    "path=%s reason=unknown damageType=%s direct=%s causing=%s ability=%s amount=%.3f victim=%d",
                    path.id(),
                    String.valueOf(ctx.damageTypeId()),
                    String.valueOf(ctx.directEntityId()),
                    String.valueOf(ctx.causingEntityId()),
                    ctx.activeAbilityName().orElse("-"),
                    event.getAmount(), victim.getId()));
                maybeLogUnknown(ctx, victim, event.getAmount());
            }
        }
    }

    /**
     * When a TBS-attributable player_attack hit falls through to IGNORE it may be a new
     * TBS path that needs triaging. Log it (once-per-hit) if debugUnknownPaths is on.
     */
    private static void maybeLogUnknown(DamageContext ctx, Entity victim, float amount) {
        if (!TbsDamageMultipliedConfig.COMMON.debugUnknownPaths.get()) {
            return;
        }
        if (!TbsDamageClassifier.PLAYER_ATTACK.equals(ctx.damageTypeId())) {
            return;
        }
        TbsDamageMultiplied.LOG.info(
            "[TBSDamageMultiplied] UNKNOWN TBS damage path | damageType={} | directEntity={} | causingEntity={} | ability={} | amount={} | victim={}",
            String.valueOf(ctx.damageTypeId()),
            String.valueOf(ctx.directEntityId()),
            String.valueOf(ctx.causingEntityId()),
            ctx.activeAbilityName().orElse("-"),
            amount, victim.getId());
    }

    private static void debug(java.util.function.Supplier<String> message) {
        if (TbsDamageMultipliedConfig.COMMON.debug.get()) {
            TbsDamageMultiplied.LOG.info("[TBSDamageMultiplied] {}", message.get());
        }
    }
}
