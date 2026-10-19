# D2/D5：读取预算、业务判定与可比性本地验证

日期：2026-10-19。分支：codex/visit-hpa-d1-d6，起点D1本地提交 `4df6263155ff028e19826a8bb8face3e755a7fb7`。只修改工具、模板、测试和说明；D1保留，应用/Redis业务实现/迁移/部署目录未改。本轮无网络依赖下载、CareLink/AWS请求、共享数据或HPA操作，未推送。

**结果：最终70项通过，0失败/错误/跳过。D2/D5本地交付完成，待Zhili复审。D3/D4/D6及正式GO仍开放。**

## 交付内容

- [新版joint](../../tools/visit_hpa/example.joint.json)：候选1→2→4→5、维持5、冷却480秒，21分钟，2700业务GET/组，max8VU，maxIterations2700；源IP/额外流量和approval仍未确认。
- [完整读取契约](visit-hpa-read-contract.md)：候选预算、断言、指标、停止、缺样/丢弃/缺测和两组可比条件。
- [离线比对器](../../tools/visit_hpa/compare_read.py)与[空白条件JSON](../../tools/visit_hpa/compare.context.example.json)：纯读本地文件；匹配不表示HPA或性能通过。
- [结果模板](visit-hpa-result-template.md)：复用4a41340固定版本并收窄到首轮A，所有正式结果留空。

实现入口（本次测试源码）：
- load.py:81/84/94：A profile、精确半开滚动窗口、WAF预算；:141：声明fixture/负载摘要。
- load.py:514/554：工作包及身份/首次响应的真正整数类型、结构与可选任务数断言；布尔/浮点不能充当ID/版本。
- load.py:689：逐阶段指标/缺样与冷却；:920：执行approval不等于目标验收；CSV同步错误分类/取消/总deadline计数。
- compare_read.py:19/121/149：原始样本与计数、人工同条件声明、比对状态。具体行为见契约；代码不读取集群。

## D2预算（离线推导，不是云端实测）

| 项目 | 每组/每窗候选 |
| --- | ---: |
| 业务GET、两组业务GET | 2700 / 5400 |
| 8VU登录/预检setup | 每组40 HTTP，其中8个work-pack |
| 每组/两组计划入口HTTP（未计其他流量） | 2740 / 5480 |
| 任意300秒业务计划最大数量 | 1500 |
| 名义2000限额减业务，再减setup | 500，再减40为460 |
| deadline10 + launchLag0.2 + poll0.02拓宽后的业务窗口峰值 | 1552 |
| 拓宽模型再加全部setup | 1592，余408，尚未减其他同IP请求 |

预算只依据Zhili 10.19反馈规则2000/300秒，并假设当前固定调用路径；sourceIpReviewed=false、otherRequestsPerWindow=null，未假装已经批准。WAF近似计数和OS/网络时序不由此模型保证。私有配置必须由Zhili确认其他流量预算，profile的远端执行才通过输入守卫；此守卫不代替Z1～Z4或正式窗口批准。

## 本地通过项

环境：Windows11/Python3.12.7标准库。完整70项（原18 + D1新增22 + 本轮新增30），总记录101.812秒。所有HTTP/TCP只由测试器在127.0.0.1建立，无Java/Docker或真实云环境。

