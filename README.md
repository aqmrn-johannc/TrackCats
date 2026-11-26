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

Covers the rare gacha only (Rare, Platinum and Legend Tickets and Cat Food rolls share one seed).

## Install

Download the APK from the repository's Releases page and open it on the phone. Android will ask you
to allow installing from that source.

## Build

Requires JDK 17 and the Android SDK (platform 35).

```
gradlew.bat :app:assembleDebug
gradlew.bat :app:testDebugUnitTest
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Data

The app ships with a snapshot of banner and cat data and refreshes it from the
[battle-cats-rolls](https://gitlab.com/godfat/battle-cats-rolls) project's `build/bc-en.yaml`.
To regenerate the bundled snapshot:

```
python tools/convert_data.py refdata/bc-en.yaml app/src/main/assets/gamedata.json
```

## Credits

The roll algorithm and seed search are a Kotlin port of
[battle-cats-rolls](https://gitlab.com/godfat/battle-cats-rolls) by godfat, Apache License 2.0.
See `NOTICE`. Cat names, banner names and game data belong to PONOS.
