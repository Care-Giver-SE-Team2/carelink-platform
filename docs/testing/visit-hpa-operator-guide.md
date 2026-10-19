# Zhili独立执行：Visit HPA A最终操作说明（D6）

日期：2026-10-19。交付范围为A工作包读取。本文供**复审/云端条件满足后**执行，本轮仅在本机验证，不授权部署、造共享数据或启动云端实验。D4真实本地与D6独立CLI、104项工具回归已通过；实际证据以[最新记录](visit-hpa-d4-d6-local-validation.md)和files-2/plans完成表为准。

## 1. 版本和依赖先核对

- 应用固定 `09d6a3f9ec453906f613efd0af7f99fad4fdd8d3`，HTTP负载/离线对照引擎固定 `c5b13e2d91b9e5daa8cb536b8427fa184f60d394`，D3/D4材料起点 `90844b2c5ccbf30001ae29a114a5b938e6a985a6`；最终材料完整提交号在files-2/plans完成表。**本轮按要求不推送、不建PR**，不能用一个不存在的PR链接代替交付。
- 交付提交包含上述应用源码，不默认带入保全分支的FM03缓存/前端后续改动。先保留自己的工作区，再用正常方式取得/检出指定交付提交；不执行hard reset覆盖个人修改。
- 负载机只需要Python3.10+标准库（本机验收为Windows/Python3.12.7）；Linux/macOS未实机验证。不需要pip/k6/Docker/AWS CLI/kubeconfig。Docker和Java25仅用于可选的**一次性本地真实验收**。
- 板块二PR55被评审版本ade044a8bf47c97bb0017dd4445405552ce97b07；实际配置/采集工具及release/digest由你回填，不能把固定源码当成已发布镜像。

从仓库根目录运行（以下三步均离线）：

```text
python -m unittest discover -s tools/visit_hpa/tests -v
python tools/visit_hpa/release_lock.py --manifest tools/visit_hpa/release-lock.json
python tools/visit_hpa/load.py --config tools/visit_hpa/example.joint.four-pair.json
```

测试只用本机回环服务/注入故障，不连接CareLink；配置命令不带--execute，不DNS/登录。release_lock需要本地已有完整Git对象。出现缺对象、源码不符或测试失败先停并反馈，不能重新生成清单凑匹配。

## 2. 云端执行前你负责确认什么

先按[D3前提](visit-hpa-release-prerequisites.md)核对应用/四库真实历史，特别是report旧证据V4，再走正常受控发布路径、记录实际release/四服务及worker digest。共享存储/会话配置不能遗漏。旧证据V4、checksum冲突、失败迁移等未解决则停止发布。

Z1～Z4由你落实：应用/digest/迁移、RDS/Redis及节点/Pod监控与停止阈值、HPA同一Helm release的加载/恢复及CD不覆盖、刷新容量/来源IP/WAF背景预算/UTC窗口/费用和停止权。10月19日快照不是永久放行。候选5RPS可能不触发扩容，不能临时改RPS/CPU requests或手动scale来补曲线。

业务规则/fixture和断言Owner=Chenyu；批准环境的准备、预检、负载、采集、恢复/归档由你独立执行；共享库历史查询执行人和数据/审计保留审批人由你具名确认。无需Chenyu全程在线，也无需交换AWS凭据、Cookie或kubeconfig。

## 3. 私有目录、数据和配置

复制以下文件到Git目录之外的私有目录，按[D4步骤](visit-hpa-fixture-runbook.md)准备/核验四组数据：

- `tools/visit_hpa/example.joint.four-pair.json` → run-A0.json；它保持1→2→4→5/维持5/冷却480s、2700业务GET、8VU和readProfile，不删profile绕守卫。
- `fixture-whitelist.example.json` → fixture-whitelist.json；2030日期、样例ID和1000字节均为占位，必须替换全部实际观察字段。
- `compare.context.example.json` → compare-context.json；实际digest、节点/资源/会话缓存/读路由/预热/fixture引用，以及具名资源审阅声明，不能填虚构值。

