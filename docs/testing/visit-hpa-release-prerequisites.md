# Visit HPA A：应用、工具版本与迁移前提（D3）

更新：2026-10-19。Chenyu交付；Zhili负责复审、批准环境中的发布与运行态核验。本文件、JSON清单和离线检查均不发起部署或数据库连接。

## 1. 固定什么、没有固定什么

| 对象 | 固定值 / 状态 | 用途 |
| --- | --- | --- |
| 应用组合源码 | `09d6a3f9ec453906f613efd0af7f99fad4fdd8d3` | main `59a91a3c021d0d85a76faf1dd64690c4b4d5c99e` + 护理员迁移 `b486ccd` + Redis保存竞争修复 `1d3c3d8` |
| 负载与对照引擎 | `c5b13e2d91b9e5daa8cb536b8427fa184f60d394` | 包含D1总时限/排空与D2/D5的5RPS模板、业务断言、离线对照；它不是新的业务部署版本 |
| D3/D4材料与离线核验工具 | 本文件所在交付提交；完整SHA见 files-2/plans 完成表 | 新增 release_lock.py、fixture_guard.py、清单、示例和测试；不改变上述应用或引擎 |
| 板块二被评审候选 | PR55，`ade044a8bf47c97bb0017dd4445405552ce97b07` | 引用Zhili反馈；实际批准的加载/恢复配置与采集工具版本仍由他回填 |
| 云端release、镜像digest | **UNCONFIRMED** | Zhili批准发布后核对四服务及report-worker；A0/A1使用同一应用release和core/visit digest |
| 后续本地6b功能 | `codex/visit-6b-followup` / `a106286224669cc9660ca1a47b0d107ae89f09f8` | 保全的k6、FM03缓存和前端跨标签工作；首轮A不自动纳入，不从该分支重建本轮应用 |

