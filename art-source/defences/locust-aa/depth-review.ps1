# Mechanical comparison only: retain both complete assembly frames at equal scale.
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$before = [System.Drawing.Bitmap]::FromFile((Join-Path $PSScriptRoot 'sources/ground-base-flat-composite.png'))
$after = [System.Drawing.Bitmap]::FromFile((Join-Path $PSScriptRoot 'composite.png'))
$sheet = [System.Drawing.Bitmap]::new(1920,1080)
$g = [System.Drawing.Graphics]::FromImage($sheet)
$title = [System.Drawing.Font]::new('Segoe UI',32)
$label = [System.Drawing.Font]::new('Segoe UI',20)
$small = [System.Drawing.Font]::new('Segoe UI',16)
$white = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(232,237,235))
$muted = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(166,181,178))
$panel = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(39,48,49))
function Draw-Frame($graphics, $sprite, [int]$x, [int]$y, [int]$size) {
    $graphics.DrawImage($sprite,
        [System.Drawing.Rectangle]::new($x,$y,$size,$size),
        [System.Drawing.Rectangle]::new(0,0,$sprite.Width,$sprite.Height),
        [System.Drawing.GraphicsUnit]::Pixel)
}
try {
    if ($before.Width -ne $before.Height -or $after.Width -ne $after.Height) {
        throw 'Depth comparison requires square full-frame composites'
    }
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
    $g.Clear([System.Drawing.Color]::FromArgb(23,30,32))
    $g.DrawString('LOCUST / TOWER DEPTH', $title,$white,60,35)
    $g.DrawString('Same canvas scale  |  Approved circular turret  |  Foundation comparison', $small,$muted,62,95)
    $g.FillRectangle($panel,40,160,900,830)
    $g.FillRectangle($panel,980,160,900,830)
    $g.DrawString('BEFORE / FLAT FOUNDATION', $label,$white,62,180)
    $g.DrawString('AFTER / RAISED FOUNDATION', $label,$white,1002,180)
    Draw-Frame $g $before 120 220 740
    Draw-Frame $g $after 1060 220 740
    $g.DrawString('1920 x 1080 art review. Full frames retained; turret appearance unchanged.', $small,$muted,60,1015)
    $sheet.Save((Join-Path $PSScriptRoot 'depth-review.png'), [System.Drawing.Imaging.ImageFormat]::Png)
} finally {
    $g.Dispose(); $sheet.Dispose(); $before.Dispose(); $after.Dispose()
    $title.Dispose(); $label.Dispose(); $small.Dispose()
    $white.Dispose(); $muted.Dispose(); $panel.Dispose()
}
