# 前端 Monorepo

## 当前定位

`web/` 继续维护 PC Web 与共享 TypeScript 契约；`web/mobile-app` 为历史 Mobile H5 兼容实现。**长期主移动端已转为仓库根目录 `android-app/` 的原生 Android。**

当前项目状态见 `../docs/CURRENT_DEVELOPMENT_STATE.md`。

## Workspace

- `shared/`：API、类型、Store、工具。
- `pc-app/`：Vue 3 + Element Plus，当前 PC 主端。
- `mobile-app/`：Vue 3 + Vant，历史兼容端，不作为新增 Phase3 移动能力的默认落点。

依赖统一在 `web/` 根目录安装：
`npm install`

构建：
`npm run build`
或使用现有 `build-web.bat`。

## 关键约定

- shared 中 Vue/Pinia 保持 peer dependency + dev dependency。
- Vite 保持 Vue/Pinia/Vue Router 去重。
- 新增移动记账、OCR、通知监听、Outbox、系统分享等能力优先进入 `android-app/`，除非任务明确要求 H5 兼容。
- PC/共享接口改动仍需与后端 `/api/v2/**` 契约同步。

## 任务收尾

仓库根目录执行 `scripts/post-task-compile-hook.ps1`。成功构建只写日志，不播放提示音、不弹窗。