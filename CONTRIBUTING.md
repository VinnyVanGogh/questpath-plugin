# Contributing to questpath-plugin

## Prerequisites

- **JDK 11 or later.** The Gradle build compiles against `--release 11` as required by the RuneLite Plugin Hub.
- A local checkout of the repository. No running `questpath` server is required to build, test, or run the plugin; the quest graph and lamp catalogue are bundled under `src/main/resources/com/vinnyvangogh/questpath/`.

## Build and test

```bash
./gradlew build          # compile and run the JUnit test suite
./gradlew test           # run unit and vector tests only
./gradlew run            # launch a RuneLite developer client with the plugin loaded
```

Dev builds resolve `runeLiteVersion` to `latest.release` (re-checked against `repo.runelite.net` on every build) and print the resolved client version before `test` and `run`. To pin a specific RuneLite version for one invocation, pass `-PruneLiteVersion=<version>` (for example, `./gradlew build -PruneLiteVersion=1.13.6`).

To log in to the development client with a Jagex Account, follow the RuneLite wiki guide: [Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts).

## Architecture and invariants

### Golden test vectors (`PlannerVectorsTest`, `LampVectorsTest`, `CombatMathTest`)

The quest classifier (`QuestClassifier`), lamp advisor (`LampAdvisor`), and combat formulas (`CombatMath`, `XpTable`) are Java ports of the Go implementations in the companion `questpath` repository. Because both sides render answers without cross-checking at runtime, parity is enforced by generated golden vectors in `src/test/resources/com/vinnyvangogh/questpath/`:

- `planner-vectors.json` — all 218 quest verdicts and blockers.
- `lamp-vectors.json` — lamp advisor rankings and block/risk reasons across rule sets.

When a vector test fails, treat it as a bug in the Java port. Never hand-edit or re-export the vector files to make a failing test pass.

### Config keys and proxy accessibility (`QuestpathConfigProxyTest`)

RuneLite implements `@ConfigGroup` interfaces at runtime using a JDK dynamic proxy in the `jdk.proxy2` module.

- **Public return types:** Every enum or class returned by a `@ConfigItem` method in `QuestpathConfig` must be `public`. A package-private return type compiles cleanly but throws `IllegalAccessError` on startup. `QuestpathConfigProxyTest` enforces this structurally.
- **Sidebar parity:** Every user-facing `@ConfigItem` must also be wired into `OverlaySettingsPanel` unless explicitly exempted in `OverlaySettingsPanelConfigParityTest` (such as the hidden developer toggle `fetchQuestGraph`).
- **Frozen keys:** Do not rename the config group (`"questroute"`, migrated once from pre-rename `"questpath"` by `ConfigGroupMigration` in `provideConfig`) or existing `keyName` values without a migration, as doing so silently resets saved user settings and per-account build rules.

### Threading and performance

- **Client thread vs. Swing EDT:** Never call `Client` methods (such as `getLocalPlayer()`, varbit reads, or script execution) on the Swing Event Dispatch Thread. Cache state on the client thread during `GameTick` or dispatch mutations via `ClientThread.invokeLater`.
- **Network I/O:** The plugin makes no HTTP calls (the server sync lives on the `feature/server-sync` branch). `StartUpThreadingTest` fails the build if `src/main` imports OkHttp.

### Plugin Hub compliance

All changes must adhere to Jagex's Third-Party Client Guidelines and the RuneLite Plugin Hub rules documented in [`AGENTS.md`](AGENTS.md):

- Java 11 source and target compatibility; no reflection, JNI/JNA, subprocess execution, or serialization.
- Use `net.runelite.api.gameval` constants (`ItemID`, `InterfaceID`, `ObjectID`, `SpriteID`) rather than raw numeric IDs.
- Any future third-party server feature must be disabled by default and carry the required RuneLite IP warning string.
- Never automate game input or interact with the live game client programmatically; in-game behaviour must be verified manually by a player in the developer client.
