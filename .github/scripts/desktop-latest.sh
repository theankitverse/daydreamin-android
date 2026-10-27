#!/usr/bin/env bash
# Copies a desktop release's installers into the "desktop-latest" release under names without the
# version (Daydreamin-windows.msi…), so the README's download links always give the newest version.
# Usage: desktop-latest.sh desktop-v1.2.3   (needs GH_TOKEN and GITHUB_REPOSITORY)
set -euo pipefail
tag="$1"
v="${tag#desktop-v}"
repo="${GITHUB_REPOSITORY:-theankitverse/daydreamin-android}"
work="$(mktemp -d)"
gh release download "$tag" --repo "$repo" --dir "$work" --pattern "Daydreamin-$v-*"
for f in "$work"/Daydreamin-"$v"-*; do mv "$f" "$work/Daydreamin-${f##*/Daydreamin-$v-}"; done
ls -la "$work"
notes="**Always the newest Daydreamin for your computer** (now version $v). These links never change:

| Your computer | Download |
|---|---|
| Mac with Apple chip (M1 and later) | [Daydreamin-macos-arm64.dmg](https://github.com/$repo/releases/download/desktop-latest/Daydreamin-macos-arm64.dmg) |
| Mac with Intel chip | [Daydreamin-macos-x64.dmg](https://github.com/$repo/releases/download/desktop-latest/Daydreamin-macos-x64.dmg) |
| Windows 10 / 11 | [Daydreamin-windows-setup.exe](https://github.com/$repo/releases/download/desktop-latest/Daydreamin-windows-setup.exe) |
| Ubuntu, Debian, Mint | [Daydreamin-linux-amd64.deb](https://github.com/$repo/releases/download/desktop-latest/Daydreamin-linux-amd64.deb) |
| Fedora, openSUSE | [Daydreamin-linux-x86_64.rpm](https://github.com/$repo/releases/download/desktop-latest/Daydreamin-linux-x86_64.rpm) |

How to install, and what's new: [Daydreamin $v](https://github.com/$repo/releases/tag/$tag)."
if gh release view desktop-latest --repo "$repo" >/dev/null 2>&1; then
  gh release edit desktop-latest --repo "$repo" --title "⬇ Download Daydreamin for Mac, Windows & Linux" --notes "$notes" --latest=false
else
  gh release create desktop-latest --repo "$repo" --target main --latest=false --title "⬇ Download Daydreamin for Mac, Windows & Linux" --notes "$notes"
fi
gh release upload desktop-latest --repo "$repo" --clobber "$work"/Daydreamin-*
