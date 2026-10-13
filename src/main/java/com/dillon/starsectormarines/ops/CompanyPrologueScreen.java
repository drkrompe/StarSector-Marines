package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.start.CerberusCompanyStart;
import com.dillon.starsectormarines.campaign.start.CompanyPrologue;
import com.fs.starfarer.api.Global;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** The escape story, before the ordinary paid preparation flow begins. */
public final class CompanyPrologueScreen extends MissionFlowMlxScreen {
    public static final String ROOT = "company-prologue";
    public static final List<String> COMPONENTS = List.of("data/ui/components/company/company-prologue.mlx");
    public static final String IMAGE = "graphics/illustrations/company/second-wind.jpg";

    public CompanyPrologueScreen() { super(ROOT, COMPONENTS); }
    @Override protected List<String> requiredElementIds() {
        return List.of("prologue-root", "prologue-next", "prologue-skip", "prologue-dialogue");
    }
    @Override protected Map<String, Object> props() {
        var planet = CerberusCompanyStart.destination();
        var cargo = Global.getSector().getPlayerFleet().getCargo();
        return model(CompanyPrologue.page(Global.getSector().getPersistentData()),
                planet == null ? null : planet.getName(), planet != null && "jangala".equals(planet.getId()),
                cargo.getCrew(), (int) cargo.getCredits().get(), this::next, this::back, this::finish);
    }
    private void next() {
        var saved = Global.getSector().getPersistentData();
        int page = CompanyPrologue.page(saved);
        if (page == CompanyPrologue.PAGE_COUNT - 1) { finish(); return; }
        CompanyPrologue.moveTo(saved, page + 1);
        rebuildDocument();
    }
    private void back() {
        var saved = Global.getSector().getPersistentData();
        CompanyPrologue.moveTo(saved, CompanyPrologue.page(saved) - 1);
        rebuildDocument();
    }
    private void finish() {
        var sector = Global.getSector();
        var destination = CerberusCompanyStart.destination();
        if (destination != null) sector.getCampaignUI().layInCourseForNextStep(destination);
        CompanyPrologue.finish(sector.getPersistentData());
        dismissDialog.run();
    }

    /** Shared production projection for live UI and visual evidence. */
    public static Map<String, Object> model(int page, String destination, boolean jangala,
                                            int crew, int credits, Runnable next, Runnable back, Runnable skip) {
        String port = destination == null ? "a working planetary spaceport" : destination;
        String[] titles = {"SECOND WIND", "WHAT WE LEFT BEHIND", "WHY THEY CALL YOU CAPTAIN",
                "SOMEWHERE TO DOCK", "A WAY TO EARN", "THE FIRST STEP"};
        String[] narrative = {
                "The drive settles into an uneven hum. Beyond the bridge glass, navigation lights move against the stars. "
                        + "For the first time since leaving the colony, nobody is shouting over an alarm. Castle checks the pressure readings twice before turning to you.",
                "The mine stopped paying. Its operator withdrew, the supply ships stopped coming, and evacuation berths cost more than most workers had saved. "
                        + "Beside the freight yard stood a grounded Cerberus, stripped for parts and long since written off. Your crew spent weeks making it fly.",
                "You brought the repair crews together and took responsibility for the departure. Castle found fuel, called in favors, and kept the port paperwork moving. "
                        + "When someone had to take the command chair, the others looked to you. Before launch, they painted a new name over the old registry: Second Wind.",
                jangala ? "Corvus has working yards and regular traffic. Hegemony patrols cross the navigation display. "
                        + "After the silence around the colony, even a routine demand for identification sounds reassuring. Castle has an old yard contact on Jangala."
                        : "Castle searches the port listings for somewhere that will take a civilian ship and can actually do the work. "
                        + "A published berth and a functioning yard are more useful than another promise.",
                "The people keeping this ship alive are mechanics, cargo hands, and watchkeepers. The flight systems work; much of the interior does not. "
                        + "Neither wrecked Hermes in the boat bays can carry a landing party. There is no marine company yet.",
                "Castle leaves the yard estimate beside the navigation console. The money left in the account belongs to the people aboard. "
                        + "Getting off the colony was the first promise. Giving them a future will take more than keeping the engines running."
        };
        String[] dialogue = {
                "Drive's holding. Pressure's stable in the occupied compartments. I'd call that an arrival. Get your breath back, Captain. We made it out.",
                "We sold what we could carry and pooled the rest. I've kept a travel reserve aboard and arranged a small prepaid consignment for the yard. "
                        + "Enough to get started. Once it's gone, it's gone.",
                "I've kept convoys supplied and crews paid. I can handle the stores and the paperwork. "
                        + "But you got these people off that rock. That's why they're calling you captain. Now we need somewhere to go.",
                destination == null ? "I can't find an accessible working port in these listings. We'll need one before we can make anything of this ship. "
                        + "I'll keep the yard requirements in your Intel log."
                        : jangala ? "Jangala's taking civilian traffic. I know someone at the yards who might still answer. "
                        + "Let's get a berth before anything else decides to fail."
                        : port + " has a working spaceport we can use. We'll try the yard there. First a berth, then the estimate.",
                "There are small surface security contracts. We hire trained marines, repair a shuttle, and give them somewhere to sleep. "
                        + "Our crew keeps the ship running. I'm not sending them down with mining tools and calling them soldiers.",
                "Dock at " + port + " and choose Marine operations in the planet menu. I'll show you the Mercenary Company Conversion order: "
                        + "5,000 credits for the yard work. Then we repair the first Hermes and muster a squad using our local prepaid stores. "
                        + "One working boat. One proper squad. We'll start there."
        };
        int index = Math.max(0, Math.min(CompanyPrologue.PAGE_COUNT - 1, page));
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("title", titles[index]); props.put("narrative", narrative[index]);
        props.put("dialogue", dialogue[index]); props.put("portrait", CompanyManagementGuide.portrait());
        props.put("image", IMAGE); props.put("page", (index + 1) + " / " + CompanyPrologue.PAGE_COUNT);
        props.put("manifest", crew + " crew aboard  /  " + String.format(Locale.ROOT, "%,d", credits) + " credits");
        props.put("destination", destination == null ? "Find a working planetary spaceport" : "Reach " + destination);
        props.put("nextLabel", index < CompanyPrologue.PAGE_COUNT - 1 ? "Continue"
                : destination == null ? "Take command" : "Set course for " + destination);
        props.put("backDisabled", index == 0);
        props.put("next", next); props.put("back", back); props.put("skip", skip);
        return props;
    }
}
