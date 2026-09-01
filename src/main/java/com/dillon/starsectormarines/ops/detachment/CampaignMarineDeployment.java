package com.dillon.starsectormarines.ops.detachment;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.fixture.MarineSeatCommitment;
import com.dillon.starsectormarines.battle.infantry.MarineLoadout;
import com.dillon.starsectormarines.battle.squad.CampaignSquadTag;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.air.ShuttleState;
import com.dillon.starsectormarines.engine.ecs.ArchetypeTable;
import com.dillon.starsectormarines.marine.MarineArmorCatalogDef;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineRosterScript;
import com.dillon.starsectormarines.marine.MarineSoldier;
import com.dillon.starsectormarines.marine.MarineSoldierStatus;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.marine.SquadExperienceStandard;
import com.dillon.starsectormarines.ops.FieldPresencePolicy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Campaign-side allocation snapshot consumed sequentially by battle shuttle seats. */
public final class CampaignMarineDeployment {

    public static final CampaignMarineDeployment EMPTY =
            new CampaignMarineDeployment(Collections.emptyList());

    private final List<MarineLoadout> seats;

    private CampaignMarineDeployment(List<MarineLoadout> seats) {
        this.seats = List.copyOf(seats);
    }

    /** Rebuilds an ordered deployment from immutable fixture-facing seat values. */
    public static CampaignMarineDeployment fromCommitments(
            List<MarineSeatCommitment> commitments) {
        Objects.requireNonNull(commitments, "commitments");
        if (commitments.isEmpty()) return EMPTY;
        List<MarineLoadout> seats = new ArrayList<>(commitments.size());
        for (MarineSeatCommitment commitment : commitments) {
            seats.add(Objects.requireNonNull(commitment, "commitment").toLoadout());
        }
        return new CampaignMarineDeployment(seats);
    }

    public static CampaignMarineDeployment freeze(List<ShuttleAssignment> manifest) {
        MarineRosterScript script = MarineRosterScript.getInstance();
        if (script == null) return EMPTY;
        return freeze(script.roster(), requiredSeats(manifest));
    }

    public static CampaignMarineDeployment freeze(MarineRoster roster, int requiredSeats) {
        return freeze(roster, Collections.emptySet(), requiredSeats);
    }

    public static CampaignMarineDeployment freeze(MarineRoster roster,
                                                   Set<String> selectedSquadIds,
                                                   int requiredSeats) {
        return freeze(roster, selectedSquadIds, requiredSeats, false);
    }

    /** Fail-closed briefing selection; empty means no named personnel, not the whole line. */
    public static CampaignMarineDeployment freezeSelection(
            MarineRoster roster, Set<String> selectedSquadIds,
            int requiredSeats) {
        return freeze(roster, selectedSquadIds, requiredSeats, true);
    }

