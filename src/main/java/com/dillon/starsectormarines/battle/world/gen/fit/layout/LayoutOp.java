package com.dillon.starsectormarines.battle.world.gen.fit.layout;

import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.Gantry;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomFloor;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;

/**
 * One authored step in furnishing a room, in the room's canonical frame.
 *
 * <p>A layout is a recorded script of {@link RoomFloor} calls rather than a
 * picture of a finished room, and this is its instruction set. That shape is
 * deliberate: replaying through the same API a procedural fitting uses means an
 * authored room passes through the same guards — lanes are unfurnishable, a
 * fixture is placed entire or not at all, a fill that seals its room is thrown
 * away — instead of getting a second, laxer path into the deck.
 *
 * <p><b>Order is meaning.</b> The steps run in the order they were authored, so
 * a lane declared after the fixture standing on it is simply not a lane, and
 * paving laid after a fixture is paving laid over it. Nothing re-sorts them.
 */
public sealed interface LayoutOp {

    /** Apply this step to a room that has been laid down at some pose. */
    void apply(RoomFloor floor, Replay replay);

    /**
     * What a replay has to remember between steps.
     *
     * <p>Only berths need it, and they need it for a real reason: work done on a
     * berthed machine names the berth it serves, and a berth's identity is an
     * index the floor hands back rather than anything the document can know. A
     * layout therefore numbers its own berths from zero and the replay maps
     * those onto whatever indices this particular deck assigns — the alternative
     * is a document whose task points are only valid on a deck with no other
     * machine bays on it.
     */
    final class Replay {

        private final java.util.List<Integer> berths = new java.util.ArrayList<>();

        void berthed(int index) {
            berths.add(index);
        }

        /** The index this deck gave the layout's {@code ordinal}-th berth, or -1. */
        int berth(int ordinal) {
            return ordinal >= 0 && ordinal < berths.size() ? berths.get(ordinal) : -1;
        }
    }

    /**
     * Circulation, reserved against furniture. Authored first in practice,
     * because that is what keeps a furnished room walkable from its door.
     */
    record Lane(int x, int y, int spanX, int spanY) implements LayoutOp {
        @Override
        public void apply(RoomFloor floor, Replay replay) {
            int[] rect = floor.toLocalRect(x, y, spanX, spanY);
            floor.reserveLane(rect[0], rect[1], rect[2], rect[3]);
        }
    }

    /**
     * Deck kept clear of people rather than for them — a beaten zone, not a
     * lane. Shut to movement and see-through, so rounds still cross it.
     */
    record Closed(int x, int y, int spanX, int spanY) implements LayoutOp {
        @Override
        public void apply(RoomFloor floor, Replay replay) {
            int[] rect = floor.toLocalRect(x, y, spanX, spanY);
            floor.closeOff(rect[0], rect[1], rect[2], rect[3]);
        }
    }

    /** A run of floor marked as a different kind of ground. Real topology, not decoration. */
    record Ground(int x, int y, int spanX, int spanY, GroundKind kind) implements LayoutOp {
        @Override
        public void apply(RoomFloor floor, Replay replay) {
            int[] rect = floor.toLocalRect(x, y, spanX, spanY);
            floor.markGround(rect[0], rect[1], rect[2], rect[3], kind);
        }
    }

    /** Floor covering, which does not claim its cell — something may still stand on it. */
    record Paving(int x, int y, String doodadId) implements LayoutOp {
        @Override
        public void apply(RoomFloor floor, Replay replay) {
            int[] cell = floor.toLocal(x, y);
            floor.pave(cell[0], cell[1], doodadId);
        }
    }

    /**
     * One fixture, and the work done at it when it affords any.
     *
     * <p>Only the anchor cell is carried through the pose. A doodad's own
     * footprint does not turn with the room — that is the standing convention
     * every procedural fitting already follows — so a multi-cell prop authored
     * across the beam occupies the same two cells along it once the room is
     * quarter-turned, and simply fails to go down where that is not free.
     * {@link RoomLayout#posesThatFit} is what reports it before it is saved.
     */
    record Fixture(int x, int y, String doodadId, Affordance affordance) implements LayoutOp {
        @Override
        public void apply(RoomFloor floor, Replay replay) {
            int[] cell = floor.toLocal(x, y);
            if (affordance == null) {
                floor.place(doodadId, cell[0], cell[1]);
            } else {
                floor.place(doodadId, cell[0], cell[1], affordance);
            }
        }
    }

    /**
     * Work at a cell the layout chose itself, rather than beside a fixture.
     *
     * <p>For the arrangements where the room's geometry decides where somebody
     * stands: a technician works on a berthed machine from the mouth of the bay,
     * not from inside the frame run down its side.
     */
    record Task(int x, int y, Affordance affordance, int fixtureX, int fixtureY)
            implements LayoutOp {
        @Override
        public void apply(RoomFloor floor, Replay replay) {
            int[] stand = floor.toLocal(x, y);
            int[] fixture = floor.toLocal(fixtureX, fixtureY);
            floor.fixtureTask(stand[0], stand[1], affordance, fixture[0], fixture[1]);
        }
    }

    /**
     * A machine berth: floor reserved for something the host parks there, not
     * for anything the map owns.
     *
     * <p>The facing turns with the room. A berth is the one authored thing here
     * with a direction, and a flipped bay whose machines still faced the old way
     * would be the mirror defect {@code RoomPose} exists to prevent.
     */
    record Berth(int x, int y, int spanX, int spanY, Gantry.Facing facing) implements LayoutOp {
        @Override
        public void apply(RoomFloor floor, Replay replay) {
            int[] rect = floor.toLocalRect(x, y, spanX, spanY);
            replay.berthed(floor.berth(rect[0], rect[1], rect[2], rect[3], turned(floor)));
        }

        private Gantry.Facing turned(RoomFloor floor) {
            int[] heading = floor.pose().mapDirection(facing.dx, facing.dy);
            for (Gantry.Facing candidate : Gantry.Facing.values()) {
                if (candidate.dx == heading[0] && candidate.dy == heading[1]) return candidate;
            }
            return facing;
        }
    }

    /**
     * Work done on whatever the host parks in one of this layout's berths.
     *
     * <p>Distinct from {@link Task} because the thing being worked on is not a
     * fixture the map owns: a technician services the machine standing in the
     * bay, and the bay may be empty. {@code berth} numbers this layout's own
     * berths from zero, in the order they are declared.
     */
    record BerthTask(int x, int y, int berth, int fixtureX, int fixtureY) implements LayoutOp {
        @Override
        public void apply(RoomFloor floor, Replay replay) {
            int index = replay.berth(berth);
            if (index < 0) return;
            int[] stand = floor.toLocal(x, y);
            int[] fixture = floor.toLocal(fixtureX, fixtureY);
            floor.berthFixtureTask(stand[0], stand[1], index, fixture[0], fixture[1]);
        }
    }
}
