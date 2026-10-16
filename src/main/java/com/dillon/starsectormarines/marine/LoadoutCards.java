package com.dillon.starsectormarines.marine;

import java.util.ArrayList;
import java.util.List;

/** Every loadout card the built-in squad definitions can be acquired as. */
public final class LoadoutCards {

    private LoadoutCards() {}

    /**
     * Weapon loadouts and tactic sheets that add something a fresh company lacks and are
     * not reserved to a story, and whose presentation is declared. Starter issue (Dockside Issue, Watchman's Kit) is never a card.
     */
    public static List<LoadoutCard> all() {
        MarineArmory starter = new MarineArmory();
        List<LoadoutCard> cards = new ArrayList<>();
        for (SquadWeaponDoctrine doctrine : SquadEquipmentDoctrines.weaponDoctrines()) {
            cards.add(new LoadoutCard(LoadoutCard.Kind.WEAPON, doctrine.id()));
        }
        for (FirstRefit.Path path : FirstRefit.Path.values()) {
            cards.add(new LoadoutCard(LoadoutCard.Kind.WEAPON, path.weapons));
        }
        for (SquadArmorPlan plan : SquadEquipmentDoctrines.armorPlans()) {
            cards.add(new LoadoutCard(LoadoutCard.Kind.SHEET, plan.id()));
        }
        cards.removeIf(card -> card.templateIds().isEmpty() || card.storyReserved()
                || !card.presentationDeclared()
                || LoadoutCardLearning.status(starter, card.id())
                == LoadoutCardLearning.Result.ALREADY_KNOWN);
        return List.copyOf(cards);
    }
}
