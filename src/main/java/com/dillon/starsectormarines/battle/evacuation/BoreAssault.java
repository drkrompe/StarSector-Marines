package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.vehicle.VehicleClearance;

import java.util.List;

/**
 * One lane's assault on one bore (`heart-bore-assault.md`): the lanes' commander's tactical layer. The
 * mission picks the bore; this decides where the lane's machines and militia stand and when the militia go in.
 *
 * <p><b>Phases.</b> {@link Phase#MOVING_UP}: the machines drive to their posts on the stand-off ring; after a bore
 * has fallen the militia wait where they stand until the machines are set. {@link Phase#BASE_OF_FIRE}: machines on
 * the ring fight the bore, the militia hold the support line behind them. {@link Phase#ASSAULT}: on a spent surge the
 * militia go in. {@link Phase#FALLING_BACK}: the bore's next surge sends them back to the support line, and the lane
 * waits for that surge to be spent.
 *
 * <p><b>The ring.</b> A post lies outside the bore's mouth by a margin, at a share of the hull's reach where that is
 * farther, on the lane's near side, spread along the ring ({@link #ringPost}). Allied command's stands then slide it
 * along the ring or back from it, never in, because each post faces the bore.
 */
final class BoreAssault {

    /** Cells about a bore its defenders spawn into: no post and no hold is inside it. */
    static final int MOUTH_CELLS = 12;
    /** How far outside the mouth the nearest post on the ring is. */
    static final int RING_MARGIN_CELLS = 3;
    /** Share of a hull's gun reach it stands off at where that is farther than the mouth and its margin. */
    static final float REACH_SHARE = 0.7f;
    /** Half the arc of the ring, about the way from the bore to the lane, that posts are drawn from. */
    static final float NEAR_ARC_DEG = 60f;
    /** Degrees along the ring between neighbouring machines' posts. */
    static final float POST_SPREAD_DEG = 15f;
    /** Cells behind the nearest ring the militia hold, toward the lane. */
    static final int SUPPORT_BACK_CELLS = 5;
    /**
     * Cells behind the support line a fire section stands: its rounds arc over the militia, out of the mouth's reach
     * and inside its launchers' reach.
     */
    static final int SECTION_BACK_CELLS = 6;
    /** Seconds of base of fire with the bore no weaker after which the lane calls a fire section. */
    static final float UNHURT_SECONDS = 30f;
    /** Seconds after a surge comes out before it can be spent: the bodies are still pouring from the mouth. */
    static final float SETTLE_SECONDS = 3f;
    /** Most of the swarm the mouth may hold for a surge to count as spent. */
    static final int QUIET_BODIES = 4;
    /** Seconds without a surge after which a quiet mouth is gone in on anyway: the bore is not being hurt. */
    static final float QUIET_SECONDS = 40f;
    /** Longest the militia wait for the machines after a fall before moving up to the support line anyway. */
    static final float ARMOUR_FIRST_SECONDS = 60f;
    /** Cells from its post a machine counts as set. */
    static final int SET_CELLS = 6;

    enum Phase { MOVING_UP, BASE_OF_FIRE, ASSAULT, FALLING_BACK }

    final String boreKey;
    final int boreX, boreY;
    /** The unit way from the bore toward the lane, fixed when the assault begins. */
    final float nearX, nearY;
    /** Whether the militia wait for the machines before moving up: the lane took this bore after one fell. */
    final boolean armourFirst;
    final int startTick;
    Phase phase = Phase.MOVING_UP;
    int phaseTick;
    /** The bore's surge count when last read, and the tick the last new surge was seen; -1 for none yet. */
    int surgesSeen;
    int lastSurgeTick = -1;
    /** The militia's support line, a walkable cell; set when the assault begins. */
    int supportX, supportY;
    /** The bore's integrity when it was last seen to fall, and the tick it was. */
    float integrity = 1f;
    int integrityTick;

    BoreAssault(String boreKey, int boreX, int boreY, float nearX, float nearY, boolean armourFirst,
                int surges, int tick) {
        this.boreKey = boreKey;
        this.boreX = boreX;
        this.boreY = boreY;
        float length = (float) Math.hypot(nearX, nearY);
        this.nearX = length > 1e-4f ? nearX / length : 0f;
        this.nearY = length > 1e-4f ? nearY / length : -1f;
        this.armourFirst = armourFirst;
        this.startTick = tick;
        this.phaseTick = tick;
        this.integrityTick = tick;
        this.surgesSeen = surges;
    }

    /**
     * The ring a machine with gun reach {@code reach} stands on: {@link #REACH_SHARE} of its reach, leaving room for a
     * stand to slide back, but never inside the mouth and its margin, even for a gun that cannot reach the bore from
     * there (it fights what comes out).
     */
    static int ringFor(float reach) {
        return Math.max(MOUTH_CELLS + RING_MARGIN_CELLS, Math.round(reach * REACH_SHARE));
    }

    /** Where the {@code index}-th machine of {@code count} is aimed along the ring: 0°, then alternately either side. */
    static float slotDegrees(int index, int count) {
        if (count <= 1) return 0f;
        float offset = (index - (count - 1) / 2f) * POST_SPREAD_DEG;
        return Math.max(-NEAR_ARC_DEG, Math.min(NEAR_ARC_DEG, offset));
    }

