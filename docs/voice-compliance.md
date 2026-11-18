# Quest Route: voice for shop stops and floor items

Board, 2026-11-14. Quest Route speaks: walking up to a shop it says every
still-needed item that shop sells ("Looking for 30 air runes and an air
talisman at Aubury's Rune Shop"); a needed item on the floor nearby gets a
bouncing arrow and is said once ("Bucket nearby").

## Phase 0: compliance verdict

Checked against `AGENTS.md` (RuneLite's Hub rules and the Rejected or
Rolled-Back Features list it reproduces) before any code.

| Feature | Rule it lands near | Verdict | Precedent |
|---|---|---|---|
| Spoken shop reminder on approach | None of the forbidden features: no input, no menu change, no network, no boss/PvP information. Audio alerts are not on the rejected list. | Allowed | RuneLite's own `Notifier` plays alert sounds; Combat Vitals (our plugin) speaks bundled Kokoro clips through the same `javax.sound.sampled` path |
| "Bucket nearby" for a needed floor item | Not a PvP or boss feature; says what the client already shows (ground items). No crowdsourcing, no other players. | Allowed | Ground Items (core) highlights and notifies on chosen drops, including a sound notification |
| Bouncing arrow over a needed floor item | "No automatic indicators showing where to stand" applies to bosses and wave minigames only. An item highlight is not that. | Allowed | Quest Helper draws arrows and highlights over quest items and objects; Ground Items (core) highlights items |
| Not using `client.setHintArrow` | Not a rule: the client has one hint arrow and other plugins take it. Drawn in our own overlay. | n/a | |
| Bundled WAV clips, `getResourceAsStream` | Forbidden language features: no reflection, JNI, processes, dynamic code, network. Clips are data in the jar; playback is `javax.sound.sampled`. | Allowed | Combat Vitals `VoiceLines`/`AlertSounds` |

Not done here: checking that a Hub reviewer accepts roughly 1,000 bundled
clips. The jar size is in the PR body.

Precedents named from memory, not re-checked in this run: Ground Items'
sound notification and Quest Helper's item arrows.

## What ships

- `VoiceLines` builds a line from `voice/lines.tsv`, `VoicePlayer` plays it
  (one at a time; a newer line replaces the queued one), `Speaker` falls back
  to a ping plus the words in chat when a clip is missing.
- `FloorItemAnnouncer`: said once per spawn, nearest first, at most one line
  every 4 s.
- `BouncingArrow`, drawn by `GroundItemOverlay` within the floor-item range.
- Config (new keys, frozen from first publish): `voiceEnabled` (on),
  `voiceVolume` (70), `voiceShops` (on), `voiceFloorItems` (on),
  `floorItemArrow` (on), `floorItemRadius` (10).
- "Say again: <shop>" on the Items tab's current shop stop.
- `tools/voice/`: `build_lines.py` (word list from `quests.json`) and
  `generate_voice.py` (Kokoro af_heart through mlx-audio).
