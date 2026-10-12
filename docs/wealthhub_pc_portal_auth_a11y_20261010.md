# R2-19 登录、门户与跨页面无障碍独立重建

任务 task-aiw-r2-19-portal-auth-a11y-20261010；正式基线 948de37dfbe4489ad3e3c52bac1b8a7b95d0120b。
旧来源 aa73b35d331e757edbc11fb738fab28ba3621647 仅复用逐文件审查的内容，旧 run 的 item_31 失败、旧回执和旧成功声明不能证明本次验收，保持原样归档。

## 内容与安全边界

Login/PortalLogin 复用正式服务端认证，Portal 保留独立站点权限和原入口；没有本地昵称登录、演示资产或新增资金接口。登录恢复仅允许注册的本地受保护路由，保留 query/hash；PortalLogin 仅恢复 Portal。
PC 401/403 边界清除 JWT、用户/账户/产品缓存及旧页面；跨标签 token、用户/家庭变化清空状态，旧 JWT 响应拒绝，普通断网保留 JWT 并仅允许手动重试。登录/注册/退出等待期间禁止重复提交；密码不持久化。
跨页 Alt+1 至9尊重输入/IME/dialog，移动抽屉保留焦点陷阱与返回，ARIA 当前状态与快捷键、reduce-motion 保留；流水表可见滚动并保留操作。行情按钮随真实请求结束恢复。
backend、SQL、Android、shared 源码、依赖锁、主类型配置、构建入口均不改。不连接生产数据库、真实资金接口、交易/券商、不执行部署。

## 本次验收要求

本文件是内容来源和待执行验收说明，不能作为 PASS。唯一权威是本次新 run 的真实日志、checks/lineage/verification、原 Runtime.integrate 及 AiCore Delivery。
在全新 clean worktree 生成新 source commit；固定执行独立基线与依赖、全部 PC Node、所有局部 TS、全量 TS 的诊断多重集合对照、完整 post-task-compile-hook MaxFixRounds0、diff-check。历史 TS 非零必须保留真实数量与退出码，新增诊断阻断。
独立 Playwright CLI + Edge 在无代理 localhost 静态 preview 验证 JWT/401/403、双登录边界、注册、深链刷新、等待重复点击、身份/家庭切换和迟到响应、弱网手动重试、14个业务页面键盘/ARIA/抽屉/dialog/reduce-motion。覆盖1440/1100/1024/760/390屏宽；回执的标准widths字段为1440/1100/760/390，逐页矩阵另含1024。
所有业务API均mock；只允许精确localhost静态GET，未知请求与外部origin中止并阻断，serviceWorkers禁用。令牌、用户名、密码和数据均为明确fixture，真实财务写入0。完整脚本回显须匹配最终fixture，原始Result显式PASS及完整隔离结构、pageerrors空，日志/源码/任务/旧原件哈希均复核。

## 后续人工验收

离线浏览器不证明真实角色/JWT与Nginx环境；实际账号/家庭权限、生产配置、真机、屏幕阅读器、密码管理器、输入法和主观设计仍待对应授权验收。工程完成不代表生产已部署；goal_only最终汇总须真实Hermes企业微信receipt。
