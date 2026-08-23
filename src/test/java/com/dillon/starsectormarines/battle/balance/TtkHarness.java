package com.dillon.starsectormarines.battle.balance;

import com.dillon.starsectormarines.battle.combat.BallisticResolver;
import com.dillon.starsectormarines.battle.combat.FireStance;
import com.dillon.starsectormarines.battle.combat.FiringSystem;
import com.dillon.starsectormarines.battle.combat.ShotService;
import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.infantry.InfantryCombatStats;
import com.dillon.starsectormarines.battle.infantry.InfantryUnitPrep;
import com.dillon.starsectormarines.battle.infantry.InfantryWeapons;
import com.dillon.starsectormarines.battle.infantry.MarineWeapon;
import com.dillon.starsectormarines.battle.infantry.SoldierProfile;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.CombatService;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.DoodadService;
import com.dillon.starsectormarines.marine.MarineArmorPattern;

/**
 * Measures time-to-kill by running the shipped firing pipeline, not by
 * re-deriving it. This is the instrument that makes the quality laws in
 * {@code progression-nouns.md} measurable: every lethality or grade-spread
 * change is argued from a
 * before/after table this class produces, and the same table is re-runnable
 * when a later story perturbs the numbers again.
 *
 * <p><b>Why it drives the real code.</b> Effective lethality is not
 * {@code damage / cooldown}. A round's fate is decided by
 * {@link BallisticResolver} — target-plane aim, range falloff, lateral
 * spread, directional cover re-expressed as physical interception, and a
 * muzzle-proximity ramp on that interception — and its damage is then further
 * reduced by the cover curve and the armor package in the damage resolver. An
 * analytical model would quietly drop most of that and mis-tune by a wide
 * margin, so a trial here spawns two real units, sets a real fire intent, and
 * runs {@link FiringSystem} / {@link InfantryWeapons} / the pending-impact
 * drain on the real tick step until the defender dies.
 *
 * <p><b>What it deliberately excludes.</b> Only the firing sub-phases run. No
 * AI, movement, morale, or hit-response — the defender stands still and does
 * not shoot back. That is the point: a TTK figure should describe the weapon
 * and the armor, not a squad's decision quality. Both combatants are
 * stationary, so every shot is {@link FireStance#STANCED}; a moving shooter is
 * a flat half-accuracy case that needs no separate measurement.
 *
 * <p><b>Statistical, not deterministic.</b> {@code InfantryWeapons.fireShot}
 * samples {@code ThreadLocalRandom} (it runs inside the parallel unit
 * dispatch), so a trial cannot be seeded from here. Precision comes from trial
 * count instead, and every {@link Measurement} carries the standard error of
 * its own mean so a reader can tell a real regression from sampling noise.
 * Compare measurements against bands, never against exact values.
 */
public final class TtkHarness {

    /** Trials per scenario. ~4% standard error on a typical mean at this count; raise it for a close call. */
    public static final int DEFAULT_TRIALS = 300;
    /** A trial that has not resolved by here is recorded as unresolved rather than skewing the mean. */
    public static final float TRIAL_TIMEOUT_SECONDS = 120f;

    private static final int ARENA_HEIGHT = 9;
    private static final int FIRING_ROW = 4;
    private static final int SHOOTER_CELL_X = 2;
    /** Walkable cells kept past the defender so a missed round overshoots into open floor instead of a map edge. */
    private static final int ARENA_TAIL_CELLS = 8;

    private TtkHarness() {}

    /** Directional cover on the defender's cell, facing the shooter. */
    public enum Cover {
        OPEN("open", 0),
        LIGHT("light", 1),
        SOLID("solid", 2),
        HARD("hard", NavigationGrid.MAX_COVER);

        public final String label;
        public final int level;

        Cover(String label, int level) {
            this.label = label;
            this.level = level;
        }
    }

    /**
     * The thing being shot at. {@code armor} is optional — null means the
     * archetype's bare stat block, which is how militia, aliens, and swarm
     * runners actually spawn.
     */
    public record Defender(String label, UnitType type, MarineArmorPattern armor) {

        public static Defender bare(String label, UnitType type) {
            return new Defender(label, type, null);
        }

        public static Defender armored(String label, MarineArmorPattern armor) {
            return new Defender(label, UnitType.MARINE, armor);
        }
    }

    /** One measurable combination: who shoots what, with which kit, from how far, into how much cover. */
    public record Scenario(MarineWeapon weapon, EquipmentGrade grade, SoldierProfile profile,
                           Defender defender, float rangeFraction, Cover cover) {}

    /**
     * One scenario's measured outcome. {@code meanTtkSeconds} averages only
     * the trials that resolved; {@code kills} against {@code trials} is what
     * says whether that mean describes the scenario or just its lucky tail.
     */
    public record Measurement(Scenario scenario, int trials, int kills,
                              float meanTtkSeconds, float standardErrorSeconds,
                              float meanRoundsFired, float landedFraction,
                              float effectiveHp, int distanceCells) {

        /** True when every trial produced a kill inside the timeout — the precondition for reading the mean at face value. */
        public boolean fullyResolved() {
            return kills == trials;
        }
    }

    public static Measurement measure(Scenario scenario) {
        return measure(scenario, DEFAULT_TRIALS);
    }

