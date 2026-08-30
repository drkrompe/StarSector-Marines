package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.ui.retained.markup.MarkupPropertySource;
import com.dillon.starsectormarines.ui.retained.reactive.MutableSignal;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;

/** Render-free projection for the retained selected-mech doctrine control. */
final class BattleMechOverlayModel {

    private static final String DEFAULT_STATE =
            "doctrine-state doctrine-state-default";
    private static final String OVERRIDDEN_STATE =
            "doctrine-state doctrine-state-overridden";

    private final MutableSignal<String> mechTitle;
    private final MutableSignal<String> mechIdentity;
    private final MutableSignal<String> deployedDoctrine;
    private final MutableSignal<String> effectiveDoctrine;
    private final MutableSignal<String> doctrineState;
    private final MutableSignal<String> doctrineStateClasses;
    private final MutableSignal<List<DoctrineCard>> doctrineCards;
    private final Runnable backAction;
    private final BiConsumer<Long, MechRole> doctrineRequest;
    private final Map<MechRole, Runnable> roleActions = new EnumMap<>(MechRole.class);

    private long selectedMechId;

    BattleMechOverlayModel(Reactor reactor, Runnable backAction,
                           BiConsumer<Long, MechRole> doctrineRequest) {
        this.backAction = backAction;
        this.doctrineRequest = doctrineRequest;
        mechTitle = reactor.signal("MECH");
        mechIdentity = reactor.signal("SELECTED MECH");
        deployedDoctrine = reactor.signal("DEPLOYED · --");
        effectiveDoctrine = reactor.signal("EFFECTIVE · --");
        doctrineState = reactor.signal("DEPLOYED DEFAULT");
        doctrineStateClasses = reactor.signal(DEFAULT_STATE);
        doctrineCards = reactor.signal(List.of());
    }

    Map<String, Object> props() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("mechTitle", mechTitle);
        props.put("mechIdentity", mechIdentity);
        props.put("deployedDoctrine", deployedDoctrine);
        props.put("effectiveDoctrine", effectiveDoctrine);
        props.put("doctrineState", doctrineState);
        props.put("doctrineStateClasses", doctrineStateClasses);
        props.put("doctrineCards", doctrineCards);
        props.put("backAction", backAction);
        props.put("defaultAction", (Runnable) () -> request(null));
        return props;
    }

    Presentation update(BattleSimulation sim, int selectedSquadId,
                        long selectedUnitEntityId) {
        if (sim == null || selectedSquadId < 0 || selectedUnitEntityId == 0L
                || !isLiveUnit(sim, selectedUnitEntityId)
                || !sim.world().isAlive(selectedUnitEntityId)
                || !sim.squad().hasSquad(selectedUnitEntityId)
                || sim.squad().squadId(selectedUnitEntityId) != selectedSquadId) {
            return hide();
        }

        Squad squad = sim.getSquad(selectedSquadId);
        if (squad == null || squad.aliveMembers <= 0
                || squad.faction != Faction.MARINE
                || !squad.isMechSquad() || squad.rescuePickupMech
                || sim.identity().faction(selectedUnitEntityId) != Faction.MARINE
                || !sim.identity().type(selectedUnitEntityId).isMech()) {
            return hide();
        }

        MechLoadoutComponent loadout = sim.world().mechLoadout(selectedUnitEntityId);
        if (loadout == null || loadout.deployedRole() == null
                || loadout.effectiveRole() == null) {
            return hide();
        }
        return updateProjected(new MechState(selectedUnitEntityId,
                sim.identity().name(selectedUnitEntityId), loadout.variant.displayName,
                loadout.deployedRole(), loadout.effectiveRole(),
                selectableRoles()));
    }

    Presentation updateProjected(MechState mech) {
        if (mech == null || mech.entityId() == 0L || mech.deployedRole() == null
                || mech.effectiveRole() == null || mech.availableRoles().isEmpty()) {
            return hide();
        }
        selectedMechId = mech.entityId();
        String variant = text(mech.variantName(), "MECH").toUpperCase(Locale.ROOT);
        String identity = text(mech.unitName(), variant).toUpperCase(Locale.ROOT);
        mechTitle.set(variant);
        mechIdentity.set(identity);
        deployedDoctrine.set("DEPLOYED · " + displayName(mech.deployedRole()));
        effectiveDoctrine.set("EFFECTIVE · " + displayName(mech.effectiveRole()));

        boolean overridden = mech.effectiveRole() != mech.deployedRole();
        doctrineState.set(overridden ? "PLAYER OVERRIDE" : "DEPLOYED DEFAULT");
        doctrineStateClasses.set(overridden ? OVERRIDDEN_STATE : DEFAULT_STATE);

        List<DoctrineCard> cards = new ArrayList<>(mech.availableRoles().size());
        for (MechRole role : mech.availableRoles()) {
            if (role == null) continue;
            boolean active = role == mech.effectiveRole();
            boolean deployed = role == mech.deployedRole();
            String id = cardId(role);
            String meta = active && deployed ? "ACTIVE · DEFAULT"
                    : active ? "ACTIVE" : deployed ? "DEFAULT" : "SELECT";
            String classes = "doctrine-card"
                    + (active ? " doctrine-card-active" : "")
                    + (deployed ? " doctrine-card-deployed" : "")
                    + (role == MechRole.LR_SUPPORT
                    ? " doctrine-card-long-range" : "");
            Runnable action = roleActions.computeIfAbsent(role,
                    key -> () -> request(key));
            cards.add(new DoctrineCard(id, id + "-name", id + "-meta",
                    compactDisplayName(role), meta, classes, active, action));
        }
        doctrineCards.set(List.copyOf(cards));
        return new Presentation(true);
    }

    private void request(MechRole role) {
        if (selectedMechId != 0L) doctrineRequest.accept(selectedMechId, role);
    }

    private Presentation hide() {
        selectedMechId = 0L;
        return new Presentation(false);
    }

    private static boolean isLiveUnit(BattleSimulation sim, long entityId) {
        for (int index = 0, count = sim.liveUnitCount(); index < count; index++) {
            if (sim.liveUnitAt(index) == entityId) return true;
        }
        return false;
    }

    static String cardId(MechRole role) {
        return "battle-mech-doctrine-"
                + role.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /** Keeps player doctrine order independent from compatibility enum order. */
    static List<MechRole> selectableRoles() {
        return List.of(MechRole.ASSAULT, MechRole.ARMORED_SUPPORT,
                MechRole.LR_SUPPORT, MechRole.BALANCED);
    }

    private static String displayName(MechRole role) {
        return role.displayName().toUpperCase(Locale.ROOT);
    }

    private static String compactDisplayName(MechRole role) {
        String name = displayName(role);
        return name.contains("LONG RANGE") ? "LR SUPPORT" : name;
    }

    private static String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    record Presentation(boolean visible) { }

    record MechState(long entityId, String unitName, String variantName,
                     MechRole deployedRole, MechRole effectiveRole,
                     List<MechRole> availableRoles) {
        MechState {
            availableRoles = availableRoles == null
                    ? List.of() : List.copyOf(availableRoles);
        }
    }

    record DoctrineCard(String id, String nameId, String metaId,
                        String name, String meta, String classes,
                        boolean disabled, Runnable action)
            implements MarkupPropertySource {
        @Override
        public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id;
                case "nameId" -> nameId;
                case "metaId" -> metaId;
                case "name" -> name;
                case "meta" -> meta;
                case "classes" -> classes;
                case "disabled" -> disabled;
                case "action" -> action;
                default -> throw new IllegalArgumentException(
                        "Unknown doctrine-card property: " + property);
            };
        }
    }
}