    private static CampaignMarineDeployment freeze(
            MarineRoster roster, Set<String> selectedSquadIds,
            int requiredSeats, boolean explicitSelection) {
        if (roster == null || requiredSeats <= 0) return EMPTY;
        List<MarineLoadout> frozen = new ArrayList<>(requiredSeats);
        List<MarineSoldier> active = new ArrayList<>();
        // Parallel to `active`: the squad each seat came from, so the manifest
        // carries organizational identity across the seam and not just names.
        List<MarineSquad> owners = new ArrayList<>();
        boolean hasExplicitSelection = explicitSelection
                || selectedSquadIds != null && !selectedSquadIds.isEmpty();
        if (hasExplicitSelection) {
            // Roster order, not Set order: seat assignment has to be the same on
            // every run for the same roster and selection, and iterating the
            // selection Set is not. Members of one squad stay adjacent, so a lift
            // carries whole fire teams rather than a slice across two squads.
            for (MarineSquad squad : roster.squads()) {
                if (selectedSquadIds == null || !selectedSquadIds.contains(squad.id())) continue;
                for (MarineSoldier soldier : roster.squadMembers(squad)) {
                    if (soldier.status() == MarineSoldierStatus.ACTIVE) {
                        active.add(soldier);
                        owners.add(squad);
                    }
                }
            }
        } else {
            for (MarineSoldier soldier : roster.lineReadySoldiers()) {
                active.add(soldier);
                owners.add(roster.squadForSoldier(soldier.id()));
            }
        }
        int seats = Math.min(requiredSeats, active.size());
        // Counted over the seats that actually fit, not the squad's manning:
        // a manifest can be short, and the battle tier assembles toward what
        // was loaded rather than toward what is back home.
        Map<String, Integer> strengths = new HashMap<>();
        for (int i = 0; i < seats; i++) {
            MarineSquad owner = owners.get(i);
            if (owner != null) strengths.merge(owner.id(), 1, Integer::sum);
        }
        for (int i = 0; i < seats; i++) {
            MarineSoldier soldier = active.get(i);
            MarineArmorCatalogDef armor = soldier.armorDef();
            frozen.add(MarineLoadout.fromCatalog(UnitRole.COMBATANT, null,
                    soldier.primaryDef(), soldier.primaryGrade(),
                    SquadExperienceStandard.profileFor(soldier),
                    soldier.specialEquipmentDef(),
                    soldier.id(), armor.appearanceFamily(),
                    armor.armorCapacity(), armor.armorRating(),
                    armor.moveSpeedMult(), armor.incomingAccuracyMult(),
                    tag(roster, owners.get(i), soldier, strengths),
                    armor.integralSystem()));
        }
        return new CampaignMarineDeployment(frozen);
    }

    public MarineLoadout seat(int index) {
        return index >= 0 && index < seats.size() ? seats.get(index) : null;
    }

    public int size() { return seats.size(); }

    /** Immutable ordered values suitable for a launch fixture. */
    public List<MarineSeatCommitment> commitments() {
        if (seats.isEmpty()) return List.of();
        List<MarineSeatCommitment> commitments = new ArrayList<>(seats.size());
        for (MarineLoadout seat : seats) {
            commitments.add(MarineSeatCommitment.capture(seat));
        }
        return List.copyOf(commitments);
    }

    /** Replace generated seat stats with this frozen campaign allocation. */
    public void applyTo(BattleSimulation sim) {
        applyTo(sim, 0);
    }

    /** Applies only after skipping employer-owned physical shuttle missions. */
    public void applyTo(BattleSimulation sim, int shuttleMissionsToSkip) {
        applyTo(sim, shuttleMissionsToSkip, FieldPresencePolicy.UNRESTRICTED);
    }

    /** Applies named seats and preserves whole-squad sortie boundaries when limited. */
    public void applyTo(BattleSimulation sim, int shuttleMissionsToSkip,
                        FieldPresencePolicy fieldPresencePolicy) {
        if (sim == null || seats.isEmpty()) return;
        BattleComponents components = sim.getBattleComponents();
        List<ShuttleMission> missions = new ArrayList<>();
        for (ArchetypeTable table : sim.getEntityWorld().matched(components.airCraft)) {
            Object[] encoded = table.objects(components.SHUTTLE_MISSION,
                    BattleComponents.SHUTTLE_MISSION_STATE).array();
            for (int row = 0; row < table.rowCount(); row++) {
                ShuttleMission mission = (ShuttleMission) encoded[row];
                if (mission != null) missions.add(mission);
            }
        }

        boolean manifestOrdered = !missions.isEmpty()
                && missions.stream().allMatch(mission -> mission.manifestOrdinal >= 0);
        if (manifestOrdered) {
            missions.sort(Comparator.comparingInt(mission -> mission.manifestOrdinal));
            List<ShuttleMission> playerMissions = missions.stream()
                    .filter(mission -> mission.manifestOrdinal
                            >= Math.max(0, shuttleMissionsToSkip))
                    .toList();
            if (fieldPresencePolicy != null && fieldPresencePolicy.limited()) {
                applyLimitedFieldOrder(playerMissions);
                return;
            }
            applyPairedWaveOrder(playerMissions);
            return;
        }

        if (fieldPresencePolicy != null && fieldPresencePolicy.limited()) {
            int skip = Math.max(0, Math.min(shuttleMissionsToSkip, missions.size()));
            applyLimitedFieldOrder(missions.subList(skip, missions.size()));
            return;
        }

        int seatIndex = 0;
        int missionIndex = 0;
        for (ShuttleMission mission : missions) {
                if (missionIndex++ < Math.max(0, shuttleMissionsToSkip)) continue;
                MarineLoadout[][] cycles = mission.cycleLoadouts;
                if (cycles == null || cycles.length == 0) {
                    cycles = new MarineLoadout[][]{mission.marineLoadout};
                }
                for (int cycle = 0; cycle < cycles.length; cycle++) {
                    MarineLoadout[] generated = cycles[cycle];
                    if (generated == null) continue;
                    MarineLoadout[] applied = new MarineLoadout[generated.length];
                    for (int slot = 0; slot < generated.length; slot++) {
                        MarineLoadout prior = generated[slot] != null
                                ? generated[slot] : MarineLoadout.COMBATANT;
                        MarineLoadout allocated = seat(seatIndex++);
                        applied[slot] = allocated != null ? merge(prior, allocated) : prior;
                    }
                    cycles[cycle] = applied;
                }
                mission.cycleLoadouts = cycles;
                mission.marineLoadout = cycles[0];
                mission.marinesRemaining = cycles[0] != null
                        ? cycles[0].length : 0;
        }
    }

