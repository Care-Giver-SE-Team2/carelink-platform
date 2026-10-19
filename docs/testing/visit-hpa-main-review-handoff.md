# 给Zhili：最新main集成候选、PR #71和下一节点交接

更新：2026-10-20。Chenyu／visit业务、fixture与读取工具Owner。

**最新main本地集成候选已推送，新增[草稿PR #71，目标main](https://github.com/Care-Giver-SE-Team2/carelink-platform/pull/71)。你反馈的应用侧最小补交已完成本地部分；共享四库兼容尚待真实历史，正式发布与云端A0/A1仍未放行。**

PR #68保持原base、head和草稿状态，作为已接受D1～D6的历史交付；没有重定向、合并或追加提交。C05独立分支保全，迟到退出/匿名响应Cookie与前端账号切换没有在本候选中修复。

## 1. 这次交给你的是什么

| 对象 | 本次固定版本／用途 |
| --- | --- |
| 新评审PR | [#71](https://github.com/Care-Giver-SE-Team2/carelink-platform/pull/71)，`codex/visit-hpa-main-integration` → `main`，OPEN/DRAFT；无自动合并 |
| 已集成main基线 | `bfad2c7a3a8bbd03123389f9c6be7688b1864ce4`，包含PR69和PR70；比你反馈时的56bb快照更新 |
| 实测应用输入提交APP_SHA | `65b9c6ac2d3b6fbb0882b3418dd0be377cb3394b` |
| 本地验收文档／模板／证据提交 | `dcb628c8a20c0648c8d86c9080726d9fcc0136dd`；后续交接文档提交不改变应用输入 |
| 已接受HTTP／对照引擎 | `c5b13e2d91b9e5daa8cb536b8427fa184f60d394`，引擎文件未改写 |
| 实际发布版本／镜像 | **尚无本次发布记录**。获准发布后，由你记录release SHA、四服务与worker digest、Helm revision及UTC；应用输入SHA和本地JAR摘要不代替实际部署标识 |

**旧09d锁只证明旧应用，不用于批准这个候选。** 新`candidate-release-lock.json`、候选配置和白名单均绑定65b应用输入。新A0和A1须采用同一个经核准候选；不能拼接旧09d A0与新候选A1。若合并/后续变更改变应用输入，须核对差异、重新固定清单及对应证据，不能只改SHA。

## 2. 你的四项应用侧要求，现在的回应

| 你要求的补交 | 已完成 | 仍需什么 |
| --- | --- | --- |
| 最新main集成候选 | 从bfad主线独立集成，不回退PR69/70护理员功能；补回必要report证据链和原子Redis保存 | 请评审PR71并确认候选是否合适；若你所见main再更新，给出SHA和相关变更 |
| V26/V27、report V5处理意图 | 保留main的core V26证书、V27注册审批、V28工作设置原文件；保留report V4 task dispatch，前向追加证据V5及生产/消费/查询链 | 实际四库业务及平台历史、schema与配置；板块四确认report/worker配套版本和消费者契约 |
| 四组正常fixture＋两Session | 两批全新真实MySQL/Redis环境，各4组正常无证书注册审批与排班、8个独立Session，跨2 core＋2 visit并发读取/鉴权/注销验证 | 共享环境批准、具名数据执行人与保留批准人、实际edge路由核验；本地对象不能复制成共享白名单 |
| 对应锁、迁移与前提 | 新应用从源码构建4服务并记实际JAR摘要；独立候选锁列41条业务迁移、17条平台迁移；已补校验器和新候选模板 | 收到真实历史后，我给逐库兼容结论；源码锁匹配不代表平台历史/schema或部署已批准 |

Redis先在未修复main做探测，三个残缺metadata场景抛异常，再恢复既有五文件原子修复。保留CSRF、namespace/序列化/Cookie格式和真实Redis错误处理；本轮没有顺带修改C05的Cookie或请求缓存。

## 3. 本地证据与结论边界

| 验证 | 已有结果 |
| --- | --- |
| Java组合验证 | 150项相关集成、2034项单元通过，无失败/错误/跳过；另扩展并复跑1项既有历史事实保留用例，不重复计为新增150项 |
| 工具 | 原104项通过，最终新候选锁8项通过，共112个不同用例；110项组合轮与最终8项的重复部分不重复计数 |
| 两批四组真实业务 | 每批正常注册→审批→关联→发布排班；8独立Session跨4实例可读，并发读取通过、跨actor拒绝、注销一个不影响另一个 |
| 两批真实Redis并发 | 三种关键交错各20次/批＋串行20次/批，共120次关键交错＋40次对照；无目标500、残缺键、退出/过期复活；收尾通过 |
| 迁移 | 空库和main合法前缀可用，原账号/角色/护理员/证书/计划事实保留；旧core编号和旧report证据V4被正确拒绝 |

真实本地小烟测每批只有4业务GET＋20 setup；没有声称在新候选上跑完云端21分钟A0/A1或观察HPA。旧基线两组200 GET、STOP和Ctrl+C证据仍是D1～D6历史证据，引擎未变；不把旧应用结果冒充新应用结果。

本轮未连接CareLink线上、AWS或共享数据库。未测真实云队列传输/模型、完整前端页面和跨标签切换；C05仍独立待处理。首轮A固定会话读取不夹带退出/切换、签到B或家属缓存实验。共享历史未收到，不写成“迁移已在云端兼容”。

## 4. 你现在可以开始什么

**现在可以开始复审PR71及其本地证据，同时继续共享历史只读导出、云端访问/监控、容量和Helm实验加载/恢复的准备。** 不需要等我全程在线，也不需要两人同时操作。

你／具名授权代理负责获准环境中的查询、发布配置核验、数据准备、烟测、A0/A1、停止、恢复和归档；我负责应用/工具/业务断言与fixture规则，并在收到历史后给兼容判断、收到缺陷后修复。共享数据与审计保留须团队具名批准，不默认由我或AWS账号持有人自动批准。

暂时不要把“PR已推送”当作部署许可。先处理下列历史与版本节点，再经正常发布评审；保持现有自动CD，不为实验关闭或绕过它。窗口内协调避免发布，若有发布污染对照就停止并标记不可比。

## 5. 发布前最关键的待回传内容

### 5.1 真实四库历史：由板块二协调只读执行人导出

对core、visit、report、notification各自实际连接的database/schema，分别回传：

- 服务→实际库/schema映射；具名执行人、UTC、采集范围和来源。
- **完整有序的`flyway_schema_history`和`flyway_platform_history`原始导出**，不能只发最高版本。包括installed_rank、version、description、type、script、checksum、success、installed_on；必要时installed_by。
- 保留baseline、NULL、负checksum、失败/特殊行和原始类型；若规范化0/1为true/false，保留原始导出与转换记录，不按字符串真假值猜测。
- 实际Flyway版本、locations/target/baseline/ignore/outOfOrder等配置，以及相关表/列/约束的只读摘要。
- 建议附manifest：service、database/schema、capturedAtUtc、capturedBy、文件名、SHA256和配置来源。内部附件可保留环境信息，公开Git仅放去敏结论，不含凭据或真实业务资料。

| 实际谱系 | 处理意图 |
| --- | --- |
| core到V25，或V26证书/V27审批且全历史匹配 | 本地已证实可行；真实业务/平台历史和schema仍要核验 |
| core V26审批／旧V26-V27顺序 | **STOP直接升级**；追加版本不能消除旧checksum冲突，另行评审兼容路线 |
| report V4 task dispatch | 保留V4，再追加证据V5；本地已验证相关业务和worker兼容 |
| report V4旧evidence projection | **STOP直接升级**；不能把本地拒绝当成已解决共享旧库 |
| 未知、失败或特殊历史 | 人工复核；不repair、改历史、清库或静默丢弃行求绿 |

**我收到这些资料后，回传逐库“匹配／待升级／停止／人工复核”表。** 当前校验器对规范化业务历史做离线一致性检查，结果不代替平台历史、实际schema和部署审批。

### 5.2 应用与板块四的确认

- 请确认是否采用本次候选，是否存在更新的main或另一个被选定候选。实际发布前后分别记批准SHA、CI结果与真实digest，不用前端version.json推断后端。
- 板块四确认report主线V4、证据V5、`VisitEvidenceUpdated`及`VisitClosed`消费者/worker配套版本，避免只更新生产者或消费者之一。首轮A不要求补做B写入/真实队列全链验收，但报告功能不能因此回退。
- 板块二确认会话Redis namespace/依赖与序列化、Lua执行权限/连接拓扑、入口Cookie名称及Path/Domain/SameSite/Secure/HttpOnly和CSRF转发；不需要Cookie值、Redis全库数据或凭据。

## 6. 数据、流量与验收：沿用已接受口径

| 项目 | 已接受候选／待落实项 |
| --- | --- |
| 业务与数据Owner | Chenyu；共享准备执行人、数据/审计批准人、日期/复核日由你协调具名 |
| fixture | 4组合成护理员/老人/计划，各白名单一个未来SCHEDULED:0 visit；执行任务0、非空服务说明1，约814～816字节小包；自动生成的其他visit也记私有清单 |
| 有效期与保留 | visit开始晚于整个正式窗口，建议至少2小时余量且不跨业务日期；默认KEEP_MARKED_NO_AUTO_DELETE，期限须批准；不自动清Redis或删共享记录 |
| 读取计划 | 8 VU；1→2→4→5 RPS、维持5、冷却480秒；21分钟、2700业务GET/组，另40 setup，共2740；两组5480入口请求，准备/烟测/人工/背景另计 |
| WAF | 已反馈2000/300秒/来源IP；模型1592，名义余量408尚未扣背景。须确认实际发生器IP和同IP其他请求，不增加probe、重试或追赶流量 |
| 审计 | 每组2700＋8次setup工作包读取＝2708个计划审计来源，两组5416，再加准备/核验/异常；超时也可能写入，以服务端核对为准 |
| 时限 | socket8秒、总请求10秒、共享排空12秒、worker启动另10秒；总进程退出含额外回收开销。STOP与Ctrl+C按已接受操作手册，不用强杀伪造正常排空 |
| 业务/性能 | 核对身份/角色、visit/elder/status/version、结构与任务数；成功HTTP p95≤1000ms、iteration p95≤3000ms、失败≤1%，有效阶段样本≥100。缺样/丢弃/未调度如实记录 |
| 停止 | 身份错配、401/403、非JSON等关键断言立即停；30秒失败率>5%两窗，或60秒iteration p95>5000ms两窗且满足最小样本，按工具规则停；云资源/成本阈值由你落实 |
| 两组可比 | 相同应用/digest、fixture/包形状、缓存/预热、配置、节点和requests；A0固定2，A1仅切换已评审HPA min2/max4/CPU70%；组间不签到/改计划/取消 |

HPA成功须有真实2→至少3→2与conditions/events；性能收益另看同负载p95/错误率/吞吐。5RPS未触发就记录未验证，不手动scale补图或擅自升压。业务通过、负载有效、对照可比、HPA机制、性能收益和恢复分开判定。

## 7. 请按节点回一份Markdown＋附件索引

以下沿用计划R01～R12，方便逐项对照；你可以一次回复已知项，其余标待补，不必为等全部材料而延迟已有反馈。

| 最晚节点／编号 | 请回传的内容 | 我收到后做什么 |
| --- | --- | --- |
| 复审本候选 R01 | PR71是否接受、当前main/SHA/CI是否有新变化，具体异议或缺项 | 调整候选或补应用证据，不重复重做已接受D1～D6 |
| 共享升级前 R02～R04 | 四库映射、完整业务与平台历史、schema/Flyway配置/谱系，具名只读人与UTC | 给逐库兼容表；遇旧core或report V4冲突另列兼容方案 |
| 选定发布前后 R05～R06 | 批准应用版本、板块四配套确认、Redis/会话/入口兼容；实际发布后给4服务/worker digest、Helm revision/UTC | 核最终源码/锁/部署与业务前提是否对应 |
| 共享造数前 R07 | 准备执行人、数据/审计批准人、范围/时区/有效期/保留期限与复核日、专用对象复用或正常新建方式 | 确认规则与白名单口径；必要时修准备步骤 |
| 烟测前 R08 | 真实edge方法/路由、Cookie/CSRF转发、私有fixture核验，所有凭据留私有 | 解释断言或修工具/业务，不索取账号Cookie |
| 云测窗口前 R09～R11 | 获准集群路径与新容量/Pod/metrics/HPA/worker快照；RDS/Redis可读指标和阈值；Helm加载/恢复与CD协调；UTC窗口、源IP/背景预算、费用上限/停止人 | 复核业务与流量条件；不会代你加载HPA或部署 |
| 执行后 R12 | runId/UTC/真实版本配置、原始请求/iteration、Pod/HPA/节点/数据层曲线、审计、停因/丢弃、恢复2/2和配置/公网登录、数据保留状态 | 核业务/工具有效性并整理个人6b结论；HPA图表由你提供 |

Z1版本/数据、Z2监控/停止、Z3受控Helm加载/恢复/CD、Z4容量/窗口/预算仍由你协调落实。采集至少1800秒/组并覆盖恢复；缺监控或共享批准时不要正式开测。首轮A不混KEDA、Active-Standby、B签到、FM03缓存或C05验收。

## 8. 可直接查看的版本与操作入口

新候选：

- [应用集成说明与本地命令（固定验收提交）](https://github.com/Care-Giver-SE-Team2/carelink-platform/blob/dcb628c8a20c0648c8d86c9080726d9fcc0136dd/docs/testing/visit-hpa-main-integration.md)。
- [候选应用锁](https://github.com/Care-Giver-SE-Team2/carelink-platform/blob/dcb628c8a20c0648c8d86c9080726d9fcc0136dd/tools/visit_hpa/candidate-release-lock.json)、[新四组模板](https://github.com/Care-Giver-SE-Team2/carelink-platform/blob/dcb628c8a20c0648c8d86c9080726d9fcc0136dd/tools/visit_hpa/example.joint.main-candidate.json)、[新白名单示例](https://github.com/Care-Giver-SE-Team2/carelink-platform/blob/dcb628c8a20c0648c8d86c9080726d9fcc0136dd/tools/visit_hpa/fixture-whitelist.main-candidate.example.json)。全是占位且未批准，不是可直接运行的共享配置。
- [去敏本地证据](https://github.com/Care-Giver-SE-Team2/carelink-platform/tree/dcb628c8a20c0648c8d86c9080726d9fcc0136dd/docs/testing/evidence/visit-hpa-main-integration-20261020)。原始凭据、Cookie、日志和实际本地fixture保持私有。

在包含APP和main基线Git对象的完整克隆中，离线校验已签入候选锁：

```text
python tools/visit_hpa/candidate_lock.py --manifest tools/visit_hpa/candidate-release-lock.json
python tools/visit_hpa/candidate_lock.py --manifest tools/visit_hpa/candidate-release-lock.json --history <私有四库规范化业务历史JSON>
```

后一命令不连接数据库，不处理平台历史审批；每库须`completeHistory:true`与有序整数installed_rank，结构见固定集成说明。匹配输出仍标shared/schema/platform待核，不能据此启动升级。不要把反馈包的工具副本当完整应用Git克隆。

已接受的D1～D6操作章节继续引用历史固定版本，**其中09d应用锁／旧模板必须用上面新候选入口替代**：

- [独立操作、STOP/Ctrl+C、退出码与结果](https://github.com/Care-Giver-SE-Team2/carelink-platform/blob/9292c78d5ef4aec79c08835b40833256adaa8094/docs/testing/visit-hpa-operator-guide.md)。
- [正常准备／核验／保留步骤](https://github.com/Care-Giver-SE-Team2/carelink-platform/blob/9292c78d5ef4aec79c08835b40833256adaa8094/docs/testing/visit-hpa-fixture-runbook.md)。
- [读取契约、WAF与可比性](https://github.com/Care-Giver-SE-Team2/carelink-platform/blob/9292c78d5ef4aec79c08835b40833256adaa8094/docs/testing/visit-hpa-read-contract.md)、[结果模板](https://github.com/Care-Giver-SE-Team2/carelink-platform/blob/9292c78d5ef4aec79c08835b40833256adaa8094/docs/testing/visit-hpa-result-template.md)。

正式运行前仍要完成新候选的实际发布、私有白名单/审批/源IP等核验；此处只给离线入口，不授权执行云端。Python工具不需要共享AWS凭据、Cookie或kubeconfig。

## 9. 推送／PR和部署的区别：本次核验结果

创建PR前已只读核对远端main bfad的5个仓库工作流、当前仓库webhook（0）及发布开关；分支没有修改workflow、Helm、infra或发布脚本。

- **分支推送不发布；本次PR指向main，因此会启动CI。** 草稿本身不是阻止部署的依据，依据是工作流的main和事件守卫。
- CI镜像发布/release限main push或手动事件；Deploy staging进一步拒绝上游pull_request事件；前端发布依赖获准的部署链。没有手动dispatch。
- 当前后端/前端自动发布开关均为true，保持原样。因此本次只提交评审，不合并、不启用自动合并、不部署。
- PR71的CI正在产生检查记录，准确结论见[PR检查](https://github.com/Care-Giver-SE-Team2/carelink-platform/pull/71/checks)；不得沿用PR68“无CI”描述，也不得把本地通过写成GitHub CI通过。

共享历史、监控/容量、HPA加载恢复、数据审批和窗口仍是开放项。请按第7节节点回一份可更新文档，出现业务缺陷时附去敏最小复现。本文已提供到GitHub PR，Chenyu没有用工具向你发送聊天消息，由用户自行转发提醒。
