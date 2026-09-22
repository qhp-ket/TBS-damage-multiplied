# TBSDamageMultiplied

A focused Forge 1.20.1 add-on for Torches Become Sunlight (TBS). It fixes only
confirmed player-owned TBS implementation paths whose fixed damage omits the
player's active `generic.attack_damage` modifiers. The classifier is intentionally
conservative and is designed to leave vanilla combat, unrelated mods, native TBS
scaling paths, and unknown paths unchanged.

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
- Narrow coremod hooks carry a stable path ID and target/owner provenance when the
  audited call site exposes both. A per-thread stack frame is consumed only by its
  matching `LivingHurtEvent`, then balanced after `hurt`, preventing hook + event
  double application without crossing nested damage calls.
- Ability-driven fixed hits are marked at their audited `playerAttack -> hurt` call
  sites; their path is never inferred merely from whichever TBS ability is active.
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

The repository uses the standard Gradle wrapper. Set `JAVA_HOME` (Java 17) and,
when needed, `GRADLE_USER_HOME` in your environment; `--offline` is opt-in rather
than being forced by the wrapper. The publishable reobfuscated JAR is written to
`build/libs/`. TBS is a mandatory runtime dependency but none of its classes or
resources are bundled into this JAR.

## Audited target

The integration and exact coremod call sites were audited against the local
`torchesbecomesunlight-0.5.0-hotfix2.jar` (whose embedded mod metadata reports
version `1.20.1-0.4.2`). A future TBS bytecode change deliberately fails the
affected transformer loudly instead of silently patching an uncertain path.

## Credits

Torches Become Sunlight is created by free fish and its contributors.

This project is an unofficial compatibility add-on and is not affiliated with or
endorsed by the Torches Become Sunlight developers.

## License

GNU General Public License v3.0. See [LICENSE](LICENSE).

Torches Become Sunlight is distributed under its own license. This project is
released under GPL-3.0.
