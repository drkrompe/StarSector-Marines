# Open pit voids: the bench face

ImageGen master for the open pit (`voids.md`): a blasted bench face in an open-cut
ore mine, seen straight on, laid along the pit's faces by `NaturalVoidRenderSystem`.
The master keeps its brief (`*.prompt.txt`) and provenance (`*.png.provenance.json`).

The shipped tile, `mod/graphics/battle/voids/pit-bench-wall.png`, is the master made
seamless and sized to 1024 with the sky port's script:

    python art-source/voids/sky-port/make_seamless.py art-source/voids/open-pit/pit-bench-wall.png mod/graphics/battle/voids/pit-bench-wall.png --size 1024
