package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.battle.mech.MechAttachmentService;
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
import java.util.function.LongConsumer;

/**
 * Render-free projection for the selected Mech's doctrine and attachment. The attachment row says
 * which squad the mech follows, if any, and offers DETACH; attaching is a right-click on a squad.
 */
final class BattleMechOverlayModel {

    private static final String DEFAULT_STATE =
            "doctrine-state doctrine-state-default";
    private static final String OVERRIDDEN_STATE =
            "doctrine-state doctrine-state-overridden";
    private static final String ATTACHMENT_FREE = "attachment-state attachment-state-free";
    private static final String ATTACHMENT_ATTACHED = "attachment-state attachment-state-attached";

    private final MutableSignal<String> mechTitle;
    private final MutableSignal<String> mechIdentity;
    private final MutableSignal<String> deployedDoctrine;
    private final MutableSignal<String> effectiveDoctrine;
    private final MutableSignal<String> doctrineState;
    private final MutableSignal<String> doctrineStateClasses;
    private final MutableSignal<String> attachmentState;
    private final MutableSignal<String> attachmentClasses;
    private final MutableSignal<Boolean> detachDisabled;
    private final MutableSignal<List<DoctrineCard>> doctrineCards;
    private final Runnable backAction;
    private final BiConsumer<Long, MechRole> doctrineRequest;
    private final LongConsumer detachRequest;
    private final Map<MechRole, Runnable> roleActions = new EnumMap<>(MechRole.class);

    private long selectedMechId;

    BattleMechOverlayModel(Reactor reactor, Runnable backAction,
                           BiConsumer<Long, MechRole> doctrineRequest,
                           LongConsumer detachRequest) {
        this.backAction = backAction;
        this.doctrineRequest = doctrineRequest;
        this.detachRequest = detachRequest;
        mechTitle = reactor.signal(Strings.get("battleMechTitle"));
        mechIdentity = reactor.signal(Strings.get("battleMechSelected"));
        deployedDoctrine = reactor.signal(Strings.format("battleMechDeployed", "--"));
        effectiveDoctrine = reactor.signal(Strings.format("battleMechEffective", "--"));
        doctrineState = reactor.signal(Strings.get("battleMechDoctrineDefault"));
        doctrineStateClasses = reactor.signal(DEFAULT_STATE);
        attachmentState = reactor.signal(Strings.get("battleMechUnattached"));
        attachmentClasses = reactor.signal(ATTACHMENT_FREE);
        detachDisabled = reactor.signal(true);
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
        props.put("attachmentState", attachmentState);
        props.put("attachmentClasses", attachmentClasses);
        props.put("detachDisabled", detachDisabled);
        props.put("detachAction", (Runnable) this::requestDetach);
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
        return updateProjected(new MechState(selectedSquadId, selectedUnitEntityId,
                sim.identity().name(selectedUnitEntityId), loadout.variant.displayName(),
                loadout.deployedRole(), loadout.effectiveRole(),
                anchorLabel(sim, sim.getMechAttachmentService().anchorOf(selectedSquadId)),
                selectableRoles()));
    }

    Presentation updateProjected(MechState mech) {
        if (mech == null || mech.squadId() < 0 || mech.entityId() == 0L
                || mech.deployedRole() == null
                || mech.effectiveRole() == null
                || mech.availableRoles().isEmpty()) {
            return hide();
        }
        selectedMechId = mech.entityId();
        String variant = Strings.upper(text(mech.variantName(), Strings.get("battleMechTitle")));
        String identity = Strings.upper(text(mech.unitName(), variant));
        mechTitle.set(variant);
        mechIdentity.set(identity);
        deployedDoctrine.set(Strings.format("battleMechDeployed", displayName(mech.deployedRole())));
        effectiveDoctrine.set(Strings.format("battleMechEffective", displayName(mech.effectiveRole())));

        boolean overridden = mech.effectiveRole() != mech.deployedRole();
        doctrineState.set(Strings.get(overridden ? "battleMechDoctrineOverride" : "battleMechDoctrineDefault"));
        doctrineStateClasses.set(overridden ? OVERRIDDEN_STATE : DEFAULT_STATE);

        boolean attached = mech.anchorLabel() != null;
        attachmentState.set(attached ? Strings.format("battleMechAttachedTo", Strings.upper(mech.anchorLabel()))
                : Strings.get("battleMechUnattached"));
        attachmentClasses.set(attached ? ATTACHMENT_ATTACHED : ATTACHMENT_FREE);
        detachDisabled.set(!attached);

        List<DoctrineCard> cards = new ArrayList<>(mech.availableRoles().size());
        for (MechRole role : mech.availableRoles()) {
            if (role == null) continue;
            boolean active = role == mech.effectiveRole();
            boolean deployed = role == mech.deployedRole();
            String id = cardId(role);
            String meta = Strings.get(active && deployed ? "battleMechActiveDefault"
                    : active ? "battleMechActive" : deployed ? "battleMechDefault" : "battleMechSelect");
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

    private void requestDetach() {
        if (selectedMechId != 0L) detachRequest.accept(selectedMechId);
    }

    /** The followed squad's name as its own plate shows it, or {@code null} when the mech follows nobody. */
    private static String anchorLabel(BattleSimulation sim, int anchorId) {
        if (anchorId == MechAttachmentService.NONE) return null;
        Squad anchor = sim.getSquad(anchorId);
        if (anchor == null) return null;
        return anchor.campaignLabel != null && !anchor.campaignLabel.isBlank()
                ? anchor.campaignLabel : Strings.format("battleSquadNumbered", anchor.id);
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
        return Strings.upper(role.displayName());
    }

    private static String compactDisplayName(MechRole role) {
        String key = "battleMechCompact_" + role.name();
        String compact = Strings.get(key);
        return compact.equals(key) ? displayName(role) : compact;
    }

    private static String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    record Presentation(boolean visible) { }

    record MechState(int squadId, long entityId, String unitName, String variantName,
                     MechRole deployedRole, MechRole effectiveRole,
                     String anchorLabel,
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
