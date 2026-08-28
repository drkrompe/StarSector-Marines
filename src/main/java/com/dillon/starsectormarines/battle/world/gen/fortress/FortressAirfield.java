package com.dillon.starsectormarines.battle.world.gen.fortress;

import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

/**
 * The garrison's airfield: an apron of open ground with berths on it.
 *
 * <p>Every other thing in the program is a building — a floor with a wall ring
 * round it, competing for the same envelope. An airfield is the opposite shape
 * of problem. Its substance <em>is</em> open ground, and the packing already
 * leaves a great deal of that: at the measured envelope ratio the largest
 * untouched square in a ward runs to seventeen cells a side, which was a defect
 * to be tuned against and is exactly what an apron wants. So the apron is
 * claimed before the packing rather than fitted into it, and the buildings go
 * round it.
 *
 * <p>It is claimed as <b>circulation</b>, which is the packer's existing word
 * for ground that already exists and has to survive. That gets the whole
 * behaviour for free: no room's floor may take apron cells, no room's wall may
 * cross them, and access routing may run through the apron to reach what is on
 * the far side. An apron is, after all, a large paved area people walk over.
 *
 * <p>A berth here is the same bargain a machine berth in a vehicle shed makes.
 * Generation authors the clear footprint and the direction its approach faces;
 * what stands on it is the host's to decide from a roster, so one authored
 * airfield serves a shuttle waiting on the pad, an empty field, and a
 * reinforcement sortie arriving mid-battle without three layouts.
 */
public final class FortressAirfield {

    /** A shuttle berth is five cells square, which is what {@link LandingPad} authors. */
    private static final int PAD = 5;
    /** Wingtip clearance between neighbouring hardstands. */
    private static final int PAD_GAP = 2;
    /** Hardstands in a row. Four is a flight — enough to read as a field, not a helipad. */
    private static final int PADS = 4;
    /** The strip behind the hardstands that crews and vehicles move along. */
    private static final int TAXIWAY = 3;

    /** Cells across the apron, and the depth it needs behind them. */
    private static final int APRON_WIDTH = PADS * PAD + (PADS - 1) * PAD_GAP;
    private static final int APRON_DEPTH = PAD + TAXIWAY;

    /** What the yard pass leaves behind, and therefore what counts as spare. */
    private static final GroundKind YARD = GroundKind.DIRT;

    /** Ground the apron is paved with, so it reads as a made surface, not yard. */
    private static final GroundKind APRON = GroundKind.STONE;
    /** Hardstand marking, the same hazard treatment a vehicle bay uses. */
    private static final GroundKind HARDSTAND = GroundKind.STRIPED;

    private final int left;
    private final int bottom;
    private final int right;
    private final int top;
    private final LandingPad.Approach approach;

    private FortressAirfield(int left, int bottom, int right, int top,
                             LandingPad.Approach approach) {
        this.left = left;
        this.bottom = bottom;
        this.right = right;
        this.top = top;
        this.approach = approach;
    }

    /**
     * Find the apron a site in the yard the packing left over, or null when it
     * left none big enough.
     *
     * <p>Sited after the packing rather than claimed before it, and that is a
     * deliberate reversal. Reserving the apron first is the tidier idea and it
     * is what a building would want, but an apron is not a building: it is open
     * ground, and open ground is precisely what packing leaves. Claiming it up
     * front bought nothing and cost a great deal — the reservation reshaped
     * every placement around it, and on one canonical map that reshaping left
     * no heavy-vehicle route to the defender rear. The ward is still perfectly
     * walkable and every building still has its door, so nothing in generation
     * notices; a relief convoy simply cannot get in. Ground that is already
     * spare cannot do that to a map.
     *
     * <p>The trade is honest: an airfield is not guaranteed. A ward whose
     * packing left no clear stretch has no airfield that seed, in the same way
     * a compound with no wing a five-by-seven bay fits in has no vehicle shed.
     */
    public static FortressAirfield site(GenContext ctx, boolean[][] ground,
                                        TraversalAxis axis,
                                        int envLeft, int envBottom,
                                        int envRight, int envTop) {
        boolean alongY = axis == TraversalAxis.SOUTH_TO_NORTH;
        int width = alongY ? APRON_WIDTH : APRON_DEPTH;
        int depth = alongY ? APRON_DEPTH : APRON_WIDTH;

        int bestLeft = -1;
        int bestBottom = -1;
        int bestRank = Integer.MIN_VALUE;
        for (int x = envLeft; x + width - 1 <= envRight; x++) {
            for (int y = envBottom; y + depth - 1 <= envTop; y++) {
                if (!spare(ctx, ground, x, y, x + width - 1, y + depth - 1)) continue;
                // Deepest first: an airfield belongs behind the fighting, because
                // a garrison that parks its lift on the wall loses it to the
                // first bombardment.
                int rank = alongY ? y : x;
                if (rank > bestRank) {
                    bestRank = rank;
                    bestLeft = x;
                    bestBottom = y;
                }
            }
        }
        if (bestLeft < 0) return null;

        // Approach faces the way the attacker came from, which is the open side
        // of the compound: a shuttle comes in over the ward rather than over the
        // wall behind it.
        LandingPad.Approach approach = alongY
                ? LandingPad.Approach.SOUTH : LandingPad.Approach.WEST;
        return new FortressAirfield(bestLeft, bestBottom,
                bestLeft + width - 1, bestBottom + depth - 1, approach);
    }

