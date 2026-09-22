package dev.tide.tbsdamagemultiplied;

/** Per-damage-path selection of the vanilla ATTACK_DAMAGE operations to replay. */
public record ModifierPolicy(
    boolean enabled,
    boolean addition,
    boolean multiplyBase,
    boolean multiplyTotal,
    boolean includeMainHandAddition,
    boolean includeMainHandMultiplyBase,
    boolean includeMainHandMultiplyTotal
) {
}
