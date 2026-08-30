package com.dillon.starsectormarines.battle.world.gen.fit.layout;

import com.dillon.starsectormarines.battle.world.gen.fit.Hookup;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomFitting;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomFloor;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomShape;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

import java.util.List;

/**
 * A {@link RoomFitting} that replays an authored {@link RoomLayout}.
 *
 * <p>The whole of the adapter. An authored room is not a second kind of room
 * with a second path into the deck: it arrives through the same interface the
 * procedural fittings implement, is handed the same {@link RoomFloor}, and is
 * judged by the same seal check afterwards. Whatever guards a programmed
 * arrangement guards this one.
 */
public record AuthoredFitting(RoomLayout layout) implements RoomFitting {

    @Override
    public RoomPurpose purpose() {
        return layout.purpose();
    }

    @Override
    public void fit(RoomFloor floor) {
        LayoutOp.Replay replay = new LayoutOp.Replay();
        for (LayoutOp op : layout.ops()) {
            op.apply(floor, replay);
        }
    }

    @Override
    public List<Hookup> hookups(RoomShape canonical) {
        return layout.hookups();
    }

    @Override
    public boolean handed() {
        return layout.handed();
    }
}