    /**
     * Re-packs player sorties so one craft never mixes two persistent squads.
     * Chunks rotate over the already-constructed physical craft and may extend
     * their cycle schedules when an under-manned squad leaves seats unused.
     */
    private void applyLimitedFieldOrder(List<ShuttleMission> missions) {
        if (missions.isEmpty()) return;
        List<MarineLoadout> scenarioSeats = new ArrayList<>();
        for (ShuttleMission mission : missions) {
            MarineLoadout[][] cycles = mission.cycleLoadouts;
            if (cycles == null || cycles.length == 0) {
                cycles = new MarineLoadout[][]{mission.marineLoadout};
            }
            for (MarineLoadout[] cycle : cycles) {
                if (cycle != null) Collections.addAll(scenarioSeats, cycle);
            }
        }

        List<List<MarineLoadout[]>> planned = new ArrayList<>(missions.size());
        for (int i = 0; i < missions.size(); i++) planned.add(new ArrayList<>());
        int seatIndex = 0;
        int scenarioIndex = 0;
        int sortie = 0;
        while (seatIndex < seats.size()) {
            String squadId = campaignSquadId(seats.get(seatIndex));
            if (squadId == null) {
                throw new IllegalStateException(
                        "Limited field presence requires persistent squad identity");
            }
            int missionIndex = sortie++ % missions.size();
            ShuttleMission mission = missions.get(missionIndex);
            int capacity = Math.max(1, mission.seatsPerSortie);
            List<MarineLoadout> load = new ArrayList<>(capacity);
            while (seatIndex < seats.size() && load.size() < capacity
                    && squadId.equals(campaignSquadId(seats.get(seatIndex)))) {
                MarineLoadout scenario = scenarioIndex < scenarioSeats.size()
                        ? scenarioSeats.get(scenarioIndex) : MarineLoadout.COMBATANT;
                if (scenario == null) scenario = MarineLoadout.COMBATANT;
                load.add(merge(scenario, seats.get(seatIndex)));
                seatIndex++;
                scenarioIndex++;
            }
            planned.get(missionIndex).add(load.toArray(new MarineLoadout[0]));
        }

        for (int i = 0; i < missions.size(); i++) {
            ShuttleMission mission = missions.get(i);
            List<MarineLoadout[]> loads = planned.get(i);
            mission.fieldPresenceAdmitted = false;
            mission.currentCycle = 0;
            mission.deboardedThisSortie = 0;
            mission.squadId = Squad.NO_SQUAD;
            if (loads.isEmpty()) {
                mission.cycleLoadouts = new MarineLoadout[][]{new MarineLoadout[0]};
                mission.marineLoadout = mission.cycleLoadouts[0];
                mission.marinesRemaining = 0;
                mission.totalCycles = 1;
                mission.state = ShuttleState.GONE;
                continue;
            }
            mission.cycleLoadouts = loads.toArray(new MarineLoadout[0][]);
            mission.marineLoadout = mission.cycleLoadouts[0];
            mission.marinesRemaining = mission.marineLoadout.length;
            mission.totalCycles = mission.cycleLoadouts.length;
        }
    }

