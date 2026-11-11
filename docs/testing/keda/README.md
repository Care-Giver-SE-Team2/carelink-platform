# 板块四从这里开始：Report Worker 接入约定 v1

状态：接口与首轮参数已决定，待 Java 实现和云端接入；此 PR 不部署、不启用、不合并。
源码核对基线 `20e456a`。此前方案中的“任务粒度/队列入口待确认”，以本文件决定为准。
完整云端盘点和改动范围见 [联合接入方案](../keda-integration-plan.md)。

## 1. 板块四不需要决定的云端参数

| 项目 | 接入决定 |
| --- | --- |
| AWS region / 应用 namespace | ap-southeast-1 / staging；不创建 demo |
| API release / Service | 保留 report；api profile；常驻 2 副本；不改变 HTTP 路由或公开网址 |
| Worker release / ServiceAccount | report-worker；worker profile；独立选择器；无 Ingress；复用同一 report 镜像 digest |
| 消息入口 | 现有 carelink-staging-events SNS topic；新增仅 type=ReportRequested 的 raw SQS 订阅；不修改原报告事实队列过滤器 |
| Worker queue / DLQ | carelink-staging-report-worker / carelink-staging-report-worker-dlq；SQS Standard；托管加密 |
| 队列初始 visibility / 重投 | 180s；Worker 每 60s 续租为 180s；maxReceiveCount=5；主队列保留 4 天，DLQ 14 天；DLQ 只允许该源队列重投 |
| AWS 身份 | Pod Identity；Worker 专用角色；KEDA 专用只读队列属性身份；不在 Java 或 YAML 放 access key |
| KEDA namespace / ServiceAccount | keda / keda-operator；关联、boundary、安装版本兼容性由板块二负责 |
| 首轮 Worker 资源 | requests 100m CPU / 512Mi；memory limit 768Mi；每 Pod 同时处理 1 条消息 |
| 首轮伸缩 | 固定 1 副本联通后，再 min=0 / max=1，最后 max=2；不增加 EC2 节点；每次放开前板块二检查容量 |
| KEDA 候选参数 | polling=15s；cooldown=300s；queueLength=1；activationQueueLength=0；scaleOnInFlight=true；delayed 不计入 |
| 退出预算 | preStop=10s；Pod termination grace=270s；Spring shutdown phase=240s。按真实退出阶段测量，不承诺超时任务无中断 |
| AI / fault-drill | 首轮 CARELINK_REPORT_AI_ENABLED=false；fault-drill 默认关闭、测试延迟 0；真实 Bedrock 测试另列，不能把 AI disabled 当真实模型验收 |
| 测试上限 | 每批最多 10 条业务任务，最多 2 个 Worker；每轮观察最多 30 分钟，到时停止生产并保留任务核查；不自动 purge 或销毁 |
| 暂缓项 | Active-Standby；完整定时生产者另行验收。首轮受控发消息不冒充已完成 EventBridge 周报调度 |

以上是实现目标，不是云资源已经存在。实际 queue URL、topic ARN、角色关联与运行 Secret
由板块二在接入准备完成后注入；Java 读取环境配置，不能写死账号或 URL。
KEDA polling 仅是零副本检测的一部分，不能承诺 15s 内 Pod Ready。

## 2. 唯一任务契约：ReportRequested / payload.schemaVersion=1

一条消息 = **一位老人 + 明确的 Monday–Sunday 完整周**，一次生成该老人该周期的全部现有
audience 报告。API 生产者负责枚举老人并 fan-out；Worker 不再把 elderId 缺省解释成“全部老人”。
首轮可由测试操作者使用 SDK/CLI 受控发布同格式消息，无需新增生产 HTTP endpoint。

消息见 [report-requested.v1.example.json](report-requested.v1.example.json)。这是新实现契约，
当前 main 的 handler 尚不支持其中 elderId / period 字段，旧 handler 会忽略它们；不能直接拿样例投现网。

