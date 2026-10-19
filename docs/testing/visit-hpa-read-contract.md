# 首轮 A 读取：D2 负载预算与 D5 判定契约

更新：2026-10-19。仅针对护理员固定登录会话的工作包读取，依据 Zhili 10.19 反馈。下面的负载、阈值和人工条件均是待复审候选；不证明云端已部署或可开测。

主入口：[模板](../../tools/visit_hpa/example.joint.json)、[工具说明](../../tools/visit_hpa/README.md)、[本轮本地证据](visit-hpa-d2-d5-local-validation.md)。

## D2：固定读取模板与 WAF 预算

readProfile=visit-hpa-A-waf-v1，scenario=work-pack，maxVUs=8，maxIterations=2700。只执行 `GET /api/visits/{id}/work-pack`；无额外probe、无自动重试、无追压补发、无签到。

| 阶段 | RPS（每迭代一个业务GET） | 秒数 | 计划GET |
| --- | ---: | ---: | ---: |
| baseline | 1 | 180 | 180 |
| step-2 | 2 | 120 | 240 |
| step-4 | 4 | 120 | 480 |
| step-5 | 5 | 120 | 600 |
| hold | 5 | 240 | 1200 |
| cooldown | 0 | 480 | 0 |
| 合计 | 峰值5 | 1260（21分钟） | 2700 |

每组8个VU，各执行 csrf→login→csrf→me→首个work-pack，共40次setup HTTP，其中8次work-pack。单组入口计划总量2740，两组5480；其中业务GET共5400，另有16次setup工作包读取。准备、烟测、人工检查、其他同学和同IP浏览器流量不包含在这5480次内，必须另计。每个GET会写访问审计；以上是请求计划，真实审计增长由Owner按实际调用核对。

预算依据是 **Zhili反馈时** 的同来源IP、300秒2000次WAF规则，本轮没有重新读取AWS。用计划到达时刻精确计算半开滚动窗口：

| 口径 | 单组结果 |
| --- | ---: |
| 业务计划任意300秒最大数量 | 1500 |
| 只减业务后的名义余量 | 500 |
| 再把全部40次setup保守计入同窗 | 1540次，余460 |
| 为发出延迟拓宽窗口 | 300+10秒请求deadline+0.2秒最大launch lag+0.02秒监督轮询 |
| 拓宽窗口业务最大数量 | 1552 |
| 再加全部setup的保守模型 | 1592次，余408，尚未减其他流量 |

拓宽窗口是规划模型，不是实际入口请求的严格上限。WAF近似计数、网络与OS调度没有此工具能保证的硬边界；正余量不等于获准执行。A0/A1之间的480秒冷却不允许另起后台probe。

模板 `wafBudget.otherRequestsPerWindow=null`、`sourceIpReviewed=false`，`approval.confirmed=false`。Zhili在私有配置中填同IP的准备/烟测/人工/背景请求预算并确认来源IP；本profile远端执行在这些输入未确认时会在DNS/登录前拒绝。保守模型减其他请求后须仍为正；这只是配置守卫，不能替代Zhili对实际余量、窗口和停止条件的复审。两组可有不同背景预算，但是否影响可比性需记录和审阅。

保留profile才能使用这一预算契约。一般旧配置不会自动继承此WAF模型；正式首轮A须从新版joint模板填写，不移除profile绕过检查。profile拒绝峰值>5、VU>8、maxIterations>2700、最低样本门槛<100或错误WAF规则。修改阶段做本地短烟测可用较小参数，但不能称其完成正式21分钟模板。

## D5：具体业务成功条件

登录及auth/me：JSON对象，userId必须是真正整数且等于fixture的护理员ID；roles必须为字符串列表且包含CAREGIVER。布尔值、浮点数、缺字段或字符串roles不能充当成功。

工作包：200+完整JSON；visit必须为对象，id/elderId/version为真正整数并与fixture完全一致，status也完全一致；tasks为对象列表、execution为对象。allowedActions如提供，必须是字符串列表。身份、越权/403、错误visit/老人/状态/版本、非JSON、坏JSON和关键结构错误均立即停发，不能只按一般1%错误容忍。

fixture可选 `expectedTaskCount` 为非负整数，提供时精确核验任务数量。新版占位fixture是SCHEDULED、expectedTaskCount=0，表示未物化执行任务，**不代表已经选定代表性业务数据**。D4需按实际服务指令/任务量和响应大小选择/冻结数据；若用有任务的IN_PROGRESS工作包，准备步骤及初始状态另行确认。工具不自动迁就错误状态或重新登录。

A每迭代1个业务GET；setup不进入业务p95/错误率。B另轮；本轮仅保留公共断言与原B超时不重试回归，并避免把签到前的冻结任务数套用到签到后的新任务。

## D5：候选指标、停止与结果解释

