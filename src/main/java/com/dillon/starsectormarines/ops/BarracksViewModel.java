package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.EquipmentIssueResources;
import com.dillon.starsectormarines.marine.MarinePersonnelLogistics.PersonnelDrawResult;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.SquadFoundingResources;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;

import java.util.function.DoubleSupplier;
import java.util.function.Function;
import java.util.function.IntSupplier;

/** The Barracks page's view model: the commander board over persistent squads. */
public final class BarracksViewModel {

    private final BarracksCommandModel command;

    public BarracksViewModel(Reactor reactor, MarineRoster roster,
                             DoubleSupplier currentDay) {
        this(reactor, roster, currentDay, EquipmentIssueResources.UNLIMITED, () -> 0,
                id -> new PersonnelDrawResult(roster.fillVacanciesFromReserve(id), 0));
    }

    public BarracksViewModel(Reactor reactor, MarineRoster roster, DoubleSupplier currentDay,
                            EquipmentIssueResources resources, IntSupplier cargoMarines,
                            Function<String, PersonnelDrawResult> reinforce) {
        this(reactor, roster, currentDay, resources, SquadFoundingResources.NONE, cargoMarines, reinforce);
    }

    public BarracksViewModel(Reactor reactor, MarineRoster roster, DoubleSupplier currentDay,
                            EquipmentIssueResources resources, SquadFoundingResources foundingResources,
                            IntSupplier cargoMarines, Function<String, PersonnelDrawResult> reinforce) {
        if (reactor == null) throw new IllegalArgumentException("reactor is required");
        if (roster == null) throw new IllegalArgumentException("roster is required");
        if (currentDay == null) throw new IllegalArgumentException("currentDay is required");
        command = new BarracksCommandModel(reactor, roster, resources, foundingResources, cargoMarines, reinforce,
                currentDay);
    }

    public BarracksCommandModel command() { return command; }

    public void refresh() { command.refresh(); }
}
