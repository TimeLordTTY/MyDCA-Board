# 标准部署

`standard_deploy.ps1` 保留固定目标与原有参数。它构建本地产物（原有 Maven 参数跳过单元测试）、校验 JAR/前端/探针 SHA-256，验证完整旧包备份后停止、替换、启动。成功必须同时满足新启动 PID 存活、服务器本机 `http://127.0.0.1:8766/actuator/health` HTTP 200 且可解析 JSON `status=UP`、独立 HTTPS 前端检查；前端 200 不能代替后端健康，HTTPS 不再跳过证书校验。

停服后任一步失败均尝试恢复旧 JAR 与完整旧前端，并确认恢复后端 UP。旧包/首页缺失、备份或复制失败在停服前阻断；停止超时不强行换包。失败保留工作目录、上传包、备份和脱敏状态。退出码 1 表示阻断或已确认健康的回滚，2 表示 `ROLLBACK_FAILED_MANUAL_RECOVERY_REQUIRED`，两者都不是成功。目标需 Bash/Linux `/proc`、Python 3、curl、flock 和既有 `www` 用户/组、Java 与日志目录；不修改运行配置。

该 profile 不修改数据库 schema/数据、密钥、证书、DNS、防火墙、网关或 Nginx，也不执行交易。首次修改脚本属于 L3 工程任务；实际执行部署仍属于另一个受控阶段，必须先通过目标环境 schema、兼容性与人工部署审批。此次只有离线模拟证据，真实 Linux 进程/权限/恢复未验证，不能据此认定可上线。

离线测试（不会调用真实 ssh/scp、启动 Java 或连接数据库）：

```text
python -m unittest discover -s scripts/deploy -p test_deploy_offline.py -v
powershell -ExecutionPolicy Bypass -File scripts/deploy/test_standard_deploy_offline.ps1
python -m unittest discover -s scripts -p test_mysql57_compat_preflight.py -v
python scripts/mysql57_compat_preflight.py --help
```

`remote_deploy.sh` 是函数库，PowerShell 发送库并追加入口。Bash 模拟复用同一库，隔离目录内用假 PID、假 curl 和拒绝联网的 ssh/scp 桩；真实复制、哈希、恢复路径经过执行。PowerShell 测试用假 scp/ssh 验证上传失败阻断和命令参数转义。完整证据与限制见 `docs/mydca_deploy_health_mysql57_offline_preflight_20261009.md`；静态报告退出码 0 只表示覆盖检查通过。

```powershell
powershell -ExecutionPolicy Bypass -File scripts\deploy\standard_deploy.ps1
```

部署完成后，在服务器使用本地隔离账号执行只读 smoke：

```bash
python3 mobile_readonly_smoke.py --base-url https://www.timelordtty.cn --credential-file /root/.config/mydca/test-account.env
```
