# Two 768x768 registered frames. Left-facing hull and twin-cannon turret.
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$image = [System.Drawing.Bitmap]::new(1536,768)
$g = [System.Drawing.Graphics]::FromImage($image)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.Clear([System.Drawing.Color]::Transparent)
function Rect($color,$x,$y,$w,$h) {
    $brush=[System.Drawing.SolidBrush]::new([System.Drawing.ColorTranslator]::FromHtml($color))
    $g.FillRectangle($brush,$x,$y,$w,$h); $brush.Dispose()
}
function Oval($color,$x,$y,$w,$h) {
    $brush=[System.Drawing.SolidBrush]::new([System.Drawing.ColorTranslator]::FromHtml($color))
    $g.FillEllipse($brush,$x,$y,$w,$h); $brush.Dispose()
}
function Poly($color,$points) {
    $brush=[System.Drawing.SolidBrush]::new([System.Drawing.ColorTranslator]::FromHtml($color))
    $vertices=[System.Drawing.PointF[]]@($points | ForEach-Object { [System.Drawing.PointF]::new($_[0],$_[1]) })
    $g.FillPolygon($brush,$vertices); $brush.Dispose()
}
# Long armored track pods, chamfered leading ends, squared trailing sprocket guards.
Poly '#262e29' @(@(70,132),@(115,84),@(711,84),@(736,109),@(736,215),@(115,215),@(70,185))
Poly '#262e29' @(@(70,636),@(115,684),@(711,684),@(736,659),@(736,553),@(115,553),@(70,583))
foreach ($x in 112..704 | Where-Object { ($_ - 112) % 24 -eq 0 }) {
    Rect '#424a40' $x 91 12 116
    Rect '#424a40' $x 561 12 116
}
# Center hull: projecting wedge nose at left, broad shoulders, squared engine deck aft.
Poly '#4b5944' @(@(32,314),@(113,232),@(234,168),@(687,168),@(727,210),@(727,558),@(687,600),@(234,600),@(113,536),@(32,454))
Poly '#a3ad8b' @(@(32,314),@(113,232),@(234,168),@(278,238),@(149,294),@(89,350),@(89,418),@(149,474),@(278,530),@(234,600),@(113,536),@(32,454))
Poly '#7b896b' @(@(89,350),@(149,294),@(278,238),@(278,530),@(149,474),@(89,418))
# Raised shoulder armor, layered applique blocks and darker downward slopes.
Poly '#8f9b7d' @(@(234,168),@(687,168),@(686,225),@(278,238))
Poly '#354434' @(@(234,600),@(687,600),@(686,543),@(278,530))
foreach ($x in @(300,407,514)) {
    Rect '#697a5b' $x 177 88 42
    Rect '#697a5b' $x 549 88 42
}
# Driver position behind glacis; twin towing eyes make the prow distinct.
Rect '#263c3e' 235 310 32 45
Rect '#263c3e' 235 413 32 45
Rect '#4a5041' 61 309 22 28
Rect '#4a5041' 61 431 22 28
# Opaque ring, true center at (384,384), no gun baked into the hull.
Oval '#303a30' 279 279 210 210
Oval '#a0aa8c' 289 289 190 190
Oval '#59674f' 303 303 162 162
# Rear engine deck has two long recessed grilles, rear exhausts and red lamps.
Rect '#728164' 526 243 160 282
Rect '#242e28' 544 261 121 105
Rect '#242e28' 544 402 121 105
foreach ($y in 270..498 | Where-Object { (($_ - 270) % 15 -eq 0) -and ($_ -lt 360 -or $_ -gt 405) }) { Rect '#475341' 549 $y 111 5 }
Rect '#29352d' 693 267 23 97
Rect '#29352d' 693 404 23 97
Rect '#a04c39' 699 235 15 17
Rect '#a04c39' 699 516 15 17
# Turret pivot (1152,384). At 8 cells/frame, tip forward=3.208, lateral=+/-0.1875.
Oval '#303a30' 1062 294 180 180
Poly '#606f51' @(@(1008,351),@(1037,305),@(1100,276),@(1215,280),@(1286,315),@(1286,453),@(1215,488),@(1100,492),@(1037,463),@(1008,417))
# Thick wedge cheeks, sloped armor facets and a raised roof crown.
Poly '#a0ac87' @(@(1008,351),@(1037,305),@(1100,276),@(1120,318),@(1065,337),@(1047,360))
Poly '#3b4c36' @(@(1008,417),@(1037,463),@(1100,492),@(1120,450),@(1065,431),@(1047,408))
Poly '#7a8b65' @(@(1120,318),@(1215,320),@(1245,345),@(1245,423),@(1215,450),@(1120,450),@(1065,431),@(1065,337))
Rect '#404e39' 1249 337 27 94
Oval '#323e30' 1144 339 54 54
Oval '#91a17a' 1150 345 42 42
Rect '#253f45' 1101 413 32 18
# Two equal cannon barrels, close-spaced, with opaque armored mantlet at their roots.
Rect '#4d5d45' 1034 345 68 78
foreach ($y in @(366,402)) {
    Rect '#3b4740' 844 ($y-6) 204 12
    Rect '#859174' 844 ($y-10) 30 20
    Rect '#5d6954' 949 ($y-9) 31 18
}
$g.Dispose()
$image.Save((Join-Path $PSScriptRoot 'guide.png'),[System.Drawing.Imaging.ImageFormat]::Png)
$image.Dispose()
