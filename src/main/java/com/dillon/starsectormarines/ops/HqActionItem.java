package com.dillon.starsectormarines.ops;

/**
 * One thing the captain can act on from Company HQ, with exactly one primary action.
 *
 * <p>Every item projects an existing authority (a clock, a story arc's step, a desk's stage or
 * an intel event); the board persists nothing of its own. See {@code hq-action-board.md}.
 *
 * @param id        stable identity across refreshes, so a selection survives one
 * @param kind      what the item is; decides its filter and its place in the ordering
 * @param title     what it is, in a few words
 * @param place     where it is ("Jangala / Corvus"), or empty
 * @param daysLeft  days until its deadline, floored at zero, or {@link #NO_DEADLINE}
 * @param action    the one thing to do about it
 * @param detail    what the detail pane shows for it: a story guide, or null for a plain item
 * @param sector    hyperspace position of its place for the holo board, or null when it has none
 */
record HqActionItem(String id, Kind kind, String title, String place, int daysLeft,
                    Action action, Object detail, float[] sector) {

    static final int NO_DEADLINE = Integer.MAX_VALUE;

    enum Kind {
        /** A clock that bites: a response, a term ending or a recovery. */
        OBLIGATION(Filter.CONTRACTS),
        /** The current step of one live story arc. */
        STORY(Filter.STORY),
        /** Correspondence awaiting a decision. */
        LETTER(Filter.OTHER),
        /** An open intel event the company can take up. */
        SIDE_JOB(Filter.OTHER);

        final Filter filter;

        Kind(Filter filter) { this.filter = filter; }
    }

    enum Filter { ALL, STORY, CONTRACTS, OTHER }

    /**
     * The item's one answer. {@code run} is null when nothing can be done now, and then
     * {@code reason} says why instead of a dead button.
     */
    record Action(Verb verb, Runnable run, String reason) {
        static Action of(Verb verb, Runnable run) { return new Action(verb, run, ""); }

        static Action unavailable(Verb verb, String reason) { return new Action(verb, null, reason); }

        boolean available() { return run != null; }
    }

    enum Verb { RESPOND, PLOT_COURSE, OPEN, TRACKING }

    boolean hasDeadline() { return daysLeft != NO_DEADLINE; }
}
