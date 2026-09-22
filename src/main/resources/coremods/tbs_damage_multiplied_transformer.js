// Narrow, path-aware hooks for TBS 0.5.0-hotfix2 fixed-damage call sites.
var Opcodes = Java.type('org.objectweb.asm.Opcodes');
var InsnList = Java.type('org.objectweb.asm.tree.InsnList');
var VarInsnNode = Java.type('org.objectweb.asm.tree.VarInsnNode');
var MethodInsnNode = Java.type('org.objectweb.asm.tree.MethodInsnNode');
var FieldInsnNode = Java.type('org.objectweb.asm.tree.FieldInsnNode');
var LdcInsnNode = Java.type('org.objectweb.asm.tree.LdcInsnNode');
var InsnNode = Java.type('org.objectweb.asm.tree.InsnNode');
var AbstractInsnNode = Java.type('org.objectweb.asm.tree.AbstractInsnNode');

var HOOK_OWNER = 'dev/tide/tbsdamagemultiplied/hook/TbsProjectileDamageHook';
var HOOK_NAME = 'scaleWithOwner';
var HOOK_DESC = '(FLnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;Ljava/lang/String;Z)F';
var MARK_FIXED_NAME = 'markFixedPath';
var MARK_FIXED_DESC = '(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;Ljava/lang/String;)V';
var CLEAR_NAME = 'finishFrame';
var CLEAR_DESC = '()V';
var GET_OWNER = 'm_19749_';
var GET_OWNER_DESC = '()Lnet/minecraft/world/entity/Entity;';
var HURT = 'm_6469_';
var ACTUALLY_HURT = 'm_6475_';
var PLAYER_ATTACK = 'm_269075_';
var PLAYER_ATTACK_DESC = '(Lnet/minecraft/world/entity/player/Player;)Lnet/minecraft/world/damagesource/DamageSource;';
var DAMAGE_SOURCES = 'm_269291_';
var DAMAGE_SOURCES_DESC = '()Lnet/minecraft/world/damagesource/DamageSources;';

function initializeCoreMod() {
    return {
        'tbs_bullet_direct_hit': projectileHook(
            'com.freefish.torchesbecomesunlight.server.entity.projectile.Bullet',
            'hitEntity', HURT, afterPlayerAttackSource, 'tbs:bullet_direct_hit', true, 3),
        'tbs_ice_crystal_hit': projectileHook(
            'com.freefish.torchesbecomesunlight.server.entity.projectile.IceCrystal',
            'hitEntity', HURT, firstMatch, 'tbs:ice_crystal_hit', true, 1),
        'tbs_lighting_boom': projectileHook(
            'com.freefish.torchesbecomesunlight.server.entity.projectile.LightingBoom',
            'hitEntity', HURT, firstMatch, 'tbs:lighting_boom', true, 7),
        'tbs_halberd_flight_hit': projectileHook(
            'com.freefish.torchesbecomesunlight.server.entity.projectile.HalberdOTIEntity',
            'm_5790_', HURT, firstMatch, 'tbs:halberd_flight_hit', true, 2),
        'tbs_machete_followup': casterHook(
            'com.freefish.torchesbecomesunlight.server.entity.effect.PlayerSkillHelpEntity',
            'doDemonAttack', ACTUALLY_HURT, firstMatch, 'tbs:machete_followup', false),
        'tbs_machete_primary': abilityFixedPathHook(
            'com.freefish.torchesbecomesunlight.server.ability.abilities.UseMachete1Ability',
            'tickUsing', 'tbs:machete_primary'),
        'tbs_ice_broadsword': abilityFixedPathHook(
            'com.freefish.torchesbecomesunlight.server.ability.abilities.UseIceBroadswordAbility',
            'doRangeAttack', 'tbs:ice_broadsword'),
        'tbs_halberd_chi': abilityFixedPathHook(
            'com.freefish.torchesbecomesunlight.server.ability.abilities.UseHalberdChiAbility',
            'doRangeAttack', 'tbs:halberd_chi'),
        'tbs_halberd_wind': abilityFixedPathHook(
            'com.freefish.torchesbecomesunlight.server.ability.abilities.UseSHalberdWindAbility',
            'tickUsing', 'tbs:halberd_wind'),
        'tbs_halberd_light_wind': abilityFixedPathHook(
            'com.freefish.torchesbecomesunlight.server.ability.abilities.UseSHalberdWindLightAbility',
            'tickUsing', 'tbs:halberd_light_wind'),
        'tbs_gravestone_slash': abilityFixedPathHook(
            'com.freefish.torchesbecomesunlight.server.ability.abilities.UseGraveStoneSlashAbility',
            'doRangeAttack', 'tbs:gravestone_slash'),
        'tbs_rosmontis_embrace_assist': rosmontisEmbraceAssistHook()
    };
}

function firstMatch(method, targetCall) {
    for (var insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
        if (isCall(insn, targetCall)) return insn;
    }
    return null;
}

