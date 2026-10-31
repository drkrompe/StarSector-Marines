package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.SmallWarCommand;
import com.dillon.starsectormarines.battle.command.objective.Objective;
import com.dillon.starsectormarines.battle.command.objective.ObjectiveFactionPresence;
import com.dillon.starsectormarines.battle.setup.SmallWarForces.Commitment;
import com.dillon.starsectormarines.battle.setup.SmallWarForces.Group;
import com.dillon.starsectormarines.battle.setup.SmallWarForces.SplitSide;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.story.SmallWarProgress;
import com.dillon.starsectormarines.battle.story.SmallWarProgress.Phase;
import com.dillon.starsectormarines.battle.story.StoryBeatLog;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.BlackSluice;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Black Sluice referee and Greyhook's split. Sites and the handover are read from physical
 * presence; there is no construction step and no tender on this mission; the split happens only when fuel control actually falls, and those who
 * obey it walk off the map along the service road, so the withdrawal is strength Greyhook loses
 * rather than a despawn. The optional {@link SmallWarDuel} hears the order; nothing here waits on it, but the battle
 * itself does not end until the duel's story has concluded (the duel holds it open through the warships).
 */
public final class SmallWarSequence implements Objective {
    /** One outfit's account: who stood or was committed, who is still here, and who walked out. */
    public record ForceReport(int committed, int deployed, int living, int withdrawn) { }

    private final SmallWarProgress progress = new SmallWarProgress();
    private final int custody;
    private final List<Integer> holders;
    private final long supportGun;
    private final boolean houseCustody;
    private final Map<Integer, Group> squadGroups = new LinkedHashMap<>();
    private final Map<Group, List<EntitySpec>> reserves;
    private final Set<Group> released = new HashSet<>();
    private final Set<Integer> withdrawing = new HashSet<>();
    private final Set<Long> withdrawn = new HashSet<>();
    private final Map<SmallWarForces.Outfit, Integer> withdrawnByOutfit = new HashMap<>();
    private final StoryBeatLog log;
    /** The story beat raised when Voss orders Black Sluice abandoned. */
    public static final String VOSS_ABANDON_BEAT = "small_war.voss_abandon";
    private SmallWarDuel duel;
    private boolean initialized, liftDown;
    /** Seconds since customs first fell; drives the bounded form-up before the push on fuel control. */
    private float sinceCustoms = -1;
    /** The longest the company waits at the staging ground for the rest of its lift before pushing on regardless. */
    public static final float FORM_UP_LIMIT_SECONDS = 120;

    public SmallWarSequence(int custody, List<Integer> holders, Map<Integer, Group> startingSquads,
                            Map<Group, List<EntitySpec>> reserves, long supportGun, boolean houseCustody, StoryBeatLog log) {
        if (holders.size() != 3) throw new IllegalArgumentException("Black Sluice needs one holding detachment per site");
        this.custody = custody; this.holders = List.copyOf(holders); this.supportGun = supportGun; this.houseCustody = houseCustody; this.log = log;
        squadGroups.putAll(startingSquads);
        var copy = new LinkedHashMap<Group, List<EntitySpec>>();
        reserves.forEach((group, members) -> {
            if (!group.reserve() || members.size() != group.size()) throw new IllegalArgumentException("Reserve must match its authored group");
            copy.put(group, List.copyOf(members));
        });
        this.reserves = copy;
    }

    /** Install the duel overhead before the first tick. Absent by default. */
    public void installDuel(SmallWarDuel value) {
        if (initialized || duel != null) throw new IllegalStateException("Install the duel once at setup");
        duel = value;
    }
    public SmallWarDuel duel() { return duel; }
    public SmallWarProgress progress() { return progress; }
    /**
     * Whether the company forms up at the staging ground south of customs instead of pushing on: customs has
     * fallen, fuel control has not yet, and the company's lift is still bringing people in, for at most
     * {@link #FORM_UP_LIMIT_SECONDS}. A command choice, not an objective: nothing waits on it.
     */
    public boolean formingUp() {
        return progress.phase() == Phase.FUEL_CONTROL && !progress.visited(SmallWarProgress.FUEL_CONTROL)
                && !liftDown && sinceCustoms >= 0 && sinceCustoms < FORM_UP_LIMIT_SECONDS;
    }
    public int custodySquadId() { return custody; }
    public List<Integer> holderSquadIds() { return holders; }
    public long supportGunId() { return supportGun; }
    public boolean houseCustody() { return houseCustody; }
    public Map<Integer, Group> squadGroups() { return Map.copyOf(squadGroups); }
    public Set<Integer> withdrawingSquads() { return Set.copyOf(withdrawing); }
    public int pendingMembers() {
        if (progress.terminal()) return 0;
        int pending = 0;
        for (var entry : reserves.entrySet()) if (!released.contains(entry.getKey())) pending += entry.getValue().size();
        return pending;
    }

