# Quest Route: setup, settings and testing

Everything needed to configure, run, and test **Quest Route**. For an overview
of what the plugin does, see [README.md](README.md).

- [Install and Account setup](#install-and-account-setup)
- [Sidebar controls and tabs](#sidebar-controls-and-tabs)
- [Settings](#settings)
  - [Quest Route sync](#quest-route-sync)
  - [In-game overlays](#in-game-overlays)
  - [Shops](#shops)
  - [Drop sources](#drop-sources)
  - [Ground items](#ground-items)
  - [Bank](#bank)
  - [Chat](#chat)
  - [Experience lamps](#experience-lamps)
  - [Quests tab](#quests-tab)
  - [Group (party)](#group-party)
  - [Voice](#voice)
- [Optional integrations and server sync](#optional-integrations-and-server-sync)
- [Testing with friends](#testing-with-friends)
  - [Windows test zip](#windows-test-zip)
  - [Developer client and Scenarios](#developer-client-and-scenarios)
  - [What to look at](#what-to-look-at)
- [Limitations](#limitations)
- [Building and tests](#building-and-tests)

---

## Install and Account setup

1. Install **Quest Route** from the Plugin Hub (or launch a developer client
   with `./gradlew run`) and open the quest-scroll icon on RuneLite's sidebar.
2. On first login for an account, the **Quests** tab opens **Account setup**
   automatically. Click **Account setup** at the top of the sidebar at any time
   to edit your choices later (**Cancel** leaves saved answers unchanged):
   - **Account type:** *Main*, *Ironman*, *Hardcore ironman*, *Ultimate
     ironman*, *Group ironman*, or *Hardcore group ironman*.
   - **Quests to route:** *Free-to-play quests* and/or *Members quests* (both
     ticked by default).
   - **Build:** *Custom (set limits below)* or one of **27 named presets**
     matching the companion planner:
     - *Free-to-play (7):* *F2P 1 Defence pure*, *F2P rune warhammer pure*,
       *F2P 20 Attack tier*, *F2P 30 Attack tier*, *F2P 40 Attack melee pure*,
       *F2P adamant pure*, *F2P rune pure*.
     - *Members (20):* *1 Defence pure (50 Attack)*, *1 Defence pure (60
       Attack)*, *1 Defence pure (75 Attack)*, *13 Defence pure*, *20 Defence
       initiate pure*, *25 Defence clan pure*, *40 Defence rune pure*, *Void
       pure*, *Berserker pure (50 Attack)*, *Berserker pure (60 Attack)*,
       *Berserker pure (75 Attack)*, *Obsidian mauler (1 Defence)*, *Obsidian
       zerker (45 Defence)*, *Obsidian tank (60 Defence)*, *Obsidian tank (75
       Defence)*, *Moon pure (50 Defence)*, *Defence pure tank*, *Medium level
       build*, *Skiller (no combat XP)*, *Owner pure*.
   - **Skill limits:** per skill across all 24 skills—*No limit*, *No XP ever*,
     *Stop at level* (1–99), *Warn only* (1–99), or *Goal level* (1–99).
     Picking a preset fills these rows; changing any row switches the build
     picker to *Custom (set limits below)*.
   - Click **Save and show my route**.
3. Open your bank once after logging in so item counts include your banked
   items (bank placeholders are ignored). On a Group Ironman account, open
   Group Storage once as well so its quest items are snapshotted.

### Where data is stored

Quest Route writes **no files of its own** on disk. Everything lives inside
RuneLite's configuration store:

- **Plugin settings** (`QuestpathConfig`, config group `questroute`): shared
  across the active RuneLite profile. Pre-rename settings under `questpath` are
  copied over once on startup without overwriting existing keys.
- **Per-account RSProfile keys** (group `questroute`, scoped by RuneLite's
  `RSProfileKey` to one RuneScape account): `localBuildRules` (your saved build
  rules), `loadouts` (named locked-gear snapshots), and `groupStorageSnapshot`
  (last seen quest-item counts in Group Ironman storage).
- **Local account profile** (config group `questrouteprofile`, key
  `profile.<accountHash>`): your account type, quest types, chosen build preset
  slug, cached route, and per-quest route marks (*pinned*, *deferred*,
  *refused*, and *hide blocked*). If a stored value cannot be parsed, it is set
  aside under `profile.<accountHash>.corrupt` and **Account setup** prompts
  again.

---

## Sidebar controls and tabs

### Header bar (above the tabs on every page)

- **Account setup:** opens the setup form on the **Quests** tab pre-filled with
  your current answers.
- **What's new:** prints the current release's update notes into your local
  chat console (`ChatMessageType.CONSOLE`).
- **Quick settings** (collapsed by default): one-click checkboxes for *Next
  quest overlay* (`overlayNextQuest`), *Needed items overlay* (`overlayItems`),
  *Shopping route overlay* (`overlayShopRoute`), *Lamp advice* (`lampAdvisor`),
  *Quest Route bank tab* (`bankTab`), *Bank highlights* (`bankHighlight`),
  *Spoken prompts* (`voiceEnabled`), and *Server mode (send account state)*
  (`syncEnabled`), plus **All settings ↓** to jump to the **View** tab.

### Tabs

1. **Combat:** *Sync with Quest Route* button; live **Combat** level and
   progress bar; **Locked gear** (*Lock current gear*, *Rename*, *Delete*,
   style/prayer/spell/melee potion selectors, live max hits, next skill level
   that raises max hit, *Without prayer* / *Without potion* lines, and
   collapsible *Items (N)* list); **Free Ranged / Magic** ceiling and next
   Strength step; **Cost of the next combat level** per stat sorted cheapest XP
   first; and **Build rules** (*Show all rules* toggle plus **Your own rules**
   editor with *Skill*, *Rule*, *Level*, *Save rule*, and *Remove*).
2. **Skills:** **What a level costs**—pick a skill and **Target level** (2–99)
   to see whether the target breaks a build cap, XP needed, *From quests now*
   (*Ready now*), *From quests later* (*After more work*, with blocker and
   boost tooltips), *Train, ready only*, *Train, after all*, and XP *Off the
   table* across build-blocked quests.
3. **Quests:** driven by **Quests tab shows** (*Quests*, *Diaries*, or *All*).
   - *Quests:* name search box, status dropdown (*All statuses*, *Can do now*,
     *With a boost*, *Needs levels*, *Needs other quests*, *Breaks your build*,
     *Blocked by a prerequisite*, *Done*), **Hide quests your build has closed
     off** checkbox, **Next on your route** (progress bar and ordered list),
     **Still needs work** (`+` boostable, `^` skill-blocked, `.`
     quest-blocked), **Start, never finish** (synced partial-run quests), and
     **Can I do this quest?** verdict sections. Right-click any quest row in
     the verdict list to toggle **Pin to the front of the route** / **Unpin**,
     **Do it later** / **Do it in order again**, or **Refuse: never do this
     quest** / **Stop refusing it**.
   - *Diaries:* **Diary route** (nearest actionable synced tasks and **Send
     diary route to Shortest Path**) and **Diary journal** (all 492 bundled
     tasks grouped into collapsible Areas and Tiers).
4. **Items:** item/quest search box; **Get while you are here** pickups during
   current and upcoming route quests; **Grand Exchange list** on main accounts
   (click an item while GE search is open to display it without typing;
   *Include recommended items* and *Runes instead of teleport tablets* toggles);
   shop trip controls (**Send shop route to Shortest Path**, **Stop the shop
   route** while active, **Skip "<item>" (move to back)**, **Say again:
   `<shop>`**, and **Skipped this trip** with per-item **restore**);
   **Everything you need** cross-quest totals; and **By quest** breakdowns.
5. **Sync:** *Sync with Quest Route* button, last push status, configured
   server/token summary, attributed character name (`Player1`) and snapshot
   counts, and any quest names the server did not recognise.
6. **Group:** party sharing status; **Your group** member list (`GIM1`, `GIM2`,
   quest counts, blocking rules on hover, *other version* / *not sharing*
   states); **Do together next**; **Breaks someone's build**; **Who brings
   what** (per-quest item split and spare hand-overs); **Group shopping**
   (filters for *All quests*, *Everyone*, *Still needed only*, sources, Group
   Ironman storage freshness, and **Send shop route to Shortest Path (group
   shops)**); and **Done by some of you**.
7. **View:** grouped collapsible controls (*Sync*, *Shared*, *Overlays*,
   *Extras*) mirroring `QuestpathConfig` with smart-close toggles and colour
   pickers. Every user-facing `@ConfigItem` appears here except *Quest Route
   address* and *Pairing token* (text/secret fields in RuneLite's config
   panel), *Show update notes in chat* (in *Chat*), and *Show all rules*
   (inline on the **Combat** tab).

---

## Settings

RuneLite's Configuration panel for **Quest Route** has **11 sections** and **76
visible settings**. All sections start closed except **Quests tab**, so a fresh
install shows every section heading cleanly.

### Quest Route sync

Closed by default. Optional self-hosted server push; leave untouched for
standalone local play.

| Setting | Default | What it does |
|---|---|---|
| Send my account state | Off | Pushes quest completion, levels, XP, containers, and diaries to your Quest Route server (prompts with RuneLite's 3rd-party IP warning) |
| Quest Route address | *(blank)* | Optional base URL of your self-hosted Quest Route server; blank makes zero network requests |
| Pairing token | *(blank, secret)* | Server token authenticating the push and naming the target account |

### In-game overlays

Closed by default. Shared sizing and style controls first, then per-overlay
switches and overrides. Every overlay starts **Off** except **Grand Exchange:
queued, next, left**, which only draws while the Grand Exchange window is open
on a non-ironman account.

| Setting | Default | What it does |
|---|---|---|
| Show | Text and images | Row style across overlays: *Text and images*, *Just text*, or *Just images* (text-only rows keep text in every mode) |
| Width | Regular | Shared outer panel width: *Compact* (168 px), *Regular* (224 px), *Wide* (300 px), or *Widest* (380 px) |
| Length | Normal | Shared row cap per list: *Short* (5), *Normal* (10), *Long* (25), or *Everything* (no cap) |
| Smaller text | Off | Draws overlay rows in the game's small font to fit roughly a third more rows |
| Say when a list is cut short | On | Shows the `...and N more` footer when a list exceeds its cap |
| Next on your route | Off | Shows the next quest on your route, route progress bar, and upcoming preview (when *Quests tab shows* includes quests) |
| - width | Match the shared setting | Width override for *Next on your route* (*Match the shared setting*, *Compact*, *Regular*, *Wide*, *Widest*) |
| - list its items | Off | Lists the next quest's own item requirements under it, ticked against what you hold |
| - length | Match the shared setting | Length override for *Next on your route* (*Match the shared setting*, *Short*, *Normal*, *Long*, *Everything*) |
| Get while you're here | Off | Lists items obtainable during your current/upcoming route quests that other doable quests also want |
| - width | Match the shared setting | Width override for *Get while you're here* |
| - length | Match the shared setting | Length override for *Get while you're here* |
| - how far ahead | 4 | How many quests down the route count as "here" (`1–12`) |
| Everything you need | Off | Shows cross-quest item totals ticked against bank, inventory, equipment, and Group Ironman storage (replaced by *Shop route* while a trip is active and that overlay is on) |
| - width | Match the shared setting | Width override for *Everything you need* |
| - hide what you have | Off | Hides the *Already held* section while keeping held items counted in the header bar |
| - length | Match the shared setting | Length override for *Everything you need* |
| Shop route: where and why | Off | While a shop trip sent to Shortest Path is active, shows the next stops, shortfall, shop/dropper, and quest |
| - width | Match the shared setting | Width override for *Shop route: where and why* |
| - length | Match the shared setting | Length override for *Shop route: where and why* |
| Grand Exchange: queued, next, left | On | While the Grand Exchange is open (mains only): shows *Next* to buy, *Queued* offers against needed counts, *Left*, and *Not sold here* |
| - width | Match the shared setting | Width override for *Grand Exchange: queued, next, left* |
| - length | Match the shared setting | Length override for *Grand Exchange: queued, next, left* |
| Recommended items | Off | Appends wiki-recommended quest items after required items on shopping lists and routes (coins shown as a balance to bring; requires quest graph v13+) |
| - teleports as | Teleport tablets | Lists recommended teleports on the GE list as *Teleport tablets* or *Runes to cast it* (runes used only when your Magic level can cast the spell; walking shop routes always decompose to runes) |
| Combat XP to next level | Off | Shows what each combat stat costs in XP and levels to gain the next combat level, cheapest first |
| - width | Match the shared setting | Width override for *Combat XP to next level* |
| - length | Match the shared setting | Length override for *Combat XP to next level* |
| Diary route -- width | Match the shared setting | Width override for the *Diary route* overlay (the overlay's on/off switch is *Quests tab shows* below) |
| Diary route -- length | Match the shared setting | Length override for the *Diary route* overlay |
| Build rules | Off | Shows your declared skill caps and no-XP rules with remaining headroom |
| - width | Match the shared setting | Width override for *Build rules* |
| - which rules | Blocking and informational | Shows *Blocking and informational*, *Blocking only*, or *Informational only* rules on the overlay |
| - length | Match the shared setting | Length override for *Build rules* |
| Show all rules | Off | On the **Combat** tab's own *Build rules* section: off shows blocking rules only (`No XP ever`, `Stop at level`); on also shows soft caps and goals |

### Shops

Closed by default. Independent of the *Everything you need* overlay so shop
slots are still marked when you keep panels off.

| Setting | Default | What it does |
|---|---|---|
| Mark what you need in shops | On | Outlines needed items inside open shop interfaces |
| Show how many to buy | On | Draws the outstanding buy count in green above marked shop slots |
| Outline colour | Amber (`#E8B13F`) | Outline colour for needed shop slots |
| Say when a shop nearby has what you need | Off | Posts a chat line and overhead call-out once when you come within 16 tiles of a reachable shop stocking needed items |
| Say when a shop sells what you need | Off | Posts one chat line per visit when you open a shop interface stocking needed items |
| Float a note where it happened | Off | Floats a short world note when you finish collecting all of a needed item (up to three at once) |
| Float notes | Above the shopkeeper | Places floating notes *Above you*, *Above the shopkeeper* (falling back to above you when no shopkeeper is open), or *Both* |

### Drop sources

Closed by default. Highlights scene NPCs and world objects whose drop table
leads an item your route still needs (skipping `#Junk` tables).

| Setting | Default | What it does |
|---|---|---|
| Mark what drops what you need | On | Outlines NPCs and objects in view that are the main drop source of a still-needed item, stopping once you hold enough |
| Show what it drops | On | Draws `drops <Item>` above outlined NPCs and objects |
| Outline colour | Amber (`#E08A1E`) | Outline and label colour for drop sources |
| Whole route, not just this quest | Off | Off restricts outlines to the current next quest on your route; on outlines drop sources across every quest on your route |

### Ground items

Closed by default. Marks needed items physically lying on the ground within 15
tiles.

| Setting | Default | What it does |
|---|---|---|
| Mark what you need on the ground | On | Outlines needed ground items and shows which quests want them on hover |
| Show how many to pick up | On | Prints the outstanding count beside marked ground items |
| Marker colour | Amber (`#E8B13F`) | Colour for ground item outlines and arrows |
| Left-click take items you need | On | Promotes *Take* to left-click on ground items you still need without altering other items |
| Bouncing arrow over needed items | On | Draws a bouncing arrow over needed ground items within *Floor-item range (tiles)* |
| Floor-item range (tiles) | 10 | Tile radius (`1–15`) for the bouncing arrow and spoken `"<Item> nearby"` call-out |

### Bank

Closed by default. Marks slots and rearranges the bank grid into *Ran out*,
*Still needed*, *Ready to withdraw*, and *Everything else* when toggled on.

| Setting | Default | What it does |
|---|---|---|
| Mark what you need in the bank | On | Outlines needed items while the bank is open (toggled by the bank title-bar button) |
| Show how many more to find | On | Prints the outstanding shortfall above marked bank slots |
| Only mark what you are short of | Off | Off outlines covered items in green too; on marks only items you are still short of |
| Marker colour | Amber (`#E8B13F`) | Outline colour for bank items you still need |
| Rearrange the bank itself | On | Groups the bank grid into *Ran out* (greyed missing items), *Still needed*, *Ready to withdraw*, and *Everything else* while keeping owned items withdrawable |
| Show the bank title-bar button | On | Shows the backpack *Quest Route* button on the bank's title row (turn off if another plugin needs the space) |
| Show the ordered list too | On | Draws the *Needed from your bank* overlay panel beside the open bank |

### Chat

Closed by default.

| Setting | Default | What it does |
|---|---|---|
| Say when an item is fully collected | On | Prints a chat line naming the item and quest(s) the moment you hold the full required amount |
| Show update notes in chat | On | Posts a one-line welcome on fresh install and release notes once on the next login after an update |

### Experience lamps

Closed by default. Draws on top of the 24-skill lamp/tome interface without
hiding widgets or changing click zones.

| Setting | Default | What it does |
|---|---|---|
| Mark up experience lamps | On | Enables the experience lamp and tome overlay and hover explanations |
| Grey out skills a rule closes | On | Washes skills forbidden by your build rules (*No XP ever*, reached/crossed caps, or unmet lamp level requirements) in translucent grey |
| Suggest where to put it | Top three | Boxes *Top three*, *Top one*, or *Off* (greying stays active when set to *Off*) |
| Best target colour | Green (`#4CC380`) | Box colour around the #1 suggested skill |
| Runner-up colour | Amber (`#E08A1E`) | Box colour around the #2 and #3 suggested skills |

### Quests tab

**Open by default.**

| Setting | Default | What it does |
|---|---|---|
| Quests tab shows | Quests | *Quests* (quest route & verdicts only), *Diaries* (diary route & journal only, plus the *Diary route* overlay), or *All* (both, each in its own order) |

### Group (party)

Closed by default. Uses RuneLite's built-in Party service only.

| Setting | Default | What it does |
|---|---|---|
| Share with my party | Off | Broadcasts your finished/started quests, levels, XP, build rules, route, account type, quest-item counts, and Group Ironman storage quest items to RuneLite party members on a 5-minute heartbeat; turning it off sends a forget-me message |
| Group needs in shop alerts | Off | Adds items your party is still short of to the near-shop chat call-out once per visit |

### Voice

Closed by default. Plays bundled pre-rendered WAV clips locally via RuneLite's
`AudioPlayer` (falling back to a short ping plus chat text if a clip is absent).

| Setting | Default | What it does |
|---|---|---|
| Voice | On | Master switch for spoken reminders; off silences all spoken lines |
| Volume | 70 | Playback volume from `0` to `100` |
| Say what to buy near a shop | On | Speaks every still-needed item sold by a reachable shop as you approach within 16 tiles |
| Say needed items on the floor | On | Speaks `"<Item> nearby"` once per spawn (at most once every 4 seconds) within *Floor-item range (tiles)* |

---

## Optional integrations and server sync

- **Shortest Path:** install and enable the **Shortest Path** plugin from the
  Plugin Hub to activate **Send shop route to Shortest Path**, **Send diary
  route to Shortest Path**, and **Send shop route to Shortest Path (group
  shops)**. Quest Route communicates via `PluginMessage` (`shortestpath` /
  `path` and `clear`) with zero reflection; when Shortest Path is missing or
  disabled, the button stays disabled and states why.
- **Quest Helper:** safe to run side by side. Quest Route's bank title-bar
  button detects occupied slots on the bank header and steps left so neither
  button covers the other. Quest Route does not start or control Quest Helper.
- **RuneLite Party (Group tab):** open RuneLite's built-in **Party** plugin,
  create or join the same party passphrase with your teammates (`GIM1`, `GIM2`,
  ...), and tick **Share with my party** under **Group (party)** (or **View →
  Sync**). You can view what teammates share even before turning sharing on
  yourself, though group lists cover only you until others share.
- **Self-hosted Quest Route (`questpath`) server:** completely optional. If you
  host your own `questpath` instance:
  1. Generate a pairing token on your server (`go run ./cmd/questpath
     -plugin-token`).
  2. Paste your server URL into **Quest Route address** and the token into
     **Pairing token**, then tick **Send my account state** (confirming
     RuneLite's third-party IP warning).
  3. Structural changes (quests, levels, bank/inventory/equipment) push
     immediately and experience updates push on a 2-minute timer (shorter than
     the server's 5-minute freshness window). Click **Sync with Quest Route**
     on the **Combat** or **Sync** tab to pull a freshly edited web plan
     immediately.

---

## Testing with friends

### Windows test zip

Package a standalone zip for a friend on Windows (requires only Java 11+ on
their machine):

```sh
bash tools/test-package/build-test-package.sh
```

This writes `build/test-package/quest-route-test-<yyyymmdd>.zip` containing the
fat developer jar, `START.bat`, and `README.txt`. Your friend unpacks the zip,
double-clicks `START.bat`, and logs in via RuneLite's **Jagex account** sidebar
panel ([Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts)).

### Developer client and Scenarios

From a local checkout on macOS, Linux, or Windows with JDK 11+:

```sh
./gradlew run
```

Developer runs also load a test-only **Scenarios (dev)** sidebar (`src/test`,
never included in Plugin Hub builds) that can fire synthetic `stat`,
`container`, `varbit`, `varp`, and `chat` events on a private `EventBus` for
offline UI checks—see [docs/SCENARIOS.md](docs/SCENARIOS.md). Always use
placeholder names (`GIM1`, `GIM2`) in saved scenarios and screenshots.

### What to look at

1. **First-run setup & presets:** log in on an account with no saved profile,
   pick *1 Defence pure (60 Attack)* or *Skiller (no combat XP)* in **Account
   setup**, save, and confirm quests that award forbidden XP appear under
   *Breaks your build* and never on **Next on your route**. Click **Account
   setup** in the header, press **Cancel**, and confirm the route stays intact.
2. **Per-quest marks:** on the **Quests** tab, right-click a doable quest to
   *Pin to the front of the route*, *Do it later*, and *Refuse: never do this
   quest*; tick *Hide quests your build has closed off* and confirm blocked and
   refused groups hide.
3. **Bank tab & button:** open your bank, click the backpack **Quest Route**
   button (`View tab`), confirm items group into *Ran out*, *Still needed*, and
   *Ready to withdraw* and stay withdrawable, then click `Hide tab` (or a
   normal bank tab) and confirm the normal bank layout returns.
4. **Shop run, voice & drop highlights:** with **Shortest Path** enabled, click
   **Send shop route to Shortest Path** on the **Items** tab, walk within 16
   tiles of a shop selling a needed item to hear the voice reminder, test **Say
   again**, **Skip**, and **Stop the shop route**, and check that scene
   monsters/objects dropping a still-needed item (such as Imps for beads) show
   their outline and `drops <Item>` label until collected.
5. **Grand Exchange & lamps:** on a main account, open GE search and click the
   **Quest Route buy list** button to pick an item without typing; on an
   ironman, confirm GE UI stays hidden. Open an XP lamp or tome with a blocking
   rule set and confirm forbidden skills are greyed out while top targets are
   boxed with hover reasons.
6. **Group party sync:** join the same RuneLite Party on two clients (`GIM1`
   and `GIM2`), enable **Share with my party**, and verify **Do together
   next**, **Breaks someone's build**, **Who brings what**, and **Group
   shopping** update as banks and Group Ironman storage are opened.

---

## Limitations

- **Local route order:** the built-in local planner (`LocalRoute`) orders
  eligible quests prerequisites-first, then alphabetically, honouring your
  build rules, skill levels, quest types, and pin/defer/refuse marks. It does
  not optimise travel time or GP cost.
- **Account type detection:** the account type chosen in **Account setup** is
  stored in your local profile and shared over Party, while Grand Exchange
  hiding and Group Ironman storage tracking follow the client's live in-game
  `AccountType`.
- **Bundled graph version:** the bundled `quests.json` on this release is graph
  version 12 (218 quests, 314 shops, 125 drop locations, 492 diary tasks with
  376 map points). *Recommended items* (v13+) and craftable *make-it* chains
  (v14+) require a newer graph; when running on v12 with *Recommended items*
  ticked, the panel states that the graph carries none rather than showing an
  empty list.
- **Achievement diaries:** **Diary journal** lets you browse all 492 bundled
  diary tasks offline by area and tier, but per-task diary completion and
  actionable **Diary route** stops require syncing with a self-hosted Quest
  Route server.
- **Container snapshots:** bank contents and Group Ironman storage are known as
  of the last time you (or a sharing party member, for group storage) opened
  them. Until you open your bank in a session, the **Items** tab notes that
  counts reflect carried items only.

---

## Building and tests

```sh
./gradlew build          # compile targeting Java 11 and run all JUnit tests
./gradlew test           # run unit, golden-vector, and parity tests (headless)
./gradlew run            # launch a developer client with Quest Route loaded
```

Development builds resolve `runeLiteVersion` to `latest.release` and re-check
`repo.runelite.net` on every invocation, printing `RuneLite client: <version>`
before `test` and `run`. If a new RuneLite release breaks compilation on a day
nothing here changed, pin a known client version or build offline from cache:

```sh
./gradlew build -PruneLiteVersion=1.13.6
./gradlew build --offline
```

The Plugin Hub replaces `build.gradle` at submission (`build=standard`), so
dynamic version resolution affects development builds only.

Key regression gates in `./gradlew test`:

- `PlannerVectorsTest`, `LampVectorsTest`, `CombatMathTest` — golden-vector
  parity against all 218 quest verdicts, lamp rankings, and combat formulas
  (never re-export or edit vectors to silence a failing test).
- `OverlaySettingsPanelConfigParityTest` & `QuestpathConfigProxyTest` — every
  non-exempt `@ConfigItem` appears in the **View** tab, and every config return
  type is `public` for RuneLite's JDK dynamic proxy.
- `NoReflectionTest` & `UserFacingNameTest` — zero forbidden Plugin Hub APIs in
  `src/main`, and zero user-facing strings still reading `questpath` or `Quest
  Path`.
