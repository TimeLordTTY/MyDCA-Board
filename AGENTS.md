# Task Completion Hook

In this repository, after any code edits are completed for a user task:

1. Run `powershell -ExecutionPolicy Bypass -File scripts/post-task-compile-hook.ps1`.
2. If build fails, continue fixing compile errors and rerun until success.
3. Only send final user response after hook passes.
4. Successful builds must be non-intrusive: do not play sounds and do not show Windows toast/popup notifications. Success is reported only in stdout/logs and through the AiCore/Hermes delivery chain.

