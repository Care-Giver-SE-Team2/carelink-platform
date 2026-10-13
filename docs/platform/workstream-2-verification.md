# 第二板块验收：怎样证明平台交付成功

核对日期：2026-10-13，新加坡时间。

当前结论：云端基础设施与四服务内部部署已验证；完整公开演示交付尚未完成。界面相似、账号相似或页面能打开，都不能单独证明微服务部署成功。

## 与旧 carelink 的差别

| 项目 | 旧 carelink 仓库的实现 | carelink-platform 当前实现 |
| --- | --- | --- |
| 后端发布单元 | 一个单体镜像 | core、visit、report、notification 四个独立镜像 |
| 镜像与交付 | GHCR；staging VM 定时拉取镜像，流水线等待版本并做 smoke/DAST | ECR；GitHub OIDC 临时身份；Helm 向 EKS 部署 |
| 运行单元 | VM 上的 Docker Compose 后端容器 | 一个 EKS、两个工作节点、四个 Deployment、八个 Pod |
| 数据边界 | 单体使用共享业务 schema | 同一 RDS 实例内四个 schema，各服务使用自己的账号和迁移记录 |
| 会话 | 旧版页面登录不能证明新平台的会话机制 | 云端 Redis；已验证同一会话跨 core 副本及四服务读取 |
| 当前访问入口 | 旧仓库提供 staging 地址配置、VM 对外入口和扫描流程 | 本轮 React 在 Mac，四个后端在 AWS；四个 Service 均为 ClusterIP，没有公开 Ingress |

旧流水线没有 SSH 登录部署机；部署机自己的 systemd timer 拉取 GHCR 的 staging 标签。不要将它描述成 GitHub 直接 SSH 部署。

## 已有证据与实际边界

| 验收项 | 合格标准 | 当前证据及限制 |
| --- | --- | --- |
| CI 与镜像发布 | 成功构建四服务，镜像可定位到提交和摘要 | 来源 CI 38345646949 成功；四个 ECR 摘要与本轮八个 Pod 的实际 imageID 一致 |
| 自动部署链路 | GitHub 任务实际取得临时权限并完成 Helm 部署 | 先前 CD 38372643013 成功；本轮快照预览由操作者运行相同部署脚本，没有重新注册 runner 或启用 CD |
| Kubernetes | 四个 Deployment 各两副本，副本分布在两个可用区 | 本轮清理前 8/8 Ready，8 个健康接口 UP，核对时重启数为 0 |
| 分库与迁移 | 四个库与平台迁移相符，不残留 core 对移出业务表的直接依赖 | 恢复后的四库与全新平台迁移结果一致；四组 Flyway histories 均存在；core 中不存在 visit 表 |
| 数据权限与 TLS | 每个服务只访问自己的库；拒绝跨库和明文连接 | 先前两个可用区、四账号的 CRUD、跨库拒绝及 TLS 验收通过；本轮没有重新执行全部权限负向测试 |
| Redis 共享会话 | 在一个 core 副本登录，另一副本能识别同一 SESSION | 本轮直接连接两个不同 core Pod：A 登录，B 的 /api/auth/me 返回 200 且身份一致；会话值未记录 |
| 跨服务读取 | 登录后实际调用 core、visit、report、notification | 管理员、家属、护理员三个角色共 15 个读取接口通过；家属能读到 2 位老人、7 条访问和 4 份报表 |
| 演示数据 | 使用平台自己的 SQL，四库关系和报表事实一致 | 28 账号、4 老人、17 护理员、34 访问、6 报表；50 组库内外键通过；未导入旧单体数据 |
| 消息基础设施 | SNS/SQS 路由、DLQ 与四服务身份可用 | 先前消息/身份探针通过；不能据此宣称所有业务事件处理、重试幂等场景完成 |
| 恢复与清理 | 从快照恢复真实四库；清理收费资源并保留约定快照 | 本轮恢复验证通过；21:00 完成清理，收费 runtime 与节点磁盘均已删除，三个加密快照 Available |

