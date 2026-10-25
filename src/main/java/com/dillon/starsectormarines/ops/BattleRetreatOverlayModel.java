package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ui.retained.reactive.MutableSignal;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Render-free interaction state for the MLX-authored battle exit control.
 *
 * <p>While the battle runs it is the bottom-left Retreat button, which arms a
 * confirmation offering Withdraw and, on a story operation, Restart. Once the
 * battle ends it becomes the centred end panel under the Victory/Defeat banner:
 * Continue, or on a lost story operation Retry beside Accept defeat.
 */
final class BattleRetreatOverlayModel {

    private static final String EXIT_ROW_CLASSES = "battle-exit-row";
    private static final String EXIT_ROW_HIDDEN_CLASSES = "battle-exit-row battle-exit-row-hidden";
    private static final String ACTION_CLASSES = "battle-exit-action";
    private static final String ACTION_HIDDEN_CLASSES =
            "battle-exit-action battle-exit-action-hidden";
    private static final String RETRY_CLASSES = "battle-exit-action battle-retry-action";
    private static final String RETRY_HIDDEN_CLASSES =
            "battle-exit-action battle-retry-action battle-exit-action-hidden";
    private static final String CONFIRM_CLASSES = "battle-retreat-confirm";
    private static final String CONFIRM_HIDDEN_CLASSES =
            "battle-retreat-confirm battle-retreat-confirm-hidden";
    private static final String RESTART_CLASSES = "retreat-restart console-action";
    private static final String RESTART_HIDDEN_CLASSES =
            "retreat-restart console-action retreat-restart-hidden";

    /** Where the battle stands, as far as leaving it is concerned. */
    enum Phase { ACTIVE, VICTORY, DEFEAT }

    enum Presentation {
        // MLX width/height are content-box dimensions. These viewport values
        // include the authored padding and border so the control is not
        // clipped against its own document edge.
        RETREAT(148f, 52f, false),
        CONFIRM(452f, 104f, false),
        CONTINUE(148f, 52f, true),
        DEFEAT_RETRY(392f, 52f, true);

        private final float documentWidth;
        private final float documentHeight;
        private final boolean centred;

        Presentation(float documentWidth, float documentHeight, boolean centred) {
            this.documentWidth = documentWidth;
            this.documentHeight = documentHeight;
            this.centred = centred;
        }

        float documentWidth() {
            return documentWidth;
        }

        float documentHeight() {
            return documentHeight;
        }

        /** End-of-battle panels sit under the centred banner; the live control stays in its corner. */
        boolean centred() {
            return centred;
        }
    }

    /**
     * Whether this operation can be tried again, and what the button says.
     * {@code battleLabel} names it from inside a live battle (Restart),
     * {@code defeatLabel} once it is lost (Retry).
     */
    record RetryOffer(boolean offered, boolean affordable, String battleLabel, String defeatLabel) {
        static final RetryOffer NONE = new RetryOffer(false, false, "", "");
    }

    interface Actions {
        /** Confirmed Retreat: end the battle as a withdrawal. */
        void withdraw();

        /** Leave a finished battle for Results. */
        void proceed();

        /** Erase this attempt and try the operation again. */
        void retry();
    }

    record Labels(String retreat, String continueLabel, String acceptDefeat,
                  String confirmPrompt, String confirmDetail, String cancel, String withdraw) {
    }

    private final MutableSignal<String> exitRowClasses;
    private final MutableSignal<String> actionClasses;
    private final MutableSignal<String> actionLabel;
    private final MutableSignal<String> retryClasses;
    private final MutableSignal<String> retryLabel;
    private final MutableSignal<Boolean> retryDisabled;
    private final MutableSignal<String> confirmClasses;
    private final MutableSignal<String> restartClasses;
    private final MutableSignal<String> restartLabel;
    private final MutableSignal<Boolean> restartDisabled;
    private final Actions actions;
    private final Labels labels;

    private Presentation presentation = Presentation.RETREAT;
    private RetryOffer offer = RetryOffer.NONE;

