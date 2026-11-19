# Scenarios and Paste JSON (dev tooling)

A "Scenarios (dev)" sidebar for the developer client (`./gradlew run`). It posts
made-up game events as the game would, on the client thread, so Quest Route can
be exercised without playing. **It lives in `src/test`, so it is never in the
Plugin Hub build.** It sends no game input and nothing reaches Jagex: an event
is a Java object handed to subscribers.

## Who receives an event

The **Send to** picker in the sidebar:

- **Quest Route only** (default): the event goes to a private `EventBus` with
  only Quest Route registered, so no other plugin in the client sees it.
- **All plugins**: the client's real bus, for cross-plugin tests.

Outputs are real. Quest Route's own push to questpath is opt-in and off by
default; leave it off, or point it at a local instance. **Dry run outputs**
(off by default) logs what a `DryRun.shared().wrap(...)`-wired output would have
sent instead of sending it. Quest Route has no webhook output today, so nothing
is wired to it yet.

## The sidebar

- **Scenarios** tab: pick a built-in (`src/test/resources/scenarios/*.json`) or
  a saved one. *Run* plays it with its waits, *Step* fires one event at a time,
  *Reset* starts over, *Reload* re-reads the files.
- **Paste JSON** tab: *Send* fires it now (waits ignored), *Save* keeps it as a
  named scenario, *Kinds* prints every kind's fields with a working example.

Errors name the step and the fix, e.g. `step 2 (stat): missing field 'level'`.
Nothing is posted unless the whole payload is valid.

## Kinds

`chat`, `stat`, `container`, `varbit`, `varp`, `widgetLoaded`, `widgetClosed`,
`userPart`; `tick` and `gameState` are headless-only and skipped live. The
authoritative list is `CoreKinds` and the *Kinds* button; a test parses every
documented example. A numeric field also accepts a registered constant name
(`"id": "INVENTORY"`, `"SHARK": 10`).

Not fakeable: `ActorDeath`, `NpcLootReceived` (need a live `Actor`/`NPC`).

## Saved scenarios

*Save* validates, then writes `<name>.json` (`a-z 0-9 - _` only) to
`.runelite/plugin-data/quest-route-scenarios/scenarios/` through the Filepath
API. Copy one into `src/test/resources/scenarios/` to make it a built-in.

Use `GIM1`, `GIM2`... as placeholder names, never a real account name.

## Limits

The private bus carries only what Quest Route's own `@Subscribe` methods see.
Objects it registers on the client's bus itself (overlays, helpers) are not on
it. Quest Route has no plugin-specific kinds yet; add one with
`EventKinds.add` and pass the kinds to `ScenariosPlugin.use` in
`QuestpathPluginTest`.
