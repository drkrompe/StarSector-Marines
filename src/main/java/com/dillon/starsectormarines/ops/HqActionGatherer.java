package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CompanyClocks;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.intel.CivilianRescueIntel;
import com.dillon.starsectormarines.intel.DeadLetterIntel;
import com.dillon.starsectormarines.intel.DefectorAsylumIntel;
import com.dillon.starsectormarines.intel.SideJobLead;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.ops.HqActionItem.Action;
import com.dillon.starsectormarines.ops.HqActionItem.Kind;
import com.dillon.starsectormarines.ops.HqActionItem.Verb;
import com.dillon.starsectormarines.ops.event.PlayerEventTarget;
import com.dillon.starsectormarines.ui.retained.markup.MarkupPropertySource;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Reads the campaign into HQ action items. Each source keeps its own authority: the clocks, each
 * story arc's progress and destination, the letter desks' stages and the open intel events.
 * Nothing is persisted here. See {@code hq-action-board.md}.
 */
final class HqActionGatherer {

    static final int OBLIGATION_LIMIT = 6;

    private HqActionGatherer() { }

    /**
     * Everything that needs the captain now.
     *
     * @param respond    the deployment route for an answerable obligation
     * @param plotCourse sets the campaign course to an entity and closes HQ
     */
    static List<HqActionItem> current(int day, List<CompanyClocks.Entry> clocks, CampaignState state,
                                      MarineRoster roster,
                                      Function<CompanyClocks.Entry, Runnable> respond,
                                      Consumer<SectorEntityToken> plotCourse,
                                      AuxiliaryWarrantDesk.Row auxiliary, AgreusHandoverDesk.Row handover) {
        List<HqActionItem> items = new ArrayList<>();
        for (int index = 0; index < Math.min(clocks.size(), OBLIGATION_LIMIT); index++) {
            CompanyClocks.Entry entry = clocks.get(index);
            items.add(obligation(entry, day, respond.apply(entry), sector(PlayerEventTarget.market(entry.marketId))));
        }
        storyLeads(roster, plotCourse, items);
        if (auxiliary != null && auxiliary.awaiting()) {
            items.add(letter("letter:auxiliary", Strings.get("hqActionLetterAuxiliary"),
                    Strings.get("hqActionLetterAuxiliaryPlace"), auxiliary.open()));
        }
        if (handover != null && handover.awaiting()) {
            items.add(letter("letter:handover", Strings.get("hqActionLetterHandover"),
                    Strings.get("hqActionLetterHandoverPlace"), handover.open()));
        }
        for (SideJobLead lead : new SideJobLead[] {
                CivilianRescueIntel.openLead(state, day),
                DeadLetterIntel.openLead(state, day),
                DefectorAsylumIntel.openLead(state, day)}) {
            if (lead != null) items.add(sideJob(lead, plotCourse));
        }
        return items;
    }

    /** One lead per live arc, in the order the arcs open. */
    private static void storyLeads(MarineRoster roster, Consumer<SectorEntityToken> plotCourse,
                                   List<HqActionItem> items) {
        boolean waylineDone = roster != null && WaylineArc.stage(roster) == WaylineArc.Stage.COMPLETE;
        if (!waylineDone) {
            OpeningBroker.Destination destination = OpeningBroker.current();
            OpeningWorkGuide guide = OpeningWorkGuide.from(roster, destination, course(destination, plotCourse));
            if (!"opening-guide-hidden".equals(guide.markupProperty("classes")))
                items.add(story("story:opening", guide, destination));
        } else {
            var progress = SupplyRouteCampaign.current();
            if (progress == null || !progress.closed() || progress.successful()) {
                OpeningBroker.Destination destination = SupplyRouteCampaign.destination();
                items.add(story("story:supply-route",
                        new SupplyRouteWorkGuide(progress, destination, course(destination, plotCourse)), destination));
            }
        }
        var corvus = CorvusWaylineCampaign.current();
        if (corvus != null && !corvus.complete()) {
            OpeningBroker.Destination destination = CorvusWaylineCampaign.destination();
            items.add(story("story:corvus",
                    new CorvusWaylineWorkGuide(corvus, destination, course(destination, plotCourse)), destination));
        }
        var shepherd = ShepherdRecoveryCampaign.current();
        if (shepherd != null && !shepherd.departed()) {
            OpeningBroker.Destination destination = ShepherdRecoveryCampaign.destination();
            items.add(story("story:shepherd",
                    new ShepherdRecoveryWorkGuide(shepherd, destination, course(destination, plotCourse)), destination));
        }
        var agreus = AgreusArcCampaign.current();
        if (agreus != null) {
            OpeningBroker.Destination destination = AgreusArcCampaign.destination();
            items.add(story("story:agreus",
                    new AgreusWorkGuide(agreus, destination, course(destination, plotCourse)), destination));
        }
    }

    static HqActionItem obligation(CompanyClocks.Entry entry, int day, Runnable respond, float[] sector) {
        Action action = CompanyHqViewModel.answerable(entry)
                ? Action.of(Verb.RESPOND, respond)
                : Action.unavailable(Verb.TRACKING, Strings.get("hqActionTracked"));
        return new HqActionItem("obligation:" + entry.kind + ":" + entry.marketId + ":" + entry.deadlineDay,
                Kind.OBLIGATION, CompanyHqViewModel.obligationHeadline(entry),
                CompanyHqViewModel.obligationPlace(entry), Math.max(0, entry.daysRemaining(day)),
                action, entry, sector);
    }

    /** A story lead around its arc's guide; the guide is the detail pane's brief. */
    static HqActionItem story(String id, MarkupPropertySource guide, OpeningBroker.Destination destination) {
        Runnable course = (Runnable) guide.markupProperty("courseAction");
        boolean reachable = destination != null && destination.planet() != null;
        Action action = reachable ? Action.of(Verb.PLOT_COURSE, course)
                : Action.unavailable(Verb.PLOT_COURSE, Strings.get("hqActionNoDestination"));
        return new HqActionItem(id, Kind.STORY, String.valueOf(guide.markupProperty("heading")),
                String.valueOf(guide.markupProperty("location")), HqActionItem.NO_DEADLINE, action, guide,
                destination == null ? null : sector(destination.planet()));
    }

    static HqActionItem letter(String id, String title, String place, Runnable open) {
        return new HqActionItem(id, Kind.LETTER, title, place, HqActionItem.NO_DEADLINE,
                Action.of(Verb.OPEN, open), null, null);
    }

    static HqActionItem sideJob(SideJobLead lead, Consumer<SectorEntityToken> plotCourse) {
        SectorEntityToken entity = lead.market() == null ? null : lead.market().getPrimaryEntity();
        Action action = entity == null
                ? Action.unavailable(Verb.PLOT_COURSE, Strings.get("hqActionNoDestination"))
                : Action.of(Verb.PLOT_COURSE, () -> plotCourse.accept(entity));
        return new HqActionItem("side:" + lead.eventId(), Kind.SIDE_JOB, lead.title(), lead.place(),
                lead.daysLeft(), action, lead, sector(entity));
    }

    private static Runnable course(OpeningBroker.Destination destination, Consumer<SectorEntityToken> plotCourse) {
        return () -> {
            if (destination != null && destination.planet() != null) plotCourse.accept(destination.planet());
        };
    }

    private static float[] sector(MarketAPI market) {
        return market == null ? null : sector(market.getPrimaryEntity());
    }

    private static float[] sector(SectorEntityToken entity) {
        if (entity == null) return null;
        Vector2f at = entity.getLocationInHyperspace();
        return at == null ? null : new float[] {at.x, at.y};
    }
}
