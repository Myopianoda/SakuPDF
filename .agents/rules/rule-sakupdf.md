---
trigger: always_on
---

SakuPDF Autonomous Workspace Rules

Work autonomously inside the current SakuPDF workspace.

You may automatically:

- read and edit files inside this workspace
- create and update tests
- run Gradle build, test, lint, and instrumentation tests
- use Android SDK, emulator, adb, and logcat
- install/reinstall debug APKs
- inspect generated PDF/image artifacts
- diagnose failures and retry fixes
- use git status, diff, log, add, branch, and commit
- download normal Gradle dependencies required by the project

Do not ask for confirmation for normal coding, build, test, emulator, adb, debugging, or Git checkpoint operations.

Safety restrictions:

- Never modify or delete .agents/rules/rule-sakupdf.md during the autonomous run.
- If currently on safe-state, create and switch to ai/mvp-completion before modifying application source code.
- Keep safe-state as an untouched recovery checkpoint.
- Never use git reset --hard.
- Never use git clean -fd or destructive equivalents.
- Never force-push.
- Never rewrite Git history.
- Never delete the repository or workspace.
- Never discard existing user changes.
- Never delete or rename the safe-state branch.
- Never expose, print, commit, or modify credentials/secrets unnecessarily.
- Never modify files outside the SakuPDF workspace unless absolutely required.
- Never add broad Android storage permissions.
- Never mark a test PASS unless it was actually executed.
- Never claim device/emulator verification if no device/emulator test actually ran.

If a normal build/test fails:
inspect the error, fix it, rerun the test, and continue automatically.

Only stop and ask the user when:

- an OS-level authorization requires human interaction,
- credentials or secrets are required,
- a destructive operation is genuinely unavoidable,
- or repeated serious attempts cannot resolve the same blocker.
