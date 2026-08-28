package com.dillon.starsectormarines.battle.appearance;

import com.dillon.starsectormarines.battle.combat.MitigationService;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.marine.IntegralSystemDef;
import com.dillon.starsectormarines.marine.MarineArmorCatalogDef;
import com.dillon.starsectormarines.marine.MarineArmorCatalogRegistry;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What a running integral system looks like, recorded so the claims the
 * treatment makes can be checked by eye.
 *
 * <p>The treatment is the wearer's own head and body drawn once more behind
 * them, larger and shifted the way the screen faces, in a shimmering blue. The
 * first claim it makes is that <b>the halo is the arc</b>: two authored patterns
 * stand side by side facing the same way — the foundry-breaker's 90-degree
 * screen and the Bulwark's 200-degree one — beside a marine wearing a suit that
 * carries no system at all. A narrow screen must read as a crescent across the
 * front and a wide one as a rim wrapping most of the way round; if they looked
 * alike, the treatment would be hiding the property that makes flanking worth
 * doing.
 *
 * <p>The second is that <b>a spent pool shows</b>. Real attributed fire arrives
 * from inside each screen's arc and is absorbed through the production damage
 * path, so the halo is seen fading as the pool goes, and then simply not being
 * there when the window closes.
 *
 * <p>The third artifact is the one the mechanical change exists for: a screen
 * <b>breaking</b>. One carrier takes concentrated fire that spends its whole
 * pool well inside its window, and the shatter — the same silhouette thrown
 * outward and gone — plays while the marine carries on taking the rest of the
 * fire unscreened.
 *
 * <p>The still artifact answers the remaining question the animations cannot: a
 * screen points where its wearer is dealing with something, so four identical
 * carriers facing four ways must draw four differently-oriented halos.
 *
 * <p>Activation and fire are scripted on fixed ticks rather than through the
 * authored use policy, so the recording is the same every run. Everything
 * downstream of that is production code: the real screen grant, the real arc,
 * the real pool, the real damage path spending it, and the real presentation
 * system authoring the appearance the renderer reads.
 */
public final class IntegralSystemFxSnapshotSuite implements SnapshotSuite {

    private static final long SEED = 20260828L;
    // Deliberately a small arena drawn large. The review camera fits the whole
    // grid to the frame, so a wide map is a small marine, and a halo nobody can
    // see in the evidence is evidence of nothing.
    private static final int WIDTH = 11;
    private static final int BREAK_WIDTH = 8;
    private static final int FACING_WIDTH = 12;
    private static final int FRAME_WIDTH = 880;
    private static final int FRAME_HEIGHT = 560;

    /** The narrow authored screen and the wide one — the comparison this suite exists for. */
    private static final String NARROW_PATTERN = "armor.foundry-breaker";
    private static final String WIDE_PATTERN = "armor.bulwark-heavy";
    /** A tier-4 suit that carries no system, so "with" and "without" are one frame apart. */
    private static final String PLAIN_PATTERN = "armor.palatine";

    private static final int NARROW_X = 1;
    private static final int WIDE_X = 5;
    private static final int PLAIN_X = 9;

    private static final int TICKS = 200;
    private static final int ACTIVATION_TICK = 20;
    private static final int FRAME_EVERY_TICKS = 3;
    private static final int FRAME_DELAY_MILLIS = 90;

    /** The window recording bleeds each pool down in even steps, so the fade is watchable rather than instant. */
    private static final int DRAIN_FIRST_TICK = 26;
    private static final int DRAIN_EVERY_TICKS = 5;
    private static final int DRAIN_STEPS = 12;
    /** Left in the pool when the drain finishes, so the window is also seen closing on its own. */
    private static final float DRAIN_RESIDUE = 0.12f;

