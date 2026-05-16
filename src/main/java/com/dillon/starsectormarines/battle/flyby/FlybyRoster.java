package com.dillon.starsectormarines.battle.flyby;

import com.dillon.starsectormarines.battle.Faction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The complete set of fighter wings committed to a single battle. The
 * {@link FlybyOverlay} reads one of these on battle start and drives spawns
 * from the per-wing schedules; no random pool sampling involved.
 *
 * <p>Sources combine here: a mission-generated employer roster, an
 * enemy-side roster, and (Phase 2) a player-owned roster all merge into a
 * single {@code FlybyRoster} via {@link #combine}. Each wing carries its own
 * {@link FighterWing#side}, so the overlay doesn't need to know which source
 * a wing came from.
 *
 * <p>{@link #EMPTY} is the no-air-support case — battles get one of these
 * when nobody on either side could spare any fighters.
 */
public final class FlybyRoster {

    public static final FlybyRoster EMPTY = new FlybyRoster(Collections.emptyList());

    public final List<FighterWing> wings;

    public FlybyRoster(List<FighterWing> wings) {
        this.wings = Collections.unmodifiableList(new ArrayList<>(wings));
    }

    public boolean isEmpty() { return wings.isEmpty(); }

    /** Returns wings belonging to {@code side}. Convenience for briefing UI summaries. */
    public List<FighterWing> wingsForSide(Faction side) {
        List<FighterWing> out = new ArrayList<>();
        for (FighterWing w : wings) if (w.side == side) out.add(w);
        return out;
    }

    /**
     * Merges two rosters into one. Null inputs are treated as {@link #EMPTY}.
     * Used at battle-start to combine employer + enemy + (Phase 2) player wings
     * into the single roster the overlay reads.
     */
    public static FlybyRoster combine(FlybyRoster a, FlybyRoster b) {
        if (a == null || a.isEmpty()) return b == null ? EMPTY : b;
        if (b == null || b.isEmpty()) return a;
        List<FighterWing> merged = new ArrayList<>(a.wings.size() + b.wings.size());
        merged.addAll(a.wings);
        merged.addAll(b.wings);
        return new FlybyRoster(merged);
    }
}
