# Build Guard: a standalone RuneLite plugin

A prompt for a fresh session in a new, empty repo. You have never seen
questpath. Everything you need is on disk at the paths below — read them, do
not work from this summary.

## Goal

A small Plugin Hub plugin that warns an Old School RuneScape player before a
quest, a lamp, or an activity puts experience into a skill they are
deliberately holding down. No network. No sync. No server.

The idea it exists for: most quest tools check whether you *meet* a quest's
requirements. This one checks whether the quest, or anything it transitively
depends on, would grant experience in a skill you are capping on purpose, or
would demand a level above a cap. Both sides. Checking rewards alone is the
bug — *Land of the Goblins* is not blocked by anything it grants, it is blocked
because a prerequisite **requires** Attack 15 and Prayer 25.

## Read these from disk, do not paraphrase

Absolute paths. Read the files; the doc comments carry the reasoning and the
reasoning is most of the value.

**The Go originals** (`~/Documents/dev/osrs`):

| What | Path |
|---|---|
| Build rule model (4 types, blocking, cap levels) | `internal/domain/types.go` |
| The 8 verdicts and their precedence | `internal/domain/planner.go` |
| Verdict engine: classification | `internal/planner/classify.go` |
| Verdict engine: the build guard | `internal/planner/guard.go` |
| Lamp advisor (the one to port) | `internal/planner/lampchoice.go` |
| Lamp types, states, block/risk reasons | `internal/domain/lamp.go` |
| Lamp catalogue (9 standalone lamps, sourced) | `internal/items/lamps.go` |
| XP curve | `internal/osrs/xp.go` |
| Combat level and the free Ranged/Magic ceiling | `internal/osrs/combat.go` |
| Verified external facts — **read §24 before any lamp work** | `RESEARCH.md` |

**The existing Java port** (`~/Documents/dev/questpath-plugin`). This is a
working plugin that already does all of it. Port from here first and consult
the Go only where the Java is unclear:

| What | Path (under `src/main/java/com/vinnyvangogh/questpath/`) |
|---|---|
| Build rule | `BuildRule.java` |
| Local rule storage and merge | `LocalBuildRules.java`, `BuildRuleCodec.java` |
| Sidebar rules editor | `BuildRulesEditor.java` |
| The 8 verdicts | `QuestStatus.java` |
| Verdict engine | `QuestClassifier.java` |
| Account state | `PlayerState.java` |
| Bundled quest graph loader | `QuestGraph.java` |
| Lamp advisor | `LampAdvisor.java`, `LampVerdict.java`, `Lamp.java`, `LampCatalogue.java` |
| Lamp interface widget ids | `LampSkillWidgets.java` |
| Which lamp is in play | `LampWatcher.java` |
| Lamp overlay | `LampOverlay.java` |
| XP curve, combat maths | `XpTable.java`, `CombatMath.java` |

**Bundled data** — copy these, do not regenerate:
- `src/main/resources/com/vinnyvangogh/questpath/quests.json` (218 quests)
- `src/main/resources/com/vinnyvangogh/questpath/lamps.json` (the lamp catalogue)

**Test vectors** — copy these too:
- `src/test/resources/com/vinnyvangogh/questpath/planner-vectors.json` — **218
  quest verdicts**
- `src/test/resources/com/vinnyvangogh/questpath/lamp-vectors.json` — the lamp
  advisor cases
- The tests that run them: `PlannerVectorsTest.java`, `LampVectorsTest.java`

**The backlog**, ranked, each with its rules check:
`~/Documents/dev/questpath-plugin/docs/TODO-build-guard.md`

**The Hub rules**: `~/Documents/dev/questpath-plugin/AGENTS.md`

## Scope in