function afterPlayerAttackSource(method, targetCall) {
    var seen = false;
    for (var insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
        if (isCall(insn, PLAYER_ATTACK)) seen = true;
        else if (seen && isCall(insn, targetCall)) return insn;
    }
    return null;
}

function isCall(insn, name) {
    return insn != null && insn.getType() == AbstractInsnNode.METHOD_INSN && insn.name == name;
}

function isCallWithDesc(insn, name, desc) {
    return isCall(insn, name) && insn.desc == desc;
}

function previousCode(insn) {
    if (insn == null) return null;
    var previous = insn.getPrevious();
    while (previous != null
        && (previous.getType() == AbstractInsnNode.LABEL
            || previous.getType() == AbstractInsnNode.FRAME
            || previous.getType() == AbstractInsnNode.LINE)) {
        previous = previous.getPrevious();
    }
    return previous;
}

function isLdcNumber(insn, expected) {
    if (insn == null || insn.getType() != AbstractInsnNode.LDC_INSN || insn.cst == null) {
        return false;
    }
    return Math.abs(insn.cst.doubleValue() - expected) < 0.000001;
}

function isAload(insn) {
    return insn != null
        && insn.getType() == AbstractInsnNode.VAR_INSN
        && insn.getOpcode() == Opcodes.ALOAD;
}

function isThisLoad(insn) {
    return isAload(insn) && insn.var == 0;
}

function isZeroFloat(insn) {
    return insn != null && (insn.getOpcode() == Opcodes.FCONST_0 || isLdcNumber(insn, 0.0));
}

function isFalse(insn) {
    return insn != null && insn.getOpcode() == Opcodes.ICONST_0;
}

/**
 * Finds one direct ability hurt call with the exact playerAttack source setup:
 * target, player.damageSources(), player, playerAttack(player), amount, hurt.
 * The target and player locals are captured so the provenance frame can match the
 * later event rather than inferring it from the player's active ability.
 */
function findAbilityFixedHurt(insn) {
    if (!isCall(insn, HURT)) return null;

    for (var cursor = previousCode(insn); cursor != null; cursor = previousCode(cursor)) {
        if (!isCallWithDesc(cursor, PLAYER_ATTACK, PLAYER_ATTACK_DESC)) continue;

        var ownerLoad = previousCode(cursor);
        var damageSourcesCall = previousCode(ownerLoad);
        var playerLoad = previousCode(damageSourcesCall);
        var targetLoad = previousCode(playerLoad);
        if (isAload(ownerLoad) && isCallWithDesc(
                damageSourcesCall, DAMAGE_SOURCES, DAMAGE_SOURCES_DESC)
            && isAload(playerLoad) && isAload(targetLoad)
            && ownerLoad.var == playerLoad.var) {
            return {'call': insn, 'targetVar': targetLoad.var, 'ownerVar': ownerLoad.var};
        }
    }
    return null;
}

function abilityFixedPathHook(className, methodName, pathId) {
    return {
        'target': {'type': 'CLASS', 'name': className},
        'transformer': function(node) {
            var matches = [];
            for (var mi = 0; mi < node.methods.size(); mi++) {
                var method = node.methods.get(mi);
                if (method.name != methodName) continue;
                for (var insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                    var match = findAbilityFixedHurt(insn);
                    if (match != null) matches.push({'method': method, 'match': match});
                }
            }
            if (matches.length != 1) {
                throw new Error('tbs_damage_multiplied: expected exactly one playerAttack hurt injection in '
                    + className + '.' + methodName + ', found ' + matches.length);
            }

            var entry = matches[0];
            var before = new InsnList();
            before.add(new VarInsnNode(Opcodes.ALOAD, entry.match.targetVar));
            before.add(new VarInsnNode(Opcodes.ALOAD, entry.match.ownerVar));
            before.add(new LdcInsnNode(pathId));
            before.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC, HOOK_OWNER, MARK_FIXED_NAME, MARK_FIXED_DESC, false));
            entry.method.instructions.insertBefore(entry.match.call, before);
            entry.method.instructions.insert(entry.match.call, new MethodInsnNode(
                Opcodes.INVOKESTATIC, HOOK_OWNER, CLEAR_NAME, CLEAR_DESC, false));
            return node;
        }
    };
}

/**
 * Finds the player-only Rosmontis Embrace calls without matching arbitrary 16F constants.
 * Both 0.5.0-hotfix2 branches load the exact (this, player, 4.0D, 115.0D, 16.0F,
 * 0.0F, false) doRangeAttackAngle signature; the NPC branches load an ATTACK_DAMAGE
 * local instead of the 16.0F constant.
 */
