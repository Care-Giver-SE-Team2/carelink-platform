# 给Zhili：D1～D6已推送交接与评审入口

更新：2026-10-19。Chenyu交付。**分支已推送，草稿PR已创建，尚未合并、未开启自动合并、未部署或启用HPA。** 本文替代原578cddf/8RPS旧交接口径；首轮只做A工作包读取。

## 1. 请先看这个PR和固定验收版本

- [草稿PR #68：D1～D6读取工具与独立操作交付](https://github.com/Care-Giver-SE-Team2/carelink-platform/pull/68)。
- 分支：`codex/visit-hpa-d1-d6`；最新交接元数据以PR head为准，核心代码/104项测试与真实本地验收固定于 `9292c78d5ef4aec79c08835b40833256adaa8094`。
- [固定完整交付树](https://github.com/Care-Giver-SE-Team2/carelink-platform/tree/9292c78d5ef4aec79c08835b40833256adaa8094)；[分支最新树](https://github.com/Care-Giver-SE-Team2/carelink-platform/tree/codex/visit-hpa-d1-d6)。固定树可复核实际测试内容；最新提交仅补PR/交接元数据，没有改负载/业务代码。
- 应用组合固定 `09d6a3f9ec453906f613efd0af7f99fad4fdd8d3`；HTTP/对照引擎固定 `c5b13e2d91b9e5daa8cb536b8427fa184f60d394`，保留护理员迁移与Redis保存竞争修复。后续本地FM03/前端功能不自动加入本轮。

**评审基线是 `codex/migrate-caregiver-admission`（578cddf），不是main。** 因为冻结的应用迁移尚未合入main，以该分支为base才能只展示D1～D6工具、测试和文档，不重复护理员/Redis业务差异。此PR不会自行把应用迁入main；未来发布应用仍需另行审核迁移集成、固定最终release/digest。本轮不合并任何分支，也不直接改PR base为main。

最新远端main为dca04552f56259babe739d91dece74a73028fa9a，新增Checkov CI；没有为上传本包合并或重设应用基线。不要把其他PR（例如provider隔离的#59）当成本包或护理员迁移PR。

## 2. 你现在可以开始什么

**现在可以开始离线复审D1～D6，并继续落实云端版本升级、数据权限和Z1～Z4的准备条件。** 收到PR不等于开测放行；确认共享库历史/旧V4、获准发布、数据/保留审批、监控/容量/HPA加载恢复/来源IP预算及窗口之后，再由你独立烟测、A0/A1、恢复和出图。

我不需要全程在线或同时点击。A业务规则/fixture与工具Owner是我Chenyu；批准环境中的准备、预检、执行、云指标、恢复与结果归档由你做，可明确具名代理。共享库查询执行人与数据/审计保留批准人由你协调具名，不默认我拥有AWS/DB权限。首轮A不要求板块四补B消费者验收。

## 3. 主要入口（固定到已验证提交）

- [D6独立操作说明](https://github.com/Care-Giver-SE-Team2/carelink-platform/blob/9292c78d5ef4aec79c08835b40833256adaa8094/docs/testing/visit-hpa-operator-guide.md)：Python/离线命令、私有配置/凭据、烟测/A0/A1、STOP/异常收尾、结果/退出码和反馈；正文“未推送”是本地验收当轮状态，当前发布状态以本页/PR为准。
- [D3版本与41迁移清单/旧V4停点](https://github.com/Care-Giver-SE-Team2/carelink-platform/blob/9292c78d5ef4aec79c08835b40833256adaa8094/docs/testing/visit-hpa-release-prerequisites.md)；[固定JSON清单](https://github.com/Care-Giver-SE-Team2/carelink-platform/blob/9292c78d5ef4aec79c08835b40833256adaa8094/tools/visit_hpa/release-lock.json)。
- [D4正常数据准备、核验、白名单与保留](https://github.com/Care-Giver-SE-Team2/carelink-platform/blob/9292c78d5ef4aec79c08835b40833256adaa8094/docs/testing/visit-hpa-fixture-runbook.md)；[四组未批准正式模板](https://github.com/Care-Giver-SE-Team2/carelink-platform/blob/9292c78d5ef4aec79c08835b40833256adaa8094/tools/visit_hpa/example.joint.four-pair.json)。
- [读取预算、业务断言与两组可比性](https://github.com/Care-Giver-SE-Team2/carelink-platform/blob/9292c78d5ef4aec79c08835b40833256adaa8094/docs/testing/visit-hpa-read-contract.md)；[最终真实本地验收记录](https://github.com/Care-Giver-SE-Team2/carelink-platform/blob/9292c78d5ef4aec79c08835b40833256adaa8094/docs/testing/visit-hpa-d4-d6-local-validation.md)；[去敏证据索引](https://github.com/Care-Giver-SE-Team2/carelink-platform/blob/9292c78d5ef4aec79c08835b40833256adaa8094/docs/testing/evidence/visit-hpa-d4-d6-20261019/index.json)。
- [结果填写模板](https://github.com/Care-Giver-SE-Team2/carelink-platform/blob/9292c78d5ef4aec79c08835b40833256adaa8094/docs/testing/visit-hpa-result-template.md)。原始请求/迭代样本、真实fixture和凭据不在去敏副本，不能直接把副本当完整compare_read输入。

这些是可直接在GitHub查看的链接；不需要访问我的E盘。需要完整工具时取得该分支/固定提交，先保留你自己的工作区。负载机只需要Python3.10+标准库；不需要我的AWS凭据、Cookie或kubeconfig。

## 4. D1～D6完成和限制

| 项目 | 已做完并有本地证据 | 尚需你确认 |
| --- | --- | --- |
| D1 | socket8s、总请求10s、统一排空12s、worker启动另10s；持续慢响应/STOP/Windows原生Ctrl+C测试通过 | 接受时间语义和停止方法；整个程序退出不承诺恰好12s |
| D2 | 候选1→2→4→5，维持5，冷却480s；21分钟2700业务GET/组，max8VU，另40setup；无probe | 实际来源IP/WAF同IP背景、准备/烟测/人工请求预算、窗口与费用 |
| D3 | 应用/引擎分开固定；41迁移文件和校验；旧report证据V4检测停点；本地core28/visit5实际33条历史匹配 | 你安排具名只读人查**共享四库**/旧V4，获准部署后记录真实release、四服务及worker digest/存储会话前提 |
| D4 | 四组正常无证书注册→审批→老人关联→发布排班→未来SCHEDULED:0读取，0执行任务/1条服务说明，814～816字节；私有白名单/审计/保留步骤齐 | 接受小包代表性范围；造数前具名批准新增数据/审计增长和保留期限，确定专用对象与准备执行人 |
| D5 | 身份/visit/elder/status/version/结构/任务数、候选p95/错误率/100样本及可比性/缺测规则已固定 | 接受业务候选阈值，补资源/数据层停止阈值；HTTP200或Pod增加不等于通过 |
| D6 | 104项完整工具回归通过；真实两组各200GET+40setup零失败/丢弃；真实STOP/Ctrl+C、16CLI与3手册离线检查、回收/目录保护通过；已推送并建草稿PR | 复审PR及以上问题，落实Z1～Z4后才正式执行 |

首次本地造数用户名含大写T返回400，已只修测试标识并在新环境重跑通过，失败记录保留。当前无未解决本地失败。两轮自建资源清理、原5容器不变；业务/迁移SQL/部署未因D1～D6工具工作而改动。

本地两个pilot为1RPS×100s、5RPS×20s、0RPS×2s，没有真实2副本/HPA，也没有云端性能结论。客户端证据可比，但资源context未提供，正确输出AWAITING_RESOURCE_REVIEW / formalAcceptance=false，HPA与性能NOT_EVALUATED。不能把本机p95写成正式扩缩容收益。

## 5. 候选流量、数据和审计

正式A每组2700负载GET +40setup=2740计划入口请求，两组5480；准备/烟测/人工/背景流量另计。反馈规则2000/300s/来源IP；计划业务峰值1500，计setup和时序模型1592，名义408余量**尚未减其他流量**，不是WAF保证。模板仍默认未批准、otherRequests未填，不额外开probe，不绕入口或追压。

四组独立合成护理员/老人/计划，各白名单1个未来visit；8VU各自登录，复用少量固定visit完成读取，不准备2700条visit。执行任务0而服务说明1是合法初始状态，只代表小包。visit开始晚于整轮有效期；A0/A1冻结同一数据/形状/缓存/预热/应用配置，不在中间签到/取消/换计划。

每组另8次setup工作包GET，计划审计来源2708，两组5416，再加准备/预检/组后检查/异常。GET会写审计，客户端超时不代表服务端没写。共享数据默认KEEP_MARKED_NO_AUTO_DELETE，按白名单保留并约复核日期，不一键删记录/清Redis/repair历史。本地tmpfs销毁只是隔离环境收尾，不是共享保留审批。

## 6. 请按节点回一份Markdown，便于AI读取

| 节点 | 请回答/记录什么 |
| --- | --- |
| 复审后 | 是否接受应用09d/引擎c5、5RPS/2700GET/8VU、时间/判定契约和小包形状；如不同意给目标SHA/形状或具体缺项 |
| 发布前 | 共享四库映射、具名只读执行人/UTC/历史摘要、旧证据V4是否存在；审批后的集成/发布版本、实际release/digest、会话/存储兼容。冲突就停，不repair凑绿 |
| 共享数据准备前 | 你/代理执行人、数据/审计保留批准人、范围/日期/期限、专用对象复用或正常新建方式、core准备接口的实际edge路由；私有白名单是否核验 |
| 烟测前 | Z1～Z4是否满足：版本、RDS/Redis/节点/Pod监控与停止阈值、HPA加载恢复/CD保护、容量、来源IP/WAF预算、UTC窗口/费用/停止权 |
| 执行后 | 各组runId/固定版本/配置/结果索引、错误/丢弃/停止、资源与副本曲线、审计核对、恢复2副本/配置及保留状态；业务缺陷给我去敏复现 |

Z1～Z4的云容量/监控权限/部署/受控HPA加载恢复仍由你负责。首轮A只读取；B签到、事件消费者、FM03缓存、家属页面与账号切换另轮，不混进首轮读对照。

## 7. 为什么这次推送/PR没有部署

上传前已读取远端main dca0455的全部5个仓库工作流及webhook配置：webhook=0；CI仅push/PR到main；发布镜像/release、后端、前端和ZAP路径均限制main及指定事件；没有手动触发。本草稿PR的base是迁移分支，不命中自动CI过滤器，**不宣称GitHub CI已通过**，采用上面的104项本地证据供复审。

分支上传后目前GitHub Actions该分支无运行记录，PR保持OPEN/DRAFT、未合并、autoMerge为空。最终上传核对另记在files-2/plans推送记录。若日后改base/合入main/手动运行或修改workflow，必须另行审查，不能套用这次结论。

本文件用于任务交接，不是云端开测许可。没有共享AWS/数据库凭据、Cookie、kubeconfig或真实共享数据；Chenyu没有通过工具给你发送消息，由用户自行转发文档/PR。
