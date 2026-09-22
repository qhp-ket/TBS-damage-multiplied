package dev.tide.tbsdamagemultiplied;

/**
 * How a single TBS damage path relates to the player's {@code ATTACK_DAMAGE}.
 */
public enum ScalingMode {
    /**
     * TBS did NOT apply the player's ATTACK_DAMAGE modifiers to this hit (a fixed
     * configured number). We re-apply them via {@link AttackModifierApplier}.
     */
    FIXED,

    /**
     * TBS already read the player's ATTACK_DAMAGE for this hit (e.g. mob_attack paths).
     * Left completely untouched to avoid double scaling.
     */
    NATIVE_ATTACK_SCALED,

    /**
     * Unknown or out of scope. Left completely untouched. Optionally logged when
     * {@code debugUnknownPaths} is enabled so new TBS paths can be triaged.
     */
    IGNORE
}
