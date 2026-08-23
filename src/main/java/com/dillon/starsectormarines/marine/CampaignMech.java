package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.mech.MechDeploymentSpec;
import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MissileReplenisherComponent;

import java.io.Serializable;

/** Persistent identity and installed kit for one player-owned support mech. */
public final class CampaignMech implements Serializable {

    private final String id;
    private String displayName;
    private final MechVariant variant;
    private MechRole role;
    private String missileReplenisherId;

    public CampaignMech(String id, String displayName, MechVariant variant,
                        MechRole role, String missileReplenisherId) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Mech id is required");
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Mech name is required");
        }
        if (variant == null) throw new IllegalArgumentException("Mech variant is required");
        this.id = id;
        this.displayName = displayName;
        this.variant = variant;
        this.role = role != null ? role : variant.defaultRole;
        this.missileReplenisherId = MissileReplenisherComponent.resolve(
                missileReplenisherId).id();
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public MechVariant variant() { return variant; }
    public MechRole role() { return role != null ? role : variant.defaultRole; }
    public String missileReplenisherId() {
        return MissileReplenisherComponent.resolve(missileReplenisherId).id();
    }

    public MissileReplenisherComponent missileReplenisher() {
        return MissileReplenisherComponent.resolve(missileReplenisherId);
    }

    public MechDeploymentSpec freezeForDeployment() {
        return new MechDeploymentSpec(variant, role(), missileReplenisher());
    }

    void installMissileReplenisher(String componentId) {
        missileReplenisherId = componentId;
    }
}
