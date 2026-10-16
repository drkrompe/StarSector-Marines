SECOND WIND - CAMPAIGN MATERIAL REFERENCES

Scope
Skinning only, on the accepted V3 design. V3 remains the relatively clean/medium
condition baseline. These variants depict the ship's campaign condition, with no
new geometry, engine/pod locations, capacity, room layout or gameplay changes.
The turquoise pod was a geometry identification marker, not established livery.
All campaign skins replace it with worn cream, oxide-orange and gunmetal.

States
arrival-heavy.reference.png (current pre-Jangala direction):
Revises arrival.reference.png after user feedback: eight to ten years of micro-
and small asteroid strikes leave unmistakable physical craters, buckled/spalled
outer armor and gouged plate seams. Salvaged patches, splints and welded backing
keep the ship barely serviceable. Primary hull/hardpoint geometry remains fixed.
This is also the damage source for the first composited arrival cinematic.

arrival.reference.png:
Earlier, less severely damaged arrival study, preserved for comparison.
Arrival at Jangala, AFTER the crew's emergency recovery and escape from the Claim
but BEFORE the berthing conversion. The four point-defence/turret mounts are
already installed. Essential pressure boundaries and propulsion work. It is not
the earlier stripped, unpowered parts-bin hulk. Old damage and makeshift repairs
remain pervasive.

after-refit.reference.png:
Earlier refit study derived from the less damaged arrival sheet. It has not been
regenerated from the new heavy skin; do not use it to imply the limited conversion
repairs all of the newly established crater damage.
The same ship immediately after the limited berthing/command conversion. Limited
new seams and local replacement plates signal recent work; most exterior scars
and damaged/sealed facilities remain. This is not a complete exterior restoration
or a repaired-boat award. The medium-condition V3 baseline is not this receipt.

Campaign evidence
mod/data/strings/strings.json:
- openingCompanyPrologueTheCerberusByTheLoadingLocksHad (line 1643 at authoring):
  Nearly a decade tethered as a parts bin, shields cold and point defence stripped;
  small belt-rock impacts. Salvaged drive coil, hab pressure seals, navigation
  core and reinstalled gantry point-defence mounts make the escape possible.
- openingCompanyPrologueTheDepartureRosterIsInYourHandwriting (1644):
  SECOND WIND painted over the old registry during the final pressure-patch check.
- openingYardSurveyNarrative (19):
  Small escape patches, scorched plating, borrowed seals, and a bent rib shored
  with cargo rail. Only some compartments hold pressure.
- openingYardHomeNarrative / openingYardOrderNarrative / openingYardHomeDialogue
  (17-20): usable quarters, eighteen added personnel places, permanent command
  conversion, twenty-five cargo capacity surrendered. Second boat bay, main
  machine shop, mess, spare bunkrooms and Mech Lab remain damaged; both Hermes
  still need work. Sealed rooms stay sealed.
- openingCompanyYardCMDNessaVennMetUsAtTheBerth (1425):
  A patch under the collar already exists at arrival.

Current transaction: src/main/java/com/dillon/starsectormarines/campaign/start/CompanyYard.java
Canonical condition: roadmap/company-view/design/company-view-nouns.md
  1413-1465; roadmap/ship-interiors/design/ship-interiors-nouns.md 1515-1567.
Older unused yard strings suggesting an immediate usable workshop/gantry are not
the authority for this condition. Medical is not an authored sealed wreck.
Normal hull repair is distinct from these saved interior restoration receipts.

Visual continuity
Keep the same three readable exterior scars between states. These skins were
painted on the V2/V3 blockout, whose port side pod no longer exists; on the
current geometry the scars are: a patched impact cluster on the forward port
flank around the grapple sponson and the port bow cheek above it; a scarred
port aft shoulder, the cowl of the port engine pod (dead and capped at
arrival); and scorched waist service covers (the waist service lines and
port trunks). These exterior locations are ART INTERPRETATION, not an
assertion of precise named compartment positions. Preserve silhouette, all
four turrets, engine count/positions, bay opening and the port grapple.
Modeled condition geometry now exists for the whole ladder:
../second-wind-geometry/states/{derelict,patched,refit,restored}/, built from
one scar table (../second-wind-geometry/states/scar-table.md) so each scar id
is the same scar in every state. Patched is the arrival condition these skins
paint: only the starboard pod's two engines work, on the salvaged drive coil;
the port pod is dead and cold, its coil cradle empty and feed lines
blind-flanged; the starboard bay door is welded shut. See ../second-wind-geometry/README.txt. Earlier studies kept damage to coatings and thin covers;
the current heavy-arrival direction also deforms local outer armor.
No active fire, catastrophic pressure holes, new engines, missing guns, launched
boats, exposed occupied rooms or people. No mapping named interior rooms to
invented exterior labels. Fine generated surface details are conceptual, not a
consistent UV texture for the Blender model.

Latest damage direction intentionally goes beyond the earlier coating-only wear:
crushed/spalled outer armor, crater depth and jury-rigged pressure backing are
visible. These local material/skin deformations do not relocate major components.
The current prologue and room-condition line now say "nearly a decade", matching
the user's approximate eight-to-ten-year history; the unrelated eleven-day
evacuation deadline remains unchanged.

Sources and regeneration
Geometry source: ../second-wind-geometry/second-wind-geometry.blend
Accepted surface/layout target: ../second-wind-design-v3.reference.png
Images edited using built-in image_gen. Exact prompts live alongside each image.
The retained post-refit study derives from the earlier arrival skin.
All .reference.png outputs remain outside the runtime *.master.png packer.
Existing campaign scene images and the accepted geometry model are unchanged.

Review
Both retained images are 1672x941 RGB PNGs and decode successfully. Visual review
against V3 and between states confirmed the gross hull geometry, four turret
positions, low horizontal pod, four-nozzle stern arrangement and side-view
occlusion. Blue is absent. Refit work stays local to the forward collar while
major old scars and aft damage remain. Minor generated lettering and surface
detail differences are conceptual; these are reference skins, not UV textures.

The current heavy-arrival sheet also decodes at 1672x941. Comparison against the
earlier arrival confirms deeper craters, torn/buckled plates, exposed backing and
overlapping emergency patches while preserving the principal geometry. There is
no blue pod, active fire or venting. The hull is damaged but patched to reach port.
