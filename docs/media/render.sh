#!/usr/bin/env bash
# Renders every playground clip to docs/media/<name>.gif.
#   ./docs/media/render.sh            all clips
#   ./docs/media/render.sh boitata    one clip
# 10 fps, 480 px wide, 48 colours keeps each GIF around 1 to 3 MB. Needs ffmpeg. The frames are drawn offscreen by the desktop sample, so a clip is the same on every machine.
set -euo pipefail
cd "$(dirname "$0")/../.."
if [ $# -gt 0 ]; then ./gradlew :samples:playground-desktop:renderClips -Pclip="$1"; else ./gradlew :samples:playground-desktop:renderClips; fi
frames=samples/playground-desktop/build/clips
for dir in "$frames"/*/; do
  name=$(basename "$dir")
  [ $# -gt 0 ] && [ "$name" != "$1" ] && continue
  ffmpeg -y -loglevel error -framerate 20 -i "$dir/%04d.png" \
    -vf "fps=10,scale=480:-1:flags=lanczos,split[a][b];[a]palettegen=max_colors=48:stats_mode=diff[p];[b][p]paletteuse=dither=bayer:bayer_scale=5:diff_mode=rectangle" \
    -loop 0 "docs/media/$name.gif"
  echo "docs/media/$name.gif $(du -h "docs/media/$name.gif" | cut -f1)"
done
