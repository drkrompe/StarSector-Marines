Add-Type -AssemblyName System.Drawing
$bmp=[Drawing.Bitmap]::new(1024,384,[Drawing.Imaging.PixelFormat]::Format32bppArgb)
$g=[Drawing.Graphics]::FromImage($bmp)
$g.Clear([Drawing.Color]::Transparent)
function Rect($color,$x,$y,$width,$height) {
    $brush=[Drawing.SolidBrush]::new([Drawing.ColorTranslator]::FromHtml($color))
    $g.FillRectangle($brush,$x,$y,$width,$height); $brush.Dispose()
}
function Poly($color,$coords) {
    $brush=[Drawing.SolidBrush]::new([Drawing.ColorTranslator]::FromHtml($color))
    $points=for($i=0;$i -lt $coords.Count;$i+=2){[Drawing.Point]::new($coords[$i],$coords[$i+1])}
    $g.FillPolygon($brush,[Drawing.Point[]]$points); $brush.Dispose()
}
# Straight overhead rigid hauler: blunt cab left, narrow machinery saddle, long freight module right.
Rect '#353a39' 220 105 200 174
Poly '#535b57' @(24,92,48,64,285,64,316,100,316,284,285,320,48,320,24,292)
Poly '#b3b8ab' @(48,110,88,86,270,86,292,110,292,274,270,298,88,298,48,274)
Rect '#34464b' 52 113 34 158
Rect '#848d81' 99 104 173 176
Rect '#59655e' 170 142 80 100
Rect '#535a53' 324 131 26 122
# Raised roof, beveled shoulder rails and covered bogies; no sidewall-facing wheels.
Poly '#625247' @(360,40,980,40,1000,60,1000,324,980,344,360,344,344,328,344,56)
Rect '#a46d3c' 358 46 623 292
Rect '#c4c1ab' 379 72 582 240
Rect '#9b9e90' 384 73 571 24
Rect '#797e70' 384 288 571 24
foreach($x in @(404,500,596,692,788,884)){ Rect '#a5a895' $x 100 8 184 }
Rect '#575f57' 899 124 42 136
Rect '#c2a864' 26 104 15 28
Rect '#c2a864' 26 252 15 28
Rect '#484e48' 985 94 12 196
$g.Dispose()
$bmp.Save((Join-Path $PSScriptRoot 'guide.png'),[Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()
