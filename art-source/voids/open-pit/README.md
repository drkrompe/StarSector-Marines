# Open pit voids: the bench face

ImageGen master for the open pit (`voids.md`): a blasted bench face in an open-cut
ore mine, seen straight on, laid along the pit's faces by `NaturalVoidRenderSystem`.
The master keeps its brief (`*.prompt.txt`) and provenance (`*.png.provenance.json`).

The shipped tile, `mod/graphics/battle/voids/pit-bench-wall.png`, is the master made
seamless and sized to 1024 with the sky port's script:

    python art-source/voids/sky-port/make_seamless.py art-source/voids/open-pit/pit-bench-wall.png mod/graphics/battle/voids/pit-bench-wall.png --size 1024

## The megaworks

The owner picked the mass driver (`raid-mine-megaworks`, option c). `concept-mass-driver.png` is that concept, with its
brief. `pit-massdriver.png` is the ImageGen master the game now draws: eight pieces overhead, fronts and runs to the
east, four columns by two rows in `PitMachines.Kind` order (cutter, crusher and capsule loader, loaded and empty hover
haulers, drilling gantry, catch station, rail bay, ore capsule). It was filled in over `massdriver-guide.png`
(`massdriver-guide.py`, a scale of its own for each frame), with the concept as design reference and the earlier
sheet below as finish reference. Its brief is `pit-massdriver.prompt.txt` and its provenance
`pit-massdriver.png.provenance.json`. `massdriver-export.py` ships it as `mod/graphics/battle/voids/pit-massdriver.png`
with its near-invisible haze cleared.

The earlier crawler-and-spine sheet, kept as the record of option a and the finish reference:

`concept-crawler-and-spine.png` is the concept the owner's pick builds on (`raid-mine-megaworks`, option a), with
its brief. `pit-megaworks.png` is the ImageGen master for `PitMachines`: eight pieces overhead, fronts and runs to
the east, four columns by two rows in `PitMachines.Kind` order (cutter, crusher, loaded and empty ore crawlers,
drilling gantry, transfer tower, gallery bay, bench hopper). It was filled in over `megaworks-guide.png`, which
`megaworks-guide.py` draws with a scale of its own for each frame (`Kind.pxPerMeter`). The concept was the design
reference, and the parked vehicles' raw sheet the finish reference. Its brief is `pit-megaworks.prompt.txt` and its
provenance `pit-megaworks.png.provenance.json`. It no longer ships.
