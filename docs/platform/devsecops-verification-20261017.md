# DevSecOps read-only verification 2026-10-17

本轮直接读取 GitHub API 和 AWS 新加坡区 ECR 控制台，核验 Proposal §5.5 的分支保护与镜像扫描。不修改保护规则、IAM、扫描配置、部署开关或线上资源；不主动扫描、推送镜像或部署应用。核验时远端 main 为 `a260ed8fd3f024c94e2c255bc9a2c24fbc7d2154`。这是有时间范围的状态记录，不代表后续设置和镜像永远不变。

## GitHub main 保护

路径：仓库 → Settings → Branches → main 的保护规则；同时核对 Settings → Rules → Rulesets。

API：`GET /repos/Care-Giver-SE-Team2/carelink-platform/branches/main/protection` 和 `GET /repos/Care-Giver-SE-Team2/carelink-platform/rules/branches/main`。

| 检查项 | 实际读取值 | 与 Proposal 的关系 |
|---|---|---|
| 必需状态检查 | `Quality gate`，绑定 GitHub Actions app | 已配置 CI 检查要求 |
| 要求分支先更新到最新 main | `strict=false` | 未强制，记录现状 |
| PR review 配置 | 存在，`required_approving_review_count=0` | **没有强制 1 人批准，未满足原文 one review** |
| 新提交使旧审批失效 | `dismiss_stale_reviews=true` | 已配置，但不能代替批准人数要求 |
| 管理员也受保护限制 | `enforce_admins.enabled=false` | 管理员可绕过，不能声称任何人都无法跳过 |
| 强制推送和删除 main | 两者均为 false | 均未放行 |
| main 适用的其他 ruleset 规则 | 返回空数组 | 本轮未发现补充的规则要求 1 人批准 |

结论：main 已有质量门禁保护，但“CI 通过且至少一人批准才能合并”只实现了前一部分。分工方案第 7 页写的“main 已加保护，一人批准”不能替代当前设置证据。本轮不调整批准人数或管理员绕过策略；应由全组确认后另行配置。

仓库为 public，查询 `GET /repos/.../environments` 返回 0 个环境。因此，当前没有 GitHub `demo` environment 的 required-reviewer 审批。自动 staging 前后端部署开关仍为 true；`CARELINK_PUBLIC_URL` 仍为 `https://dzfs8sv334fwf.cloudfront.net`。新增的 `CARELINK_ZAP_ENABLED` 变量未设置。

## ECR 已完成扫描结果

路径：AWS → Singapore → Amazon ECR → Private registry → Repositories → `carelink/<service>` → Images → 完整 main SHA 标签 → Scanning and vulnerabilities。

本轮四个镜像标签均为上述 main SHA。以下是已存在扫描的读取结果，没有点击 Scan，没有重新触发扫描。

| 服务 | 镜像 digest | 扫描完成时间 SGT 2026-10-16 | Critical | High | Medium | Low | 未定级条目 |
|---|---|---|---:|---:|---:|---:|---:|
| core | `sha256:ff3336d31af2ff5b0a56d84093d42f22b24d67d8d15afac732e384717cb5d578` | 18:17:32 | 0 | 0 | 2 | 0 | 2 |
| visit | `sha256:def18906aa809bcac5d341edeed0a7a15a92623f51ecc8fc657cfa345c77f45b` | 18:17:51 | 0 | 0 | 2 | 0 | 2 |
| report | `sha256:2bddb0a11ef583b292d5f4edebbf8208cc142979494f5ceccd8b8cef0d115ec2` | 18:17:31 | 0 | 0 | 2 | 0 | 2 |
| notification | `sha256:ca6be9c74245a96a77a12a52c5cc8e328836380920d47b27fbb19fd5311cbab5` | 18:17:36 | 0 | 0 | 2 | 0 | 2 |

四个状态均为 Complete。每个镜像列表显示 4 条：FreeType 相关 2 条 Medium，以及 libpng 相关 2 条 UNDEFINED。按包条目显示不等于有 4 个不同 CVE；不同镜像中重复出现也不应累加为独立漏洞。Critical/High 为 0 不代表无漏洞，也不证明 Java 依赖、应用逻辑或运行时攻击面全部安全。

## 推送扫描配置与范围限制

只读入口：Repositories → 选仓库 → Summary → Edit → 展开 Image scanning settings - deprecated；读取后 Cancel，不保存。四个仓库旧版 `Scan on push` 字段均为勾选状态。

注册表入口：Private registry → Features & Settings → Scanning。当前 Scan type 为 Basic，新版页面显示 `Scan on push all repositories` 未勾选，且没有列出的过滤规则；core Summary 的 Scan frequency 显示 `-`。旧版字段已明确标为 deprecated。应把“已有四个镜像的扫描结果”和“后续每次推送一定由新版规则自动扫描”分开描述。

本轮确认了扫描已经发生、扫描结果可查和旧字段为 true，但没有把界面差异解释为已完整验收新版自动扫描策略。要消除这一差异，后续用有效的 carelink CLI 会话读取 `get-registry-scanning-configuration` 与 `describe-repositories`，核对实际规则和遗留字段；再在下一次获准的正常镜像发布中关联 push 与 scan-complete 事件。当前 carelink CLI 会话已过期，本轮没有使用 root CLI 凭据，也没有为核验创建测试镜像或更改过滤规则。

当前 main 工作流没有读取 ECR findings 并按严重级别阻断 CD 的步骤。Trivy 目前是服务目录的依赖扫描，不能把它当成对这些 ECR OS 包发现的阻断门禁。“ECR 扫描结果已存在”已核验；“ECR 发现高危就阻断发布”未实现。

AWS 对 Basic 扫描范围和过滤规则的说明：[ECR basic scanning](https://docs.aws.amazon.com/AmazonECR/latest/userguide/image-scanning-basic.html)。

## 本轮 PR 的交付边界

- 准备默认关闭的 ZAP baseline，保留报告和版本身份核验；没有执行线上扫描。
- 准备 demo 版本标签、人工审批、同 digest/前端制品晋级及回滚方案；没有创建 demo 环境或资源。
- 不修改当前自动 staging 发布链路、分支保护、ECR 配置或 IAM。
- Checkov、ECR findings 发布阻断及新版扫描规则确认仍是后续协作项，未在本 PR 中冒充完成。

实施和验收步骤见 [ZAP 与 demo 晋级方案](zap-and-demo-promotion-plan.md)。原始 GitHub JSON 和 AWS 控制台原图保存在本地验收资料中，不随 PR 上传完整账户配置或本机路径。
