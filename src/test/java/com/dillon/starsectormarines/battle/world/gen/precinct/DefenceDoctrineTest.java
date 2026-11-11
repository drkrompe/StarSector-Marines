package com.dillon.starsectormarines.battle.world.gen.precinct;

import com.dillon.starsectormarines.battle.turret.DefensePostKind;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A walled place's emplacements are sited by doctrine: off every road and gate approach, flanking every gate before
 * anything else, the heaviest at the main gate, corners facing the threat, every direct-fire gun outside the wall with
 * its lane open, and standoff weapons deep and away from the main gate. Proved on a bare square with gates cut in it.
 */
class DefenceDoctrineTest {

    private static final int W = 80, H = 80;
    /** The place behind its wall: cells 10..69 on both axes. */
    private static final int LO = 10, HI = 69;
    private static final int MID = (LO + HI) / 2;

    /** The south gate (main), the north gate and the east gate, each three cells wide on the wall line. */
    private static final DefenceDoctrine.Gate SOUTH = new DefenceDoctrine.Gate(MID, LO, 0, -1, 1);
    private static final DefenceDoctrine.Gate NORTH = new DefenceDoctrine.Gate(MID, HI, 0, 1, 1);
    private static final DefenceDoctrine.Gate EAST = new DefenceDoctrine.Gate(HI, MID, 1, 0, 1);

    private static boolean[][] inside() {
        boolean[][] in = new boolean[W][H];
        for (int x = LO; x <= HI; x++) for (int y = LO; y <= HI; y++) in[x][y] = true;
        return in;
    }

    /** The approaches, and a road across the place from west to east at y 30..32 as a street it keeps. */
    private static boolean[][] clear(List<DefenceDoctrine.Gate> gates) {
        boolean[][] clear = DefenceDoctrine.approaches(gates, W, H);
        for (int x = 0; x < W; x++) for (int y = 30; y <= 32; y++) clear[x][y] = true;
        return clear;
    }

