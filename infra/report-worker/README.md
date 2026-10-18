# Report Worker / KEDA：独立增量资源

只新增专用队列、DLQ、SNS 订阅、两个有 permissions boundary 的角色及 Pod Identity 关联。
不管理现有 EKS、节点、RDS、Redis、原四服务队列、网络、ALB、CloudFront 或 IAM 管理策略。
这是独立第五个 Terraform root / state key，避免为新增 Worker 对旧 runtime 使用不完整的私有 HA 输入。
不要复制 runtime state，也不要 import 现有资源到此 root。

## 当前交付与开测条件

源代码基线为 main `2c59b3a`。板块四 v1 单老人任务和 Producer 已在该基线中。
`deploy/report-worker/staging.json` 默认 enabled=false；未合并、未设置 Cloud Ready 前不参与现有部署。
新增 Chart 没有 Service/Ingress，不在 `services/*/deploy/values.yaml` 自动发现路径中。
Worker 镜像取完整 release manifest 的 report digest，不构建第五个镜像。

截至首次准备，当前 carelink-dev 对新队列和新角色均无权操作（补齐 region/tags/boundary 上下文后，
有效权限模拟仍为 implicitDeny）。先由管理员创建两份新 boundary 和一份限定 operator policy，
不改原四服务的 boundary。生成 JSON 的命令只写本地文件，不调用 AWS：

```bash
python3 scripts/prepare-report-worker-iam.py --account-id ACCOUNT_ID --output infra/.local/keda-integration/admin
```

CareLinkReportWorkerBoundary / CareLinkKedaQueueReadBoundary 仅由新角色引用；
CareLinkKedaIntegrationOperator 通过仅含 carelink-dev 的专用组附加。
操作者不能修改/移除 boundary，不能创建通用 IAM 管理策略，不能访问其他队列或其他角色。
SNS 测试发布权限只覆盖现有 staging topic，应用仍需按 v1 校验后才允许受控投递。

## 分阶段执行（授权恢复后）

1. 私下填写 backend.hcl.example，固定 key=`carelink/report-worker/terraform.tfstate`、
   workspace_key_prefix=`carelink/report-worker/workspaces`；文件放 infra/.local。
2. `AWS_PROFILE=carelink terraform -chdir=infra/report-worker init -backend-config=ABSOLUTE_PRIVATE_BACKEND`。
   不使用 runtime 根目录，不执行 destroy。
3. `terraform plan -var=aws_account_id=ACCOUNT_ID -out=PRIVATE_PLAN`，再 `terraform show -json PRIVATE_PLAN`
   输出到私有 JSON；运行 `python3 scripts/check-report-worker-plan.py PRIVATE_JSON`。
   首次计划必须仅有本 root 的 11 项 create，不含替换、删除、导入、延后或漂移。
4. 实施经过核验的保存计划；记录 queue URL、角色与关联。核对原队列过滤器/公开入口未变化。
5. 复检节点 CPU/内存 requests、实际使用、Pod/IP、发布 surge、数据库连接预算。KEDA 三组件
   requests 共 300m / 320Mi；Worker 每个 100m / 512Mi。计入后每节点仍留至少 500m / 1024Mi
   requests 余量；不满足就不启用 max=2，不自动扩 EC2。单 AZ 故障下仍需单独预算，不能承诺峰值吞吐。
