# 财富中枢 PC 研究与回测实验室：独立重建

任务：task-aiw-r2-16-strategy-lab-20261010；2026-10-12。
正式基线为已交付R2-15的97bef8a477e353b9227dec75b9cfb0c2e6cfa9de。原source f607c0c5c5618a5ca0bf1fa156543b0d27a14ce2仅复用已审查PC内容；原run收尾失败、开发期CLI失败和旧验收断言不继承。

## 授权行为与安全

- 研究方案只用原researchPlanApi手动创建/编辑元数据、保存参数草稿、确认归档；来源快照、警告、参数、dataset/hash/引擎版本/日期保留。
- 原白名单数据集/策略及后台受控回测API保留。请求中/失败/超时/结果未知须手动刷新历史，不自动重试；不增加真实交易、券商、订单、资金占用、入账或结算权限。
- 未保存草稿与未授权数据集禁回测；PC只校验输入和显示后台指标，真实0/未知不同，历史不预测未来收益。
- 分页重试保留页码；用户/家庭切换及卸载清结果，迟到响应和旧选择版本不能复活。仅成功run参与2至5条比较/导出；JSON/Markdown/证据ZIP保留原字节和来源。
- 主题、窄屏、同页键盘导航统一；策略页隐藏记账等业务快捷入口。后端、SQL、shared、Android和权限未修改。

## 独立验收链

本提交描述内容，不自称验收成功。由AiCore独立请求固定执行：独立基线npm ci ignore-scripts/shared build/完整TS、全部PC Node、全部18局部TS、完整TS诊断多重集合、原完整hook MaxFixRounds0、新鲜Playwright CLI+Edge。
浏览器仅本地3115无代理静态preview及mock，全部业务API拦截、未知请求失败。研究创建/编辑/归档、保存后受控回测、真实零/未知、错误重试、历史/候选/比较/三种导出、1440/1100/760/390及深色/键盘都重新执行，原始fixture回显及哈希必须匹配，显式PASS和完整隔离边界才接受。

正式Delivery仅由原Runtime.integrate/Finalizer产生；原failed记录和日志不改。新鲜证据、来源映射及结果在AiCore reports/work-rescue/20261012-r215-independent-rebuild.md及同名附件。完整PC历史TS错误不宣称通过；hook的mvn -DskipTests package不代表后端单元/数据库测试。真实登录/角色、真实后端财务/研究引擎、生产数据库/财务记录/交易/部署不运行，fixture不能替代这些环境验收。
