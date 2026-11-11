# WorstGen Items (Fabric 26.3, server-side)
Adds `/items` (alias `/wgitems`, requires gamemaster/op) which opens a click-to-take GUI of all WorstGenR resource pack items.
Clients do NOT need this mod, only the resource pack.

- `/items reload` re-reads `config/worstgen_items.json` (if that file exists) instead of the list bundled in the jar.
- Build: `./gradlew build` (JDK 25), jar is in `build/libs/worstgen-items-1.0.0.jar` (not the `-sources` one).
