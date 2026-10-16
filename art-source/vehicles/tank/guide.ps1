# Two 768x768 registered frames. Left-facing hull and single-cannon turret.
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
# Abrams / Type 10 proportions. Left is front: flat transverse glacis, no prow.
# Hull bearing (384,384). Long rectangular track base, 680 x 460 overall.
foreach ($y in @(154,534)) {
    Poly '#272b29' @(@(48,$y+16),@(65,$y),@(710,$y),@(730,$y+18),@(730,$y+62),@(710,$y+80),@(65,$y+80),@(48,$y+64))
    foreach ($x in 62..710 | Where-Object { ($_-62)%20 -eq 0 }) { Rect '#44463d' $x ($y+5) 10 70 }
}
Poly '#6f705a' @(@(66,225),@(86,211),@(714,211),@(723,227),@(723,541),@(714,557),@(86,557),@(66,543))
# Broad front face and long sloped upper glacis, only tiny corner clips.
Poly '#aa9b69' @(@(66,245),@(82,225),@(240,225),@(240,543),@(82,543),@(66,523))
Rect '#5e5d49' 66 263 15 242
Rect '#b3a576' 85 239 143 290
Rect '#777b64' 231 242 74 284
Rect '#263431' 251 333 26 50
Rect '#787b60' 249 389 39 48
# Narrow segmented side skirts cover the upper edge of the tracks.
foreach ($x in @(83,186,289,392,495,598)) {
    Rect '#817b5d' $x 201 96 40
    Rect '#565c4b' $x 527 96 40
}
# Driver forward, turret bearing slightly forward of rear engine compartment.
Oval '#2e342e' 292 292 184 184
Oval '#82836a' 301 301 166 166
Oval '#555d4b' 313 313 142 142
# Rectangular aft power deck, two grilles, rear exhaust beam.
Rect '#747760' 500 250 190 268
Rect '#30362f' 525 266 139 99
Rect '#30362f' 525 403 139 99
foreach ($x in 533..659 | Where-Object { ($_-533)%14 -eq 0 }) {
    Rect '#5b6150' $x 272 5 86
    Rect '#5b6150' $x 409 5 86
}
Rect '#40463b' 697 263 22 242
Rect '#9e5739' 695 245 19 13
Rect '#9e5739' 695 510 19 13
# Turret bearing (1152,384). Compact angular roof, paired separate cheek wedges.
Oval '#30362e' 1080 312 144 144
# Rear ammunition bustle is a rectangle with tiny chamfers, not a rounded shield.
Poly '#626b56' @(@(1144,304),@(1278,304),@(1290,316),@(1290,452),@(1278,464),@(1144,464))
Rect '#777b61' 1201 312 77 144
Rect '#414a3d' 1262 319 16 130
# Two Type-10-like arrowhead cheek modules, separated by narrow mantlet.
Poly '#9c956d' @(@(1029,351),@(1070,294),@(1160,292),@(1190,309),@(1160,347),@(1083,359))
Poly '#565e49' @(@(1029,417),@(1070,474),@(1160,476),@(1190,459),@(1160,421),@(1083,409))
Poly '#b2a57a' @(@(1042,347),@(1074,305),@(1158,303),@(1138,324),@(1080,347))
Poly '#808467' @(@(1042,421),@(1074,463),@(1158,465),@(1138,444),@(1080,421))
Rect '#747d62' 1104 344 98 80
Oval '#394438' 1152 341 38 38
Oval '#91967b' 1157 346 28 28
Rect '#303f3b' 1122 397 28 16
# Short single cannon centered on bearing; tip (940,384), no hull attached.
Rect '#525b49' 1051 359 54 50
Rect '#454e43' 940 379 125 10
Rect '#777d65' 1005 377 24 14
Rect '#626953' 940 378 12 12
$g.Dispose()
$image.Save((Join-Path $PSScriptRoot 'guide.png'),[System.Drawing.Imaging.ImageFormat]::Png)
$image.Dispose()