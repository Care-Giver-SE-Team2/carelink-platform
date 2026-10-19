# D1：请求总时限与停止排空本地验证

历史版本说明：本页40项/源码行号对应D1固定提交4df6263155ff028e19826a8bb8face3e755a7fb7；后续D2/D5改动后的70项组合回归见[D2/D5记录](visit-hpa-d2-d5-local-validation.md)。原失败与去敏证据保留，不以新结果改写旧轮次。

日期：2026-10-19。范围：仅 visit_hpa 的 Python 工具、测试、时间字段和相应说明。基线为已评审的 `578cddf1926fa23bd3127bc56f877e7d43334072`；独立分支 `codex/visit-hpa-d1-d6`。原 followup 分支及其缓存/前端提交保留。应用/Redis修复/迁移/部署源码未改，未联网或推送。

**结果：最终 40 项通过、0 失败、0 错误、0 跳过。只代表本机 loopback/CLI 工具验证；D1 待 Zhili 复审，D2～D6 和正式云端 GO 未完成。**

## 实现与真实时间口径

- 每 VU 一个持久 HTTP 子进程，私有管道保留本 VU Cookie/CSRF；主进程监督请求。与仅对 future 设置等待超时不同，到界限会结束本轮拥有的 HTTP 进程，不留下阻塞读取继续工作，也不补建会话/重试。
- `socketTimeoutSeconds`：网络操作 timeout；`requestDeadlineSeconds`：从 HTTP 准入到完整响应、解析、断言及 IPC 完成的总监督预算；`stopDrainSeconds`：从首次停止检测或最终排空开始的所有在途请求共享预算。示例分别 8/10/12 秒，值仍需正式复审。
- 工作进程首次启动另有 10 秒预算，发生在 HTTP 准入前，单独受 STOP/Ctrl+C 控制，不计入 HTTP p95。该区分来自首轮失败后的修正。
- 监督轮询 20ms，回收每步 grace 0.5s；终止、必要时 kill、管道线程退出、落盘及 OS 调度会增加耗时。没有“配置多少秒，整个程序就必然在多少秒内退出”的承诺。
- STOP/Ctrl+C 被观察后关闭新准入；已经准入的请求仍可能到达或在服务器提交。停止客户端不能撤销审计或 POST，模糊写入仍需核对。
- `request_deadline`、`stop_drain_deadline`、`http_worker_exited` 等分别记录。部分 200 可为 HTTP通过、业务失败；失败迭代包含被取消请求，不能重复相加。
- `worker-pids.json`、`worker-cleanup.json` 记录本轮子进程及回收；summary 包括 timing、剩余在途、accountingValid。父进程异常被杀时，子进程由私有输入管道 EOF 看门线程退出；缺失 summary 解释为 INCOMPLETE。
- 旧 `timeoutSeconds` 仅兼容为 socket 值，离线/manifest 输出弃用提示；新旧 socket 值冲突时拒绝。移除把 socket 值称作 maximumRequestSeconds 的字段。

实现入口（本次测试源码）：
- `tools/visit_hpa/load.py:64`：时间配置与兼容；`:169`：停止/共享排空；`:242`：进程和管道回收；`:323`：单请求监督。
- `load.py:461`：子进程内业务断言；`:479`：HTTP与EOF看门；`:618`：调度/结果落盘。
- [新增测试](../../tools/visit_hpa/tests/test_deadlines.py)、[原工具回归](../../tools/visit_hpa/tests/test_load.py)、[完整时间/停止说明](../../tools/visit_hpa/README.md)。

## 本机执行与通过项

环境：Windows 11 build 26200，Python 3.12.7，标准库，无安装/镜像下载。只建立 127.0.0.1 临时 HTTP/TCP 服务、虚构身份、私有临时文件和本轮进程；未启动 Docker/Java、未请求 CareLink 或 AWS。

