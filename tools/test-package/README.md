# Friend-ready Windows test zip

One template and one command that package any of our RuneLite plugins for a
friend to try on Windows.

```sh
bash tools/test-package/build-test-package.sh                 # this plugin
bash tools/test-package/build-test-package.sh ~/path/to/other-plugin
bash tools/test-package/build-test-package.sh --jar some-all.jar   # skip gradle
```

Output: `build/test-package/<slug>-test-<yyyymmdd>.zip` containing the fat jar,
`START.bat` and `README.txt` (CRLF). Name, description and build id (git sha,
`-dirty` if tracked files are modified) come from `runelite-plugin.properties`
and git.

It works for every plugin from the `runelite-plugin-dev` template, because they
all define `shadowJar` with the developer-client launcher as `Main-Class`. The
friend needs Java 11+ only; `START.bat` checks and says so.

- `template/START.bat.tmpl`, `template/README.txt.tmpl`: the only per-package
  text. Placeholders: `__DISPLAY_NAME__`, `__DESCRIPTION__`, `__BUILD_ID__`,
  `__JAR_NAME__`.
- `test.sh`: tests the packaging with a fake plugin and jar (no gradle).

Not verified here: the real `shadowJar` build and `START.bat` on a Windows
machine. Run the script once for real and have the friend (or a Windows box)
start it before relying on it. The script only builds a file; it never starts
RuneLite or touches the game.

Possible move: this is toolchain, so it may belong in `runelite-plugin-dev`
(`scripts/` + `template/`); it is written to run from any directory.
