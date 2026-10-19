# Checkov Terraform CI与历史问题基线

## 已接入的控制

CI增加并行的 `Checkov IaC security` 作业，PR、main push、现有定时CI和手动CI均运行；现有 `Quality gate` 依赖其成功。Checkov固定3.3.29/Python3.12，源码或配置升级必须单独评审。

- `scripts/scan-iac.py --output <新的目录>`：先保留**完整扫描报告**，再使用固定基线识别新增失败。
- 扫描对象仅为Git已跟踪、infra下非隐藏目录的 `.tf`。不扫描真实tfvars、state、plan、provider缓存、`.local`或任意未跟踪文件；拒绝源文件符号链接。
- 不运行Terraform init/plan/apply，不申请云身份，不修改AWS。子进程移除AWS/TF/Checkov/平台凭据覆盖，使用临时HOME和显式空配置，不下载外部模块、不向Prisma上传。
- Checkov执行异常、JSON/解析错误、空扫描和新增失败阻断Quality gate；不使用全局soft-fail或continue-on-error掩盖失败。
- 两份JSON、来源哈希、固定基线哈希及summary作为 `checkov-iac` artifact保留14天。没有上传到外部安全平台；GitHub上的报告可能含源码与资源名称，应按仓库分享权限处理。

## 必须如实说明的基线范围

固定main `59a91a3c021d0d85a76faf1dd64690c4b4d5c99e` 源码（2026-10-19）：默认变量初扫为21个TF文件、99个资源、175通过/36失败。反例验证发现默认关闭的数据资源会被部分规则跳过，因此最终使用仅供分析的显式非敏感变量启用数据/WAF/HA并选择expand，覆盖可选资源。最终21文件、108资源，198通过、53失败、0跳过、0解析错误。最终基线使用这53项，未沿用不完整初扫结论。

`.github/checkov-baseline.json` 使用Checkov原生格式固定这53项既有发现。本PR提出的是**新增问题门禁**，不是批准现有风险、修复所有问题或零漏洞认证。基线未在CI自动生成/扩大；任何变更必须随PR说明并审查。完整报告继续包含基线发现，差异报告会把它们显示为baseline跳过。

原生基线按文件、资源、check ID匹配；它不比较同一旧问题的严重程度，也不能识别同一资源已存在的同类违规进一步恶化。修改这些资源仍须审查完整报告和代码差异，不能以“新增0”代替安全评估。修复后应删除相应基线条目；不会为让扫描变绿而修改云资源或默默新增豁免。

此扫描是Terraform源码检查，不读取真实tfvars，也不检查线上AWS状态。脚本在临时目录生成 enable_data_services=true、enable_waf=true、high_availability=true、ha_worker_migration=expand 的分析夹具；不是部署参数，不代表遍历所有配置组合。脚本还要求RDS公网访问规则实际出现，否则失败，防止数据资源意外从扫描消失。变量分支、外部管理资源及课程环境取舍必须单独核验。离线模式没有完整严重性元数据，本门禁不伪称“所有高危均已阻断”。

## 初始53项发现的处置说明

负责人：板块二跟进基础设施/CI，安全Owner协作复核；涉及课程需求、费用、密钥、网络或数据变更时，由用户/账号Owner决定。下表都是**待跟进或待确认的现状**，不是已经接受的豁免。不得把有理由保留基线等同问题已解决。

