package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ui.retained.reactive.MutableSignal;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;

import java.util.LinkedHashMap;
import java.util.Map;

/** Render-free interaction state for the MLX-authored battle exit control. */
final class BattleRetreatOverlayModel {

    private static final String ACTION_CLASSES = "battle-exit-action";
    private static final String ACTION_HIDDEN_CLASSES =
            "battle-exit-action battle-exit-action-hidden";
    private static final String CONFIRM_CLASSES = "battle-retreat-confirm";
    private static final String CONFIRM_HIDDEN_CLASSES =
            "battle-retreat-confirm battle-retreat-confirm-hidden";

    enum Presentation {
        // MLX width/height are content-box dimensions. These viewport values
        // include the authored padding and border so the control is not
        // clipped against its own document edge.
        RETREAT(148f, 52f),
        CONFIRM(378f, 54f),
        CONTINUE(148f, 52f);

        private final float documentWidth;
        private final float documentHeight;

        Presentation(float documentWidth, float documentHeight) {
            this.documentWidth = documentWidth;
            this.documentHeight = documentHeight;
        }

        float documentWidth() {
            return documentWidth;
        }

        float documentHeight() {
            return documentHeight;
        }
    }

    private final MutableSignal<String> actionClasses;
    private final MutableSignal<String> actionLabel;
    private final MutableSignal<String> confirmClasses;
    private final Runnable retreatAction;
    private final Runnable continueAction;
    private final String retreatLabel;
    private final String continueLabel;
    private final String confirmPrompt;
    private final String cancelLabel;
    private final String confirmLabel;

    private Presentation presentation = Presentation.RETREAT;

    BattleRetreatOverlayModel(Reactor reactor,
                              Runnable retreatAction,
                              Runnable continueAction,
                              String retreatLabel,
                              String continueLabel,
                              String confirmPrompt,
                              String cancelLabel,
                              String confirmLabel) {
        this.retreatAction = retreatAction;
        this.continueAction = continueAction;
        this.retreatLabel = retreatLabel;
        this.continueLabel = continueLabel;
        this.confirmPrompt = confirmPrompt;
        this.cancelLabel = cancelLabel;
        this.confirmLabel = confirmLabel;
        actionClasses = reactor.signal(ACTION_CLASSES);
        actionLabel = reactor.signal(retreatLabel);
        confirmClasses = reactor.signal(CONFIRM_HIDDEN_CLASSES);
    }

    Map<String, Object> props() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("actionClasses", actionClasses);
        props.put("actionLabel", actionLabel);
        props.put("action", (Runnable) this::activatePrimary);
        props.put("confirmClasses", confirmClasses);
        props.put("confirmPrompt", confirmPrompt);
        props.put("cancelLabel", cancelLabel);
        props.put("cancelAction", (Runnable) this::cancelRetreat);
        props.put("confirmLabel", confirmLabel);
        props.put("confirmAction", (Runnable) this::confirmRetreat);
        return props;
    }

    Presentation update(boolean battleComplete) {
        if (battleComplete && presentation != Presentation.CONTINUE) {
            setPresentation(Presentation.CONTINUE);
        } else if (!battleComplete && presentation == Presentation.CONTINUE) {
            setPresentation(Presentation.RETREAT);
        }
        return presentation;
    }

    Presentation presentation() {
        return presentation;
    }

    private void activatePrimary() {
        if (presentation == Presentation.CONTINUE) {
            continueAction.run();
        } else if (presentation == Presentation.RETREAT) {
            setPresentation(Presentation.CONFIRM);
        }
    }

    void cancelRetreat() {
        if (presentation == Presentation.CONFIRM) {
            setPresentation(Presentation.RETREAT);
        }
    }

    private void confirmRetreat() {
        if (presentation != Presentation.CONFIRM) return;
        setPresentation(Presentation.RETREAT);
        retreatAction.run();
    }

    private void setPresentation(Presentation next) {
        presentation = next;
        boolean confirming = next == Presentation.CONFIRM;
        actionClasses.set(confirming ? ACTION_HIDDEN_CLASSES : ACTION_CLASSES);
        actionLabel.set(next == Presentation.CONTINUE ? continueLabel : retreatLabel);
        confirmClasses.set(confirming ? CONFIRM_CLASSES : CONFIRM_HIDDEN_CLASSES);
    }
}
