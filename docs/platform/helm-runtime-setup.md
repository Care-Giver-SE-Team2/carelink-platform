# 第二板块：四个服务的部署配置

这一阶段把现有 Helm 部署接上环境配置和运行时 Secret。可以先于业务功能完成，
不需要创建 AWS 资源。CI 已经能构建并推送镜像；这里准备的是镜像运行时需要的配置，
EKS、RDS 与 Redis 已完成短时云端验收并清理，最终加密数据库快照保留；SNS/SQS 与自动部署尚未启用。

## 已接好的接口

| 配置 | 位置 | 作用 |
|---|---|---|
| Pod、健康检查、滚动更新 | `charts/carelink-service/` | 保留仓库原有的共享 Chart |
| 每个服务的资源和内部 API 地址 | `services/<service>/deploy/values.yaml` | 保留服务之间 `http://core`、`http://visit` 的访问方式 |
| 环境公共配置 | `deploy/environments/staging.yaml`、`demo.yaml` | 同环境共享 Redis 会话命名空间，不同环境分开 |
| 数据库、Redis、消息地址 | 同命名空间的 `<service>-runtime` Secret | 注入环境变量；真实值不写进 Helm values 或 Git |
| RDS 公共 CA | 同命名空间的 `rds-ca` ConfigMap | 挂载专用 Connector/J 信任库，校验数据库服务器身份 |
| 部署入口 | `scripts/deploy.sh` | 先检查 CA 文件键和全部选中服务的 Secret，再执行 Helm |
| 配置检查 | `scripts/check-deploy.sh`、CI 的 Helm job | 检查四个服务 × 两个环境，不访问 AWS 或集群 |

`staging` 与 `demo` 是两个 Kubernetes namespace，可运行在同一个集群。
这一步没有减少原有两副本配置，也没有把不同环境的数据隔离自动实现完：
实际部署时须提供不同数据库/账号、消息 topic 和 queue；Redis 如共用实例，
本次的会话前缀仅隔离会话 key，不构成独立的安全边界。

## 每个服务需要什么

| 服务 | 数据库 | Secret 名称 | 消息配置 |
|---|---|---|---|
| core | `carelink` | `core-runtime` | `CARELINK_EVENTS_TOPICARN` |
| visit | `carelink_visit` | `visit-runtime` | `CARELINK_EVENTS_TOPICARN` |
| report | `carelink_report` | `report-runtime` | topic ARN 和 `CARELINK_EVENTS_QUEUEURL` |
| notification | `carelink_notification` | `notification-runtime` | queue URL |

所有服务都需要：

- `DB_URL`、`DB_USER`、`DB_PASSWORD`：每个服务使用自己的数据库账号。
- `SPRING_DATA_REDIS_HOST`、`SPRING_DATA_REDIS_PORT`、
  `SPRING_DATA_REDIS_PASSWORD`、`SPRING_DATA_REDIS_SSL_ENABLED`：
  当前接口按启用 TLS、密码认证的非集群模式 Redis 端点准备。
  若后续选用 cluster mode、ACL 或 IAM 认证，需要据实际方案调整接口。

Redis 会话代码已在应用中实现；本板块供应端点、认证和网络连通性。
`SPRING_SESSION_DATA_REDIS_NAMESPACE` 由环境 values 统一设置，四个服务一致。
云端不要设置 `CARELINK_EVENTS_ENDPOINT` 或 LocalStack 的 `test` Access Key。
AWS SDK 的权限要由各服务的 EKS Pod Identity 提供。
现有 ECR 发布角色仅供 GitHub 上传镜像，不能替代 Pod 的 SNS/SQS 权限。

## 现在可以验证的内容

```bash
# 检查八种配置组合，只需 Helm
scripts/check-deploy.sh

# 查看 core 的 Kubernetes 配置；不会创建 Secret 或部署 Pod
DRY_RUN=1 IMAGE_TAG=check scripts/deploy.sh core staging
```

CI 新增的 Helm job 被纳入现有 Quality gate；配置错误会阻止后续 ECR 发布。
本地 Helm lint/render 通过只说明模板可以生成，不证明数据库可连接、镜像可以拉取、
Pod Identity 有效或业务健康。真实集群仍须完成下方验证。

## 集群和数据资源准备好后再执行

