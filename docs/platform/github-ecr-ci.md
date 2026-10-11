# GitHub Actions → AWS OIDC → ECR

本阶段的 GitHub 发布范围是 CI、镜像上传脚本和本文。Terraform `infra/`、
账号专用配置、state 和本机登录文件保留在开发者本机；基础设施校验工作流
也暂不发布。下文的 Terraform 命令供持有本地配置的基础设施负责人使用。

## 这一阶段解决什么

你负责先搭好镜像交付通路，队友继续实现 core、visit、report、notification。
业务还没写完也可以搭建通路，但服务自己的测试或扫描失败时，流水线应当
停止发布。队友把代码合并到 main 后，就能使用这条通路。

当前流程：

```text
PR → 按改动选择服务 → 编译、测试、SonarCloud（已配置时）、依赖扫描、Docker 构建
                    + 前端检查（有改动时）+ 全仓库密钥扫描
                    → Quality gate

main push / main 手动运行 → 上述检查 → 保存的同一镜像 → OIDC 临时权限
                        → 对应 ECR 仓库 → 保存 SHA 与 digest
```

PR、其他分支和定时扫描不会发布镜像。发布 job 依赖整个 Quality gate，
因此密钥扫描或前端检查失败时，也不能上传后端镜像。

## OIDC 和两种 AWS 身份

本机 Terraform 使用你的 `carelink` AWS profile；GitHub runner 没有这个
profile，也不能读取你的本机登录文件。它向 GitHub 请求身份令牌，再向 AWS
申请发布角色的临时权限。本配置请求 900 秒会话。

- 开发身份 `carelink-dev`：创建基础设施；新增的管理策略只指定 GitHub
  身份提供商和 `CareLinkGitHubEcrPublish` 角色。它能修改这个角色的信任
  和权限，所以属于管理权限，应仅授予你自己的开发身份。
- 发布角色 `CareLinkGitHubEcrPublish`：只允许 ECR 登录、上传和查询这四个
  服务的镜像。没有创建仓库、删除镜像、访问 Terraform state 或部署集群的权限。

GitHub 发布不需要设置 AWS Access Key 或 Secret Key。`id-token: write`
只放在发布 job；AWS 最终允许什么操作，由角色权限决定。

