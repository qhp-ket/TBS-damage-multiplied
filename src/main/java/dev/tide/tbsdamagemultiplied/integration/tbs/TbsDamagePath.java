package dev.tide.tbsdamagemultiplied.integration.tbs;

import dev.tide.tbsdamagemultiplied.ScalingMode;

/** Stable IDs for concrete TBS damage implementation paths, never whole weapons. */
public enum TbsDamagePath {
    MACHETE_PRIMARY("machete_primary", ScalingMode.FIXED),
    MACHETE_FOLLOWUP("machete_followup", ScalingMode.FIXED),
    BULLET_DIRECT_HIT("bullet_direct_hit", ScalingMode.FIXED),
    ICE_CRYSTAL_HIT("ice_crystal_hit", ScalingMode.FIXED),
    ICE_BROADSWORD("ice_broadsword", ScalingMode.FIXED),
    HALBERD_CHI("halberd_chi", ScalingMode.FIXED),
    HALBERD_WIND("halberd_wind", ScalingMode.FIXED),
    HALBERD_LIGHT_WIND("halberd_light_wind", ScalingMode.FIXED),
    HALBERD_LIGHT_WIND_REAL_DAMAGE("halberd_light_wind_real_damage", ScalingMode.FIXED),
    GRAVESTONE_SLASH("gravestone_slash", ScalingMode.FIXED),
    LIGHTING_BOOM("lighting_boom", ScalingMode.FIXED),
    HALBERD_FLIGHT_HIT("halberd_flight_hit", ScalingMode.FIXED),
    ROSMONTIS_EMBRACE_ASSIST("rosmontis_embrace_assist", ScalingMode.FIXED),

    BULLET_AOE("bullet_aoe", ScalingMode.NATIVE_ATTACK_SCALED),
    DRONE_PROJECTILE("drone_projectile", ScalingMode.NATIVE_ATTACK_SCALED),
    STOMP("stomp", ScalingMode.NATIVE_ATTACK_SCALED),
    GRAVESTONE_PROJECTILE("gravestone_projectile", ScalingMode.NATIVE_ATTACK_SCALED),
    ROS_MULTI_BLOCK("ros_multi_block", ScalingMode.NATIVE_ATTACK_SCALED),
    ROSMONTIS_INSTALLATION("rosmontis_installation", ScalingMode.NATIVE_ATTACK_SCALED),
    ROSMONTIS_LIVING_INSTALLATION("rosmontis_living_installation", ScalingMode.NATIVE_ATTACK_SCALED),
    HALBERD_GROUND("halberd_ground", ScalingMode.NATIVE_ATTACK_SCALED),

    UNKNOWN("unknown", ScalingMode.IGNORE);

    private final String key;
    private final String id;
    private final ScalingMode mode;

    TbsDamagePath(String key, ScalingMode mode) {
        this.key = key;
        this.id = "tbs:" + key;
        this.mode = mode;
    }

    public String key() { return key; }
    public String id() { return id; }
    public ScalingMode mode() { return mode; }
    public boolean configurable() { return mode == ScalingMode.FIXED; }

    public static TbsDamagePath byId(String id) {
        for (TbsDamagePath path : values()) {
            if (path.id.equals(id)) {
                return path;
            }
        }
        return UNKNOWN;
    }
}
