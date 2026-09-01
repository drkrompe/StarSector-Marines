/**
 * Category: battle feature domain.
 *
 * <p>Charter: what everybody who is not fighting is doing. A {@code JobSite} is
 * a place with work in it, a {@code Shift} is the watch bill standing on one,
 * and an {@code AmbientTaskRoute} is the loop one person walks round it. The
 * service assigns those and advances them; a crew is on the ordinary roster
 * throughout, so they can be seen, shot, and stopped.
 *
 * <p>It also owns the standing of a watch over time: a posting outlives the
 * people filling it, belongs to whoever holds the ground it stands on, and sends
 * for a replacement when a billet has been empty long enough. That is the layer
 * that makes clearing a facility a delay rather than a permanent result, and
 * makes taking one worth more than denying it.
 *
 * <p>Boundary: this package decides who is at work and where they walk. It does
 * not decide what the work produces — a bay's machine is
 * {@code battle.fabrication}'s and an airframe's turnaround is
 * {@code battle.air}'s — nor where the work is in the first place, which is
 * authored by the fittings that cut the rooms ({@code world.gen.FixtureTask}).
 * A relief flight is flown by {@code battle.air}; what this package owns of it
 * is only who is aboard and which billet they are for.
 *
 * <p>Deliberately not reinforcement. A reinforcement means answers a request
 * from off the map and is chosen against other means by how soon it would
 * arrive; a replacement answers a seat in a building and has no alternative to
 * be chosen against. See {@code reinforcement-nouns.md}.
 *
 * <p>Pointer: {@code ai-nouns.md} owns the model — job sites, shifts, postings,
 * and the replacement rule.
 */
package com.dillon.starsectormarines.battle.ambient;
