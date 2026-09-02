@AGENTS.md

# questpath plugin — project instructions

A RuneLite plugin for accounts deliberately capping a skill: not "do I meet
this quest's requirements" but "would this quest, or anything it depends on,
push a skill past a cap I am holding on purpose".

`AGENTS.md` above is RuneLite's own rules for hub plugins. It is authoritative
on logging, threading, HTTP, config naming, packaging, and the list of features
that get a plugin rejected. Everything below is specific to this one.

## Read order

1. `AGENTS.md` — the rules.
2. `README.md` — what ships now versus what is coming.
3. **`docs/questpath-plugin-plan.md` in the questpath Go repo** — the v1/v2
   plan, the deliverable list, and the open decisions. It spans both repos, so
   it lives in one of them rather than being copied into both. Reach it with
   `claude --add-dir ~/Documents/dev/osrs`.

## The relationship to the Go app

questpath is a Go web app that ingests the OSRS wiki, holds the quest graph,
and runs the planner. This plugin is not its remote control:

- **The plugin must be useful with the Go app not running.** That is what keeps
  it a plugin rather than a client, and it is what makes it publishable. The
  combat panel needs no server; the quest planner will run off a bundled quest
  graph, not a live query.
- The state push to questpath is **opt-in and off by default**, and it will
  carry the same payload shape WikiSync uses so the Go side reuses its existing
  apply path rather than growing a parallel one.

## Two implementations of the same rules

`CombatMath` is a port of `internal/osrs/combat.go`. The planner port will be a
much larger one. Two implementations in two languages will drift, and the drift
is silent — both sides render confidently.

- `CombatMathTest` is pinned to the same vectors the Go test asserts. Keep it
  that way.
- When the planner is ported, its correctness gate is **generated golden
  vectors** from the Go side run as JUnit tests, not hand-copied numbers. Do not
  start the port without them.

## State of the code

- `CombatMath` / `CombatStats` — the combat formula. Pure, no client
  dependency, fully tested.
- `QuestpathPanel` — the sidebar. Rebuilt wholesale on each update, which is
  cheap at this size; revisit if it grows a quest table.
- `QuestpathPlugin` — nav button, `StatChanged` and `GameStateChanged`.
- **No config class.** The first real setting is the state-push URL, which
  arrives with the feature that needs it. Config group must be `questpath`.
- The nav-button icon is drawn in code, not loaded from a resource. It is a
  placeholder; a real `icon.png` (root, max 48x72) replaces it.

## Verifying

`./gradlew build` is the gate you can run. `./gradlew run` launches a developer
client — but per `AGENTS.md` you **cannot verify in-game behaviour yourself and
must not try**. Hand the user something specific to look at and wait. A clean
JVM start is not a passing test.
