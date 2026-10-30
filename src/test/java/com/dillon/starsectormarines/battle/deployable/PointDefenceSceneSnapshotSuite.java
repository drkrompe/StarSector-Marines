package com.dillon.starsectormarines.battle.deployable;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.ReviewImpactFx;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import org.json.JSONObject;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Animated review of a point-defence emplacement under artillery: a mech
 * walking long-range missile salvos onto a stationary marine section, first
 * with nothing to answer them and then with a pod on the ground.
 *
 * <p>This suite exists because the mechanic is not verifiable from a still
 * frame or from a counter. A stopped warhead is removed from the air, which
 * looks identical to a missile that was never fired unless the engagement is
 * drawn; and the whole design rests on the mount being saturable, which is a
 * claim about a <em>volley</em> — three rounds dying and two arriving in the
 * same second. Watching that happen is the only honest check that the bound is
 * real rather than asserted.
 *
 * <p>The salvos are fired on a fixed schedule and the pod is placed at a fixed
 * tick, so the recording is the same battle every run. Everything downstream of
 * the trigger is production code: real LRM rounds with real scatter and real
 * flight, the real engagement roll against the mount's authored accuracy, and
 * the real detonations for the ones that get through.
 */
public final class PointDefenceSceneSnapshotSuite implements SnapshotSuite {

    private static final long SEED = 20260828L;
    private static final int WIDTH = 44;
    private static final int HEIGHT = 26;
    private static final int FRAME_WIDTH = 620;
    private static final int FRAME_HEIGHT = 400;

    private static final int TICKS = 900;
    /** One frame every fifth of a second — an engagement mark lives ~0.9s, so a kill or a miss lands in several consecutive frames. */
    private static final int FRAME_EVERY_TICKS = 6;
    private static final int FRAME_DELAY_MILLIS = 110;

    private static final int MECH_CELL_X = 6;
    private static final int SECTION_CELL_X = 32;
    private static final int LANE_CELL_Y = 13;

    /** Rounds per salvo — deliberately larger than the mount can answer inside one salvo's arrival window. */
    private static final int SALVO_ROUNDS = 5;
    private static final int SALVO_SPACING_TICKS = 9;
    private static final int[] SALVO_START_TICKS = {60, 300, 480, 660};
    /** The pod goes down after the first salvo has already hurt, so the recording shows the same attack with and without an answer. */
    private static final int PLACEMENT_TICK = 210;
    /**
     * Forward of the section rather than on top of it. A pod standing in the
     * impact area is inside the blast of the first round it fails to stop, and
     * a recording of it being destroyed on the second salvo shows less about
     * interception than one where it survives to empty its magazine. It is also
     * simply where the thing belongs: between the incoming and what it covers.
     */
    private static final int POD_CELL_X = SECTION_CELL_X - 7;
    /**
     * The section is deliberately unkillable. This recording is about what the
     * emplacement does to a salvo, and a real marine dies to the second LRM
     * round — which would end the recording before the pod is even down and
     * leave nothing for the later salvos to be aimed at. The rounds, the
     * scatter, the detonations, and the engagements are all real; only the
     * bodies are stand-ins holding the aim point still.
     */
    private static final float SECTION_STAND_IN_HP = 100_000f;

