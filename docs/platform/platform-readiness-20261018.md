# 板块二只读核验：预算、metrics-server、KEDA

采集时间：2026-10-18 04:41:57 UTC（新加坡 12:41:57）起。
身份：已核实使用 `carelink` profile 的 carelink-dev IAM 用户；不是 root。
集群：Singapore / carelink-runtime，EKS ACTIVE，版本 1.35。
这是本次实际 GET 结果，不是沿用旧报告，也不是长期在线保证。

| 项目 | 本次实际结果 | 准确结论 / 仍缺什么 |
| --- | --- | --- |
| AWS Budgets 清单 | DescribeBudgets 被拒，错误明确缺 budgets:ViewBudget | **未核验**，不能说“没有预算”，也不能说“三档提醒完成” |
| Chrome 预算备用入口 | 现有 AWS 页面显示 You have been signed out | 未使用过期页面或切换 root 凭据补查，也未追加 IAM |
| 50/100/150 美元配置及通知 | 因预算读取失败未取得 | 周期、币种、实际/预测阈值、接收方式和实际送达都待核验 |
| metrics-server Deployment | kube-system，v0.8.1，2 desired / 2 Ready | **已安装且就绪**，不应继续列为“未安装” |
| Metrics API | v1beta1.metrics.k8s.io，Available=True，reason=Passed | 聚合 API 可用 |
| 实际节点指标 | GET metrics.k8s.io 返回两个节点的 CPU/内存，时间戳约04:42 UTC；kubectl top nodes 成功 | 指标链实际可读；不是仅根据 Deployment Ready 推断 |
| staging Pod 指标 | 12:45 新加坡时间补查，8个业务Pod均有CPU/内存读数 | 已确认Pod层采集基础；尚未进行HPA负载/伸缩验收 |
| KEDA 控制器 | 全 namespace Deployment 无 KEDA；helm list -A 无 KEDA release | 未见 KEDA 控制器安装证据 |
| KEDA CRD | scaledobjects / scaledjobs / triggerauthentications / clustertriggerauthentications.keda.sh 均 NotFound | 标准 KEDA 安装所需 CRD 均缺，**当前未安装可用的 KEDA** |
| 外部指标 API | v1beta1.external.metrics.k8s.io NotFound | KEDA 外部指标链未提供；不影响已有普通 Metrics API |
| HPA 对象 | get hpa -A 成功，items=[] | 当前没有 HPA；PR55 的准备配置不等于启用 |
| 当前业务/控制器 | 四个 staging 服务均2/2 Ready；LB Controller 2/2 Ready | 查询时工作负载正常；不代表本次做了负载或故障测试 |
| EKS managed add-on 清单 | ListAddons 被当前身份拒绝 | add-on 管理方式清单未核验；不能将此拒绝解释为组件不存在 |

## 队员怎样复查（只读）

在本地已经拥有有效 carelink 登录和 EKS 读取权限后，从仓库根目录执行：

```sh
python3 scripts/audit-platform-readiness.py \
  --account-id YOUR_12_DIGIT_ACCOUNT \
  --cluster carelink-runtime \
  --output infra/.local/readiness.json
```

脚本先验证身份与账号、拒绝 root；使用本地临时 kubeconfig，不修改默认上下文。
只调用 STS/Budgets/EKS 的读取接口和 kubectl get，不 apply、安装、重启、发布、创建预算或发通知。
输出权限为0600且位于 Git 忽略目录；预算接收地址只保留数量/类型，禁止把原始报告直接上传公开仓库。
权限不足、超时、网络不通记为 unverified，只有明确 NotFound 才记缺失。
本地 kubeconfig 生成若超时或出现执行错误，也会保留已取得的身份、预算和 add-on 结果并落盘；
Kubernetes 整项记 unverified，不执行后续 kubectl，异常中的原始进程输出不写入报告。
该 JSON 的存在/脚本退出成功不表示所有检查通过，必须看每项 status。

控制台路径：

- Billing and Cost Management → Budgets → 目标预算 → Alerts/Notifications。
  核对原计划的50/100/150美元提醒。可用多个固定预算，也可能是一套金额加百分比阈值，
  必须计算实际触发金额；还要核对周期、成本范围、实际/预测及接收端。不要为了拍图新建或发送。
- EKS → Clusters → carelink-runtime → Resources → Workloads → kube-system → metrics-server。
  Console 无权限时保留 kubectl GET 证据；`kubectl top`/Metrics API 是采集链证据。
- KEDA 使用 CRD、operator 和 metrics adapter，不能只看 EKS Add-ons 页面判定有没有安装。

## 后续责任与最小缺口

1. **预算**：由已有账单只读权限的管理员查看配置及实际收件记录，或另行审查所需预算只读权限。
   本轮只记录缺 budgets:ViewBudget；不请求 AdministratorAccess，也不直接扩大策略。
   有已配置订阅不等于邮件真正送达；需接收人给出对应时间/阈值的收件证据。
2. **metrics-server**：安装与读取不再是缺口。板块三先给负载、阈值和HPA上限；板块二核容量后
   才部署经批准的HPA并联合测2→N→2，保留p95、错误率和副本曲线。
3. **KEDA**：当前缺安装、身份配置、ScaledObject/认证、真实Worker消费与扩缩容验收。
   板块四交Worker/消息协议/幂等，板块三交伸缩阈值和负载脚本，板块二提供集群安装、队列与最小权限。
   安装KEDA不会自动改造现有report调度；本次不安装或启用，避免后台任务重复触发。
4. **一键交接**：独立新环境实跑、完整管理权限引导、CodeBuild/组件安装的源码化和安全扫描仍未闭环。
   见 [IaC交接表](iac-handoff.md)。不重跑已经做过的节点/RDS/Redis演练，也不修改当前网站。

本轮未收集/输出 Secret 值、Cookie、访问密钥、完整 kubeconfig 或数据库数据。
原始 JSON 留在本机 `infra/.local/`，公开交接只保留上述摘要。没有定时清理安排。

## 本地交接验证结果

Terraform 1.16.5 / AWS provider 6.68.0：四个根在无远程 backend 的临时目录中通过 fmt、validate、
mock tests，共23项（platform 2、bootstrap 1、runtime 17、public-site 3）。新增检查包括完整HA
节点/双NAT路径以及响应头策略必填与两个静态行为的引用。只读核验脚本6项单元测试通过，覆盖权限
拒绝不冒充不存在、NotFound分类、接收人脱敏、root/错误账号拒绝及仅执行GET/读取命令；
新增回归覆盖 kubeconfig 生成超时和 OSError 时保留部分报告、0600权限及屏蔽原始输出。

提交前白名单检查未发现state/plan/实际输入、访问密钥/私钥标记、现网账号ID、现网响应头策略ID或
本机绝对路径进入infra源码。该检查不替代Checkov或完整安全评审。原始盘点JSON不进Git。
