Ship detail kit
===============

The shared detail language of the cinematic hulls, as code. Standard and
sizes: ship-detail-guide.md (one folder up). Read that first.

  ship_detail_kit.py  the module. Layer 1: primitives moved verbatim out of the
                      Legion and Colossus builds (both import them and still
                      build byte-identical geometry). Layer 2: metric builders
                      (window_row, round_port, deck_hatch, wall_door, ladder,
                      handrail, mushroom_vent, rcs_cluster, lamp_fitting, mast,
                      dish, seam_cut, skin_groove, armour_run, smooth, bevel)
                      taking sdk.Scale(metres_per_unit) and sizes in metres.
  check_detail.py     the detail check. Chain it after a build driver in the
                      same Blender process; flags come from $SDK_CHECK.
                      Reports faceted curves, unbevelled hard edges, emissives
                      off the lights layer, unframed glazing, large bare flat
                      faces and bare primitives; prints a CHECK_DETAIL json line.
  kit_sampler.py      one of each metric builder on a deckhouse: the worked
                      example and the kit's smoke test.
  guide_plates.py     composes the guide's figure plates (1920x1080) from the
                      accepted Legion/Colossus review renders on the Drive.

Use from a hull package (see legion-geometry/lg_kit.py, colossus-geometry/cl_kit.py):

  sys.path.insert(0, str(Path(__file__).resolve().parent.parent/'ship-detail-kit'))
  import ship_detail_kit as sdk
  S = sdk.Scale(METRES_PER_UNIT)
  class Kit(sdk.BaseKit): ...   # the hull's palette

Smoke test and check:

  SDK_CHECK="--mpu 0.5 --label sampler" sh ../../blender.sh --background --factory-startup \
      --python kit_sampler.py --python check_detail.py -- --mpu 0.5

Turrets are not here: turret-kit/ owns them.
Changing a Layer 1 primitive changes the Legion and Colossus: diff their
geometry before and after (identical up to element order is the bar).