    /** The worn pack's loop is reviewed by eye, so it is drawn at the review size and nearly every tick. */
    private static final int REVIEW_WIDTH = 1920;
    private static final int REVIEW_HEIGHT = 1080;
    private static final int PACK_TICKS = 540;
    /**
     * Every other tick. An interceptor crosses its ten cells in about a fifth of a
     * second, so a frame every sixth tick shows a launch and an arrival and nothing
     * of the flight between them, which is the part this loop exists to show.
     */
    private static final int PACK_FRAME_EVERY_TICKS = 2;
    private static final int PACK_FRAME_DELAY_MILLIS = 66;
    /**
     * Framed on the ground between the section and the incoming rounds, close
     * enough that an interceptor -- a fifth of a cell long -- is a missile and not
     * a pixel, with the section at the right edge and the meeting points in view.
     */
    private static final float PACK_FOCUS_X = SECTION_CELL_X - 7f;
    private static final float PACK_FOCUS_CELLS_ACROSS = 26f;
    private static final int PACK_CARRIER_X = SECTION_CELL_X + 1;
    private static final int PACK_CARRIER_Y = LANE_CELL_Y + 2;
    /** Close enough that the pack on the carrier's back is drawn at a size its livery can be judged at. */
    private static final float CLOSE_UP_CELLS_ACROSS = 6f;
    /**
     * The first launch played tick by tick from just ahead of the carrier: the coast out of the tube,
     * the motor lighting, and the start of the run. Wide enough to keep the missile in frame until
     * it is well under way.
     */
    private static final float LAUNCH_VIEW_CELLS_ACROSS = 16f;
    private static final int LAUNCH_VIEW_TICKS = 16;
    private static final int LAUNCH_VIEW_FRAME_DELAY_MILLIS = 100;

    @Override public String id() { return "point-defence"; }

    @Override public String label() { return "Point defence: a salvo partly stopped, partly arriving"; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        BattleReviewFrameRenderer renderer =
                new BattleReviewFrameRenderer(context.modRoot(), FRAME_WIDTH, FRAME_HEIGHT);
        installTurretCatalog(context.modRoot());
        BattleReviewFrameRenderer review = new BattleReviewFrameRenderer(context.modRoot(),
                REVIEW_WIDTH, REVIEW_HEIGHT, PACK_FOCUS_X, LANE_CELL_Y + 0.5f, PACK_FOCUS_CELLS_ACROSS)
                .withImpactEffects();
        BattleReviewFrameRenderer closeUp = new BattleReviewFrameRenderer(context.modRoot(),
                REVIEW_WIDTH, REVIEW_HEIGHT, PACK_CARRIER_X + 0.5f, PACK_CARRIER_Y + 0.5f,
                CLOSE_UP_CELLS_ACROSS).withImpactEffects();
        List<SnapshotArtifact> artifacts = new ArrayList<>();
        artifacts.add(record(renderer));
        BattleReviewFrameRenderer launchView = new BattleReviewFrameRenderer(context.modRoot(),
                REVIEW_WIDTH, REVIEW_HEIGHT, PACK_CARRIER_X - 5f, PACK_CARRIER_Y + 2.5f,
                LAUNCH_VIEW_CELLS_ACROSS).withImpactEffects();
        artifacts.addAll(recordPack(review, closeUp, launchView, new ReviewImpactFx(context.modRoot())));
        return artifacts;
    }

    /**
     * The shared headless bootstrap installs weapons and special equipment but
     * not the emplacement catalogs, which nothing else in the snapshot set
     * needed. A placed pod resolves its structure through them, so this suite
     * installs them itself rather than widening a bootstrap every other suite
     * would then pay for. Runs after the renderer, which installs the weapon
     * registry the turret catalog resolves against.
     */
    private static void installTurretCatalog(Path modRoot) throws Exception {
        if (TurretCatalogRegistry.installed() != null) return;
        TurretCatalogRegistry turrets = new TurretCatalogRegistry();
        for (String path : TurretCatalogRegistry.BUILTIN_CATALOGS) {
            turrets.ingest(new JSONObject(Files.readString(
                    modRoot.toAbsolutePath().normalize().resolve(path))),
                    WeaponRegistry.installed());
        }
        TurretCatalogRegistry.install(turrets);
    }

