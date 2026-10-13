# CareLink 公开入口：下一轮实施清单

更新：2026-10-13。当前无运行中的预览，没有公开网址。本文件记录下一步，不构成收费创建授权。

## 已完成

- 管理员已按批准范围配置公开入口权限，专用组只有 `carelink-dev`。
- 前端静态产物校验、S3 发布及 CloudFront 刷新脚本和独立 CD 已在本地完成。
- 私有 ALB 的四服务路由、HTTPS 会话配置及 Controller 安装参数已准备。
- 后端 runtime plan：88 项创建；公开入口预检查：13 项创建。均未执行。
- 公共依赖清理编排和独立核验已编写，8 项离线测试通过。运行时还需验证真实 AWS 删除行为。

公开入口预检查使用 ALB 占位值，**禁止 apply**。真实环境必须使用本轮 Active 私有 ALB 的 ARN／DNS，重新生成 plan。

## 下一步顺序

1. 仅提交公开入口与前端交付源码，创建草稿 PR，运行 CI。Terraform、IAM JSON、账号输入、状态、plan、凭据和本轮清理文件留在本地。此步骤不启动云环境。
2. CI 通过后审查并合并，取得含前端静态产物的成功 main CI run。旧 CI run 没有新增产物，不能作为这次前端发布来源。
3. 在新的收费窗口确认前，刷新 Mac 的实际公网 IP、AWS 登录及 runtime plan；补齐启动时的资源清单捕获和监控。当前清理入口要求完整的 88 项 runtime 清单；创建中途失败必须从实际 Terraform 状态接管清理，不能直接宣称此入口覆盖所有部分创建情形。
4. 获得新窗口确认后，从平台快照 `03` 恢复四库并部署四个后端；安装 Controller，创建私有 ALB，检查目标组健康和共享路径归属。
5. 使用真实 ALB 生成并审查 public-site plan，再创建 S3、CloudFront、VPC origin、WAF 和前端发布角色；填入 GitHub 变量，发布前端。
6. 验收 CloudFront HTTPS 地址：关闭本机前端和 port-forward 后仍能访问；三角色登录、页面刷新、四服务读取、CSRF 写操作、退出、Cookie 属性与 API 不缓存通过。WAF 和前端回滚另做实测。
7. 到期关闭发布开关并清理；保留原三个平台快照及本轮 `04` 最终快照，保留 ECR、原 GitHub 身份和状态桶。

## 清理顺序与失败处理

`infra/.local/public-preview-20261013-04/cleanup.py` 仅接受本轮已批准的 window 和资源清单，检查 AWS 账号及 VPC 标签。`--dry-run` 只打印顺序，不调用 AWS。

先通过独立 public-site Terraform 状态删除 CloudFront 分发、VPC origin、WAF、OAC、SPA Function、前端桶及其全部版本、本轮前端发布角色。保存实际 ID，再用 AWS API 确认删除；读取权限错误不会被当作资源已删除。

随后删除 `staging/carelink-api` Ingress，保持 Controller 运行，等待本轮 VPC 中 ALB 与目标组消失。只清理该 VPC 中未被网卡使用、且有 Controller 或 AWS VPC origin 标签的安全组。最后调用 runtime 清理，删除 EKS、节点磁盘、RDS、Redis、消息、网络，五个 Secret 使用恢复期删除，核验四个加密快照与三个状态 key。

公共依赖或 ALB 删除失败时停止后续 runtime 删除，记录错误，需要接管排查；watchdog 不会把失败标为完成。新窗口的 watchdog 尚未启动，当前只有模板，没有生效的收费窗口。

## 发布授权范围

待提交文件清单与完整源码 patch 已保存在本地 `infra/.local/public-preview-20261013-04/source-review/`。包含工作流、Ingress chart、CloudFront Function、Controller／HTTPS 配置、前端发布脚本、相关测试及三份现状文档和本清单。原有 Helm／runner 文档改动与 `infra-ci.yml` 不纳入本次提交。

本轮已获得上述 20 个源码文件的提交、推送和草稿 PR 授权。尚未获得合并 main 或新收费窗口授权；不需要重复配置已经完成的 IAM 批次。
