package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Trusted projection of authored opening-operation places into a side frame. */
public final class OpeningOperationCommandDisclosure
        implements CommandFrameDisclosure<OpeningOperationCommandFrame> {
    private final OpeningOperationCommandFacts facts;

    private final BooleanSupplier warehouseCaptured;
    public OpeningOperationCommandDisclosure(OpeningOperationCommandFacts facts) { this(facts, facts::warehouseCaptured); }
    public OpeningOperationCommandDisclosure(OpeningOperationCommandFacts facts, BooleanSupplier warehouseCaptured) {
        this.warehouseCaptured = Objects.requireNonNull(warehouseCaptured, "warehouseCaptured");
        this.facts = Objects.requireNonNull(facts, "facts");
    }

    @Override
    public OpeningOperationCommandFrame freeze(
            BattleView sim, Faction perspective, CommandTopology topology,
            CommandAssignmentSnapshot assignments) {
        if (perspective != Faction.MARINE && perspective != Faction.DEFENDER && perspective != Faction.ALLY) {
            throw new IllegalArgumentException(
                    "opening operation requires a fighting-faction perspective");
        }
        return OpeningOperationCommandFrame.disclose(sim, perspective,
                topology, assignments, facts.withWarehouseCaptured(warehouseCaptured.getAsBoolean()));
    }
}
