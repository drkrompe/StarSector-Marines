package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.i18n.Strings;
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
            feedback = refused == null ? Strings.get("openingWorkshopRestorationWorkCompleteTheNextStageIsYours") : refused;
            if (refused == null) { reviewed = null; changed.run(); }
        }
        refresh();
    }
    private void reclaim() {
        if (!reclaimReviewed) { reclaimReviewed = true; feedback = Strings.get("openingWorkshopRestorationReviewConsumeHeavyMachineryAndRecoverMetals"); }
        else {
            String refused = reclaim.get();
            feedback = refused == null ? Strings.get("openingWorkshopRestorationRecoveredMetalsFromHeavyMachinery") : refused;
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
                    case BURIED -> Strings.get("openingWorkshopRestorationWorkshopBuried");
                    case CLEARED -> Strings.get("openingWorkshopRestorationWorkshopCleared");
                    case REPAIRED -> Strings.get("openingWorkshopRestorationWorkshopRepaired");
                    default -> Strings.get("openingWorkshopRestorationWorkshopEquipped");
                };
                case "progress" -> "graphics/ui/company/workshop-stage-" + Math.max(0, state.ordinal() - 1) + ".svg";
                case "copy" -> switch (state) {
                    case BURIED -> Strings.get("openingWorkshopRestorationThatHatchStaysShutTheYardCan");
                    case CLEARED -> Strings.get("openingWorkshopRestorationThePatchIsHoldingAndTheFloor");
                    case REPAIRED -> Strings.get("openingWorkshopRestorationSheWorksGiveTheHandsProperTooling");
                    default -> Strings.get("openingWorkshopRestorationAProperShopBuiltBackByOur");
                };
                case "benefit" -> switch (state) {
                    case BURIED -> Strings.get("openingWorkshopRestorationNextClearWreckageAndSealTheBreach");
                    case CLEARED -> Strings.get("openingWorkshopRestorationNextRestoreMachineryUnlockReclamationHeavyMachinery");
                    case REPAIRED -> Strings.get("openingWorkshopRestorationNextInstallToolingBoatAndMechLab");
                    default -> Strings.get("openingWorkshopRestorationActiveReclamationAndFewerSuppliesForBoat");
                };
                case "bill" -> complete ? Strings.get("openingWorkshopRestorationInstalledAboardThisHull") : String.format(Locale.ROOT,
                        Strings.get("openingWorkshopRestorationCreditsCostDAvailableDShortBy"), state.price(), offer.credits(), Math.max(0, state.price() - offer.credits()));
                case "reason" -> offer.refusal() == null ? Strings.get("openingWorkshopRestorationPlanetaryYardLaborAndInstallationIncluded") : offer.refusal();
                case "actionLabel" -> !reviewed ? Strings.get("openingWorkshopRestorationInspectWorkshop") : String.format(Locale.ROOT, "%s - %,d", switch (state) {
                    case BURIED -> Strings.get("openingWorkshopRestorationClear"); case CLEARED -> Strings.get("openingWorkshopRestorationRepair"); default -> Strings.get("openingWorkshopRestorationInstallTools");
                }, state.price());
                case "actionClasses" -> complete ? "restoration-action hidden" : "restoration-action console-action";
                case "disabled" -> reviewed && offer.refusal() != null;
                case "action" -> action;
                case "inspect" -> inspect;
                case "reclaimClasses" -> state.operational() ? "reclaim-block" : "reclaim-block hidden";
                case "reclaimCopy" -> Strings.format("openingWorkshopRestorationReclaimMachineryMetalsCargoMachineryMetals", offer.machinery(), offer.metals());
                case "reclaimReason" -> offer.reclaimRefusal() == null ? Strings.get("openingWorkshopRestorationUsesFleetCargoStartingDepotStoresAre") : offer.reclaimRefusal();
                case "reclaimDisabled" -> offer.reclaimRefusal() != null;
                case "reclaimLabel" -> reclaimReviewed ? Strings.get("openingWorkshopRestorationConfirmReclamation") : Strings.get("openingWorkshopRestorationReclaimMachinery");
                case "reclaim" -> reclaim;
                case "feedback" -> feedback;
                default -> throw new IllegalArgumentException("Unknown workshop property " + key);
            };
        }
    }
}
