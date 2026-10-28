# Agent instructions

Read `docs/HANDOFF.md`, `docs/ARCHITECTURE.md`, and `docs/DEVELOPMENT.md` before editing.
For device work also read `docs/INSTALL.md`, `docs/DEBUGGING.md`, `docs/ACCEPTANCE.md`, and the selected device note.

## Working rules
- Use English for code, identifiers and technical documentation; README is Chinese.
- Keep behavior in one implementation; scripts must not duplicate configuration rules.
- Default to local-only validation. Device commands are separate and require explicit current-session authorization. Honor a user's no-device constraint even for discovery.
- Respect existing authorization; do not repeatedly request approval for already authorized operations. Reboot, sleep/wake and destructive actions require authorization covering those actions.
- Never disable/uninstall system packages, alter other apps' data, or overwrite other accessibility/notification bindings.
- Record original settings and owned artifact IDs under ignored `local/` before changing them; provide a rollback path.
- Capture focus before injecting device keys. An unsupported HDMI screenshot is not proof of focus; document the limitation and inspect window focus.
- Do not commit device addresses, serials, raw logs, private paths, credentials, signatures or APKs. Use synthetic fixtures.
- No device claim from a successful build, process existence, ADB disconnect, or simulated tests. Distinguish local checks, inherited evidence and exact-build physical acceptance.
- Make one checkpoint commit per major workstream. Review staged content and scan secrets before every push.
- Preserve the package ID and signing identity after publication. Release builds must be non-debuggable.

## Entry points
- Local build: `./build.sh`
- Tests: `./tools/test.sh` (offline).
- Staged secret check: `python3 tools/check-secrets.py`.
- Install/diagnose/uninstall: follow INSTALL; never invoke automatically from build/tests.
