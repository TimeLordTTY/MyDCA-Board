# PR1 无限探索整卡入口验收

日期：2026-10-17，Asia/Hong_Kong。本次在 UI 输入 `03d72111f19c141b090182354180e1125edf9a6f` 的独立工作树完成本地合成验收。未推送、合并、部署、连接生产数据库或修改真实财务记录；未使用真实登录凭据。正式交付、通知及进程清理由控制器负责。

## 范围与审查

已读取仓库规则、文档索引、当前状态及既有私有认证和门户接续报告。当前 UI 实现满足整卡入口要求，故没有修改 Portal.vue 或 auth-portal.css，仅增强 `web/pc-app/tests/privateSites.browser.js` 并新增本报告。

以已存在的认证提交 `0afa7f823409bc36ca4ffa005200f9dfcb61714e` 计算 merge-base 为 `bb14e03df9ed5c57399542d5ab7af5493cd5ef46`。该 merge-base 至 UI 输入仅三个前端文件差异（Portal.vue、auth-portal.css、privateSites.browser.js）；未移植新认证 base 的后端差异。首次尝试短分支名因本地无该引用失败，随后使用任务指定的完整提交计算，未 fetch 或改动分支。

整卡是原生 button，内部使用 phrasing content；复用 app-card、图标、标题字体和箭头。激活仍走 enterExplore → readSessionIdentity → 私有 Owner exchange → 固定 `/explore/`，没有改成绕认证直链。保留身份恢复禁用、busy、重试、aria-live、注销及跨标签身份版本边界。其他独立站点卡片和财务入口未改。

## 原生检查实际结果

所有命令均直接执行，原始输出由控制器持有；未使用自动编译 hook、源码替换、临时覆盖比较或自制进程清理。

| 命令 | 最终结果 |
| --- | --- |
| `npm.cmd --prefix web ci --ignore-scripts --no-audit --no-fund` | exit 0，安装 156 包，锁文件未改 |
| `node --test web/pc-app/tests/privateSites.test.mjs` | exit 0，25/25 |
| `node --test web/pc-app/tests/portalAuth.test.mjs` | exit 0，6/6 |
| `node --test --test-concurrency=1 web/pc-app/tests/*.test.mjs` | exit 0，298/298，0 skipped |
| `node web/node_modules/vue-tsc/bin/vue-tsc.js --noEmit -p web/pc-app/tsconfig.json` | exit 0，无诊断 |
| `node web/node_modules/vue-tsc/bin/vue-tsc.js --noEmit -p web/pc-app/tests/tsconfig.privateSites.json` | exit 0，无诊断 |
| `npm.cmd --prefix web run build` | exit 0，shared、PC、兼容 H5 全部成功 |
| `mvn -f backend/pom.xml -DskipTests package` | exit 0，BUILD SUCCESS；测试明确跳过 |
| `node --check web/pc-app/tests/privateSites.browser.js` | exit 0 |
| `git diff --check` | exit 0 |

上述两组 Node 专项测试包含真实 Vue SFC 模板编译断言，均通过。首次两个专项 Node 命令及前端 build 因此树尚未安装 TypeScript 依赖失败；安装后按原命令完整重跑通过，失败输出不删除。构建保留既有 npm 配置、大 chunk 和 Java deprecated API 警告；成功仅输出日志，无声音或系统弹窗。

类型比较只使用控制器提供的 baseline.json 和 types-baseline.log：基线 base 等于本次 UI 输入，type exit 0、error count 0，日志为空（SHA256 `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`）。本树最终完整 PC 类型检查也是 exit 0、零诊断，因此新增诊断 0。没有编辑基线树或重跑基线检查；未以历史报告中的 44 条诊断代替本次控制器证据。

## 最终隔离 Edge 验收

先运行本树静态预览：`npm.cmd --prefix web/pc-app run preview -- --host 127.0.0.1 --port 3115 --strictPort`。未启动业务后端。随后在 web/pc-app 直接执行：

```text
npx.cmd --yes --package @playwright/cli@0.1.22 playwright-cli -s=portal-pr1-validation-20261017-02 open about:blank --browser msedge
npx.cmd --yes --package @playwright/cli@0.1.22 playwright-cli -s=portal-pr1-validation-20261017-02 run-code --filename tests/privateSites.browser.js
```

最终命令 exit 0，完整原样 fixture 已由 CLI 回显。运行后的 fixture SHA256 为 `163c7b2d89c1766e125c374eed694e27942ed347a4d1895a16441d4cff7b0232`；此后没有再修改 fixture。原始结果：

```json
{"result":"PASS","boundary":"ALL_BUSINESS_REQUESTS_INTERCEPTED_NO_SERVER_OR_PRODUCTION_CONNECTION","pageerrors":[],"unexpectedRequests":[],"financialWrites":0,"interceptedRequests":83,"widths":[1440,390]}
```

该段摘录保留全部安全结果字段；完整 CLI 输出另包含 matrix 及完整执行代码。1440/390 宽度均验证无横向溢出、卡片无重叠、标题字重字号一致、焦点可达且有 outline。Enter 覆盖错误重试，Space 覆盖成功激活；pending 时 disabled/aria-busy 为真，重复激活仅一次 exchange；错误状态可见且保持 polite live status。

保留 401/403/404/503、离线、过期/可缓存响应拒绝、固定目的地、恶意 redirect 不跟随、JWT 切换、延迟身份/交换、真实第二标签切账号、注销中交换和旧注销不能清新账号等矩阵。CSRF 覆盖 exchange 引导头、Cookie state/logout 不夹带 Bearer、注销使用服务端 CSRF；Node 反例保留空 CSRF 成功响应拒绝。此为客户端合成契约检查，不能代替真实后端 CSRF 攻击验收。

所有 `/api/` 业务 HTTP 与 `/explore/` 均 mock fulfill/abort，未知路径和外 origin abort；只放行本树 loopback `/wealth-hub/` 静态 GET。合成 Owner/成员凭据不是真实账号。故意注入 HTTP 拒绝和离线产生 console 资源错误，pageerrors 仍为空。没有复用 Owner 浏览器/Cookie。

## 交付边界

验收发现的缺口是浏览器断言不够明确，现已补齐；未发现需要产品源码修复的问题。只有上述 fixture 与本报告进入一个本地结果提交。未改 backend、shared、SQL、认证实现、依赖、调度、控制器或其它任务文件；旧失败证据保持只读。

未运行 Maven 单元测试（本任务要求的是固定 skipTests package）、Android、其余全站浏览器 fixture、真实 Owner/设备、Nginx 或生产验收；本次 mock PASS 不表示生产入口启用或部署完成。未生成截图，不将自动几何与样式断言宣称为 Owner 主观视觉验收。后续普通合并和生产最小增量发布由授权流程处理，合并时仍须核实后端相对新认证 base 逐路径不变。