    BattleRetreatOverlayModel(Reactor reactor, Actions actions, Labels labels) {
        this.actions = actions;
        this.labels = labels;
        exitRowClasses = reactor.signal(EXIT_ROW_CLASSES);
        actionClasses = reactor.signal(ACTION_CLASSES);
        actionLabel = reactor.signal(labels.retreat());
        retryClasses = reactor.signal(RETRY_HIDDEN_CLASSES);
        retryLabel = reactor.signal("");
        retryDisabled = reactor.signal(false);
        confirmClasses = reactor.signal(CONFIRM_HIDDEN_CLASSES);
        restartClasses = reactor.signal(RESTART_HIDDEN_CLASSES);
        restartLabel = reactor.signal("");
        restartDisabled = reactor.signal(false);
    }

    Map<String, Object> props() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("exitRowClasses", exitRowClasses);
        props.put("actionClasses", actionClasses);
        props.put("actionLabel", actionLabel);
        props.put("action", (Runnable) this::activatePrimary);
        props.put("retryClasses", retryClasses);
        props.put("retryLabel", retryLabel);
        props.put("retryDisabled", retryDisabled);
        props.put("retryAction", (Runnable) this::retryAfterDefeat);
        props.put("confirmClasses", confirmClasses);
        props.put("confirmPrompt", labels.confirmPrompt());
        props.put("confirmDetail", labels.confirmDetail());
        props.put("restartClasses", restartClasses);
        props.put("restartLabel", restartLabel);
        props.put("restartDisabled", restartDisabled);
        props.put("restartAction", (Runnable) this::restart);
        props.put("cancelLabel", labels.cancel());
        props.put("cancelAction", (Runnable) this::cancelRetreat);
        props.put("confirmLabel", labels.withdraw());
        props.put("confirmAction", (Runnable) this::confirmRetreat);
        return props;
    }

    Presentation update(Phase phase, RetryOffer nextOffer) {
        offer = nextOffer != null ? nextOffer : RetryOffer.NONE;
        Presentation next = switch (phase) {
            case ACTIVE -> presentation == Presentation.CONFIRM ? Presentation.CONFIRM : Presentation.RETREAT;
            case VICTORY -> Presentation.CONTINUE;
            case DEFEAT -> offer.offered() ? Presentation.DEFEAT_RETRY : Presentation.CONTINUE;
        };
        setPresentation(next, phase == Phase.DEFEAT);
        return presentation;
    }

    Presentation presentation() {
        return presentation;
    }

    private void activatePrimary() {
        if (presentation == Presentation.CONTINUE || presentation == Presentation.DEFEAT_RETRY) {
            actions.proceed();
        } else if (presentation == Presentation.RETREAT) {
            setPresentation(Presentation.CONFIRM, false);
        }
    }

    private void retryAfterDefeat() {
        if (presentation == Presentation.DEFEAT_RETRY && offer.affordable()) actions.retry();
    }

    private void restart() {
        if (presentation != Presentation.CONFIRM || !offer.offered() || !offer.affordable()) return;
        setPresentation(Presentation.RETREAT, false);
        actions.retry();
    }

    void cancelRetreat() {
        if (presentation == Presentation.CONFIRM) {
            setPresentation(Presentation.RETREAT, false);
        }
    }

    private void confirmRetreat() {
        if (presentation != Presentation.CONFIRM) return;
        setPresentation(Presentation.RETREAT, false);
        actions.withdraw();
    }

    private void setPresentation(Presentation next, boolean defeat) {
        presentation = next;
        boolean confirming = next == Presentation.CONFIRM;
        exitRowClasses.set(confirming ? EXIT_ROW_HIDDEN_CLASSES : EXIT_ROW_CLASSES);
        actionClasses.set(confirming ? ACTION_HIDDEN_CLASSES : ACTION_CLASSES);
        actionLabel.set(next == Presentation.DEFEAT_RETRY ? labels.acceptDefeat()
                : next == Presentation.CONTINUE ? labels.continueLabel() : labels.retreat());
        retryClasses.set(next == Presentation.DEFEAT_RETRY ? RETRY_CLASSES : RETRY_HIDDEN_CLASSES);
        retryLabel.set(offer.defeatLabel());
        retryDisabled.set(!offer.affordable());
        confirmClasses.set(confirming ? CONFIRM_CLASSES : CONFIRM_HIDDEN_CLASSES);
        restartClasses.set(!offer.offered() || defeat ? RESTART_HIDDEN_CLASSES : RESTART_CLASSES);
        restartLabel.set(offer.battleLabel());
        restartDisabled.set(!offer.affordable());
    }
}
