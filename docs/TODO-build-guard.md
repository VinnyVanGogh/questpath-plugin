# Build Guard backlog

The lamp advisor covers one interface. This is everything else that can put
experience into a skill the account is deliberately holding down, in the order
worth building.

Everything here derives from the account's own build rules — the ones synced
from questpath and the ones written in the sidebar, merged by
`QuestpathPlugin.effectiveBuildRules()`. That is the whole difference between
this and the plugins that already exist: they protect a fixed idea of a level-3
skiller, and this protects whatever the player actually declared. A Prayer
tome on an account capping Prayer at 31 is a problem; on an account with no
Prayer rule it is a reward.

**Prior art, checked and not assumed.** Jam's Skiller Guard
(<https://github.com/JamsRepos/skiller-guard>) already ships five of the six
items below, and **it is on the Plugin Hub** — the hub repo carries
`plugins/skiller-guard` pinning commit `338de5f`. That matters twice: it is
the strongest available evidence that this category of feature passes review,
and it means questpath must not duplicate it for its own sake. Its licence is
**BSD 2-Clause** (`LICENSE`, "Copyright (c) 2026, JamsRepos"), which is
compatible with this plugin's own and with `AGENTS.md`'s "retain a permissive
license, such as BSD-2" — so borrowing code is legally fine with attribution.
Borrowing its *catalogues* is the thing to be careful about, because they
encode its build assumption, not ours.

## Order, and why

Ranked by value over effort, with one deliberate demotion.

1. **XP alarm** — near-zero effort, and it is the only item that catches a
   mistake nothing else anticipated.
2. **Quest journal warnings** — the verdict and its reason already exist; this
   is display.
3. **Attack style warnings** — high value on a pure, and the base client has
   already shipped the shape.
4. **Lamp locking** — moderate effort, and the highest review risk in the file,
   which is why it sits below three things that carry none.
5. **Lowest-skill activities (Tears of Guthix)** — narrow, but it is the one
   hazard a rules engine cannot see coming, because the dangerous skill is
   chosen by the game rather than by the player.
6. **Risky NPCs and objects** — open-ended investigation with no bounded
   deliverable yet. Last because "list everything that forces XP" is not a
   task, it is a research programme.

---

## 1. XP alarm

**What it does.** A loud, unmissable alert the moment a skill carrying a NO_XP
rule gains any experience at all. On-screen banner, chat line, sound. It never
prevents anything — by the time it fires the experience is already gained —
and that is the point: it is the alarm on the door, not the lock.

**Reuses.** `QuestpathPlugin.effectiveBuildRules()` for which skills are
NO_XP, a `StatChanged` subscriber for the experience itself, and
`OverlayTheme` plus the existing `QuestpathOverlay` panel machinery for the
banner. RuneLite's `Notification` config type gives the sound and desktop
notification without new code.

**Rules check.** No rule in the forbidden list is near this: it reads the
player's own experience and draws text. The closest thing in
`AGENTS.md` is under Data & Privacy — *"No exposing player information over
HTTP"* — and this exposes nothing anywhere. Precedent: Skiller Guard's
"Combat XP alarm ... Red breach banner + chat + sound if combat/Prayer XP is
gained", shipped on the Hub.

**Open questions.**
- Does the alarm apply to HARD_CAP too, or only NO_XP? A hard cap crossing is
  equally permanent, but it fires on a level change rather than on any XP, so
  the trigger is different and the two may want different wording.
- One alarm per session, per skill, or per event? An alarm that fires every
  tick while a skill is training is an alarm nobody reads.
- What does it say for a SOFT_CAP skill — or does it stay silent, since the
  player asked for a warning, not an emergency?

**Facts to verify.** None. This is entirely about the account's own state.

---

## 2. Quest journal warnings

**What it does.** When a quest journal or a quest-list entry names a quest
this account cannot do, say so in the interface, with the reason. BUILD_BLOCKED
and UNREACHABLE are the two that matter, and the reason chain already
distinguishes them: one quest breaks a rule itself, the other is poisoned by a
prerequisite, and the second is worthless without naming *which* prerequisite.

This covers every rule, not just combat — a quest forcing Prayer experience on
an account capping Prayer is the same problem as one forcing Attack.

**Reuses.** `QuestClassifier`, `QuestVerdicts`, `QuestBlockers` — all of it
already computed and already rendered in the sidebar. `QuestJournalFilter`
already reaches into the quest list interface, so the attachment point exists.
This item is display, not logic.

**Rules check.** Drawing over an already-visible interface. The nearest rule is
Interface Restrictions — *"No unhiding hidden interface components (special
attack bar, minimap)"* — and nothing here unhides anything. Precedent:
Skiller Guard's "Quest warnings ... Warns when you open a combat-XP quest
journal, start that quest, or open it in Quest Helper". Note that its warning
list is a hand-maintained `QuestDenylist`; ours is derived, which is strictly
more work at build time and strictly less to maintain.

