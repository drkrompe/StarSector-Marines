package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.battle.mech.CompanyMechLedger;
import com.dillon.starsectormarines.battle.mech.MechDeploymentSpec;
import com.dillon.starsectormarines.battle.mech.MechSections;
import com.dillon.starsectormarines.battle.mech.WaylineHound;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketDef;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketId;
import com.dillon.starsectormarines.battle.mech.MechMountSlot;
import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.mech.MissileReplenisherComponent;
import com.dillon.starsectormarines.battle.vehicle.VehicleDeploymentSpec;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.campaign.CampaignClock;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;

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
    /** Owned internal assemblies by {@link MechInternal#id}, installed copies included. */
    private Map<String, Integer> ownedInternals = new HashMap<>();
    private String activeSquadId;
    private int nextFabricatedSerial = 1;
    private int nextVehicleSerial = 1;
    /**
     * Save-model marker. Old serialized bays do not contain this field and
     * therefore deserialize false; those saves retain their original starter
     * Bulwark. New campaigns begin with only the empty support-lance gantries.
     */
    private boolean foundingModelInitialized = true;
    // Absent in older saves: preserve their unrestricted workshop. Now migration input only;
    // knownPatternIds is the gate.
    private boolean starterPatternsOnly = true;
    /** Learned {@link MechPattern} ids. Permanent: nothing removes one. */
    private Set<String> knownPatternIds = starterPatternIds();
    private int labLesson;
    private boolean labLessonPaused;
    private boolean lessonRackRemoved;

    public boolean canFabricate(MechWeaponComponent component) {
        return component != null && knowsPattern(MechPattern.of(component).id());
    }

    public boolean canFabricate(MechVariant variant) {
        return variant != null && knowsPattern(MechPattern.of(variant).id());
    }

    public boolean canFabricate(VehicleType type) {
        return type != null && knowsPattern(MechPattern.of(type).id());
    }

    public boolean canFabricate(MechInternal internal) {
        return internal != null && knowsPattern(MechPattern.of(internal).id());
    }

    public boolean knowsPattern(String patternId) {
        return patternId != null && knownPatternIds.contains(patternId);
    }

    /** True when the pattern was registered and not yet known. Never removes a pattern. */
    public boolean learnPattern(String patternId) {
        return MechPattern.parse(patternId) != null && knownPatternIds.add(patternId);
    }

    public Set<String> knownPatternIds() {
        return Collections.unmodifiableSet(knownPatternIds);
    }

    private static Set<String> starterPatternIds() {
        Set<String> ids = new LinkedHashSet<>();
        for (MechPattern pattern : MechPattern.STARTER) ids.add(pattern.id());
        return ids;
    }

    /**
     * What a pre-pattern save's unrestricted workshop could build: every chassis and weapon.
     * Internals did not exist then, so it gets only the stock ones.
     */
    private static Set<String> allPatternIds() {
        Set<String> ids = new LinkedHashSet<>();
        for (MechPattern pattern : MechPattern.all()) {
            if (pattern.kind() != MechPattern.Kind.INTERNAL || pattern.starter()) ids.add(pattern.id());
        }
        return ids;
    }

    public int labLesson() { return labLesson; }
    public boolean labLessonPaused() { return labLessonPaused; }
    public void pauseLabLesson(boolean paused) { labLessonPaused = paused; }

    /**
     * Once-only recovery package; also migrates an already-owned story Hound. The rack is the
     * fragmentation one, since the first operations are fought against men.
     */
    public void beginLabLesson() {
        if (labLesson != 0) return;
        addWeapon(MechWeaponComponent.SRM_5_FRAG.id, 1);
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
            if (!isLessonRack(mech.weaponAt(mount))) continue;
            if (labLesson == 2) { labLesson = 3; return; }
            if (labLesson == 3 && lessonRackRemoved) labLesson = 4;
        }
    }

    public MechBay() {
        ensureSupportSquad();
    }

    /** Either SRM-5 warhead counts for the lesson; a save from before the split holds the anti-armor one. */
    private static boolean isLessonRack(MechWeaponComponent component) {
        return component == MechWeaponComponent.SRM_5_FRAG || component == MechWeaponComponent.SRM_5;
    }

    /** Explicit legacy/test fixture; ordinary campaign construction stays empty. */
    public static MechBay legacyStarterFixture() {
        return legacyStarterBay();
    }

    static MechBay legacyStarterBay() {
        MechBay bay = new MechBay();
        bay.starterPatternsOnly = false;
        bay.knownPatternIds = allPatternIds();
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

    public CampaignVehicle vehicleById(String vehicleId) {
        if (vehicleId == null) return null;
        for (CampaignMechSquad squad : squads) {
            CampaignVehicle vehicle = squad.vehicleById(vehicleId);
            if (vehicle != null) return vehicle;
        }
        return null;
    }

    /** Acquisition seam for a vehicle; it takes a vacant gantry and nothing else. */
    public boolean addVehicle(String squadId, CampaignVehicle vehicle) {
        CampaignMechSquad squad = squadById(squadId);
        return squad != null && vehicle != null && vehicleById(vehicle.id()) == null
                && mechById(vehicle.id()) == null && squad.add(vehicle);
    }

    /** Builds one vehicle onto a vacant gantry in the selected lance. */
    public CampaignVehicle fabricateVehicle(String squadId, VehicleType type) {
        if (type == null || !canAddMech(squadId)) return null;
        int serial = Math.max(1, nextVehicleSerial);
        String number = String.format(Locale.ROOT, "%02d", serial);
        CampaignVehicle vehicle = new CampaignVehicle(vehicleId(serial),
                CampaignVehicle.typeName(type) + " " + String.format(Strings.locale(), "%02d", serial), type);
        if (!addVehicle(squadId, vehicle)) return null;
        nextVehicleSerial = serial + 1;
        return vehicle;
    }

    /**
     * Battle loss writeback: each destroyed vehicle leaves its gantry for good. Unknown
     * ids (a debug vehicle, one already written off) are ignored.
     *
     * @return how many vehicles left the bay
     */
    public int loseVehicles(Collection<String> vehicleIds) {
        if (vehicleIds == null) return 0;
        int lost = 0;
        for (String vehicleId : vehicleIds) {
            for (CampaignMechSquad squad : squads) {
                CampaignVehicle vehicle = squad.vehicleById(vehicleId);
                if (vehicle != null && squad.remove(vehicle)) {
                    lost++;
                    break;
                }
            }
        }
        return lost;
    }

    /**
     * Battle damage writeback for the company's own mechs ({@code mechs-nouns.md},
     * After the battle). A destroyed torso loses the chassis: it leaves its gantry and every
     * part fitted to it leaves stores with it. Otherwise a destroyed mount loses its weapon
     * assembly from stores and the mount comes back empty, the pattern kept; every damaged
     * section is recorded for repair, and the mech stays out of drops until it is repaired.
     *
     * <p>Idempotent: a replayed result finds the chassis gone, the mount already empty and the
     * same damage recorded, so nothing is struck twice. Unknown ids are ignored.
     */
    public Settlement settleBattleDamage(Collection<CompanyMechLedger.Damage> damage) {
        if (damage == null || damage.isEmpty()) return Settlement.NONE;
        int chassisLost = 0;
        int assembliesLost = 0;
        int damaged = 0;
        for (CompanyMechLedger.Damage entry : damage) {
            if (entry == null) continue;
            CampaignMech mech = mechById(entry.campaignId());
            if (mech == null) continue;
            if (entry.chassisLost()) {
                if (loseChassis(mech)) chassisLost++;
                continue;
            }
            MechSections.Report report = entry.report();
            if (report == null) continue;
            for (MechMountSlot slot : MechMountSlot.values()) {
                MechWeaponComponent lost = report.lostMounts()[slot.ordinal()];
                if (lost == null || mech.weaponAt(slot) != lost) continue;
                mech.installWeapon(slot, null);
                strike(ownedWeapons, lost.id);
                assembliesLost++;
            }
            MechDamage taken = MechDamage.of(report);
            if (taken != null) {
                mech.recordDamage(taken);
                damaged++;
            }
        }
        return new Settlement(chassisLost, assembliesLost, damaged);
    }

    /**
     * Breaks a chassis up on its gantry so the berth can take another machine. Unlike a loss in
     * battle, nothing fitted is destroyed: its weapons, internals and replenisher stay owned and,
     * with no frame to sit in, are spares in stores again.
     */
    public boolean scrapMech(String mechId) {
        CampaignMech mech = mechById(mechId);
        if (mech == null) return false;
        for (CampaignMechSquad squad : squads) {
            if (squad.mechById(mechId) == mech) return squad.remove(mech);
        }
        return false;
    }

    /** Breaks a vehicle up; it carries nothing the bay counts, so only the berth comes back. */
    public boolean scrapVehicle(String vehicleId) {
        return vehicleId != null && loseVehicles(List.of(vehicleId)) == 1;
    }

    /** The chassis and everything fitted to it leave the bay; its gantry stands empty. */
    private boolean loseChassis(CampaignMech mech) {
        for (CampaignMechSquad squad : squads) {
            if (squad.mechById(mech.id()) != mech || !squad.remove(mech)) continue;
            for (MechMountSlot slot : MechMountSlot.values()) {
                MechWeaponComponent component = mech.weaponAt(slot);
                if (component != null) strike(ownedWeapons, component.id);
            }
            for (MechInternal.Role role : MechInternal.Role.values()) {
                MechInternal internal = mech.internal(role);
                if (internal != null) strike(ownedInternals, internal.id);
            }
            strike(ownedReplenishers, mech.missileReplenisherId());
            return true;
        }
        return false;
    }

    private static void strike(Map<String, Integer> owned, String id) {
        Integer count = owned.get(id);
        if (count == null) return;
        if (count <= 1) owned.remove(id);
        else owned.put(id, count - 1);
    }

    /** What one battle's writeback did to the bay. */
    public record Settlement(int chassisLost, int assembliesLost, int mechsDamaged) {
        public static final Settlement NONE = new Settlement(0, 0, 0);
    }

    private static String vehicleId(int serial) {
        return "support_vehicle_" + String.format(Locale.ROOT, "%02d", serial);
    }

    /** Acquisition seam: a chassis arrives with its currently installed subsystem. */
    public boolean addMech(String squadId, CampaignMech mech) {
        CampaignMechSquad squad = squadById(squadId);
        if (squad == null || mech == null || mechById(mech.id()) != null
                || !squad.add(mech)) return false;
        addReplenisher(mech.missileReplenisherId(), 1);
        addInstalledWeapons(mech);
        addInstalledInternals(mech);
        return true;
    }

    public boolean canAddMech(String squadId) {
        CampaignMechSquad squad = squadById(squadId);
        return squad != null && squad.occupied() < CampaignMechSquad.CAPACITY;
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
    /**
     * A bought chassis: {@link #fabricateChassis} stripped to its frame. Every weapon that is not
     * factory-locked, the engine and the capacitor bank come off; none of them was ever in stores,
     * so stripping them makes no spares. It stays in its gantry until an engine and a capacitor
     * bank are fitted ({@link CampaignMech#missingInternals}).
     */
    public CampaignMech fabricateFrame(String squadId, MechVariant variant) {
        CampaignMech mech = fabricateChassis(squadId, variant);
        if (mech != null) mech.stripToFrame();
        return mech;
    }

    public CampaignMech fabricateChassis(String squadId, MechVariant variant) {
        if (variant == null || !canAddMech(squadId)) return null;
        int serial = nextAvailableSerial();
        CampaignMech mech = new CampaignMech("support_mech_"
                + String.format(Locale.ROOT, "%02d", serial),
                variant.displayName() + " " + String.format(Strings.locale(), "%02d", serial), variant,
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

    public int ownedInternal(String internalId) {
        return Math.max(0, ownedInternals.getOrDefault(internalId, 0));
    }

    public int installedInternal(String internalId) {
        MechInternal internal = MechInternal.findById(internalId);
        if (internal == null) return 0;
        int installed = 0;
        for (CampaignMechSquad squad : squads) {
            for (CampaignMech mech : squad.mechs()) {
                if (mech.internal(internal.role) == internal) installed++;
            }
        }
        return installed;
    }

    public int availableInternal(String internalId) {
        return Math.max(0, ownedInternal(internalId) - installedInternal(internalId));
    }

    /** Acquisition seam for fabricated or recovered internal assemblies. */
    public void addInternal(String internalId, int quantity) {
        if (MechInternal.findById(internalId) == null || quantity <= 0) return;
        ownedInternals.merge(internalId, quantity, Integer::sum);
    }

    /**
     * Whether {@code internal} physically fits this mech: its role's socket exists on the
     * chassis and holds the part's whole footprint (plating covers the frame and needs no
     * socket), and a vent array never shares the utility socket with a non-standard loader.
     * Mass is a separate question ({@link #withinBudget}).
     */
    public boolean canInstallInternal(String mechId, MechInternal internal) {
        CampaignMech mech = mechById(mechId);
        if (mech == null || internal == null) return false;
        SocketId socketId = internal.role.socket();
        if (socketId != null) {
            SocketDef socket = MechFittingLayout.forVariant(mech.variant()).socket(socketId);
            if (socket == null || !socket.accommodates(internal.footprintColumns, internal.footprintRows)) {
                return false;
            }
        }
        return internal.role != MechInternal.Role.VENT_ARRAY
                || !MechFrameBudget.loaderOccupiesUtility(mech.missileReplenisher());
    }

    /**
     * The engine budget for a proposed fit. A fit over budget is refused unless it is no
     * further over than the current one, so a mech already over budget (a fit from before
     * the budget existed) can still be stripped down, but never loaded further.
     */
    public boolean withinBudget(String mechId, MechFrameBudget.Fit proposed) {
        CampaignMech mech = mechById(mechId);
        if (mech == null || proposed == null) return false;
        if (!proposed.overBudget()) return true;
        MechFrameBudget.Fit current = mech.fit();
        return proposed.fittedMass() - proposed.output()
                <= current.fittedMass() - current.output() + 1e-4f;
    }

    /** Atomic one-part refit; the outgoing assembly returns to finite stores. */
    public boolean installInternal(String mechId, MechInternal internal) {
        CampaignMech mech = mechById(mechId);
        if (!canInstallInternal(mechId, internal)) return false;
        if (mech.internal(internal.role) == internal) return true;
        if (availableInternal(internal.id) <= 0
                || !withinBudget(mechId, mech.fit().withInternal(internal.role, internal))) return false;
        mech.installInternal(internal.role, internal);
        return true;
    }

    /** Empties the optional utility socket; the vent array returns to stores. */
    public boolean stripVentArray(String mechId) {
        return stripInternal(mechId, MechInternal.Role.VENT_ARRAY);
    }

    /**
     * Empties one socketed internal back to stores. An engine or capacitor bank may be left
     * empty too; the mech is then {@link CampaignMech.Readiness#INCOMPLETE} and stays off the
     * drop manifest until one is fitted. Plating covers the frame and is only ever replaced.
     */
    public boolean stripInternal(String mechId, MechInternal.Role role) {
        CampaignMech mech = mechById(mechId);
        if (mech == null || role == null || role.socket() == null || mech.internal(role) == null) return false;
        mech.installInternal(role, null);
        return true;
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
        for (String internalId : new ArrayList<>(ownedInternals.keySet())) {
            int installed = installedInternal(internalId);
            destroyed += Math.max(0, ownedInternal(internalId) - installed);
            if (installed <= 0) ownedInternals.remove(internalId);
            else ownedInternals.put(internalId, installed);
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
        // A faster loader takes the utility socket a vent array would otherwise hold.
        if (MechFrameBudget.loaderOccupiesUtility(candidate)
                && (!MechFittingLayout.forVariant(mech.variant()).hasSocket(SocketId.MINI_FAB)
                    || mech.internal(MechInternal.Role.VENT_ARRAY) != null)) return false;
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
        if (availableWeapon(componentId) <= 0
                || !withinBudget(mechId, mech.fit().withWeapon(slot, component))) return false;
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
                && isLessonRack(mech.weaponAt(slot))) lessonRackRemoved = true;
        mech.installWeapon(slot, null);
        return true;
    }

    /**
     * Frozen values for the active squad's mechs that can deploy now; campaign objects stay in
     * the roster. A damaged mech, one under repair, or one missing a required internal stays in
     * its gantry ({@link #heldBack}).
     */
    public List<MechDeploymentSpec> activeDeployment() {
        return activeDeployment(CampaignClock.dayFloat());
    }

    public List<MechDeploymentSpec> activeDeployment(float day) {
        CampaignMechSquad squad = activeSquad();
        if (squad == null) return List.of();
        List<MechDeploymentSpec> deployment = new ArrayList<>();
        for (CampaignMech mech : squad.mechs()) {
            if (mech.readiness(day) == CampaignMech.Readiness.READY) {
                deployment.add(mech.freezeForDeployment());
            }
        }
        return Collections.unmodifiableList(deployment);
    }

    /** The active squad's mechs left out of the drop manifest on {@code day}, and why. */
    public List<HeldBack> heldBack(float day) {
        CampaignMechSquad squad = activeSquad();
        if (squad == null) return List.of();
        List<HeldBack> held = new ArrayList<>();
        for (CampaignMech mech : squad.mechs()) {
            CampaignMech.Readiness readiness = mech.readiness(day);
            if (readiness != CampaignMech.Readiness.READY) {
                held.add(new HeldBack(mech, readiness, mech.repairDaysRemaining(day)));
            }
        }
        return Collections.unmodifiableList(held);
    }

    /** A mech the drop manifest leaves out: awaiting a repair order, or under one. */
    public record HeldBack(CampaignMech mech, CampaignMech.Readiness reason, float daysRemaining) {
    }

    /** Frozen vehicles on the active squad's gantries, in gantry order. */
    public List<VehicleDeploymentSpec> activeVehicleDeployment() {
        CampaignMechSquad squad = activeSquad();
        if (squad == null) return List.of();
        List<VehicleDeploymentSpec> deployment = new ArrayList<>();
        for (CampaignVehicle vehicle : squad.vehicles()) {
            deployment.add(vehicle.freezeForDeployment());
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
        for (CampaignMech mech : squad.mechs()) {
            addInstalledWeapons(mech);
            addInstalledInternals(mech);
        }
    }

    private void putAtLeast(String componentId, int quantity) {
        ownedReplenishers.merge(componentId, quantity, Math::max);
    }

    private void addInstalledInternals(CampaignMech mech) {
        for (MechInternal.Role role : MechInternal.Role.values()) {
            MechInternal internal = mech.internal(role);
            if (internal != null) addInternal(internal.id, 1);
        }
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
            case LEFT_ARM -> MechFittingLayout.SocketId.LEFT_ARM;
            case RIGHT_ARM -> MechFittingLayout.SocketId.RIGHT_ARM;
            case LEFT_SHOULDER -> MechFittingLayout.SocketId.LEFT_SHOULDER;
            case RIGHT_SHOULDER -> MechFittingLayout.SocketId.RIGHT_SHOULDER;
        };
    }

    private Object readResolve() {
        if (squads == null) squads = new ArrayList<>();
        if (ownedReplenishers == null) ownedReplenishers = new HashMap<>();
        if (ownedWeapons == null) ownedWeapons = new HashMap<>();
        if (ownedInternals == null) ownedInternals = new HashMap<>();
        if (knownPatternIds == null) {
            knownPatternIds = starterPatternsOnly ? starterPatternIds() : allPatternIds();
        }
        // Saves from before internals: every company knows the stock frame it is built on.
        knownPatternIds.addAll(starterPatternIds());
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
        // Existing mechs resolve their unset internals to the stock parts; own those assemblies.
        for (MechInternal internal : MechInternal.values()) {
            int installed = installedInternal(internal.id);
            if (installed > 0) ownedInternals.merge(internal.id, installed, Math::max);
        }
        if (labLesson == 0 && mechById(WaylineHound.ID) != null) beginLabLesson();
        nextFabricatedSerial = Math.max(1, nextFabricatedSerial);
        nextVehicleSerial = Math.max(1, nextVehicleSerial);
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
