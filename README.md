# questpath

A RuneLite plugin for accounts that are deliberately capping a skill.

Most quest tools check whether you *meet* a quest's requirements. This one is
being built to also check whether the quest, or anything it depends on, would
push a skill past a cap you are holding on purpose — the question a pure or a
restricted account actually has.

> **Status: v1, in progress.** The panel answers the combat question today. The
> quest planner follows.

## What it does now

A sidebar panel showing, from your real levels:

- **Combat level**, and how far through it you are.
- **The free Ranged / Magic ceiling** — the highest level those skills can reach
  without raising your combat level, given your Attack and Strength. It drifts
  upward as Strength grows, so a hardcoded number leaves levels on the table.
- **What the next combat level costs**, in every stat that could buy it,
  cheapest first. For a pure this is the question behind every training
  decision: one Strength level is worth having, one Defence level may be the
  end of the build.

No network requests. No configuration. Nothing leaves your client.

## What is coming

- Bank-aware quest item checklists — the client can see your bank, which is the
  one thing no external quest tool can.
- Quest verdicts against your own build rules: eligible, skill-blocked, or
  permanently off the table because a prerequisite grants XP you refuse.
- An opt-in push of your account state to a self-hosted
  [questpath](https://github.com/VinnyVanGogh) instance, replacing its WikiSync
  polling. Off by default, and the plugin is fully useful without it.

## Building

```bash
./gradlew build          # compile and test
./gradlew run            # launch a developer client with the plugin loaded
```

The build targets Java 11 bytecode. To log in to the development client, follow
[Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts).

## Licence

BSD 2-Clause. See [LICENSE](LICENSE).
