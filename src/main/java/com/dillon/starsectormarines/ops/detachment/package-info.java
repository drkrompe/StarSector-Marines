/**
 * Bridge layer (campaign &rarr; battle) — resolves the player's <em>committed
 * detachment</em> into the support a battle runs with.
 *
 * <p>Category: pre-battle resolver (the diegetic-loadout core, roadmap
 *           command-powers S2).
 * <br>Charter:  turn <em>(committed fleet subset + employer/contract offerings)</em>
 *           into a {@link com.dillon.starsectormarines.ops.detachment.Detachment}
 *           — the single source of all three battle-support kinds: shuttle
 *           transport, passive fighter cover, and active command powers.
 *           {@code DetachmentResolver} owns the resolution (and the shuttle-
 *           manifest / employer-cap / heavy-armaments helpers the briefing
 *           screens used to duplicate); {@code PowerCatalog} is the hardcoded
 *           ship-id / hull-mod-id &rarr; {@code CommandPower} mapping (the
 *           {@code ShuttleType.forHullId} / {@code profileFromWingId} precedent).
 * <br>Boundary: this package is the <em>only</em> place that reads
 *           {@code FleetMemberAPI} / {@code Mission} to derive battle support.
 *           The battle tier ({@code battle.*}) never imports campaign types — it
 *           consumes the resolved plain data ({@code FlybyRoster},
 *           {@code List<ShuttleAssignment>}, {@code List<CommandPower>}) via the
 *           sim's {@code setFlybyRoster} / {@code setCommandPowers} seams, the
 *           same discipline {@code FlybyRoster} / {@code ShuttleAssignment}
 *           already keep.
 *
 * <p>See {@code roadmap/command-powers/} (S2 — explicit detachment + unified
 * support resolver) for the design track.
 */
package com.dillon.starsectormarines.ops.detachment;