| 项目 | 口径 |
| --- | --- |
| 成功HTTP p95 | work-pack候选≤1000ms，nearest-rank；保留全响应p95，不挑成功样本掩盖失败 |
| 迭代p95 | 候选≤3000ms；报告all/成功迭代p95，elapsed不含调度等待，失败时可能含回收开销 |
| 业务失败率 | 非预期失败候选≤1%；包含HTTP失败，不与httpFailures重复相加 |
| 样本 | 每活动阶段/endpoint和迭代至少100；不足为INSUFFICIENT_SAMPLES；0RPS冷却为NOT_APPLICABLE |
| 自动停止 | 致命业务/认证错误立即停；连续2个独立30秒窗失败率>5%，或2个独立60秒窗迭代p95>5000ms；每窗至少20完成样本 |
| 超时/排空 | 继承D1独立总deadline与统一drain；request_deadline、stop_drain_deadline分别记录，均非业务成功 |
| 部分200 | httpPassed可能true，businessPassed=false；只读完并通过断言才是成功 |
| 发生器有效性 | 计划=发起+丢弃+停止后未调度；实际发起与错误分类/取消分别报告，不自动扩大VU或补发 |
| 少样本/缺测 | 空/低样本窗口不是健康证明；STOP、丢弃、原始结果缺失、未解释版本/数据漂移不得包装成正式对照 |

summary的执行outcome=COMPLETED只说明工具完成且无失败/丢弃，不等于目标、HPA或SLA通过。新增 `clientEvidenceStatus` 区分完整客户端样本、样本不足、负载不完整、停止和回收不完整；各阶段另有候选目标观察值。因此可以“样本完整但p95超标”，也可以“业务全成功但样本不足”。

HTTP的businessFailures包含httpFailures；cancelledRequests和requestDeadlineFailures属于其中的错误分类，不再相加。summary顶层HTTP分类包含setup以便排错；正式业务指标以逐阶段结果为准。CSV增加相应计数和JSON字符串形式errorCategories，与summary JSON同步。

`targetsAreDraft=true`不再由执行approval自动变为false：开测授权与正式性能目标验收是不同事项。无论何种客户端输出，formalAcceptance都为false。

## D5：A0/A1 可比条件与离线检查

两组保持同一应用core/visit digest、源代码组合、工具版本、入口、fixture/身份比例/任务量、阶段/速率/VU、超时与停止值、节点、requests/limits、会话/Redis/业务缓存、读路由与预热方法。只改变固定2副本与批准HPA的控制方式。runId及输出目录必须不同。A0先发生的审计增长、运行顺序和背景流量作为限制记录；不在两组之间切换FM03缓存、修改业务数据或并发发布。

manifest新增fixtureSha256、workloadSha256，不输出原始身份/访视ID；它们是声明数据/参数的一致性摘要，不能证明真实数据库或镜像已核验。D3负责应用与迁移清单，D4负责真实fixture，Zhili负责运行态取证。

离线工具：[compare_read.py](../../tools/visit_hpa/compare_read.py)、[空白条件表](../../tools/visit_hpa/compare.context.example.json)：

```text
python tools/visit_hpa/compare_read.py --a0 <私有A0结果目录> --a1 <私有A1结果目录> --context <私有已填写条件JSON> --output <新报告JSON文件>
```

输入每组需有manifest、summary、requests/iterations JSONL、UTC phases.csv、worker-cleanup。检查版本和两类摘要、独立runId、计划/实际/原始计数、丢弃/停止、100样本、指标与清理记录；缺失不会被HTTP200掩盖。context只放镜像digest、节点/资源/会话缓存/读路由/预热/数据形态引用和具名审阅声明，不放凭据。

- NOT_COMPARABLE：已知版本、fixture、负载或声明条件不同，或副本控制方式错误。
- INSUFFICIENT_CLIENT_EVIDENCE：负载未完整、STOP、丢弃、低样本、文件/指标/清理缺失或业务致命错误。
- AWAITING_RESOURCE_REVIEW：客户端条件匹配，但资源/预热/背景/发生器/监控等人工审阅未齐。
- CLIENT_AND_DECLARED_CONDITIONS_MATCH：客户端证据一致且人工声明齐全，退出0；**仅表示上述检查匹配，不表示性能达标或HPA通过**。

该工具纯读本地文件，不能认证人工声明的真实性，也不读取集群。HPA需Zhili用真实2→至少3→2和conditions/事件证明；性能收益另看同负载指标。5RPS未触发扩容时记未验证，不提速、改requests/阈值或手动scale追曲线。资源缺采或数据层停止阈值未定时，由Zhili补Z1～Z4后再决定开测。

PR55对接仍使用UTC phases.csv作为--markers；不把requests.jsonl目录传--probe-dir，不另开probe。采集覆盖21分钟及缩容/恢复，不能用1200秒截断末段。