    /**
     * Whether this rectangle is yard and nothing else.
     *
     * <p>Walkable is not enough. A roadway is walkable and belongs to the
     * packing's own circulation; a building's threshold is walkable and is its
     * door. Only ground the yard pass opened — unclaimed, unlabelled, inside
     * the envelope — is genuinely spare, and paving over anything else would
     * take something the ward is using.
     */
    private static boolean spare(GenContext ctx, boolean[][] ground,
                                 int left, int bottom, int right, int top) {
        for (int x = left; x <= right; x++) {
            for (int y = bottom; y <= top; y++) {
                if (x < 0 || y < 0 || x >= ctx.width || y >= ctx.height) return false;
                if (!ground[x][y] || !ctx.grid.isWalkable(x, y)) return false;
                if (ctx.topology.getRoomPurpose(x, y) != RoomPurpose.GENERIC) return false;
                if (ctx.topology.getGroundKind(x, y) != YARD) return false;
            }
        }
        return true;
    }

    /**
     * Pave the apron, mark its hardstands, and author a berth on each.
     *
     * <p>What this leaves behind is ground rather than structure: nothing here
     * is a wall, and the whole point of an airfield in a fight is that crossing
     * it means crossing it in the open.
     */
    public void author(GenContext ctx, TraversalAxis axis) {
        for (int x = left; x <= right; x++) {
            for (int y = bottom; y <= top; y++) {
                ctx.grid.setWalkableFloor(x, y);
                ctx.topology.setGroundKind(x, y, APRON);
                ctx.topology.setRoomPurpose(x, y, RoomPurpose.HANGAR);
            }
        }

        boolean alongY = axis == TraversalAxis.SOUTH_TO_NORTH;
        for (int i = 0; i < PADS; i++) {
            int offset = i * (PAD + PAD_GAP) + PAD / 2;
            // Hardstands sit at the rear of the apron; the taxiway is the strip
            // in front of them, which is the side the aircraft leave over.
            int centreX = alongY ? left + offset : right - PAD / 2;
            int centreY = alongY ? top - PAD / 2 : bottom + offset;
            markHardstand(ctx, centreX, centreY);
            ctx.landingPads.add(LandingPad.garrison(centreX, centreY, approach));
        }

        int centreX = (left + right) / 2;
        int centreY = (bottom + top) / 2;
        ctx.tactical.add(new TacticalNode(TacticalNode.Kind.AIRBASE,
                centreX, centreY, left, bottom, right, top,
                Faction.DEFENDER, 65, 3, false));
    }

    /** Paint one berth's footprint so a pad reads as a pad from across the yard. */
    private void markHardstand(GenContext ctx, int centreX, int centreY) {
        for (int x = centreX - PAD / 2; x <= centreX + PAD / 2; x++) {
            for (int y = centreY - PAD / 2; y <= centreY + PAD / 2; y++) {
                if (x < left || x > right || y < bottom || y > top) continue;
                ctx.topology.setGroundKind(x, y, HARDSTAND);
            }
        }
    }

    /**
     * Ground one apron occupies, for a caller sizing a ward that has to hold it.
     *
     * <p>A fixed figure rather than a measurement of a placed field, because
     * the sizing happens before the reservation does: the ward has to be big
     * enough for an airfield before anyone can say where the airfield goes. The
     * apron is the same size wherever it lands, so there is nothing to measure.
     */
    public static int apronArea() {
        return APRON_WIDTH * APRON_DEPTH;
    }
}
