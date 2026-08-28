package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.world.gen.ship.CompanyShip;
import com.dillon.starsectormarines.battle.world.gen.ship.HullClass;
import com.dillon.starsectormarines.battle.world.gen.ship.HullRole;
import com.dillon.starsectormarines.battle.world.gen.ship.TestHulls;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Choosing where the company lives, before it lives anywhere and after.
 *
 * <p>Founding and transfer are the same screen asking the same question of a
 * hull — what will she not do for us — and these check it answers in both
 * states rather than only the one it was built for.
 */
class ShipTransferViewModelTest {

    private static final ShipTransferViewModel.Candidate TRANSPORT =
            new ShipTransferViewModel.Candidate("transport", "VALKYRIE",
                    "destroyer, troop transport", 240, 50, TestHulls.transport());
    private static final ShipTransferViewModel.Candidate LINER =
            new ShipTransferViewModel.Candidate("liner", "STARLINER",
                    "cruiser, liner", 1450, 300,
                    new CompanyShip(HullClass.CRUISER, HullRole.LINER,
                            60, 1510, 300, 0.44f));

    private static final List<ShipTransferViewModel.Candidate> FLEET =
            List.of(TRANSPORT, LINER);

    /** A company's standing with respect to quarters, as a mutable fixture. */
    private static final class Quarters {
        private CompanyShipDesignation.Home standing = CompanyShipDesignation.Home.NONE;

        CompanyShipDesignation.Home get() {
            return standing;
        }

        void moveAboard(String shipId) {
            standing = new CompanyShipDesignation.Home(shipId, null, false);
        }

        void displace(String formerShipName, boolean lostInAction) {
            standing = new CompanyShipDesignation.Home(null, formerShipName, lostInAction);
        }
    }

    /** A company that has not chosen: no home, and a transfer that records one. */
    private static ShipTransferViewModel founding(Reactor reactor) {
        Quarters quarters = new Quarters();
        return new ShipTransferViewModel(
                reactor, () -> FLEET, quarters::get, quarters::moveAboard);
    }

    @Test
    @DisplayName("a company with no ship is asked to found rather than to move")
    void foundingAsksToQuarterRatherThanToMove() {
        ShipTransferViewModel viewModel = founding(new Reactor());

        assertTrue(viewModel.roomTitle().get().contains("FOUNDING"));
        assertEquals("QUARTER THE COMPANY HERE", viewModel.transferLabel().get());
    }

    @Test
    @DisplayName("with nothing to compare against, a hull is judged on what it lacks")
    void foundingNamesWhatAHullDoesNotHave() {
        ShipTransferViewModel viewModel = founding(new Reactor());
        viewModel.select(LINER.id());

        // A liner berths the whole company and has nowhere to service a walker.
        String verdict = viewModel.verdict().get();
        assertTrue(verdict.contains("mech bay"),
                "a liner has no mech bay and the founding verdict should say so: " + verdict);

        String cost = row(viewModel, LINER.id()).cost();
        assertTrue(cost.startsWith("no "),
                "a founding row reports absence rather than loss: " + cost);
    }

    @Test
    @DisplayName("choosing gives the company a home, and the screen becomes a transfer")
    void choosingAShipTurnsFoundingIntoTransfer() {
        Reactor reactor = new Reactor();
        Quarters quarters = new Quarters();
        ShipTransferViewModel viewModel = new ShipTransferViewModel(
                reactor, () -> FLEET, quarters::get, quarters::moveAboard);

        viewModel.select(TRANSPORT.id());
        viewModel.commit();

        assertEquals(TRANSPORT.id(), quarters.get().shipId(), "the choice is recorded");
        assertEquals("THE COMPANY LIVES HERE", viewModel.transferLabel().get());
        assertTrue(viewModel.roomTitle().get().contains("TRANSFER"));
        assertEquals("HOME", row(viewModel, TRANSPORT.id()).cost());
    }

