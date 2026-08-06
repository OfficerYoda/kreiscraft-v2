# Wiring History

These files were the wiring specifications produced by Plan 3's per-module
subagents. Each file describes what its module contributed to `config.yml`,
`lang/de.yml`, `lang/en.yml`, `plugin.yml`, and `KreiscraftBootstrap.java`
during Plan 3's Batch C.

**They are historical.** Plan 3's Batch C wiring subagent already applied
every section to the shared files. Do not re-apply them.

`src/test/java/de/kreiscraft/wiring/WiringAuditTest` asserts, on every build,
that the shared files still contain what these files describe. If you change
a module's config, lang, permission, or bootstrap wiring, update the
corresponding file in this directory to match, or the audit test will fail.
