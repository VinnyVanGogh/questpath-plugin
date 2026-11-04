# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added

- Group tab for RuneLite parties (Group Ironman): opt-in, off-by-default sharing of quest states, levels, build rules and quest-item counts over RuneLite's `PartyService` (`QuestpathGroupState`, `GroupMember`, `GroupBroadcaster`); group quest planning with build-rule flags for capped members (`GroupPlan`, `BuildBreaches`); per-quest item split with hand-overs (`ItemSplit`) (#9).
- Opt-in recommended quest items (`recommendedItems`) and teleport tablet-or-runes equivalence (`TeleportEquivalence`, `TeleportForm`, `recommendedTeleportForm`) across the Grand Exchange buy list, shop route, and Items tab, with recommended coins summed as a balance to bring (`QuestItems.recommendedCoins`) and quest graph v13 support (#4).
- Melee max-hit comparison lines under locked gear showing max hit without prayer and without potion (`920689b`).
- Melee strength potion selection (`StrengthPotion`: Strength, Super strength, Zamorak brew, Black warlock) on locked gear loadouts (`dbefed7`).
- Grand Exchange workflow screenshots and progress calculation notes in `README.md` (`94fe8a0`).
- Standalone Build Guard handover prompt (`docs/prompts/build-guard-standalone.md`) and ranked backlog (`docs/TODO-build-guard.md`) (`96578a0`, `ef848bf`, `00cf3c1`).
- Experience lamp and tome advisor (`LampAdvisor`, `LampOverlay`, `LampWatcher`, `LampCatalogue`) with per-account local build rules editor (`LocalBuildRules`, `BuildRulesEditor`) (`aedbc70`).
- Next-level max-hit breakpoint readout (`MaxHitStep`) under each combat style row in locked gear (`579a0e3`).
- Quests / Diaries / All journal filter (`QuestJournalFilter`), searchable achievement diary journal (`DiaryJournal`), left-click `Take` prioritisation for needed ground items (`GroundItemTakePriority`), and `item_plan` consumption from sync responses (`ItemPlan`) (`42cc299`).
- Weapon attack-style capture on locked loadouts (`LoadoutCapture`) from `ItemManager` offensive bonuses (`73631eb`).
- Achievement diary route overlay (`DiaryRouteOverlay`, `DiaryRouteStops`, `DiaryRouteGuard`) sending the nearest actionable diary task to Shortest Path (`59472f2`).
- Locked gear sidebar section (`LockedGear`, `LoadoutStore`, `Loadout`, `MaxHit`) with per-account RSProfile persistence and pure melee, ranged, and magic max-hit calculation (`f76a490`, `4e5b10d`, `5967b3d`, `717f657`, `89d4c8e`, `04e1745`).
- Manual "Sync with questpath" button on the Combat and Sync tabs (`dc762cc`).
- "Still needs work" and "Start, never finish" quest sections (`RouteWork`) in the Quests tab (`ace9e74`).
- Wiki requirement caveats carried in quest graph v11 and displayed under the Next row on the Grand Exchange overlay (`b4f75e5`).
- Item group resolution (`any pickaxe`, `a light source`) against held items and account skill levels (`554391d`).
- Quest Path button in the Grand Exchange chatbox search interface and live offer tracking overlay (`GrandExchangeOffers`, `GrandExchangeOverlay`, `GrandExchangeShoppingList`) (`fb81941`, `c803037`, `7c3b899`).
- Opt-in startup quest graph fetcher (`QuestGraphFetcher`) with version validation and chat status notice (`48da823`).
- Account region and activity constraint handling (`WARN` and `FATAL`, including Wilderness routing guards) (`1075c98`).
- Monster drop fallback routing (`ShopRouteStops`) when no reachable shop stocks a needed quest item (`178f9ca`).
- Shop reachability gating by skill level, quest requirements, and skill boosts (`0179247`).
- Floating world notifications (`FloatingNoteOverlay`) and 16-tile shopkeeper approach call-outs (`ShopApproachWatcher`) (`66ada79`, `a882fb8`, `c6bfb59`).
- Automatic shop route advancement on item completion (`ShopRouteAdvance`) and active shop route overlay (`ShopRouteOverlay`) (`7d3ac07`, `1d05910`).
- Shortest Path route integration (`ShortestPathBridge`) and automatic Quest Helper start on arriving within 8 tiles of a route quest (`QuestStartArrivalWatcher`) (`4c54ee5`).
- Started-only quest prerequisite support in quest graph v6 and `QuestClassifier` (`705cccd`).
- Quest Helper hand-off (`QuestHelperBridge`) from the Quests and Skills tabs with optional auto-advance on quest completion (`a6c042f`, `51ee08a`).
- Skill boost magnitude tables (`Boosts`) in `QuestClassifier` and blocker tooltips (`1caa018`).
- Skills tab (`SkillPlan`) pricing target levels across Ready now, After more work, Off the table, and Left to train (`9ad11e1`, `6d350d5`).
- In-bank Quest Path tab (`BankTabLayout`, `BankNeedsOverlay`, `BankListOverlay`) grouping quest items into Ran out, Still needed, Ready to withdraw, and Everything else (`b0b1ce2`, `a5448c5`).
- EDT stall watchdog (`EdtWatchdog`) and in-progress carried item tracking (`7da5ada`, `72578f2`).
- Shop stock outlines (`ShopItemOverlay`), 15-tile ground item highlights (`GroundItemOverlay`), and sidebar View settings tab (`OverlaySettingsPanel`) (`f95e1ea`, `4f53aa3`).
- Five configurable in-game overlays (`NextQuestOverlay`, `NeededItemsOverlay`, `PickupsOverlay`, `BuildRulesOverlay`, `CombatXpOverlay`) with text/icon display modes, multi-column packing, and per-panel width and list length (`3cf71cc`, `24283a9`, `a19336b`, `7fd69dd`).
- Quest browser search, verdict filter, cross-quest item shopping list, and "Get while you're here" pickup suggestions (`QuestItems`) (`c8c4651`, `7c94b78`, `0fcddcc`, `2917c46`).
- Offline quest verdict engine (`QuestClassifier`, `QuestGraph`) pinned to 218 golden vectors exported from the Go planner (`4c87e0a`).
- Opt-in account state push (`AccountState`, `AccountStateSender`, `OwnedItems`) sending quests, levels, real XP, bank, inventory, and equipment to a self-hosted `questpath` server (`9b8b4d1`, `16d3c1d`, `70c6dfa`).
- Initial RuneLite plugin scaffold and combat level / XP ceiling calculator (`CombatMath`, `XpTable`, `SkillXp`) (`8d3d3a8`, `db37218`, `fdb0c8f`).

### Changed

- Bumped `runeLiteVersion` to `1.13.5` so the developer client connects after the game update (#7).
- Re-exported bundled quest graph and planner vectors after NPC shop-town, item-link, and iron bar ingest updates (`02982e8`, `24014e8`, `e6f72f4`).
- Replaced initial `README.md` overview with in-game screenshots of the shop run, bank tab, ground markers, and all six sidebar tabs (`6bd6a33`).
- Merged `Tab.BUILD` into `Tab.COMBAT` and placed non-blocking build rules behind a "Show all rules" toggle (`93aa574`).
- Ordered shop trips as a nearest-neighbour tour from the player's current position, advancing one stop at a time (`460b597`, `92603a5`).
- Switched sidebar tab icons to native client sprites via `SpriteManager` (`f2b1999`, `e84b2b5`, `f0a76f5`).

### Fixed

- Followed Quest Helper v4.18.1's Recipe for Disaster renames (Zoinkwiz/quest-helper#2843, the Plugin Hub pin `02315c94`) in `QuestHelperBridge.SPLIT_QUEST_DEFAULT_HELPER`: "Freeing the Mountain Dwarf" now requests `"RFD - Mountain Dwarf"` (was `"RFD - Dwarf"`) and "Freeing King Awowogei" requests `"RFD - King Awowogei"` (was `"RFD - Monkey Ambassador"`), so neither reports "Not supported by Quest Helper" any more. `QuestHelperBridgeTest` now pins every name in that map to Quest Helper's names at the pinned commit.
- Copied existing rune totals before merging in `TeleportEquivalence.decompose` so per-frame overlay rendering does not mutate shared `QuestItems.Total` counts, and replaced item ID `8013` (Teleport to house) with `ItemID.TELETAB_DRAYNOR` on `TeleportEquivalence.DRAYNOR` (#4).
- Labelled id-less generic wiki item requirements (such as `"Seeds"`) as `"check wiki"` (`Availability.GENERIC`) instead of `"can't look up"` on the Grand Exchange overlay (#5).
- Changed the Grand Exchange item-completion chat notice (`ItemCompletionWatcher.Completion`) to read `"you now have the <item>"` instead of `"you now have all 1 <item>"` when a quest needs only one (#3).
- Stopped offering members-only items on the Grand Exchange buy list when logged into a free-to-play world (`ad53e0f`).
- Made `LampSuggestions` public so RuneLite's JDK dynamic config proxy does not throw `IllegalAccessError` on plugin startup, and added `QuestpathConfigProxyTest` to require public return types on all `@ConfigItem` methods (`e1fcbd5`).
- Removed duplicate Elite Void 5% subtraction from gear magic damage bonus in `MaxHit.magic` (`8fcf77f`).
- Applied gear magic damage percentage in `MaxHit.magic` and marked Toxic blowpipe ranged max hits as not modelled (`a0136c2`).
- Guarded powered-staff max hits in `MaxHit.magic` so formulas that drop below 1 at low Magic levels report `notModelled` instead of a negative max hit, and wired charged Accursed sceptre, Thammaron's sceptre, and Bone staff IDs (`9104dcf`, `ebcf924`).
- Synchronized `LoadoutStore` access and trimmed loadout names in `LoadoutCodec` (`df392f8`).
- Kept the shop route active across monster drop stops, removed the 5-minute expiry from trip liveness, and required opening the target shop to finish a stop (`54f8b35`).
- Cleared the Grand Exchange collected-to-bank tally only when the bank container (`InventoryID.BANK`) is read rather than on any container update after `bankSeen()` becomes true (`ffef5fc`).
- Credited filled Grand Exchange offers when collected to the bank so items do not reappear on the Left list before the bank is reopened (`eb81176`).
- Mapped all ten Recipe for Disaster sub-quests to Quest Helper's enum names in `QuestHelperBridge` (`173e850`).
- Resolved `<N> sets of <item>` and dash-suffixed wiki item lines, and ignored combat wield requirements on skilling tools (`pickaxe`, `axe`) while parsing each wiki table against its own column headers (`b4f75e5`, `cc1baf3`).
- Excluded post-ladder ornamental and special tool variants (such as gilded and 3rd Age pickaxes) from recommended purchases while still counting them when already held (`554391d`).
- Counted Grand Exchange offer progress against the quest's needed quantity rather than the offer size (`0d59d7a`).
- Lowercased Grand Exchange search queries to avoid triggering RuneLite's fuzzy-search red underlines, excluded untradeable items (`isGeTradeable()`) from the GE buy list, and included Group Ironman variants in the ironman check (`c803037`, `5ed9f91`, `7c3b899`).
- Tested shop approach proximity on `GameTick` as the player moves and cleared the overhead shout when a shop interface opens (`9d73a78`, `21475e1`, `66ada79`).
- Cached the player's `WorldPoint` on the client thread so rendering the Items tab on the Swing EDT does not throw `AssertionError` (`228a6f9`).
- Sent the `"clear"` message to Shortest Path instead of an empty `"path"` target set that re-sent the previous route (`7ae76f7`).
- Aligned the shop skip button and route overlay header with the first routable stop rather than the first unroutable item in the list (`989c377`, `85fa226`).
- Included adjacent walkable tiles around a shopkeeper's coordinate so Shortest Path does not fail on blocked counter tiles (`e184b67`).
- Hid the Items tab container during `skipRow` rebuilds and re-sent the Shortest Path route on skip and undo (`2c6330a`, `8abe671`).
- Replaced missing Unicode arrow and triangle glyphs (`U+2192`, carets, check marks) with characters supported by `runescape_small.ttf` or painted polygons (`a882fb8`, `a6c042f`, `c44776c`).
- Hid tab bodies before `removeAll()`, gated mid-quest arrival prompts on in-progress route quests, suppressed automatic Quest Helper starts during active shop routes, and posted Shortest Path events on the client thread (`5633896`, `63fb418`, `50839a6`).
- Added `OverlaySettingsPanelConfigParityTest` to keep sidebar settings in sync with `QuestpathConfig` and fixed `NeededItemsOverlay` hiding when the bank tab button was inactive (`7c0001c`).
- Parented the bank Quest Path button to `Bankmain.UNIVERSE` offset from Quest Helper's button, and deselected the active bank tab when switching to Quest Path (`956c30d`, `24dc24b`, `0fa4d69`).
- Ignored bank placeholder items via `getPlaceholderTemplateId()` so empty placeholders are not counted as owned items (`8960c40`).
- Cancelled the `EdtWatchdog` scheduled task in `shutDown()`, switched tab bodies with `CardLayout` instead of `removeAll()`, replaced per-row HTML `JLabel` instances with plain labels, and coalesced refresh passes to once per game tick (`7da5ada`, `d0e5bd1`, `1d92821`, `6c1155e`).
- Refreshed overlays on `ItemContainerChanged` and raised build-rule violation red contrast to WCAG AA (`4f53aa3`).
- Skipped account-state pushes when `getLocalPlayer()` returns an empty username and cleared stale build rules on a rejected push (`513fa54`).
- Matched completed quests by name instead of comparing RuneLite enum IDs against database row IDs (`83268e7`).
- Repainted the sidebar when the first push response arrives (`4e5bb8c`).
- Reopened `java.desktop/com.apple.eawt` in the Gradle `run` task on macOS so the developer client starts on Java 11+ (`a9a77b9`).

### Known issues

- `QuestHelperBridge` reaches Quest Helper by reflection across plugin class loaders, which blocks Plugin Hub submission. Its replacement is `PluginMessage`, and that waits on two open upstream PRs reaching a Plugin Hub pin: Zoinkwiz/quest-helper#2756 (`"questhelper"`/`"start"` with `{quest, source}`) and Zoinkwiz/quest-helper#2846 (ours, `"questhelper"`/`"selected"` with `{quest}`). Neither is in v4.18.1, the current pin. Scope and status in `docs/TODO-build-guard.md`.
