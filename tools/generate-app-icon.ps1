param([string]$Source=(Join-Path $PSScriptRoot '../release/assets/app-icon-source.png'))
# Resize the supplied artwork without redrawing or changing its contents.
Add-Type -AssemblyName System.Drawing
$root=Split-Path $PSScriptRoot -Parent
$image=[System.Drawing.Image]::FromFile((Resolve-Path -LiteralPath $Source))
function Save-Icon([string]$path,[int]$size) {
    [IO.Directory]::CreateDirectory((Split-Path $path -Parent)) | Out-Null
    $bitmap=[System.Drawing.Bitmap]::new($size,$size)
    $graphics=[System.Drawing.Graphics]::FromImage($bitmap)
    try {
        $graphics.Clear([System.Drawing.Color]::White)
        $graphics.InterpolationMode=[System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $graphics.PixelOffsetMode=[System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
        $graphics.DrawImage($image,0,0,$size,$size)
        $bitmap.Save($path,[System.Drawing.Imaging.ImageFormat]::Png)
    } finally { $graphics.Dispose();$bitmap.Dispose() }
}
try {
    foreach($entry in @{mdpi=48;hdpi=72;xhdpi=96;xxhdpi=144;xxxhdpi=192}.GetEnumerator()) {
        Save-Icon (Join-Path $root "app/src/main/res/mipmap-$($entry.Key)/ic_smart_remote.png") $entry.Value
    }
    Save-Icon (Join-Path $root 'app/src/main/res/drawable-nodpi/tv_remote_icon_artwork.png') 512
    Save-Icon (Join-Path $root 'release/assets/app-icon-512.png') 512
} finally { $image.Dispose() }
