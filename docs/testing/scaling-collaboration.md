# CareLink 伸缩测试准备与分工

准备日期：2026-10-17。代码基线：a260ed8fd3f024c94e2c255bc9a2c24fbc7d2154。

本次交付只增加隔离的实验配置、只读工具和文档。没有修改现有工作流、默认 Helm values、部署脚本或公网入口；没有应用 HPA、安装组件、扩大权限、创建云资源或设置定时清理。本分支仅供评审，尚未合并或启用。

## 谁来测试，云端会自己做什么

**第一次由板块二和业务负责人联合测试。** 板块三负责业务负载和性能判定；板块四负责 Worker/选主实现和业务正确性；板块二负责云端准备、指标采集、容量判断和恢复配置。后续在场景固定后，队友可独立运行负载脚本并生成报告，不需要板块二一直手动操作。

| 动作 | 谁负责 | 是否自动 |
|---|---|---|
| 确定业务接口、测试数据、账号、成功条件 | 板块三/四与业务 Owner | 人工确认 |
| 核对版本、容量、Metrics、权限并启用已评审配置 | 板块二 | 有脚本辅助；本次未执行启用 |
| 发起压测、设置阶梯负载 | 板块三 | 人工启动负载脚本 |
| 根据 CPU 调整 visit 副本 | HPA 控制器 | 配置启用、指标正常、容量足够后自动 |
| 根据队列扩缩 Worker | KEDA + HPA | Worker/队列/权限完成后自动；目前不是已验证能力 |
| 故障注入 | 板块二与板块四 | 在约定窗口人工触发，不能让队友随意删除节点/数据库 |
| Leader 接管 | 应用选主机制 | 应当自动；手动重启、切换必须另行记录 |
| 保存集群/HTTP观测、生成图表 | 本包脚本 | 人工启动后持续采集，有限时长，可 Ctrl-C 停止 |
| 业务正确性、p95、错误率验收 | 板块三/四 | 由测试结果判定，不以 Pod 数增加代替验收 |

公网域名可供队友发负载请求，不要求把 EKS API 对 GitHub runner 或全网开放。没有集群权限的队友也能看共享的 CSV、HTML、录屏，或由你共享终端。不要共享 carelink 的 AWS 凭据、root、Cookie 或 kubeconfig。

## 文件与边界

| 文件 | 用途 |
|---|---|
| `deploy/experiments/visit-hpa.values.yaml` | HPA 备选：最少 2、最多 4、CPU 70%。仅候选值，未证明当前容量足够；正常 CD 不加载 |
| `tools/scaling/capacity.py` | 只读检查全节点 Allocatable、全命名空间非终态 Pod requests、Pending、候选最大副本及单节点丢失预算 |
| `tools/scaling/collect.py` | 只读采集 visit 的部署、HPA、Pod、Metrics，输出 `samples.jsonl` / `metrics.csv` |
| `tools/scaling/probe.py` | 可选：对业务 Owner 确认只读的认证 JSON GET 做低频观测，默认每 2 秒一次，不是压测器 |
| `tools/scaling/report.py` | 本地生成离线 HTML，显示副本、CPU、内存和可选探测曲线；不上传数据 |
| `docs/testing/teammate-confirmation-checklist.md` | KEDA、Active-Standby、缓存与压测约定 |

工具仅用 Python 3 标准库；实时采集还需要已有的 `kubectl`、AWS 登录与集群网络访问。离线渲染需要 Helm。缺权限/登录/指标时记录失败，不会自动申请权限或改成 root。

## 先离线准备，不动网站

在仓库根目录执行以下命令。`registry.invalid` 是离线占位值；渲染输出不能直接用于部署。

```bash
mkdir -p /tmp/carelink-hpa-review
helm template visit charts/carelink-service \
  -f services/visit/deploy/values.yaml \
  -f deploy/environments/staging.yaml \
  -f deploy/experiments/visit-hpa.values.yaml \
  --set-string image.repository=registry.invalid/carelink/visit \
  --set-string image.tag=offline-check \
  --show-only templates/hpa.yaml \
  > /tmp/carelink-hpa-review/visit-hpa.yaml
python3 -m unittest discover -s tools/scaling/tests -v
```

