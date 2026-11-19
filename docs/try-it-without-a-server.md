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

Leave the **questpath address** setting empty. With nothing there the plugin
makes no network requests and says nothing in chat. The website is an optional
extra: put its address in that setting and turn on sync if you run it.

## Known limits at this launch

- Setup runs once. To change it, use **Reset account setup**.
- The route is prerequisites-first, then alphabetical. It does not optimise
  for time or cost; questpath's own planner does, if you sync with it.
- Account types are recorded but do not yet change the route.
