# TBSDamageMultiplied

A focused Forge 1.20.1 add-on for Torches Become Sunlight (TBS). It fixes only
confirmed player-owned TBS implementation paths whose fixed damage omits the
player's active `generic.attack_damage` modifiers. Vanilla combat and unrelated
mods are not intercepted.

## Damage model

For a confirmed fixed path, the original TBS amount `D` becomes a synthetic
attribute base. The player's current modifiers are replayed in Minecraft 1.20.1
`AttributeInstance` order:

```text
(D + sum(ADDITION))
* (1 + sum(MULTIPLY_BASE))
* product(1 + each MULTIPLY_TOTAL)
```

The player's ordinary ATTACK_DAMAGE base is not used. Main-hand `ItemStack`
modifiers are controlled per operation by config; defaults exclude its `ADDITION`
and include its `MULTIPLY_BASE` / `MULTIPLY_TOTAL`. Modifiers from effects, armor,
Curios, relics, and other standard attribute sources remain. The live
`AttributeInstance` is never mutated.

## Safety model

- Classification is by concrete damage implementation path, not weapon.
- Only `ServerPlayer` owners/casters can be multiplied; TBS bosses and NPCs are
  returned unchanged.
- Known native ATTACK_DAMAGE paths and unknown paths are always left unchanged.
- Narrow coremod hooks carry a stable path ID. A thread-local marker is consumed
  by `LivingHurtEvent`, then cleared after `hurt`, preventing hook + event double
  application.
- Genuine left-click attacks are excluded because vanilla already calculated
  their complete attack attribute.

## Configuration

The common Forge config is generated as
`config/tbs_damage_multiplied-common.toml`. Every fixed path has ordinary
`ForgeConfigSpec.BooleanValue` entries suitable for generic Forge config GUIs:

```toml
[paths.tbs.ice_crystal_hit]
enabled = true
addition = false
multiply_base = true
multiply_total = true
main_hand_addition = false
main_hand_multiply_base = true
main_hand_multiply_total = true
```

All fixed paths default to compatibility scaling enabled, with `ADDITION`
disabled. Main-hand `ADDITION` is excluded by default, while main-hand
`MULTIPLY_BASE` and `MULTIPLY_TOTAL` are included by default; each can be changed
per path. General settings provide a master switch and opt-in path-oriented
diagnostics.

The Rosmontis Embrace player assist path retains its original fixed `16F` as
the base and only replays `MULTIPLY_BASE` / `MULTIPLY_TOTAL`; `ADDITION` is
always ignored for that path because the fixed skill value already represents
the complete skill base.

## Build

Requirements: Java 17, Minecraft 1.20.1, Forge 47.4.23.

```text
gradlew.bat clean build
```

The Windows wrapper automatically uses the sibling Minecraft-instance
`.gradle-home` (offline, because that cache is complete) and
`java/jdk-17.0.20.1+1` when present, while remaining a normal Gradle wrapper
elsewhere. The publishable reobfuscated JAR is written to
`build/libs/`. TBS is a mandatory runtime dependency but none of its classes or
resources are bundled into this JAR.

## Audited target

The integration and exact coremod call sites were audited against the local
`torchesbecomesunlight-0.5.0-hotfix2.jar` (whose embedded mod metadata reports
version `1.20.1-0.4.2`). A future TBS bytecode change deliberately fails the
affected transformer loudly instead of silently patching an uncertain path.

## License

GNU GPL v3. See [LICENSE](LICENSE).