    private SnapshotArtifact record(BattleReviewFrameRenderer renderer) throws Exception {
        BattleSimulation sim = openArena();
        WeaponDef lrm = WeaponRegistry.require("weapon.mech-lrm");
        SpecialEquipmentDef pod = SpecialEquipmentRegistry.require(
                SpecialEquipmentRegistry.POINT_DEFENCE_EMPLACEMENT_ID);

        long mech = sim.spawn(new EntitySpec("lrm-mech", Faction.DEFENDER,
                UnitType.HEAVY_MECH, MECH_CELL_X, LANE_CELL_Y).role(UnitRole.STRUCTURE));
        List<Long> section = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            section.add(sim.spawn(new EntitySpec("rifleman-" + i, Faction.MARINE,
                    UnitType.MARINE, SECTION_CELL_X + (i % 3), LANE_CELL_Y - 1 + i / 3)
                    .role(UnitRole.STRUCTURE)
                    .health(SECTION_STAND_IN_HP)));
        }
        long carrier = sim.spawn(new EntitySpec("pd-carrier", Faction.MARINE,
                UnitType.MARINE, SECTION_CELL_X + 1, LANE_CELL_Y + 2)
                .role(UnitRole.STRUCTURE)
                .specialEquipment(pod, pod.startingAmmo()));

