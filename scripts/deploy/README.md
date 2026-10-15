# 部署与本地流式恢复：当前 BLOCKED

2026-10-14 的候选不具备真实部署能力。`standard_deploy.ps1` 和
`remote_deploy.sh` 在任何上传、服务操作或构建前拒绝执行。旧 TERM/nohup、
Xmx1024M/Xms256M 和服务器完整备份路径已移除；不得改用旧脚本绕过门禁。
这会阻断原有部署调用，是本次安全整改的明确行为变化。

`systemd_local_rollback.py` 仅实现离线事务和文件沙盒适配器：64KiB 原字节流、
完整 JAR/PC/H5/合成配置文件与目录清单、SHA-256 确认链、mode/uid/gid 清单、
备份确认后切换、失败从本地原件流式恢复。原状态为 inactive 的冷启动失败恢复
原 inactive，不伪报 UP；断连或损坏备份返回 2，不能声明回滚完成。
配置在正常切换时保持原字节。沙盒锁是实际 O_EXCL 文件竞争，**不是已验证的 Linux flock**。

systemd 契约使用指定监管报告的 Type=simple、空 PIDFile、unit/override SHA、
ExecStart 和有效 Xms128M/Xmx512M；未知/漂移全部拒绝。验收独立核验 MainPID、
唯一实例、有效 argv、严格 JSON UP、NGINX 未认证 upstream 401、PC200。
这些状态由离线 fixture 提供，不是现场 systemd 或 HTTP 验收。

真实 SSH 流适配器、跨备份/切换/恢复持续持有的 flock 会话、受限
`D:/AicoreRescue` NTFS ACL 尚未实现/验证。现有监管证据只有 `db_config.json`
文件名与未标注路径的 401，缺少完整配置绝对路径清单、独立 upstream URL。
因此不能安全绑定现场备份和探针，也没有开放现场写入入口。
本轮授权仅允许离线工程，禁止借补证连接服务器。

离线回归：

```text
python -m unittest discover -s scripts/deploy -p test_deploy_offline.py -v
powershell -ExecutionPolicy Bypass -File scripts/deploy/test_standard_deploy_offline.ps1
python -m unittest discover -s scripts/deploy -p test_systemd_local_rollback.py -v
python -m unittest discover -s scripts/deploy -p test_mysql_isolated_rehearsal.py -v
mvn -f backend/pom.xml -DskipTests package
npm --prefix web run build
```

原 Python 入口继续保留严格健康探针语料，并运行新事务与真实 Bash 阻断入口。
原 PowerShell 入口现在检验固定目标和无网络的阻断行为；旧上传成功/nohup
测试已随旧实现撤销，不代表旧发布能力保持兼容。
MySQL 回归只使用既有模拟，不运行数据库/容器。

现场恢复、Unix www 属主/权限、真实 systemd PID 与 Restart、flock 生命周期、
连接中断后的现场状态、峰值内存/磁盘都未验证。Windows chmod 不是 NTFS ACL。
完整结果和待补证项见 `docs/mydca_systemd_local_rollback_20261014.md`。
