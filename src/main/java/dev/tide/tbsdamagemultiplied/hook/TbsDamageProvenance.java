package dev.tide.tbsdamagemultiplied.hook;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import dev.tide.tbsdamagemultiplied.integration.tbs.TbsDamagePath;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;

/**
 * Server-thread provenance frames for coremod-instrumented TBS damage calls.
 *
 * <p>A frame is pushed immediately before the audited call which emits damage and is
 * observed, but not removed, by matching {@code LivingHurtEvent}s. The audited call's
 * post-call finish is the only operation which pops its frame. The stack, rather than
 * a single thread-local value, keeps nested damage calls isolated. The target is known for
 * direct ability hits; range/projectile hooks use {@link #ANY_TARGET} when TBS does
 * not expose the eventual victim at their narrow scaling call site.</p>
 */
public final class TbsDamageProvenance {
    static final int ANY_TARGET = -1;

    public static final class Frame {
        private final TbsDamagePath path;
        private final UUID ownerId;
        private final int targetId;
        private boolean alreadyScaled;
        private final ResourceLocation expectedDamageType;
        private final boolean requiresPlayerAttackSource;
        private final Set<Integer> observedTargets = new HashSet<>();

        private Frame(
            TbsDamagePath path,
            UUID ownerId,
            int targetId,
            boolean alreadyScaled,
            ResourceLocation expectedDamageType,
            boolean requiresPlayerAttackSource
        ) {
            this.path = path;
            this.ownerId = ownerId;
            this.targetId = targetId;
            this.alreadyScaled = alreadyScaled;
            this.expectedDamageType = expectedDamageType;
            this.requiresPlayerAttackSource = requiresPlayerAttackSource;
        }

        public TbsDamagePath path() { return path; }
        public boolean alreadyScaled() { return alreadyScaled; }
        void setAlreadyScaled(boolean value) { alreadyScaled = value; }

        boolean matches(ServerPlayer player, Entity victim, DamageSource source) {
            return ownerId != null
                && ownerId.equals(player.getUUID())
                && (targetId == ANY_TARGET || targetId == victim.getId())
                && (expectedDamageType == null
                    || expectedDamageType.equals(source.typeHolder().unwrapKey()
                        .map(key -> key.location()).orElse(null)))
                && (!requiresPlayerAttackSource
                    || (source.getDirectEntity() == player && source.getEntity() == player));
        }

        boolean tryObserve(ServerPlayer player, Entity victim, DamageSource source) {
            if (!matches(player, victim, source)) {
                return false;
            }

            int observedTarget = targetId == ANY_TARGET ? victim.getId() : targetId;
            return observedTargets.add(observedTarget);
        }
    }

    private static final ThreadLocal<Deque<Frame>> FRAMES =
        ThreadLocal.withInitial(ArrayDeque::new);

    private TbsDamageProvenance() {
    }

    static Frame push(
        TbsDamagePath path,
        Entity owner,
        Entity target,
        boolean alreadyScaled,
        ResourceLocation expectedDamageType,
        boolean requiresPlayerAttackSource
    ) {
        UUID ownerId = owner instanceof ServerPlayer player ? player.getUUID() : null;
        int targetId = target == null ? ANY_TARGET : target.getId();
        Frame frame = new Frame(
            path, ownerId, targetId, alreadyScaled,
            expectedDamageType, requiresPlayerAttackSource);
        FRAMES.get().push(frame);
        return frame;
    }

    static Frame observeMatching(ServerPlayer player, Entity victim, DamageSource source) {
        Deque<Frame> frames = FRAMES.get();
        Frame frame = frames.peek();
        if (frame == null) {
            FRAMES.remove();
            return null;
        }
        // ANY_TARGET is best-effort provenance for audited multi-target calls.
        // Victims are de-duplicated within the call, but an unrelated nested damage
        // event with identical owner/source semantics and a new victim cannot be
        // distinguished here.
        return frame.tryObserve(player, victim, source) ? frame : null;
    }

    /** Ends one audited call and pops exactly its still-top provenance frame. */
    static void finishTop() {
        Deque<Frame> frames = FRAMES.get();
        if (!frames.isEmpty()) {
            frames.pop();
        }
        removeThreadLocalWhenEmpty(frames);
    }

    static void clear() {
        FRAMES.remove();
    }

    private static void removeThreadLocalWhenEmpty(Deque<Frame> frames) {
        if (frames.isEmpty()) {
            FRAMES.remove();
        }
    }
}
