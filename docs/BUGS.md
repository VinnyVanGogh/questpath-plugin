# Bug Log

Merged bug fixes in reverse chronological order (newest first). Each entry records what broke, how it was found, and the commit or PR that fixed it.

---

## Sidebar Grand Exchange single-item click wrote item names into `VarClientStr.INPUT_TEXT` and `src/main` retained reflection types
- **What broke:** Clicking a row in the sidebar's Grand Exchange list (`GrandExchangeSearchBridge`) wrote `searchText(itemName)` directly into `VarClientStr.INPUT_TEXT` (varcstr 359, the chatbox message-layer input) and replayed the search box's key listener, violating the Plugin Hub's no-autotyping rule. Separately, `QuestHelperBridge` used `java.lang.reflect` and `BuildRuleCodec` used `com.google.gson.reflect.TypeToken` (`java.lang.reflect.Type`), violating the Plugin Hub's ban on reflection.
- **How it was found:** Caught during the Plugin Hub compliance audit and bridge re-check before first submission.
- **Fix:** Routed single-item Grand Exchange clicks by item ID through the buy-list button's sentinel search (`GrandExchangeButton#showOne`) so no search term is written into the chatbox input, disabled clicks on id-less rows, switched `BuildRuleCodec` to `BuildRule[].class`, removed `QuestHelperBridge` until Quest Helper exposes a `PluginMessage` receiver (`Zoinkwiz/quest-helper#2756`), and added `NoReflectionTest` and `QuestHelperAbsentTest` (#14).

