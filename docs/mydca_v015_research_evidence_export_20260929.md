# v0.15 策略研究证据导出

PC 策略实验室选择 2 至 5 条本人成功历史 run 后，可使用当前研究阈值导出 `backtest-research-evidence.zip`。接口为 `POST /api/v2/backtest-lab/runs/evidence`，请求体与研究接口一致，响应类型为 `application/zip`，附件名固定。失败或越权记录会拒绝导出；全部数据来自已有历史，不重新回测，也不创建交易。

ZIP 包含 UTF-8 的 `manifest.json`、`summary.md`、`compare.json` 和 `research.json`。Manifest 记录规范化排序的 run ID、每条 run 的数据集 SHA-256、策略与版本、规范参数、引擎版本、区间与指标，以及候选规则版本、阈值、命中理由和警告。`content_sha256` 分别校验另外三个文件；HTTP 响应头 `X-Content-SHA256` 校验完整 ZIP。ZIP 文件顺序与时间戳固定，同一组输入与阈值产生相同字节。摘要说明入选比较的原因、证据不足点和不能推出的结论。

导出只使用 compare/research 的字段白名单，不包含历史原始结果、CSV、账户/交易明细、Token、路径、通知原文或图片 URI。服务端限制 ZIP 为 256 KB、处理时间为 10 秒；PC 请求超时为 15 秒并显示失败态。历史回测不代表未来表现，研究候选不是买卖或执行建议。
