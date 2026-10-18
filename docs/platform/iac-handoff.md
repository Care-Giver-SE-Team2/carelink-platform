# 板块二 IaC 源码交接（仅评审）

本次只准备源码、离线校验和只读核验。未执行真实 Terraform plan/apply/destroy，未安装组件、
未修改 IAM、预算、网站或 CI/CD 开关。PR #59 保持独立且不变。

## 队员从哪里开始

1. 读 `infra/README.md` 和本文件；使用干净 clone，先运行
   `python3 scripts/validate-iac-offline.py`。不需要 AWS 登录，不读远程 state。
2. 四个根目录分别核对源文件、lockfile、mock tests；输入示例中的账号、地址、策略 ID 都是占位。
3. 现有环境的真实输入、state 地址、已保留快照、Secret、run ID 和权限由授权操作者私下交接；
   不上传聊天附件中的 state、plan、实际 tfvars 或整个本地 `infra` 目录。
4. 此 PR 不提供可直接对现网执行的“一键重建/销毁”。任何真实变更先恢复准确输入，逐根核对 plan，
   再另定执行范围。默认开发配置不能对齐现网完整 HA。

## 依赖顺序与责任

| 顺序 | 交付物 | 来源与边界 |
| --- | --- | --- |
| 0 | AWS 账号、carelink 身份、预算、最小权限与管理员边界 | 账号管理员；本 PR 不创建预算或追加权限 |
| 1 | 私有版本化 state bucket | `infra/bootstrap`；已有桶不可再次创建，初始 local state 必须私下保管 |
| 2 | ECR、GitHub OIDC 与镜像发布身份 | `infra`；保留固定仓库与 main subject 的边界；已有资源先确认 state 归属 |
| 3 | 网络、EKS、节点、RDS/Redis、消息和服务身份 | `infra/runtime`；需外部 workload/controller boundaries、正确 HA 输入和已有快照 |
| 4 | LB Controller、metrics-server、KEDA | Helm/集群组件层；当前 Terraform 仅含 LB Controller 身份，组件未全部 IaC 化 |
| 5 | 数据库四 schema/账号、CA 信任、运行 Secret、应用 | 板块二云依赖与业务 Owner 联验；排除历史自动生成密码/修改 SQL 的脚本 |
| 6 | Service/Ingress → ALB Ready | 现有 service/ingress Charts 与部署脚本；ALB 由控制器管理，不是 public-site 创建 |
| 7 | 私有 S3 → CloudFront VPC origin/WAF/前端身份 | `infra/public-site`；需已存在 ALB、响应头策略、前端角色 boundary |
| 8 | CodeBuild 私网执行器、EKS deploy access、GitHub 变量与流水线 | 现有自动 CD 已运行，但其完整创建/恢复 IaC 尚未包含在这四个根中 |
| 9 | 四服务镜像、前端制品、公开登录/角色验收与回滚 | 现有 CI/CD；新环境从零完整复现仍待单独验收 |

CloudFront/WAF 原属板块五，此次只交接已存在的本地源码与接口依赖，不将开放 API 业务转给板块二。
现有 `deploy/cloudfront/security-headers-strict.json` 是策略定义来源之一，但其部署不是本 Terraform
root 所管理；交接输入必须引用已审查且实际存在的策略，不能去掉 CSP 以使 plan 通过。

## 本次安全整理的调整

- 只复制 `.tf`、Terraform mock tests、provider lockfile、controller policy 和占位输入示例。
- 不复制 `.terraform`、`.local`、`*.tfstate*`、`*.tfplan`、实际 tfvars/backend、kubeconfig、凭据、
  SQL、历史日志及清理/看门狗脚本；原本地工作副本保持不变。
- public-site 中写死的现网 response-header policy ID 改为必填变量
  `static_response_headers_policy_id`；未创建/替换任何现网策略。
- 重写过时 README，明确“支持的源码路径”与“当前云端实际状态”不同。
- 添加两条独立手动工具：离线验证、只读盘点。未添加或修改 GitHub Actions 工作流。

原源文件与交接文件的 SHA-256 见 `infra/source-manifest.json`。来源是尚未提交的本地工作树，
不能将其冒充为某个历史 main 提交中已经存在的 infra。

## 还缺什么：按板块二交付边界列明

| 缺口 | 本次结论 | 后续关闭标准 |
| --- | --- | --- |
| 现网 drift 与真实 plan | 本次未执行；没有“0 changes”证明 | 私下恢复实际输入与正确 state，确认数据库不替换、不删除；保留检查记录 |
| 管理员一次性 bootstrap | boundaries、CodeBuild 角色/access 等未全纳入源码 | 收敛可复制的限定权限清单，管理员审查；不授予队员管理员策略 |
| 完整组件安装说明/版本 | metrics-server 实际可用；KEDA 未安装 | 记录受审查的安装/升级值；KEDA 等三/四确认队列和 Worker 后再启用 |
| CodeBuild 执行器完整 IaC | 现有 executor 正常用于 CD；创建过程未完整纳入四个根 | 可审查地复现网络、身份、边界、EKS namespace access 与 GitHub 配置 |
| 新环境从零重建 | 尚无这套 handoff 的完整实跑记录 | 独立获准环境中按依赖顺序重建，验证应用、入口、身份及真实耗时 |
| 清理交接 | 保留顺序说明，未提供泛化一键 destroy | 先公网/VPC origin/WAF/S3，再 Ingress/ALB，再 runtime；保留约定快照/ECR/state/IAM，分阶段核验 |
| 预算提醒 | CLI 只读被拒；本次不能核验配置与送达 | 只读查看 50/100/150 美元提醒、周期、阈值、接收方式及实际收件记录 |
| Checkov 与策略门禁 | 本次没有扫描通过证明 | 板块五审查 IaC policy findings，板块二配合接入；不把 fmt/validate 当安全扫描 |

## 后续执行时必须保留的边界

不要把四个 root 合并为一个 state；不要因切换 HA 标志删除 read replica/Redis/NAT；不要改变
snapshot/run ID 来解决命名冲突；不要为验收重建当前 CloudFront 分发。固定 CloudFront 域名
属于现有分发，重建新分发会得到新域名。保留现网时必须保留它的 state/resource identity。

销毁原始输入也有两道保护：RDS `prevent_destroy` 与 deletion protection；解除它们属于单独
审查的破坏性步骤。本次不解除。已有快照/ECR/state/IAM 不因临时资源测试被自动删除。
“删除干净”应说明保留清单；本环境没有定时清理计划。