**Open questions.**
- Warn on opening the journal, on clicking start, or both? Both is what
  Skiller Guard does, and two warnings for one action may be one too many.
- The quest list has 200+ entries. Colouring every blocked one at once may be
  more useful than a warning, or may be visual noise — this probably wants the
  same "suggest / warn / off" three-way the lamp advisor has.
- Does it hook Quest Helper's own start flow? Not by reflection: the old
  `QuestHelperBridge` did, which is an automatic rejection, and it was removed
  for the first Hub submission. Any Quest Helper integration goes through
  `PluginMessage` or does not happen.

**Facts to verify.** None from the wiki; the quest graph already carries the
requirements and rewards.

---

## 3. Attack style warnings from build rules

**What it does.** Warn on, or hide, a combat style that trains a blocked or
capped skill. Derived from build rules rather than from manual checkboxes: an
account capping Strength at 82 wants Aggressive warned and Accurate left
alone, and it should not have to say so twice.

Magic is included and is the harder half — casting mode, not weapon style,
decides which skill gets the experience.

**Reuses.** `effectiveBuildRules()`, and `AttackStyle.java` and
`MagicPrayer`/`RangedStyle`/`StrengthPrayer` already exist for the max-hit
work, so the style-to-skill mapping is partly present.

**Overlap with the base client, which is the thing to be honest about.**
RuneLite ships **Attack Styles**
(`net.runelite.client.plugins.attackstyles`) and it already does most of
this: `AttackStylesConfig` exposes `warnForAttack`, `warnForStrength`,
`warnForDefence`, `warnForRanged`, `warnForMagic`, and `removeWarnedStyles`
actually hides the offending widgets. **What this adds is only the
derivation** — the core plugin needs five checkboxes ticked by hand and kept
in step with the build by memory, and this would set them from the rules the
account already declared. That is a real but narrow gain, and it is worth
deciding whether it justifies a second warning system on the same interface
rather than, say, a chat line saying "your build implies these Attack Styles
settings" once.

**Rules check.** Warning is plainly allowed. *Hiding* styles is allowed too,
by the strongest precedent available — the base client does it under
`removeWarnedStyles`. The nearest forbidden line is Interface Restrictions'
*"No moving or resizing click zones for combat options, inventory, equipment,
or spellbook"*: hiding a style widget is not moving or resizing a click zone,
but anything that starts repositioning the remaining styles to fill the gap
would be, and must not be built.

**Facts to verify from the wiki.**
- Which casting modes grant which experience, and in what ratio. Standard
  combat spells grant Magic and Hitpoints; autocast on a staff differs from
  manual cast; "Defensive autocast" grants Defence. **None of this is settled
  here and none of it may be written from memory** — read `Magic`,
  `Combat Options` and `Autocast` and record the result in questpath's
  `RESEARCH.md` in the §24 shape, with revision ids.
- Whether splashing grants Magic experience with no damage dealt (it does not
  grant Hitpoints, which is exactly why it matters to a capped build).
- Which styles on which weapon types are Controlled — the shared-experience
  ones are the traps, because they train a skill the player did not select.

---

## 4. Lamp locking

**What it does.** Goes beyond the advisor's greying and consumes the click on a
blocked skill, so a misclick on a NO_XP skill does nothing at all. The greying
is advice; this is a lock.

**Reuses.** Everything from the advisor: `LampAdvisor`, `LampVerdict`,
`LampSkillWidgets`, `LampWatcher`, and the same `effectiveBuildRules()`. The
only new part is the interception.

**Prior art.** Skiller Guard's "Lock combat skills on lamps — Hides those seven
skills on the lamp interface", plus its layered approach, which is worth
copying wholesale as a *design*: "Guard is fail-closed and layered: hide risky
menu options on `PostMenuSort` and `MenuOpened`; **consume the click if a
hidden option still fires**". The second layer exists because the first one
can miss, and a guard that assumes its own first layer worked is a guard that
fails silently.

**Licence.** BSD 2-Clause, so code may be reused with the copyright notice
retained. Its *catalogue* (`SafetyIds`, `NamedNpcCatalog`, `QuestDenylist`)
should not be: those encode "level-3 skiller" as the build, and copying them
would quietly reintroduce the fixed assumption this plugin exists to replace.