| 字段 | 必须行为 |
| --- | --- |
| id | UUID；同一传输消息重试保留相同 id；不同业务任务用新 id |
| type / source | ReportRequested / report；SNS MessageAttributes 也设置同名 type 和 source 的 String 值 |
| occurredAt | 带 Z 的 UTC ISO 时间，仅表示事件发生时间；不再用它替代明确的报告周期 |
| sequence | 由现有 Outbox 生成时沿用；手动测试可省略 |
| payload.schemaVersion | 整数 1；未知版本拒绝处理 |
| payload.elderId | 正整数；缺失、null、布尔值、0、负数不得回退到全量批任务 |
| payload.periodStart / periodEnd | ISO date；周一到同周周日，两端包含；按 Asia/Singapore 解释，必须在 occurredAt 对应本地日期之前完整结束 |
| payload.runId | 本轮测试或生产批次标识，小写字母/数字/连字符，1–64 字符；用于日志关联，不参加业务唯一键 |

V1 不接受未约定的 payload 字段；以后扩展应先改契约及测试，避免静默忽略字段。
业务唯一性沿用 `(elder_id, audience, period_start, period_end)`；runId / event id 不得使同一业务报告重复。
两种重复都要验收：相同 id 重投；不同 id 但相同老人和周期。数据库唯一约束不等于重复 AI 调用为零。
无效契约应失败并保留消息，让 SQS 重试后进入 DLQ；不作全量 fallback，不把无效任务标记为成功。

无需 AWS 的格式检查（只验证交接契约，不证明 Java 已实现）：

```bash
python3 docs/testing/keda/task_contract.py docs/testing/keda/report-requested.v1.example.json
python3 -m unittest discover -s docs/testing/keda -p 'test_*.py'
```

样例 elderId 为占位测试 ID，不代表线上已有数据；首次投递前换成已建立的隔离 fixture。

## 3. 板块四需要修改及随代码交付的内容

- `ReportRequestedHandler`：按 v1 校验；从 payload 读取 elderId、周期，调用
  `prepareBatch(elderId, periodStart, periodEnd, null)`，继续在消费者同一事务中提交报告和消费标记。
- 任务生产代码：一位老人一条任务；沿用 Events/Outbox→SNS Envelope 与 type/source 属性，
  明确周期和 UUID；不要新增静态 AWS 密钥，也不要在 Worker 内重新枚举全体老人。
- API / Worker profile 均关闭旧批量 timer；说明一次性切换、未完成批次补发与回退如何避免漏跑/双跑。
  原定时器是否承载其他结算任务也要检查，不能为周报切换误停其他业务。
- 正常 Worker 不启用 fault-drill；线程/数据库事务/续租与退出不破坏既有可靠性测试。
- 测试至少覆盖：缺 elderId / 未知版本 / 错周期；两个独立老人任务；同 id 重投；不同 id 同业务键；
  SIGTERM / SIGKILL 后恢复；准备或提交失败；续租失败；API 原 HTTP 和事实队列回归。
- 日志至少能关联 eventId、runId、elderId、period、Pod、开始/完成/失败/去重；可用日志与 SQL
  汇总，不强制新增监控框架。重投次数与业务唯一结果分开统计，避免把 SQS MessageId 当业务 ID。

**交付包必须包含，缺一项不能只回复“代码好了”：**

1. PR、提交 SHA、通过的本地测试命令与结果，配置键和新增数据库 migration 的说明。
2. 可复现 fixture：隔离老人、必要的 core/visit 事实数据、明确周期；初始化和仅清理该 fixture 的脚本。
   不复制真实个人资料，不依赖某人本机临时改库。
3. 输入消息文件：单任务、多个独立任务、两种重复任务；标注哪些可进正常队列、哪些只用于隔离负向测试。
4. 预期报告数量/内容/状态，以及执行前后 SQL；说明已有报告时预期新增为零，不能拿总表行数代替正确性。
5. 已知限制、最长任务与退出测量、错误定位步骤；有 AI disabled / mock 时明确标注。

## 4. 板块二交付条件：由我方完成，不要求队友配置 AWS

- 建立专用 SQS/DLQ 和 SNS 订阅、Worker / KEDA 限定身份；真实 plan 只新增目标资源，
  不重建数据库、Redis、网络、CloudFront，不改现有事实队列。