    @Test
    @DisplayName("once there is a home, a hull is judged on what leaving would cost")
    void transferNamesWhatAMoveWouldGiveUp() {
        Reactor reactor = new Reactor();
        Quarters quarters = new Quarters();
        quarters.moveAboard(TRANSPORT.id());
        ShipTransferViewModel viewModel = new ShipTransferViewModel(
                reactor, () -> FLEET, quarters::get, quarters::moveAboard);
        viewModel.select(LINER.id());

        assertTrue(viewModel.verdict().get().contains("give up"),
                "a transfer verdict is about the trade: " + viewModel.verdict().get());
        assertTrue(row(viewModel, LINER.id()).cost().startsWith("loses "));
    }

    @Test
    @DisplayName("the company is never moved onto the ship it is already on")
    void committingToTheCurrentShipDoesNothing() {
        Reactor reactor = new Reactor();
        Quarters quarters = new Quarters();
        quarters.moveAboard(TRANSPORT.id());
        ShipTransferViewModel viewModel = new ShipTransferViewModel(reactor, () -> FLEET,
                quarters::get, ship -> quarters.displace("SOMEWHERE ELSE", false));

        viewModel.select(TRANSPORT.id());
        viewModel.commit();

        assertEquals(TRANSPORT.id(), quarters.get().shipId(),
                "committing to home should not move anybody");
    }

    @Test
    @DisplayName("a fleet with nothing in it leaves the company nowhere rather than failing")
    void anEmptyFleetIsAnsweredRatherThanRefused() {
        ShipTransferViewModel viewModel = new ShipTransferViewModel(
                new Reactor(), List::of,
                () -> CompanyShipDesignation.Home.NONE, ship -> { });

        assertNull(viewModel.selectedPlan());
        assertTrue(viewModel.candidateRows().get().isEmpty());
        assertTrue(viewModel.facilityCells().get().isEmpty());
        assertFalse(viewModel.selectedSummary().get().isBlank());
    }

    @Test
    @DisplayName("a company that lost its ship is told so, and told she was lost")
    void aLostShipIsNamedAndDistinguishedFromOneLetGo() {
        Quarters quarters = new Quarters();
        quarters.displace("SABRE", true);
        ShipTransferViewModel viewModel = new ShipTransferViewModel(
                new Reactor(), () -> FLEET, quarters::get, quarters::moveAboard);

        assertTrue(viewModel.roomTitle().get().contains("DISPLACED"),
                "a company that had a home is not founding a company: "
                        + viewModel.roomTitle().get());
        String copy = viewModel.roomCopy().get();
        assertTrue(copy.startsWith("SABRE"), "she is named: " + copy);
        assertTrue(copy.contains("was lost"), "losing her is not selling her: " + copy);
        // Displaced or founding, there is nothing to measure a hull against.
        assertEquals("QUARTER THE COMPANY HERE", viewModel.transferLabel().get());
    }

    @Test
    @DisplayName("a ship let go is not reported as a casualty")
    void aShipLetGoReadsDifferentlyFromOneLost() {
        Quarters quarters = new Quarters();
        quarters.displace("SABRE", false);
        ShipTransferViewModel viewModel = new ShipTransferViewModel(
                new Reactor(), () -> FLEET, quarters::get, quarters::moveAboard);

        String copy = viewModel.roomCopy().get();
        assertTrue(copy.contains("is gone"), copy);
        assertFalse(copy.contains("was lost"), copy);
    }

    @Test
    @DisplayName("choosing again puts the loss behind them")
    void quarteringAgainClearsTheDisplacement() {
        Quarters quarters = new Quarters();
        quarters.displace("SABRE", true);
        ShipTransferViewModel viewModel = new ShipTransferViewModel(
                new Reactor(), () -> FLEET, quarters::get, quarters::moveAboard);

        viewModel.select(TRANSPORT.id());
        viewModel.commit();

        assertEquals(TRANSPORT.id(), quarters.get().shipId());
        assertFalse(quarters.get().displaced(), "they have a home again");
        assertTrue(viewModel.roomTitle().get().contains("TRANSFER"));
    }

    private static ShipTransferViewModel.CandidateRow row(
            ShipTransferViewModel viewModel, String shipId) {
        for (ShipTransferViewModel.CandidateRow row : viewModel.candidateRows().get()) {
            if (row.id().endsWith(":" + shipId)) return row;
        }
        throw new AssertionError("no row for " + shipId);
    }
}
