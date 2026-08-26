package com.dillon.starsectormarines.battle.fixture;

import com.dillon.starsectormarines.battle.flyby.FighterProfile;
import com.dillon.starsectormarines.battle.flyby.FighterWing;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.Objects;
import java.util.ArrayList;
import java.util.List;

/** Immutable, equality-safe representation of one fighter-support commitment. */
public record FighterWingCommitment(
        FighterProfile profile,
        Faction side,
        int sortieCount,
        float firstArrivalSec,
        float spawnIntervalSec) {

    public FighterWingCommitment {
        profile = Objects.requireNonNull(profile, "profile");
        side = Objects.requireNonNull(side, "side");
        if (sortieCount < 1) {
            throw new IllegalArgumentException("sortieCount must be positive");
        }
        if (firstArrivalSec < 0f || !Float.isFinite(firstArrivalSec)) {
            throw new IllegalArgumentException(
                    "firstArrivalSec must be finite and non-negative");
        }
        if (spawnIntervalSec < 0.1f || !Float.isFinite(spawnIntervalSec)) {
            throw new IllegalArgumentException(
                    "spawnIntervalSec must be finite and at least 0.1");
        }
    }

    public static FighterWingCommitment capture(FighterWing wing) {
        Objects.requireNonNull(wing, "wing");
        return new FighterWingCommitment(wing.profile, wing.side,
                wing.sortieCount, wing.firstArrivalSec, wing.spawnIntervalSec);
    }

    public FighterWing toWing() {
        return new FighterWing(profile, side, sortieCount,
                firstArrivalSec, spawnIntervalSec);
    }

    public static List<FighterWingCommitment> captureRoster(FlybyRoster roster) {
        if (roster == null || roster.isEmpty()) return List.of();
        List<FighterWingCommitment> captured = new ArrayList<>(roster.wings.size());
        for (FighterWing wing : roster.wings) captured.add(capture(wing));
        return List.copyOf(captured);
    }

    public static FlybyRoster toRoster(List<FighterWingCommitment> commitments) {
        if (commitments == null || commitments.isEmpty()) return FlybyRoster.EMPTY;
        List<FighterWing> wings = new ArrayList<>(commitments.size());
        for (FighterWingCommitment commitment : commitments) {
            wings.add(Objects.requireNonNull(commitment, "commitment").toWing());
        }
        return new FlybyRoster(wings);
    }
}
