# 标准部署

`standard_deploy.ps1` 是固定目标、无模型、可回滚的 MyDCA 日常部署入口。它执行本地测试构建、SHA-256 校验、服务器旧包备份、停止、替换、启动和 HTTPS health check；health 失败时自动恢复旧后端与前端。

该 profile 不修改数据库 schema/数据、密钥、证书、DNS、防火墙、网关或 Nginx，也不执行交易。首次修改脚本属于 L3 工程任务；脚本通过验证后的重复执行属于 L0。

```powershell
powershell -ExecutionPolicy Bypass -File scripts\deploy\standard_deploy.ps1
```

部署完成后，在服务器使用本地隔离账号执行只读 smoke：

```bash
python3 mobile_readonly_smoke.py --base-url https://www.timelordtty.cn --credential-file /root/.config/mydca/test-account.env
```