function findRosmontisEmbraceAssistCall(insn, className) {
    var internalName = className.replace(/\./g, '/');
    if (insn == null || insn.getType() != AbstractInsnNode.METHOD_INSN
        || insn.owner != internalName
        || insn.name != 'doRangeAttackAngle'
        || insn.desc != '(Lnet/minecraft/world/entity/LivingEntity;DDFFZ)Z') {
        return null;
    }

    var falseInsn = previousCode(insn);
    var zeroFloat = previousCode(falseInsn);
    var amount = previousCode(zeroFloat);
    var angle = previousCode(amount);
    var range = previousCode(angle);
    var ownerLoad = previousCode(range);
    var receiver = previousCode(ownerLoad);

    if (!isFalse(falseInsn) || !isZeroFloat(zeroFloat) || !isLdcNumber(amount, 16.0)
        || !isLdcNumber(angle, 115.0) || !isLdcNumber(range, 4.0)
        || !isAload(ownerLoad) || !isThisLoad(receiver)) {
        return null;
    }
    return {'call': insn, 'amount': amount, 'ownerVar': ownerLoad.var};
}

function rosmontisEmbraceAssistHook() {
    var className = 'com.freefish.torchesbecomesunlight.server.entity.rhodesIsland.rosmontis.RosmontisInstallation';
    var methodName = 'm_8119_';
    return {
        'target': {'type': 'CLASS', 'name': className},
        'transformer': function(node) {
            var matches = [];
            for (var mi = 0; mi < node.methods.size(); mi++) {
                var method = node.methods.get(mi);
                if (method.name != methodName) continue;
                for (var insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                    var match = findRosmontisEmbraceAssistCall(insn, className);
                    if (match != null) matches.push({'method': method, 'match': match});
                }
            }

            if (matches.length != 2) {
                throw new Error('tbs_damage_multiplied: expected exactly two Rosmontis Embrace '
                    + 'assist injections in ' + className + '.' + methodName + ', found '
                    + matches.length);
            }

            for (var i = 0; i < matches.length; i++) {
                var entry = matches[i];
                var before = new InsnList();
                before.add(new VarInsnNode(Opcodes.ALOAD, entry.match.ownerVar));
                before.add(new InsnNode(Opcodes.ACONST_NULL));
                before.add(new LdcInsnNode('tbs:rosmontis_embrace_assist'));
                before.add(new InsnNode(Opcodes.ICONST_1));
                before.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC, HOOK_OWNER, HOOK_NAME, HOOK_DESC, false));
                entry.method.instructions.insert(entry.match.amount, before);

                var clear = new MethodInsnNode(
                    Opcodes.INVOKESTATIC, HOOK_OWNER, CLEAR_NAME, CLEAR_DESC, false);
                entry.method.instructions.insert(entry.match.call, clear);
            }
            return node;
        }
    };
}

function projectileHook(className, methodName, targetCall, locate, pathId, emitsEvent, targetVar) {
    return buildTransformer(className, methodName, targetCall, locate, pathId, emitsEvent,
        function(node) {
            var load = new InsnList();
            load.add(new VarInsnNode(Opcodes.ALOAD, 0));
            load.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL, node.name, GET_OWNER, GET_OWNER_DESC, false));
            return load;
        },
        function(node) {
            var load = new InsnList();
            load.add(new VarInsnNode(Opcodes.ALOAD, targetVar));
            return load;
        });
}

function casterHook(className, methodName, targetCall, locate, pathId, emitsEvent) {
    return buildTransformer(className, methodName, targetCall, locate, pathId, emitsEvent,
        function(node) {
            var load = new InsnList();
            load.add(new VarInsnNode(Opcodes.ALOAD, 0));
            load.add(new FieldInsnNode(
                Opcodes.GETFIELD, node.name, 'caster',
                'Lnet/minecraft/world/entity/LivingEntity;'));
            return load;
        },
        function(node) {
            var load = new InsnList();
            load.add(new InsnNode(Opcodes.ACONST_NULL));
            return load;
        });
}

function buildTransformer(
    className, methodName, targetCall, locate, pathId, emitsEvent, ownerLoad, targetLoad) {
    return {
        'target': {'type': 'CLASS', 'name': className},
        'transformer': function(node) {
            var patched = 0;
            for (var mi = 0; mi < node.methods.size(); mi++) {
                var method = node.methods.get(mi);
                if (method.name != methodName) continue;
                var target = locate(method, targetCall);
                if (target == null) continue;

                var before = ownerLoad(node);
                before.add(targetLoad(node));
                before.add(new LdcInsnNode(pathId));
                before.add(new InsnNode(emitsEvent ? Opcodes.ICONST_1 : Opcodes.ICONST_0));
                before.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC, HOOK_OWNER, HOOK_NAME, HOOK_DESC, false));
                method.instructions.insertBefore(target, before);

                if (emitsEvent) {
                    var clear = new MethodInsnNode(
                        Opcodes.INVOKESTATIC, HOOK_OWNER, CLEAR_NAME, CLEAR_DESC, false);
                    method.instructions.insert(target, clear);
                }
                patched++;
            }
            if (patched != 1) {
                throw new Error('tbs_damage_multiplied: expected exactly one injection in '
                    + className + '.' + methodName + ', patched ' + patched);
            }
            return node;
        }
    };
}