参考：[GitHub 官方 AWS OIDC 说明](https://docs.github.com/en/actions/how-tos/secure-your-work/security-harden-deployments/oidc-in-aws)
和 [AWS 官方凭据 Action](https://github.com/aws-actions/configure-aws-credentials)。

## AWS 配置

2026-10-12 已创建这三个 AWS 资源；真实计划为 `3 to add, 0 to change,
0 to destroy`，apply 后再次 plan 为 `No changes`。已从 AWS 读取实际角色，
确认信任规则与四个仓库的权限范围。已通过 Safari 在 GitHub 仓库保存并
核对两个 Repository variables。本地工作流代码尚未推送；首次端到端运行
仍待完成。

`infra/github-oidc.tf` 创建身份提供商、发布角色和角色内联策略。管理权限
由账号管理员一次性为开发用户配置，不由 GitHub 发布角色授予自己。

信任条件同时严格匹配 AWS audience 和这个仓库 main 分支的 subject：

```text
aud: sts.amazonaws.com
sub: repo:Care-Giver-SE-Team2@323581092/carelink-platform@1408273257:ref:refs/heads/main
```

仓库创建于 2026-10-07，按照 GitHub 官方的新规则，默认 subject 包含不可变
组织与仓库 ID。[官方规则](https://docs.github.com/en/actions/how-tos/secure-your-work/security-harden-deployments/oidc-in-aws)
已核对；这里假定仓库没有另外自定义 subject。名称、ID 或 OIDC subject
设置变化后，应重新核对信任条件。
不能改成允许整个组织、所有分支或任意仓库的通配符。

如果该账号已存在 GitHub 身份提供商，应先审阅并导入已有资源，避免重复创建。
部署环境以后若加入 GitHub Environment，subject 会变化，届时必须同步设计
环境保护规则与角色信任条件；本阶段没有使用 Environment。

验证和输出：

```bash
AWS_PROFILE=carelink terraform -chdir=infra plan
AWS_PROFILE=carelink terraform -chdir=infra output -raw ecr_publish_role_arn
AWS_PROFILE=carelink terraform -chdir=infra output -raw image_registry
```

## GitHub 仓库需要两个变量

进入仓库 Settings → Secrets and variables → Actions → Variables，创建
**Repository variables**，其值分别使用上面两个 Terraform 输出：

| 名称 | 内容 |
| --- | --- |
| `AWS_ECR_PUBLISH_ROLE_ARN` | 发布角色 ARN |
| `ECR_REGISTRY` | `<账号>.dkr.ecr.ap-southeast-1.amazonaws.com` |

2026-10-12 已配置并从变量列表核对，两者与实际 Terraform 输出一致。
现有 `SONAR_ORG` 和 `SONAR_PROJECT_PREFIX` 保留。

这两个值是资源标识，不是登录密钥。工作流缺少变量时会明确失败，不能
静默跳过发布并显示整个流程成功。

## 镜像和重跑

service job 用现有 Dockerfile 构建，随后 `docker save` 保存镜像为 Actions
artifact，保留一天。发布 job 下载当前运行的对应 artifact，执行 `docker load`
后上传，不重新构建。GitHub 的 Linux runner 构建平台与本机 Mac 可能不同，
不能把先前手动上传的 ARM64 验证镜像当成自动发布的 AMD64 镜像。

`scripts/push-image.sh` 支持两种调用方式：

```bash
# 本机使用 Terraform 输出和你自己的临时登录
AWS_PROFILE=carelink scripts/push-image.sh core

# CI 已通过 OIDC 获得环境凭据：无需 Terraform 或访问 state
IMAGE_REGISTRY='<账号>.dkr.ecr.ap-southeast-1.amazonaws.com' \
IMAGE_TAG='<提交SHA>' scripts/push-image.sh core
```

已有 SHA 标签时，脚本拉取镜像并比较本地 Docker image ID，只有完全一致
才复用。如果镜像内容不同（包括重新构建造成的差异），流程失败，需使用
新提交。优先只重跑失败的发布 job，复用之前保存的镜像；artifact 过期后
需要新的构建和版本。不要关闭不可变标签来修复重跑。

每个服务成功上传后会保留 `ecr-reference-<服务>-<SHA>` artifact 七天：

```json
{
  "service": "core",
  "tag": "<提交SHA>",
  "image": "<registry>/carelink/core",
  "digest": "sha256:<摘要>"
}
```

这只记录本次变更服务的镜像。完整部署的 release manifest 还需要保留
未改动服务的既有版本，不能假定四个服务每次都有同一个新 SHA 镜像。

## 验收和后续

本地检查包括 actionlint、ShellCheck、Terraform validate/test，以及上传脚本
对已有真实 ECR 镜像的复用验证。另用临时命令替身验证首次上传、相同镜像
复用、不同镜像拒绝、账号不符拒绝和 ECR 权限不足停止五种路径，并检查
登录临时目录清理。Terraform mock 测试检查精确 main 信任条件
与 ECR 权限范围，不会创建资源。

完整 GitHub 验收需要把代码推送到仓库并合入 main：查看 CI、确认 Quality
gate 通过后才启动 Publish to ECR、确认四个独立仓库的 SHA/digest 与 artifact
一致。仅完成本地检查和 AWS 角色创建，不能称为 GitHub 端到端验证通过。

后续接 ECR 镜像扫描结果门禁、运行环境、数据库/Redis、Helm staging、冒烟
测试与 demo 发布。开启 ECR 上传扫描不等于扫描结果已通过；本阶段上传
成功也不表示业务服务已在云端运行。
