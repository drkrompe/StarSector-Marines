package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.OfficerInduction.Step;

/** Castle's first-company lesson; all speech retains the shared Castle portrait. */
final class OfficerInductionGuide {
    private OfficerInductionGuide() { }

    static CompanyManagementGuide.Line line(Step step, String name) {
        return switch (step) {
            case SHIP -> new CompanyManagementGuide.Line("preparation-ship", "A HOME FOR THE COMPANY",
                    "First, Commander, choose a ship with quarters and a boat bay. I have counted the stores for our first lift and a full squad. Bring the complete bill aboard before we begin; then we can see this through together.");
            case LIFT -> new CompanyManagementGuide.Line("preparation-lift", "OUR FIRST LIFT",
                    "Our people need a way down and a way home. Choose an empty berth and commission its shuttle. One boat can ferry the squad in repeated trips; more boats bring them down together. Keep the squad's stores aside for our next stop.");
            case INTRODUCTION -> new CompanyManagementGuide.Line("officer-introduction", "MEET YOUR FIELD COMMANDER",
                    "Commander, " + name + " will lead our first formation. You set the course; she brings our people through it. Open her portrait on the left. Let's give her a proper start.");
            case SPECIALTY -> new CompanyManagementGuide.Line("officer-specialty", "HER FIRST SPECIALTY",
                    "She has one training point. Cover protects her troops; Recovery Planning helps our wounded return; Salvage Stewardship brings more home. Read all three, then choose. It is permanent. Her next promotion earns another point.");
            case FORMATION -> new CompanyManagementGuide.Line("officer-formation", "PEOPLE BEFORE ORDERS",
                    "Now she needs a formation. Raise a squad in Armory, then return here. Twelve Marines, thirty-six supplies and twenty-four food give them their first field kit and stores. Check the bill, then give our people a place on the roll.");
            case ASSIGNMENT -> new CompanyManagementGuide.Line("officer-assignment", "GIVE HER A COMMAND",
                    "Select the squad, open Organization, and choose " + name + " as its home command. Check the capacity, then Assign Command. This is her standing formation; you can reorganize it as the company grows.");
            case READY -> new CompanyManagementGuide.Line("officer-ready", "A COMPANY TAKING SHAPE",
                    "Good. She has a specialty and people to lead. At briefing, assign her squad to the mission. Every officer who participates earns their own service credit. Bring them home, and we will build on what they learn.");
            case COMPLETE -> new CompanyManagementGuide.Line("officer-complete", "READY", "");
        };
    }
}
