# TrackCats

A seed tracker for The Battle Cats rare gacha, built for casual players. It works out the hidden seed
behind your rolls and then tells you, in plain language, which cats you will get next and what to do
with your tickets and Cat Food.

Unofficial fan tool. Not affiliated with or endorsed by PONOS. It reads nothing from the game and
changes nothing in it; it only does maths on rolls you enter.

## What it does

- **Guided seed search**: pick the banner, tap the cats you rolled in order (or import screenshots, beta), and the phone searches all 4.29 billion seeds.
- **Next rolls list**: your upcoming cats on any banner, with new, wishlist and duplicate cats marked. Duplicate swaps and track switches are handled for you.
- **Banner comparison**: what your next rolls would be on every open and upcoming banner, with a verdict.
- **Planner**: enter your Rare Tickets, Cat Food, Platinum and Legend Tickets and get exact steps; pick a target cat to find the cheapest way to reach it; see notable cats in your next 100 rolls.
- **Staying in sync**: an "I rolled" button, recovery when rolls stop matching, history with undo.
- **Collection**: mark owned cats and a wishlist.
- **Alerts**: a background check about twice a day for new banners and wishlist cats close on your path.
- **Advanced view**: the classic A/B track table, for comparing with other trackers.
- **Backup**: save and restore everything as one file.
- **Pictures**: cat icons and banner art, downloaded once and kept on the phone.

What changed in each version is in [CHANGELOG.md](CHANGELOG.md).

Covers the rare gacha only (Rare, Platinum and Legend Tickets and Cat Food rolls share one seed).

## Install

Download the APK from the repository's Releases page and open it on the phone. Android will ask you
to allow installing from that source.

## Build

Requires JDK 17 and the Android SDK (platform 35).

```
gradlew.bat :app:assembleRelease
gradlew.bat :app:testDebugUnitTest
```

The APK is written to `app/build/outputs/apk/release/app-release.apk`. The release build is optimised
and signed with the build machine's debug key, so updates must be built on the same machine to
install over an existing copy.

To render the main screens to PNG files on the PC for a visual check, set `TRACKCATS_SHOTS` to an
output folder before running the tests.

## Data

The app ships with a snapshot of banner and cat data and refreshes it from the
[battle-cats-rolls](https://gitlab.com/godfat/battle-cats-rolls) project's `build/bc-en.yaml`.
To regenerate the bundled snapshot:

```
python tools/convert_data.py refdata/bc-en.yaml app/src/main/assets/gamedata.json
```

## Pictures

No artwork is stored in this repository or in the APK. The app downloads cat icons and banner art
from the community-run [Battle Cats Wiki](https://battlecats.miraheze.org) and caches them on the
phone. `app/src/main/assets/banner_art.json` only lists which wiki file belongs to which banner;
regenerate it with:

```
python tools/make_banner_map.py app/src/main/assets/gamedata.json app/src/main/assets/banner_art.json
```

## Credits

The roll algorithm and seed search are a Kotlin port of
[battle-cats-rolls](https://gitlab.com/godfat/battle-cats-rolls) by godfat, Apache License 2.0.
See `NOTICE`. Cat names, banner names and game data belong to PONOS.
Title lettering is [Lilita One](https://fonts.google.com/specimen/Lilita+One), SIL Open Font License
(`licenses/LilitaOne-OFL.txt`).
