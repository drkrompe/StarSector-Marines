package com.dillon.starsectormarines.ops;

import java.util.function.Supplier;

/**
 * View-local visit through the yard storybook. Only the first page's Next may install; once the
 * conversion is paid the pages are a straight click-through, and Back cannot reach the bill again.
 */
final class CompanyYardReview {
    /** Pages in order: the bill, the runway, the road up, the ore-dust papers. */
    enum Stage { COST, RUNWAY, ROAD, PAPERS }
    private final boolean story;
    private Stage stage = Stage.COST;
    private String failure;

    /** {@code story}: the Jangala visit, which runs on to the road and papers pages. */
    CompanyYardReview(boolean story) { this.story = story; }

    Stage stage() { return stage; }
    String failure() { return failure; }
    boolean last() { return stage == (story ? Stage.PAPERS : Stage.RUNWAY); }
    /** Back is off on the bill (the first page) and on the page that follows payment. */
    static boolean backAllowed(Stage stage) { return stage.ordinal() > Stage.RUNWAY.ordinal(); }
    void back() { if (backAllowed(stage)) stage = Stage.values()[stage.ordinal() - 1]; }
    void next(Supplier<String> install, Runnable leave) {
        if (stage == Stage.COST) {
            failure = install.get();
            if (failure == null) stage = Stage.RUNWAY;
        } else if (last()) {
            leave.run();
        } else {
            stage = Stage.values()[stage.ordinal() + 1];
        }
    }
}