    public static Measurement measure(Scenario scenario, int trials) {
        int distance = Math.max(1, Math.round(
                InfantryCombatStats.range(scenario.weapon(), scenario.grade()) * scenario.rangeFraction()));
        int targetCellX = SHOOTER_CELL_X + distance;
        int width = targetCellX + ARENA_TAIL_CELLS;

        NavigationGrid grid = new NavigationGrid(width, ARENA_HEIGHT);
        for (int y = 0; y < ARENA_HEIGHT; y++) {
            for (int x = 0; x < width; x++) grid.setWalkableFloor(x, y);
        }
        BattleSimulation sim = new BattleSimulation(grid, new CellTopology(width, ARENA_HEIGHT));
        // Cover is set after the floor fill so a walkability bake cannot wipe
        // it. Only the shooter-facing side is raised: the damage resolver's
        // reduction curve reads the SUM across facings, so covering all four
        // would silently double-count into a higher reduction tier.
        if (scenario.cover().level > 0) {
            grid.setCoverAtFacing(targetCellX, FIRING_ROW, NavigationGrid.FACING_W, scenario.cover().level);
        }

        UnitRosterService roster = sim.getRoster();
        World world = roster.world();
        CombatService combat = roster.combat();
        ShotService shots = sim.getShots();
        UnitSpatialIndex unitIndex = sim.getUnitIndex();
        // Own resolver / weapons instances over the sim's roster and shot
        // service: burst continuation reads its state from the roster, so a
        // parallel instance behaves identically to the sim's own and saves
        // exposing the sim's internals. Doodads are empty here by design —
        // this measures cover, not scenery.
        BallisticResolver resolver = new BallisticResolver(
                grid, new DoodadService(grid), unitIndex, roster);
        // Shares the sim's seeded stream, so a TTK measurement is reproducible run
        // to run rather than an average over whatever the global source handed out.
        InfantryWeapons weapons = new InfantryWeapons(roster, resolver, shots, sim.random());
        FiringSystem firing = new FiringSystem(grid, roster);

        long shooter = sim.spawn(new EntitySpec("shooter", Faction.MARINE, UnitType.MARINE,
                SHOOTER_CELL_X, FIRING_ROW)
                .primaryWeapon(scenario.weapon(), scenario.grade(), scenario.profile()));

        int kills = 0;
        double ttkSum = 0;
        double ttkSumSq = 0;
        long roundsFiredTotal = 0;
        long roundsLandedTotal = 0;
        float effectiveHp = 0f;

        for (int trial = 0; trial < trials; trial++) {
            long target = sim.spawn(defenderSpec(scenario.defender(), trial, targetCellX));
            if (trial == 0) {
                effectiveHp = world.maxHp(target)
                        / Math.max(0.0001f, world.damageTakenMult(target));
            }
            // A previous trial's kill leaves the shooter mid-burst against a
            // corpse and mid-cooldown; both are cleared so every trial starts
            // from the same trigger state.
            combat.setCooldownTimer(shooter, 0f);
            combat.setBurstRemaining(shooter, 0);
            combat.setBurstTargetId(shooter, 0L);

            RoundTally tally = new RoundTally();
            float elapsed = 0f;
            while (elapsed < TRIAL_TIMEOUT_SECONDS && roster.isAliveById(target)) {
                // Rebuilt every tick because the sim's own rebuild lives in
                // BattleSimulation.tick(), which this harness deliberately
                // does not run — without it the resolver gathers no contacts
                // and every round overshoots.
                unitIndex.rebuild(roster);
                combat.setFireIntent(shooter, target, FireStance.STANCED, false);
                firing.tick(sim);
                weapons.tick();
                InfantryUnitPrep.tickCooldowns(shooter, world);
                tally.roundsFired += shots.getShotsThisFrame().size();
                shots.tickImpacts(BattleSimulation.TICK_DT, impact -> {
                    if (!roster.isAliveById(impact.victimId)) return;
                    tally.roundsLanded++;
                    // moraleImpact 0: this is a lethality measurement, and a
                    // squadless defender has no morale state to drain anyway.
                    sim.applyDamage(impact.victimId, impact.damage, impact.vsTurretMult, 0f);
                });
                // The sim clears these in advance(); this harness never calls
                // it, so they are drained here to keep a long run flat.
                shots.getShotsThisFrame().clear();
                shots.getActiveShots().clear();
                elapsed += BattleSimulation.TICK_DT;
            }

            roundsFiredTotal += tally.roundsFired;
            roundsLandedTotal += tally.roundsLanded;
            if (!roster.isAliveById(target)) {
                kills++;
                ttkSum += elapsed;
                ttkSumSq += (double) elapsed * elapsed;
            }
        }

        float mean = kills > 0 ? (float) (ttkSum / kills) : Float.NaN;
        float standardError = Float.NaN;
        if (kills > 1) {
            double variance = Math.max(0d, (ttkSumSq - ttkSum * ttkSum / kills) / (kills - 1));
            standardError = (float) (Math.sqrt(variance) / Math.sqrt(kills));
        }
        return new Measurement(scenario, trials, kills, mean, standardError,
                trials > 0 ? (float) roundsFiredTotal / trials : 0f,
                roundsFiredTotal > 0 ? (float) roundsLandedTotal / roundsFiredTotal : 0f,
                effectiveHp, distance);
    }

    private static EntitySpec defenderSpec(Defender defender, int trial, int cellX) {
        EntitySpec spec = new EntitySpec("target-" + trial, Faction.DEFENDER,
                defender.type(), cellX, FIRING_ROW);
        MarineArmorPattern armor = defender.armor();
        if (armor != null) {
            spec.armor(armor.bonusHp, armor.damageReduction, armor.moveSpeedMult,
                    armor.incomingAccuracyMult);
        }
        return spec;
    }

    /** Per-trial counters; a class rather than locals so the impact sink lambda can write to them. */
    private static final class RoundTally {
        int roundsFired;
        int roundsLanded;
    }
}
