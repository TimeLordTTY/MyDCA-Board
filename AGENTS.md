# Task Completion Hook

In this repository, after any code edits are completed for a user task:

1. Run `powershell -ExecutionPolicy Bypass -File scripts/post-task-compile-hook.ps1`.
2. If build fails, continue fixing compile errors and rerun until success.
3. Only send final user response after hook passes.
4. Keep the success beep behavior enabled in the hook script.

