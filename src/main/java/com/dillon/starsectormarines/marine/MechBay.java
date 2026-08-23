package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.mech.MechDeploymentSpec;
import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MissileReplenisherComponent;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Persisted mech-squad and finite subsystem inventory authority. Installed
 * components count against owned stock and a refit returns the target's current
 * component before evaluating the replacement.
 */
public final class MechBay implements Serializable {

    public static final String STARTER_SQUAD_ID = "support_squad_01";
    public static final String STARTER_MECH_ID = "support_mech_01";

    private List<CampaignMechSquad> squads = new ArrayList<>();
    private Map<String, Integer> ownedReplenishers = new HashMap<>();
    private String activeSquadId;

    public MechBay() {
        seedStarterSquad();
    }

    public List<CampaignMechSquad> squads() {
        return Collections.unmodifiableList(squads);
    }

    public CampaignMechSquad squadById(String id) {
        if (id == null) return null;
        for (CampaignMechSquad squad : squads) {
            if (id.equals(squad.id())) return squad;
        }
        return null;
    }

    public CampaignMechSquad activeSquad() {
        CampaignMechSquad active = squadById(activeSquadId);
        return active != null ? active : squads.isEmpty() ? null : squads.get(0);
    }

    public boolean selectActiveSquad(String squadId) {
        if (squadById(squadId) == null) return false;
        activeSquadId = squadId;
        return true;
    }

    public CampaignMech mechById(String mechId) {
        if (mechId == null) return null;
        for (CampaignMechSquad squad : squads) {
            CampaignMech mech = squad.mechById(mechId);
            if (mech != null) return mech;
        }
        return null;
    }

    /** Acquisition seam: a chassis arrives with its currently installed subsystem. */
    public boolean addMech(String squadId, CampaignMech mech) {
        CampaignMechSquad squad = squadById(squadId);
        if (squad == null || mech == null || mechById(mech.id()) != null
                || !squad.add(mech)) return false;
        addReplenisher(mech.missileReplenisherId(), 1);
        return true;
    }

    public int ownedReplenisher(String componentId) {
        return Math.max(0, ownedReplenishers.getOrDefault(componentId, 0));
    }

    public int installedReplenisher(String componentId) {
        int installed = 0;
        for (CampaignMechSquad squad : squads) {
            for (CampaignMech mech : squad.mechs()) {
                if (mech.missileReplenisherId().equals(componentId)) installed++;
            }
        }
        return installed;
    }

    public int availableReplenisher(String componentId) {
        return Math.max(0, ownedReplenisher(componentId)
                - installedReplenisher(componentId));
    }

    /** Acquisition seam for salvage, fabrication, rewards, and tests. */
    public void addReplenisher(String componentId, int quantity) {
        if (MissileReplenisherComponent.findById(componentId) == null || quantity <= 0) return;
        ownedReplenishers.merge(componentId, quantity, Integer::sum);
    }

    /** Atomic one-slot refit; failure leaves the installed component untouched. */
    public boolean installReplenisher(String mechId, String componentId) {
        CampaignMech mech = mechById(mechId);
        MissileReplenisherComponent candidate =
                MissileReplenisherComponent.findById(componentId);
        if (mech == null || candidate == null) return false;
        if (candidate.id().equals(mech.missileReplenisherId())) return true;
        if (availableReplenisher(candidate.id()) <= 0) return false;
        mech.installMissileReplenisher(candidate.id());
        return true;
    }

    /** Frozen values for the active squad; campaign objects stay in the roster. */
    public List<MechDeploymentSpec> activeDeployment() {
        CampaignMechSquad squad = activeSquad();
        if (squad == null) return List.of();
        List<MechDeploymentSpec> deployment = new ArrayList<>();
        for (CampaignMech mech : squad.mechs()) {
            deployment.add(mech.freezeForDeployment());
        }
        return Collections.unmodifiableList(deployment);
    }

    private void seedStarterSquad() {
        if (!squads.isEmpty()) return;
        CampaignMechSquad squad = new CampaignMechSquad(
                STARTER_SQUAD_ID, "Support Squad 01");
        squad.add(new CampaignMech(STARTER_MECH_ID, "Bulwark 01",
                MechVariant.BULWARK, MechRole.ARMORED_SUPPORT,
                MissileReplenisherComponent.STANDARD.id()));
        squads.add(squad);
        activeSquadId = squad.id();
        putAtLeast(MissileReplenisherComponent.STANDARD.id(), 1);
        putAtLeast(MissileReplenisherComponent.ACCELERATED_FEED.id(), 1);
    }

    private void putAtLeast(String componentId, int quantity) {
        ownedReplenishers.merge(componentId, quantity, Math::max);
    }

    private Object readResolve() {
        if (squads == null) squads = new ArrayList<>();
        if (ownedReplenishers == null) ownedReplenishers = new HashMap<>();
        squads.removeIf(squad -> squad == null);
        seedStarterSquad();
        CampaignMechSquad active = activeSquad();
        activeSquadId = active != null ? active.id() : null;
        for (MissileReplenisherComponent component : MissileReplenisherComponent.catalog()) {
            putAtLeast(component.id(), installedReplenisher(component.id()));
        }
        return this;
    }
}