- Worker 专用 runtime Secret 注入 DB/TLS/Redis 及 Worker queue URL；不复用含原队列 URL 的 Secret。
  凭据不进 PR、不交队友手工粘贴。
- 保持现有 report Service / release / 两副本；增加可选 Worker release、同 report digest 发布与回退。
  不能让 Worker 被当成第五个镜像；未启用时四服务原渲染不变。
- Helm KEDA 模式不写 replicas、不生成额外 CPU HPA；正常 CD 不把零副本强制恢复成两个。
- 安装配置固定兼容版本和资源，核对集群 RBAC / external metrics、Pod Identity 与队列属性读取。
- 固定现网发布状态后做容量复检：KEDA 和两 Worker 加入后，每节点仍留至少 500m CPU / 1024Mi
  requests 余量，并另检峰值使用、rollout surge、Pod/IP、DB 连接、单 AZ 降级限制。
  不满足就保持 max=1 / 暂停测试，由板块二调整方案；不让队友猜资源规格，不自动加 EC2。
- 提供私下的实际端点映射、首轮可运行的发布/采集入口和回退记录；云端准备通过前不要求队友投消息。

**本 PR 仅提交约定、示例和离线校验；上述云端实现和测试入口尚未交付完成。**
代码完成不会自动补齐这些资源。板块二可与板块四编码并行准备，接入完成后再宣布 Cloud Ready。

## 5. 代码交付后，用户可独立执行验收

不要求板块四全程在线。用户可由助手配合，依交付包部署、启动、投递和核验。
板块四仍负责修复发现的 Java / 业务问题；“能部署”与“结果正确”是两个验收维度。

| 轮次 | 用户/板块二可独立执行的步骤 | 核验依据 |
| --- | --- | --- |
| 1 固定 1 Worker | 建 fixture、发布 1 条消息、观察日志和执行 SQL | 板块四交付的准确预期；提交后删除消息；API/事实队列正常 |
| 2 0→1→0 | 空队列零副本→投 1 条→等待完成→300s cooldown 后观察 | 自动唤醒、处理、缩回零，记录实际各阶段时间 |
| 3 0→2→0 | 最多 10 条独立任务、两个 Worker，上限内观察 | 不同 Pod 实际处理任务；结果正确、无遗漏；任务过快未触发并行应标“不充分”，不能造假延迟冒充吞吐 |
| 4 中断恢复 | 仅定位 fixture 的活跃 Worker；终止后等待重投 | 按 SQL 验证不丢结果/不重复落库；记录重复执行与恢复时间 |

每轮保留：固定 SHA/digest、输入、UTC 时间线、SQS visible/in-flight/DLQ、Pod desired/ready/Pending、
CPU/内存、任务成功/失败/重投/去重、处理和等待时间、SQL 输出与公网验证。
捕获原网页及登录基线；测试中页面/登录探针连续 3 次失败（10s 间隔）、Pending 连续 120s、
出现任何非预期 DLQ/业务重复、KEDA 队列指标连续 60s 不可读，或本轮超过 30 分钟即停止新投递。
停止不等于删队列；排空/固定 Worker/暂停伸缩后核查，保留证据和未完成任务。
真实 Bedrock、负向 DLQ 注入、强制终止各有明确轮次标签，不能和正常扩容通过率混算。

遇到以下问题才需要板块四回来：SQL 结果不符、任务契约实现不符、事务/幂等/重试或报告内容错误、
新增 migration 不兼容。若是 IAM、Pod Pending、KEDA 指标、发布脚本或云配置问题，板块二负责处理。

## 6. 两方最终回复格式

板块四回复：`Code Ready: SHA + 本地测试结果 + fixture/消息/SQL/预期结果路径 + 已知限制`。
板块二回复：`Cloud Ready: 实际端点已私下注入 + 身份/容量/发布/回退通过 + 固定测试 SHA/digest`。
两项均满足后，用户可自己开跑，不需要额外等队友在场；尚未实测时状态仍为 Pending Validation。
