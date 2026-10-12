package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.world.gen.ship.WorkshopState;
import com.dillon.starsectormarines.campaign.start.CompanyWorkshopRestoration.Offer;
import com.dillon.starsectormarines.ui.retained.markup.MarkupPropertySource;
import com.dillon.starsectormarines.ui.retained.reactive.MutableSignal;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.dillon.starsectormarines.ui.retained.reactive.Signal;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Supplier;

/** Each stage has its own inspection; a stale stage cannot silently purchase the next one. */
public final class WorkshopRestorationModel {
    private final Supplier<Offer> read;
    private final Function<WorkshopState, String> purchase;
    private final Supplier<String> reclaim;
    private final Runnable inspect, changed;
    private final Runnable action = this::act, reclaimAction = this::reclaim;
    private final MutableSignal<Row> row;
    private WorkshopState reviewed;
    private boolean reclaimReviewed;
    private String feedback = "";
    public WorkshopRestorationModel(Reactor reactor, Supplier<Offer> read,
                                    Function<WorkshopState, String> purchase, Supplier<String> reclaim,
                                    Runnable inspect, Runnable changed) {
        this.read = read; this.purchase = purchase; this.reclaim = reclaim;
        this.inspect = inspect; this.changed = changed;
        row = reactor.signal(project());
    }
    public Signal<Row> row() { return row; }
    public void refresh() { row.set(project()); }
    private Row project() {
        Offer offer = read.get();
        if (reviewed != offer.state()) reviewed = null;
        return new Row(offer, reviewed != null, reclaimReviewed, feedback, action, reclaimAction, inspect);
    }
    private void act() {
        Offer offer = read.get();
        if (reviewed != offer.state()) { reviewed = offer.state(); feedback = ""; inspect.run(); }
        else {
            String refused = purchase.apply(reviewed);
            feedback = refused == null ? "Work complete. The next stage is yours to choose." : refused;
            if (refused == null) { reviewed = null; changed.run(); }
        }
        refresh();
    }
    private void reclaim() {
        if (!reclaimReviewed) { reclaimReviewed = true; feedback = "Review: consume 1 heavy machinery and recover 4 metals."; }
        else {
            String refused = reclaim.get();
            feedback = refused == null ? "Recovered 4 metals from 1 heavy machinery." : refused;
            reclaimReviewed = false;
        }
        refresh();
    }
    public record Row(Offer offer, boolean reviewed, boolean reclaimReviewed, String feedback,
                      Runnable action, Runnable reclaim, Runnable inspect) implements MarkupPropertySource {
        public static Row hidden() {
            return new Row(new Offer(WorkshopState.NONE, 0, "", "", 0, 0), false, false, "", () -> {}, () -> {}, () -> {});
        }
        @Override public Object markupProperty(String key) {
            WorkshopState state = offer.state();
            boolean complete = state == WorkshopState.EQUIPPED;
            return switch (key) {
                case "classes" -> state == WorkshopState.NONE ? "restoration-panel hidden" : "restoration-panel";
                case "portrait" -> CompanyManagementGuide.portrait();
                case "title" -> switch (state) {
                    case BURIED -> "WORKSHOP / BURIED";
                    case CLEARED -> "WORKSHOP / CLEARED";
                    case REPAIRED -> "WORKSHOP / REPAIRED";
                    default -> "WORKSHOP / EQUIPPED";
                };
                case "progress" -> "graphics/ui/company/workshop-stage-" + Math.max(0, state.ordinal() - 1) + ".svg";
                case "copy" -> switch (state) {
                    case BURIED -> "That hatch stays shut. The yard can clear the fallen ribs and plate the breach. Then we can see what the machines need.";
                    case CLEARED -> "The patch is holding and the floor is ours again. Now we can mend those broken machines and put this room back to work.";
                    case REPAIRED -> "She works. Give the hands proper tooling and they will waste fewer supplies keeping our boats and machines ready.";
                    default -> "A proper shop, built back by our own company. The tools stay here. Our boat and Mech Lab jobs now need fewer supplies.";
                };
                case "benefit" -> switch (state) {
                    case BURIED -> "NEXT: Clear wreckage and seal the breach. Recover 10 metals once; reopen the workshop safely.";
                    case CLEARED -> "NEXT: Restore machinery. Unlock reclamation: 1 heavy machinery becomes 4 metals.";
                    case REPAIRED -> "NEXT: Install tooling. Boat and Mech Lab fabrication/fittings use 20% fewer supplies, rounded up.";
                    default -> "ACTIVE: Reclamation and 20% fewer supplies for boat and Mech Lab jobs. Other materials and pattern requirements stay the same.";
                };
                case "bill" -> complete ? "INSTALLED ABOARD THIS HULL" : String.format(Locale.ROOT,
                        "CREDITS  COST %,d / AVAILABLE %,d / SHORT BY %,d", state.price(), offer.credits(), Math.max(0, state.price() - offer.credits()));
                case "reason" -> offer.refusal() == null ? "Planetary yard labor and installation included." : offer.refusal();
                case "actionLabel" -> !reviewed ? "INSPECT WORKSHOP" : String.format(Locale.ROOT, "%s - %,d", switch (state) {
                    case BURIED -> "CLEAR"; case CLEARED -> "REPAIR"; default -> "INSTALL TOOLS";
                }, state.price());
                case "actionClasses" -> complete ? "restoration-action hidden" : "restoration-action console-action";
                case "disabled" -> reviewed && offer.refusal() != null;
                case "action" -> action;
                case "inspect" -> inspect;
                case "reclaimClasses" -> state.operational() ? "reclaim-block" : "reclaim-block hidden";
                case "reclaimCopy" -> "RECLAIM: 1 MACHINERY -> 4 METALS. Cargo: " + offer.machinery() + " machinery / " + offer.metals() + " metals.";
                case "reclaimReason" -> offer.reclaimRefusal() == null ? "Uses fleet cargo. Starting-depot stores are excluded." : offer.reclaimRefusal();
                case "reclaimDisabled" -> offer.reclaimRefusal() != null;
                case "reclaimLabel" -> reclaimReviewed ? "CONFIRM RECLAMATION" : "RECLAIM MACHINERY";
                case "reclaim" -> reclaim;
                case "feedback" -> feedback;
                default -> throw new IllegalArgumentException("Unknown workshop property " + key);
            };
        }
    }
}