run-A0：填唯一runId、同源HTTPS的coreBase/visitBase（走CloudFront/WAF）、四组actor的实际userId/visitId/elderId/status/version/expectedTaskCount=0；alias与白名单一致。core/visit跨服务共享会话须真实核验。8VU按四actor分配，每VU独立登录，不复用浏览器Cookie、不退出或切账号。

正式开测前才在私有配置中回填：
- approval.confirmed、owner、environment、windowUtc、deploymentRef、allowedOrigins精确实际源；这些字段是执行人声明，工具不会验证人类授权或部署状态。
- wafBudget.sourceIpReviewed=true与实际otherRequestsPerWindow；5RPS保守模型1592/300s，未减其他请求；准备/烟测/浏览器/同IP同学/手工核验均计入其他预算。不默认额外probe，不绕WAF。保持候选请求/停止/样本门槛；若另审改变参数，完整记录并重新验证两组可比性。

每个账号分别使用HPA_CG1_USERNAME/HPA_CG1_PASSWORD至HPA_CG4_USERNAME/HPA_CG4_PASSWORD环境变量。不要把真实值写入命令历史、Git、交接文档或日志。PowerShell可在**负载运行的同一个终端**逐一输入：

```powershell
foreach ($n in 1..4) {
    Set-Item -LiteralPath ("Env:HPA_CG{0}_USERNAME" -f $n) -Value (Read-Host "Synthetic caregiver $n username")
    $secretInput = Read-Host "Synthetic caregiver $n password" -AsSecureString
    $plainInput = [System.Net.NetworkCredential]::new('', $secretInput).Password
    Set-Item -LiteralPath ("Env:HPA_CG{0}_PASSWORD" -f $n) -Value $plainInput
    Remove-Variable secretInput, plainInput
}
```

此代码只设置本进程环境，不登录。不要在共享终端显示环境变量。4组可用不同密码，不假设使用群演示密码。Linux/macOS用相应安全提示或既有私有Secret注入方式设置同名变量，别把密码写进shell参数。

## 4. 预检 → 烟测 → A0/A1

先设置私有路径变量；PowerShell示例路径由你替换为本机真实目录，不执行对共享数据的清理：

```powershell
$privateRoot = 'D:\private\visit-hpa-reviewed-run'
$configA0 = Join-Path $privateRoot 'run-A0.json'
$configA1 = Join-Path $privateRoot 'run-A1.json'
$whitelist = Join-Path $privateRoot 'fixture-whitelist.json'
$runA0 = Join-Path $privateRoot 'results-A0-unique'
$runA1 = Join-Path $privateRoot 'results-A1-unique'
```

目录必须私有且已有，两个输出目录必须**尚不存在**。按真实路径填，已有结果只读保留。正式每组运行前15分钟内刷新全部四组观察，确认未来SCHEDULED:0、0执行任务/1服务说明、归属/计划不变，并登记responseBytes。只刷新观察时间，不能靠改预期状态掩盖变化。

```powershell
python tools/visit_hpa/load.py --config "$configA0"
python tools/visit_hpa/fixture_guard.py --whitelist "$whitelist" --config "$configA0" --ready
```

--ready通过仍不代表线上权限/资源条件通过。A1运行前对$configA1重复两条命令。私有A1由A0同参数复制，仅换runId/独立输出目录和正确组别元信息；应用digest、工具、数据/负载/缓存/预热等必须相同。不要两组之间发布、造新夹具、签到或改变偏好/计划。

先从四组正式配置另存烟测配置：新runId/新目录、maxVUs=4、maxIterations=4、phases仅1RPS×4秒；保留全部4个actors、readProfile、业务断言/时限/样本下限；对全部四actor做真实预检并运行1～数次读取。烟测也计入口/审计预算，不把其缺样结论当SLA；不直接修改正式A0/A1文件来跑烟测。

