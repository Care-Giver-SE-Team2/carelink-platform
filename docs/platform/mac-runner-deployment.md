# Mac 临时 runner：staging 部署

用户已选择在 Mac 上临时执行部署任务。Mac 复用当前获准访问 EKS API 的公网 IP，
GitHub 云端 runner 继续承担构建、测试和镜像发布；无需新增常驻 EC2。
这不会免除 EKS、节点、数据库等运行资源本身的费用。

## 已准备的交付

- `.github/workflows/deploy.yml`：main 上手动触发的 staging 部署。
- `scripts/prepare-deployment-release.sh`：从成功的 main CI run 获取四份发布记录并生成完整清单。
- `scripts/check-deployment.py`：只读验证账号、EKS 状态、context、镜像 tag/digest 与集群 API 连接。
- `scripts/deploy-release.sh`：先执行检查，再调用现有 Helm 部署入口。
- `deploy/access/staging-deployer.yaml`：staging 内的部署 Role/RoleBinding，由管理员提前应用。
- `infra/.local/CareLinkGitHubStagingDeploy-{trust,permissions,access-entry}.json`：账号专用的角色与 EKS 入口草案，仅在本地保存。

2026-10-12：22 项离线测试及 actionlint、ShellCheck 通过。

2026-10-13 配置进度：

- 用户已报告部署角色和内联策略创建完成。本地登录已恢复并确认身份为 `carelink-dev`；
  该用户没有目标角色的 `iam:GetRole` / `iam:GetRolePolicy` 权限，尚未独立读取核验。
  本次不追加 IAM 权限；后续可由管理员核对，或在获准部署时验证 OIDC。
- 已通过 GitHub 页面保存并核验 `AWS_STAGING_DEPLOY_ROLE_ARN` 指向约定部署角色，
  `CARELINK_MAC_DEPLOY_ENABLED=false`。
- 专用 runner 组尚未创建：GitHub 拒绝保存尚不存在于 main 的 `deploy.yml` 工作流限制，
  因此必须先合并部署工作流，再创建受限组。
- 这些部署代码尚未推送，runner 尚未注册或启动，未授予部署角色集群访问权限；
  EKS 仍按此前验收后清理的状态记录，本次未启动收费资源。

## 执行链路

1. 在 main 的 `Deploy staging` 中填写 CI run ID。每个服务可选择不同的成功发布；
   visit/report/notification 留空时使用 core 的 run ID。
2. GitHub 云端任务核对 run 属于当前仓库的 `ci.yml`、main、push/manual 事件且成功完成，
   下载准确名称的 ECR artifact，再核对记录的 service 与 commit SHA。
3. 保存 `staging-release` artifact。只有仓库变量 `CARELINK_MAC_DEPLOY_ENABLED=true`
   且前一步成功，才将部署任务发给 `carelink-deployment` 组内的 Mac。
4. Mac 通过 OIDC 获取 `CareLinkGitHubStagingDeploy` 的一小时临时身份，
   使用任务专属 AWS 配置和 kubeconfig，不复用个人 carelink profile。
5. 核对清单的镜像在 ECR 中仍存在，且 tag 对应 digest；验证 EKS/context/API 访问，
   再按 core → visit → report → notification 部署，逐个等待 ready。
6. 清理任务专属配置文件；runner 使用 `--ephemeral` 注册时完成一个任务后自动注销。

当前只实现手动触发的 staging CD，不会在每次 main 提交后自动启动，也没有 demo 晋级任务。
Helm ready 不等于业务验收；队友仍需提供登录、业务操作和消息消费的 smoke case。
部署失败时停止后续服务，之前成功的服务不会自动回滚。

## Mac runner 的访问限制

先将 `.github/workflows/deploy.yml` 合并到 main；GitHub 会验证工作流在指定 ref 存在。
再在组织 Settings → Actions → Runner groups 创建 `carelink-deployment`：

- Repository access：Selected repositories，仅 `Care-Giver-SE-Team2/carelink-platform`。
- 因仓库为 public，需要显式允许 public repositories。
- Workflow access：Selected workflows，仅填写：
  `Care-Giver-SE-Team2/carelink-platform/.github/workflows/deploy.yml@refs/heads/main`。

公开仓库不能仅依赖 runner label 或工作流里的 `if` 隔离不可信 PR；必须核对组的服务端工作流限制。
普通 CI 继续使用 ubuntu-latest。Main 的工作流变更也需要审查，因为被允许的工作流能在 Mac 上执行代码。
建议 runner 在专用 macOS 标准用户下运行，不放个人 AWS/SSH 凭据；任务的临时配置隔离不能代替主机隔离。

