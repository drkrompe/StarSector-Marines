package com.dillon.starsectormarines.ops.detachment;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.power.CommandPower;

import java.util.Collections;
import java.util.List;

/**
 * The frozen output of {@link DetachmentResolver#resolve} — what the player's
 * committed fleet (plus the employer's offerings) brings to one battle. Created
 * at the mission-accept instant and consumed by the battle setup; immutable.
 *
 * <p>Stores <em>pre-resolved capability lists</em>, not {@code FleetMemberAPI}
 * ids: the fleet can't change between accept and battle entry, and the battle
 * tier is campaign-agnostic, so resolving up front keeps the
 * {@code ops &rarr; battle} boundary clean (the sim only ever sees these plain
 * shapes). Transient UI toggle state stays in the briefing screens.
 *
 * <p>{@link #marineWings} is the <em>marine side only</em> (player carriers +
 * the employer's {@code clientFighterSupport}). Enemy fighter support is the
 * Mission's, combined in at the call site — it never belongs to the player's
 * detachment.
 */
public final class Detachment {

    /** Transports + sortie counts that fly this battle (employer first, then committed player ships). */
    public final List<ShuttleAssignment> shuttleManifest;

    /** Marine-side fighter cover: committed player carrier bays combined with the employer's support. */
    public final FlybyRoster marineWings;

    /** Active command powers the player can invoke this battle, deduped by id. */
    public final List<CommandPower> powers;

    public static final Detachment EMPTY =
            new Detachment(Collections.emptyList(), FlybyRoster.EMPTY, Collections.emptyList());

    public Detachment(List<ShuttleAssignment> shuttleManifest,
                      FlybyRoster marineWings,
                      List<CommandPower> powers) {
        this.shuttleManifest = shuttleManifest != null
                ? Collections.unmodifiableList(shuttleManifest) : Collections.emptyList();
        this.marineWings = marineWings != null ? marineWings : FlybyRoster.EMPTY;
        this.powers = powers != null
                ? Collections.unmodifiableList(powers) : Collections.emptyList();
    }
}