1. **Build rules UI.** A sidebar editor: one rule per skill, four types —
   NO_XP, HARD_CAP, SOFT_CAP, GOAL. Stored per RuneScape account
   (`getRSProfileConfiguration`, never the plain profile pair — see
   `LocalBuildRules`' class doc for why). This is the whole input; with no
   rules the plugin is an ordinary quest calculator and must say so rather
   than looking like it checked.
2. **Per-quest verdict with the reason**, from the bundled graph. Name the
   prerequisite for an UNREACHABLE verdict; it is the most valuable output.
3. **A warning on starting a blocked or unreachable quest.**
4. **The lamp advisor**, ported: greying, the top-three boxes, the settings.
5. **Then the items in `TODO-build-guard.md`, in the order that file ranks
   them**: XP alarm, quest journal warnings, attack style warnings, lamp
   locking, lowest-skill activities, risky NPCs.

## Scope out

Shop runs. Achievement diaries. Route planning and ordering. Quest Helper and
Shortest Path integration of any kind. Website sync, pairing tokens, state
push. Locked gear and max-hit calculation. Themes. The bank tab, ground items,
the Grand Exchange bridge.

If you find yourself reaching for any of these, you are rebuilding questpath's
plugin rather than extracting the guard from it.

## Plugin Hub constraints

- **Java 11.** Nothing newer.
- **No reflection, no JNI or JNA, no subprocesses, no dynamic class loading,
  no runtime code generation, no Java serialisation.** These are automatic
  rejections. Reflection is the obvious way to read another plugin's state and
  there is no compliant version of it — the sanctioned channel is
  `net.runelite.client.events.PluginMessage`.
- **No network at all** is the target. The quest graph and lamp catalogue are
  bundled at build time. If anything ever does talk to a server, that config
  item must be off by default and carry the exact warning string:
  `"This feature submits your IP address to a 3rd-party server not controlled
  or verified by RuneLite developers"`.
- Config group name specific (`"build-guard"`, not `"guard"`), and **frozen**:
  renaming a group or a key silently resets every user's settings.
- BSD-2 licence. No committed build artifacts. No
  `META-INF/services/net.runelite.client.plugins.Plugin`.

Read `AGENTS.md` for the full list rather than trusting this summary.

## Rules check — required, before building each feature

For **every** in-game feature, before designing it:

1. Check it against Jagex's third-party client guidelines and RuneLite's
   "Rejected or Rolled Back Features" wiki page.
2. Write down **the specific line it lands nearest** and why it falls on the
   allowed side — not "it seems fine".
3. Find **a precedent**: something in the base client or a merged Hub plugin
   that already does this. A feature with no clean rule and no precedent is
   not a feature yet.

Two precedents already established and worth reusing:
- The lamp overlay's nearest rule is *"No unhiding hidden interface
  components"* and its precedent is the base client's **Attack Styles** plugin
  (`net.runelite.client.plugins.attackstyles`), which takes per-skill user
  config, warns, and with `removeWarnedStyles` hides widgets outright.
- Lamp locking's nearest rule is *"No conditional menu entry removal based on
  NPC type, friend status, etc."* and its precedent is **Jam's Skiller Guard**,
  which is on the Hub (the hub repo pins `plugins/skiller-guard`) and is
  BSD 2-Clause.

## Test bar

**The 218 ported quest verdicts and every lamp vector must pass unchanged.**

A failing vector is a porting bug. It is never a vector to edit. Both
implementations answer confidently, so a disagreement is invisible until the
game and the tool tell the player different things about the same
irreversible decision — which is the entire reason the files exist. If you
cannot make a vector pass, stop and say which one and why.

`./gradlew build` is the gate you can run.

## Ask Vince before submitting

**How does Hub review treat two plugins by the same author that share logic?**

questpath-plugin is already on its way to the Hub and carries this same verdict
engine and lamp advisor. This plugin would be a second submission with
substantially overlapping code from the same author. That could read as
duplication to be rejected, as a reasonable split between a companion tool and
a standalone one, or as something needing the overlap declared in the PR. It
changes whether this should be submitted at all, so it is worth asking before
the work is finished rather than after.

## Standing rules

- **No fabricated data.** No invented lamp XP amounts, widget IDs, region IDs,
  or XP rates. A value you don't have stays unset, and the UI says "not
  modelled". It must look different from "empty".
- **No game formula from memory.** Lamp XP amounts, lamp level requirements and
  skill restrictions come from the OSRS wiki. Record each in the repo's
  research file using the existing research-template shape: URL, revision id,
  exact procedure, worked vectors, and what the source does not settle.
- **XP comes from the real XP table** already in the codebase. Find it; don't
  write a new one.
- **Build rules are about XP, not levels.** Never suggest anything that
  loosens a rule.
- **Tests:** the Gradle test task must pass before you report a task done. Say
  which tests you ran.
- **Plugin rules:** check every in-game feature against Jagex's third-party
  client guidelines and RuneLite's "Rejected or Rolled Back Features" wiki page
  before building it. Quote the relevant line in your report.
- **Commits:** one per task minimum. Messages explain why, not what.

## One thing that is not negotiable

You cannot verify in-game behaviour and must never automate game input — it
violates Jagex's third-party client guidelines and risks the account. A clean
`./gradlew run` is not a passing test. When a feature is built, hand Vince
something specific to look at and wait.
