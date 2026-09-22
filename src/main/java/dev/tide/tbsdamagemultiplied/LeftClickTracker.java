package dev.tide.tbsdamagemultiplied;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Records genuine left-click melee attacks ({@code AttackEntityEvent} -> {@code Player.attack})
 * so the handler can hard-exclude them. Real left-clicks already use the player's full
 * attributes, so scaling them would double-count. Keyed by player UUID with the exact
 * target and server tick, this pins the one melee hit without affecting projectile hits.
 */
public final class LeftClickTracker {
    private record Record(int targetId, long tick) {
    }

    private static final Map<UUID, Record> RECENT = new ConcurrentHashMap<>();

    private LeftClickTracker() {
    }

    public static void record(UUID playerId, int targetId, long tick) {
        RECENT.put(playerId, new Record(targetId, tick));
    }

    /** True when this hurt matches the player's genuine left-click this tick against this target. */
    public static boolean isGenuineLeftClick(UUID playerId, int targetId, long tick) {
        Record record = RECENT.get(playerId);
        if (record == null || record.tick() != tick || record.targetId() != targetId) {
            return false;
        }
        // One AttackEntityEvent represents one vanilla melee hit. Consume it so a
        // same-tick, same-target follow-up from another implementation is classified normally.
        return RECENT.remove(playerId, record);
    }

    public static void forget(UUID playerId) {
        RECENT.remove(playerId);
    }

    public static void clear() {
        RECENT.clear();
    }
}