你安排采集覆盖预检、21分钟和缩容/恢复，建议每组1800秒起并按实际结束延长；不使用旧1200秒截断模板。正式A0固定2副本，确认后：

```powershell
python tools/visit_hpa/load.py --config "$configA0" --output "$runA0" --execute
```

保存A0完整结果、核对业务状态/审计；你沿已批准Helm路径加载A1 HPA配置，完成同条件核对和预检后：

```powershell
python tools/visit_hpa/load.py --config "$configA1" --output "$runA1" --execute
```

不要同时运行两组，不私自追压、自动补发、重试或增加probe。client COMPLETED不代表HPA或性能通过。A1停压后由你观察HPA缩回2、沿同一受控路径恢复实验配置，复核2/2 Ready、镜像/入口和正常CD；负载工具不会操作集群。

## 5. STOP、Ctrl+C和异常收尾

正常停止：运行终端按Ctrl+C一次；或在第二终端把$activeRun填成本次**实际输出目录**后创建STOP：

```powershell
$activeRun = 'D:\private\visit-hpa-reviewed-run\results-A0-unique'
if (-not (Test-Path -LiteralPath (Join-Path $activeRun 'manifest.json'))) { throw 'Verify this run output first' }
New-Item -ItemType File -Path (Join-Path $activeRun 'STOP')
```

不预先创建输出目录；输出目录须由load.py创建。STOP仅停止新业务请求并统一排空，不注销账号、不撤销服务端已写审计、不恢复HPA。请求总deadline10s、socket inactivity8s、停止排空12s；worker启动另10s，监督轮询20ms与每次回收grace0.5s，最终文件I/O/OS可能增加退出耗时。不是“按STOP后恰好12秒整个程序必退出”的硬承诺。慢响应/原生Ctrl+C的实际边界见D1记录与本轮测试。

必须看summary和worker-cleanup，不能只看终端停止。正常STOP通常退出2且outcome=STOPPED，notScheduledAfterStop非零；这属于**被中止的实验**，不是通过或自动无效数据删除。保留文件和停因，下一轮另用runId/目录，不恢复旧轮接着跑。

若明显超过边界且没有完整summary：先核对本轮worker-pids.json、CLI PID、完整命令/路径及所属run；仅终止已确认属于此轮的CLI及子进程。Windows可用Get-CimInstance Win32_Process查看完整命令/父子PID，核对后用Stop-Process -Id 实际已核对PID；Linux/macOS用ps -p 实际PID -o pid,ppid,args核对后再kill -TERM 实际PID。PID可能复用，不能只凭旧文件或进程名。不要批量kill Python/Java、prune容器或删目录。缺summary/回收失败/硬杀标INCOMPLETE，反馈去敏证据；即便客户端停住，服务端GET也可能已写审计，按Owner核对。HPA/服务资源停止条件由你观察，触发时应立即停负载并按受控路径恢复。

## 6. 结果文件、错误与退出码

| 文件/状态 | 怎么看 |
| --- | --- |
| manifest.json | 应用源码/引擎文件SHA、输入配置/fixture/workload摘要、origin、VU/阶段、时限/候选阈值；不证明实际部署 |
| requests.jsonl / iterations.jsonl | 原始逐请求/迭代成功与失败、时间/阶段/错误；setup独立，业务成功不能只看200 |
| summary.json / summary.csv | outcome、计数恒等式、失败/丢弃/未调度、逐阶段p95/错误率与样本；失败分类是子集，不重复相加 |
| phases.csv | UTC阶段标记；给PR55 report.py的--markers，不把requests.jsonl目录传--probe-dir |
| worker-pids.json / worker-cleanup.json | 本轮子进程归属、已回收与管道线程结束；缺失/不完整不能交付为有效完成 |
| 退出0 | load：COMPLETED且无失败/丢弃；不等于达标/HPA通过。compare：仅客户端+人工声明一致，不证明性能 |
| 退出2 | 配置拒绝、STOP、失败/丢弃或证据不足等；查看outcome/stopReasons和文件，不能一律当程序崩溃 |
| HTTP401/403、wrong_*、non_json等 | 立即停并核身份/权限/夹具/正文；403还须由你区分WAF，不能当后端吞吐 |
| request_deadline / stop_drain_deadline | 未完成业务断言，按失败/取消分类；200响应头到达也不等于业务成功 |
| transport_error / server_http | 非成功响应，保留真实失败率/停窗结果；不自动重试 |
| INSUFFICIENT_SAMPLES / NOT_APPLICABLE | 活动阶段少于100样本不能判目标；0RPS冷却无样本正常，不能当零延迟 |
| INCOMPLETE_LOAD / STOPPED / INCOMPLETE | 丢弃/中止/回收缺口，保留问题；不能拼成一组完整对照 |

