SECOND WIND - CAMPAIGN MATERIAL REFERENCES

Scope
Skinning only, on the accepted V3 design. V3 remains the relatively clean/medium
condition baseline. These variants depict the ship's campaign condition, with no
new geometry, engine/pod locations, capacity, room layout or gameplay changes.
The turquoise pod was a geometry identification marker, not established livery.
All campaign skins replace it with worn cream, oxide-orange and gunmetal.

States
arrival.reference.png:
Arrival at Jangala, AFTER the crew's emergency recovery and escape from the Claim
but BEFORE the berthing conversion. The four point-defence/turret mounts are
already installed. Essential pressure boundaries and propulsion work. It is not
the earlier stripped, unpowered parts-bin hulk. Old damage and makeshift repairs
remain pervasive.

after-refit.reference.png:
The same ship immediately after the limited berthing/command conversion. Limited
new seams and local replacement plates signal recent work; most exterior scars
and damaged/sealed facilities remain. This is not a complete exterior restoration
or a repaired-boat award. The medium-condition V3 baseline is not this receipt.

Campaign evidence
mod/data/strings/strings.json:
- openingCompanyPrologueTheCerberusByTheLoadingLocksHad (line 1643 at authoring):
  Eleven years tethered as a parts bin, shields cold and point defence stripped;
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
Keep the same three readable exterior scars between states: a patched impact
cluster above the forward port pod / under the armor collar; a scarred port aft
shoulder; and scorched waist service covers. These exterior locations are ART
INTERPRETATION, not an assertion of precise named compartment positions.
Preserve silhouette, all four turrets, engine count/positions, bay opening and
horizontal side pod. Damage stays local to coatings, thin outer skins and covers.
No active fire, catastrophic pressure holes, new engines, missing guns, launched
boats, exposed occupied rooms or people. No mapping named interior rooms to
invented exterior labels. Fine generated surface details are conceptual, not a
consistent UV texture for the Blender model.

Sources and regeneration
Geometry source: ../second-wind-geometry-v3/second-wind-geometry.blend
Accepted surface/layout target: ../second-wind-design-v3.reference.png
Images edited using built-in image_gen. Exact prompts live alongside each image.
Post-refit derives from the accepted arrival skin to preserve the damage history.
Both .reference.png outputs remain outside the runtime *.master.png packer.
Existing campaign scene images and the accepted geometry model are unchanged.

Review
Both retained images are 1672x941 RGB PNGs and decode successfully. Visual review
against V3 and between states confirmed the gross hull geometry, four turret
positions, low horizontal pod, four-nozzle stern arrangement and side-view
occlusion. Blue is absent. Refit work stays local to the forward collar while
major old scars and aft damage remain. Minor generated lettering and surface
detail differences are conceptual; these are reference skins, not UV textures.
