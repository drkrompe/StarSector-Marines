package com.dillon.starsectormarines.battle.matchup;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.deployable.FieldMortarService;
import com.dillon.starsectormarines.battle.scene.SceneRun;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.CombatTelemetryService;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.HashMap;
import java.util.Map;

/** Plays one scenario for one pair of kits and one seed, headless, until a side is gone or the clock runs out. */
public final class MatchupPlay {

    /** Ticks without a point of damage, after the first, that end a fight as a stalemate (40 s of game time). */
    static final int STALEMATE_TICKS = 1200;

    /** Cells a fire mission's ground reaches past the farthest defender, so a man who shifts in his trench stays on it. */
    static final int FIRE_MISSION_MARGIN_CELLS = 3;

    private MatchupPlay() {}

    /** What one run came to. {@code winner} is ATTACKERS, DEFENDERS or BOTH_DESTROYED (a side wiped out; the defenders'
     * side includes its turrets), STALEMATE (both stand, no damage for
     * {@link #STALEMATE_TICKS}) or UNRESOLVED (the tick limit). */
    public record Result(String scenario, String attacker, String defender, long seed, String winner,
                         int ticks, int attackerStart, int defenderStart,
                         int attackerSurvivors, int defenderSurvivors,
                         float attackerDamageTaken, float defenderDamageTaken,
                         float attackerDamageMitigated, float defenderDamageMitigated,
                         int attackerRounds, int defenderRounds, int turretStart, int turretsLeft,
                         int turretRounds, int attackerSpecialsUsed, int defenderSpecialsUsed,
                         Mortars attackerMortars, Mortars defenderMortars) {

        /** Defenders killed per attacker killed; the attackers' kills when they lost none. */
        public float exchange() {
            int lost = attackerStart - attackerSurvivors;
            int killed = defenderStart - defenderSurvivors;
            return lost == 0 ? killed : (float) killed / lost;
        }
    }

    /**
     * What one side's field mortars did: tubes set down, rounds fired and the damage and kills those rounds made.
     * A tube is its own entity, so its fire is not in the squad's figures.
     */
    public record Mortars(int setUps, int rounds, float damage, int kills) {
        static final Mortars NONE = new Mortars(0, 0, 0f, 0);
    }

    /** Running tally of every tube seen, by entity, read each tick because a packed tube leaves the service. */
    private static final class MortarWatch {
        private final Map<Long, float[]> tubes = new HashMap<>();
        private final Map<Long, Faction> factions = new HashMap<>();

        void observe(BattleSimulation sim, CombatTelemetryService telemetry) {
            FieldMortarService service = sim.fieldMortars();
            for (FieldMortarService.Tube tube : service.tubes()) {
                long entity = tube.entityId();
                // A tube still being set up has no entity yet; it counts once it stands.
                if (entity == 0L) continue;
                factions.put(entity, tube.faction);
                float[] t = tubes.computeIfAbsent(entity, k -> new float[3]);
                t[0] = Math.max(t[0], tube.roundsFired());
                if (telemetry.isRecorded(entity)) {
                    t[1] = Math.max(t[1], telemetry.damageDealt(entity));
                    t[2] = Math.max(t[2], telemetry.kills(entity));
                }
            }
        }

        Mortars of(Faction faction) {
            int setUps = 0, rounds = 0, kills = 0;
            float damage = 0f;
            for (Map.Entry<Long, float[]> e : tubes.entrySet()) {
                if (factions.get(e.getKey()) != faction) continue;
                setUps++;
                rounds += (int) e.getValue()[0];
                damage += e.getValue()[1];
                kills += (int) e.getValue()[2];
            }
            return new Mortars(setUps, rounds, damage, kills);
        }
    }

    public static Result play(MatchupScenario scenario, MatchupSide attacker, MatchupSide defender, long seed) {
        return play(scenario, attacker, defender, seed, false, null);
    }

