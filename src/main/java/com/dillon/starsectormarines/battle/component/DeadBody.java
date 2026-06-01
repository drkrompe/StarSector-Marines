package com.dillon.starsectormarines.battle.component;

import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;

/**
 * The corpse left behind when a unit dies — a composable component attached on
 * the death event, so a dead unit becomes an entity that still <em>has</em> a
 * body (this) and a render position (a shared {@code RenderPosition} in
 * {@code RenderPositionService}) but no longer has health / AI / combat columns
 * in the live {@code UnitRegistry}.
 *
 * <p>This is the "lightweight body entity" the retire-legacy-units-list story
 * calls for: the corpse home that lets the dead-sprite render read bodies from
 * a component store instead of scanning the legacy live+dead units list, and
 * the seam future mechanics (medics, a downed-not-dead / revive state) hook
 * into by querying or consuming bodies.
 *
 * <p>Carries what the dead-sprite render needs (the {@link UnitType}, for the
 * corpse sheet + render scale, and the {@code deathPoseIdx}, the frozen
 * prone-pose frame) plus the dead unit's {@link Faction} — the first non-render
 * consumer of the corpse home is {@code MissionResolver}'s casualty count, which
 * tallies dead marines by faction without the legacy units list. Position is
 * <b>not</b> duplicated here — it lives in the shared render-position component
 * keyed by the same entity id, so the corpse composes its location rather than
 * copying it. Cell can be added when a consumer (medic targeting, a diagnostics
 * dump) needs it; kept minimal until then.
 */
public final class DeadBody {

    /** The dead unit's archetype — selects the corpse sprite sheet + render scale. */
    public final UnitType type;
    /** The dead unit's side — lets a corpse-store reader tally casualties by faction. */
    public final Faction faction;
    /**
     * Frozen prone-pose frame index rolled at death ({@code 0..3}), or
     * {@code -1} if the unit died without a pose roll (e.g. a cascade-killed
     * drone that bypassed the damage resolver). The renderer skips {@code < 0}.
     */
    public final int deathPoseIdx;

    public DeadBody(UnitType type, Faction faction, int deathPoseIdx) {
        this.type = type;
        this.faction = faction;
        this.deathPoseIdx = deathPoseIdx;
    }
}
