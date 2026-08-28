package com.dillon.starsectormarines.battle.deployable;

import com.dillon.starsectormarines.battle.combat.FireStance;
import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.marine.DeployableCoverSpec;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Animated review of deployable cover, built as a controlled experiment
 * because there is no other honest way to look at a probability.
 *
 * <p>Three groups take fire from identical range at an identical rate, and
 * revetments go up in front of two of them partway through:
 * <ul>
 *   <li>the <b>covered lane</b>, shot from the east, with screens on its east
 *       boundary;</li>
 *   <li>the <b>open lane</b>, shot from the east with nothing in front of it —
 *       the control that says what the fire would have done;</li>
 *   <li>the <b>flanked post</b>, which gets exactly the same screen on its east
 *       boundary and is then shot from the south instead.</li>
 * </ul>
 *
 * <p>The first two figures answer "does it work". The third answers the
 * question that actually decides whether this is cover or a durability bonus
 * in a costume: the flanked post has a screen and must keep taking full
 * damage anyway, because the screen is on a boundary the fire never crosses.
 * A recording where all three diverge together would be a bug report.
 *
 * <p>Everything downstream of the fixed firing schedule is production code:
 * real rounds through the real ballistic resolver, the real per-facing cover
 * lookup, and the real damage path.
 */
public final class DeployedCoverSceneSnapshotSuite implements SnapshotSuite {

    private static final long SEED = 20260828L;
    private static final int WIDTH = 40;
    private static final int HEIGHT = 30;
    private static final int FRAME_WIDTH = 640;
    private static final int FRAME_HEIGHT = 440;

    private static final int TICKS = 1_200;
    private static final int FRAME_EVERY_TICKS = 8;
    private static final int FRAME_DELAY_MILLIS = 100;

    /** Every shot in the scene travels this far, so no figure is a range artefact. */
    private static final int SHOOTING_RANGE_CELLS = 18;

    private static final int LANE_CELL_X = 10;
    private static final int LANE_LENGTH = 4;
    /** Two cells between neighbours: closer bodies overlap and get relaxed apart. */
    private static final int LANE_SPACING = 2;
    private static final int COVERED_LANE_Y = 16;
    private static final int OPEN_LANE_Y = 3;

    private static final int FLANKED_POST_X = 22;
    private static final int FLANKED_POST_Y = 25;

    private static final int FIRE_EVERY_TICKS = 4;
    /** Late enough that all three groups have taken comparable fire on bare ground first. */
    private static final int PLACEMENT_TICK = 480;

    /**
     * The bodies are deliberately unkillable practice targets rather than
     * marines. A marine dies to a handful of rifle rounds, which would end the
     * measurement before the screens are even up; and a marine who is shot
     * rolls a fall-back and walks off the boundary the screen was built on,
     * which would make this a recording of drift rather than of cover. The
     * rounds, the cover rolls, and the damage are all real; only the bodies
     * are stand-ins holding the aim points still.
     */
    private static final float STAND_IN_HP = 1_000_000f;

    @Override public String id() { return "deployable-cover"; }

