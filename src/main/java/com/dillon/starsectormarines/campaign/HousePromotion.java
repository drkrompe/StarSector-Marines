package com.dillon.starsectormarines.campaign;

import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;

/**
 * Higher-level operations on the rank ladder in {@code houses[]} — the
 * promotion-progress + promote step that {@link CampaignState}'s low-level
 * mutators leave out (see {@code architecture.md} §2). Stateless; operates on a
 * passed-in {@link CampaignState}.
 *
 * <p>The single primitive every promotion-driving caller routes through: the
 * player path (a backed patron's mission victories bump progress, via
 * {@code MissionResolver}) and the autonomous {@code AutonomousPromotionSystem}
 * to come ({@code living-world/overview.md} Slice C). One implementation means
 * the player-fast / autonomous-glacial distinction lives purely in the *delta*
 * each caller passes — the threshold-crossing + cascade logic is shared.
 *
 * <p>Promote semantics mirror the captain XP ladder in {@code MissionResolver}:
 * crossing a {@link HouseRank#promotionThreshold} carries the remainder forward
 * (no progress lost on a big bump) and cascades if a single delta skips a tier.
 * {@link HouseRank#TIER_4} is terminal here — reaching it is the hand-off to the
 * T3 endgame thread, which owns the vanilla faction flip.
 */
public final class HousePromotion {

    private static final Logger LOG = Global.getLogger(HousePromotion.class);

    private HousePromotion() {}

    /**
     * Adds {@code delta} promotion progress to {@code houseRow}, clamped to the
     * {@code short} column range (and floored at zero — suppression chains pass a
     * negative delta to hold a rival down). Does not promote.
     */
    public static void addProgress(CampaignState state, int houseRow, int delta) {
        if (houseRow < 0) return; // tolerate a missed lookup — this is the shared entry point
        int next = state.housePromotionProgress[houseRow] + delta;
        if (next < 0) next = 0;
        if (next > Short.MAX_VALUE) next = Short.MAX_VALUE;
        state.housePromotionProgress[houseRow] = (short) next;
    }

    /**
     * Adds {@code delta} progress and promotes the house as many ranks as the new
     * total crosses, carrying the remainder forward each step. Returns the number
     * of promotions that fired (0 if none).
     */
    public static int addProgressAndPromote(CampaignState state, int houseRow, int delta, int day) {
        if (houseRow < 0) return 0; // tolerate a missed lookup — this is the shared entry point
        addProgress(state, houseRow, delta);

        int promotions = 0;
        while (true) {
            HouseRank rank = HouseRank.fromByte(state.houseRank[houseRow]);
            if (rank == HouseRank.TIER_4) break; // terminal — T3 endgame owns the next step
            int progress = state.housePromotionProgress[houseRow];
            if (progress < rank.promotionThreshold) break;

            state.housePromotionProgress[houseRow] = (short) (progress - rank.promotionThreshold);
            HouseRank promoted = rank.next();
            state.houseRank[houseRow] = promoted.toByte();
            promotions++;
            onPromoted(state, houseRow, rank, promoted, day);
        }
        return promotions;
    }

    /**
     * Side effects of a rank change. Two of {@code mechanics.md}'s three are
     * intentionally absent for now:
     * <ul>
     *   <li><b>Visibility recompute / new relationship edges</b> — lands with the
     *       relationship sim (Slices C–D); no edges are seeded yet.</li>
     *   <li><b>Contract-type unlock</b> — pull-based: {@code ContractGenerator}
     *       reads {@code houseRank} live each tick, so there's nothing to push.</li>
     *   <li><b>TIER_4 → faction flip</b> — the T3 endgame hand-off; that thread
     *       owns the only write-back to vanilla state, so this stays a log line.</li>
     * </ul>
     */
    private static void onPromoted(CampaignState state, int houseRow, HouseRank from, HouseRank to, int day) {
        HouseFlavor flavor = HouseFlavor.fromByte(state.houseFlavor[houseRow]);
        LOG.info("Campaign: house " + state.houseId[houseRow] + " (" + state.houseDisplayName[houseRow]
                + ") promoted " + from.displayName(flavor) + " → " + to.displayName(flavor)
                + " on day " + day);
    }
}
