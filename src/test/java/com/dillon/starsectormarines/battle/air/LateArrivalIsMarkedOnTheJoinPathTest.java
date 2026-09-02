package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.infantry.MarineLoadout;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.CampaignSquadTag;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The join path marks a late arrival.
 *
 * <p>{@code SquadRejoin.markIfLateArrival} is tested on its own arguments, and
 * {@code LateArrivalScene} plays the crossing by calling that rule directly —
 * so the one thing neither of them touches is the line in
 * {@link InfantryPayload} that actually calls it, on the tagged campaign join
 * path only. This lands two lifts of one campaign squad at two ends of one
 * landing area and asks the squad afterwards.
 *
 * <p>Two marines and a strength of two, so the second deboard is the one that
 * completes the squad: the form-up gate is what would otherwise answer here,
 * and a squad still expecting people is not a squad anybody has arrived late
 * to.
 */
class LateArrivalIsMarkedOnTheJoinPathTest {

    private static final int W = 64;
    private static final int H = 24;

    /** One logical arrival, so both lifts join one battle squad however far apart they set down. */
    private static final int LANDING_AREA = 7;

    /** Two lifts, far enough apart that the second lands well outside cohesion of the first. */
    private static final int NEAR_LZ_X = 8;
    private static final int FAR_LZ_X = 52;
    private static final int LZ_Y = 12;

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        BattleSimulation sim = new BattleSimulation(grid, new CellTopology(W, H));
        sim.setMissionCompletionEnabled(false);
        return sim;
    }

    private static MarineLoadout seat(CampaignSquadTag tag) {
        return new MarineLoadout(UnitRole.COMBATANT, null, MarineLoadout.DEFAULT_PRIMARY_ID,
                null, null, null, 0, null, null, 0f, 0f, 1f, 1f, tag);
    }

    /** The squad's live membership, which the dense roster array over-allocates. */
    private static long[] members(BattleSimulation sim, Squad squad) {
        long[] dense = sim.getRoster().squadMemberArray(squad.id);
        int count = Math.min(sim.getRoster().squadMemberCount(squad.id), dense.length);
        long[] live = new long[count];
        System.arraycopy(dense, 0, live, 0, count);
        return live;
    }

    /**
     * Sets one marine down at {@code lzX} through the shipped deboard, and
     * returns the battle squad it joined.
     */
    private static Squad land(BattleSimulation sim, int lzX, MarineLoadout seat,
                              int arrivalGroupId) {
        long craft = sim.spawnShuttle(ShuttleType.AEROSHUTTLE, Faction.MARINE,
                lzX, LZ_Y, lzX, LZ_Y, 2f, 2f, 0f, 1);
        ShuttleMission mission = sim.world().mission(craft);
        mission.state = ShuttleState.LANDED;
        mission.landingAreaId = LANDING_AREA;
        mission.arrivalGroupId = arrivalGroupId;
        mission.expectedArrivalStrength = 2;
        mission.marineLoadout = new MarineLoadout[]{seat};
        mission.marinesRemaining = 1;
        mission.deboardedThisSortie = 0;
        mission.deboardCountdown = 0f;

        for (int tick = 0; tick < 20 && mission.deboardedThisSortie < 1; tick++) {
            sim.advance(BattleSimulation.TICK_DT);
        }
        assertTrue(mission.deboardedThisSortie == 1, "the lift never set anybody down");
        Squad squad = sim.getSquad(mission.squadId);
        assertNotNull(squad, "the marine landed into no squad at all");
        return squad;
    }

    /** The last marine of a tagged squad, landing a map away from it, is rejoining. */
    @Test
    void aTaggedMarineLandingAwayFromItsSquadIsMarkedRejoining() {
        BattleSimulation sim = openSim();
        CampaignSquadTag tag = new CampaignSquadTag("cs-join-path", "Squad 01", false, 2, 0);

        Squad squad = land(sim, NEAR_LZ_X, seat(tag), -1);
        long first = squad.leaderId;
        Squad joined = land(sim, FAR_LZ_X, seat(tag), -1);

        assertTrue(joined == squad, "the second lift landed into a different squad");
        assertFalse(squad.isRejoining(first), "the first marine had nobody to rejoin");
        long late = 0L;
        for (long member : members(sim, squad)) {
            if (member != first) late = member;
        }
        assertTrue(late != 0L, "the second marine is not in the squad");
        assertTrue(squad.isRejoining(late),
                "a campaign marine landed a map behind its squad and joined as an ordinary member");
    }

    /**
     * The control: generated personnel take the same journey and are never
     * marked, because only the campaign join path asks.
     */
    @Test
    void anUntaggedArrivalIsNeverMarked() {
        BattleSimulation sim = openSim();

        Squad squad = land(sim, NEAR_LZ_X, seat(null), 3);
        Squad joined = land(sim, FAR_LZ_X, seat(null), 3);

        assertTrue(joined == squad, "the arrival group did not group");
        assertTrue(members(sim, squad).length == 2, "both lifts did not land");
        for (long member : members(sim, squad)) {
            assertFalse(squad.isRejoining(member), "generated personnel carry the state");
        }
    }
}
