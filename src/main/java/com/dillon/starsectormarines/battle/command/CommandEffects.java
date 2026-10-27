package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.airsupport.AirPoolService;
import com.dillon.starsectormarines.battle.assault.MachineGuards;
import com.dillon.starsectormarines.battle.power.MechDropService;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.Set;

/**
 * The few live side effects a commander may make after its plan is committed, behind a narrow interface so a
 * strategy never holds the live battle. Pure planning stays pure: the plan wishes, and
 * {@link AutonomousMissionCommand#afterCommit} acts here, once, serially.
 */
public interface CommandEffects {

    /** No effects available: a battle that is not controllable, or a test. */
    CommandEffects NONE = new CommandEffects() {
        @Override public int tick() { return -1; }
        @Override public int mechLancesRemaining(Faction side) { return 0; }
        @Override public MechDropService.Outcome callMechLance(Faction side, SquadCommandClaim claim, int hintX, int hintY) {
            return MechDropService.Outcome.NO_ALLOWANCE;
        }
        @Override public AirPoolService.Outcome airStrike(Faction side, float x, float y, Set<Integer> operatorSquads) {
            return AirPoolService.Outcome.NO_AIRFRAME;
        }
        @Override public AirPoolService.Outcome airCover(Faction side, float x, float y) {
            return AirPoolService.Outcome.NO_AIRFRAME;
        }
    };

    int tick();

    /** Lances {@code side} may still call down. */
    int mechLancesRemaining(Faction side);

    /** Calls a lance down at the drop zone near the hint, claimed by {@code claim}. */
    MechDropService.Outcome callMechLance(Faction side, SquadCommandClaim claim, int hintX, int hintY);

    /**
     * Asks the side's fire-support officer for a strike on a point, directed through a forward observer in one of the named
     * squads. The officer's own judgement of the anti-air over the point applies: {@code TOO_DEFENDED} is a refusal, not an error.
     */
    AirPoolService.Outcome airStrike(Faction side, float x, float y, Set<Integer> operatorSquads);

    /**
     * Asks the side's fire-support officer for a fighter on combat air patrol over friendly ground, directed through any
     * comms operator the side has in radio range of it. The officer's own judgement applies: a fighter is kept back for
     * strafes, and anti-air it believes too strong over the ground is a refusal ({@code TOO_DEFENDED}), not an error.
     */
    AirPoolService.Outcome airCover(Faction side, float x, float y);

    /**
     * Orders every chassis of mech lance {@code squadId} of {@code side} to stand guard by the garrisonable at the
     * cell, which the side mans (`garrisonables.md`, machine guard; {@code MachineGuards}). A chassis the player has an
     * order on keeps it, and a lance the player commands is never posted.
     *
     * @return the chassis ordered; zero when the squad is not a lance of the side or the side does not man the place
     */
    default int guardPlace(Faction side, int squadId, int cellX, int cellY) { return 0; }

    /** Stands every chassis of lance {@code squadId} down from a guard a commander gave it; a player's order stays. */
    default void standDownGuard(int squadId) { }

    /**
     * Hands squad {@code squadId} from {@code fromIssuer} to this commander ({@code toIssuer}) under mission command, with
     * no order yet: a garrison sortieing into a counter-attack. Nothing happens when {@code fromIssuer} no longer owns it.
     */
    default boolean takeOverSquad(int squadId, String fromIssuer, String toIssuer, String reason) { return false; }

    /** The effects of a live battle, or {@link #NONE} where it cannot be controlled. */
    static CommandEffects over(BattleView sim) {
        if (!(sim instanceof BattleControl control)) return NONE;
        return new CommandEffects() {
            @Override public int tick() { return control.getSimTickIndex(); }
            @Override public int mechLancesRemaining(Faction side) { return control.mechDrops().lancesRemaining(side); }
            @Override public MechDropService.Outcome callMechLance(Faction side, SquadCommandClaim claim, int hintX, int hintY) {
                return control.callMechDrop(side, claim, hintX, hintY);
            }
            @Override public AirPoolService.Outcome airStrike(Faction side, float x, float y, Set<Integer> operatorSquads) {
                if (!(sim instanceof BattleSimulation live)) return AirPoolService.Outcome.NO_AIRFRAME;
                AirPoolService pool = live.airPool();
                return pool.director(side).strike(live, pool, x, y, false, unit ->
                        live.squad().hasSquad(unit) && operatorSquads.contains(live.squad().squadId(unit)));
            }
            @Override public AirPoolService.Outcome airCover(Faction side, float x, float y) {
                if (!(sim instanceof BattleSimulation live)) return AirPoolService.Outcome.NO_AIRFRAME;
                AirPoolService pool = live.airPool();
                return pool.director(side).cover(live, pool, x, y, unit -> true);
            }
            @Override public int guardPlace(Faction side, int squadId, int cellX, int cellY) {
                if (!(sim instanceof BattleSimulation live)) return 0;
                Squad squad = live.getSquad(squadId);
                if (squad == null || squad.faction != side || !squad.isMechSquad() || live.playerCommands(squad)
                        || MachineGuards.targetUnderClick(live, side, cellX, cellY) == null) return 0;
                int ordered = 0;
                for (int i = 0, n = live.squadMemberCount(squadId); i < n; i++) {
                    long member = live.squadMemberAt(squadId, i);
                    if (!live.world().isAlive(member) || !live.world().hasMechLoadout(member)) continue;
                    live.getMechMoveOrderService().requestGuard(member, cellX, cellY);
                    ordered++;
                }
                return ordered;
            }
            @Override public void standDownGuard(int squadId) {
                if (!(sim instanceof BattleSimulation live) || live.getSquad(squadId) == null) return;
                for (int i = 0, n = live.squadMemberCount(squadId); i < n; i++) {
                    live.getMechMoveOrderService().standDownGuard(live.squadMemberAt(squadId, i));
                }
            }
            @Override public boolean takeOverSquad(int squadId, String fromIssuer, String toIssuer, String reason) {
                Squad squad = control.getSquad(squadId);
                if (squad == null) return false;
                if (!control.handoffSquadCommand(squadId, fromIssuer, CommandAuthority.MISSION_COMMAND, toIssuer, null,
                        reason)) return false;
                // Its spawn-time post and its ambush discipline would otherwise keep it garrisoning its compound,
                // or guarding its post, whatever it is ordered.
                squad.assignedNode = null;
                squad.holdsFireUntilKillZone = false;
                return true;
            }
        };
    }
}