    /**
     * A machine's post on the ring {@code ring} cells from the bore at ({@code boreX}, {@code boreY}): a cell its
     * clearance passes, within {@link #NEAR_ARC_DEG} of the near way ({@code nearX}, {@code nearY}), not within
     * {@code spacing} of a post already {@code taken}, nearest along the ring to {@code slotDeg} off the near way.
     * A cell with a line of fire to the bore beats one without. {@code null} when no cell of the arc serves.
     */
    static int[] ringPost(NavigationGrid grid, VehicleClearance clearance, int boreX, int boreY,
                          float nearX, float nearY, int ring, float slotDeg, List<int[]> taken, int spacing) {
        float cx = boreX + .5f, cy = boreY + .5f;
        double nearAngle = Math.atan2(nearY, nearX);
        int[] best = null;
        float bestCost = Float.MAX_VALUE;
        for (int dy = -ring - 1; dy <= ring + 1; dy++) {
            for (int dx = -ring - 1; dx <= ring + 1; dx++) {
                double distance = Math.hypot(dx, dy);
                if (distance < ring || distance >= ring + 1.5) continue;
                double off = Math.toDegrees(wrap(Math.atan2(dy, dx) - nearAngle));
                if (Math.abs(off) > NEAR_ARC_DEG) continue;
                int x = boreX + dx, y = boreY + dy;
                if (!clearance.isPassable(x, y) || crowded(x, y, taken, spacing)) continue;
                float cost = (float) Math.abs(off - slotDeg);
                if (!grid.hasLineOfFire(x + .5f, y + .5f, cx, cy)) cost += 1000f;
                if (cost < bestCost) {
                    bestCost = cost;
                    best = new int[]{x, y};
                }
            }
        }
        return best;
    }

    /**
     * A fire-section carrier's ring: as far out as its launcher reaches, less {@link #RING_MARGIN_CELLS}, and never
     * nearer than {@link #SECTION_BACK_CELLS} behind the support line. A carrier is soft, and one stood just behind the
     * militia was overrun within a minute of each surge; its spotters stand on the support line, not beside it.
     */
    static int sectionRing(float reach) {
        return Math.max(MOUTH_CELLS + RING_MARGIN_CELLS + SUPPORT_BACK_CELLS + SECTION_BACK_CELLS,
                Math.round(reach) - RING_MARGIN_CELLS);
    }

    /**
     * Whether a lane on its bore needs a fire section: none of its machines on the ring has a line of fire to the
     * bore, or the bore has lost nothing for {@link #UNHURT_SECONDS} of base of fire.
     */
    static boolean needsSection(boolean ringSeesBore, float unhurtSeconds) {
        return !ringSeesBore || unhurtSeconds >= UNHURT_SECONDS;
    }

    /** The militia's support line: {@link #SUPPORT_BACK_CELLS} behind the nearest ring, on the near way. */
    static int[] supportCell(int boreX, int boreY, float nearX, float nearY) {
        int back = MOUTH_CELLS + RING_MARGIN_CELLS + SUPPORT_BACK_CELLS;
        return new int[]{Math.round(boreX + nearX * back), Math.round(boreY + nearY * back)};
    }

    /**
     * Where a formation coming from ({@code fromX}, {@code fromY}) onto the bore rallies for its assault: the bore's
     * support line on the way it comes. {@code null} when the lane stands that close already, with no advance to make.
     */
    static int[] rallyCell(int fromX, int fromY, int boreX, int boreY) {
        float dx = fromX - boreX, dy = fromY - boreY;
        float distance = (float) Math.hypot(dx, dy);
        if (distance <= MOUTH_CELLS + RING_MARGIN_CELLS + SUPPORT_BACK_CELLS + SET_CELLS) return null;
        return supportCell(boreX, boreY, dx / distance, dy / distance);
    }

    /**
     * Whether the bore's last surge is spent: one has come out, {@link #SETTLE_SECONDS} have passed, and the mouth
     * holds no more than {@link #QUIET_BODIES}; or none has come for {@link #QUIET_SECONDS} and the mouth is that quiet.
     *
     * @param sinceSurge seconds since the last surge seen, or negative for none since the assault began
     * @param sinceStart seconds since the assault began
     */
    static boolean spent(float sinceSurge, float sinceStart, int mouthBodies) {
        if (mouthBodies > QUIET_BODIES) return false;
        return sinceSurge >= 0f ? sinceSurge >= SETTLE_SECONDS : sinceStart >= QUIET_SECONDS;
    }

    /** Reads the bore's surge count; true when a new surge has come out since last read. */
    boolean surged(int surges, int tick) {
        if (surges <= surgesSeen) return false;
        surgesSeen = surges;
        lastSurgeTick = tick;
        return true;
    }

    void enter(Phase next, int tick) {
        phase = next;
        phaseTick = tick;
    }

    private static boolean crowded(int x, int y, List<int[]> taken, int spacing) {
        for (int[] t : taken) if (Math.max(Math.abs(t[0] - x), Math.abs(t[1] - y)) < spacing) return true;
        return false;
    }

    private static double wrap(double radians) {
        while (radians > Math.PI) radians -= 2 * Math.PI;
        while (radians < -Math.PI) radians += 2 * Math.PI;
        return radians;
    }
}