以下是后续执行说明。示例文件只有占位符，不可直接作为真实部署配置。

1. 配好 EKS 节点的 ECR 拉取权限、网络、数据库和 Redis，创建环境的 SNS/SQS。
   依据 `event-catalogue.md` 配置订阅、DLQ 和 Pod Identity，准备各数据库账号。
2. 确认目标 kube context。只读检查 `kubectl config get-contexts`，
   然后把准确名称填到 `KUBE_CONTEXT`。脚本对 kubectl 和 Helm 都显式使用该 context。
3. 为每个环境准备四份本地配置。真实文件放在已忽略的 `infra/.local/` 内。

```bash
umask 077
mkdir -p infra/.local/staging
for service in core visit report notification; do
  cp "deploy/secrets/$service.env.example" "infra/.local/staging/$service.env"
done
```

编辑这四份本地文件，替换所有占位符；不要 `source` 它们，JDBC URL 中的 `&`
不是 shell 命令。`kubectl --from-env-file` 按原始字符串读取值，不需要加引号。
RDS JDBC 使用 `VERIFY_IDENTITY`，通过专用 `/etc/carelink/rds/rds-ca.p12` 校验身份。
信任库生成脚本和 Chart 挂载已准备；操作者须先在目标 namespace 创建 `rds-ca`
ConfigMap，再部署应用。公共 CA 不放入运行时 Secret。详细步骤在操作者本地保留的
`docs/platform/runtime-data-setup.md` 中；该本地指南还包含四账号初始化。
写入 Secret 不会自动创建数据库账号。

```bash
export KUBE_CONTEXT='<实际目标集群的 context 名称>'
kubectl --context "$KUBE_CONTEXT" create namespace staging
for service in core visit report notification; do
  kubectl --context "$KUBE_CONTEXT" --namespace staging create secret generic "$service-runtime" \
    --from-env-file="infra/.local/staging/$service.env" --dry-run=client -o yaml \
    | kubectl --context "$KUBE_CONTEXT" apply --server-side -f -
done
```

如果 namespace 已存在，跳过创建它的命令。不要把 Secret YAML 保存进仓库。
更改 Secret 后须重新启动相关 Pod，已经运行的容器不会自动刷新环境变量。

```bash
export IMAGE_REGISTRY='<实际 ECR registry>'
export IMAGE_TAG='<四个服务均已发布的同一提交 SHA>'
scripts/deploy.sh all staging
```

脚本会在任何 Helm 安装/升级之前检查 `rds-ca.p12` 和所有选中服务需要的 Secret key，
缺失或空值则退出。该检查不验证密码正确、占位符已替换或云端连通性。
`--wait` 会等待 Pod ready；多个服务的部署不是一个事务，后续服务失败时前面的服务
可能已升级，不会宣称“全部成功”。

不使用清单时，`all` 使用统一 `IMAGE_TAG`，要求四个服务都已发布该 SHA。
增量发布现在可改用 `RELEASE_MANIFEST`，分别指定每个服务的已发布镜像 digest；
只更新 visit 时保留其他三个服务的版本。具体生成、预览和部署步骤见
[四服务版本清单](release-manifest.md)。不要同时设置 `IMAGE_TAG` 和 `RELEASE_MANIFEST`。

## 你与队友如何并行

你可以先提供：这份配置接口、数据库/Redis/消息资源方案，以及后续稳定的环境地址。
服务负责人继续写业务和 Flyway，保持端口 8080、现有健康检查与环境变量接口，
并提供登录、业务操作和事件处理的 smoke case。

网络/EKS 和开发数据层已完成云端验收与清理；验收记录由操作者在本地保留。
随后补消息资源、服务身份与 Secret 消费；Terraform 继续留在本地。
资源可用后，先验证 core 的启动、版本与 Redis 登录，
再接 visit/report/notification。最后才接 main 自动部署 staging、冒烟测试和 demo 晋级。

参考：[Kubernetes 环境变量与 envFrom](https://kubernetes.io/docs/tasks/inject-data-application/define-environment-variable-container/)、
[Spring Boot Redis/Session 属性](https://docs.spring.io/spring-boot/appendix/application-properties/index.html)、
[Helm template 的本地验证边界](https://helm.sh/docs/helm/helm_template/)。
