package com.dillon.starsectormarines.ops.detachment;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.appearance.LayeredArmorFamily;
import com.dillon.starsectormarines.battle.infantry.MarineLoadout;
import com.dillon.starsectormarines.battle.infantry.MarineSecondary;
import com.dillon.starsectormarines.battle.squad.CampaignSquadTag;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.engine.ecs.ArchetypeTable;
import com.dillon.starsectormarines.marine.MarineArmorPattern;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineRosterScript;
import com.dillon.starsectormarines.marine.MarineSoldier;
import com.dillon.starsectormarines.marine.MarineSoldierStatus;
import com.dillon.starsectormarines.marine.MarineSquad;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/** Campaign-side allocation snapshot consumed sequentially by battle shuttle seats. */
public final class CampaignMarineDeployment {

    public static final CampaignMarineDeployment EMPTY =
            new CampaignMarineDeployment(Collections.emptyList());

    private final List<MarineLoadout> seats;

    private CampaignMarineDeployment(List<MarineLoadout> seats) {
        this.seats = Collections.unmodifiableList(seats);
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
        for (int i = 0; i < Math.min(requiredSeats, active.size()); i++) {
            MarineSoldier soldier = active.get(i);
            MarineSecondary secondary = soldier.secondary();
            frozen.add(new MarineLoadout(UnitRole.COMBATANT, null,
                    soldier.primary(), soldier.primaryGrade(), soldier.profile(),
                    secondary, secondary != null ? secondary.startingAmmo : 0,
                    soldier.id(), armorFamily(soldier.armor()),
                    soldier.armor().bonusHp, soldier.armor().damageReduction,
                    soldier.armor().moveSpeedMult, soldier.armor().incomingAccuracyMult,
                    tag(owners.get(i), soldier)));
        }
        return new CampaignMarineDeployment(frozen);
    }

    /** Builds a complete picker-only allocation with no campaign identities. */
    public static CampaignMarineDeployment debugFixture(DebugPersonnelPreset preset,
                                                         int requiredSeats) {
        if (requiredSeats <= 0) return EMPTY;
        DebugPersonnelPreset resolved = preset != null
                ? preset : DebugPersonnelPreset.MIXED;
        List<MarineLoadout> frozen = new ArrayList<>(requiredSeats);
        for (int seat = 0; seat < requiredSeats; seat++) {
            frozen.add(resolved.loadout(seat));
        }
        return new CampaignMarineDeployment(frozen);
    }

    public MarineLoadout seat(int index) {
        return index >= 0 && index < seats.size() ? seats.get(index) : null;
    }

    public int size() { return seats.size(); }

    /** Replace generated seat stats with this frozen campaign allocation. */
    public void applyTo(BattleSimulation sim) {
        applyTo(sim, 0);
    }

    /** Applies only after skipping employer-owned physical shuttle missions. */
    public void applyTo(BattleSimulation sim, int shuttleMissionsToSkip) {
        if (sim == null || seats.isEmpty()) return;
        BattleComponents components = sim.getBattleComponents();
        int seatIndex = 0;
        int missionIndex = 0;
        for (ArchetypeTable table : sim.getEntityWorld().matched(components.airCraft)) {
            Object[] missions = table.objects(components.SHUTTLE_MISSION,
                    BattleComponents.SHUTTLE_MISSION_STATE).array();
            for (int row = 0; row < table.rowCount(); row++) {
                ShuttleMission mission = (ShuttleMission) missions[row];
                if (mission == null) continue;
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
            }
        }
    }

    private static MarineLoadout merge(MarineLoadout scenario, MarineLoadout allocation) {
        return new MarineLoadout(scenario.role, scenario.objective,
                allocation.primary, allocation.equipmentGrade, allocation.soldierProfile,
                allocation.secondary, allocation.secondaryAmmo,
                allocation.campaignSoldierId, allocation.armorFamily,
                allocation.armorBonusHp, allocation.armorDamageReduction,
                allocation.armorMoveSpeedMult, allocation.armorIncomingAccuracyMult,
                allocation.campaignSquad);
    }

    /**
     * Freezes the squad's identity onto one seat. The label is copied, not
     * referenced — the battle tier has no roster access, and a rename back home
     * mid-battle must not change what the HUD says.
     */
    private static CampaignSquadTag tag(MarineSquad squad, MarineSoldier soldier) {
        if (squad == null || squad.reserve()) return null;
        return new CampaignSquadTag(squad.id(), squad.name(),
                soldier.id().equals(squad.leaderSoldierId()));
    }

    public static int requiredSeats(List<ShuttleAssignment> manifest, int firstAssignment) {
        if (manifest == null) return 0;
        int total = 0;
        for (int i = Math.max(0, firstAssignment); i < manifest.size(); i++) {
            ShuttleAssignment assignment = manifest.get(i);
            if (assignment != null) total += assignment.type.capacity * assignment.cycles;
        }
        return total;
    }

    private static int requiredSeats(List<ShuttleAssignment> manifest) {
        return requiredSeats(manifest, 0);
    }

    private static LayeredArmorFamily armorFamily(MarineArmorPattern armor) {
        if (armor == null) return LayeredArmorFamily.ARMORLESS;
        return switch (armor) {
            case ARMORLESS -> LayeredArmorFamily.ARMORLESS;
            case CHARCOAL -> LayeredArmorFamily.CHARCOAL;
            case BLUE_SCOUT -> LayeredArmorFamily.BLUE_SCOUT;
            case RED_ELITE -> LayeredArmorFamily.RED_ELITE;
            case OUTLAW -> LayeredArmorFamily.OUTLAW;
            case ARMY_GREEN -> LayeredArmorFamily.ARMY_GREEN;
            case MILITIA -> LayeredArmorFamily.MILITIA;
        };
    }
}