本包复用现有 HPA 模板，采用 Kubernetes 默认伸缩行为，没有修改共享 chart。CPU 70% 是相对于容器 requests 的利用率，不是 EC2 整机利用率。

## 测试日：先做只读检查

以下命令是将来执行指南，本次准备没有对 AWS 运行它们。先确认 context，不要盲用当前默认集群。

```bash
kubectl config get-contexts
export TEST_CONTEXT='替换为已确认的现有集群context'
export RUN_DIR="$PWD/reports/scaling/$(date -u +%Y%m%dT%H%M%SZ)"
mkdir -p "$RUN_DIR"
kubectl --context "$TEST_CONTEXT" get --raw /apis/metrics.k8s.io/v1beta1
kubectl --context "$TEST_CONTEXT" -n staging get deploy visit
python3 tools/scaling/capacity.py \
  --context "$TEST_CONTEXT" --namespace staging --deployment visit \
  --max-replicas 4 --reserve-pods 1 --output "$RUN_DIR/capacity.json"
```

`aggregate_budget_fits=true` 仅说明 CPU、内存和 Pod 数的总 requests 预算够，不是“允许上线/测试”的自动批准。还要检查：

- 每节点和每 AZ 余量、Ready、taint、拓扑分布、可用 IP；不能用集群总余量代替实际调度。
- 当前 Pending 必须先解释；CPU requests 缺失会影响 CPU HPA。
- `single_node_loss` 是丢一台节点的粗预算，不是丢整个 AZ 的模拟，不保证峰值流量；DaemonSet 在失效节点上的副本不迁移。
- `reserve-pods=1` 仅预留一个 visit 大小的 Pod，不涵盖并发发布其他服务。测试窗口不应与滚动发布重叠。
- 固定节点组不一定自动加 EC2；HPA 只调 Pod。预算不足时先降低候选上限或另行评审容量变更，不能直接扩节点。

离线重放也可用 `--snapshot-dir DIR`，其中须有同一时点的 `nodes.json`、全命名空间 `pods.json`、`deployment.json`。历史快照只证明历史状态，不能作为今天测试的放行依据。

## HPA 什么时候真正启用

**本包没有启用命令，避免准备工作意外改到线上。** 等队友提供压测接口、真实资源预算通过、测试窗口约定后：

1. 板块二确认同一应用版本、既有 HTTPS/Secret/image digest/跨 AZ 配置。
2. 以正常 PR/CD 接入 visit 专属的 autoscaling 配置，或对实验 overlay 的加载做明确评审；不要只新建一个无人读取的文件。
3. 不能同时 `kubectl apply` 一份独立 HPA，又让 Helm 管另一份；也不能让默认关闭 HPA 的 CD 在测试中重置 replicas。
4. 启用与后续恢复都必须保留原网址、当前数据和自动发布链路。测试恢复配置不是销毁云环境，也没有定时清理。

## 联合测试步骤（建议第一次预留 25–35 分钟）

这只是测试安排建议，不是系统 SLA 或已完成的测试结果。先由团队确认最大负载、预算和停止条件。

| 阶段 | 建议时间 | 操作与观察 |
|---|---|---|
| 基线 | 3 分钟 | 固定提交/镜像、2 个 Ready 副本；采集空闲与轻负载指标 |
| 阶梯压测 | 6–10 分钟 | 板块三按约定档位增加并发/到达率；记录业务 checks、p95、错误率、实际发出/丢弃迭代 |
| 维持负载 | 3–5 分钟 | 看 CPU、HPA desired、Pod Ready 的先后变化；检查 Pending；不得为了图好看手动 scale |
| 停止负载/观察缩容 | 至少 6–8 分钟 | 观察冷却和缩回最少 2 个；默认 downscale 稳定窗口通常为 5 分钟，还需考虑指标/控制周期 |
| 复核 | 3 分钟 | 登录和业务核验；保存结果；注明手工操作、漏采和限制 |

停止条件需在会前填写，例如连续错误率/延迟超阈值、Ready 少于 2、OOM/重启、持续 Pending、RDS/Redis 压力、网站业务异常。阈值由业务 Owner 确认，不在脚本里虚构。达到条件先停止负载；脚本不会擅自 patch、回滚或销毁资源。

