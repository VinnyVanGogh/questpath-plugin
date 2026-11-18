# Voice clips

Quest Route speaks shop reminders ("Looking for 30 air runes and an air
talisman at Aubury's Rune Shop") and floor items ("Bucket nearby") by joining
short pre-rendered clips. Nothing is synthesised at runtime and nothing goes
over the network: the plugin only plays the WAVs bundled under
`src/main/resources/com/vinnyvangogh/questpath/voice/`.

Two steps, both offline, both developer-only (neither script ships in the jar):

1. `python3 tools/voice/build_lines.py` writes `voice/lines.tsv` from
   `quests.json`: the fixed phrases, numbers 1-100, every item a quest line can
   name, a plural for every item a shop sells, and every shop. Re-run it
   whenever `quests.json` changes. `VoiceLinesCoverageTest` fails when the
   bundle names an item or shop the list has no row for.
2. `python tools/voice/generate_voice.py` renders every clip not yet on disk,
   Kokoro `af_heart` (the same voice as Combat Vitals) through mlx-audio.
   Setup is in the script's docstring. Once any clip is bundled,
   `VoiceLinesCoverageTest` also fails for every row without its WAV.

A missing clip never means silence: the plugin plays a short ping and puts the
line in chat instead.

## Size

The list is large: about 470 item names, 200 plurals and 314 shop names.
Measure the real total from the script's last line after a render. The
16-bit PCM default at 22.05 kHz costs 44 KB per second of speech; `--ulaw`
halves that and `--rate 16000` takes off another 27%.
