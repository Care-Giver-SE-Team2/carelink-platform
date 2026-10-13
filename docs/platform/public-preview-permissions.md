# 公开预览：一次集中权限配置

2026-10-13。这批权限已按本会话批准范围配置完成，尚未启动新的收费窗口。Terraform、策略 JSON、账户输入与 plan 都留在本地被忽略的目录中。

管理员控制台已确认四份新策略创建成功、现有快照预览策略更新成功。专用组中只有 `carelink-dev`，只附加两份 Operator 策略；两份 Boundary 不直接附加到组。提交的五份策略 JSON 校验值与本地审查版本一致，记录与组配置截图保存在本轮本地目录。实际 AWS CLI 查询 CloudFront 托管策略及新加坡 origin-facing prefix list 已成功；这不等于已验证所有创建和删除动作。

## 为什么还需要这一批权限

先前权限覆盖四个后端、RDS、Redis 和消息资源，没有覆盖 S3 前端、CloudFront、WAF 或 ALB Controller。这次是增加公开入口，而不是修复原 IAM 用户的登录。

新的运行编号为 `20261013-04`，从最新平台快照 `carelink-staging-mysql-final-20261013-03` 恢复。新 Secret 和最终快照使用编号 `04`，不会修改或删除三个旧快照。真实 runtime plan 为 88 项创建、0 项修改、0 项删除；尚未 apply。

## 管理员一次配置的内容

所有文件位于仓库下 `infra/.local/public-preview-20261013-04/`。AWS 托管策略限制 6,144 个非空白字符，这五份文件都已检查未超限。

| 项目 | 管理员动作 | 给谁使用 |
| --- | --- | --- |
| `CareLinkLoadBalancerBoundary` | 新建权限上限策略；不直接附加到用户／组 | Controller 角色必须带此上限 |
| `CareLinkFrontendDeployBoundary` | 新建权限上限策略；不直接附加到用户／组 | 前端 GitHub 角色必须带此上限 |
| `CareLinkPublicSiteOperator` | 新建操作策略 | 专用组 `CareLinkPublicPreviewOperators` |
| `CareLinkPublicIdentityOperator` | 新建操作策略 | 同一专用组 |
| `CareLinkRuntimeSnapshotPreview` | 替换现有策略的 JSON，保持原附加关系 | 现有 `carelink-dev` 的快照预览授权 |
| `CareLinkPublicPreviewOperators` | 新建组，只附加上面两份 Operator 策略，只加入 `carelink-dev` | 不加入其他用户 |

两份 Boundary 由管理员管理。Operator 没有创建／修改 IAM policy 的权限，也没有移除／替换角色权限上限的权限。可以创建的自定义角色只有 `CareLinkRuntimeLoadBalancerController` 和 `CareLinkGitHubFrontendDeploy`，创建时分别强制使用对应 Boundary。

## 实际授权范围与限制

**Controller 身份：** Pod Identity 信任限定为 `carelink-runtime` 集群、`kube-system` namespace、`aws-load-balancer-controller` ServiceAccount。HTTP ALB 的创建和写操作限定新加坡及指定 cluster tag；节点安全组规则操作限定 EKS 的 `carelink-runtime` 标签。Controller 可以读取新加坡区域的 EC2／ELB 配置元数据，也可以创建带该 cluster tag 的安全组。没有 Cognito、证书、WAF 或 Shield 管理权限；Controller 的这些功能均关闭。WAF 由独立 Terraform 配置管理。

**前端 GitHub 身份：** 只信任本仓库 main 的 OIDC。可以上传指定前端桶的构建文件、读取／刷新带 CareLink PublicSite 标签的本账号 CloudFront；不能部署 EKS、写数据库、读取业务 Secret 或修改 IAM。

**公开入口操作者：** 管理唯一前端桶 `carelink-frontend-<本账号>-staging` 及其中全部对象版本；管理带指定标签的 CloudFront distribution、VPC origin 和指定名称的 SPA function；管理 `us-east-1` 中名为 `carelink-staging` 的 CloudFront-scope WAF；读写独立的 public-site 状态 key 和锁。只允许给 Controller 传递指定角色，不向其他角色增加 PassRole 权限。

**AWS 权限模型的例外须明确知道：**

