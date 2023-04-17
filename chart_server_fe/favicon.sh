#!/usr/bin/env bash
dir=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

image="njord.jpg"
magick -density 256x256 -background transparent $dir/src/$image -define icon:auto-resize -colors 256 $dir/public/favicon.ico

convert -size 1024x1024 xc:Black -fill White -draw 'circle 512 512 512 1' -alpha Copy $dir/mask.png
convert $dir/src/$image -gravity Center mask.png -compose CopyOpacity -composite -trim $dir/src/njord_circle.png
magick -density 256x256 -background transparent $dir/src/njord_circle.png -define icon:auto-resize -colors 256 $dir/public/favicon.ico
rm $dir/mask.png $dir/src/njord_circle.png
convert $dir/src/$image -resize 192x192 $dir/public/njord192.png
convert $dir/src/$image -resize 512x512 $dir/public/njord512.png