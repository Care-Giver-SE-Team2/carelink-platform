# 四个服务的版本清单与部署

CI 按改动范围发布镜像。只修改 visit 的提交，通常只有 visit 拥有该提交 SHA 的镜像。
部署全部服务时，不能假定 core、report、notification 也存在同一个 SHA。
发布清单保存四个服务各自的 commit、镜像仓库和 digest；增量发布只替换发生变化的记录。

## 已完成与尚未完成

本地已接通：CI 的 image-record.json → 完整发布清单 → Helm 按服务 digest 部署。
已有 CI 在 ECR 发布成功后保存每个服务的记录；本次新增清单生成、校验和部署支持，
以及纳入 Quality gate 的离线测试。代码尚未推送，GitHub 上尚未运行本次新增检查。

清单文件不会创建 EKS、数据库或消息资源，也不会自动同步 Secret。
本地新增的 Deploy staging 工作流已串起 CI 记录下载与手动触发的 CD，尚未上线运行。
环境当前版本的自动维护和业务冒烟仍未接入。EKS API 仅允许指定公网地址，
用户已选择 Mac 临时 runner；见 [Mac 部署准备](mac-runner-deployment.md)。

## 1. 首次生成完整清单

从仓库中通过 Quality gate、且 ECR 发布成功的 CI run 下载四个
`ecr-reference-<service>-<commit>` artifact。每个 artifact 内都是 `image-record.json`，
解压到不同服务目录，避免文件相互覆盖。只使用可信 main 流水线的发布记录。
若首轮缺少某个服务的镜像，可在 main 手动运行现有 CI，让全部服务构建和发布。

以下命令在仓库根目录执行；将 registry 替换为实际值，账号配置留在本地。

```bash
export IMAGE_REGISTRY='<账号ID>.dkr.ecr.ap-southeast-1.amazonaws.com'
mkdir -p infra/.local/releases

# 假设四份 artifact 已分别解压到 infra/.local/publications/<service>/
python3 scripts/release-manifest.py --registry "$IMAGE_REGISTRY" build \
  --record infra/.local/publications/core/image-record.json \
  --record infra/.local/publications/visit/image-record.json \
  --record infra/.local/publications/report/image-record.json \
  --record infra/.local/publications/notification/image-record.json \
  --output infra/.local/releases/release-001.json
```

生成器要求四个服务完整、仓库属于指定新加坡 ECR registry、tag 是完整 Git SHA、
digest 是 SHA-256。它拒绝重复的新服务记录，也不会覆盖已有输出文件。
清单仅包含镜像元数据，不包含数据库密码；实际账号信息仍按当前约定留在被忽略的本地目录。

## 2. 只更新一个服务

例如 visit 的新发布记录已下载到独立目录：

```bash
python3 scripts/release-manifest.py --registry "$IMAGE_REGISTRY" build \
  --base infra/.local/releases/release-001.json \
  --record infra/.local/publications/visit-new/image-record.json \
  --output infra/.local/releases/release-002.json
```

新清单保留另外三个服务的原镜像。若一次更新多个服务，重复传入 `--record`。
`--base` 应选择该环境已验收的完整版本；不能把尚未成功部署的清单当成当前环境状态。
脚本校验记录结构，不联网确认 ECR 镜像仍然存在，也不判断业务兼容性。

## 3. 离线预览和真实部署

```bash
unset IMAGE_TAG
export RELEASE_MANIFEST='infra/.local/releases/release-002.json'

# 仅渲染，不访问 AWS/Kubernetes
DRY_RUN=1 scripts/deploy.sh all staging
```

Chart 使用 `repository@sha256:...` 拉取确定的镜像；commit SHA 留作版本标签。
不使用清单时，原有 `IMAGE_TAG` 部署方式仍可用；不能同时设置这两种版本来源。
使用清单时即使只部署一个服务，也会先校验全部四条记录，避免保存残缺版本。
参见 [Kubernetes 镜像 digest 说明](https://kubernetes.io/docs/concepts/containers/images/#image-names)。

真实部署须先完成基础设施创建、数据库账号初始化、Pod Identity 关联及公开 RDS CA 准备。
然后同步配置并部署：

```bash
export AWS_PROFILE=carelink
export KUBE_CONTEXT='<实际 carelink-runtime context>'
python3 scripts/sync-runtime-config.py staging --profile carelink --context "$KUBE_CONTEXT"
bash scripts/deploy-release.sh staging
```

同步配置脚本校验 AWS 账号与集群端点；发布入口额外核对 ECR 中的 tag/digest 和 API 连接。
部署脚本先检查全部选中服务的 Secret 和 CA，
然后按 core、visit、report、notification 顺序执行 Helm，并等待各服务 ready。
任一部署失败就停止，已完成的服务不会自动回滚；四服务升级不是一个事务。
Secret 更新本身不重启已有 Pod：若镜像和 Pod 模板均未变化，需单独安排滚动重启。

## 4. Demo 晋级和镜像回退

staging 验收后的同一清单可以用于 demo，镜像无需重新构建。
但 demo 的数据、消息资源、身份权限和 Secret 必须先单独准备；当前云端消息策略只授权 staging。
本步骤仅证明两个环境可以渲染同一组镜像，不代表 demo 已经可部署。

镜像回退可指定先前保存的完整清单重新部署。清单只记录镜像，不包含 Chart、环境配置或数据快照。
执行回退前须确认相应 Chart/配置仍兼容，以及旧应用能使用已迁移的数据库；
它不能撤销 Flyway 数据库迁移，也不能恢复数据。

## 验证

```bash
python3 -m unittest discover -s scripts/tests -v
scripts/check-deploy.sh
```

离线测试覆盖首次组装、visit 增量更新、保留旧清单、残缺或重复输入、错误仓库/digest/SHA、
混用版本来源、两个环境的实际 Helm 渲染，以及模拟部署失败后的停止和 context 传递。
云端镜像拉取、应用启动和业务冒烟仍须在下一次限时云端验收中完成。
