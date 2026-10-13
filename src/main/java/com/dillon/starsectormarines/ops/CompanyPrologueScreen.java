package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.start.CerberusCompanyStart;
import com.dillon.starsectormarines.campaign.start.CompanyPrologue;
import com.dillon.starsectormarines.campaign.start.CompanyYard;
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
                cargo.getCrew(), (int) cargo.getCredits().get(), CompanyYard.reservedCredits(CerberusCompanyStart.startingShip()),
                this::next, this::back, this::finish);
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
                                            int crew, int credits, int reserve, Runnable next, Runnable back, Runnable skip) {
        String port = destination == null ? "a working planetary spaceport" : destination;
        String[] titles = {"SECOND WIND", "WHAT WE LEFT BEHIND", "THE DEPARTURE ROSTER",
                "SOMEWHERE TO DOCK", "A WAY TO EARN", "THE FIRST STEP"};
        String[] narrative = {
                "The alarm above the hatch has finally gone quiet. Castle still glances at it whenever the drive changes pitch. "
                        + "Someone has wedged a folded ore-shift roster under the navigation console to stop it rattling. Harker's Claim is several jumps behind you.",
                "The last ore carrier took the mine's administrators with it. At Harker's Claim, the remaining workers kept reporting for shifts until the ration counter stopped accepting company scrip. "
                        + "The Cerberus beside the loading locks had been a parts store for eleven years. You still have the list of what you couldn't replace.",
                "The departure roster is in your handwriting. Castle's additions run down the margin: fuel, seals, one more person who needed a berth. "
                        + "Nobody held a vote about the command chair. Someone painted SECOND WIND over the old registry while you were checking the last pressure patch.",
                jangala ? "Corvus has working yards and regular traffic. Hegemony patrols cross the navigation display. "
                        + "Castle scrolls past the public yard listings and opens a private address: Nessa Venn, Jangala. Her last message is years old. Castle reads it before starting a new one."
                        : "Castle searches the port listings for somewhere that will take a civilian ship and can actually do the work. "
                        + "A published berth and a functioning yard are more useful than another promise.",
                "A cargo hand sleeps beside the bridge hatch, boots still on. The crew has been taking turns with the sound bunks. "
                        + "Below them are two wrecked Hermes and rooms you haven't dared reconnect to the power bus. Castle has been reading the surface security notices.",
                "Castle puts the yard estimate beside the old shift roster. For a moment she holds it flat, covering the mine operator's crest. "
                        + "Then she moves it aside so you can reach the navigation controls."
        };
        String[] dialogue = {
                "You can let go of the chair now. She's holding. I sent everyone I could spare to get some sleep. "
                        + "They keep asking if we're going back. Told them you'd already picked a direction.",
                "Still had a month's pay on my account at the Claim. Kept the chit, for some reason. "
                        + (reserve > 0 ? "The real money's mostly spoken for. I've kept the yard payment safe; shuttle parts and stores for our first squad are already paid. "
                        : "I've paid for shuttle parts and stores for our first squad. We'll need to keep enough back for the yard. ")
                        + "What's left in the account is ours. Try to make it last.",
                "You remember the night they shut off the lower habs? You were still down there, running cable from this wreck. "
                        + "That's when people started bringing me their names. Not when we got the drive lit. "
                        + "So you can stop looking at me every time someone says 'Captain.'",
                destination == null ? "No answer from a yard we can reach. I'll keep trying. We didn't come all this way to live on emergency power."
                        : jangala ? "Nessa Venn. We ran ore together before she settled at the yards on Jangala. Once spent three days towing my dead freighter home, "
                        + "swearing she'd leave me at the next buoy. Never did. She'll have plenty to say about this ship. Let me talk first."
                        : port + " has a working yard. I don't know anyone there. We'll ask for a berth and a written estimate, and keep the story short.",
                "You said we could take contract work once we were clear. I've been looking. Depots, convoy stops, places that need a few people with rifles. "
                        + "We'll have to hire marines. The people asleep out there have done enough just getting us here. One shuttle first. See what we can manage.",
                (destination == null ? "We'll find a berth. Then bunks, a workshop, and someone who can tell us which of those shuttles is worth saving. "
                        : jangala ? "Nessa sent an estimate. Called the job 'ambitious' twice. That's better than 'scrap.' "
                        + "Five thousand for the conversion, and she's found space for our stores. "
                        : "We'll get the yard at " + port + " to do the conversion. Five thousand, if they hold to the estimate. ")
                        + (reserve > 0 ? "I've kept the payment safe."
                        : "We'll have to watch that yard money.")
                        + " Then we get a boat flying and a squad aboard. After that... well. We might actually have a company, Captain."
        };
        int index = Math.max(0, Math.min(CompanyPrologue.PAGE_COUNT - 1, page));
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("title", titles[index]); props.put("narrative", narrative[index]);
        props.put("dialogue", dialogue[index]); props.put("portrait", CompanyManagementGuide.portrait());
        props.put("image", IMAGE); props.put("page", (index + 1) + " / " + CompanyPrologue.PAGE_COUNT);
        props.put("manifest", crew + " crew aboard  /  " + String.format(Locale.ROOT, "%,d", credits) + " spendable credits");
        props.put("reserve", reserve > 0 ? String.format(Locale.ROOT, "%,d", reserve) + " credits held for the conversion / first boat and squad stores prepaid"
                : "Conversion payment due from fleet funds / preparation stores prepaid");
        props.put("destination", destination == null ? "Find a working planetary spaceport" : "Reach " + destination);
        props.put("nextLabel", index < CompanyPrologue.PAGE_COUNT - 1 ? "Continue"
                : destination == null ? "Take command" : "Set course for " + destination);
        props.put("backDisabled", index == 0);
        props.put("next", next); props.put("back", back); props.put("skip", skip);
        return props;
    }
}
