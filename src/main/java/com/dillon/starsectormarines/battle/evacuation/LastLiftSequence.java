package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.story.StoryBeatLog;
import com.dillon.starsectormarines.battle.story.StoryBeatLog.Audience;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import java.util.List;

/** Finite authored road arrivals and observational hooks; squad AI owns combat. */
public final class LastLiftSequence {
    private final List<List<EntitySpec>> waves;
    private final int[] squads = {-1, -1};
    private final boolean[] handedOff = new boolean[2];
    private final StoryBeatLog log;
    private final CivilianEvacuationPlacement placement;
    private int released = -1, crossed = -1;
    private boolean terminal;
    private int lastBoarded;
    private String status = "Reach the Rill motor pool";

    public LastLiftSequence(List<EntitySpec> west, List<EntitySpec> east,
                            CivilianEvacuationPlacement placement, StoryBeatLog log) {
        if (west.size() != 6 || east.size() != 6) throw new IllegalArgumentException("Two finite six-person reserves required");
        waves = List.of(List.copyOf(west), List.copyOf(east));
        this.placement = placement; this.log = log;
    }
    public int pendingMembers() { return (squads[0] < 0 ? 6 : 0) + (squads[1] < 0 ? 6 : 0); }
    public String status() { return status; }
    public static int releaseTick(int wave, int release, int crossing) {
        int trigger = wave == 0 ? release : crossing;
        int earliest = (wave == 0 ? 70 : 120) * 30;
        int latest = (wave == 0 ? 180 : 240) * 30;
        return trigger < 0 ? latest : Math.min(latest, Math.max(earliest, trigger + 8 * 30));
    }
    public void tick(BattleSimulation sim) {
        int tick = sim.getSimTickIndex();
        updateProgress(sim);
        for (int i = 0; i < 2; i++) {
            if (squads[i] < 0 && tick >= releaseTick(i, released, crossed)) {
                squads[i] = sim.mintSquad(Faction.DEFENDER, UnitType.MILITIA);
                for (EntitySpec spec : waves.get(i)) sim.spawn(spec.squad(squads[i]));
                sim.getSquad(squads[i]).originalSize = 6;
                sim.assignSquadCommand(ObjectiveAssignment.sweepSector(squads[i], i == 0 ? 69 : 123, 64),
                        CommandAuthority.SCRIPTED, "last-lift-road", "advance along the loading street");
                emit(tick, "road_arrival_" + (i+1), Audience.OBSERVER_ONLY);
            }
            if (squads[i] < 0 || handedOff[i]) continue;
            int n = sim.squadMemberCount(squads[i]);
            float x = 0, y = 0;
            for (int j = 0; j < n; j++) {
                long id = sim.squadMemberAt(squads[i], j); x += sim.world().x(id); y += sim.world().y(id);
            }
            if (n == 0) { handedOff[i] = true; continue; }
            float dx = x / n - (i == 0 ? 69 : 123), dy = y / n - 64;
            if (dx*dx + dy*dy <= 25 || sim.getSquad(squads[i]).hasDirectContactThisTick()) {
                handedOff[i] = sim.handoffSquadCommand(squads[i], "last-lift-road", CommandAuthority.MISSION_COMMAND,
                        "extraction-defender", null, "Rill raiders begin local interdiction");
            }
        }
    }
    /** Confirmed payload facts only. Private arrivals never become player radio intelligence. */
    public void observe(BattleSimulation sim) {
        if (terminal) return;
        int tick = sim.getSimTickIndex();
        var tracker = sim.getCivilianEvacuationTracker();
        emit(tick, "start", Audience.PLAYER);
        if (sim.isCivilianEvacuationTriggered()) emit(tick,"crew_contacted",Audience.PLAYER);
        if (released >= 0) emit(tick,"crew_released",Audience.PLAYER);
        if (crossed >= 0) emit(tick,"crossing_cleared",Audience.PLAYER);
        int dead = 0;
        for (int i = 0; i < tracker.registeredCount(); i++)
            if (tracker.isConfirmedDead(tracker.entityIdAt(i))) dead++;
        boolean resolved = tracker.evacuatedCount() + dead == tracker.registeredCount();
        if (tracker.evacuatedCount() > 0) emit(tick,"first_boarding",Audience.PLAYER);
        if (resolved && tracker.evacuatedCount() > 0) emit(tick,"cohort_resolved",Audience.PLAYER);
        if (resolved && tracker.evacuatedCount() > lastBoarded) emit(tick,"last_boarding",Audience.PLAYER);
        lastBoarded = tracker.evacuatedCount();
        var hold = sim.getCivilianEscortHold();
        status = switch (hold) {
            case NO_ESCORT -> "Crew holding — restore close escort";
            case REGROUPING -> "Regrouping separated crew";
            case ROUTE_BLOCKED -> "Crew holding — clear the route";
            case PICKUP_WAITING -> "Crew waiting for employer pickup";
            default -> !sim.isCivilianEvacuationTriggered() ? "Reach and secure the motor pool"
                    : tracker.evacuatedCount() > 0 ? "Protect boarding at the southern apron"
                    : "Escort the crew across the loading street";
        };
        if (released >= 0 && (hold == CivilianEvacuationSystem.HoldReason.NO_ESCORT
                || hold == CivilianEvacuationSystem.HoldReason.ROUTE_BLOCKED)) emit(tick,"escort_hold",Audience.PLAYER);
        if (sim.isComplete()) finish(tick, sim.getWinner() == Faction.MARINE ? "victory" : "defeat");
    }
    private void updateProgress(BattleSimulation sim) {
        int tick = sim.getSimTickIndex();
        var tracker = sim.getCivilianEvacuationTracker();
        boolean moving = false;
        int rearY = -1, active = 0;
        for (int i = 0; i < tracker.registeredCount(); i++) {
            long id = tracker.entityIdAt(i);
            if (tracker.state(id) != CivilianEvacuationTracker.State.ACTIVE || sim.resolveUnit(id) == 0L) continue;
            int y = sim.world().cellY(id); rearY = Math.max(rearY, y); active++;
            moving |= y < placement.shelterApproachY - 2;
        }
        if (sim.isCivilianEvacuationTriggered() && moving && released < 0) released = tick;
        if (released >= 0 && active > 0 && rearY < 59 && crossed < 0) crossed = tick;
    }
    public void finish(int tick, String result) {
        if (terminal) return;
        emit(tick,result,Audience.PLAYER); terminal = true;
    }
    private void emit(int tick,String key,Audience audience) {
        log.emitOnce(tick,"supply_route.last_lift." + key,audience);
    }
}
