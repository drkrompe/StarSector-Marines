Add-Type -AssemblyName System.Drawing
$bmp = [System.Drawing.Bitmap]::new(1024,384)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.Clear([System.Drawing.Color]::Transparent)
function Rect($c,$x,$y,$w,$h) { $b=[System.Drawing.SolidBrush]::new([System.Drawing.ColorTranslator]::FromHtml($c)); $g.FillRectangle($b,$x,$y,$w,$h); $b.Dispose() }
Rect '#252723' 96 65 75 35
Rect '#252723' 96 284 75 35
Rect '#252723' 347 65 75 35
Rect '#252723' 347 284 75 35
Rect '#72766a' 45 98 421 185
Rect '#5f6954' 52 110 86 160
Rect '#8a907b' 145 100 110 183
Rect '#344348' 145 112 21 158
Rect '#4a5141' 266 112 187 158
Rect '#707461' 279 127 160 128
Rect '#363a31' 365 168 45 46
Rect '#7b806b' 364 169 47 45
Rect '#394037' 780 155 65 75
Rect '#59624a' 762 168 58 46
Rect '#292d28' 657 185 126 15
Rect '#81866e' 805 201 28 28
$g.Dispose()
$bmp.Save((Join-Path $PSScriptRoot 'guide.png'),[System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()
