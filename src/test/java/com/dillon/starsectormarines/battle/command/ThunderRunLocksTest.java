package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.compound.CompoundService.CompoundState;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.ThunderRunCompound;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Thunder Run's militia holds re-expressed as locked positions (`garrisonables.md`, locked positions;
 * {@link ThunderRunCommand#militiaLocks}): over random histories of the four sites, the lock each detachment holds is the
 * site the old public-territory rule chose ({@link RelayNineTactics#militiaTarget}), on the same post
 * ({@link ThunderRunCompound#militiaPost}), with the same reason.
 */
class ThunderRunLocksTest {

    private static final CompoundState[] STATES = {CompoundState.MARINE_HELD, CompoundState.DEFENDER_HELD,
            CompoundState.CONTESTED};

    @Test
    void locksChooseWhatTheMilitiaRuleChose() {
        Random random = new Random(41);
        for (int history = 0; history < 200; history++) {
            RelayNineTactics tactics = new RelayNineTactics();
            PlaceDirectives book = PlaceDirectives.forParty(ThunderRunCommand.militiaLocks(), Faction.ALLY);
            for (int pulse = 0; pulse < 12; pulse++) {
                List<CompoundState> states = new ArrayList<>();
                states.add(CompoundState.MARINE_HELD);
                // Mostly unchanged pulse to pulse, as territory is; now and then a site turns.
                for (int site = 1; site < 4; site++) {
                    states.add(random.nextInt(4) == 0 ? STATES[random.nextInt(STATES.length)] : CompoundState.DEFENDER_HELD);
                }
                tactics.observe(states);
                PlaceSignals signals = PlaceSignals.at(pulse * 75);
                for (int i = 1; i < 4; i++) signals.flag(ThunderRunCommand.gained(i), tactics.gained(i));
                book.evaluate(signals);
                for (int detachment = 0; detachment < 6; detachment++) {
                    PlaceDirectives.Lock lock = book.lockFor(detachment);
                    assertNotNull(lock, "a militia lock is always active");
                    int site = tactics.militiaTarget(detachment);
                    assertEquals(site, lock.directive().key(), "history " + history + " pulse " + pulse + " detachment " + detachment);
                    ThunderRunCompound.Cell post = ThunderRunCompound.militiaPost(site, detachment);
                    assertEquals(new PlaceDirective.Post(post.x(), post.y()), lock.post());
                    assertEquals(site == 0 ? "FREIGHT_COVER_LANDING" : "FREIGHT_HOLD_GAIN", lock.directive().reason());
                    assertEquals(5, lock.directive().radius());
                }
            }
        }
    }
}