    /** The break recording lands its concentrated fire in a handful of heavy hits. */
    private static final int BREAK_TICKS = 150;
    private static final int BREAK_ACTIVATION_TICK = 15;
    private static final int BREAK_FIRST_TICK = 30;
    private static final int BREAK_EVERY_TICKS = 6;
    private static final int BREAK_HITS = 6;
    /** Six of these spend a screen whose pool is well short of them, and then keep landing. */
    private static final float BREAK_HIT_FRACTION = 0.28f;

    private static final float PENETRATION = 6f;

    /** North, in the facing convention {@code AirBody.facingToward} produces. */
    private static final float NORTH = 0f;

    /** Stand-ins holding still: this records a treatment, not a fight. */
    private static final float STAND_IN_HP = 1_000_000f;

    @Override public String id() { return "integral-system-fx"; }

    @Override public String label() {
        return "Integral system: the halo a running screen wears, its pool draining, and one breaking";
    }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        BattleReviewFrameRenderer renderer =
                new BattleReviewFrameRenderer(context.modRoot(), FRAME_WIDTH, FRAME_HEIGHT);
        return List.of(recordWindow(renderer), recordBreak(renderer), recordFacings(renderer));
    }

    private SnapshotArtifact recordWindow(BattleReviewFrameRenderer renderer) throws Exception {
        BattleSimulation sim = openArena(WIDTH);
        int row = rowFor(WIDTH);
        long narrow = carrier(sim, "foundry-breaker", NARROW_PATTERN, NARROW_X, row);
        long wide = carrier(sim, "bulwark", WIDE_PATTERN, WIDE_X, row);
        long plain = carrier(sim, "palatine", PLAIN_PATTERN, PLAIN_X, row);
        // Standing inside both screens' arcs, so what they fire is what the
        // pools actually absorb rather than a scripted decrement.
        long atNarrow = muzzle(sim, "muzzle-narrow", NARROW_X, row + 2);
        long atWide = muzzle(sim, "muzzle-wide", WIDE_X, row + 2);
        MitigationService screens = sim.getRoster().mitigations();

        List<BufferedImage> frames = new ArrayList<>(TICKS / FRAME_EVERY_TICKS + 1);
        float narrowStep = 0f;
        float wideStep = 0f;
        for (int tick = 0; tick <= TICKS; tick++) {
            // Held explicitly, because these three are standing still and have
            // nothing to point at: the aiming rule needs a heading or a target,
            // and this recording is about the arc rather than about the aim.
            face(sim, narrow, NORTH);
            face(sim, wide, NORTH);
            if (tick == ACTIVATION_TICK) {
                sim.getRoster().integralSystems().activate(narrow);
                sim.getRoster().integralSystems().activate(wide);
                narrowStep = drainStep(screens.soakCapacity(narrow));
                wideStep = drainStep(screens.soakCapacity(wide));
            }
            if (isDrainTick(tick)) {
                sim.applyDamage(narrow, atNarrow, narrowStep, PENETRATION, 0f);
                sim.applyDamage(wide, atWide, wideStep, PENETRATION, 0f);
            }
            if (tick % FRAME_EVERY_TICKS == 0) {
                frames.add(renderer.render(sim, windowCaption(sim, narrow, wide, plain, tick)));
            }
            sim.advance(BattleSimulation.TICK_DT);
        }
        return SnapshotArtifact.animation("screen-arc-and-pool.gif", frames, FRAME_DELAY_MILLIS);
    }

    /**
     * The point of the mechanical change, recorded: massed fire beats a screen
     * instead of waiting it out. The window is long, the pool is not, and the
     * marine is still standing there when it goes.
     */
    private SnapshotArtifact recordBreak(BattleReviewFrameRenderer renderer) throws Exception {
        BattleSimulation sim = openArena(BREAK_WIDTH);
        int row = rowFor(BREAK_WIDTH);
        int column = BREAK_WIDTH / 2;
        long breacher = carrier(sim, "bulwark", WIDE_PATTERN, column, row);
        // Three muzzles across the covered front: the fire is concentrated, and
        // every round of it arrives inside the arc the screen is holding.
        long[] muzzles = {
                muzzle(sim, "muzzle-left", column - 2, row + 2),
                muzzle(sim, "muzzle-centre", column, row + 2),
                muzzle(sim, "muzzle-right", column + 2, row + 2),
        };
        MitigationService screens = sim.getRoster().mitigations();

        List<BufferedImage> frames = new ArrayList<>(BREAK_TICKS / FRAME_EVERY_TICKS + 1);
        float hit = 0f;
        for (int tick = 0; tick <= BREAK_TICKS; tick++) {
            face(sim, breacher, NORTH);
            if (tick == BREAK_ACTIVATION_TICK) {
                sim.getRoster().integralSystems().activate(breacher);
                hit = screens.soakCapacity(breacher) * BREAK_HIT_FRACTION;
            }
            int since = tick - BREAK_FIRST_TICK;
            if (since >= 0 && since % BREAK_EVERY_TICKS == 0
                    && since / BREAK_EVERY_TICKS < BREAK_HITS) {
                sim.applyDamage(breacher, muzzles[(since / BREAK_EVERY_TICKS) % muzzles.length],
                        hit, PENETRATION, 0f);
            }
            if (tick % FRAME_EVERY_TICKS == 0) {
                frames.add(renderer.render(sim, breakCaption(sim, breacher, tick)));
            }
            sim.advance(BattleSimulation.TICK_DT);
        }
        return SnapshotArtifact.animation("screen-breaks-under-fire.gif", frames,
                FRAME_DELAY_MILLIS);
    }

    private SnapshotArtifact recordFacings(BattleReviewFrameRenderer renderer) throws Exception {
        BattleSimulation sim = openArena(FACING_WIDTH);
        float[] facings = {0f, -90f, 180f, 90f};
        String[] names = {"north", "east", "south", "west"};
        long[] carriers = new long[facings.length];
        for (int i = 0; i < facings.length; i++) {
            // The narrow pattern, deliberately: a 90-degree crescent points
            // somewhere unmistakably, while a 200-degree rim covers so much of
            // the silhouette that four facings of it look nearly alike.
            carriers[i] = carrier(sim, names[i], NARROW_PATTERN, 1 + i * 3,
                    rowFor(FACING_WIDTH));
        }
        for (int tick = 0; tick <= 3; tick++) {
            for (int i = 0; i < carriers.length; i++) face(sim, carriers[i], facings[i]);
            if (tick == 1) {
                for (long carrier : carriers) sim.getRoster().integralSystems().activate(carrier);
            }
            sim.advance(BattleSimulation.TICK_DT);
        }
        for (int i = 0; i < carriers.length; i++) face(sim, carriers[i], facings[i]);
        BufferedImage frame = renderer.render(sim,
                "One 90-degree screen, four facings: north, east, south, west — the halo sits on"
                        + " the side the screen covers and nowhere else");
        return new SnapshotArtifact("screen-faces-its-arc.png", frame);
    }

    /** How much post-cover damage one drain step should hand a pool of this size. */
    private static float drainStep(float capacity) {
        return capacity * (1f - DRAIN_RESIDUE) / DRAIN_STEPS;
    }

    private static boolean isDrainTick(int tick) {
        int since = tick - DRAIN_FIRST_TICK;
        return since >= 0 && since % DRAIN_EVERY_TICKS == 0
                && since / DRAIN_EVERY_TICKS < DRAIN_STEPS;
    }

    private static void face(BattleSimulation sim, long unit, float degrees) {
        sim.getRoster().mitigations().face(unit, degrees);
    }

    private static long carrier(BattleSimulation sim, String name, String armorId, int x, int y) {
        MarineArmorCatalogDef pattern = MarineArmorCatalogRegistry.installed().get(armorId);
        if (pattern == null) {
            throw new IllegalStateException("fixture assumption: " + armorId + " is catalogued");
        }
        IntegralSystemDef system = pattern.integralSystem();
        EntitySpec spec = new EntitySpec(name, Faction.MARINE, UnitType.MARINE, x, y)
                .role(UnitRole.STRUCTURE)
                .layeredArmorFamily(pattern.appearanceFamily())
                .armor(pattern.armorCapacity(), pattern.armorRating(),
                        pattern.moveSpeedMult(), pattern.incomingAccuracyMult())
                .health(STAND_IN_HP);
        if (system != null) spec.integralSystem(system);
        return sim.spawn(spec);
    }

    /**
     * Something for the fire to come <em>from</em>. The arc is resolved against
     * the bearing to the hit's source, so a scripted decrement would prove
     * nothing about the arc; a body standing in the covered front makes the
     * absorbed damage the same damage a real attacker's would be.
     */
    private static long muzzle(BattleSimulation sim, String name, int x, int y) {
        return sim.spawn(new EntitySpec(name, Faction.DEFENDER, UnitType.MARINE, x, y)
                .role(UnitRole.STRUCTURE)
                .health(STAND_IN_HP));
    }

    /**
     * Grid height matched to the frame's aspect, so the fitted camera fills it.
     * A mismatched arena leaves blank bands above and below the map, which read
     * as a broken image rather than as an empty field.
     */
    private static int heightFor(int width) {
        return Math.max(4, Math.round(width
                * (FRAME_HEIGHT - BattleReviewFrameRenderer.HEADER_HEIGHT) / (float) FRAME_WIDTH));
    }

    /** The row every carrier stands on: the middle of whatever arena it is. */
    private static int rowFor(int width) {
        return heightFor(width) / 2;
    }

    private static BattleSimulation openArena(int width) {
        int height = heightFor(width);
        NavigationGrid grid = new NavigationGrid(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) grid.setWalkableFloor(x, y);
        }
        BattleSimulation sim = new BattleSimulation(grid, new CellTopology(width, height), SEED);
        sim.setMissionCompletionEnabled(false);
        return sim;
    }

    /**
     * States the numbers a viewer is being asked to compare against the picture:
     * how wide each screen is and how much of each pool is left. The caption is
     * a check on the drawing rather than a substitute for it — if the wide
     * carrier's halo reads narrower than the number says, that is the bug this
     * artifact exists to catch.
     */
    private static String windowCaption(BattleSimulation sim, long narrow, long wide,
                                        long plain, int tick) {
        SystemFxService fx = sim.getRoster().systemFx();
        return String.format(Locale.ROOT,
                "t%-3d  Foundry-breaker %s  |  Bulwark %s  |  Palatine: %s",
                tick, state(fx, narrow), state(fx, wide),
                fx.has(plain) ? "carries a system" : "no integral system");
    }

    private static String breakCaption(BattleSimulation sim, long breacher, int tick) {
        SystemFxService fx = sim.getRoster().systemFx();
        String shatter = fx.breakFlash(breacher) > 0f
                ? String.format(Locale.ROOT, "  |  SHATTERED (%d%%)",
                        Math.round(fx.breakFlash(breacher) * 100f))
                : "";
        return String.format(Locale.ROOT,
                "t%-3d  Bulwark under concentrated fire: %s%s",
                tick, state(fx, breacher), shatter);
    }

    private static String state(SystemFxService fx, long unit) {
        if (!fx.isRunning(unit)) return "idle";
        if (fx.arcDegrees(unit) <= 0f) {
            return String.format(Locale.ROOT, "no screen, %d%% of window left",
                    Math.round(fx.intensity(unit) * 100f));
        }
        return String.format(Locale.ROOT, "%.0f° screen, %d%% of pool, %d%% of window",
                fx.arcDegrees(unit), Math.round(fx.soakFraction(unit) * 100f),
                Math.round(fx.intensity(unit) * 100f));
    }
}