| Check ID | 数量 | 规则 | 现状与后续约束 |
|---|---:|---|---|
| `CKV2_AWS_11` | 1 | Ensure VPC flow logging is enabled in all VPCs | VPC flow logs未在此源码配置；保留为可观测性/审计缺口。 |
| `CKV2_AWS_12` | 1 | Ensure the default security group of every VPC restricts all traffic | 未用Terraform显式收紧VPC默认安全组；实际是否被使用和规则状态需只读核验，不擅自改组。 |
| `CKV2_AWS_31` | 1 | Ensure WAF2 has a Logging Configuration | WAF日志配置未纳入此源码；CloudWatch指标并不替代完整访问日志。 |
| `CKV2_AWS_32` | 1 | Ensure CloudFront distribution has a response headers policy attached | 静态behavior引用外部已审响应头policy，API行为分离；该发现仍保留，不以受控ZAP通过直接关闭此源码检查项。 |
| `CKV2_AWS_42` | 1 | Ensure AWS CloudFront distribution uses custom SSL certificate | 沿用现有CloudFront域名，未配置自定义证书；与保持当前网址的要求一起评审。 |
| `CKV2_AWS_47` | 1 | Ensure AWS CloudFront attached WAFv2 WebACL is configured with AMR for Log4j Vulnerability | 规则要求的额外AWS managed rule覆盖未由此源码证明；需独立评审兼容性，不能认为WAF存在即全规则覆盖。 |
| `CKV2_AWS_57` | 5 | Ensure Secrets Manager secrets should have automatic rotation enabled | 数据库凭据与服务Secret没有自动轮换编排；未确认客户端更新链路前不能直接启用轮换。 |
| `CKV2_AWS_60` | 2 | Ensure RDS instance with copy tags to snapshots is enabled | RDS快照复制标签未全部启用；不影响现有数据的判断，后续独立评审标签追溯。 |
| `CKV2_AWS_61` | 2 | Ensure that an S3 bucket has a lifecycle configuration | 桶的历史版本/对象生命周期策略未显式设置；保留为存储治理缺口，不自动删除对象。 |
| `CKV2_AWS_62` | 2 | Ensure S3 buckets should have event notifications enabled | 状态桶和前端桶未配置事件通知；当前是否需要通知属于用途选择，不自动创建事件链。 |
| `CKV_AWS_118` | 2 | Ensure that enhanced monitoring is enabled for Amazon RDS instances | 未启用RDS Enhanced Monitoring；涉及角色/权限/费用，另行评审。 |
| `CKV_AWS_129` | 2 | Ensure that respective logs of Amazon Relational Database Service (Amazon RDS) are enabled | 未显式配置RDS日志导出；保留为数据层可观测性缺口。 |
| `CKV_AWS_136` | 4 | Ensure that ECR repositories are encrypted using KMS | ECR显式使用AES256而非客户KMS密钥；密钥方案变更需另审，不为扫描通过替换仓库。 |
| `CKV_AWS_144` | 2 | Ensure that S3 bucket has cross-region replication enabled | 没有跨区域S3复制；当前已验证的双AZ能力不能代替跨区域灾备。 |
| `CKV_AWS_145` | 2 | Ensure that S3 buckets are encrypted with KMS by default | S3使用AES256加密而非客户KMS；与CKV_AWS_136一样属于密钥管理控制差异，不能说没有加密。 |
| `CKV_AWS_149` | 5 | Ensure that Secrets Manager secret is encrypted using KMS CMK | 服务Secret未显式采用客户管理KMS密钥；不据此断言运行态未加密，需单独评审密钥控制要求。 |
| `CKV_AWS_157` | 1 | Ensure that RDS instances have Multi-AZ enabled | 这里命中的是异步Reader本身未做Multi-AZ，而非主库关闭Multi-AZ；主库同步standby与独立Reader应区分，不能只为消警再复制一套Reader。 |
| `CKV_AWS_158` | 1 | Ensure that CloudWatch Log Group is encrypted by KMS | 控制面日志组未在源码绑定客户KMS密钥；保留为待评审项。 |
| `CKV_AWS_161` | 1 | Ensure RDS database has IAM authentication enabled | RDS使用当前凭据认证，尚未配置IAM数据库认证；这不是数据未加密，需与应用连接方式共同评审。 |
| `CKV_AWS_174` | 1 | Verify CloudFront Distribution Viewer Certificate is using TLS v1.2 or higher | 当前使用CloudFront默认域名/证书，未显式配置此规则要求的TLS策略；后续结合自有域名/证书方案评审。 |
| `CKV_AWS_18` | 2 | Ensure the S3 bucket has access logging enabled | 状态桶和前端桶未配置S3访问日志；不自动创建额外日志桶。 |
| `CKV_AWS_191` | 1 | Ensure ElastiCache replication group is encrypted by KMS using a customer managed Key (CMK) | Redis未指定客户KMS密钥；at_rest_encryption_enabled=true，不据此说磁盘数据未加密。 |
| `CKV_AWS_192` | 1 | Ensure WAF prevents message lookup in Log4j2. See CVE-2021-44228 aka log4jshell | WAF缺少规则要求的Log4j managed rule证明；与CKV2_AWS_47共同跟进，不自动扩大阻断规则。 |
| `CKV_AWS_293` | 1 | Ensure that AWS database instances have deletion protection enabled | 独立异步Reader没有显式删除保护；主库保护不能等同Reader保护，不自动修改数据库。 |
| `CKV_AWS_31` | 1 | Ensure all data stored in the ElastiCache Replication Group is securely encrypted at transit and has auth token | Redis源码启用transit_encryption且使用auth_token_wo写入凭据，规则检查旧auth_token字段；需核验新provider语义与运行态，不为静态规则改成明文配置或取消写入凭据。 |
| `CKV_AWS_310` | 1 | Ensure CloudFront distributions should have origin failover configured | 未配置CloudFront源站故障转移；不把双AZ ALB当作独立源站failover。 |
| `CKV_AWS_338` | 1 | Ensure CloudWatch log groups retains logs for at least 1 year | 课程环境控制面日志保留7天，规则要求至少一年；需结合费用和留存要求决定。 |
| `CKV_AWS_353` | 1 | Ensure that RDS instances have performance insights enabled | Reader未启用规则要求的Performance Insights；需另审监控需求与费用。 |
| `CKV_AWS_37` | 1 | Ensure Amazon EKS control plane logging is enabled for all log types | 当前配置api/audit/authenticator三类控制面日志，未包括全部类型；作为覆盖缺口保留。 |
| `CKV_AWS_374` | 1 | Ensure AWS CloudFront web distribution has geo restriction enabled | 未设置地域封锁；是否限制访问地区需业务决定，不自动开启。 |
| `CKV_AWS_39` | 1 | Ensure Amazon EKS public endpoint disabled | EKS同时开启私有端点和受admin_ipv4_cidr限定的公网管理端点；不自动改成private-only影响管理访问。 |
| `CKV_AWS_393` | 1 | Ensure AWS GitHub Actions OIDC authorization policies only allow safe claims and claim order on IAM role | GitHub OIDC subject使用带组织/仓库不可变ID的精确main绑定；需复核规则与自定义subject的匹配，不能放宽信任以消警。 |
| `CKV_AWS_58` | 1 | Ensure EKS Cluster has Secrets Encryption Enabled | 源码未显式配置规则检查的EKS encryption_config；需结合集群版本及运行态加密核验，不能直接等同明文Secret。 |
| `CKV_AWS_86` | 1 | Ensure CloudFront distribution has Access Logging enabled | CloudFront访问日志尚未在此源码配置；保留为日志覆盖缺口。 |