6. 固定 KEDA Chart/app 2.21.0（[官方文档支持 K8s 1.34–1.36](https://keda.sh/docs/2.21/deploy/)，当前 EKS 1.35），使用
   `deploy/report-worker/keda-values.yaml`。先离线渲染并审查 RBAC、APIService 和 webhook；
   watchNamespace=staging，不启用任意 custom scaled kind。通过 carelink 的明确 kubeconfig/context 安装。
7. 单独安装 `deploy/report-worker/priorityclass.yaml`；Worker 使用低优先级且不能抢占 HTTP 服务。
   它只提供调度优先关系，不代替内存限额、CPU 峰值或故障容量测试。
8. 验证队列、SNS 订阅、Pod Identity、KEDA Ready 及 external metrics API 后，记录 namespaced
   ConfigMap `staging/report-worker-cloud-ready`：data 中 workerQueueURL=实际专用 URL，cloudReady="true"，
   kedaReady="true"。该记录由操作者维护，是部署前置记录，不代替实际权限和消费测试。
9. 先启用固定单 Worker：staging.json enabled=true / scalingEnabled=false。
   同一发布把原 report release 切到 api profile、旧 timer=false、producer=false；保留其两个副本。
   固定一个完整、经过 CI 的 release manifest/digest，由现有 deploy-release.sh 发布。
   本地必须显式使用 carelink；CodeBuild 沿用现有角色，不授予它基础设施或 IAM 创建权限。
10. 准备可复现的合成 fixture（含 core API 可读取的老人资料及 report 的访问事实）；核对包中预期 SQL。
    包中的 manifest/cleanup 目前是模板，不能将占位 ID 当真实数据或直接投现有队列。
11. 单任务正确后，再 scalingEnabled=true / scalingPaused=false / maxReplicas=1；观察 0→1→0；
    最后复检容量、maxReplicas=2，观察多个独立任务的 0→2→0。每轮最多 10 条任务、30 分钟、AI disabled。

第 9 步的 active config 变更应与相关部署脚本经 CI 后一并发布；不要为了测试关闭全自动 CI/CD。
合并本分支即使开关为 false 仍可能触发 main CI 和正常四服务 CD，因此真实合并与发布要纳入执行记录。
脚本仅在 enabled=true 时创建专用 Worker Secret/Chart；KEDA 安装与 Terraform 不是应用 CD 的动作。

## 镜像、Secret 与副本管理

- `scripts/deploy-report-worker.py` 复用已有 release-manifest 验证逻辑；只取 report 镜像与 digest。
- 从 report-runtime 仅复制 DB/TLS/Redis 数据键到 report-worker-runtime，并替换专用队列 URL；
  不复制原队列、SNS topic、静态 AWS 凭据，不显示凭据或写入 Helm values；RDS CA 继续使用 rds-ca。
- Chart 固定 worker profile，关闭旧 timer、Producer、真实 AI 和 fault-drill；保留 servlet 健康探针。
- KEDA 模式不写 Deployment replicas，不创建 CPU HPA；自动发布只更新同一 report 镜像和受审查配置。
- Worker 的低优先级由操作者预先安装，namespace 范围的 CodeBuild 不创建 PriorityClass 或 KEDA。

## 证据与恢复

四轮标准、消息与 SQL 以板块四验收包及 PR #62 的交接资料为准：
https://github.com/Care-Giver-SE-Team2/carelink-platform/pull/62 。该 PR 仍未合并，不应假定交接目录已在 main。
不要把手动 SNS 投递当 Producer/Outbox 已验收，不把 AI disabled 当真实 Bedrock 已验收。
仅在目标业务结果正确且无任务丢失后记录消费通过；仅 Pod 数量变化不算 KEDA 完成。

停止新任务后保留队列和 DLQ，不 purge、不删除 dispatch claim。处理中任务要排空或记录重投。
若回退到旧 handler，必须先核对 v1 消息兼容性；不能仅关闭 enabled 而让已运行 Worker 无人维护。
进入 KEDA 前后和发布后均核验原网址、登录及四服务健康。无定时清理，无主备演练。

## 离线检查

```bash
terraform -chdir=infra/report-worker init -backend=false
terraform -chdir=infra/report-worker validate
terraform -chdir=infra/report-worker test
python3 -m unittest discover -s scripts/tests -p 'test_report_worker_deployment.py'
python3 -m unittest discover -s scripts/tests -p 'test_release_manifest.py'
```

Terraform mock apply 只使用测试 provider，不创建 AWS 资源；不能当真实 plan 或云端验收。
