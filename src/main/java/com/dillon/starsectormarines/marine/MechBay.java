package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.mech.MechDeploymentSpec;
import com.dillon.starsectormarines.battle.mech.WaylineHound;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketDef;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketId;
import com.dillon.starsectormarines.battle.mech.MechMountSlot;
import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.mech.MissileReplenisherComponent;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

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
    private Map<String, Integer> ownedWeapons = new HashMap<>();
    private String activeSquadId;
    private int nextFabricatedSerial = 1;
    /**
     * Save-model marker. Old serialized bays do not contain this field and
     * therefore deserialize false; those saves retain their original starter
     * Bulwark. New campaigns begin with only the empty support-lance gantries.
     */
    private boolean foundingModelInitialized = true;
    // Absent in older saves: preserve their unrestricted workshop.
    private boolean starterPatternsOnly = true;
    private int labLesson;
    private boolean labLessonPaused;
    private boolean lessonRackRemoved;

    public boolean canFabricate(MechWeaponComponent component) {
        return component != null && (!starterPatternsOnly
                || component == MechWeaponComponent.NOSE_CHAINGUN
                || component == MechWeaponComponent.SRM_5);
    }

    public boolean canFabricate(MechVariant variant) {
        return variant != null && (!starterPatternsOnly || variant == MechVariant.HOUND);
    }

    public int labLesson() { return labLesson; }
    public boolean labLessonPaused() { return labLessonPaused; }
    public void pauseLabLesson(boolean paused) { labLessonPaused = paused; }

    /** Once-only recovery package; also migrates an already-owned story Hound. */
    public void beginLabLesson() {
        if (labLesson != 0) return;
        addWeapon(MechWeaponComponent.SRM_5.id, 1);
        labLesson = 1;
    }

    public void advanceLabLesson() {
        if (labLesson == 1) { labLesson = 2; observeLabFit(); }
        else if (labLesson == 4) labLesson = 5;
    }

    private void observeLabFit() {
        CampaignMech mech = mechById(WaylineHound.ID);
        if (mech == null) return;
        for (MechMountSlot mount : List.of(MechMountSlot.LEFT_SHOULDER, MechMountSlot.RIGHT_SHOULDER)) {
            if (mech.weaponAt(mount) != MechWeaponComponent.SRM_5) continue;
            if (labLesson == 2) { labLesson = 3; return; }
            if (labLesson == 3 && lessonRackRemoved) labLesson = 4;
        }
    }

    public MechBay() {
        ensureSupportSquad();
    }

    /** Explicit legacy/test fixture; ordinary campaign construction stays empty. */
    public static MechBay legacyStarterFixture() {
        return legacyStarterBay();
    }

    static MechBay legacyStarterBay() {
        MechBay bay = new MechBay();
        bay.starterPatternsOnly = false;
        bay.seedLegacyStarterAssets();
        return bay;
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
        addInstalledWeapons(mech);
        return true;
    }

    public boolean canAddMech(String squadId) {
        CampaignMechSquad squad = squadById(squadId);
        return squad != null && squad.mechs().size() < CampaignMechSquad.CAPACITY;
    }

    /** Retains a recovered chassis even when the existing gantries are full. */
    public boolean receiveRecoveredMech(CampaignMech mech) {
        if (mech == null || mechById(mech.id()) != null) return false;
        for (CampaignMechSquad squad : squads) {
            if (canAddMech(squad.id())) return addMech(squad.id(), mech);
        }
        CampaignMechSquad recovery = new CampaignMechSquad("recovery_" + mech.id(), "Recovered walkers");
        squads.add(recovery);
        return addMech(recovery.id(), mech);
    }

    /** Builds one standard-fit chassis into a vacant gantry in the selected lance. */
    public CampaignMech fabricateChassis(String squadId, MechVariant variant) {
        if (variant == null || !canAddMech(squadId)) return null;
        int serial = nextAvailableSerial();
        CampaignMech mech = new CampaignMech("support_mech_"
                + String.format(Locale.ROOT, "%02d", serial),
                variant.displayName() + " " + String.format(Locale.ROOT, "%02d", serial), variant,
                variant.defaultRole, MissileReplenisherComponent.STANDARD.id());
        if (!addMech(squadId, mech)) return null;
        nextFabricatedSerial = serial + 1;
        return mech;
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

    public int ownedWeapon(String componentId) {
        return Math.max(0, ownedWeapons.getOrDefault(componentId, 0));
    }

    public int installedWeapon(String componentId) {
        int installed = 0;
        for (CampaignMechSquad squad : squads) {
            for (CampaignMech mech : squad.mechs()) {
                for (MechMountSlot slot : MechMountSlot.values()) {
                    MechWeaponComponent component = mech.weaponAt(slot);
                    if (component != null && component.id.equals(componentId)) installed++;
                }
            }
        }
        return installed;
    }

    public int availableWeapon(String componentId) {
        return Math.max(0, ownedWeapon(componentId) - installedWeapon(componentId));
    }

    /** Acquisition seam for recovered or fabricated weapon assemblies. */
    public void addWeapon(String componentId, int quantity) {
        if (MechWeaponComponent.findById(componentId) == null || quantity <= 0) return;
        ownedWeapons.merge(componentId, quantity, Integer::sum);
    }

    /** Acquisition seam for salvage, fabrication, rewards, and tests. */
    public void addReplenisher(String componentId, int quantity) {
        if (MissileReplenisherComponent.findById(componentId) == null || quantity <= 0) return;
        ownedReplenishers.merge(componentId, quantity, Integer::sum);
    }

    /**
     * Spares go down with the ship; the machines and what is bolted into them
     * do not.
     *
     * <p>A walker is the company's own, and what it is carrying is carrying it.
     * What sinks is the shelf of replacements beside it, which is the input the
     * player has to restock before the bay can refit anything again.
     *
     * @return how many spare components were destroyed
     */
    public int loseSpares() {
        int destroyed = 0;
        for (String componentId : new ArrayList<>(ownedReplenishers.keySet())) {
            int installed = installedReplenisher(componentId);
            destroyed += Math.max(0, ownedReplenisher(componentId) - installed);
            if (installed <= 0) ownedReplenishers.remove(componentId);
            else ownedReplenishers.put(componentId, installed);
        }
        for (String componentId : new ArrayList<>(ownedWeapons.keySet())) {
            int installed = installedWeapon(componentId);
            destroyed += Math.max(0, ownedWeapon(componentId) - installed);
            if (installed <= 0) ownedWeapons.remove(componentId);
            else ownedWeapons.put(componentId, installed);
        }
        return destroyed;
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

    public boolean canInstallWeapon(String mechId, MechMountSlot slot, String componentId) {
        CampaignMech mech = mechById(mechId);
        MechWeaponComponent component = MechWeaponComponent.findById(componentId);
        if (mech == null || slot == null || component == null || !component.accepts(slot)) {
            return false;
        }
        MechFittingLayout.SocketId socketId = socketFor(slot);
        MechFittingLayout.SocketDef socket = MechFittingLayout.forVariant(
                mech.variant()).socket(socketId);
        if (socket == null || socket.factoryLocked()
                || !socket.accommodates(component.footprintColumns,
                        component.footprintRows)) {
            return false;
        }
        return socket.type() == MechFittingLayout.SocketType.OMNI
                || socket.type().name().equals(component.hardpointType.name());
    }

    /** Atomic one-hardpoint refit; the outgoing assembly returns to finite stores. */
    public boolean installWeapon(String mechId, MechMountSlot slot, String componentId) {
        CampaignMech mech = mechById(mechId);
        MechWeaponComponent component = MechWeaponComponent.findById(componentId);
        if (!canInstallWeapon(mechId, slot, componentId)) return false;
        if (component == mech.weaponAt(slot)) return true;
        if (availableWeapon(componentId) <= 0) return false;
        mech.installWeapon(slot, component);
        observeLabFit();
        return true;
    }

    /**
     * Unequips an installed weapon assembly from the specified slot, returning
     * it directly to available fleet stores.
     */
    public boolean stripWeapon(String mechId, MechMountSlot slot) {
        CampaignMech mech = mechById(mechId);
        if (mech == null || slot == null) return false;
        SocketId socketId = socketFor(slot);
        SocketDef socket = MechFittingLayout.forVariant(
                mech.variant()).socket(socketId);
        if (socket == null || socket.factoryLocked()) return false;
        if (mech.weaponAt(slot) == null) return false;
        if (labLesson == 3 && WaylineHound.ID.equals(mechId)
                && mech.weaponAt(slot) == MechWeaponComponent.SRM_5) lessonRackRemoved = true;
        mech.installWeapon(slot, null);
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

    private void ensureSupportSquad() {
        if (squadById(STARTER_SQUAD_ID) != null) return;
        CampaignMechSquad squad = new CampaignMechSquad(
                STARTER_SQUAD_ID, "Support Squad 01");
        squads.add(squad);
        if (activeSquadId == null) activeSquadId = squad.id();
    }

    private void seedLegacyStarterAssets() {
        ensureSupportSquad();
        CampaignMechSquad squad = squadById(STARTER_SQUAD_ID);
        if (mechById(STARTER_MECH_ID) == null) {
            squad.add(new CampaignMech(STARTER_MECH_ID, "Bulwark 01",
                    MechVariant.BULWARK, MechRole.ARMORED_SUPPORT,
                    MissileReplenisherComponent.STANDARD.id()));
        }
        activeSquadId = squad.id();
        putAtLeast(MissileReplenisherComponent.STANDARD.id(), 1);
        putAtLeast(MissileReplenisherComponent.ACCELERATED_FEED.id(), 1);
        for (CampaignMech mech : squad.mechs()) addInstalledWeapons(mech);
    }

    private void putAtLeast(String componentId, int quantity) {
        ownedReplenishers.merge(componentId, quantity, Math::max);
    }

    private void addInstalledWeapons(CampaignMech mech) {
        for (MechMountSlot slot : MechMountSlot.values()) {
            MechWeaponComponent component = mech.weaponAt(slot);
            if (component != null) addWeapon(component.id, 1);
        }
    }

    private int nextAvailableSerial() {
        int candidate = Math.max(1, nextFabricatedSerial);
        while (mechById("support_mech_"
                + String.format(Locale.ROOT, "%02d", candidate)) != null) {
            candidate++;
        }
        return candidate;
    }

    private static MechFittingLayout.SocketId socketFor(MechMountSlot slot) {
        return switch (slot) {
            case ARMS -> MechFittingLayout.SocketId.ARMS;
            case LEFT_SHOULDER -> MechFittingLayout.SocketId.LEFT_SHOULDER;
            case RIGHT_SHOULDER -> MechFittingLayout.SocketId.RIGHT_SHOULDER;
        };
    }

    private Object readResolve() {
        if (squads == null) squads = new ArrayList<>();
        if (ownedReplenishers == null) ownedReplenishers = new HashMap<>();
        if (ownedWeapons == null) ownedWeapons = new HashMap<>();
        squads.removeIf(squad -> squad == null);
        if (!foundingModelInitialized) seedLegacyStarterAssets();
        else ensureSupportSquad();
        foundingModelInitialized = true;
        CampaignMechSquad active = activeSquad();
        activeSquadId = active != null ? active.id() : null;
        for (MissileReplenisherComponent component : MissileReplenisherComponent.catalog()) {
            putAtLeast(component.id(), installedReplenisher(component.id()));
        }
        for (MechWeaponComponent component : MechWeaponComponent.values()) {
            ownedWeapons.merge(component.id, installedWeapon(component.id), Math::max);
        }
        if (labLesson == 0 && mechById(WaylineHound.ID) != null) beginLabLesson();
        nextFabricatedSerial = Math.max(1, nextFabricatedSerial);
        return this;
    }
    public boolean moveWeapon(String mechId, MechMountSlot source, MechMountSlot target) {
        CampaignMech mech = mechById(mechId);
        if (mech == null || source == null || target == null || source == target) return false;
        MechWeaponComponent component = mech.weaponAt(source);
        if (component == null || mech.weaponAt(target) != null
                || !canInstallWeapon(mechId, source, component.id)
                || !canInstallWeapon(mechId, target, component.id)) return false;
        mech.installWeapon(target, component);
        mech.installWeapon(source, null);
        observeLabFit();
        return true;
    }

}