    public SmallWarProgress.Report report(BattleSimulation sim) {
        if (sim.isForcedVictory()) return progress.wonReport(withdrawn.size(), houseCustody);
        return progress.report(SmallWarProgress.CUSTODIANS - sim.squadMemberCount(custody),
                SmallWarProgress.HOLDERS - holders.stream().mapToInt(sim::squadMemberCount).sum(), withdrawn.size(), houseCustody);
    }

    /** Per-outfit strength: what was committed, what actually reached the field, who lives and who left. */
    public Map<SmallWarForces.Outfit, ForceReport> forceReport(BattleSimulation sim) {
        var out = new LinkedHashMap<SmallWarForces.Outfit, ForceReport>();
        for (var outfit : SmallWarForces.Outfit.values()) {
            int committed = 0, deployed = 0, living = 0;
            for (var group : SmallWarForces.GROUPS) if (group.outfit() == outfit) {
                committed += group.size();
                if (!group.reserve() || released.contains(group)) deployed += group.size();
            }
            for (var entry : squadGroups.entrySet()) if (entry.getValue().outfit() == outfit) living += sim.squadMemberCount(entry.getKey());
            out.put(outfit, new ForceReport(committed, deployed, living, withdrawnByOutfit.getOrDefault(outfit, 0)));
        }
        return out;
    }

