# Open pit voids: the bench face

ImageGen master for the open pit (`voids.md`): a blasted bench face in an open-cut
ore mine, seen straight on, laid along the pit's faces by `NaturalVoidRenderSystem`.
The master keeps its brief (`*.prompt.txt`) and provenance (`*.png.provenance.json`).

The shipped tile, `mod/graphics/battle/voids/pit-bench-wall.png`, is the master made
seamless and sized to 1024 with the sky port's script:

    python art-source/voids/sky-port/make_seamless.py art-source/voids/open-pit/pit-bench-wall.png mod/graphics/battle/voids/pit-bench-wall.png --size 1024

## The megaworks

`concept-crawler-and-spine.png` is the concept the owner's pick builds on (`raid-mine-megaworks`, option a), with
its brief. `pit-megaworks.png` is the ImageGen master for `PitMachines`: eight pieces overhead, fronts and runs to
the east, four columns by two rows in `PitMachines.Kind` order (cutter, crusher, loaded and empty ore crawlers,
drilling gantry, transfer tower, gallery bay, bench hopper). It was filled in over `megaworks-guide.png`, which
`megaworks-guide.py` draws with a scale of its own for each frame (`Kind.pxPerMeter`). The concept was the design
reference, and the parked vehicles' raw sheet the finish reference. Its brief is `pit-megaworks.prompt.txt` and its
provenance `pit-megaworks.png.provenance.json`. It ships unchanged as `mod/graphics/battle/voids/pit-megaworks.png`;
the renderer cuts the frames by quarters and halves.
