# MyDCA-Board Agent Rules

> 详细工程约定见 `AGENT.MD`；当前实现事实见 `docs/CURRENT_DEVELOPMENT_STATE.md`。

1. 任何代码或项目文件编辑后，运行 `powershell -ExecutionPolicy Bypass -File scripts/post-task-compile-hook.ps1`。
2. hook 失败则修复并重跑；测试/构建未通过不得声称完成。
3. **成功构建必须非侵入**：不得播放 Beep、不得弹 Windows Toast/Popup；成功只写 stdout/log，并由 AiCore/Hermes 负责交付通知。
4. 不自动修改真实财务记录、不自动交易、不连接生产数据库，除非任务具有对应高风险明确授权。
5. Android/AI/OCR/通知/Outbox 默认只到 DRAFT；preview/confirm/正式入账人工边界不得绕过。
6. 新任务先读取 `docs/DOCUMENT_INDEX.md`，不要依据 Phase1/Phase2 或旧专项文档中的历史“下一步”覆盖当前计划。