离线对照：

```powershell
python tools/visit_hpa/compare_read.py --a0 "$runA0" --a1 "$runA1" --context (Join-Path $privateRoot 'compare-context.json') --output (Join-Path $privateRoot 'comparison-new.json')
```

客户端相同但资源/背景/预热声明未齐是AWAITING_RESOURCE_REVIEW；版本/数据/负载不同是NOT_COMPARABLE；缺样/文件/回收是INSUFFICIENT_CLIENT_EVIDENCE。即使CLIENT_AND_DECLARED_CONDITIONS_MATCH，也仍formalAcceptance=false，HPA机制和性能收益需分别用你采集的实际资源/副本曲线与业务结果评审。本地两个CLI pilot没有HPA，绝不填成扩缩容成功。

## 7. 结果回传与数据保留

按[结果模板](visit-hpa-result-template.md)回填：固定应用/工具/配置、实际release/digest、各runId/UTC与文件索引、白名单/形状的去敏证明、实际请求/错误/丢弃/停因、监控缺口、HPA扩缩与恢复、审计核对和保留状态。逐阶段p95保留样本数，不挑成功区间。共享库原始fixture、Cookie/密码、完整业务正文和Secret不入Git或反馈ZIP。

数据默认按D4的KEEP_MARKED_NO_AUTO_DELETE保留并登记复核日期；共享对象/审计不一键删除。仅一次性本地验证工具会在所有检查后销毁其自建tmpfs DB/Redis，这是已授权隔离环境收尾，与共享数据保留分开。业务工具/本人代码问题附去敏复现给Chenyu；数据权限、云监控、配置恢复由你协调。没有要求双方同步点击。

## 8. 可选：本地复现D4→D6

只用固定缓存生产JAR、Java25和已缓存mysql:8.4/redis:7.4-alpine。下面是本机一次性复现，不是远端命令：

```text
python tools/visit_hpa/local_read_acceptance.py --core-jar /local/core.jar --visit-jar /local/visit.jar --java /local/jdk25/bin/java --output /private/new-local-run --include-handoff-checks
```

JAR摘要必须与固定D3构建证据相同；工具拒绝不匹配的JAR和已占端口/旧输出。只绑定127.0.0.1:13306/16379/18080/18081，不拉镜像、清继承云配置、禁用本地事件/后台扫描；只在空的一次性库引导初始MANAGER，其余4护理员与4老人、审批/归属/计划/排班全部走正常HTTP。数据保持未来SCHEDULED:0，先D4烟测成功才继续D6两组各200读取（每活动阶段100样本），再测真实STOP/Ctrl+C、目录拒绝与离线对照。没有真实HPA/两副本，不替代正式21分钟模板。

只清理精确ID/名称/归属标签匹配的自建容器网络及本轮子进程，核对原容器快照和端口关闭。私有配置/日志/SQL和真实映射留本机，不自动导出。既有local_smoke.py包含B签到，用于历史功能验证；首轮A使用本工具，不混写入场景。
