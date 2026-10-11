package com.dillon.starsectormarines.battle.mech;

/** The cooperative's recoverable security walker; one identity and one initial fit. */
public final class WaylineHound {
    public static final String ID = "wayline_hound";
    public static final String NAME = "Wayward";

    private WaylineHound() {}

    public static MechDeploymentSpec deployment() {
        return new MechDeploymentSpec(MechVariant.HOUND, MechRole.ASSAULT,
                MissileReplenisherComponent.STANDARD, MechWeaponComponent.NOSE_CHAINGUN,
                null, null, NAME);
    }
}