    private static String campaignSquadId(MarineLoadout loadout) {
        return loadout != null && loadout.campaignSquad != null
                ? loadout.campaignSquad.squadId : null;
    }

    /**
     * Conquest seats are filled wave-major: both six-seat craft receive one
     * complete twelve-marine squad before either craft is loaded for its next
     * sortie. Explicit manifest ordinals keep this stable across ECS tables.
     */
    private void applyPairedWaveOrder(List<ShuttleMission> missions) {
        int seatIndex = 0;
        int maxCycles = missions.stream().mapToInt(mission -> {
            MarineLoadout[][] cycles = mission.cycleLoadouts;
            return cycles != null && cycles.length > 0 ? cycles.length : 1;
        }).max().orElse(0);
        for (int cycle = 0; cycle < maxCycles; cycle++) {
            for (ShuttleMission mission : missions) {
                MarineLoadout[][] cycles = mission.cycleLoadouts;
                if (cycles == null || cycles.length == 0) {
                    cycles = new MarineLoadout[][]{mission.marineLoadout};
                }
                if (cycle >= cycles.length || cycles[cycle] == null) continue;
                MarineLoadout[] generated = cycles[cycle];
                MarineLoadout[] applied = new MarineLoadout[generated.length];
                for (int slot = 0; slot < generated.length; slot++) {
                    MarineLoadout prior = generated[slot] != null
                            ? generated[slot] : MarineLoadout.COMBATANT;
                    MarineLoadout allocated = seat(seatIndex++);
                    applied[slot] = allocated != null ? merge(prior, allocated) : prior;
                }
                cycles[cycle] = applied;
                mission.cycleLoadouts = cycles;
                if (cycle == 0) {
                    mission.marineLoadout = cycles[0];
                    mission.marinesRemaining = cycles[0] != null
                            ? cycles[0].length : 0;
                }
            }
        }
    }

    private static MarineLoadout merge(MarineLoadout scenario, MarineLoadout allocation) {
        return new MarineLoadout(scenario.role, scenario.objective,
                allocation.primaryWeaponId, allocation.equipmentGrade,
                allocation.soldierProfile, allocation.specialEquipmentId,
                allocation.secondaryAmmo,
                allocation.campaignSoldierId, allocation.armorFamily,
                allocation.armorCapacity, allocation.armorRating,
                allocation.armorMoveSpeedMult, allocation.armorIncomingAccuracyMult,
                allocation.campaignSquad, allocation.integralSystem);
    }

    /**
     * Freezes the squad's identity onto one seat. The label is copied, not
     * referenced — the battle tier has no roster access, and a rename back home
     * mid-battle must not change what the HUD says.
     */
    private static CampaignSquadTag tag(MarineRoster roster, MarineSquad squad,
                                        MarineSoldier soldier,
                                        Map<String, Integer> strengths) {
        if (squad == null || squad.reserve()) return null;
        return new CampaignSquadTag(squad.id(), squad.name(),
                soldier.id().equals(squad.leaderSoldierId()),
                strengths.getOrDefault(squad.id(), 0),
                roster.teamIndexOf(squad, soldier.id()));
    }

    public static int requiredSeats(List<ShuttleAssignment> manifest, int firstAssignment) {
        if (manifest == null) return 0;
        long total = 0L;
        for (int i = Math.max(0, firstAssignment); i < manifest.size(); i++) {
            ShuttleAssignment assignment = manifest.get(i);
            if (assignment != null) {
                total += assignment.embarkedPersonnel;
                if (total >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
            }
        }
        return (int) total;
    }

    private static int requiredSeats(List<ShuttleAssignment> manifest) {
        return requiredSeats(manifest, 0);
    }

}