        List<BufferedImage> frames = new ArrayList<>(TICKS / FRAME_EVERY_TICKS + 1);
        for (int tick = 0; tick <= TICKS; tick++) {
            if (tick == PLACEMENT_TICK) {
                sim.pointDefense().queuePlacement(carrier, Faction.MARINE,
                        POD_CELL_X, LANE_CELL_Y, pod.deployableEmplacementSpec());
            }
            fireScheduledSalvoRound(sim, tick, mech, section, lrm);
            if (tick % FRAME_EVERY_TICKS == 0) {
                frames.add(renderer.render(sim, caption(sim, carrier, tick)));
            }
            sim.advance(BattleSimulation.TICK_DT);
        }
        return SnapshotArtifact.animation("salvo-under-point-defence.gif",
                frames, FRAME_DELAY_MILLIS);
    }

    /**
     * The same salvos into the same section, answered this time by the worn
     * interceptor pack on one marine's back: small missiles going up out of the
     * pack and turning onto the rounds. Switched on at tick zero, so every salvo
     * meets it; the magazine of six runs dry partway through, which is part of
     * what the loop shows.
     */
    private List<SnapshotArtifact> recordPack(BattleReviewFrameRenderer renderer,
                                              BattleReviewFrameRenderer closeUp,
                                              BattleReviewFrameRenderer launchView,
                                              ReviewImpactFx impacts) throws Exception {
        BattleSimulation sim = openArena();
        WeaponDef lrm = WeaponRegistry.require("weapon.mech-lrm");
        SpecialEquipmentDef pack = SpecialEquipmentRegistry.require(
                SpecialEquipmentRegistry.POINT_DEFENCE_PACK_ID);

        long mech = sim.spawn(new EntitySpec("lrm-mech", Faction.DEFENDER,
                UnitType.HEAVY_MECH, MECH_CELL_X, LANE_CELL_Y).role(UnitRole.STRUCTURE));
        List<Long> section = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            section.add(sim.spawn(new EntitySpec("rifleman-" + i, Faction.MARINE,
                    UnitType.MARINE, SECTION_CELL_X + (i % 3), LANE_CELL_Y - 1 + i / 3)
                    .role(UnitRole.STRUCTURE)
                    .health(SECTION_STAND_IN_HP)));
        }
        long carrier = sim.spawn(new EntitySpec("pd-carrier", Faction.MARINE,
                UnitType.MARINE, PACK_CARRIER_X, PACK_CARRIER_Y)
                .role(UnitRole.STRUCTURE)
                .health(SECTION_STAND_IN_HP)
                .specialEquipment(pack, pack.startingAmmo()));
        sim.pointDefense().queuePlacement(carrier, Faction.MARINE,
                PACK_CARRIER_X, PACK_CARRIER_Y, pack.deployableEmplacementSpec());

        List<BufferedImage> frames = new ArrayList<>(PACK_TICKS / PACK_FRAME_EVERY_TICKS + 1);
        List<SnapshotArtifact> stills = new ArrayList<>();
        int launchedBefore = 0;
        int firstLaunchTick = -1;
        List<BufferedImage> launchFrames = new ArrayList<>(LAUNCH_VIEW_TICKS);
        for (int tick = 0; tick <= PACK_TICKS; tick++) {
            fireScheduledSalvoRound(sim, tick, mech, section, lrm);
            if (tick == 1) {
                stills.add(new SnapshotArtifact("interceptor-pack-worn.png",
                        closeUp.render(sim, "Interceptor pack on its carrier", ReviewAnnotations.NONE, impacts)));
            }
            int launched = sim.telemetry().ordnanceEngaged(carrier);
            if (launchedBefore == 0 && launched > 0) firstLaunchTick = tick;
            if (firstLaunchTick >= 0 && tick - firstLaunchTick < LAUNCH_VIEW_TICKS) {
                launchFrames.add(launchView.render(sim, String.format(Locale.ROOT,
                        "First interceptor, tick by tick  •  %.2fs after launch",
                        (tick - firstLaunchTick) * BattleSimulation.TICK_DT), ReviewAnnotations.NONE, impacts));
            }
            if (launchedBefore == 0 && launched > 0) {
                stills.add(new SnapshotArtifact("interceptor-pack-launch.png",
                        closeUp.render(sim, "First interceptor leaving the pack  •  t" + tick,
                                ReviewAnnotations.NONE, impacts)));
            }
            launchedBefore = launched;
            if (tick % PACK_FRAME_EVERY_TICKS == 0) {
                frames.add(renderer.render(sim, packCaption(sim, carrier, tick),
                        ReviewAnnotations.NONE, impacts));
            }
            sim.advance(BattleSimulation.TICK_DT);
            impacts.feed(sim);
        }
        stills.add(SnapshotArtifact.animation("interceptor-pack-launch.gif",
                launchFrames, LAUNCH_VIEW_FRAME_DELAY_MILLIS));
        stills.add(SnapshotArtifact.animation("salvo-under-interceptor-pack.gif",
                frames, PACK_FRAME_DELAY_MILLIS));
        return stills;
    }

    private static String packCaption(BattleSimulation sim, long carrier, int tick) {
        String state = sim.pointDefense().activeEmplacements().isEmpty() ? "pack dry" : "pack live";
        return String.format(Locale.ROOT,
                "LRM salvos on a section wearing an interceptor pack  •  t%-4d  •  %s  •  launched %d, stopped %d",
                tick, state, sim.telemetry().ordnanceEngaged(carrier),
                sim.telemetry().ordnanceIntercepted(carrier));
    }

    /** Walks one round of the current salvo downrange, at the authored burst spacing. */
    private static void fireScheduledSalvoRound(BattleSimulation sim, int tick, long mech,
                                                List<Long> section, WeaponDef lrm) {
        for (int start : SALVO_START_TICKS) {
            int offset = tick - start;
            if (offset < 0 || offset % SALVO_SPACING_TICKS != 0) continue;
            int round = offset / SALVO_SPACING_TICKS;
            if (round >= SALVO_ROUNDS) continue;
            long victim = sim.resolveUnit(section.get(round % section.size()));
            if (victim == 0L) return;
            sim.fireMechWeapon(mech, victim, lrm);
            return;
        }
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
     * The two figures that make the bound reviewable — how many warheads the
     * mount fired at, and how many it actually stopped — plus how many rounds
     * are in the air right now, so a saturating salvo is visible as a number as
     * well as a picture.
     */
    private static String caption(BattleSimulation sim, long carrier, int tick) {
        int engaged = sim.telemetry().ordnanceEngaged(carrier);
        int stopped = sim.telemetry().ordnanceIntercepted(carrier);
        String state;
        if (!sim.pointDefense().activeEmplacements().isEmpty()) {
            state = "emplacement live";
        } else if (tick < PLACEMENT_TICK) {
            state = "no emplacement";
        } else {
            state = "emplacement down";
        }
        return String.format(Locale.ROOT,
                "LRM salvos on a marine section  •  t%-4d  •  %s  •  engaged %d, stopped %d  •  %d rounds in the air",
                tick, state, engaged, stopped, sim.getActiveProjectiles().size());
    }
}