    @Override public String label() {
        return "Deployable cover: one screen, the fire it faces and the fire it does not";
    }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        BattleReviewFrameRenderer renderer =
                new BattleReviewFrameRenderer(context.modRoot(), FRAME_WIDTH, FRAME_HEIGHT);
        return List.of(record(renderer));
    }

    private SnapshotArtifact record(BattleReviewFrameRenderer renderer) throws Exception {
        BattleSimulation sim = openArena();
        SpecialEquipmentDef revetment = SpecialEquipmentRegistry.require(
                SpecialEquipmentRegistry.FIELD_REVETMENT_ID);
        DeployableCoverSpec spec = revetment.deployableCoverSpec();

        List<Long> coveredLane = lane(sim, COVERED_LANE_Y, "covered");
        List<Long> openLane = lane(sim, OPEN_LANE_Y, "open");
        long flankedPost = body(sim, "flanked", FLANKED_POST_X, FLANKED_POST_Y);

        long coveredShooter = shooter(sim, "east-covered",
                LANE_CELL_X + SHOOTING_RANGE_CELLS, laneCentreY(COVERED_LANE_Y));
        long openShooter = shooter(sim, "east-open",
                LANE_CELL_X + SHOOTING_RANGE_CELLS, laneCentreY(OPEN_LANE_Y));
        long flanker = shooter(sim, "flanker",
                FLANKED_POST_X, FLANKED_POST_Y - SHOOTING_RANGE_CELLS);

        // The marine who carries the kit. Placements fire on a fixed tick
        // rather than through the carrier's opportunity gate, so the recording
        // is the same battle every run; everything the placement then does is
        // production code.
        long carrier = sim.spawn(new EntitySpec("revetment-carrier", Faction.MARINE,
                UnitType.MARINE, LANE_CELL_X - 2, laneCentreY(COVERED_LANE_Y))
                .role(UnitRole.STRUCTURE)
                .health(STAND_IN_HP)
                .specialEquipment(revetment, revetment.startingAmmo()));

        List<BufferedImage> frames = new ArrayList<>(TICKS / FRAME_EVERY_TICKS + 1);
        float[] baseline = null;
        for (int tick = 0; tick <= TICKS; tick++) {
            if (tick == PLACEMENT_TICK) {
                baseline = new float[] {
                        damageTaken(sim, coveredLane),
                        damageTaken(sim, openLane),
                        sim.telemetry().damageTaken(flankedPost)};
                for (int i = 0; i < LANE_LENGTH; i++) {
                    sim.deployedCover().queuePlacement(carrier, Faction.MARINE,
                            LANE_CELL_X, COVERED_LANE_Y + LANE_SPACING * i,
                            Direction.E, spec);
                }
                // The same screen, on the same boundary, in front of a post
                // that is about to be shot from somewhere else entirely.
                sim.deployedCover().queuePlacement(carrier, Faction.MARINE,
                        FLANKED_POST_X, FLANKED_POST_Y, Direction.E, spec);
            }
            fireScheduledRound(sim, tick, coveredShooter, openShooter, flanker,
                    coveredLane, openLane, flankedPost);
            if (tick % FRAME_EVERY_TICKS == 0) {
                frames.add(renderer.render(sim,
                        caption(sim, coveredLane, openLane, flankedPost, tick, baseline)));
            }
            sim.advance(BattleSimulation.TICK_DT);
        }
        return SnapshotArtifact.animation("fire-across-a-revetment.gif",
                frames, FRAME_DELAY_MILLIS);
    }

    private static int laneCentreY(int baseY) {
        return baseY + LANE_SPACING * (LANE_LENGTH - 1) / 2;
    }

    private static List<Long> lane(BattleSimulation sim, int baseY, String label) {
        List<Long> lane = new ArrayList<>(LANE_LENGTH);
        for (int i = 0; i < LANE_LENGTH; i++) {
            lane.add(body(sim, label + "-" + i, LANE_CELL_X, baseY + LANE_SPACING * i));
        }
        return lane;
    }

    private static long body(BattleSimulation sim, String name, int x, int y) {
        return sim.spawn(new EntitySpec(name, Faction.MARINE, UnitType.RANGE_TARGET, x, y)
                .role(UnitRole.STRUCTURE)
                .health(STAND_IN_HP));
    }

    private static long shooter(BattleSimulation sim, String name, int x, int y) {
        return sim.spawn(new EntitySpec(name, Faction.DEFENDER, UnitType.MARINE, x, y)
                .role(UnitRole.STRUCTURE)
                .health(STAND_IN_HP));
    }

    /**
     * Strict three-way rotation, one round at a time. Each group therefore
     * receives the same number of rounds over the same distance, which is what
     * makes the three figures on the caption directly comparable rather than
     * three unrelated numbers.
     */
    private static void fireScheduledRound(BattleSimulation sim, int tick,
                                           long coveredShooter, long openShooter,
                                           long flanker, List<Long> coveredLane,
                                           List<Long> openLane, long flankedPost) {
        if (tick % FIRE_EVERY_TICKS != 0) return;
        int slot = tick / FIRE_EVERY_TICKS;
        int rotation = slot % 3;
        long shooter;
        long victim;
        if (rotation == 0) {
            shooter = coveredShooter;
            victim = coveredLane.get((slot / 3) % coveredLane.size());
        } else if (rotation == 1) {
            shooter = openShooter;
            victim = openLane.get((slot / 3) % openLane.size());
        } else {
            shooter = flanker;
            victim = flankedPost;
        }
        victim = sim.resolveUnit(victim);
        if (victim == 0L) return;
        // The schedule is the authority on rate here, not the weapon cooldown:
        // the comparison only means anything if every group gets the same fire.
        sim.world().setCooldownTimer(shooter, 0f);
        sim.fireShot(shooter, victim, FireStance.STANCED);
    }

    private static BattleSimulation openArena() {
        NavigationGrid grid = new NavigationGrid(WIDTH, HEIGHT);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) grid.setWalkableFloor(x, y);
        }
        BattleSimulation sim = new BattleSimulation(grid, new CellTopology(WIDTH, HEIGHT), SEED);
        // A scene, not a mission: nobody wins, so the recording runs its full
        // length instead of stopping the moment one side is out of bodies.
        sim.setMissionCompletionEnabled(false);
        return sim;
    }

    /**
     * The three figures the scene exists to put side by side.
     *
     * <p>Once the screens are up the caption reports damage <em>taken since
     * they went up</em>, not since the battle began. That is the only figure
     * the comparison is entitled to: the three lanes do not arrive at the
     * placement tick carrying identical totals, because a hit roll is a roll,
     * so a running total from t0 mixes the effect being measured with whatever
     * spread the opening exchange happened to produce. Subtracting the reading
     * at placement makes every frame afterwards a controlled comparison that
     * can be read straight off the picture, which is what this artifact is
     * for.
     */
    private static String caption(BattleSimulation sim, List<Long> coveredLane,
                                  List<Long> openLane, long flankedPost, int tick,
                                  float[] baseline) {
        String state = !sim.deployedCover().activeScreens().isEmpty()
                ? sim.deployedCover().activeScreens().size() + " revetments up, all facing east"
                : (tick < PLACEMENT_TICK ? "no revetments yet" : "revetments expired");
        float covered = damageTaken(sim, coveredLane);
        float open = damageTaken(sim, openLane);
        float flanked = sim.telemetry().damageTaken(flankedPost);
        if (baseline != null) {
            covered -= baseline[0];
            open -= baseline[1];
            flanked -= baseline[2];
        }
        return String.format(Locale.ROOT,
                "t%-4d %s | damage %s: covered %.0f, open %.0f, flanked %.0f",
                tick, state, baseline == null ? "so far" : "since placement",
                covered, open, flanked);
    }

    private static float damageTaken(BattleSimulation sim, List<Long> lane) {
        float total = 0f;
        for (long body : lane) total += sim.telemetry().damageTaken(body);
        return total;
    }
}
