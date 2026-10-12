package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.OfficerInduction.Step;

/** Castle's first-company lesson; all speech retains the shared Castle portrait. */
final class OfficerInductionGuide {
    private OfficerInductionGuide() { }

    static CompanyManagementGuide.Line line(Step step, String name) {
        return switch (step) {
            case INTRODUCTION -> new CompanyManagementGuide.Line("officer-introduction", "MEET YOUR FIELD COMMANDER",
                    "Commander, " + name + " will lead our first formation. You set the course; she brings our people through it. Open her portrait on the left. Let's give her a proper start.");
            case SPECIALTY -> new CompanyManagementGuide.Line("officer-specialty", "HER FIRST SPECIALTY",
                    "She has one training point. Cover protects her troops; Recovery Planning helps our wounded return; Salvage Stewardship brings more home. Read all three, then choose. It is permanent. Her next promotion earns another point.");
            case FORMATION -> new CompanyManagementGuide.Line("officer-formation", "PEOPLE BEFORE ORDERS",
                    "Now she needs a formation. Raise a squad in Armory, then return here. We need twelve Marines, thirty-six supplies and twenty-four food. If the hold is short, pause here and resupply. No one deploys on a promise.");
            case ASSIGNMENT -> new CompanyManagementGuide.Line("officer-assignment", "GIVE HER A COMMAND",
                    "Select the squad, open Organization, and choose " + name + " as its home command. Check the capacity, then Assign Command. This is her standing formation; you can reorganize it as the company grows.");
            case READY -> new CompanyManagementGuide.Line("officer-ready", "A COMPANY TAKING SHAPE",
                    "Good. She has a specialty and people to lead. At briefing, assign her squad to the mission. Every officer who participates earns their own service credit. Bring them home, and we will build on what they learn.");
            case COMPLETE -> new CompanyManagementGuide.Line("officer-complete", "READY", "");
        };
    }
}