| 检查 | 实际结果 |
| --- | --- |
| D2模板/边界 | 精确阶段/1260秒/2700求和，rolling窗口与独立暴力算法30组交叉核对，setup与发出延迟预算、旧8RPS/VU9/过高cap/错误规则/低样本门槛拒绝均通过 |
| 默认守卫 | 不带execute不DNS/不启动HTTP worker；源IP/额外流量未确认时拒绝远端profile；示例仍未批准 |
| D5断言 | bool/float身份和版本、roles字符串/缺失、错老人/状态/结构、可选任务数、非JSON/坏JSON拒绝；空SCHEDULED tasks合法 |
| D5指标 | 200未完成body、取消/总deadline错误分类、CSV与JSON同步、成功/全响应p95、完整样本但超目标、未完整负载、少样本和0RPS N/A区分通过 |
| A0/A1检查器 | 版本/数据/负载/同runId/资源缓存预热漂移、缺原始样本/清理/UTC标记/指标、NaN指标、缺资源审阅均不能取得匹配结论 |
| 两组缩短读取 | 回环模拟服务分别17个业务GET +10 setup HTTP，0业务失败/丢弃，峰值阶段5RPS，2VU；无重试/probe，worker回收通过 |
| 低样本诚实性 | 两组均标INSUFFICIENT_SAMPLES，冷却NOT_APPLICABLE；离线比对结果INSUFFICIENT_CLIENT_EVIDENCE，不冒充正式A0/A1 |
| 兼容/回收 | D1持续滴流、STOP、Windows原生Ctrl+C及原18项均通过；B初始任务数不误套至签到后，超时POST仍不重试 |
| CLI额外走查 | 对完全人工合成结果文件，匹配声明退出0、缺声明退出2、既有报告拒绝覆盖；均formalAcceptance=false，不是业务/云指标 |

对比器测试中构造的完整2700行文件是**纯合成测试输入**，只验证检查器分支，不作为实际负载证据发布。真正发出的本轮负载仅上述两组短回环请求，不含正式21分钟或真实服务联调。

## 失败、修正与重复轮次

1. 首轮新增26项：25项通过，1项ERROR。新增JSON指标未同步CSV列，第一次短读取结束导出时抛ValueError；不是业务或云端故障。失败日志保留本机验证目录。
2. 修正CSV列及JSON编码后，补充边界/兼容检查，新增30项全部通过（24.108秒）。
3. 首次完整70项通过（94.890秒）；最终核对把“文件缺失”诊断从不可比明确为证据不足，并补断言，再完整70项通过（101.812秒）。没有将最初错误轮写成通过。
4. 最后代码摘要与测试记录一致；文件链接、差异、进程回收另作静态核对。没有因为完成本地测试就把approval设为true。

## 阻塞与限制

- 当前D2/D5本机无未解决执行阻塞；候选负载、阈值及工具仍待Zhili复审。
- 模板placeholder fixture（SCHEDULED/任务0）不是代表性数据选择；D4的真实合成账号、任务量/响应大小、业务Owner、审计保留和预检仍未交齐。
- D3最终应用组合、迁移与共享旧V4历史，D6真实core/visit/MySQL/Redis、独立运行整包尚未完成；Linux/macOS未实机复测。
- WAF规则、云端digest/容量/监控/HPA均没有本轮实读；RDS/Redis监控和Z1～Z4归Zhili。未触发HPA不能提速/手动scale补曲线。
- 对比器检查文件一致性与人工声明，不认证真实部署或监控；匹配也不表示候选指标达标、伸缩机制通过或正式SLA。

## 证据与复现

[70项最终统计/源码摘要](evidence/visit-hpa-d2-d5-20261019/test-results.json)、[离线budget](evidence/visit-hpa-d2-d5-20261019/budget.json)、[轮次](evidence/visit-hpa-d2-d5-20261019/rounds.json)、[CLI合成文件检查](evidence/visit-hpa-d2-d5-20261019/cli-checks.json)。

[短回环A0](evidence/visit-hpa-d2-d5-20261019/A0-loopback-summary.json)、[短回环A1](evidence/visit-hpa-d2-d5-20261019/A1-loopback-summary.json)、[缺样比对结果](evidence/visit-hpa-d2-d5-20261019/loopback-comparison.json)仅为模拟服务的本地实际观察，不是云端A0/A1。

```text
python -m unittest discover -s tools/visit_hpa/tests -v
python tools/visit_hpa/load.py --config tools/visit_hpa/example.joint.json
```

前者只启动本机测试服务，后者只离线校验。不要给示例补--execute请求公网；最终云端由Zhili完成审核、私有配置和正常发布后独立执行。
