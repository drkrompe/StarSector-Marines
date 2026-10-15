Add-Type -AssemblyName System.Drawing
$bitmap = New-Object System.Drawing.Bitmap 768,768
$g = [System.Drawing.Graphics]::FromImage($bitmap)
$g.Clear([System.Drawing.Color]::Transparent)
function Rect($color,$x,$y,$w,$h) {
    $b = New-Object System.Drawing.SolidBrush ([System.Drawing.ColorTranslator]::FromHtml($color))
    $g.FillRectangle($b,[float]$x,[float]$y,[float]$w,[float]$h); $b.Dispose()
}
# Three 256-square frames, left to right: intact, damaged, destroyed.
# Horizontal travel; side rails and rollers touch both frame edges for snapping.
for ($state=0; $state -lt 3; $state++) {
    $g.ResetTransform(); $g.TranslateTransform($state*256,256)
    if ($state -eq 2) {
        Rect '#738278' 0 25 93 15
        Rect '#414d4b' 150 30 100 18
        Rect '#a2a99c' 32 65 18 85
        Rect '#77867d' 118 160 18 60
        Rect '#555f58' 191 100 14 100
        Rect '#b5a363' 0 222 73 10
        Rect '#77867d' 165 225 90 13
        continue
    }
    Rect '#354440' 0 24 256 208
    Rect '#7e8e81' 0 25 256 16
    Rect '#7e8e81' 0 216 256 16
    foreach ($x in @(5,35,65,95,125,155,185,215,245)) { Rect '#a6afa4' $x 47 12 162 }
    Rect '#bdab61' 0 27 256 6
    Rect '#bdab61' 0 225 256 6
    if ($state -eq 1) { Rect '#3e423b' 95 92 90 30; Rect '#4d4f42' 130 200 90 28 }
}
$g.Dispose()
$bitmap.Save((Join-Path $PSScriptRoot 'factory-conveyor-guide.png'),[System.Drawing.Imaging.ImageFormat]::Png)
$bitmap.Dispose()