    /**
     * As above, additionally watched by {@code recording} (null for none; it only reads the sim). With
     * {@code fireMission} the attackers' order carries a fire mission on the ground the defenders start on, the way a
     * commander tells a squad with crew-served tubes where the position is; without one a marching squad never halts
     * to set a tube down, and only fires one at what it stumbles on while stopped.
     */
    public static Result play(MatchupScenario scenario, MatchupSide attacker, MatchupSide defender, long seed,
                              boolean fireMission, MatchupRecording recording) {
        SceneWorld world = scenario.build(attacker.seats(), defender.seats(), seed);
        long[] attackers = world.members(MatchupScenario.ATTACKERS);
        long[] defenders = world.members(MatchupScenario.DEFENDERS);
        if (fireMission) assignFireMission(world, defenders);
        long[] turrets = world.structures();
        long[] defence = new long[defenders.length + turrets.length];
        System.arraycopy(defenders, 0, defence, 0, defenders.length);
        System.arraycopy(turrets, 0, defence, defenders.length, turrets.length);
        long[][] both = {attackers, defence};
        SceneRun run = SceneRun.of(world);
        BattleSimulation sim = world.sim();
        int ticks = 0;
        int attackersLeft = attackers.length;
        int defendersLeft = defenders.length;
        int turretsLeft = turrets.length;
        CombatTelemetryService telemetry = sim.telemetry();
        float damageSoFar = 0f;
        int lastDamageTick = -1;
        boolean stalemate = false;
        MortarWatch mortars = new MortarWatch();
        Faction attackerFaction = sim.identity().faction(attackers[0]);
        Faction defenderFaction = sim.identity().faction(defenders[0]);
        while (ticks < scenario.maxTicks() && attackersLeft > 0 && defendersLeft + turretsLeft > 0) {
            if (recording != null) {
                recording.observe(sim, ticks, damageSoFar > 0f, status(sim, attackers, defenders, turrets), both);
            }
            run.step();
            ticks++;
            if (recording != null) recording.advanced(sim);
            mortars.observe(sim, telemetry);
            attackersLeft = standing(sim, attackers);
            defendersLeft = standing(sim, defenders);
            turretsLeft = standing(sim, turrets);
            float damage = taken(telemetry, attackers) + taken(telemetry, defenders) + taken(telemetry, turrets);
            if (damage > damageSoFar) {
                damageSoFar = damage;
                lastDamageTick = ticks;
            } else if (lastDamageTick >= 0 && ticks - lastDamageTick >= STALEMATE_TICKS) {
                // Both sides still stand and nobody has hurt anybody for a long while: the fight is over in every
                // sense but the clock, and waiting out the limit would only make every such run cost the same.
                stalemate = true;
                break;
            }
        }
        if (recording != null) recording.finish(sim, ticks, status(sim, attackers, defenders, turrets), both);
        String winner;
        int defenceLeft = defendersLeft + turretsLeft;
        if (attackersLeft == 0 && defenceLeft == 0) winner = "BOTH_DESTROYED";
        else if (defenceLeft == 0) winner = "ATTACKERS";
        else if (attackersLeft == 0) winner = "DEFENDERS";
        else winner = stalemate ? "STALEMATE" : "UNRESOLVED";
        return new Result(scenario.id(), attacker.label(), defender.label(), seed, winner, ticks,
                attackers.length, defenders.length, attackersLeft, defendersLeft,
                taken(telemetry, attackers), taken(telemetry, defenders),
                mitigated(telemetry, attackers), mitigated(telemetry, defenders),
                rounds(telemetry, attackers), rounds(telemetry, defenders),
                turrets.length, turretsLeft, rounds(telemetry, turrets),
                specials(telemetry, attackers), specials(telemetry, defenders),
                mortars.of(attackerFaction), mortars.of(defenderFaction));
    }

    /** A fire mission over the defenders' starting ground: its centre, and a radius that takes all of them in. */
    private static void assignFireMission(SceneWorld world, long[] defenders) {
        BattleSimulation sim = world.sim();
        float cx = 0f, cy = 0f;
        for (long id : defenders) {
            cx += sim.world().x(id);
            cy += sim.world().y(id);
        }
        cx /= defenders.length;
        cy /= defenders.length;
        float reach = 0f;
        for (long id : defenders) {
            reach = Math.max(reach, (float) Math.hypot(sim.world().x(id) - cx, sim.world().y(id) - cy));
        }
        Squad squad = world.squad(MatchupScenario.ATTACKERS);
        squad.assignedObjective = squad.assignedObjective.withFireMission(new ObjectiveAssignment.FireMission(
                (int) Math.floor(cx), (int) Math.floor(cy), (int) Math.ceil(reach) + FIRE_MISSION_MARGIN_CELLS));
    }

    private static int specials(CombatTelemetryService telemetry, long[] ids) {
        int total = 0;
        for (long id : ids) if (telemetry.isRecorded(id)) total += telemetry.secondaryUsed(id);
        return total;
    }

    private static String status(BattleSimulation sim, long[] attackers, long[] defenders, long[] turrets) {
        return "attackers " + standing(sim, attackers) + "/" + attackers.length
                + "   defenders " + standing(sim, defenders) + "/" + defenders.length
                + (turrets.length == 0 ? "" : "   turrets " + standing(sim, turrets) + "/" + turrets.length);
    }

    private static int standing(BattleSimulation sim, long[] ids) {
        int live = 0;
        for (long id : ids) if (sim.resolveUnit(id) != 0L) live++;
        return live;
    }

    private static float taken(CombatTelemetryService telemetry, long[] ids) {
        float total = 0f;
        for (long id : ids) if (telemetry.isRecorded(id)) total += telemetry.damageTaken(id);
        return total;
    }

    private static float mitigated(CombatTelemetryService telemetry, long[] ids) {
        float total = 0f;
        for (long id : ids) if (telemetry.isRecorded(id)) total += telemetry.damageMitigated(id);
        return total;
    }

    private static int rounds(CombatTelemetryService telemetry, long[] ids) {
        int total = 0;
        for (long id : ids) if (telemetry.isRecorded(id)) total += telemetry.roundsFired(id);
        return total;
    }
}
