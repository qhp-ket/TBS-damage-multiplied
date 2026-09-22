package dev.tide.tbsdamagemultiplied.integration.tbs;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.util.Optional;

import dev.tide.tbsdamagemultiplied.TbsDamageMultiplied;
import net.minecraft.world.entity.Entity;

/**
 * The only place that reaches into TBS internals. Everything is done through
 * reflection / {@link MethodHandle} so that a TBS update which renames or removes a
 * class or method degrades to "no active ability detected" (a warning, once) rather
 * than crashing the game.
 *
 * <p>Reflection targets, verified against {@code torchesbecomesunlight-0.5.0-hotfix2}:</p>
 * <ul>
 *   <li>{@code CapabilityHandle.ABILITY_CAPABILITY} (static field, a Forge Capability)</li>
 *   <li>{@code CapabilityHandle.getCapability(Entity, Capability)} (static)</li>
 *   <li>{@code AbilityCapability$IAbilityCapability.getActiveAbility()} -> Ability</li>
 *   <li>{@code Ability.getAbilityType()} -> AbilityType</li>
 *   <li>{@code AbilityType.getName()} -> String</li>
 * </ul>
 */
public final class TbsAbilityAdapter {
    private static final String CAPABILITY_HANDLE =
        "com.freefish.torchesbecomesunlight.server.capability.CapabilityHandle";
    private static final String ABILITY_CAPABILITY_INTERFACE =
        "com.freefish.torchesbecomesunlight.server.capability.AbilityCapability$IAbilityCapability";
    private static final String ABILITY =
        "com.freefish.torchesbecomesunlight.server.ability.Ability";
    private static final String ABILITY_TYPE =
        "com.freefish.torchesbecomesunlight.server.ability.AbilityType";
    private static final String CAPABILITY =
        "net.minecraftforge.common.capabilities.Capability";

    private static volatile boolean initialized;
    private static volatile boolean available;

    private static Object abilityCapabilityToken;
    private static MethodHandle getCapability;   // (Entity, Capability) -> IAbilityCapability
    private static MethodHandle getActiveAbility; // (IAbilityCapability) -> Ability (declared on IAbilityCapability)
    private static MethodHandle getAbilityType;   // (Ability) -> AbilityType
    private static MethodHandle getName;          // (AbilityType) -> String

    private TbsAbilityAdapter() {
    }

    /** True once TBS classes were resolved successfully at least once. */
    public static boolean isAvailable() {
        ensureInitialized();
        return available;
    }

    /**
     * The registry-style name of the ability the entity is currently using, if any.
     * Returns empty when TBS is absent, the reflection failed, or nothing is active.
     */
    public static Optional<String> getActiveAbilityName(Entity entity) {
        if (entity == null) {
            return Optional.empty();
        }
        ensureInitialized();
        if (!available) {
            return Optional.empty();
        }

        try {
            Object capability = getCapability.invoke(entity, abilityCapabilityToken);
            if (capability == null) {
                return Optional.empty();
            }
            Object ability = getActiveAbility.invoke(capability);
            if (ability == null) {
                return Optional.empty();
            }
            Object type = getAbilityType.invoke(ability);
            if (type == null) {
                return Optional.empty();
            }
            Object name = getName.invoke(type);
            return name == null ? Optional.empty() : Optional.of(name.toString());
        } catch (Throwable t) {
            // A live failure (e.g. signature drift) should not crash combat.
            warnOnce("[TBSDamageMultiplied] active-ability lookup failed", t);
            return Optional.empty();
        }
    }

    private static void ensureInitialized() {
        if (initialized) {
            return;
        }
        synchronized (TbsAbilityAdapter.class) {
            if (initialized) {
                return;
            }
            try {
                ClassLoader loader = TbsAbilityAdapter.class.getClassLoader();
                Class<?> capabilityHandle = Class.forName(CAPABILITY_HANDLE, false, loader);
                Class<?> abilityCapabilityInterface =
                    Class.forName(ABILITY_CAPABILITY_INTERFACE, false, loader);
                Class<?> abilityClass = Class.forName(ABILITY, false, loader);
                Class<?> abilityTypeClass = Class.forName(ABILITY_TYPE, false, loader);
                Class<?> capabilityClass = Class.forName(CAPABILITY, false, loader);

                Field token = capabilityHandle.getField("ABILITY_CAPABILITY");
                abilityCapabilityToken = token.get(null);

                MethodHandles.Lookup lookup = MethodHandles.publicLookup();

                getCapability = lookup.unreflect(
                    capabilityHandle.getMethod("getCapability", Entity.class, capabilityClass));
                getActiveAbility = lookup.unreflect(
                    abilityCapabilityInterface.getMethod("getActiveAbility"));
                getAbilityType = lookup.unreflect(
                    abilityClass.getMethod("getAbilityType"));
                getName = lookup.unreflect(
                    abilityTypeClass.getMethod("getName"));

                available = abilityCapabilityToken != null;
                if (available) {
                    TbsDamageMultiplied.LOG.info("[TBSDamageMultiplied] TBS ability adapter linked.");
                } else {
                    TbsDamageMultiplied.LOG.warn(
                        "[TBSDamageMultiplied] TBS ABILITY_CAPABILITY was null; ability attribution disabled.");
                }
            } catch (ClassNotFoundException e) {
                available = false;
                TbsDamageMultiplied.LOG.info(
                    "[TBSDamageMultiplied] TBS not present; ability attribution disabled.");
            } catch (Throwable t) {
                available = false;
                TbsDamageMultiplied.LOG.warn(
                    "[TBSDamageMultiplied] could not link TBS ability adapter: {}", t.toString());
            } finally {
                initialized = true;
            }
        }
    }

    private static volatile boolean warned;

    private static void warnOnce(String message, Throwable t) {
        if (!warned) {
            warned = true;
            TbsDamageMultiplied.LOG.warn("{}: {}", message, t.toString());
        }
    }
}