参考：[GitHub runner 组](https://docs.github.com/en/actions/how-tos/manage-runners/self-hosted-runners/manage-access)、
[自托管 runner 安全边界](https://docs.github.com/en/actions/reference/security/secure-use#hardening-for-self-hosted-runners)。

组限制配置完成、代码合并到 main 且进入云端验收窗口后，才注册并启动 runner：

1. 在组织 Actions → Runners → New self-hosted runner，选择 macOS / ARM64。
2. 在专用用户的独立目录下载官方页面指定版本，核对页面提供的 SHA-256 后解压。
   不要装在项目工作区，也不要安装常驻系统服务。
3. 按页面注册命令添加 `--runnergroup carelink-deployment --ephemeral`。
   注册 token 在本机输入，不粘贴进聊天或提交 Git；注册前再次核对组的 main 工作流限制。
4. 确认该用户的 PATH 中有 AWS CLI、kubectl、Helm 4、Python 3；Mac 只部署，不构建 Linux 镜像。
5. 用 `./run.sh` 前台运行并保持电脑在线。任务结束后核对 GitHub 中该 runner 已注销，
   检查后删除该 runner 的独立工作目录；ephemeral 注销本身不会擦除本地文件。

如果未实际执行任务而提前停止，需要在 GitHub 移除仍登记的 runner。
不要为了解决连接问题将 EKS public CIDR 改成 `0.0.0.0/0`；网络/IP 变化应更新实际 /32。
参见 [GitHub ephemeral runner](https://docs.github.com/en/actions/reference/runners/self-hosted-runners#ephemeral-runners-for-autoscaling)。

## 独立部署身份的范围

管理员创建 `CareLinkGitHubStagingDeploy`，信任本仓库 main 的 GitHub OIDC subject。
使用仓库已经验证过的含 owner/repository ID 的 immutable subject，不能替换成旧版名称格式。
它的 AWS 权限仅为：

| 动作 | 范围 |
| --- | --- |
| `eks:DescribeCluster` | 新加坡 carelink-runtime |
| `ecr:DescribeImages` | 四个 carelink 服务仓库 |

没有 Terraform state、AWS IAM 修改、Secrets Manager 读取、云资源创建/删除权限。
节点使用自身已有权限拉取镜像，因此部署角色不需要 ECR 登录和下载权限。
OIDC main 条件允许本仓库其他 main 工作流请求该身份；它不单独约束工作流文件。
Mac 接收什么任务由前述 runner 组限制；main 的仓库写入权限仍是信任边界。

EKS 重建后，由管理员用准备好的 access-entry JSON 创建 STANDARD 入口，
将此角色映射到 `carelink:staging-deployers`，不关联 cluster-admin access policy。
再由现有集群操作者应用 `deploy/access/staging-deployer.yaml`。
当前操作者的 IAM 策略没有创建此部署角色入口的权限，因此本步骤由管理员完成，
无需再扩大 carelink-dev 的 IAM 权限。该入口随本次集群删除；重建后必须重新配置。

Kubernetes Role 允许 staging 内部署相关资源的增删改查，以及 Helm 所需的 Secret 读写。
这意味着部署身份可以读取 staging 的运行时 Secret，且能以该 namespace 的服务身份运行应用；
不能将它描述成“无法访问应用密码”。Role 不授予 namespace、node、Role/RoleBinding 或其他环境的管理权限。
完整权限以 YAML 为准，真实 Helm 4 操作仍需云端验收。

## 启用前的顺序

1. 完成并核验部署角色，设置仓库变量 `AWS_STAGING_DEPLOY_ROLE_ARN`；
   复用现有 `ECR_REGISTRY`，保持 `CARELINK_MAC_DEPLOY_ENABLED=false`。
2. 将已检查的 workflow、脚本、Chart 和文档经 PR 合并到 main；Terraform/账号 JSON 不上传。
   工作流在 main 存在后，创建并核验上述受限 runner 组。
3. 在新的获准计费窗口内重建 runtime/data/messaging，恢复所需数据并配置数据库账户。
4. 管理员创建部署角色的 EKS access entry；操作者同步 Secret 和 CA、应用 staging RBAC。
5. 在具有当前 /32 网络路径的 Mac 注册临时 runner，开启部署变量，手动运行一次部署。
6. 记录四服务启动与业务验收；停止/移除 runner、关闭部署开关，并按窗口清理云资源及保留数据快照。

本次只准备部署入口，不执行上述收费资源启动。
