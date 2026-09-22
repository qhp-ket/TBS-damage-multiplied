package dev.tide.tbsdamagemultiplied;

import java.util.EnumMap;
import java.util.Map;

import dev.tide.tbsdamagemultiplied.integration.tbs.TbsDamagePath;
import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/** Forge-native, GUI-friendly configuration with one policy section per fixed path. */
public final class TbsDamageMultipliedConfig {
    public static final ForgeConfigSpec SPEC;
    public static final Common COMMON;

    static {
        Pair<Common, ForgeConfigSpec> pair =
            new ForgeConfigSpec.Builder().configure(Common::new);
        COMMON = pair.getLeft();
        SPEC = pair.getRight();
    }

    private TbsDamageMultipliedConfig() {
    }

    public static final class Common {
        public final ForgeConfigSpec.BooleanValue enabled;
        public final ForgeConfigSpec.BooleanValue debug;
        public final ForgeConfigSpec.BooleanValue debugUnknownPaths;
        private final Map<TbsDamagePath, PathConfig> paths =
            new EnumMap<>(TbsDamagePath.class);

        Common(ForgeConfigSpec.Builder builder) {
            builder.comment(
                "TBSDamageMultiplied",
                "Only confirmed player-owned TBS fixed-damage implementation paths are changed.",
                "Paths that already read ATTACK_DAMAGE and unknown paths remain untouched.");

            builder.push("general");
            enabled = builder.comment("Master switch for every path.").define("enabled", true);
            debug = builder.comment("Log recognised path decisions and calculations.")
                .define("debug", false);
            debugUnknownPaths = builder.comment(
                "Log player-owned TBS-looking hits which are not a confirmed path.")
                .define("debug_unknown_paths", false);
            builder.pop();

            builder.push("paths");
            builder.push("tbs");
            for (TbsDamagePath path : TbsDamagePath.values()) {
                if (path.configurable()) {
            paths.put(path, new PathConfig(builder, path));
                }
            }
            builder.pop(2);
        }

        public ModifierPolicy policy(TbsDamagePath path) {
            PathConfig config = paths.get(path);
            return config == null
                ? new ModifierPolicy(false, false, false, false, false, false, false)
                : config.policy();
        }
    }

    private static final class PathConfig {
        private final ForgeConfigSpec.BooleanValue enabled;
        private final ForgeConfigSpec.BooleanValue addition;
        private final ForgeConfigSpec.BooleanValue multiplyBase;
        private final ForgeConfigSpec.BooleanValue multiplyTotal;
        private final ForgeConfigSpec.BooleanValue includeMainHandAddition;
        private final ForgeConfigSpec.BooleanValue includeMainHandMultiplyBase;
        private final ForgeConfigSpec.BooleanValue includeMainHandMultiplyTotal;
        private final boolean additionSupported;

        PathConfig(ForgeConfigSpec.Builder builder, TbsDamagePath path) {
            additionSupported = path != TbsDamagePath.ROSMONTIS_EMBRACE_ASSIST;
            builder.comment("Policy for " + path.id() + ".").push(path.key());
            enabled = builder.comment("Apply compatibility scaling to this path.")
                .define("enabled", true);
            addition = builder.comment(additionSupported
                    ? "Replay ATTACK_DAMAGE ADDITION modifiers."
                    : "Not replayed: the fixed 16F already contains the skill base value.")
                .define("addition", false);
            multiplyBase = builder.comment("Replay ATTACK_DAMAGE MULTIPLY_BASE modifiers.")
                .define("multiply_base", true);
            multiplyTotal = builder.comment("Replay ATTACK_DAMAGE MULTIPLY_TOTAL modifiers.")
                .define("multiply_total", true);
            includeMainHandAddition = builder.comment(
                    "Include ATTACK_DAMAGE ADDITION modifiers supplied by the main-hand item.")
                .define("main_hand_addition", false);
            includeMainHandMultiplyBase = builder.comment(
                    "Include ATTACK_DAMAGE MULTIPLY_BASE modifiers supplied by the main-hand item.")
                .define("main_hand_multiply_base", true);
            includeMainHandMultiplyTotal = builder.comment(
                    "Include ATTACK_DAMAGE MULTIPLY_TOTAL modifiers supplied by the main-hand item.")
                .define("main_hand_multiply_total", true);
            builder.pop();
        }

        ModifierPolicy policy() {
            return new ModifierPolicy(
                enabled.get(), additionSupported && addition.get(),
                multiplyBase.get(), multiplyTotal.get(),
                includeMainHandAddition.get(), includeMainHandMultiplyBase.get(),
                includeMainHandMultiplyTotal.get());
        }
    }
}