## Developer client failed to connect after game update on RuneLite 1.12.37
- **What broke:** Launching the developer client against the pinned `runeLiteVersion = '1.12.37'` failed to connect after an Old School RuneScape game update (`error_game_js5connect_outofdate`).
- **How it was found:** Observed when launching the developer client after the game update.
- **Fix:** Bumped `runeLiteVersion` in `build.gradle` to `1.13.5` (#7).

## Teleport rune decomposition mutated shared totals every frame and mapped Draynor to house tablets
- **What broke:** Merging decomposed teleport runes into an existing rune row in `TeleportEquivalence` mutated the caller's shared `QuestItems.Total` in place on every overlay frame, causing the displayed rune count to climb by one cast per frame. In addition, `TeleportEquivalence.DRAYNOR` used item ID `8013` (Teleport to house) instead of `ItemID.TELETAB_DRAYNOR` (`19615`), which would count a house tablet in the bank as a Draynor manor teleport.
- **How it was found:** Caught while extracting `TeleportEquivalence.decompose` and replacing raw tablet IDs with `gameval` constants during the RUN-9 implementation.
- **Fix:** Copied the existing `QuestItems.Total` before absorbing decomposed rune totals in `TeleportEquivalence.decompose`, and switched `DRAYNOR` and all other tablet recipes to `ItemID` gameval constants (#4).

## Grand Exchange overlay labelled generic wiki requirements ("Seeds") as "can't look up"
- **What broke:** `GrandExchangeOverlay.reason()` fell through to its `"can't look up"` default for `quests.json` requirements with no `item_id` (such as `"Seeds"` in Barbarian Training), which describe an open-ended kind of item on the wiki rather than a specific item whose client lookup failed.
- **How it was found:** Observed in an in-game Grand Exchange screenshot (`docs/screenshots/ge-tracker.jpg`) and filed as issue #1.
- **Fix:** Added `GrandExchangeShoppingList.Availability.GENERIC` for requirements with `itemId <= 0` and rendered it as `"check wiki"` on `GrandExchangeOverlay`, reserving `UNKNOWN` (`"can't look up"`) for real item IDs that the client gave no answer for (#5).

## Grand Exchange collection chat line said "you now have all 1 X" for single items
- **What broke:** `QuestpathPlugin.announce()` hardcoded `"Quest Path: you now have all "` + `done.required() + " " + done.itemName()`, producing `"you now have all 1 <item>"` when a quest needed only one of an item.
- **How it was found:** Observed in an in-game screenshot (`docs/screenshots/ge-collected.jpg`) and filed as issue #2.
- **Fix:** Added `leadIn()` and `subject()` to `ItemCompletionWatcher.Completion` so a single needed item reads `"you now have the <item>"` while quantities above one keep `"you now have all <N> <item>"` (#3).

## Members-only items offered on the Grand Exchange in free-to-play worlds
- **What broke:** `GrandExchangeShoppingList` included members-only items (such as Adamant seeds, Black knife, Caviar, Dragon axe, and Dragon pickaxe) in the clickable GE buy list while logged into a free-to-play world, where the game rejects the offer with `"You can't buy members-only items on this world."`
- **How it was found:** Reported from in-game play on a free-to-play world.
- **Fix:** Added `Availability.MEMBERS_ONLY` gated on the live world type (`WorldType.MEMBERS`) so members items are separated into a `"members world"` section and excluded from `left()` on free worlds (`ad53e0f`).

## Package-private `LampSuggestions` enum prevented plugin startup
- **What broke:** The plugin failed to start with `java.lang.IllegalAccessError: failed to access class com.vinnyvangogh.questpath.LampSuggestions from class jdk.proxy2.$Proxy179`, taking down the sidebar and all overlays. RuneLite implements `@ConfigGroup` interfaces via a JDK dynamic proxy in the `jdk.proxy2` module, which cannot access a package-private return type.
- **How it was found:** Observed in the client log on plugin startup.
- **Fix:** Made `LampSuggestions` `public` and added `QuestpathConfigProxyTest` to assert that every `@ConfigItem` return type in `QuestpathConfig` is `public` (`e1fcbd5`).

## Elite Void 5% bonus subtracted twice from gear magic damage
- **What broke:** `MaxHit.magic` subtracted 5% from `GearBonuses.magicDamagePercent()` when Elite Void mage was worn, under-reporting magic max hits for Elite Void loadouts that also wore magic-damage gear (e.g., returning 26 instead of 27 for Fire Surge with +10% item magic damage). `LockedGear.resolve` sums `magicDamagePercent` purely from per-item `ItemEquipmentStats.getMdmg()`, which never included the Elite Void set bonus in the first place.
- **How it was found:** Caught during pre-merge code review by running the compiled `MaxHit` class against an Elite Void + item magic-damage vector.
- **Fix:** Removed the Void subtraction from `magicDamagePercent` before scaling by `ShadowBonus`, keeping Elite Void added once as its own term (`8fcf77f`).

## Gear magic damage bonus ignored and Toxic blowpipe ranged max hit miscalculated
- **What broke:** `MaxHit.magic` never read `GearBonuses.magicDamagePercent()`, silently under-reporting magic max hits for any loadout wearing magic-damage equipment (Occult necklace, Ancestral robes, Tormented bracelet, god capes, or Tumeken's shadow). Separately, `MaxHit.ranged` computed an unverified ranged max hit for the Toxic blowpipe without knowing the loaded dart's ranged strength.
- **How it was found:** Caught during whole-branch code review of the max-hit implementation.
- **Fix:** Implemented the full `PrimaryMagicDamage` pipeline in `MaxHit.magic` (including x3 Shadow Bonus outside ToA and `GearEffect.TUMEKENS_SHADOW_TOA_BONUS`), and detected `TOXIC_BLOWPIPE` / `TOXIC_BLOWPIPE_LOADED` in `GearEffects` so `MaxHit.ranged` returns `notModelled` (`a0136c2`).

## Powered staves returned zero or negative max hits at low Magic levels
- **What broke:** Thammaron's sceptre (`magicLevel / 3 - 8`) has no stated minimum in the formula and evaluates to zero or negative below Magic 27, which flowed directly into `MaxHitResult.known()` as a valid max hit. In addition, charged IDs for Accursed sceptre, Thammaron's sceptre, and Bone staff were missing from `poweredStaffBase()`.
- **How it was found:** Caught during code review of the max-hit task (`ebcf924`, `9104dcf`).
- **Fix:** Wired the charged gameval IDs into `poweredStaffBase()`, returned `notModelled` whenever a powered staff's base (other than Accursed sceptre's documented 0 minimum) is below 1, and made `MaxHitResult.known()` reject negative values with `IllegalArgumentException` (`9104dcf`, `ebcf924`).

## Unsynchronized `LoadoutStore` access and untrimmed loadout names
- **What broke:** `LoadoutStore` accessed its cache without synchronization across threads, and `LoadoutCodec` accepted leading/trailing whitespace that diverged from `Loadout`'s trimmed name invariant.
- **How it was found:** Caught during code review of the loadout persistence task.
- **Fix:** Synchronized `LoadoutStore` methods and trimmed names in `LoadoutCodec` (`df392f8`).

## Shop route ended after one item when remaining stops were monster drops
- **What broke:** The shop route overlay disappeared after the first item and declared `"shopping done"`, or ended early when visiting an unrelated general store. `ShopRouteAdvance` called the shop-only overloads of `ShopRouteStops` (ignoring monster drop stops), `shopRouteActive()` reused `ShopRouteGuard`'s 5-minute Quest Helper suppression timer, and opening any shop interface ended the trip.
- **How it was found:** Reported from in-game play while routing to monster drops.
- **Fix:** Passed the drop locator into `ShopRouteAdvance`, removed the 5-minute expiry from trip liveness, and required the opened shop to match the current target stop before ending a trip (`54f8b35`).

## Grand Exchange collected-to-bank tally cleared on non-bank container updates
- **What broke:** Collecting a Grand Exchange offer to the bank still caused the item to fall back into `Left` for any player who had opened their bank earlier in the session. `QuestpathPlugin` called `forgetCollected()` whenever `owned.bankSeen()` was true, which stays true for the entire session and fired on every inventory (`93`) or GE collection box (`521`) event.
- **How it was found:** Caught via a live in-game container trace after `eb81176` failed when a bank had been opened earlier in the session.
- **Fix:** Keyed `forgetCollected()` specifically on `InventoryID.BANK` (`95`) container updates rather than `owned.bankSeen()` (`ffef5fc`).

## Collecting a Grand Exchange offer to the bank returned the item to "Left"
- **What broke:** Clicking "Collect to bank" on a completed GE offer emptied the offer slot without updating the client's bank snapshot (which is only updated when the bank interface is opened), causing the item to appear unbought and move back to `Left`.
- **How it was found:** Reported from in-game play at the Grand Exchange.
- **Fix:** Credited filled quantities when an offer slot empties and merged them with `max` until the bank container is next read (`eb81176`).

## Recipe for Disaster sub-quests reported as unsupported by Quest Helper
- **What broke:** All ten Recipe for Disaster sub-quests displayed `"Not supported by Quest Helper"` in the right-click menu because `QuestHelperBridge` only mapped the umbrella name `"Recipe for Disaster"` while the quest graph names sub-quests `"Recipe for Disaster/<Subquest>"`.
- **How it was found:** Reported from in-game play when starting RFD sub-quests from the route list.
- **Fix:** Mapped all ten sub-quest names to Quest Helper's `QuestHelperQuest` enum names verified via `javap` (`173e850`).

## Wiki item parser dropped "sets of" items, dash clauses, and parenthetical caveats
- **What broke:** `"3 sets of Goblin mail"` failed to resolve because `"sets of"` was treated as part of the item name; `"1 Orange dye – created by..."` kept the en-dash clause as the item name; and parenthetical caveats such as `"(4 if you want your own blurite sword)"` were stripped at export and never shown to the player.
- **How it was found:** Reported from in-game play during Goblin Diplomacy and The Knight's Sword.
- **Fix:** Offered the tail after `" of "` as a fallback candidate, stripped clauses after spaced ASCII and en dashes, and exported requirement notes in graph v11 to display under the GE Next row (`b4f75e5`, `24014e8`).

## Skilling tools gated on Attack level and felling axes parsed against wrong column headers
- **What broke:** An account with 41 Mining and Attack capped at 1 was told to buy an iron pickaxe instead of a rune pickaxe because `QuestItems` treated a tool's Attack wield requirement as a requirement to use it from the inventory. Investigating this also revealed that the wiki `Axe` page's felling-axe table (which lacks a shop-price column) was parsed against the first table's headers, filing Woodcutting levels under Attack.
- **How it was found:** Reported from in-game play on a 1-Attack account and traced into the wiki group parser.
- **Fix:** Ignored combat skill requirements on item groups that also require a non-combat skill, and parsed each wikitable on a group page against its own column headers (`cc1baf3`).

## Item group resolver recommended ornamental variants at the end of wiki tables
- **What broke:** Selecting the last usable row in a wiki item-group table recommended a Gilded pickaxe instead of a Rune pickaxe at 41 Mining, because ornamental and special variants (gilded, 3rd Age, dragon (or), infernal) are appended after the main progression ladder.
- **How it was found:** Caught by unit tests against the bundled item groups before in-game play.
- **Fix:** Split each table at the first row whose stated skill requirement decreases, recommending purchases only from the leading progression ladder while counting all variants when checking owned items (`554391d`).

## Grand Exchange offer progress counted against offer size instead of quest need
- **What broke:** Buying 10 Pots of flour when a quest needed 1 displayed `"10 of 10"`, and partial fills that already covered the quest's requirement did not mark the row ready to collect until the entire offer completed.
- **How it was found:** Reported from in-game play at the Grand Exchange.
- **Fix:** Formatted progress against the needed quantity (`Line.progress()`, e.g., `"10 of 1"`) and marked offers ready to collect as soon as `Order.covers(needed)` is satisfied (`0d59d7a`).

## Grand Exchange search row overlapped chatbox prompt, triggered fuzzy-search underlines, and offered untradeable or Group Ironman items
- **What broke:**
  1. The initial custom GE suggestion row called client script `752` with no arguments (it requires three), overlapped `Chatbox.MES_LAYER` or hid when `VarClientStr.INPUT_TEXT` retained the previous search, and sent title-case names that caused RuneLite's `GrandExchangePlugin` case-sensitive filter to fall back to fuzzy search with red underlines (`fb81941`, `c803037`, `5ed9f91`).
  2. Untradeable quest items such as Karamjan rum were offered on the GE buy list (`c803037`).
  3. `AccountType.isIronman()` returned `false` for `GROUP_IRONMAN` and `HARDCORE_GROUP_IRONMAN` because their enum ordinals sit after `HARDCORE_IRONMAN` (`7c3b899`).
- **How it was found:** Reported from in-game clicks at the Grand Exchange (`5ed9f91`, `c803037`, `fb81941`) and caught by an exhaustive `AccountType` unit test (`7c3b899`).
- **Fix:** Replaced the custom row with a native chatbox button beside Quest Helper's (`x=456`) that populates `setGeSearchResultIds` on `GrandExchangeSearched`, lowercased search strings, gated buyable items on `isGeTradeable()`, and checked `isGroupIronman()` alongside `isIronman()` (`fb81941`, `c803037`, `5ed9f91`, `7c3b899`).

## Shopkeeper approach shout fired only after account state changed and lingered while trading
- **What broke:** `checkShopApproach` lived inside `refresh()`, which only runs when account state changes (such as opening a shop or gaining XP), so walking past a shop never triggered a call-out until the player opened the shop. Once triggered, the overhead note stayed visible for 10 seconds behind the open shop interface.
- **How it was found:** Reported from in-game play when walking up to shops.
- **Fix:** Moved `checkShopApproach` to `onGameTick` and cleared active shop-approach overhead notes when a shop interface opens (`9d73a78`, `21475e1`, `66ada79`).

## Items tab went blank due to calling `client.getLocalPlayer()` on the Swing EDT
- **What broke:** `QuestpathPanel.shopRouteButton` called `QuestpathPlugin.playerLocation()` during Swing rendering to order shop destinations, throwing `java.lang.AssertionError: must be called on client thread` and aborting the rest of `itemsBlock()`.
- **How it was found:** Observed in-game when the Items tab rendered blank and logged an `AssertionError`.
- **Fix:** Cached the player's `WorldPoint` on the client thread each `GameTick` and read the cached coordinate from the Swing panel (`228a6f9`).

## Shopping list and shop route ordered alphabetically instead of geographically
- **What broke:** Before the bank was opened, all needed items tied on completion ratio and fell back to alphabetical sorting, sending the player back and forth across the map (e.g., Catherby to Al Kharid and back to Catherby).
- **How it was found:** Reported from in-game play while running a shop trip.
- **Fix:** Ordered shop stops as a nearest-neighbour geographic tour starting from the player's current position (`460b597`).

## "Stop the route" button flickered and re-sent the previous Shortest Path route
- **What broke:** Clicking "Stop the route" sent a `"path"` `PluginMessage` with an empty target set. Shortest Path treats an empty target set on `"path"` as a request to restart pathfinding to its existing targets rather than clearing the route.
- **How it was found:** Reported from in-game play and confirmed by disassembling Shortest Path's `onPluginMessage` handler.
- **Fix:** Sent Shortest Path's `"clear"` message instead of `"path"` when stopping a route (`7ae76f7`).

## Shop route overlay and skip button named unroutable items instead of the active stop
- **What broke:** When the first item on the shopping list had no known shop or drop location (such as Barley malt or a Black candle), the overlay header and skip button named that unroutable item while Shortest Path routed to the first routable item further down the list, and clicking Skip appeared to do nothing.
- **How it was found:** Reported from in-game play when the route line pointed to the Lumbridge Swamp Caves while the UI named Black candle.
- **Fix:** Advanced the active stop and skip target to the first routable item, sent one stop at a time to Shortest Path, and re-sent the route on skip and undo (`989c377`, `85fa226`, `92603a5`, `8abe671`).

## Shop route sent players to guilds they lacked the levels to enter and to unwalkable counter tiles
- **What broke:**
  1. The shop router sent an account with 59 Fishing to the Fishing Guild Shop (requires 68 Fishing) instead of Harry's Fishing Shop in Catherby, and Catherby was missing because the export capped items at two shops (`e6f72f4`, `0179247`).
  2. Routing to a single shop failed with `"destination could not be reached"` because a shop's recorded coordinate is the tile the shopkeeper stands on behind a counter (`e184b67`).
- **How it was found:** Reported from in-game play with screenshots of Shortest Path failing beside a shop counter.
- **Fix:** Gated shop reachability on skill levels, quest states, and boosts; removed the two-shop export cap; and passed the 8 surrounding neighbour tiles alongside each shop coordinate to Shortest Path (`e6f72f4`, `e184b67`, `0179247`).

## Half-second to four-second client freezes when rebuilding the Items tab or switching tabs
- **What broke:** Calling `Container.removeAll()` on a visible Swing container with hundreds of child components triggered AWT heavyweight/lightweight component mixing (`Component.mixOnHiding -> recursiveApplyCurrentShape`) once per child, stalling the EDT for ~500ms on `skipRow` and up to 4 seconds when leaving the Items tab or rebuilding on every `ItemContainerChanged` slot event.
- **How it was found:** Caught by `EdtWatchdog` stack dumps and performance logs during in-game sessions.
- **Fix:** Switched tabs via `CardLayout` (`setVisible`) instead of `removeAll()`, hid tab bodies and `fillItemResults` containers before emptying them, replaced per-row HTML `JLabel` documents with plain labels, and coalesced `ItemContainerChanged` / `StatChanged` rebuilds to once per game tick (`2c6330a`, `5633896`, `d0e5bd1`, `1d92821`, `6c1155e`).

## Unicode arrows, carets, and check marks rendered as orange tofu boxes
- **What broke:** Characters such as `U+2192` (`→`), Unicode triangles, and check marks have no glyph in `runescape_small.ttf` and rendered as orange placeholder boxes in the sidebar.
- **How it was found:** Observed in-game on the View, Quests, and Items tabs.
- **Fix:** Replaced `U+2192` with `U+00B7` (`·`), used plain ASCII markers in quest rows, and painted collapsible section chevrons as 9px polygons (`a882fb8`, `a6c042f`, `c44776c`).

## Shop-route button threw `AssertionError` on the Swing EDT and Quest Helper overwrote active shop routes
- **What broke:** Pressing the shop-route button posted a `PluginMessage` from the Swing EDT; Shortest Path asserted the client thread via `AssertionError` (an `Error`, which bypassed `catch (RuntimeException)` and falsely reported success). In addition, walking near a quest start during a shop run triggered an automatic Quest Helper start, which immediately overwrote Shortest Path's shop route, and the mid-quest arrival gate failed when Quest Helper's sidebar was closed or offered quests the account lacked levels for.
- **How it was found:** Reported from in-game play and traced in the client log.
- **Fix:** Dispatched `PluginMessage` posts via `clientThread`, caught `RuntimeException | AssertionError`, suppressed automatic Quest Helper starts while a shop route is active, gated arrival prompts on eligible/boostable quests not already in progress, and checked `QuestHelperPlugin`'s public Lombok getters without private reflection (`5633896`, `63fb418`, `50839a6`, `a6c042f`).

## Sidebar settings missing config toggles and bank overlays hiding each other
- **What broke:** Five `@ConfigItem` settings existed in RuneLite's config panel but were missing from `OverlaySettingsPanel`, and `NeededItemsOverlay` hid itself whenever the bank was open and `bankList` was enabled in config—even when the in-bank Quest Path title-bar button was toggled off, leaving neither overlay visible.
- **How it was found:** Found during in-game testing of the bank and sidebar settings.
- **Fix:** Added `OverlaySettingsPanelConfigParityTest` to require every non-exempt `@ConfigItem` to appear in the sidebar, and checked the in-bank button state before hiding `NeededItemsOverlay` (`7c0001c`).

## QuestClassifier treated any boostable requirement as satisfied regardless of boost size
- **What broke:** A quest requirement marked `boostable` on the wiki returned `BOOSTABLE` for any level shortfall—reporting, for example, a `+9` Cooking boost for Zogre Flesh Eaters when the maximum Cooking boost in the game is `+6`.
- **How it was found:** Reported from in-game inspection of quest tooltips.
- **Fix:** Exported the skill boost table in quest graph v5 and required `Boosts.covers()` to verify both permission and boost magnitude at the player's current level (`3126614`, `1caa018`).

## Bank Quest Path button failed to render, overlapped Quest Helper's button, or hijacked normal bank tabs
- **What broke:** The bank button was initially parented to `Bankmain.TITLE` (a `TEXT` widget that never lays out children), then placed at `x=408, y=5` directly on top of Quest Helper's bank button, and `BankTabLayout` defaulted on and replaced the contents of whichever numbered bank tab was open without updating the title or deselecting the tab.
- **How it was found:** Observed in-game when opening the bank alongside Quest Helper.
- **Fix:** Parented the button to `Bankmain.UNIVERSE` at `x=380, y=5` with a backpack icon, defaulted the bank tab off, reset `BANK_CURRENTTAB` to `0` and titled it `"Tab Quest Path"` while active, and exited the tab when any standard bank tab or search is clicked (`956c30d`, `24dc24b`, `0fa4d69`).

## Bank placeholders counted as owned items and `EdtWatchdog` leaked threads on plugin restart
- **What broke:** Empty bank placeholders (such as item `17281` for `"Pigeon cage"`, which carries quantity `1` in the bank container) were merged by name and counted as owned items. Separately, `EdtWatchdog` spawned an uncancellable `while (true)` daemon thread on every `startUp()` that was never stopped in `shutDown()`.
- **How it was found:** Placeholder bug reported from in-game bank counts (`8960c40`); thread leak caught during code review (`7da5ada`).
- **Fix:** Filtered out bank placeholders via `ItemComposition.getPlaceholderTemplateId()`, and moved `EdtWatchdog` onto RuneLite's injected `ScheduledExecutorService` with its `ScheduledFuture` cancelled in `shutDown()` (`8960c40`, `7da5ada`).

## Picking up an item did not update overlays until banking, and build-rule red failed WCAG AA contrast
- **What broke:** `onItemContainerChanged` updated `owned` without triggering `refresh()`, leaving overlays and sidebar counts stale until the next bank visit. In addition, the shared `OverlayWidth` / `OverlayListLength` dropdowns offered `"Match the shared setting"` to themselves, and the red warning colour for broken build rules had a 3.93:1 contrast ratio over sunlit grass.
- **How it was found:** Observed in-game when picking up quest items, and measured by a WCAG contrast test (`4f53aa3`).
- **Fix:** Triggered a coalesced refresh on `ItemContainerChanged`, split `PanelWidth` / `PanelListLength` from the shared enums, and adjusted the overlay background opacity and red token to 5.95:1 contrast (`4f53aa3`).

## Account-state pushes with an empty player name overwrote another character's quests
- **What broke:** When `client.getLocalPlayer()` was null or had no name yet, `AccountState.read()` sent `""` as the username, causing the server to route one character's quest states over another account's record. In addition, a rejected push left the previous account's build rules displayed on screen.
- **How it was found:** Discovered after three pushes overwrote another character's quest state during multi-account testing.
- **Fix:** Returned `null` from `AccountState.read()` when the player or username is blank so the push is skipped, and cleared displayed build rules when a push is rejected (`513fa54`).

## Quest completion matched RuneLite enum IDs against unrelated SQLite row IDs, and unstated item quantities multiplied across quests
- **What broke:** `QuestpathPlugin` built the completed-quest set from RuneLite `Quest.getId()` values and compared them against `questpath`'s SQLite row IDs, marking untouched quests complete and completed quests undone. In the Items tab, non-consumed tools without a stated quantity (such as a Ghostspeak amulet wanted by three quests) were summed to `x3` instead of `1`.
- **How it was found:** Found in-game when searching `"dragon"` in the Quests tab returned only Dragon Slayer II and the shopping list asked for `"Ghostspeak amulet x3"`.
- **Fix:** Matched quest completion by canonical quest name, counted unstated item quantities once across quests while summing explicit quantities, and repainted the sidebar when the initial sync response arrives (`83268e7`, `4e5bb8c`).

## `Skill.OVERALL` returned null icon and level thresholds overstated XP costs
- **What broke:** Using `Skill.OVERALL` for the Sync tab icon threw / fell back to text because `Skill.OVERALL` is never assigned by RuneLite's static initializer. Earlier in the Combat tab, XP costs were estimated from level thresholds (`xpFor(target) - xpFor(current)`), overstating the remaining grind for partially trained levels, and the macOS developer client crashed on startup with `IllegalAccessError` accessing `com.apple.eawt.FullScreenAdapter`.
- **How it was found:** Caught by `everyTabHasAnIcon` unit test (`e84b2b5`), in-game inspection of XP costs (`8d3d3a8`), and launching `./gradlew run` on macOS (`a9a77b9`).
- **Fix:** Avoided `Skill.OVERALL`, read exact experience from `Client.getSkillExperience(Skill)` validated by `SkillXp` (`8d3d3a8`), and added `--add-exports java.desktop/com.apple.eawt=ALL-UNNAMED` to the macOS `run` task in `build.gradle` (`a9a77b9`).
