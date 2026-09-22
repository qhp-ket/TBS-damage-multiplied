package dev.tide.tbsdamagemultiplied;

import com.mojang.logging.LogUtils;
import dev.tide.tbsdamagemultiplied.integration.tbs.TbsDamageClassifier;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * TBSDamageMultiplied.
 *
 * <p>Torches Become Sunlight (TBS) deals player-driven skill damage in two very
 * different ways. Some skills read the player's {@code ATTACK_DAMAGE} attribute
 * (native scaling); those already grow with the player's gear and are left alone.
 * Other skills deal a fixed configured number that ignores the player entirely.</p>
 *
 * <p>For that second family (FIXED paths) this mod takes the original fixed amount as
 * a temporary {@code ATTACK_DAMAGE} base and re-applies the player's current
 * ATTACK_DAMAGE modifiers in vanilla order, with main-hand operations controlled by
 * the path policy. By default its flat ADDITION is excluded while its two multiplier
 * operations participate. Paths that already read ATTACK_DAMAGE (NATIVE) and unknown
 * paths (IGNORE) are left untouched, so the Pathfinder Gun's two hits split correctly
 * without excluding the whole weapon. See {@code integration.tbs.TbsDamageClassifier}.</p>
 */
@Mod(TbsDamageMultiplied.ID)
public final class TbsDamageMultiplied {
    public static final String ID = "tbs_damage_multiplied";
    public static final Logger LOG = LogUtils.getLogger();

    public TbsDamageMultiplied() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, TbsDamageMultipliedConfig.SPEC);

        var modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(TbsDamageMultiplied::onConfigLoad);
        modBus.addListener(TbsDamageMultiplied::onConfigReload);

        MinecraftForge.EVENT_BUS.register(new TbsDamageHandler());
    }

    private static void onConfigLoad(ModConfigEvent.Loading event) {
        TbsDamageClassifier.invalidateCaches();
    }

    private static void onConfigReload(ModConfigEvent.Reloading event) {
        TbsDamageClassifier.invalidateCaches();
    }
}
