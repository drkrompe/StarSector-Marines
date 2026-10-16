# Code-owned jagged metal silhouette; mechanical vector rasterization only.
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Drawing
$root=Split-Path (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent) -Parent
$bitmap=[System.Drawing.Bitmap]::new(16,16,[System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$g=[System.Drawing.Graphics]::FromImage($bitmap)
try {
 $g.Clear([System.Drawing.Color]::Transparent)
 $points=[System.Drawing.Point[]]@([System.Drawing.Point]::new(2,5),[System.Drawing.Point]::new(7,1),[System.Drawing.Point]::new(10,4),[System.Drawing.Point]::new(14,3),[System.Drawing.Point]::new(12,11),[System.Drawing.Point]::new(7,14),[System.Drawing.Point]::new(5,10),[System.Drawing.Point]::new(1,11))
 $g.FillPolygon([System.Drawing.Brushes]::White,$points)
 $output=Join-Path $root 'mod/graphics/battle/fx/aircraft-metal-chip.png'
 $bitmap.Save($output,[System.Drawing.Imaging.ImageFormat]::Png)
}finally{$g.Dispose();$bitmap.Dispose()}