**Rules check — the riskiest item here, and the reason it is fourth.** The
nearest rule is Menu Restrictions: *"No conditional menu entry removal based on
NPC type, friend status, etc. (can be overpowered)"*. The lamp interface is
neither an NPC nor a player, and the condition is the account's own declared
rule rather than anything about another entity, so this is very likely on the
allowed side — and Skiller Guard doing exactly this while sitting on the Hub is
the evidence. It is still the item most likely to draw a review question, so
it should ship after the three above have established the plugin, and the PR
should cite Skiller Guard by name.

**Open questions.**
- Consume the click, or consume it *and* say why? A click that does nothing
  with no explanation reads as a broken plugin.
- Does locking apply to RISKY squares, or only BLOCKED? Locking a soft cap
  would be overriding a warning the player explicitly asked to be able to
  ignore.
- An escape hatch. If a player genuinely wants to spend a lamp against their
  own rule, is that a config toggle, a shift-click, or not supported? "Not
  supported" is defensible and is one more reason a reviewer might ask.

**Facts to verify.** None new; RESEARCH.md §24.6 already has the interface's
component ids.

---

## 5. Lowest-skill activities (Tears of Guthix)

**What it does.** Warns before an activity that awards experience to whichever
skill is *lowest* — which on a capped build is, by construction, a blocked one.
This is the one hazard the rules engine cannot anticipate, because the player
never chooses the skill.

**Built generically, not as one hardcoded place.** The check is "this activity
awards experience to the account's lowest skill; that skill is
`<name>`; a rule closes it" — so a second activity with the same mechanic is a
row of data, not a new feature.

**Reuses.** `effectiveBuildRules()` and the client's own experience values to
find the lowest skill. `DiaryRouteOverlay` and `ShopApproachWatcher` are the
existing patterns for "warn on approaching a place", and
`FloatingNoteOverlay` for saying it where it happens.

**Rules check.** Warning about a minigame's own documented reward is not near
any forbidden line. The Boss & Combat restrictions do not apply — Tears of
Guthix is not a boss and has no mechanic to telegraph. Precedent: Skiller
Guard warns on Juna by name.

**Facts verified so far** (wiki, fetched 2026-09-17):
- Tears of Guthix minigame, rev **15233614**: *"the player will be rewarded
  with experience points in their lowest skill"*, located in the Chasm of
  Tears. The article's own map template gives `x=3257 y=9517`.
- Juna, rev **15341972**: NPC id **5785**, located in the Chasm of Tears.

**Facts still to verify.**
- The **region id** for the Chasm of Tears. The coordinates above are a map
  tile, not a region, and the region must be derived or read rather than
  guessed.
- Whether "lowest skill" means lowest **level** or lowest **experience**, and
  how ties are broken. This decides which skill the warning names, and naming
  the wrong one is worse than naming none.
- Whether Hitpoints and other skills at their starting level are eligible at
  all.
- Whether any other activity shares the mechanic — the Lamp of the Gatherer
  and several diary rewards are fixed-skill, so they are not it, but the list
  has not been searched properly.

---

## 6. Risky NPCs and objects

**What it does.** Everything else in the world that forces experience without
asking: bone altars, dummies, a conversation that hands over a reward.

**Status: investigation, not a feature.** There is no bounded deliverable yet,
which is why it is last.

**What is already known.** Skiller Guard's own README is the best available
index, and it is a starting list rather than an answer:
- Prayer: burying and scattering remains, altars (house, Chaos, Forthos bone
  burner, Varlamore, Camdozaal, Woodcutting Guild shrine), Pyramid Plunder
  sarcophagi.
- Combat: public and house dummies, the dwarf cannon, the Blast Furnace pump,
  barbarian Use-rod, barehanded Harpoon, house lectern Study and tablet
  buttons, the house ranging game.
- Magic XP traps: charging Kharedst's memoirs and the Book of the dead at the
  Old Memorial — *"10 Magic XP per charge"*.
- Conversations: Juna, Tamayu, Otto Godblessed, Elnock, Father Aereck, and the
  Varrock Museum information clerk (*"151 kudos includes Prayer XP"*).

**Reuses.** The same menu-inspection layer item 4 builds, plus
`GroundItemOverlay`'s existing pattern for marking things in the world.

**Rules check — UNCHECKED.** Deliberately. Menu Restrictions' *"No conditional
menu entry removal based on NPC type, friend status, etc."* lands directly on
the NPC half of this, and Skiller Guard's own Plugin Hub notes say its
"NPC-type menu hiding is opt-in and off by default" with "no freeform hide
box" — which reads like a constraint it was given rather than one it chose.
That distinction has to be established before anything here is designed, not
after.

**Facts to verify.** All of them. Every entry above is another project's
catalogue, not a verified fact, and each one needs its own wiki read with an
item/NPC/object id and a revision recorded in questpath's `RESEARCH.md`. Do
not copy the list across and call it sourced — that is exactly the mistake the
teleport curation made, and it took an afternoon to undo.
