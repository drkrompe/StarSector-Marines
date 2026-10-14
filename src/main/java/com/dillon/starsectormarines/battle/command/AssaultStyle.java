package com.dillon.starsectormarines.battle.command;

/**
 * How a Conquest's attacking commander takes defended works, as a mission declares it. It travels with the mission's
 * {@code TargetProfile} and from there to that battle's own {@link ConquestCommand}, so two battles in one process
 * never share it.
 *
 * <p>{@link #MUSTER} is the standing behaviour: land, muster a company, assault the works by the stamp's plan
 * ({@code phased-assault.md}). {@link #TRICKLE} is the mission's choice to leave that out: the commander detaches one
 * or two squads at a time to a disclosed fort, the trickle a fort is built to beat. No shipped mission chooses it; it
 * exists for a mission whose premise is a disorganised force, and as the control of the assault-muster comparison.
 */
public enum AssaultStyle {
    MUSTER,
    TRICKLE
}