板块三必须压代表性业务接口；压健康检查、静态网页或大量被 WAF 拦截的请求不能证明后端 HPA。若业务是 I/O 密集，CPU 不升高，不能声称 CPU HPA 失效，应如实记录并重新评审伸缩指标。

## 指标怎样看，怎样展示

终端 A（板块二）：先开始集群采集，覆盖基线、负载和缩容阶段。

```bash
python3 tools/scaling/collect.py \
  --context "$TEST_CONTEXT" --namespace staging --deployment visit \
  --duration 1800 --interval 10 --output-dir "$RUN_DIR/cluster"
```

终端 B（板块三）：运行他们已确认的 k6/JMeter 等业务负载脚本，输出原始或分阶段汇总。负载方案至少记录并发/到达率、数据集、业务成功断言、实际请求数、失败数、p95、未发出/丢弃迭代。**本包不伪造接口和写入数据，也不把低频探测当作负载发生器。**

终端 C（可选，板块二）：用测试账号的有效 Cookie 观测只读 API。Cookie 只放本地私有临时文件，不能进 Git 或证据包。用公开域名；不绕过 WAF/认证。

```bash
chmod 600 /tmp/carelink-test-cookie.txt
python3 tools/scaling/probe.py \
  --url 'https://dzfs8sv334fwf.cloudfront.net/api/替换为已确认的只读接口' \
  --cookie-file /tmp/carelink-test-cookie.txt \
  --duration 1800 --interval 2 --output-dir "$RUN_DIR/probe"
```

Cookie 文件内容仅为 Cookie 请求头的值。禁止放密码、浏览器导出的完整请求或 `Cookie:` 前缀。探测要求 JSON 2xx，不跟随重定向；302 登录跳转、HTML 登录页、401/403、超时均不能计成功。JSON 2xx 也不等于完整业务断言通过。Cookie 到期应停止并标注，不能把认证错误算成服务容量结论。

实时可看采集终端输出，或运行只读命令（需权限）：

```bash
kubectl --context "$TEST_CONTEXT" -n staging get hpa visit --watch
kubectl --context "$TEST_CONTEXT" -n staging get pods -l app.kubernetes.io/name=visit --watch
kubectl --context "$TEST_CONTEXT" -n staging top pods
```

团队成员无需进入 AWS 就能看结果：将下列脚本生成的 HTML 发给他们，浏览器直接打开。重新运行 report 可刷新正在采集的数据，不是云端常驻仪表盘。

```bash
python3 tools/scaling/report.py \
  --cluster-dir "$RUN_DIR/cluster" --probe-dir "$RUN_DIR/probe" \
  --output "$RUN_DIR/report.html"
```

没有运行 probe 时省略 `--probe-dir`。可追加 `--markers "$RUN_DIR/phases.csv"`，CSV 格式为 `timestamp,phase`，时间必须是有时区的 ISO8601，如 `2026-10-17T10:00:00Z,load-start`；记录基线、每档负载、停止及人工操作。该示例时间不是实测。

图表包括 desired/ready、副本变化、CPU/requests、内存及低频探测延迟。报告会提示缺失指标、无 HPA、发布版本变化，不填零掩盖缺失。Metrics API 可能滞后，原 JSONL 保留指标 timestamp/window 和 HPA conditions，不能把采集时刻说成指标精确发生时刻。

**板块三的压测 p95/错误率必须独立展示。** 将其分阶段汇总放在 HTML 曲线旁：负载→CPU→desired→Ready→业务 p95/错误率→停止负载→缩容。成功扩容不等于性能一定提升；只展示真实结果。

证据目录建议：`capacity.json`、`cluster/`、可选 `probe/`、`phases.csv`、队友负载脚本及 summary、版本记录、`report.html`、截图/录屏、结论与限制。不要收集 Secret、Cookie 或原始业务响应。原始资源名/私有 IP 仍需按分享范围处理。

## 官方参考

- HPA、requests、Metrics、默认伸缩行为：https://kubernetes.io/docs/concepts/workloads/autoscaling/horizontal-pod-autoscale/
- Pod/init/sidecar requests：https://kubernetes.io/docs/concepts/configuration/manage-resources-containers/
- KEDA 管理伸缩对象：https://keda.sh/docs/2.21/concepts/scaling-deployments/

这些参考说明机制；当前功能是否启用、测试是否通过仍以实际部署与本次记录为准。
