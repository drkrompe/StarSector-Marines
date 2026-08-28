package com.dillon.starsectormarines.battle.appearance;

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
 * What a running integral system looks like, recorded so the two claims the
 * treatment makes can actually be checked by eye.
 *
 * <p>The first claim is that <b>the drawn screen is the arc</b>. Two authored
 * patterns stand side by side facing the same way — the foundry-breaker's 90-degree
 * screen and the Bulwark's 200-degree one — beside a marine wearing a suit that
 * carries no system at all. If the two screens looked alike, or if the plain
 * marine were hard to pick out, the treatment would be hiding the property that
 * makes the capability interesting.
 *
 * <p>The second is that <b>the window closes</b>. The systems are spent on a
 * fixed tick and the recording runs past their authored durations, so the band
 * is seen thinning and then simply not being there. The foundry-breaker's two and a half seconds
 * end before the Bulwark's three and a half, which is visible in the same
 * frame.
 *
 * <p>The still artifact answers the remaining question the animation cannot: a
 * screen points where its wearer is dealing with something, so four identical
 * carriers facing four ways must draw four differently-oriented screens.
 *
 * <p>Activation fires on a fixed tick rather than through the authored use
 * policy, so the recording is the same every run. Everything downstream of it
 * is production code: the real screen grant, the real arc, the real clock, and
 * the real presentation system authoring the appearance the renderer reads.
 */
public final class IntegralSystemFxSnapshotSuite implements SnapshotSuite {

    private static final long SEED = 20260828L;
    // Deliberately a small arena drawn large. The review camera fits the whole
    // grid to the frame, so a wide map is a small marine, and a treatment
    // nobody can see in the evidence is evidence of nothing.
    private static final int WIDTH = 14;
    private static final int FACING_WIDTH = 18;
    private static final int FRAME_WIDTH = 880;
    private static final int FRAME_HEIGHT = 560;

    /** The narrow authored screen and the wide one — the comparison this suite exists for. */
    private static final String NARROW_PATTERN = "armor.foundry-breaker";
    private static final String WIDE_PATTERN = "armor.bulwark-heavy";
    /** A tier-4 suit that carries no system, so "with" and "without" are one frame apart. */
    private static final String PLAIN_PATTERN = "armor.palatine";

    private static final int NARROW_X = 2;
    private static final int WIDE_X = 7;
    private static final int PLAIN_X = 12;

    private static final int TICKS = 200;
    private static final int ACTIVATION_TICK = 20;
    private static final int FRAME_EVERY_TICKS = 3;
    private static final int FRAME_DELAY_MILLIS = 90;

    /** North, in the facing convention {@code AirBody.facingToward} produces. */
    private static final float NORTH = 0f;

    /** Stand-ins holding still: this records a treatment, not a fight. */
    private static final float STAND_IN_HP = 1_000_000f;

    @Override public String id() { return "integral-system-fx"; }

    @Override public String label() {
        return "Integral system: the screen a running system holds, and the window closing";
    }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        BattleReviewFrameRenderer renderer =
                new BattleReviewFrameRenderer(context.modRoot(), FRAME_WIDTH, FRAME_HEIGHT);
        return List.of(recordWindow(renderer), recordFacings(renderer));
    }

    private SnapshotArtifact recordWindow(BattleReviewFrameRenderer renderer) throws Exception {
        BattleSimulation sim = openArena(WIDTH);
        long narrow = carrier(sim, "foundry-breaker", NARROW_PATTERN, NARROW_X, rowFor(WIDTH));
        long wide = carrier(sim, "bulwark", WIDE_PATTERN, WIDE_X, rowFor(WIDTH));
        long plain = carrier(sim, "palatine", PLAIN_PATTERN, PLAIN_X, rowFor(WIDTH));

        List<BufferedImage> frames = new ArrayList<>(TICKS / FRAME_EVERY_TICKS + 1);
        for (int tick = 0; tick <= TICKS; tick++) {
            // Held explicitly, because these three are standing still and have
            // nothing to point at: the aiming rule needs a heading or a target,
            // and this recording is about the arc rather than about the aim.
            face(sim, narrow, NORTH);
            face(sim, wide, NORTH);
            if (tick == ACTIVATION_TICK) {
                sim.getRoster().integralSystems().activate(narrow);
                sim.getRoster().integralSystems().activate(wide);
            }
            if (tick % FRAME_EVERY_TICKS == 0) {
                frames.add(renderer.render(sim, windowCaption(sim, narrow, wide, plain, tick)));
            }
            sim.advance(BattleSimulation.TICK_DT);
        }
        return SnapshotArtifact.animation("screen-arc-and-window.gif", frames, FRAME_DELAY_MILLIS);
    }

    private SnapshotArtifact recordFacings(BattleReviewFrameRenderer renderer) throws Exception {
        BattleSimulation sim = openArena(FACING_WIDTH);
        float[] facings = {0f, -90f, 180f, 90f};
        String[] names = {"north", "east", "south", "west"};
        long[] carriers = new long[facings.length];
        for (int i = 0; i < facings.length; i++) {
            carriers[i] = carrier(sim, names[i], WIDE_PATTERN, 2 + i * 4, rowFor(FACING_WIDTH));
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
                "One pattern, four facings: north, east, south, west — the screen goes where its wearer is looking");
        return new SnapshotArtifact("screen-faces-its-arc.png", frame);
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
     * States the two numbers a viewer is being asked to compare against the
     * picture: how wide each screen is, and how much of each window is left.
     * The caption is a check on the drawing rather than a substitute for it —
     * if the wide carrier's arc reads narrower than the number says, that is
     * the bug this artifact exists to catch.
     */
    private static String windowCaption(BattleSimulation sim, long narrow, long wide,
                                        long plain, int tick) {
        SystemFxService fx = sim.getRoster().systemFx();
        return String.format(Locale.ROOT,
                "t%-3d  Foundry-breaker %s  |  Bulwark %s  |  Palatine: %s",
                tick, state(fx, narrow), state(fx, wide),
                fx.has(plain) ? "carries a system" : "no integral system");
    }

    private static String state(SystemFxService fx, long unit) {
        if (!fx.isRunning(unit)) return "idle";
        return String.format(Locale.ROOT, "%.0f° screen, %d%% of window left",
                fx.arcDegrees(unit), Math.round(fx.intensity(unit) * 100f));
    }
}
