package dev.tide.tbsdamagemultiplied;

import dev.tide.tbsdamagemultiplied.integration.tbs.TbsDamageClassifier;
import dev.tide.tbsdamagemultiplied.integration.tbs.TbsAbilityAdapter;
import dev.tide.tbsdamagemultiplied.integration.tbs.TbsDamagePath;
import dev.tide.tbsdamagemultiplied.hook.TbsDamageProvenance;
import dev.tide.tbsdamagemultiplied.hook.TbsProjectileDamageHook;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
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

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLivingHurt(LivingHurtEvent event) {
        Entity victim = event.getEntity();
        if (victim == null || victim.level().isClientSide()) {
            return;
        }

        DamageSource source = event.getSource();
        ServerPlayer player = TbsDamageClassifier.resolvePlayer(source);
        if (player == null) {
            debug(() -> "path=tbs:unknown reason=non_player_owner");
            return;
        }

        // Observe explicit provenance before any other listener can synchronously emit
        // nested damage. A non-matching nested event leaves the outer frame untouched;
        // the audited call's post-call finish owns frame removal.
        TbsDamageProvenance.Frame frame =
            TbsProjectileDamageHook.observeMatchingFrame(player, victim, source);
        if (frame != null) {
            if (!TbsDamageMultipliedConfig.COMMON.enabled.get()) {
                return;
            }
            if (frame.alreadyScaled()) {
                debug(() -> "path=" + frame.path().id() + " reason=already_scaled_by_hook");
                return;
            }
            applyFixedPath(event, player, frame.path());
            return;
        }

        if (!TbsDamageMultipliedConfig.COMMON.enabled.get()) {
            return;
        }

        // Exclude genuine left-click melee: that hit already uses full player attributes.
        if (isVanillaPlayerAttack(source, player)
            && LeftClickTracker.isGenuineLeftClick(
                player.getUUID(), victim.getId(), player.level().getGameTime())) {
            debug(() -> "skip left-click | victim=" + victim.getId());
            return;
        }

        DamageContext ctx = TbsDamageClassifier.buildContext(source, player);
        TbsDamagePath path = TbsDamageClassifier.classify(ctx);
        ScalingMode mode = path.mode();

        switch (mode) {
            case FIXED -> applyFixedPath(event, player, path);
            case NATIVE_ATTACK_SCALED -> debug(() -> String.format(
                "path=%s reason=native_attack_scaled victim=%d", path.id(), victim.getId()));
            case IGNORE -> {
                debug(() -> String.format(
                    "path=%s reason=unknown damageType=%s direct=%s causing=%s ability=%s amount=%.3f victim=%d",
                    path.id(),
                    String.valueOf(ctx.damageTypeId()),
                    String.valueOf(ctx.directEntityId()),
                    String.valueOf(ctx.causingEntityId()),
                    TbsAbilityAdapter.getActiveAbilityName(player).orElse("-"),
                    event.getAmount(), victim.getId()));
                maybeLogUnknown(ctx, player, victim, event.getAmount());
            }
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() != null) {
            LeftClickTracker.forget(event.getEntity().getUUID());
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        LeftClickTracker.clear();
        TbsProjectileDamageHook.clearProvenance();
    }

    private static void applyFixedPath(
        LivingHurtEvent event,
        ServerPlayer player,
        TbsDamagePath path) {
        if (path.mode() != ScalingMode.FIXED) {
            debug(() -> "path=" + path.id() + " reason=unexpected_provenance_mode");
            return;
        }
        float baseDamage = event.getAmount();
        ModifierPolicy policy = TbsDamageMultipliedConfig.COMMON.policy(path);
        if (!policy.enabled()) {
            debug(() -> "path=" + path.id() + " reason=disabled");
            return;
        }
        double result = AttackModifierApplier.applyAttackModifiers(player, baseDamage, policy);
        if (!Double.isFinite(result) || result < 0.0) {
            return;
        }
        float newAmount = (float) result;
        event.setAmount(newAmount);
        debug(() -> String.format(
            "path=%s base=%.3f addition=%s multiplyBase=%s multiplyTotal=%s result=%.3f player=%s victim=%d",
            path.id(), baseDamage, policy.addition(), policy.multiplyBase(),
            policy.multiplyTotal(), newAmount, player.getGameProfile().getName(),
            event.getEntity().getId()));
    }

    /**
     * When an unmarked player_attack hit falls through to IGNORE it may be a new TBS
     * path that needs triaging. Log it (once-per-hit) if debugUnknownPaths is on.
     */
    private static void maybeLogUnknown(
        DamageContext ctx, ServerPlayer player, Entity victim, float amount) {
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
            TbsAbilityAdapter.getActiveAbilityName(player).orElse("-"),
            amount, victim.getId());
    }

    private static boolean isVanillaPlayerAttack(DamageSource source, ServerPlayer player) {
        return TbsDamageClassifier.PLAYER_ATTACK.equals(
                TbsDamageClassifier.damageTypeId(source))
            && source.getDirectEntity() == player
            && source.getEntity() == player;
    }

    private static void debug(java.util.function.Supplier<String> message) {
        if (TbsDamageMultipliedConfig.COMMON.debug.get()) {
            TbsDamageMultiplied.LOG.info("[TBSDamageMultiplied] {}", message.get());
        }
    }
}
