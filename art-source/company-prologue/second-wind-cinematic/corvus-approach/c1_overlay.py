"""C1 overlay: anything drawn over the finished frame in screen space (labels, subtitles).

Owned by the UI pass. `apply(img, t, duration)` takes and returns the 1920x1080 RGB frame;
it does not move with the camera. Empty until that pass lands.
"""


def apply(img, t, duration):
    return img