需部署的是获准的**应用组合**，不能只搬负载脚本却认为新护理员接口已在线。建议沿正常CI/CD构建应用基线对应的完整release；迁移分支codex/migrate-caregiver-admission已推送但尚无合入main的迁移PR；[D1～D6草稿PR #68](https://github.com/Care-Giver-SE-Team2/carelink-platform/pull/68)以该分支为评审base，仅展示工具/文档差异。PR59是provider构建隔离，与本包无关。交付已上传，未合并main或部署，后续应用集成/发布仍须单独审核。

`release-lock.json`固定了14个应用/发布路径的Git对象、引擎3个核心文件的Git字节SHA256，以及四服务共41个迁移。工作树应用路径与组合基线一致。检查同时拒绝已跟踪应用差异与未跟踪应用路径；忽略文件和实际构建输入仍需发布审查，不以工具检查代替审查。新构建应使用干净checkout/clean输出，避免旧report V4资源残留在target/classes。

## 2. 离线核对命令

从仓库根目录，用Python3.10+（标准库）：

```text
python tools/visit_hpa/release_lock.py --manifest tools/visit_hpa/release-lock.json
python -m unittest discover -s tools/visit_hpa/tests -p test_release_fixture.py -v
```

第一条输出 `PINNED_SOURCE_MATCH`，只证明本地源码与固定清单匹配，`cloudReleaseVerified=false`。依赖已有完整本地Git对象，不联网fetch；浅克隆缺少对象时先由接收人用正常方式取得指定提交，再离线核验。不要重新生成清单来掩盖差异。

`gitBlobSha256` 是固定Git文件字节的SHA256，**不是 Flyway checksum**；Windows CRLF转换可能改变字节SHA。`flywayChecksum` 是UTF-8、无占位符替换SQL的行结束归一化有符号CRC32，已用本机Flyway12.4.0真实计算器逐项验证。四个缓存生产JAR的迁移条目也已离线核对；该检查没有启动JAR，不等于重跑集成测试。

## 3. 四服务迁移清单与边界

完整逐文件路径、版本、Git SHA256、Flyway checksum见 [release-lock.json](../../tools/visit_hpa/release-lock.json)，不要仅看最高版本。

| 服务 / 默认数据库名（实际映射由Zhili确认） | 目标最高版本 | 本次护理员差异及需注意内容 |
| --- | --- | --- |
| core / carelink | V28 | V26 admission、V27 credential_submission、V28 work_settings；保留main已有V1～V25 |
| visit / carelink_visit | V5 | V3 caregiver_system_alert_index、V4 visit_evidence_file、V5 caregiver_history_index；保留V1～V2 |
| report / carelink_report | V5 | **V4 report_task_dispatch**，然后 **V5 visit_evidence_projection**；worker与API共享该服务的迁移史，不重复创造另一套版本 |
| notification / carelink_notification | V3 | 本组合含V1 notification_tables、V2 webhook_delivery、V3 provider_onboarding；不把它们宣称为本轮新开发 |

兼容前提：Java25 / 当前POM及锁定依赖；迁移由服务自身Flyway管理，Hibernate只验证schema；core/visit共享一致Redis session namespace、序列化和必要脚本权限；服务内部鉴权与路由配置匹配同一release。护理员完整迁移涉及私有证书/证据持久存储、上传/PDF资源，部署完整组合前仍需Zhili确认存储挂载和预算。A读取不上传文件、不测PDF，也不据此豁免完整应用的部署前提。

report-api/worker角色、队列与任务生产器配置沿现有正常发布路径由Zhili/板块四核对。A不新增B事件负载、不启用真实AI、不要求重做KEDA验收；不能随意关闭共享消费者来制造可比条件。

## 4. 旧 report 证据 V4 是明确停点

旧护理员分支 `b486ccd` 的 `V4__visit_evidence_projection.sql` 与本组合的证据V5 **SQL正文相同**，名称/版本不同。main的 `V4__report_task_dispatch.sql` 是另一份迁移。不能把“都到V4”当成兼容。

**当前未知：共享库是否运行过旧证据V4。** Chenyu已完成源码侧识别；Zhili在部署前安排具名、有权限的只读执行人查询当前四库历史，回传去敏核对表。业务规则和异常解释Owner为Chenyu；共享库查询执行人/保留批准人不擅自填写，见D4责任表。

- 若report V4=`report_task_dispatch`、checksum匹配且前缀完整，证据V5待正常升级；这与历史本地“main V4→V5”验证路径一致。
- 若report V4=旧证据投影，或V4 checksum不符、任意迁移失败/缺口/重复/未知版本，**停止发布**，保留历史和备份，反馈Chenyu与数据执行人单独设计前向兼容方案。
- 若已达V5，仍逐项比对script、checksum、success；不能只检查表存在或最大版本。
- 不执行repair，不修改已应用checksum/历史行，不删除历史表，不先回滚旧JAR赌兼容。允许的新升级须正常Flyway validate/migrate并有受审恢复方案。

## 5. 共享库只读核验交付格式

由Zhili安排的执行人使用已有批准连接方式，在**每个实际数据库/schema**中执行以下只读SQL。它不会自动连接数据库：

```sql
SELECT DATABASE() AS current_database;
SELECT installed_rank, version, description, type, script, checksum, success, installed_on
FROM flyway_schema_history ORDER BY installed_rank;
```

同时记录数据库/schema映射、UTC、执行人、来源release以及当前库的结构/空库证明。没有历史表、存在BASELINE/SCHEMA/非SQL行、旧分支DDL、修复记录或不符合前缀时，人工审查；不直接创建表或baseline。启动日志只能辅助，不能代替当前历史。

私有JSON导出格式（四服务都要提供，示例仅演示结构；不要提交真实导出）：

```json
{
  "schemaVersion": 1,
  "databases": [
    {
      "service": "report",
      "database": "实际数据库名",
      "capturedBy": "具名只读执行人",
      "capturedAtUtc": "实际UTC时间",
      "rows": [
        {
          "version": "4",
          "description": "report task dispatch",
          "type": "SQL",
          "script": "V4__report_task_dispatch.sql",
          "checksum": 0,
          "success": true,
          "installed_on": "实际数据库返回的时间"
        }
      ]
    }
  ]
}
```

示例checksum=0为占位，不是期望值；rows必须填完整历史，databases包含core/visit/report/notification四项，success转为JSON布尔，不使用字符串"true"或数字1。数据库时间需附连接/会话时区。用可信私有方式保留原导出，回传摘要可以只报匹配/冲突及待升级版本。

```text
python tools/visit_hpa/release_lock.py --manifest tools/visit_hpa/release-lock.json --history /private/flyway-history.json
```

`STOP_REVIEW`/退出2必须停。`SOURCE_HISTORY_COMPATIBLE` 只表示导出内容与源文件一致：子项 `UPGRADE_REQUIRED` 仍需升级，`TARGET_MATCH` 仍需实际Flyway validate与schema核验；两者均 `deploymentApproved=false`。空rows不证明数据库为空，也不授权baseline/初始化。

## 6. Zhili部署/约测前回填

1. 是否接受应用09d6a3f与负载引擎c5b13e2分离固定；最终发布PR/commit、构建来源、四服务/worker digest及release标识。
2. 四库实际映射、只读执行人的姓名和UTC、逐项迁移比对、旧证据V4是否存在。若未知，保持NO-GO。
3. 同release的Redis会话和私有文件存储前提；PR55实际批准的配置/collector提交；CD窗口是否冻结。
4. Z2～Z4监控/容量/加载恢复/窗口继续由Zhili负责。D3离线通过不替代这些条件，也不代表已获准云端测试。

## 附：逐文件 Flyway checksum 快速表

SHA256及完整路径以固定JSON为准。本表由同一JSON生成。

| 服务 | 版本 | 文件 | Flyway checksum |
| --- | --- | --- | --- |
| core | 1 | V1__baseline.sql | -766878 |
| core | 2 | V2__care_domain.sql | -728354739 |
| core | 3 | V3__stop_care_plan.sql | 1176559857 |
| core | 4 | V4__flatten_care_plan_node.sql | -468232710 |
| core | 5 | V5__drop_care_plan_node_service_type.sql | -461535555 |
| core | 6 | V6__care_plan_start_date.sql | -879592470 |
| core | 7 | V7__elder_service_dispute_incident_source.sql | 457372633 |
| core | 8 | V8__report_amendment.sql | 1836072364 |
| core | 9 | V9__elder_primary_caregiver.sql | -512060905 |
| core | 10 | V10__care_plan_node_visit.sql | -561518148 |
| core | 11 | V11__credential_review.sql | -1043063645 |
| core | 12 | V12__absence_rerostering.sql | -276744204 |
| core | 13 | V13__spot_check_conclusion.sql | 462914158 |
| core | 14 | V14__seed_value_added_services.sql | -1677146032 |
| core | 15 | V15__family_alert_delivery.sql | 1833942631 |
| core | 16 | V16__caregiver_command_receipt.sql | 1295585224 |
| core | 17 | V17__visit_check_in_and_task_uniqueness.sql | -216226740 |
| core | 18 | V18__visit_missed_check_in_trigger.sql | -380969238 |
| core | 19 | V19__incident_description_and_report_basis.sql | -2116843670 |
| core | 20 | V20__visit_health_records.sql | 503757286 |
| core | 21 | V21__family_care_service_application.sql | 27408493 |
| core | 22 | V22__value_added_service_duration.sql | -568160987 |
| core | 23 | V23__visit_instructions.sql | -969942316 |
| core | 24 | V24__care_plan_node_activity_code.sql | -384056573 |
| core | 25 | V25__service_application_decline.sql | 993302770 |
| core | 26 | V26__caregiver_admission.sql | 1119888666 |
| core | 27 | V27__caregiver_credential_submission.sql | 1999752257 |
| core | 28 | V28__caregiver_work_settings.sql | 740801175 |
| visit | 1 | V1__visit_tables.sql | -1450296056 |
| visit | 2 | V2__visit_request.sql | 556943074 |
| visit | 3 | V3__caregiver_system_alert_index.sql | -1444325943 |
| visit | 4 | V4__visit_evidence_file.sql | -597251045 |
| visit | 5 | V5__caregiver_history_index.sql | 1897602332 |
| report | 1 | V1__report_tables.sql | 1215694517 |
| report | 2 | V2__value_added_service_catalogue.sql | 1085518467 |
| report | 3 | V3__report_unique_period.sql | -572798646 |
| report | 4 | V4__report_task_dispatch.sql | 1912429229 |
| report | 5 | V5__visit_evidence_projection.sql | -1121044410 |
| notification | 1 | V1__notification_tables.sql | 898893085 |
| notification | 2 | V2__webhook_delivery.sql | 1050415276 |
| notification | 3 | V3__provider_onboarding.sql | 1620249201 |
