package dev.tide.tbsdamagemultiplied;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.google.common.collect.Multimap;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Re-applies the player's current {@code ATTACK_DAMAGE} modifiers to a TBS fixed
 * damage value, treating that fixed value as a temporary attribute base.
 *
 * <p>This is not "damage times a multiplier". The TBS fixed number {@code D} takes
 * the place of the vanilla base (normally 1 for a player), and every currently active
 * ATTACK_DAMAGE modifier is folded on top in the exact order vanilla
 * {@link AttributeInstance} uses:</p>
 *
 * <pre>
 *   D1 = D + sum(ADDITION)
 *   D2 = D1 * (1 + sum(MULTIPLY_BASE))
 *   D3 = D2 * product(1 + MULTIPLY_TOTAL_i)
 * </pre>
 *
 * <p>Main-hand ATTACK_DAMAGE operations are controlled independently by the path
 * policy. The defaults exclude the weapon's flat ADDITION so it is not counted a
 * second time, while allowing its MULTIPLY_BASE/MULTIPLY_TOTAL layers to participate.
 * Everything else a player has (Strength as ADDITION, attack-boost effects as
 * MULTIPLY_BASE, relics as MULTIPLY_TOTAL, Curios, Origins, potions, other gear)
 * remains available through the standard ATTACK_DAMAGE attribute and participates
 * when its corresponding operation is enabled by the path policy. The real
 * AttributeMap is never mutated; all math is done in memory.</p>
 */
public final class AttackModifierApplier {
    private AttackModifierApplier() {
    }

    /**
     * Folds the player's ATTACK_DAMAGE modifiers onto {@code baseDamage}. Returns
     * {@code baseDamage} unchanged on null, invalid, or runtime-failing reads so a
     * broken third-party attribute provider never distorts damage.
     */
    public static double applyAttackModifiers(
        ServerPlayer player,
        double baseDamage,
        ModifierPolicy policy) {
        if (!policy.enabled()) {
            return baseDamage;
        }
        try {
            AttributeInstance attack = player.getAttribute(Attributes.ATTACK_DAMAGE);
            if (attack == null) {
                return baseDamage;
            }

            Set<UUID> excluded = mainHandAttackModifierIds(player);

            double addition = 0.0;
            double multiplyBase = 0.0;
            List<Double> multiplyTotal = new ArrayList<>();

            for (AttributeModifier modifier : attack.getModifiers()) {
                if (excluded.contains(modifier.getId())
                    && !includeMainHandModifier(policy, modifier.getOperation())) {
                    continue;
                }
                switch (modifier.getOperation()) {
                    case ADDITION -> addition += modifier.getAmount();
                    case MULTIPLY_BASE -> multiplyBase += modifier.getAmount();
                    case MULTIPLY_TOTAL -> multiplyTotal.add(modifier.getAmount());
                }
            }

            // Vanilla AttributeInstance.calculateValue order: base+ADDITION, then
            // *(1+sum MULTIPLY_BASE), then a separate *(1+amount) per MULTIPLY_TOTAL.
            // Each operation can be disabled independently via config.
            double value = baseDamage;
            if (policy.addition()) {
                value += addition;
            }
            if (policy.multiplyBase()) {
                value = value + value * multiplyBase;
            }
            if (policy.multiplyTotal()) {
                for (double amount : multiplyTotal) {
                    value = value * (1.0 + amount);
                }
            }

            if (!Double.isFinite(value)) {
                return baseDamage;
            }
            return Math.max(0.0, value);
        } catch (RuntimeException ignored) {
            return baseDamage;
        }
    }

    private static boolean includeMainHandModifier(
        ModifierPolicy policy,
        AttributeModifier.Operation operation) {
        return switch (operation) {
            case ADDITION -> policy.includeMainHandAddition();
            case MULTIPLY_BASE -> policy.includeMainHandMultiplyBase();
            case MULTIPLY_TOTAL -> policy.includeMainHandMultiplyTotal();
        };
    }

    private static Set<UUID> mainHandAttackModifierIds(Player player) {
        Set<UUID> excluded = new HashSet<>();
        ItemStack stack = player.getMainHandItem();
        if (stack == null || stack.isEmpty()) {
            return excluded;
        }

        Multimap<Attribute, AttributeModifier> modifiers =
            stack.getAttributeModifiers(EquipmentSlot.MAINHAND);
        for (AttributeModifier modifier : modifiers.get(Attributes.ATTACK_DAMAGE)) {
            excluded.add(modifier.getId());
        }
        return excluded;
    }
}
