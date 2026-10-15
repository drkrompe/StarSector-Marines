package com.dillon.starsectormarines.battle.matchup;

import com.dillon.starsectormarines.battle.scene.SceneRun;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.CombatTelemetryService;

/** Plays one scenario for one pair of kits and one seed, headless, until a side is gone or the clock runs out. */
public final class MatchupPlay {

    /** Ticks without a point of damage, after the first, that end a fight as a stalemate (40 s of game time). */
    static final int STALEMATE_TICKS = 1200;

    private MatchupPlay() {}

    /** What one run came to. {@code winner} is ATTACKERS, DEFENDERS or BOTH_DESTROYED (a side wiped out), STALEMATE (both stand, no damage for
     * {@link #STALEMATE_TICKS}) or UNRESOLVED (the tick limit). */
    public record Result(String scenario, String attacker, String defender, long seed, String winner,
                         int ticks, int attackerStart, int defenderStart,
                         int attackerSurvivors, int defenderSurvivors,
                         float attackerDamageTaken, float defenderDamageTaken,
                         float attackerDamageMitigated, float defenderDamageMitigated,
                         int attackerRounds, int defenderRounds) {

        /** Defenders killed per attacker killed; the attackers' kills when they lost none. */
        public float exchange() {
            int lost = attackerStart - attackerSurvivors;
            int killed = defenderStart - defenderSurvivors;
            return lost == 0 ? killed : (float) killed / lost;
        }
    }

    public static Result play(MatchupScenario scenario, MatchupSide attacker, MatchupSide defender, long seed) {
        return play(scenario, attacker, defender, seed, null);
    }

    /** As above, additionally watched by {@code recording} (null for none). The recording only reads the sim. */
    public static Result play(MatchupScenario scenario, MatchupSide attacker, MatchupSide defender, long seed,
                              MatchupRecording recording) {
        SceneWorld world = scenario.build(attacker.seats(), defender.seats(), seed);
        long[] attackers = world.members(MatchupScenario.ATTACKERS);
        long[] defenders = world.members(MatchupScenario.DEFENDERS);
        long[] both = new long[attackers.length + defenders.length];
        System.arraycopy(attackers, 0, both, 0, attackers.length);
        System.arraycopy(defenders, 0, both, attackers.length, defenders.length);
        SceneRun run = SceneRun.of(world);
        BattleSimulation sim = world.sim();
        int ticks = 0;
        int attackersLeft = attackers.length;
        int defendersLeft = defenders.length;
        CombatTelemetryService telemetry = sim.telemetry();
        float damageSoFar = 0f;
        int lastDamageTick = -1;
        boolean stalemate = false;
        while (ticks < scenario.maxTicks() && attackersLeft > 0 && defendersLeft > 0) {
            if (recording != null) {
                recording.observe(sim, ticks, damageSoFar > 0f, status(sim, attackers, defenders), both);
            }
            run.step();
            ticks++;
            if (recording != null) recording.advanced(sim);
            attackersLeft = standing(sim, attackers);
            defendersLeft = standing(sim, defenders);
            float damage = taken(telemetry, attackers) + taken(telemetry, defenders);
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
        if (recording != null) recording.finish(sim, ticks, status(sim, attackers, defenders), both);
        String winner;
        if (attackersLeft == 0 && defendersLeft == 0) winner = "BOTH_DESTROYED";
        else if (defendersLeft == 0) winner = "ATTACKERS";
        else if (attackersLeft == 0) winner = "DEFENDERS";
        else winner = stalemate ? "STALEMATE" : "UNRESOLVED";
        return new Result(scenario.id(), attacker.label(), defender.label(), seed, winner, ticks,
                attackers.length, defenders.length, attackersLeft, defendersLeft,
                taken(telemetry, attackers), taken(telemetry, defenders),
                mitigated(telemetry, attackers), mitigated(telemetry, defenders),
                rounds(telemetry, attackers), rounds(telemetry, defenders));
    }

    private static String status(BattleSimulation sim, long[] attackers, long[] defenders) {
        return "attackers " + standing(sim, attackers) + "/" + attackers.length
                + "   defenders " + standing(sim, defenders) + "/" + defenders.length;
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
