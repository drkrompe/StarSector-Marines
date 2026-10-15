# Read-only art assembly preview; full source frames are registered by their pivots.
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$sourceRoot = $PSScriptRoot
$registration = Get-Content -Raw (Join-Path $sourceRoot 'export-metadata.json') | ConvertFrom-Json
$mountRegistration = $registration | Where-Object { $_.name -eq 'locust-mount' }
$towerRegistration = $registration | Where-Object { $_.name -eq 'ground-base' }
$tower = [System.Drawing.Bitmap]::FromFile((Join-Path $sourceRoot 'ground-base.master.png'))
$turret = [System.Drawing.Bitmap]::FromFile((Join-Path $sourceRoot 'locust-mount.master.png'))
function Draw-Layer($graphics, $sprite, [single]$x, [single]$y, [int]$size) {
    $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
    $graphics.DrawImage($sprite, [System.Drawing.RectangleF]::new($x,$y,$size,$size), [System.Drawing.RectangleF]::new(0,0,$sprite.Width,$sprite.Height), [System.Drawing.GraphicsUnit]::Pixel)
}
function Draw-Assembly($graphics, [int]$x, [int]$y, [int]$size, [single]$angle = 0) {
    Draw-Layer $graphics $tower ($x+$size/2-$size*$towerRegistration.pivot[0]/$towerRegistration.layerSize) ($y+$size/2-$size*$towerRegistration.pivot[1]/$towerRegistration.layerSize) $size
    $state = $graphics.Save()
    try {
        $graphics.TranslateTransform(($x + $size/2), ($y + $size/2))
        $graphics.RotateTransform($angle)
        Draw-Layer $graphics $turret (-$size*$mountRegistration.pivot[0]/$mountRegistration.layerSize) (-$size*$mountRegistration.pivot[1]/$mountRegistration.layerSize) $size
    } finally { $graphics.Restore($state) }
}
$composite = [System.Drawing.Bitmap]::new(512,512,[System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$cg = [System.Drawing.Graphics]::FromImage($composite)
try {
    $cg.Clear([System.Drawing.Color]::Transparent)
    Draw-Assembly $cg 0 0 512
    $composite.Save((Join-Path $PSScriptRoot 'composite.png'), [System.Drawing.Imaging.ImageFormat]::Png)
} finally { $cg.Dispose(); $composite.Dispose() }
$sheet = [System.Drawing.Bitmap]::new(1920,1080)
$g = [System.Drawing.Graphics]::FromImage($sheet)
$title = [System.Drawing.Font]::new('Segoe UI',32)
$label = [System.Drawing.Font]::new('Segoe UI',20)
$small = [System.Drawing.Font]::new('Segoe UI',16)
$white = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(232,237,235))
$muted = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(166,181,178))
$panel = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(39,48,49))
try {
    $g.Clear([System.Drawing.Color]::FromArgb(23,30,32))
    $g.DrawString('LOCUST / TOWER COMPOSITE', $title, $white, 60, 35)
    $g.DrawString('Tapered tower walls  |  Registered bearing pivot  |  Approved turret artwork unchanged', $small, $muted, 62, 95)
    $g.FillRectangle($panel, 40,160,840,810)
    $g.DrawString('FORWARD', $label,$white,62,180)
    Draw-Assembly $g 60 210 800
    $g.FillRectangle($panel, 900,160,550,810)
    $g.DrawString('45-DEGREE TRAVERSE', $label,$white,922,180)
    Draw-Assembly $g 900 310 550 45
    $g.DrawString('Tower fixed; mount rotates.', $small,$muted,922,920)
    $g.FillRectangle($panel,1470,160,410,810)
    $g.DrawString('SIZE STUDIES', $label,$white,1492,180)
    Draw-Assembly $g 1547 230 256
    $g.DrawString('256 px canvas', $small,$muted,1595,495)
    Draw-Assembly $g 1579 550 192
    $g.DrawString('192 px canvas', $small,$muted,1595,750)
    Draw-Assembly $g 1525 820 128
    Draw-Assembly $g 1740 852 64
    $g.DrawString('128 px', $small,$muted,1550,935)
    $g.DrawString('64 px', $small,$muted,1740,935)
    $g.DrawString('Art composite preview at 1920 x 1080. Final world footprint and in-game presentation are still to be authored.', $small,$muted,60,1015)
    $sheet.Save((Join-Path $PSScriptRoot 'composite-review.png'), [System.Drawing.Imaging.ImageFormat]::Png)
} finally {
    $g.Dispose(); $sheet.Dispose(); $tower.Dispose(); $turret.Dispose()
    $title.Dispose(); $label.Dispose(); $small.Dispose(); $white.Dispose(); $muted.Dispose(); $panel.Dispose()
}