| 检查 | 实际结果 |
| --- | --- |
| 最终完整回归 | 40/40，84.000秒；原18项 + 新22项。新用例含subtest/重复轮次，不把它们另加成独立测试数 |
| 旧问题最小复现 | 网络 timeout=100ms，服务每30ms返回字节，完整请求约437ms仍成功；证明旧 socket 语义不是总 deadline |
| 持续滴流 | 3轮独立CLI；socket=150ms、每30ms返回字节、总deadline=500ms；观察触发500～516ms，200响应未读完按业务失败 |
| STOP在途 | 3轮独立CLI，排空=300ms；测试器触发STOP至进程退出422～453ms，全部回收、无新准入 |
| 原生Ctrl+C在途 | 3轮Windows真实CTRL_C_EVENT，排空=300ms；注入开始至退出640～656ms，均记录operator_interrupt并回收。此耗时含事件辅助进程 |
| 多VU排空 | 3个同时在途请求，共用300ms排空预算；STOP至退出约391ms，不逐VU叠加 |
| 其他停止 | 登录/预检阶段STOP与Ctrl+C、worker启动中STOP、冷却0RPS、重复停止、停止/完成竞争3轮均通过 |
| 其他阻塞 | 无首字节、不结束body、chunked滴流、错误长度、TLS握手阻塞通过；DNS/connect/断言阻塞以临时工具副本故障注入验证，不做外部DNS/HTTPS |
| 回收与不完整结果 | worker崩溃失败关闭；父进程硬终止后worker退出且summary缺失，正确解释INCOMPLETE；正常/停止结果无遗留worker |
| 会话/发生器 | 2 VU、5个业务读取通过，setup共10 HTTP、2次独立登录，无额外重新登录、失败、丢弃；只说明本地小样本 |
| 旧契约 | 身份/CSRF/Cookie、错数据/重定向/403/坏格式拒绝、统计窗口、样本不足、发生器丢弃、超时POST不重试均通过；8MiB响应上限和去敏亦通过 |
| 示例CLI | local和joint无--execute离线校验通过，均输出socket8/request10/drain12；joint仍为历史3780 GET，D2尚未替换 |

数值为本机实际观察，不是正式 SLA 或跨机器硬上限；健康负载小样本不是对云端发生器容量的校准。

## 失败与修正（保留，不覆盖）

1. 修改监督模型后，原有18项先单独通过。
2. 首次新增18项运行出现 **12条失败断言 + 1条错误**（subtest条目计数，不是13个独立测试方法）。原因是把500ms请求deadline也当作Windows子进程启动预算，部分测试还没发HTTP就worker_start_deadline；一个结果断言随后因没有业务样本而报错。不是云端或业务服务故障。修正为独立10秒bootstrap预算；也收敛测试服务对主动断连的预期错误输出。
3. 修正后的18项新增测试全部通过；再补启动预算/启动中STOP、健康多VU不重复登录、响应大小4项，最终与原18项合跑40项全部通过。
4. 原失败日志保留本机私有验证目录；此处提交去敏统计和最终计时，没有拼接为“首轮即通过”。

## 阻塞、限制与下一步

- 当前 D1 本机执行无未解决阻塞。Linux/macOS未实机复测；Windows原生Ctrl+C已经实际验证，不能把其他OS写为实测通过。
- DNS/connect/断言阻塞属于临时副本故障注入；TLS使用本机只接受TCP但不完成握手的服务。不是对公网 DNS、证书或链路性能的验收。
- 此次没有复跑真实 core/visit/MySQL/Redis。旧版本的真实服务结果仍是历史证据；D6再按最终工具/应用组合做本地A组合验证。
- D2的5RPS/2700 GET模板、D3固定应用/迁移、D4数据Owner/步骤、D5正式口径、D6完整交接尚待执行。现有8RPS模板不可直接给当前公网入口运行。
- Zhili复审D1的实现、时间口径和证据后，仍须完成其Z1～Z4；本记录不批准推送/部署、造共享数据、HPA或A0/A1。

## 证据与复现

去敏证据：[最终测试统计与源码摘要](evidence/visit-hpa-d1-20261019/test-results.json)、[最终分场景计时](evidence/visit-hpa-d1-20261019/timings.jsonl)、[失败与中间轮次统计](evidence/visit-hpa-d1-20261019/rounds.json)。

从仓库根执行（只用本地回环服务）：

```text
python -m unittest discover -s tools/visit_hpa/tests -v
python tools/visit_hpa/load.py --config tools/visit_hpa/example.local.json
python tools/visit_hpa/load.py --config tools/visit_hpa/example.joint.json
```

可选设置 VISIT_HPA_TEST_EVIDENCE 为新的私有 JSONL 路径，记录分场景耗时；独立轮次使用新文件。测试器启动自己的隐藏CLI控制台；Windows使用原生CTRL_C事件，POSIX分支使用SIGINT。测试只清理自己创建的临时目录、监听与进程，不操作共享库、已有Docker资源或网站。
