package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.world.gen.ship.CompanyShip;
import com.dillon.starsectormarines.battle.world.gen.ship.HullClass;
import com.dillon.starsectormarines.battle.world.gen.ship.HullRole;
import com.dillon.starsectormarines.ops.battleview.LaidDecks;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How often the transfer screen reads the fleet it is comparing.
 *
 * <p>Reading it is expensive and reading it is not the point: a ship with
 * battle damage is read as she would be without it, which clones her variant
 * and builds a throwaway member to ask the game what that comes to. The screen
 * carries a dozen signals and most of them want to know which ship is selected
 * or which one is home, so a fleet read per question is a fleet read per label.
 */
class ShipTransferReadingTest {

    private static final CompanyShipDesignation.Home FOUNDING =
            CompanyShipDesignation.Home.NONE;

    /** Small hulls: this is about how often they are read, not what is aboard them. */
    private static List<ShipTransferViewModel.Candidate> fleet() {
        List<ShipTransferViewModel.Candidate> ships = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            ships.add(new ShipTransferViewModel.Candidate(
                    "ship-" + index, "SHIP " + index, "cruiser, transport",
                    30, 40, new CompanyShip(HullClass.CRUISER, HullRole.TROOP_TRANSPORT,
                            20, 50, 30, 0.5f)));
        }
        return List.copyOf(ships);
    }

    /**
     * What the shell remembers outlives a screen, and these hulls are the same
     * hulls in every one of these tests — so a screen that opened a moment ago
     * would hand the next one its answers, and the row that should say it is
     * still laying a deck out would already know.
     */
    @BeforeEach
    void freshStart() {
        LaidDecks.forget();
    }

    @Test
    @DisplayName("the fleet is read once however many questions the screen asks")
    void readsTheFleetOncePerRevision() {
        AtomicInteger reads = new AtomicInteger();
        List<ShipTransferViewModel.Candidate> ships = fleet();
        Reactor reactor = new Reactor();
        ShipTransferViewModel model = new ShipTransferViewModel(reactor,
                () -> { reads.incrementAndGet(); return ships; },
                () -> FOUNDING, moved -> { }, CompanyMeans.of(20, 1, 100_000));

        model.candidateRows().get();
        model.facilityCells().get();
        model.selectedName().get();
        model.selectedSummary().get();
        model.verdict().get();
        model.transferLabel().get();
        model.costLabel().get();

        assertEquals(1, reads.get(),
                "the whole screen should cost one reading of the fleet");
    }

    @Test
    @DisplayName("a row says it is being laid out, and then says what is aboard")
    void rowsFillInWhenTheirDecksLand() {
        List<ShipTransferViewModel.Candidate> ships = fleet();
        ShipTransferViewModel model = new ShipTransferViewModel(new Reactor(),
                () -> ships, () -> FOUNDING, moved -> { },
                CompanyMeans.of(20, 1, 100_000));

        List<ShipTransferViewModel.CandidateRow> opening = model.candidateRows().get();
        assertEquals(ships.size(), opening.size(),
                "every ship is listed the moment the screen opens");
        assertTrue(opening.stream().allMatch(row -> row.detail().contains("laying out")),
                "and none of them claims to know what is aboard yet");

        while (model.reading()) {
            if (model.advance()) model.candidateRows().get();
            else Thread.onSpinWait();
        }

        List<ShipTransferViewModel.CandidateRow> read = model.candidateRows().get();
        assertTrue(read.stream().allMatch(row -> row.detail().contains("berth")),
                "once the decks land every row says what she berths: "
                        + read.stream().map(ShipTransferViewModel.CandidateRow::detail)
                                .toList());

        // The panel is thrown away and rebuilt on every visit, so the second one
        // is a different screen over the same fleet. It should not wait.
        ShipTransferViewModel returning = new ShipTransferViewModel(new Reactor(),
                () -> ships, () -> FOUNDING, moved -> { },
                CompanyMeans.of(20, 1, 100_000));
        List<ShipTransferViewModel.CandidateRow> again = returning.candidateRows().get();

        assertTrue(again.stream().allMatch(row -> row.detail().contains("berth")),
                "coming back should cost nothing for a fleet already read: "
                        + again.stream().map(ShipTransferViewModel.CandidateRow::detail)
                                .toList());
    }

    @Test
    @DisplayName("a fleet that may have changed is read again")
    void readsAgainAfterRefresh() {
        AtomicInteger reads = new AtomicInteger();
        List<ShipTransferViewModel.Candidate> ships = fleet();
        Reactor reactor = new Reactor();
        ShipTransferViewModel model = new ShipTransferViewModel(reactor,
                () -> { reads.incrementAndGet(); return ships; },
                () -> FOUNDING, moved -> { }, CompanyMeans.of(20, 1, 100_000));
        model.candidateRows().get();

        model.refresh();
        model.candidateRows().get();

        assertEquals(2, reads.get());
    }
}
