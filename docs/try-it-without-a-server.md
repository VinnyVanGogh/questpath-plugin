# Try Quest Route with no server

For a friend who just wants to see it work. No website, no database, no
account anywhere.

1. Build and start a developer client from this repo: `./gradlew run`
   (log in with the Jagex account steps at
   https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts).
2. Open the **Quest Route** sidebar icon and go to the **Quests** tab.
3. First time on an account you get **Set up Quest Route**. Pick the account
   type and the quest types, then **Save and show my route**.
4. The route appears. Quests your build or levels forbid are left off.
5. Settings are in RuneLite's plugin configuration for Quest Route.

Where it keeps things: RuneLite's own settings, one saved value per account
(your account type, quest types, progress, route). No files are written. Tick
**Reset account setup** in the plugin settings (it asks first) to start over.

The plugin makes no network requests of its own: the server sync was taken out
for the Plugin Hub launch (see `docs/hub-submission.md` for how it comes back).

## Known limits at this launch

- Setup runs once. To change it, use **Reset account setup**.
- The route is prerequisites-first, then alphabetical. It does not optimise
  for time or cost.
- Account types are recorded but do not yet change the route.
