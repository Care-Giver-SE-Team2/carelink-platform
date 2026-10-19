# D4真实A业务验证与D6独立执行验收记录

日期：2026-10-19（Asia/Shanghai；证据内UTC对齐）。**D4真实本地A通过，D6本地独立执行验收通过；材料仅本地，未推送/建PR/发送给Zhili，未连接线上或启用HPA。** 固定应用09d6a3f9ec453906f613efd0af7f99fad4fdd8d3、HTTP引擎c5b13e2d91b9e5daa8cb536b8427fa184f60d394；D3/D4材料起点90844b2c5ccbf30001ae29a114a5b938e6a985a6，最终本地材料SHA见files-2/plans完成表。

## 本轮环境与变更

Docker29.4.1可用；缓存mysql:8.4 / redis:7.4-alpine，Java25.0.4直接路径，Windows11/Python3.12.7。复用固定生产core/visit JAR，摘要与D3一致，没有测试替身、业务修改、重新构建或外部下载。服务只监听127.0.0.1，独立MySQL/Redis和session namespace；清除子服务继承云配置，禁用本地事件/后台扫描。

增加local_read_acceptance.py作为A专用可复现验收入口；原local_smoke.py仍为历史A/B流程，不修改。新增四组默认未批准模板、2项离线模板测试和[Zhili独立操作说明](visit-hpa-operator-guide.md)。应用、认证、迁移SQL、部署/HPA/WAF/IAM未改。不能以本地关闭扫描推断共享环境设置；正式仍按未来数据/后台一致性审查。

## 失败保留与修正

r1在首个护理员注册返回400，未进入负载：脚本把UTC runId中大写T带入用户名，违反小写用户名规则。只修正本地造数脚本生成的标识为小写；r1真实服务就绪后失败，其自建容器/网络均已移除、原5容器不变、端口关闭。保留失败JSON与原因，未覆盖或把它计为通过。

r2从全新一次性库重新执行，12个检查点通过；本轮没有未解决的测试/业务失败。Docker在D3/D4轮的阻塞已解除，本记录不倒改历史阻塞为通过。

## D4通过依据

1. 只在空的一次性库引导初始MANAGER；4护理员正常注册（不上传证书）→MANAGER读取申请/审批→本人确认canWork；4老人正常注册，主管详情核对userId链接，指定主护理员、发布计划、读取真实排班。
2. 总计9账号、4计划、8个未来visit，白名单每人取1个；全部SCHEDULED:0、0执行任务/1条Personal care服务说明，实际UTF-8工作包814～816字节。其余4个生成visit保持不变。
3. 4个原始core登录SESSION不变即可访问visit档案与本人工作包；匿名401、跨护理员403均通过。没有B签到/任务提交或修改业务版本。
4. 四组真实观察填入私有白名单，offline ready/配置映射通过；4次业务GET+20次setup HTTP，失败/丢弃0；对应8次成功工作包审计增长，全部visit状态/版本/归属保持。
5. 实际本地core28+visit5共33条Flyway历史script/checksum/success与固定清单匹配。未查询共享库、report/notification实时历史，不能据此关闭共享库旧report证据V4停点。

D4烟测只有4样本，正常标INSUFFICIENT_SAMPLES，业务通过不等于SLA或正式性能通过。原始账号/ID映射、SQL、配置、密码、Cookie和正文留私有目录。

## D6通过依据

完整工具离线/loopback回归 **104通过、0失败/错误/跳过，79.453秒**：原18+D1 22+D2/D5 30+D3/D4 32+本轮2项。新四组正式候选模板通过无DNS/socket校验，远端未批准时在DNS/worker启动前拒绝。慢滴流、STOP、Windows原生Ctrl+C与子进程回收全部本轮重跑。

真实环境独立CLI顺序：四组刷新观察→离线配置/ready→全新目录运行→读结果/审计→下一组。两组各1RPS×100s +5RPS×20s +0RPS×2s，8VU，各200业务GET+40setup；两个活动阶段各100样本。两组都没有真实2副本或HPA，命名A0/A1仅用于客户端交接走查。

| 本地轮次 | 业务GET | setup HTTP | 失败 / 丢弃 | 完成 / 证据状态 |
| --- | ---: | ---: | --- | --- |
| d4-smoke | 4 | 20 | 0 / 0 | COMPLETED / INSUFFICIENT_SAMPLES |
| local-A0-client | 200 | 40 | 0 / 0 | COMPLETED / COMPLETE_CLIENT_SAMPLES |
| local-A1-client | 200 | 40 | 0 / 0 | COMPLETED / COMPLETE_CLIENT_SAMPLES |
| real-stop | 3 | 20 | 0 / 0 | STOPPED / STOPPED |
| real-ctrl-c | 3 | 20 | 0 / 0 | STOPPED / STOPPED |

两组每组成功读取审计增208，版本/归属/任务形状不变；两阶段HTTP p95分别为125/109ms和94/94ms，仅为本机小工作包观察值。不能把顺序差异解释为HPA收益。

真实STOP和原生Ctrl+C均在3次正常读取后停止，退出2/outcome STOPPED，回收/计数有效，失败/丢弃0；57个计划项未调度（不是补发）。监督结束观测分别31/47ms，不含最终文件I/O/进程退出；本次健康请求停止证据不能替代D1慢响应排空测试。

16项真实CLI检查通过（包含各组配置/白名单、运行/中止、离线对照、拒绝复用既有负载/对照输出，以及未批准远端拒绝）。已存在summary内容未改；拒绝路径的退出2为预期通过，不算工具故障。

离线对照clientComparable=true、无客户端差异，但资源context未提供，正确输出 **AWAITING_RESOURCE_REVIEW / formalAcceptance=false / HPA与性能NOT_EVALUATED**。没有填虚构镜像、节点或HPA声明来获得退出0。

## 审计、保留与收尾

所有轮次及准备/刷新观察共核对458条成功工作包审计。运行期间没有删除/重置行、清Redis、签到、改计划或迁移repair。白名单与附属visit一直冻结。

本次数据位于自建tmpfs数据库，全部检查结束后按本地隔离环境的明确授权销毁容器/Redis；这不等于共享库KEEP_MARKED_NO_AUTO_DELETE规则的批准或执行。共享数据保留负责人/日期仍需Zhili协调具名确认。

r2自建容器/网络已按精确ID+名字+归属标签删除；子Java/CLI/HTTP worker已收尾，13306/16379/18080/18081关闭，原5个停止容器保持原样。完整测试自身的loopback服务/进程也按各测试teardown收尾。

## 交付与仍需确认

[独立操作说明](visit-hpa-operator-guide.md)、[D3版本与迁移](visit-hpa-release-prerequisites.md)、[D4合成数据步骤](visit-hpa-fixture-runbook.md)、[四组未批准模板](../../tools/visit_hpa/example.joint.four-pair.json)、[去敏证据](evidence/visit-hpa-d4-d6-20261019/index.json)。

尚未做：推送/PR/正式回传（本轮明确禁止推送）；云端四库历史/旧V4和实际release/digest；共享数据审批；Z1～Z4；正式21分钟×2、HPA扩缩/资源曲线/性能收益。Chenyu业务Owner、Zhili批准环境执行分工不变；不需要Chenyu同步在线。

去敏副本不含原始requests/iterations或私有fixture，不能直接对其重跑compare_read；原完整样本保留在本机私有目录，或用A专用工具重新生成全新本地轮次。事实与复现限制均与云端验收分开。
