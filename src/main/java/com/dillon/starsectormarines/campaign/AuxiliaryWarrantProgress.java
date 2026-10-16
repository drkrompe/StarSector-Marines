package com.dillon.starsectormarines.campaign;

import java.io.Serializable;

/**
 * Save-owned receipt for the Hegemony auxiliary warrant offer ({@code progression-nouns.md},
 * Kit and armory). Each transition happens at most once, so a reloaded or replayed answer
 * can never grant the warrant twice or open its second stage early.
 *
 * <p>Holds only the offer's own state. The warrant it grants is a military warrant row like
 * any other; this record says which authored terms produced it.
 */
public final class AuxiliaryWarrantProgress implements Serializable {

    public enum Stage {
        /** Not offered yet. */
        NONE,
        /** The terms are in the company's correspondence awaiting an answer. */
        OFFERED,
        /** Enrolled: stage one granted. */
        ACCEPTED,
        /** Declined. The same terms are never offered again. */
        DECLINED
    }

    private Stage stage = Stage.NONE;
    private int offeredDay = -1;
    private int answeredDay = -1;
    private boolean secondStage;
    private int secondStageDay = -1;

    public Stage stage() { return stage == null ? Stage.NONE : stage; }
    public int offeredDay() { return offeredDay; }
    public int answeredDay() { return answeredDay; }
    /** Whether the honoured-period stage (the XIV shock pattern) has been opened. */
    public boolean secondStage() { return secondStage; }
    public int secondStageDay() { return secondStageDay; }

    /** NONE to OFFERED; false when the terms were already offered or answered. */
    public boolean offer(int day) {
        if (stage() != Stage.NONE) return false;
        stage = Stage.OFFERED;
        offeredDay = day;
        return true;
    }

    /** OFFERED to ACCEPTED; false for any other stage. */
    public boolean accept(int day) {
        if (stage() != Stage.OFFERED) return false;
        stage = Stage.ACCEPTED;
        answeredDay = day;
        return true;
    }

    /** OFFERED to DECLINED; false for any other stage. */
    public boolean decline(int day) {
        if (stage() != Stage.OFFERED) return false;
        stage = Stage.DECLINED;
        answeredDay = day;
        return true;
    }

    /** Opens the second stage once, and only for an enrolled company. */
    public boolean openSecondStage(int day) {
        if (stage() != Stage.ACCEPTED || secondStage) return false;
        secondStage = true;
        secondStageDay = day;
        return true;
    }
}