来源链接：

- [成功的 CI](https://github.com/Care-Giver-SE-Team2/carelink-platform/actions/runs/38345646949)
- [先前成功的 staging CD](https://github.com/Care-Giver-SE-Team2/carelink-platform/actions/runs/38372643013)
- [先前部署验收记录](staging-deployment-acceptance.md)

## 可以现场展示的只读检查

以下命令供下一次批准的运行窗口使用；当前集群已删除。在 carelink-platform 仓库根目录执行。不要展示 Secret 内容、密码、会话 cookie、Terraform state 或未脱敏日志。

```bash
export AWS_PROFILE=carelink
export KUBECONFIG=infra/.local/runtime.kubeconfig

# 证明实际连接的是 AWS EKS，而不是本机 Docker/Kubernetes。
aws eks describe-cluster --name carelink-runtime --region ap-southeast-1 \
  --query 'cluster.{Name:name,Status:status,Version:version}'
kubectl --context carelink-runtime get nodes -L topology.kubernetes.io/zone

# 证明四个独立部署和八个副本。
kubectl --context carelink-runtime -n staging get deployments,pods -o wide
helm --kube-context carelink-runtime -n staging list

# 证明公网入口目前没有交付：Service 为 ClusterIP、EXTERNAL-IP 为 none、Ingress 为空。
kubectl --context carelink-runtime -n staging get services,ingresses
```

当前窗口的详细证据保存在被 Git 忽略的 `infra/.local/snapshot-preview-20261013/`：

- `cloud-pod-verification.json`：八个 Pod 的健康、重启与镜像摘要。
- `restored-platform-schema-verification.json`：四库结构与迁移记录。
- `cloud-demo-data-verification.json`：演示数据计数及外键完整性。
- `preview-api-verification.json`：三个角色的跨服务读取结果。
- `shared-session-verification.json`：跨两个 core Pod 的同一会话验证。
- `inventory.json`、`window.json`：资源清单和清理时限。不要把整个本地目录上传。

## 下一批验收与公开网址

1. 交付云端前端及公网 API 入口、HTTPS；与入口板块协调负载均衡、路由、域名及 TLS。验收地址必须能由其他人的设备访问，不依赖你的 Mac 或 port-forward。
2. 从一个新成功 CI 发布版本，通过 CD 部署并对公开地址执行登录及业务 smoke；确认本轮重建集群的部署角色 access entry、runner 和部署开关已准备。先前的成功 CD 记录不代表重建后的集群立即具备这个入口。
3. 验证可独立更新一个服务并保持其他服务的版本；既有发布清单支持不同服务版本，但现场独立发布验收仍需补做。
4. 在约定的测试窗口验证一个 Pod 重建、会话保留和服务恢复；不要在用户操作页面时随意重启其 port-forward 指向的 Pod。
5. 由业务负责人提供事件产生、消费、重试及幂等用例，共同完成业务消息联调；补充跨版本回滚和数据库迁移兼容性验证。
6. 每次运行都完成到期清理核验和新最终快照验收，留下证据。本轮已完成。本轮数据库、Redis 和 NAT 采用开发配置，不能宣称整套系统已经高可用。

本轮预览在 20:45:59 开始清理；仅存在于 Mac 的 `127.0.0.1:15173` 不是公开网址。后台服务和数据位于 AWS，但这不等于公开访问与完整演示交付完成。

最终公开交付方向已改为 S3 + CloudFront + 私有 ALB，见 [最终部署方案](final-deployment.md)。尚未创建这些入口资源，也没有可访问的公开网址。最新快照 `carelink-staging-mysql-final-20261013-03` 保存的是平台四库与平台演示数据。独立清理核验见本地 `cleanup-verification.json`；本轮总时长 99.15 分钟，清理安排已删除。
