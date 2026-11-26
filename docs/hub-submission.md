# Plugin Hub submission — Quest Route

Prepared 2026-11-14 for the first submission. **Not submitted.** The Board
submits; this file is what to paste and what is still open.

## 1. Manifest

File `plugins/quest-route` in a fork of `runelite/plugin-hub`, no extension:

```
repository=https://github.com/VinnyVanGogh/questpath-plugin.git
commit=<full 40-char hash of the merged main commit>
```

- `repository=` changes if the GitHub repo is renamed (the Board's call; the
  repo is still `questpath-plugin`).
- No `warning=` line: the server sync was removed for the first release (see
  section 5), so nothing in the plugin contacts a third-party server. Add the
  line back, with the in-plugin `warning =` on the config item, when sync
  returns.
- Slug `quest-route`: `plugins/quest-route` returned HTTP 404 on
  `raw.githubusercontent.com/runelite/plugin-hub/master` on 2026-11-14, and no
  plugin-hub PR title matches it. **Recheck immediately before submitting.**

## 2. Pre-submission checklist

| Item | Status | Evidence |
| --- | --- | --- |
| Repo is public | **NO — Board** | `GET api.github.com/repos/VinnyVanGogh/questpath-plugin` unauthenticated → 404 on 2026-11-14 |
| `icon.png` at repo root, ≤48×72, real PNG, optimised | yes | `file icon.png` → `PNG image data, 48 x 48, 8-bit/color RGBA`; 286 bytes; drawn from scratch (scroll + dotted route + X), no game art |
| Resources via `getResourceAsStream` only | yes | `LampCatalogue.java:108`, `QuestGraph.java:1121`; no `getResource(` anywhere in `src/main` |
| Zero non-RuneLite dependencies | yes | `build.gradle`: `compileOnly client`, `lombok` (compileOnly + annotationProcessor, the template's own), test-only `junit`, `jshell` |
| No reflection / forbidden language features | yes | `NoReflectionTest` scans `src/main`; `QuestHelperBridge` removed |
| No agent scaffolding in the published tree | **NO — Board** | still tracked: `AGENTS.md`, `CLAUDE.md`, `docs/prompts/build-guard-standalone.md`. Removing them changes how agent runs on this repo are briefed, so it is not done in this PR |
| No third-party names in screenshots | **check — Board** | player names visible: `VinnyWanGogh` (`ge-collected.jpg`), `Ship Wrek` (`passing-a-shop.jpg`). Confirm both are your own accounts. Not reviewed: `ground-items`, `shop-open`, `tab-combat`, `tab-items`, `tab-quests`, `tab-skills` |
| Screenshots show the new name | **NO — Board** | old name "Quest Path" visible in `bank-tab.jpg`, `ge-buy-list.jpg`, `ge-collected.jpg`, `passing-a-shop.jpg`. Retaking needs a logged-in client, which only you can do |
| Config group and keys settled | yes | group `questroute`; Quest Helper keys removed so none freeze with no feature behind them |

## 3. Hub PR body (draft)

Write the final one from the submitted commit; edit this if anything moved.

---

**Quest Route** plans quests for accounts that cap a skill on purpose. It
answers "would this quest, or anything it depends on, push a skill past a cap I
am holding?", turns the quest items for a whole route into one shop run, and
warns before a build rule ends the account. Repo:
https://github.com/VinnyVanGogh/questpath-plugin — see the README for
screenshots.

**Why not a PR to an existing plugin.**
- *Quest Helper* is the best quest guide there is and this does not replace
  it: it walks you through one quest's steps. It has no notion of a skill you
  must not train, a route across many quests, or a shopping trip for all of
  them. Quest Route works the same with Quest Helper installed or not.
- *QuestPath* (ripperosa) is a quest prerequisite visualiser and planner. It
  plans by requirements; it does not check build rules, cost combat levels in
  XP, plan shop runs, or advise on lamps. The rename to Quest Route is so the
  two do not read as the same plugin.
- *Skiller Guard* warns when a quest gives combat XP from a hand-kept list.
  Quest Route derives the same answer from the quest graph for any capped
  skill, and does the planning around it.
Bolting route planning, a shop route and a build-rule engine onto any of these
would change what that plugin is.

**What it does.**
- Quest verdicts against your build rules, with the blocker chain on hover.
- One shop run for every item on your route, sent to Shortest Path; shops call
  out when you pass something you need.
- Bank tab, shop, ground and Grand Exchange markers for what the route needs.
- What the next combat level costs per stat in real XP; locked-gear max hits.
- Experience lamp advisor; achievement diary route.
- Optional Group tab over RuneLite's own party service.
- Runs entirely locally: setup asks account type and quest types, and the route
  comes from the bundled graph and the Java planner.

**Compliance.**
- *Network:* none. The plugin makes no HTTP requests and does not use OkHttp;
  the quest graph and planner are bundled. `StartUpThreadingTest` fails the
  build if `src/main` imports `okhttp3`.
- *Party:* "Share with my party" is off by default and uses `PartyService`
  only.
- *Grand Exchange:* the buy list writes the plugin's own sentinel into the
  search box and answers `GrandExchangeSearched` with item ids (the pattern
  Quest Helper's bank tab uses). No real search term is ever written into the
  chatbox input.
- *Plugins:* Shortest Path is reached with `PluginMessage` only. No
  reflection anywhere; `NoReflectionTest` scans the sources.
- *Not built, deliberately:* no input automation of any kind, no menu entries
  that send actions, no information about other players, no max hit against a
  target during a fight.

**Technical.** No dependencies beyond `runelite-client` (Lombok as in the
template). Java 11, `build=standard`, BSD-2-Clause. 69 test classes, 739
`@Test` methods, including the planner checked against all 218 verdicts
exported from the questpath server.

---

## 4. Quest Helper: how it comes back

Removed for this submission (reflection is an automatic rejection). It returns
over `PluginMessage` once **Zoinkwiz/quest-helper#2756** is merged and in the
Quest Helper release the Hub ships: namespace `questhelper`, name `start`,
data `{quest, source}`. The returning version posts that message instead of
reflecting, and only asks Quest Helper to start a quest — it cannot read the
current selection until Quest Helper also emits one. **This repo's PR #12**
(Recipe for Disaster sub-quest renames for Quest Helper v4.18.1; its tests fail
on main because it edits the removed `QuestHelperBridge`) is the input for the
quest-name mapping that version needs; the removed code is in git history at
`ee638ff`.

## 5. Server sync: how it comes back

Removed for the first Hub release (RUNELITE-499) so the plugin makes no network
calls and needs no third-party-server warning. The full sync code is kept on the
branch **`feature/server-sync`**, cut from `main` at `9b961f0`, the commit just
before the removal.

What was removed: the Sync tab (`PanelSections.SYNC`, `Tab.SYNC`, `syncBlock`),
the "Sync with Quest Route" button, the `syncEnabled`, `serverUrl` and
`pairingToken` config items and the "Quest Route sync" config section,
`AccountState`, `AccountStateSender`, `SyncStatus`, `SyncSettings`,
`AccountReport`, `QuestGraphFetcher`, `QuestpathPlugin.maybeSync` / `syncNow` /
`syncStatus` and the `VarbitChanged` subscriber that only drove it, and the
Quick settings "Server mode" row. `PushResponse` is kept as the data shape for
the diary and item-plan fields; nothing writes to the plugin fields that held
the pushed state (`buildRules`, `constraints`, `routeOrder`, `partialRuns`,
`knownItems`, `itemPlan`, `diaryPush`), so they stay empty.

To bring it back, once a public server exists:

1. Branch from current `main`, then restore from the saved branch, e.g.
   `git checkout feature/server-sync -- <files>` for the classes above, or
   `git merge feature/server-sync` and resolve against the newer `main`.
   `git diff 9b961f0 main -- src/main` shows what changed since the cut.
2. Re-add the three config items and the `syncSection` with
   `warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers"`
   on the toggle, off by default. Never rename `syncEnabled`, `serverUrl` or
   `pairingToken`: they were never published, but the saved key names match the
   branch.
3. Add `warning=` back to the Hub manifest (section 1) and the *Network* bullet
   back to the PR body; replace the `src/main` OkHttp check in
   `StartUpThreadingTest` (`mainCodeMakesNoHttpRequests`) with one that allows
   only the sync classes.
4. Point the default server address at the public server, keep push opt-in, and
   keep the plugin's two-minute experience re-push inside the server's
   five-minute freshness window (see `CLAUDE.md`).
5. Re-run `tools/tokcount.py`; the sync code was roughly 6,000 tokens of the
   Hub's 200,000-token main-code limit.