- 托管缓存／请求策略的查询及部分配置查询需要读取账户范围的 CloudFront 元数据。
- OAC 没有可用的名称／项目标签约束，创建权限使用 `Resource:*`；读取与删除范围是本账号的 OAC ARN，因此权限层面可删除本账号其他 OAC。执行时只操作本轮 Terraform 清单中的 OAC，不授予 OAC 更新权限。
- CloudFront 的 `CreateConnectionGroup` 依赖按账户范围授予创建权限；不授予修改或删除其他 connection group 的权限。
- 清理权限可删除新加坡中带指定 cluster tag 的 Controller 安全组、ALB 与目标组，以及带 AWS CloudFront VPC origin 标签的安全组；执行脚本还会限定到本轮保存的 VPC ID、ALB ARN 和资源清单。
- 可以创建两个特定 AWS service-linked role：Elastic Load Balancing 与 CloudFront VPC origin 所需角色。这些角色由 AWS 管理，只信任对应 AWS 服务。

不应将这份权限描述为每个动作都只能访问一个预先知道的资源 ID。上述例外来自资源 ID 尚未创建及 AWS 动作的资源约束能力。它没有 `AdministratorAccess`、`iam:*`、旧快照删除或其他 S3 桶访问权限。

## 管理员操作步骤

1. 在有 IAM 管理权限的 AWS 控制台打开 IAM → Policies → Create policy → JSON。
2. 逐一使用对应 `.json` 文件全部内容，新建前四份策略，名称与文件名一致；不要把 Boundary 附加给用户。
3. 搜索现有 `CareLinkRuntimeSnapshotPreview` → Edit → JSON，用本次同名文件全部替换 → Next → Save changes。只有本轮 Secret、恢复来源、新最终快照范围更新，动作集合不变。
4. IAM → User groups → Create group，名称 `CareLinkPublicPreviewOperators`；只选择 `carelink-dev`，只附加两份 Operator 策略。
5. 核对组中只有该用户，Boundary 未作为用户授权策略附加。保留已经存在的其他组、角色和权限。

本步骤只创建策略／组并更新现有策略，不创建 EKS、ALB、CloudFront、WAF、RDS 或 Redis，不开启发布开关，不推送代码。完成后再做权限与真实公开入口 plan 检查，收费创建另行确认。

## 新窗口的准备情况

Controller chart 固定为 1.14.0，镜像固定为 v2.14.1，两个轻量 Controller Pod 使用现有两台 t3.medium；未增加 EC2。正则路由与 Gateway API 关闭参数已核对和离线渲染。Gateway API 的两个 feature gate 在该版本默认关闭，显式关闭后本次只使用 Ingress。

公开入口 Terraform 包含私有 S3、OAC、私有 VPC origin、CloudFront 和可选 WAF。WAF 计划为一个 Common Rule Set 和一个每 IP 每五分钟 2,000 次的限流规则；不启用 Bot Control、CAPTCHA、Shield Advanced 或收费月租套餐。真实 ALB ARN／DNS 需要等批准重建后的内部 ALB Active 再填写，不能把离线占位值当作实际资源。

清理覆盖新公网资源与所有前端对象版本。原三个数据库快照、ECR、GitHub OIDC／原发布角色、Terraform 状态桶保留；本轮再保留编号 `04` 的最终数据库快照。到期移除本轮新建的前端发布角色，权限上限和管理员权限配置保留。已获源码推送和草稿 PR 授权，合并 main 与收费窗口仍待确认。

公开入口的预检查 plan 已通过：13 项创建、0 项修改、0 项删除，包括 WAF。这份 plan 使用明确标记的 ALB 占位值，仅验证配置与读取权限，**禁止 apply**。实际运行必须先取得本轮 Active 私有 ALB 的 ARN／DNS，再生成并审查新的真实 plan。公共资源清理编排已通过 8 项离线测试，真实 AWS 删除仍待验收；启动时的清单与监控须就绪，收费窗口须另行确认。

来源：[AWS Controller 安装文档](https://docs.aws.amazon.com/eks/latest/userguide/lbc-helm.html)、[CloudFront 动作与资源约束](https://docs.aws.amazon.com/service-authorization/latest/reference/list_cloudfront.html)、[VPC origin 服务关联角色](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/using-service-linked-roles.html)。
