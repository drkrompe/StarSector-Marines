package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.DefenceStructureFact.Family;
import com.dillon.starsectormarines.battle.command.DefenceStructureFact.Kind;
import com.dillon.starsectormarines.battle.command.DefenceStructureTactics.Candidate;
import com.dillon.starsectormarines.battle.command.DefenceStructureTactics.Role;
import com.dillon.starsectormarines.battle.command.DefenceStructureTactics.Task;
import com.dillon.starsectormarines.battle.command.FortTactics.Pick;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The aspect choice as a unit: small hand-made pictures of one fort, asked of each side in turn. The sides are written
 * both ways round on purpose; nothing in the choice is special to attacker or defender.
 */
class FortTacticsTest {

    private static final int SIZE = 120;
    private static final Faction D = Faction.DEFENDER;
    private static final Faction M = Faction.MARINE;

    private static CommandTopology openTopology() {
        NavigationGrid grid = new NavigationGrid(SIZE, SIZE);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) grid.setWalkableFloor(x, y);
        }
        return CommandTopology.freeze(new BattleSimulation(grid, new CellTopology(SIZE, SIZE)));
    }

    private static DefenceStructureFact aspect(Kind kind, Family family, int x, int y, Faction holder, Faction original,
                                               boolean pressed) {
        return new DefenceStructureFact(kind, x, y, holder, original, pressed, 4, 50, 50, 1, family);
    }

    private static DefenceStructureFact bunker(Faction holder, Faction original, boolean pressed) {
        return aspect(Kind.FIRE_CONTROL, Family.BATTERY_FORT, 60, 40, holder, original, pressed);
    }

    private static DefenceStructureFact magazine(Faction holder, Faction original) {
        return aspect(Kind.FORT_MAGAZINE, Family.BATTERY_FORT, 80, 40, holder, original, false);
    }

    private static DefenceStructureFact batteryPlant(Faction holder, Faction original, boolean pressed) {
        return aspect(Kind.FORT_POWER, Family.BATTERY_FORT, 60, 70, holder, original, pressed);
    }

    private static DefenceStructureFact room(Faction holder, Faction original, boolean pressed) {
        return aspect(Kind.SHIELD_GENERATOR, Family.SHIELD_FORT, 60, 40, holder, original, pressed);
    }

    private static DefenceStructureFact plant(Faction holder, Faction original, boolean pressed) {
        return aspect(Kind.FORT_POWER, Family.SHIELD_FORT, 60, 70, holder, original, pressed);
    }

    private static Candidate squad(int id, int x, int y) {
        return new Candidate(id, x, y, null);
    }

    private static Candidate sapper(int id, int x, int y) {
        return new Candidate(id, x, y, null, true);
    }

    private static List<Kind> kinds(List<Pick> picks) {
        return picks.stream().map(p -> p.structure().kind()).toList();
    }

    // ------------------------------------------------------------------------------------------ battery fort

    @Test
    void anAttackerSilencesAHostileBatteryByTakingItsBunkerAndNothingElse() {
        List<Pick> picks = FortTactics.choose(M,
                List.of(bunker(D, D, false), magazine(D, D), batteryPlant(D, D, false)), List.of(squad(1, 10, 40)));
        assertEquals(List.of(Kind.FIRE_CONTROL), kinds(picks), "the bunker is the guns' command: taking it silences and keeps them");
        assertEquals(Role.TAKE, picks.get(0).role());
        assertFalse(picks.get(0).wreck());
        assertTrue(picks.get(0).reason().contains("bunker"), picks.get(0).reason());
    }

    @Test
    void aSapperWrecksTheMagazineOnlyWhenTheBunkerIsMuchDeeperAndOnlyWithDemolition() {
        // The magazine stands on the squad's doorstep; the bunker is far past it.
        DefenceStructureFact deep = aspect(Kind.FIRE_CONTROL, Family.BATTERY_FORT, 100, 40, D, D, false);
        DefenceStructureFact near = aspect(Kind.FORT_MAGAZINE, Family.BATTERY_FORT, 20, 40, D, D, false);
        List<Pick> sappers = FortTactics.choose(M, List.of(deep, near), List.of(sapper(1, 10, 40)));
        assertEquals(List.of(Kind.FORT_MAGAZINE), kinds(sappers));
        assertTrue(sappers.get(0).wreck());
        List<Pick> riflemen = FortTactics.choose(M, List.of(deep, near), List.of(squad(1, 10, 40)));
        assertEquals(List.of(Kind.FIRE_CONTROL), kinds(riflemen), "no demolition, so it takes the bunker however deep");
        assertFalse(riflemen.get(0).wreck());
        // A bunker that is only a little deeper is not too costly.
        DefenceStructureFact close = aspect(Kind.FIRE_CONTROL, Family.BATTERY_FORT, 40, 40, D, D, false);
        assertEquals(List.of(Kind.FIRE_CONTROL), kinds(FortTactics.choose(M, List.of(close, near), List.of(sapper(1, 10, 40)))));
    }

    @Test
    void onceTheGunsAreOursTheAttackerTakesThePlantThenTheMagazineToMakeThemShootProperly() {
        List<Pick> picks = FortTactics.choose(M,
                List.of(bunker(M, D, false), magazine(D, D), batteryPlant(D, D, false)), List.of(squad(1, 10, 40), squad(2, 10, 50)));
        assertEquals(List.of(Kind.FORT_POWER, Kind.FORT_MAGAZINE), kinds(picks), "plant before magazine");
        assertTrue(picks.stream().allMatch(p -> p.role() == Role.TAKE && !p.wreck()));
        // With the plant and magazine already ours there is nothing left to do.
        assertTrue(FortTactics.choose(M, List.of(bunker(M, D, false), magazine(M, D), batteryPlant(M, D, false)),
                List.of(squad(1, 10, 40))).isEmpty());
    }

    @Test
    void aDefenderWhoLostTheBunkerRetakesItBeforeAnythingElse() {
        List<Pick> picks = FortTactics.choose(D,
                List.of(bunker(M, D, false), magazine(M, D), batteryPlant(M, D, false)), List.of(squad(1, 10, 40)));
        assertEquals(List.of(Kind.FIRE_CONTROL), kinds(picks));
        assertEquals(Role.RETAKE, picks.get(0).role());
        // Had the bunker held and only the plant fallen, the plant is what is retaken.
        List<Pick> plantOnly = FortTactics.choose(D,
                List.of(bunker(D, D, false), magazine(D, D), batteryPlant(M, D, false)), List.of(squad(1, 10, 40)));
        assertEquals(List.of(Kind.FORT_POWER), kinds(plantOnly));
        assertEquals(Role.RETAKE, plantOnly.get(0).role());
    }

    @Test
    void aDefenderWeightsTheGarrisonToTheAspectUnderThreat() {
        List<DefenceStructureFact> calm = List.of(bunker(D, D, false), magazine(D, D), batteryPlant(D, D, false));
        assertTrue(FortTactics.choose(D, calm, List.of(squad(1, 10, 40), squad(2, 10, 50))).isEmpty(),
                "nothing pressed, nothing to stand to");
        List<DefenceStructureFact> oneAxis = List.of(bunker(D, D, true), magazine(D, D), batteryPlant(D, D, false));
        List<Pick> picks = FortTactics.choose(D, oneAxis, List.of(squad(1, 10, 40), squad(2, 10, 50)));
        assertEquals(1, picks.size());
        assertEquals(Role.DEFEND, picks.get(0).role());
        assertEquals(2, picks.get(0).squads(), "the single axis of assault draws a second squad");
        assertEquals(1, FortTactics.choose(D, oneAxis, List.of(squad(1, 10, 40))).get(0).squads(), "one squad is all there is");
        List<DefenceStructureFact> twoAxes = List.of(bunker(D, D, true), magazine(D, D), batteryPlant(D, D, true));
        List<Pick> split = FortTactics.choose(D, twoAxes, List.of(squad(1, 10, 40), squad(2, 10, 50)));
        assertEquals(List.of(Kind.FIRE_CONTROL, Kind.FORT_POWER), kinds(split), "most critical first, one squad each");
        assertTrue(split.stream().allMatch(p -> p.squads() == 1));
    }

    // ------------------------------------------------------------------------------------------ shield fort

    @Test
    void anAttackerCutsThePlantFirstAndAddsTheControlRoomOnlyWhenTwoSquadsCanHoldBoth() {
        List<DefenceStructureFact> fort = List.of(room(D, D, false), plant(D, D, false));
        List<Pick> lone = FortTactics.choose(M, fort, List.of(squad(1, 10, 40)));
        assertEquals(List.of(Kind.FORT_POWER), kinds(lone), "one squad: the quickest dome-down is the plant");
        assertFalse(lone.get(0).wreck(), "no demolition, so it takes it");
        List<Pick> pair = FortTactics.choose(M, fort, List.of(squad(1, 10, 40), squad(2, 10, 50)));
        assertEquals(List.of(Kind.FORT_POWER, Kind.SHIELD_GENERATOR), kinds(pair), "two squads: the plant, then the dome itself");
    }

    @Test
    void aLoneSapperWrecksThePlantBecauseTakingAndLeavingItHandsItBack() {
        List<Pick> picks = FortTactics.choose(M, List.of(room(D, D, false), plant(D, D, false)),
                List.of(sapper(1, 10, 40)));
        assertEquals(List.of(Kind.FORT_POWER), kinds(picks));
        assertTrue(picks.get(0).wreck());
        // Two squads can hold the fort, so capture is preferred: a wrecked plant is gone for both sides.
        List<Pick> pair = FortTactics.choose(M, List.of(room(D, D, false), plant(D, D, false)),
                List.of(sapper(1, 10, 40), squad(2, 10, 50)));
        assertTrue(pair.stream().noneMatch(Pick::wreck));
    }

    @Test
    void withThePlantOursTheLoneSquadGoesOnToTheControlRoom() {
        List<Pick> picks = FortTactics.choose(M, List.of(room(D, D, false), plant(M, D, false)), List.of(squad(1, 10, 40)));
        assertEquals(List.of(Kind.SHIELD_GENERATOR), kinds(picks));
        assertTrue(picks.get(0).reason().contains("plant is ours"), picks.get(0).reason());
        // The dome is already ours (room and plant both): nothing remains.
        assertTrue(FortTactics.choose(M, List.of(room(M, D, false), plant(M, D, false)), List.of(squad(1, 10, 40))).isEmpty());
    }

    @Test
    void aDefenderRetakesThePlantBeforeTheControlRoom() {
        List<DefenceStructureFact> bothLost = List.of(room(M, D, false), plant(M, D, false));
        List<Pick> picks = FortTactics.choose(D, bothLost, List.of(squad(1, 10, 40), squad(2, 10, 50)));
        assertEquals(List.of(Kind.FORT_POWER, Kind.SHIELD_GENERATOR), kinds(picks));
        assertTrue(picks.stream().allMatch(p -> p.role() == Role.RETAKE));
        assertEquals(List.of(Kind.FORT_POWER), kinds(FortTactics.choose(D, bothLost, List.of(squad(1, 10, 40)))),
                "one squad: the plant first");
        // A pressed plant is stood to at before a pressed control room.
        List<Pick> pressed = FortTactics.choose(D, List.of(room(D, D, true), plant(D, D, true)),
                List.of(squad(1, 10, 40), squad(2, 10, 50)));
        assertEquals(List.of(Kind.FORT_POWER, Kind.SHIELD_GENERATOR), kinds(pressed));
        assertTrue(pressed.stream().allMatch(p -> p.role() == Role.DEFEND));
    }

    @Test
    void theSameFortIsTheSameChoiceWithTheSidesSwapped() {
        // The marines built it and the garrison attacks: the mirror image of the attacker case above.
        List<Pick> picks = FortTactics.choose(D, List.of(room(M, M, false), plant(M, M, false)), List.of(squad(1, 10, 40)));
        assertEquals(List.of(Kind.FORT_POWER), kinds(picks));
        assertEquals(Role.TAKE, picks.get(0).role());
    }

    // ------------------------------------------------------------------------------------------ the planner

    @Test
    void thePlannerSendsASquadToTheChosenAspectAndSaysWhy() {
        CommandTopology topology = openTopology();
        List<Task> tasks = DefenceStructureTactics.plan(M,
                List.of(room(D, D, false), plant(D, D, false)),
                List.of(squad(1, 20, 40), squad(2, 20, 70)), topology);
        assertEquals(2, tasks.size(), "two squads can hold both aspects, so both are worked");
        Task first = tasks.get(0);
        assertEquals(2, first.squadId(), "the plant comes first and the nearer squad takes it");
        assertTrue(first.reason().contains("shield_fort") && first.reason().contains("plant"), first.reason());
        int gap = Math.max(Math.abs(first.assignment().targetCellX() - 60), Math.abs(first.assignment().targetCellY() - 70));
        assertTrue(gap <= 4, "ordered to within the plant's reach, was " + gap);
    }

    @Test
    void aSingleAxisOfAssaultDrawsTwoSquadsToTheSameAspect() {
        CommandTopology topology = openTopology();
        List<Task> tasks = DefenceStructureTactics.plan(D,
                List.of(bunker(D, D, true), magazine(D, D), batteryPlant(D, D, false)),
                List.of(squad(1, 30, 40), squad(2, 30, 45), squad(3, 30, 90)), topology);
        assertEquals(DefenceStructureTactics.MAX_TASKS, tasks.size());
        assertTrue(tasks.stream().allMatch(t -> t.role() == Role.DEFEND && t.structure().kind() == Kind.FIRE_CONTROL));
        assertEquals(2, tasks.stream().map(Task::squadId).distinct().count());
    }

    @Test
    void aSapperIsTheSquadSentToWreckAndTheTaskSaysSo() {
        CommandTopology topology = openTopology();
        DefenceStructureFact deep = aspect(Kind.FIRE_CONTROL, Family.BATTERY_FORT, 100, 40, D, D, false);
        DefenceStructureFact near = aspect(Kind.FORT_MAGAZINE, Family.BATTERY_FORT, 20, 40, D, D, false);
        List<Task> tasks = DefenceStructureTactics.plan(M, List.of(deep, near),
                List.of(squad(1, 12, 40), sapper(2, 10, 40)), topology);
        assertEquals(1, tasks.size());
        assertEquals(2, tasks.get(0).squadId(), "only a demolition squad can be sent to wreck");
        assertTrue(tasks.get(0).wreck());
    }

    @Test
    void aBareStructureAndAFortAreChosenForTogetherByRole() {
        CommandTopology topology = openTopology();
        DefenceStructureFact bare = new DefenceStructureFact(Kind.SHIELD_GENERATOR, 100, 100, D, D, false, 6);
        List<Task> tasks = DefenceStructureTactics.plan(M, List.of(bare, bunker(D, D, false)),
                List.of(squad(1, 90, 100), squad(2, 50, 40)), topology);
        assertEquals(2, tasks.size());
        assertTrue(tasks.stream().anyMatch(t -> t.structure().kind() == Kind.FIRE_CONTROL && t.squadId() == 2));
        assertTrue(tasks.stream().anyMatch(t -> t.structure() == bare && t.squadId() == 1));
    }
}
