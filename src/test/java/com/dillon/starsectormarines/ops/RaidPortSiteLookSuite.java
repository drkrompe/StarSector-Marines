package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.ParkedAircraft;
import com.dillon.starsectormarines.battle.air.PortFreighter;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.DefensePost;
import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.ReviewStyle;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;

import java.util.List;
import java.util.Locale;

/**
 * The spaceport Raid site, framed as {@link RaidSiteLookSuite} frames heavy industry's, its close-up taking in the
 * cargo terminal and the lighter apron beside it, from the bonded sheds to the lighter on its pad; and a close-up of
 * the hub's town side ({@code <case>-town-side.png}): the terminal, the truck gate's road and the streets meeting the
 * lines, where the town's roads join the port's.
 */
public final class RaidPortSiteLookSuite extends RaidSiteLookSuite {


    public RaidPortSiteLookSuite() {
        super("raid-port-site-look", "spaceport", EconomicFunction.COMMERCE);
    }

    @Override
    protected float[] closeUp(BattleSimulation sim, float machinesX, float machinesY) {
        float lx = 0, ly = 0;
        int n = 0;
        for (ParkedAircraft parked : sim.getParkedAircraft()) {
            // The port's traffic is bulk freighters; a town's berths hold its small craft.
            if (!(parked.type instanceof PortFreighter)) continue;
            lx += parked.centerX;
            ly += parked.centerY;
            n++;
        }
        if (n == 0) return super.closeUp(sim, machinesX, machinesY);
        lx /= n;
        ly /= n;
        // The pad line leads the frame, centred on its lighters; the terminal stands at the frame's edge.
        float cx = machinesX * .3f + lx * .7f;
        return new float[]{cx, ly, Math.abs(lx - machinesX) * .8f + 64f};
    }

    /** Width of the town side's close-up, cells. */
    static final float TOWN_SIDE_WIDTH = 120f;

    @Override
    protected List<Shot> extraShots(BattleSimulation sim, float machinesX, float machinesY) {
        float[] pads = pads(sim);
        if (pads == null) return List.of();
        // From the terminal a little toward the hub, and from it toward the lines, away from the pads.
        float x = machinesX + Math.signum(pads[0] - machinesX) * TOWN_SIDE_WIDTH * .3f;
        float y = machinesY + (machinesY - pads[1]) * .5f;
        return List.of(new Shot("town-side", x, y, TOWN_SIDE_WIDTH));
    }

    /** Cells a gun's facing arrow runs: its laid lane. */
    static final float GUN_ARROW = 10f;

    /**
     * The whole map marked with every gun ({@code <case>-guns.png}): an arrow from each emplacement along the bearing
     * its guns are laid on, labelled with its weight, so where the port's defence stands and what it covers reads at a
     * glance.
     */
    @Override
    protected void moreShots(SnapshotContext context, BattleSimulation sim, String caseId, List<SnapshotArtifact> out)
            throws Exception {
        ReviewAnnotations.Builder marks = ReviewAnnotations.builder();
        for (DefensePost post : sim.getDefensePosts()) {
            System.out.printf(Locale.ROOT, "[%s] %s gun %s %d,%d facing %s%n", id(), caseId, post.tier, post.anchorX,
                    post.anchorY, post.restFacing == null ? "none" : String.format(Locale.ROOT, "%.0f", post.restFacing));
            float x = post.anchorX + .5f, y = post.anchorY + .5f;
            String label = switch (post.tier) {
                case LIGHT -> "L";
                case MEDIUM -> "M";
                case LARGE -> "H";
                case ARTILLERY -> "A";
                default -> "D";
            };
            if (post.restFacing == null) {
                marks.label(x, y, label, ReviewStyle.KEEP);
                continue;
            }
            double a = Math.toRadians(post.restFacing);
            marks.arrow(x, y, x - (float) Math.sin(a) * GUN_ARROW, y + (float) Math.cos(a) * GUN_ARROW, label,
                    ReviewStyle.KEEP);
        }
        int w = sim.getGrid().getWidth(), h = sim.getGrid().getHeight();
        out.add(new SnapshotArtifact(caseId + "-guns.png", BattleReviewFrameRenderer.look(context.modRoot(), 1920,
                1080, w / 2f, h / 2f, Math.max(w, h * 16f / 9f) + 4f).renderMarked(sim, marks.build())));
    }

    /** The port's ships' mean cell, or null on a map with none. */
    private static float[] pads(BattleSimulation sim) {
        float lx = 0, ly = 0;
        int n = 0;
        for (ParkedAircraft parked : sim.getParkedAircraft()) {
            if (!(parked.type instanceof PortFreighter)) continue;
            lx += parked.centerX;
            ly += parked.centerY;
            n++;
        }
        return n == 0 ? null : new float[]{lx / n, ly / n};
    }
}
