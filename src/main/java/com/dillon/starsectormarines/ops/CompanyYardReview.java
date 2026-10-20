package com.dillon.starsectormarines.ops;

import java.util.function.Supplier;

/**
 * View-local visit through the yard storybook. Only the first page's Next may install; once the
 * conversion is paid the pages are a straight click-through, and Back cannot reach the bill again.
 */
final class CompanyYardReview {
    /** Pages in order: the bill, the runway, the road up, the ore-dust papers. */
    enum Stage { COST, RUNWAY, ROAD, PAPERS }
    private Stage stage = Stage.COST;
    private String failure;

    Stage stage() { return stage; }
    String failure() { return failure; }
    boolean last() { return stage == Stage.PAPERS; }
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
