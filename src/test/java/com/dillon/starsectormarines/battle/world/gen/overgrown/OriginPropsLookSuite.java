package com.dillon.starsectormarines.battle.world.gen.overgrown;

import com.dillon.starsectormarines.battle.evacuation.BoreTier;
import com.dillon.starsectormarines.battle.evacuation.SwarmBore;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.PreparedGround;
import com.dillon.starsectormarines.battle.world.gen.overgrown.OriginProp.State;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The heart's origin props as the owner should judge them (`heart-origins.md`): each origin's set piece composed
 * from {@link OriginProp} on real generated ground, the first mother opened beside its telling object and the creep
 * matured round it as weeks old, a marine squad standing off it for scale. Framed at play zoom and close. Each origin
 * is shot twice, its telling object {@link State#GROWN grown} and {@link State#SWALLOWED swallowed}, the open look
 * choice. The composition is a stand-in for the heart's own layout, which places these on its maps.
 */
public final class OriginPropsLookSuite implements SnapshotSuite {

    static final int W = 44, H = 30;
    /** The telling object's low corner in the window; every set piece is laid round it. */
    static final int TX = 20, TY = 13;

    @Override public String id() { return "origin-props-look"; }

    @Override public String label() { return "Heart origin props: each origin's set piece in the creep, grown and swallowed"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        List<SnapshotArtifact> out = new ArrayList<>();
        for (Origin origin : Origin.values()) {
            for (State state : new State[]{State.GROWN, State.SWALLOWED}) {
                String name = origin.name().toLowerCase() + "-" + state.name().toLowerCase();
                out.addAll(LookReview.record(context.modRoot(), shot(name, origin, state)).stream()
                        .filter(a -> !a.relativePath().toString().endsWith(".gif")).toList());
            }
        }
        return out;
    }

    enum Origin { WORKINGS, FREIGHT, LAB, PARK }

    static LookReview.Shot shot(String name, Origin origin, State telling) {
        return new LookReview.Shot(name, LookGround.verdant(), W, H,
                placement -> {
                    int x0 = placement.site().x0(), y0 = placement.site().y0();
                    GenContext ctx = new GenContext(placement.map().grid, placement.map().topology, new Random(5L),
                            placement.map().grid.getWidth(), placement.map().grid.getHeight(), 5L);
                    clear(placement.map().grid, ctx, placement.map().doodads, x0 + 2, y0 + 2, W - 4, H - 4);
                    Laying lay = new Laying(ctx, x0, y0);
                    int[] bore = switch (origin) {
                        case WORKINGS -> workings(lay, telling);
                        case FREIGHT -> freight(lay, telling);
                        case LAB -> lab(lay, telling);
                        case PARK -> park(lay, telling);
                    };
                    SceneBuilder builder = placement.builder().doodads(ctx.doodads);
                    builder.squad("marines").faction(Faction.MARINE).size(6).at(TX - 10, 4).stationary().done();
                    BattleSimulation sim = builder.build().sim();
                    SwarmBore mother = sim.swarmBores().open(sim, 1, true, x0 + bore[0], y0 + bore[1]);
                    for (int guard = 0; mother != null && mother.tier().ordinal() < BoreTier.MAW.ordinal()
                            && guard < 64; guard++) sim.swarmBores().feed(sim, mother, 1f);
                    sim.creepField();
                    sim.matureCreep();
                    System.out.printf("    [origin/%s] %d props laid, %d refused, mother %s%n", name, lay.laid,
                            lay.refused, mother == null ? "REFUSED" : "open");
                    return sim;
                },
                placement -> {
                    float cx = placement.site().x(TX + 2f);
                    float cy = placement.site().y(origin == Origin.PARK ? TY - 0.5f : TY + 1.5f);
                    return List.of(LookReview.View.play(cx, cy), LookReview.View.close("close", cx, cy, 20f));
                }, 12, 12, 1).withStills(1);
    }

    /** Clears the set piece's ground: open floor, nothing laid on it, so the composition is the same on any seed. */
    private static void clear(NavigationGrid grid, GenContext ctx, List<Doodad> laid, int x, int y, int w, int h) {
        laid.removeIf(d -> d.cellX < x + w && d.cellX + d.footprintCellsX > x && d.cellY < y + h
                && d.cellY + d.footprintCellsY > y);
        for (int cy = y; cy < y + h; cy++) for (int cx = x; cx < x + w; cx++) {
            grid.setWalkableFloor(cx, cy);
            ctx.topology.setFixture(cx, cy, false);
        }
    }

    /** Places in window coordinates and counts what went down. */
    private static final class Laying {
        final GenContext ctx;
        final int x0, y0;
        int laid, refused;

        Laying(GenContext ctx, int x0, int y0) { this.ctx = ctx; this.x0 = x0; this.y0 = y0; }

        void put(OriginProp prop, int x, int y, int turns) { put(prop, x, y, turns, State.GROWN); }

        void put(OriginProp prop, int x, int y, int turns, State state) {
            if (prop.place(ctx, x0 + x, y0 + y, turns, state)) laid++;
            else refused++;
        }

        void path(int x, int y, int w, int h) {
            for (int cy = y; cy < y + h; cy++) for (int cx = x; cx < x + w; cx++)
                ctx.topology.setGroundKind(x0 + cx, y0 + cy, GroundKind.STONE);
        }
    }

    /** The shaft with its pump sheds either side and their pipes running into its broken collar. */
    private static int[] workings(Laying lay, State telling) {
        lay.put(OriginProp.SHAFT, TX, TY, 0, telling);
        lay.put(OriginProp.PUMP_SHED, TX - 12, TY + 4, 0);
        lay.put(OriginProp.PUMP_SHED, TX + 11, TY + 5, 0);
        lay.put(OriginProp.PIPE_MOUTH, TX - 2, TY + 1, 0);
        lay.put(OriginProp.PIPE_RUN, TX - 5, TY + 1, 0);
        lay.put(OriginProp.PIPE_ELBOW, TX - 7, TY + 1, 1);
        lay.put(OriginProp.PIPE_RUN, TX - 7, TY + 3, 1);
        lay.put(OriginProp.PIPE_MOUTH, TX + 3, TY + 1, 2);
        lay.put(OriginProp.PIPE_RUN, TX + 5, TY + 1, 0);
        lay.put(OriginProp.PIPE_RUN, TX + 8, TY + 1, 0);
        return new int[]{TX + 1, TY - 3};
    }

    /** The split reefer beside the crane that was lifting it, its seafood crates, the stack, two trucks stopped mid-unload. */
    private static int[] freight(Laying lay, State telling) {
        lay.put(OriginProp.SPLIT_CONTAINER, TX - 1, TY, 0, telling);
        lay.put(OriginProp.GANTRY_LEG, TX - 3, TY + 2, 0);
        lay.put(OriginProp.GANTRY_LEG, TX + 3, TY + 2, 0);
        lay.put(OriginProp.GANTRY_SPAN, TX - 3, TY + 2, 0);
        lay.put(OriginProp.CONTAINER_RUST, TX - 14, TY + 2, 0);
        lay.put(OriginProp.CONTAINER_BLUE, TX - 14, TY + 4, 0);
        lay.put(OriginProp.CONTAINER_RUST, TX - 14, TY + 6, 0);
        lay.put(OriginProp.CONTAINER_BLUE, TX - 9, TY + 4, 0);
        lay.put(OriginProp.CONTAINER_BLUE, TX + 9, TY + 5, 1);
        lay.put(OriginProp.CONTAINER_RUST, TX + 11, TY + 5, 1);
        lay.put(OriginProp.SEAFOOD_CRATES, TX - 4, TY - 2, 0);
        lay.put(OriginProp.SEAFOOD_CRATES, TX + 4, TY - 2, 1);
        lay.put(OriginProp.UNLOADING_TRUCK, TX - 8, TY - 5, 0);
        lay.put(OriginProp.UNLOADING_TRUCK, TX + 7, TY - 3, 2);
        return new int[]{TX + 1, TY - 4};
    }

    /** The blown annex inside its fence, the fence sandbagged on its inner side, the kit dragged out round it. */
    private static int[] lab(Laying lay, State telling) {
        lay.put(OriginProp.ANNEX, TX - 1, TY - 1, 0, telling);
        int left = TX - 7, right = TX + 9, bottom = TY - 7, top = TY + 7;
        GenContext ctx = lay.ctx;
        for (int x = left + 1; x < right; x++) {
            if (Math.abs(x - (TX + 1)) <= 1) continue;
            PreparedGround.fence(ctx, lay.x0 + x, lay.y0 + top, true);
            PreparedGround.fence(ctx, lay.x0 + x, lay.y0 + bottom, true);
            PreparedGround.sandbag(ctx, lay.x0 + x, lay.y0 + top - 1, new int[]{0, -1}, null);
        }
        for (int y = bottom + 1; y < top; y++) {
            PreparedGround.fence(ctx, lay.x0 + left, lay.y0 + y, false);
            PreparedGround.fence(ctx, lay.x0 + right, lay.y0 + y, false);
            PreparedGround.sandbag(ctx, lay.x0 + left + 1, lay.y0 + y, new int[]{1, 0}, null);
            PreparedGround.sandbag(ctx, lay.x0 + right - 1, lay.y0 + y, new int[]{-1, 0}, null);
        }
        lay.put(OriginProp.TANK_RACK, TX - 4, TY + 4, 0);
        lay.put(OriginProp.TANK_RACK, TX + 4, TY + 4, 0);
        lay.put(OriginProp.SAMPLE_FREEZER, TX - 4, TY - 4, 0);
        lay.put(OriginProp.SAMPLE_FREEZER, TX + 5, TY - 4, 0);
        return new int[]{TX + 1, TY - 3};
    }

    /** The pond the first mother opens in, the bandstand beside it, the playground and benches on the paths round them. */
    private static int[] park(Laying lay, State telling) {
        lay.path(2, TY - 1, W - 4, 1);
        lay.path(TX + 1, 2, 1, H - 4);
        lay.put(OriginProp.BANDSTAND, TX - 1, TY + 1, 0, telling);
        lay.put(OriginProp.POND, TX - 1, TY - 5, 0, telling);
        lay.put(OriginProp.SWINGS, TX - 14, TY + 4, 0);
        lay.put(OriginProp.SLIDE, TX - 14, TY - 3, 0);
        lay.put(OriginProp.CLIMBER, TX - 9, TY + 4, 0);
        lay.put(OriginProp.ROUNDABOUT, TX - 9, TY - 4, 0);
        lay.put(OriginProp.BENCH, TX + 6, TY, 0);
        lay.put(OriginProp.BENCH, TX + 10, TY, 0);
        lay.put(OriginProp.BENCH, TX + 6, TY - 3, 0);
        lay.put(OriginProp.BENCH, TX - 6, TY, 0);
        // Beside the pond rather than in it: the look is of the pond, which the mother's own sprite would cover.
        return new int[]{TX + 5, TY - 5};
    }
}
