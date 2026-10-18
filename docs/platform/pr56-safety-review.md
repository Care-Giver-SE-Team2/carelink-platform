# PR 56 safety review

本次复核以 PR 初始提交 `23d57147e76f02282e930f4195c2ac8b4fbf2c2c` 为起点，对照用户要求：准备 ZAP 和 demo 审批晋级，保留当前网址及全自动部署，先不合并、不启用。审查分别覆盖实现规范与需求符合性。

## Standards

未发现确定的规范违例。改动集中在新增扫描、测试与方案文件，没有改写既有 staging CI/CD、Terraform、Helm 值或权限。没有发现需要另行重构的确认问题。

## Spec

发现并修复以下扫描问题。原工作流一直未启用，这些问题是在本地审查和隔离测试中发现，没有在现有网站上触发。

| 问题 | 影响 | 修复与核验 |
|---|---|---|
| context XML 使用了 `incregex`，实际字段是 `incregexes` | 真实 ZAP 报 URL_NOT_IN_CONTEXT，爬虫无法启动；原正则单元测试不能证明配置可用 | 改正字段；用真实容器确认允许的静态资源确实被访问 |
| 静态资源尾部正则过宽；ZAP context 又会去掉 query 再匹配 | 原正则允许穿越式资源路径；单纯收紧正则仍会放行带参数的首页/静态资源请求 | 限定平铺文件名和扩展名；添加原始 URI 的查询/编码排除规则；真实请求日志验证拒绝 |
| baseline 初始 `zap.urlopen` 默认跟随重定向，不受随后 context 爬虫边界约束 | 首页重定向可能先访问 API 或其他域名 | 只读挂载 hook，以不跟随重定向的 API 发出固定 seed GET，要求返回单一 HTTP 200；重定向场景仅发一次首页 GET 后失败 |

另将 checkout 固定为工作流的 `github.sha`、固定实际测试过的官方 scanner digest，关闭 scanner 的自发更新/遥测请求，并补齐 SIGTERM 取消时的本地容器清理。这些不修改应用部署或 AWS。

## Demo isolation

未发现独立的 demo 方案缺陷。当前 PR 没有可执行的 demo 发布工作流，也没有创建 demo environment、AWS 执行器、域名、数据库或 IAM。后续实现必须有独立目标、数据和权限，审批前验证环境保护，审批后验证候选制品摘要；禁止复用当前写向 staging 的 CodeBuild/前端发布入口。共享集群的容量和故障域仍需后续评估，不能仅用 namespace 宣称完全隔离。

## Evidence and limits

- ZAP 的两个真实请求回归场景在临时 Docker internal 网络运行，无公网出口、无主机端口映射；测试目标完全是本地 HTTPS 模拟站点。
- 普通链接场景访问两次首页、三个允许的静态资源，API、表单、query、穿越及其他域名请求均未出现。
- 首次访问重定向场景只访问一次首页，拒绝跟随，scanner 退出码为 3。普通场景因测试页面故意缺少安全控制而返回 WARN/退出码 2；这是范围测试通过，不是网站扫描全绿。
- [原始请求记录、镜像及代码摘要](evidence/pr56-zap-scope-check.json)对应真实 ZAP 2.17.0、linux/arm64；实际 staging 扫描及 hosted runner 首次运行尚未执行。
- 不合并 PR、不设置 `CARELINK_ZAP_ENABLED`、不创建 demo、不更改 main 保护、不修改当前自动发布开关。

本轮 Standards 确认问题 0 项；Spec 扫描问题 3 项均已修复，其中影响范围最大的是初始重定向可绕过爬虫 context 的限制。线上启用仍须单独决定，不能把本次隔离回归当作已完成 staging 安全验收。
