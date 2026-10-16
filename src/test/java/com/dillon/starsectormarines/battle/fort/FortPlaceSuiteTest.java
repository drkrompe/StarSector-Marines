package com.dillon.starsectormarines.battle.fort;

import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.fortress.BatteryFort;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortPlan;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortRoom;
import com.dillon.starsectormarines.battle.world.gen.fortress.ShieldFort;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An aspect's place is the floors of its suite, not a point and not a room recovered from the map: every room of the
 * suite is held ground, the walls and doors are not, and a hostile body in the ante-room alone is in the place. Asked of
 * {@link Fort.Place} and {@link FortFrame} directly, at every turn the fort is stamped at.
 */
class FortPlaceSuiteTest {

    private static final List<FortPlan> PLANS = List.of(BatteryFort.PLAN, ShieldFort.PLAN);

    private static Fort.Place resolved(FortPlan plan, FortFrame frame, FortAspect aspect) {
        Fort.Place place = new Fort.Place(aspect, 1L, Faction.DEFENDER);
        int[] core = frame.cell(plan.coreCentre(aspect)[0], plan.coreCentre(aspect)[1]);
        place.resolveSuite(frame, core[0], core[1]);
        return place;
    }

    @Test
    void thePlaceIsTheUnionOfTheSuitesFloorsAtEveryTurn() {
        for (FortPlan plan : PLANS) {
            for (int turn = 0; turn < 4; turn++) {
                FortFrame frame = new FortFrame(plan, 60, 40, turn);
                for (FortAspect aspect : plan.aspects()) {
                    Fort.Place place = resolved(plan, frame, aspect);
                    int area = 0;
                    for (int[] f : plan.suite(aspect)) {
                        area += f[2] * f[3];
                        for (int x = 0; x < f[2]; x++) {
                            for (int y = 0; y < f[3]; y++) {
                                int[] cell = frame.cell(f[0] + x, f[1] + y);
                                assertTrue(place.contains(cell[0], cell[1]),
                                        plan.kind() + " " + aspect + " floor cell held at turn " + turn);
                            }
                        }
                    }
                    assertEquals(area, place.interiorCells(), plan.kind() + " " + aspect + ": exactly the suite's floors");
                }
            }
        }
    }

    @Test
    void anAnteRoomIsPartOfThePlaceSoAHostileBodyThereContestsIt() {
        for (FortPlan plan : PLANS) {
            for (FortAspect aspect : plan.aspects()) {
                List<FortRoom> suite = plan.rooms().stream().filter(r -> r.aspect() == aspect).toList();
                FortFrame frame = new FortFrame(plan, 60, 40, 1);
                Fort.Place place = resolved(plan, frame, aspect);
                for (FortRoom room : suite) {
                    int[] cell = frame.cell(room.x() + room.width() / 2, room.y() + room.height() / 2);
                    assertTrue(place.contains(cell[0], cell[1]), plan.kind() + " " + aspect + " " + room.purpose() + " is held ground");
                }
            }
        }
    }

    @Test
    void theDoorsAndAnotherAspectsFloorsAreNotThePlace() {
        for (FortPlan plan : PLANS) {
            FortFrame frame = new FortFrame(plan, 60, 40, 2);
            for (FortAspect aspect : plan.aspects()) {
                Fort.Place place = resolved(plan, frame, aspect);
                for (FortRoom room : plan.rooms()) {
                    if (room.aspect() != aspect) continue;
                    for (int[] door : room.doorCells()) {
                        int[] cell = frame.cell(door[0], door[1]);
                        assertFalse(place.contains(cell[0], cell[1]), "a door is the way in, not the room");
                    }
                }
                for (FortAspect other : plan.aspects()) {
                    if (other == aspect) continue;
                    for (int[] f : plan.suite(other)) {
                        int[] cell = frame.cell(f[0] + f[2] / 2, f[1] + f[3] / 2);
                        assertFalse(place.contains(cell[0], cell[1]), aspect + " does not take in " + other);
                    }
                }
            }
        }
    }

    @Test
    void theAntiAirControlRoomIsItsOwnPlaceOfItsFloorAlone() {
        for (FortPlan plan : PLANS) {
            FortFrame frame = new FortFrame(plan, 60, 40, 3);
            Fort.Place place = resolved(plan, frame, FortAspect.AIR_DEFENCE);
            int[] floor = plan.floor(FortAspect.AIR_DEFENCE);
            assertEquals(floor[2] * floor[3], place.interiorCells());
            for (int[] f : plan.airDefence().closeInCells()) {
                int[] cell = frame.cell(f[0], f[1]);
                assertFalse(place.contains(cell[0], cell[1]), "the close-in tower stands outside the room");
            }
        }
    }
}
