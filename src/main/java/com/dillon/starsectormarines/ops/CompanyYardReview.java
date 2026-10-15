package com.dillon.starsectormarines.ops;

import java.util.function.Supplier;

/** View-local visit: reading never orders work; only the reviewed action may install. */
final class CompanyYardReview {
    enum Stage { SURVEY, ORDER, COMPLETE }
    private Stage stage = Stage.SURVEY;
    private String failure;

    Stage stage() { return stage; }
    String failure() { return failure; }
    void back() { if (stage == Stage.ORDER) { stage = Stage.SURVEY; failure = null; } }
    void next(Supplier<String> install, Runnable leave) {
        switch (stage) {
            case SURVEY -> stage = Stage.ORDER;
            case ORDER -> {
                failure = install.get();
                if (failure == null) stage = Stage.COMPLETE;
            }
            case COMPLETE -> leave.run();
        }
    }
}