    public void advance(BattleSimulation sim) {
        if (sim.isComplete()) return;
        if (progress.terminal()) {
            // The ground is decided; the duel overhead plays on until it concludes, and the battle waits for it.
            if (duel != null && progress.split()) duel.advance(sim);
            return;
        }
        if (!initialized) { initialized = true; emit(sim, "mobilization"); }
        Phase before = progress.phase();
        liftDown = companyLiftDown(sim);
        if (sinceCustoms >= 0) sinceCustoms += BattleSimulation.TICK_DT;
        int company = 0, friendly = 0, contested = 0;
        boolean custodyInRoom = false;
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long id = sim.liveUnitAt(i);
            Faction side = sim.identity().faction(id);
            int cx = sim.world().cellX(id), cy = sim.world().cellY(id);
            for (int site = 0; site < 3; site++) {
                var place = BlackSluice.SITES.get(site);
                boolean inside = BlackSluice.inside(sim.getTopology(), cx, cy, site);
                if (inside && isCompany(sim, id)) company |= 1 << site;
                if (inside && (isCompany(sim, id) || side == Faction.ALLY && sim.identity().type(id).combatant)) friendly |= 1 << site;
                if (site == SmallWarProgress.FUEL_CONTROL && inside && sim.squad().hasSquad(id) && sim.squad().squadId(id) == custody) custodyInRoom = true;
                if (Faction.MARINE.hostileTo(side) && sim.identity().type(id).combatant
                        && (inside || Math.hypot(sim.world().x(id) - place.x(), sim.world().y(id) - place.y()) < 12)) contested |= 1 << site;
            }
        }
        boolean split = progress.advance(BattleSimulation.TICK_DT, companyInPlay(sim), infrastructureIntact(sim),
                sim.squadMemberCount(custody) > 0, custodyInRoom, company, friendly, contested);
        if (progress.visited(SmallWarProgress.CUSTOMS)) {
            if (sinceCustoms < 0) sinceCustoms = 0;
            release(sim, Commitment.CUSTOMS_FALLS);
        }
        if (split) split(sim);
        observeExits(sim);
        if (duel != null && progress.split()) duel.advance(sim);
        if (before != progress.phase()) emit(sim, progress.phase().name().toLowerCase(Locale.ROOT));
    }

    private void split(BattleSimulation sim) {
        log.emitOnce(sim.getSimTickIndex(), VOSS_ABANDON_BEAT, StoryBeatLog.Audience.PLAYER);
        for (var entry : squadGroups.entrySet())
            if (entry.getValue().split() == SplitSide.WITHDRAWS) withdrawing.add(entry.getKey());
        release(sim, Commitment.FUEL_CONTROL_FALLS);
        log.emitOnce(sim.getSimTickIndex(), "small_war.berth_four_refuses", StoryBeatLog.Audience.PLAYER);
        if (duel != null) duel.withdrawalOrdered(sim);
    }

    private void release(BattleSimulation sim, Commitment trigger) {
        for (var entry : reserves.entrySet()) {
            var group = entry.getKey();
            if (group.commitment() != trigger || !released.add(group)) continue;
            int squad = sim.mintSquad(Faction.DEFENDER, UnitType.MILITIA);
            for (var member : entry.getValue()) sim.spawn(member.squad(squad));
            sim.getSquad(squad).originalSize = group.size();
            sim.claimSquadCommand(squad, CommandAuthority.MISSION_COMMAND, SmallWarCommand.issuer(Faction.DEFENDER),
                    group.name() + " committed from the " + group.ingress());
            squadGroups.put(squad, group);
            // A reserve committed after the order has already heard it.
            if (progress.split() && group.split() == SplitSide.WITHDRAWS) withdrawing.add(squad);
            log.emitOnce(sim.getSimTickIndex(), "small_war.reserve_" + group.id(), StoryBeatLog.Audience.OBSERVER_ONLY);
        }
    }

    private void observeExits(BattleSimulation sim) {
        var departing = new ArrayList<long[]>();
        for (int squadId : withdrawing) {
            for (int member = 0; member < sim.squadMemberCount(squadId); member++) {
                long id = sim.squadMemberAt(squadId, member);
                if (BlackSluice.atExit(sim.world().x(id), sim.world().y(id))) departing.add(new long[]{id, squadId});
            }
        }
        for (long[] leaving : departing) {
            if (!withdrawn.add(leaving[0])) continue;
            withdrawnByOutfit.merge(squadGroups.get((int) leaving[1]).outfit(), 1, Integer::sum);
            sim.takeOffTheField(leaving[0]);
            log.emitOnce(sim.getSimTickIndex(), "small_war.withdrawn_" + leaving[0], StoryBeatLog.Audience.OBSERVER_ONLY);
        }
    }

    /** Every company boat has flown its last sortie and set down everyone aboard it. */
    private static boolean companyLiftDown(BattleSimulation sim) {
        for (long air : sim.getAirEntityIds()) {
            if (sim.world().airFaction(air) != Faction.MARINE) continue;
            var flight = sim.world().mission(air);
            if (flight == null || flight.cycleLoadouts == null) continue;
            if (flight.currentCycle + 1 < flight.totalCycles || flight.marinesRemaining > 0) return false;
        }
        return true;
    }

    /** Every marked tank still stands, intact or damaged; a destroyed tank is lost infrastructure. */
    public static boolean infrastructureIntact(BattleSimulation sim) {
        for (int[] tank : BlackSluice.TANKS) {
            boolean standing = sim.getDoodads().stream().anyMatch(prop -> prop.cellX == tank[0] && prop.cellY == tank[1]
                    && (BlackSluice.TANK_ID.equals(prop.definitionId()) || (BlackSluice.TANK_ID + "-damaged").equals(prop.definitionId())));
            if (!standing) return false;
        }
        return true;
    }

    private static boolean companyInPlay(BattleSimulation sim) {
        return ObjectiveFactionPresence.anyInPlay(sim, Faction.MARINE, id -> isCompany(sim, id));
    }
    static boolean isCompany(BattleSimulation sim, long id) {
        return sim.identity().faction(id) == Faction.MARINE && switch (sim.identity().type(id)) {
            case MARINE, MARINE_BLUE, MARINE_RED -> true; default -> false;
        };
    }
    private void emit(BattleSimulation sim, String beat) { log.emitOnce(sim.getSimTickIndex(), "small_war." + beat, StoryBeatLog.Audience.PLAYER); }
    public Faction owningFaction() { return Faction.MARINE; }
    public void tick(BattleView ignored) { }
    public boolean isComplete() { return progress.phase() == Phase.COMPLETE; }
    public boolean isFailed() { return progress.phase() == Phase.FAILED; }
    public String displayName() { return switch (progress.phase()) {
        case CUSTOMS -> Strings.get("smallWarCustoms");
        case FUEL_CONTROL -> Strings.get("smallWarFuelControl");
        case DISPATCH -> Strings.get("smallWarDispatch");
        case HANDOVER -> Strings.get("smallWarHandover");
        case COMPLETE -> Strings.get("smallWarComplete");
        case FAILED -> progress.report(0, 0, 0, houseCustody).infrastructureLost() ? Strings.get("smallWarFailedInfrastructure")
                : Strings.get("smallWarFailed");
    }; }
}