    /**
     * The ground takes a post whose box and a cell of verge are off the kept ground, a standoff weapon's inside the
     * place and a direct-fire gun's outside it.
     */
    private static DefenceDoctrine.Fits fits(boolean[][] inside, boolean[][] clear) {
        return (x, y, kind, fx, fy) -> {
            boolean within = DefenceDoctrine.STANDOFF.contains(kind);
            int r = PrecinctDefence.reach(kind) + PrecinctDefence.VERGE;
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    int nx = x + dx, ny = y + dy;
                    if (nx < 0 || ny < 0 || nx >= W || ny >= H || inside[nx][ny] != within || clear[nx][ny]) {
                        return false;
                    }
                }
            }
            return true;
        };
    }

    private static Map<DefensePostKind, Integer> posts(Object... kindsAndCounts) {
        Map<DefensePostKind, Integer> posts = new EnumMap<>(DefensePostKind.class);
        for (int i = 0; i < kindsAndCounts.length; i += 2) {
            posts.put((DefensePostKind) kindsAndCounts[i], (Integer) kindsAndCounts[i + 1]);
        }
        return posts;
    }

    private static List<DefenceDoctrine.Site> plan(List<DefenceDoctrine.Gate> gates, List<int[]> hearts,
                                                   int[] threat, Map<DefensePostKind, Integer> posts) {
        boolean[][] inside = inside();
        return DefenceDoctrine.plan(inside, gates, hearts, threat, posts, fits(inside, clear(gates)));
    }

    @Test
    void noSiteStandsOnARoadOrAGatesApproach() {
        List<DefenceDoctrine.Gate> gates = List.of(SOUTH, NORTH, EAST);
        boolean[][] clear = clear(gates);
        List<DefenceDoctrine.Site> sites = plan(gates, List.of(new int[]{MID, MID}), null,
                posts(DefensePostKind.LARGE, 3, DefensePostKind.MEDIUM, 3, DefensePostKind.LIGHT, 6,
                        DefensePostKind.ARTILLERY, 1));
        assertFalse(sites.isEmpty());
        for (DefenceDoctrine.Site s : sites) {
            int r = PrecinctDefence.reach(s.kind());
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    assertFalse(clear[s.x() + dx][s.y() + dy],
                            s.role() + " " + s.kind() + " at " + s.x() + "," + s.y() + " stands on a road or approach");
                }
            }
        }
    }

    @Test
    void everyGateIsFlankedBeforeAnyGateGetsItsSecond() {
        List<DefenceDoctrine.Gate> gates = List.of(SOUTH, NORTH, EAST);
        List<DefenceDoctrine.Site> sites = plan(gates, List.of(), null, posts(DefensePostKind.LIGHT, 3));
        assertEquals(3, sites.size());
        boolean[] covered = new boolean[3];
        for (DefenceDoctrine.Site s : sites) {
            assertEquals(DefenceDoctrine.Role.GATE, s.role());
            covered[s.gate()] = true;
            DefenceDoctrine.Gate g = gates.get(s.gate());
            double d = Math.hypot(s.x() - g.x(), s.y() - g.y());
            assertTrue(d <= 14, "the post covering the gate at " + g.x() + "," + g.y() + " stands " + d + " off it");
            assertTrue((s.x() - g.x()) * g.outX() + (s.y() - g.y()) * g.outY() > 0, "and out in front of the wall");
        }
        assertTrue(covered[0] && covered[1] && covered[2], "three posts left a gate unwatched");
    }

    @Test
    void theHeaviestGunsHoldTheMainGate() {
        List<DefenceDoctrine.Site> sites = plan(List.of(SOUTH, NORTH), List.of(), null,
                posts(DefensePostKind.LARGE, 1, DefensePostKind.LIGHT, 3));
        assertEquals(DefensePostKind.LARGE, sites.get(0).kind());
        assertEquals(0, sites.get(0).gate(), "the heaviest post went to a gate other than the main one");
    }

    @Test
    void aBigLoadoutTakesTheCornersFacingTheThreatFirstAndSitesNoGunAtTheHearts() {
        int[] heart = {MID - 12, MID + 12};
        List<DefenceDoctrine.Site> sites = plan(List.of(SOUTH), List.of(heart), new int[]{W - 1, H - 1},
                posts(DefensePostKind.LIGHT, 7));
        List<DefenceDoctrine.Site> corners = new ArrayList<>();
        for (DefenceDoctrine.Site s : sites) {
            if (s.role() == DefenceDoctrine.Role.CORNER) corners.add(s);
            assertTrue(Math.hypot(s.x() - heart[0], s.y() - heart[1]) > 8,
                    s.role() + " at " + s.x() + "," + s.y() + " guards the motor pool from inside, covering nothing");
        }
        assertEquals(4, corners.size(), "a seven-post loadout on one gate leaves a post for every corner");
        assertTrue(corners.get(0).x() > MID && corners.get(0).y() > MID,
                "the first corner manned is not the one facing the threat to the north-east");
    }

    /**
     * A wall blocks direct fire whole, so every perimeter gun stands outside the place (its wall line included), and
     * its laid arc, its bearing and {@link DefenceDoctrine#ARC_DEGREES} either side, runs {@link DefenceDoctrine#LANE}
     * cells over open ground without crossing back over the place.
     */
    @Test
    void everyPerimeterGunHasALineOfFireOverOpenGroundBeyondTheWall() {
        List<DefenceDoctrine.Site> sites = plan(List.of(SOUTH, NORTH), List.of(), new int[]{W - 1, H - 1},
                posts(DefensePostKind.LARGE, 2, DefensePostKind.LIGHT, 12));
        boolean[][] inside = inside();
        assertTrue(sites.size() > 6, "the loadout was sited");
        for (DefenceDoctrine.Site s : sites) {
            assertFalse(inside[s.x()][s.y()], s.role() + " at " + s.x() + "," + s.y() + " stands behind the wall");
            double bearing = Math.atan2(s.faceY(), s.faceX());
            for (int turn = -1; turn <= 1; turn++) {
                double a = bearing + Math.toRadians(DefenceDoctrine.ARC_DEGREES * turn);
                for (int t = 1; t <= DefenceDoctrine.LANE; t++) {
                    int x = (int) Math.round(s.x() + Math.cos(a) * t), y = (int) Math.round(s.y() + Math.sin(a) * t);
                    if (x < 0 || y < 0 || x >= W || y >= H) break;
                    assertFalse(inside[x][y], s.role() + " at " + s.x() + "," + s.y() + " fires into the wall at "
                            + x + "," + y);
                }
            }
        }
    }

    @Test
    void standoffWeaponsStandDeepAndAwayFromTheMainGate() {
        List<DefenceDoctrine.Site> sites = plan(List.of(SOUTH), List.of(), null,
                posts(DefensePostKind.ARTILLERY, 1, DefensePostKind.DRONE_HUB, 1));
        assertEquals(2, sites.size());
        for (DefenceDoctrine.Site s : sites) {
            assertEquals(DefenceDoctrine.Role.DEPTH, s.role());
            assertTrue(s.y() > MID, s.kind() + " at " + s.x() + "," + s.y() + " stands on the main gate's half");
            int depth = Math.min(Math.min(s.x() - LO, HI - s.x()), Math.min(s.y() - LO, HI - s.y()));
            assertTrue(depth >= DefenceDoctrine.DEEP - 1, s.kind() + " stands " + depth + " cells inside the wall");
        }
    }

    /**
     * A grown claim is not a square: its corners are its own, not its bounding box's, and a loadout with posts to
     * spare lines the wall rather than going unplaced.
     */
    @Test
    void anLShapedPlaceStillTakesItsWholeLoadout() {
        boolean[][] inside = inside();
        for (int x = MID; x <= HI; x++) for (int y = MID; y <= HI; y++) inside[x][y] = false;   // the missing quarter
        boolean[][] clear = DefenceDoctrine.approaches(List.of(SOUTH), W, H);
        List<DefenceDoctrine.Site> sites = DefenceDoctrine.plan(inside, List.of(SOUTH), List.of(), null,
                posts(DefensePostKind.MEDIUM, 2, DefensePostKind.LIGHT, 6), fits(inside, clear));
        assertEquals(8, sites.size(), "an L-shaped place left posts unsited");
        for (DefenceDoctrine.Site s : sites) {
            assertFalse(inside[s.x()][s.y()], s.role() + " at " + s.x() + "," + s.y() + " stands behind the wall");
        }
    }

    @Test
    void groundThatTakesNothingGetsNothingRatherThanAScatter() {
        boolean[][] inside = inside();
        List<DefenceDoctrine.Site> sites = DefenceDoctrine.plan(inside, List.of(SOUTH), List.of(), null,
                posts(DefensePostKind.LIGHT, 4, DefensePostKind.ARTILLERY, 1), (x, y, kind, fx, fy) -> false);
        assertTrue(sites.isEmpty());
    }

    @Test
    void sitesKeepThePlacersSpacing() {
        List<DefenceDoctrine.Site> sites = plan(List.of(SOUTH, NORTH, EAST), List.of(new int[]{MID, MID}), null,
                posts(DefensePostKind.LARGE, 4, DefensePostKind.MEDIUM, 4, DefensePostKind.LIGHT, 4));
        for (int i = 0; i < sites.size(); i++) {
            for (int j = i + 1; j < sites.size(); j++) {
                double d = Math.hypot(sites.get(i).x() - sites.get(j).x(), sites.get(i).y() - sites.get(j).y());
                assertTrue(d >= DefenceDoctrine.SPACING, "two sites " + d + " apart: the placer refuses the second");
            }
        }
    }

    @Test
    void aGatesApproachRunsInAndOutAlongItsRoad() {
        DefenceDoctrine.Gate gate = new DefenceDoctrine.Gate(40, 30, 0, -1, 1);
        boolean[][] clear = DefenceDoctrine.approaches(List.of(gate), W, H);
        assertTrue(clear[40][30 - DefenceDoctrine.APPROACH_OUT], "the road out is not kept");
        assertTrue(clear[40][30 + DefenceDoctrine.APPROACH_IN], "the road in is not kept");
        assertTrue(clear[41 + DefenceDoctrine.APPROACH_VERGE][30], "the verge is not kept");
        assertFalse(clear[42 + DefenceDoctrine.APPROACH_VERGE][30], "the approach is wider than its road");
    }

    @Test
    void aGateKnowsWhichWayIsOut() {
        boolean[][] own = inside();
        List<int[]> cells = List.of(new int[]{HI, MID - 1}, new int[]{HI, MID}, new int[]{HI, MID + 1});
        DefenceDoctrine.Gate gate = DefenceDoctrine.gateOf(cells, own);
        assertNotNull(gate);
        assertEquals(1, gate.outX());
        assertEquals(0, gate.outY());
        assertEquals(HI, gate.x());
        assertEquals(MID, gate.y());
    }
}