## 本地复现与报告查看

在独立Python虚拟环境安装固定依赖，随后执行：

```bash
python -m pip install -r .github/checkov-requirements.txt
python3 scripts/scan-iac.py --output /tmp/carelink-checkov-new-run
python3 -m unittest discover -s scripts/tests -p test_iac_scan.py -v
```

`full-report.json`是所有已运行检查；`gate-report.json`是基线差异；`scan-evidence.json`记录版本/统计/输入哈希；`summary.md`显示全量失败与新增失败的区别。输出目录必须是新目录，避免混用旧结果。

CI→Checkov IaC security作业可看摘要，在Artifacts下载 `checkov-iac`。现有站点、域名和自动CD不变；合并本PR后，正常main CI通过仍会触发既有自动CD，本PR不加入Terraform执行或demo创建。

## 验证边界

- 对真实的已跟踪源码扫描，53项仍完整可见，新发现0，解析错误0。
- 在本地一次性源码副本里把RDS改成publicly_accessible=true，真实Checkov报告新增CKV_AWS_17，门禁退出1；不接触线上数据库。
- 单元测试覆盖private/untracked输入排除、符号链接、空扫描、解析/运行错误和退出状态一致性。

## demo审批晋级（本PR不启用）

staging是自动部署与测试环境；demo是另一套正式展示环境。晋级应从已验收release选定同一组镜像digest与前端制品，经指定人员批准后部署demo，不从另一个提交重新打包。审批只阻挡demo job，不应阻断main到staging的自动发布。

完整验收需要：选定release/tag→检查staging/扫描证据→独立审批→原制品发布demo→demo验收→保留身份与结果。目前没有创建demo，没有配置或运行这条晋级链；仅写审批说明不能宣称实测完成。用户明确要求demo暂不创建，本PR保持该边界。

## 官方参考

- https://www.checkov.io/2.Basics/CLI%20Command%20Reference.html
- https://www.checkov.io/2.Basics/Suppressing%20and%20Skipping%20Policies.html
- https://www.checkov.io/2.Basics/Hard%20and%20soft%20fail.html
