# Visit HPA A：合成数据准备、核验、白名单与保留（D4）

更新：2026-10-19。首轮只读工作包A；本文件是交付步骤，**没有在共享环境创建账号或visit**。随后Docker恢复，已按本文正常流程补完真实本地D4及D6；见[最新实际记录](visit-hpa-d4-d6-local-validation.md)。共享环境仍未操作，不把本地通过当作云端数据已准备。

## 1. 责任明确到人

| 工作 | 负责人 | 交付/答复节点 |
| --- | --- | --- |
| A业务契约、fixture合法性/形状、业务断言、工具问题解释与修复 | **Chenyu（业务Owner）** | 本文/固定工具；Zhili复审及业务异常时反馈Chenyu，不要求他全程在线 |
| 批准环境中的准备操作、核验、负载运行、私有配置保存、结果归档 | **Zhili**，可由其明确具名代理 | 数据准备前确认实际执行人，运行后回传结果索引 |
| 共享库Flyway只读查询及结构核对 | **Zhili协调的具名有权限执行人，待回填** | 应用发布前；Chenyu不持有AWS/DB凭据，不伪称已经查询 |
| 共享数据新增/审计增长/保留审批 | **审批人待Zhili具名确认**；若由Chenyu兼任，需明文回签范围 | 创建任何共享记录前；业务Owner不自动等于共享库管理权限 |
| 容量、监控、镜像/发布、HPA加载/恢复、来源IP与窗口 | **Zhili / 板块二** | Z1～Z4满足后才约正式执行；不转交Chenyu代做 |
| B签到事件、report/notification消费者与副作用 | 板块四具名Owner，**本轮A不要求新增验收** | 只有改为IN_PROGRESS造数或启动B前，才专门确认影响 |

不分享AWS凭据、Cookie、kubeconfig；所有实际账号、映射、私有核验表在批准的私有目录/通道。仓库与反馈ZIP只放占位模板、去敏摘要、固定SHA。

## 2. 首轮可复现的数据形状

候选 `scheduled-small-v1`：**4个独立护理员账号 + 4位合成老人 + 每人1个发布计划 + 每人1个未来SCHEDULED visit白名单**。max8VU循环分配到4个actor，每VU独立登录；同一actor的两个会话合法。2700次GET复用这4个visit，**无需准备2700条visit**。

每个白名单visit：真实分配给该护理员、`SCHEDULED`、`version=0`、`tasks=[]（0个执行任务）`、`serviceInstructions`恰有1条非空护理说明、execution为对象。计划发布/排班后、签到前执行任务本来未物化，0不等于没有服务内容。护理员已审批且canWork=true；不上传证书，选择无需证书的普通服务（例如自由文本“Personal care”，activityCode不填，evidenceType=NONE），不能绕过有证书要求的项目。

**代表性限制**：这是小工作包的合法常见起始状态，只能支持该形状的读取/HPA结论，不代表IN_PROGRESS大任务包、附件、缓存、家属端或最大数据量。Zhili复审时确认接受此形状；若需有执行任务的负载，另约IN_PROGRESS夹具准备与事件预算，不能直接把expectedTaskCount从0改成1。一个计划增加多个节点通常产生多个visit，不能假设一个visit就有多条执行任务。

时间：Asia/Singapore业务时区，选**次日或之后、排班生成窗口内**的计划开始日，visit开始必须晚于A0/A1整个有效期；建议预留至少2小时数据有效期，避开跨业务日和计划滚动时间。未来visit不需要CHECK_IN权限，首轮不做签到。选定日的实际weekday/本地10:00:00等时间须一致；不要复制过期日期。后台扫描保持正常，选未来状态来避免漏签到自动迁移；不为压测擅自关共享定时任务。

发布计划可能生成多个周次visit：白名单只选每人1个，**其余生成记录也归入私有附属清单并计保留/增长**，不删除或隐藏它们。4人若各生成2次，则实际保留8条visit，负载仍只读4条。

## 3. 准备前门槛

D3完成实际版本/四库历史核验且应用已获准发布；确认旧证据V4无冲突、core/visit共享会话正确、HTTPS入口不绕WAF。Zhili写明环境、可操作范围、数据审批人、准备执行人、唯一familyId、UTC有效期、审计保留复核日期。

选择带familyId的唯一用户名及纯虚构姓名（姓名字段不接受数字，例如Synthetic Elder Alpha；将familyId存于私有映射和允许的备注，不强塞姓名）。手机号字段须符合 +65[89]后接7位数字，由Owner指定为测试值，不联系/发送通知给真实号码。密码使用私有强随机值；**不用群演示账号、真实老人、真实证书/附件或固定样例ID**。保留准备前后记录与实际新增ID，失败后不要盲目重复注册/发布。

新账号可以走正常入口，也可在Owner确认后复用4组**已核验的专用合成对象**。复用必须确认不是他人测试/演示使用、无活动修改、身份与归属真实、状态/版本/形状符合本方案。不能把现有demo账号自动算成专用对象。

## 4. 可按顺序执行的正常业务操作

建议通过现有页面/批准的API客户端逐项做，始终同源、按角色保持独立Cookie容器。每次新会话：GET /api/auth/csrf，保存该会话Cookie和XSRF-TOKEN；JSON写请求附Content-Type和匹配Cookie的X-XSRF-TOKEN（URL解码值）。POST /api/auth/login 后重新GET csrf，再GET /api/auth/me核身份。**以下是操作说明，本文件不发请求。**

| 次序 | 操作者 / 方法与入口 | 必填内容和核对 |
| --- | --- | --- |
| 1 | 匿名CSRF会话；POST `/api/caregiver-registrations` →201 | username、password、fullName、phone、declaredDialects（如English）、requestedSector（如North）；不要附role、status、证书。保存返回userId、caregiverId、application.id及version；初始PENDING，不可工作 |
| 2 | 已有获准MANAGER；GET `/api/caregiver-applications/{applicationId}` →200 | 对照唯一username/caregiverId及当前version，certificates可为空；不得选错他人申请 |
| 3 | MANAGER；POST `/api/caregiver-applications/{applicationId}/approve` →200 | `{"message":"Synthetic visit HPA fixture","expectedVersion":实际version,"clientRequestId":"新UUID"}`；要求APPROVED。409先停并刷新，不无脑重试 |
| 4 | 对应CAREGIVER独立登录；GET `/api/caregivers/me/application`（core）→200 | id映射匹配、APPROVED、canWork=true；/api/auth/me实际id及roles含CAREGIVER。core专属路径按批准路由走；若edge /api/caregivers/* 路由未覆盖这些core路径，由Zhili发布路由解决，不直连私有ALB |
| 5 | 匿名CSRF会话；POST `/api/elder-registrations` →201 | `{"fullName":"Synthetic Elder Alpha","username":"唯一测试用户名","password":"私有值"}`，返回userId/username，**不直接返回elderId**。MANAGER随后GET /api/elders，先按准备前后新增的唯一合成姓名定位候选，再GET /api/elders/{候选id}核其userId等于注册返回的userId，记录实际elderId；出现重名/不唯一就停，不能猜ID |
| 6 | MANAGER；GET `/api/caregivers?elderId={elderId}`，然后PUT `/api/elders/{elderId}/primary-caregiver` | 确认候选caregiverId对应正确用户且assignable=true，PUT body为 `{"caregiverId":实际id}`；核对响应归属。被证书/冲突规则拒绝就停，不改SQL绕过 |
| 7 | MANAGER；POST `/api/care-plans` →201 | `{"elderId":实际id}`，保存返回planId |
| 8 | MANAGER；POST `/api/care-plans/{planId}/publish` →200 | 使用下方payload；要求PUBLISHED；保存计划版本与实际排班。可用GET /api/care-plans/{id}及/{id}/nodes核对发布快照 |
| 9 | 对应CAREGIVER；GET `/api/caregivers/me/schedule?dateFrom=YYYY-MM-DD&dateTo=YYYY-MM-DD`（visit）→200 | 日期覆盖刚发布的未来开始日；按该护理员会话的schedule筛选elderId/预定时间匹配的visit，随后从work-pack顶层carePlanId核对计划，记录id/status/version。不能从ID范围或SQL自增推测；没有visit先核排班与内部core/visit路由 |
| 10 | 同一CAREGIVER；GET `/api/visits/{visitId}/work-pack`（visit）→200 | 按第5节核对，记录形状和UTF-8响应字节数；GET本身写审计。每人只选1条入白名单，其余生成记录入附属清单 |
| 11 | 逐一重复4组，停止准备写入 | 完成私有白名单/配置映射，保持A0/A1同一批对象；准备期间的请求另入WAF预算 |

发布payload（替换日期、weekday、实际时间；weekday使用MON/TUE/WED/THU/FRI/SAT/SUN）：

```json
{
  "startDate": "实际未来本地日期",
  "nodes": [{
    "groupName": "Personal care",
    "name": "Personal care",
    "visits": [{"day":"实际weekday","startTime":"10:00:00","minutes":90}],
    "evidenceType": "NONE"
  }]
}
```

工作偏好/可用性不是把人变成可排班的替代审批，也不是已被全部排班算法消费的保证。本轮不为了“填满资料”强制修改偏好；如实际排班要求，护理员先GET /api/caregivers/me/work-settings读catalog/当前settings.version，再通过现有UI或PUT /api/caregivers/me/availability/{date}填写合法windows和expectedSettingsVersion/clientRequestId。所有额外写入在准备期登记，不进入A请求数。

## 5. A0前、A1前及两组结束后的核验

每次运行前15分钟内，由Zhili对**所有4组**重新核验，只有已观察字段才能填入白名单。原始响应和Cookie只存私有目录：

- /api/auth/me 的实际userId、CAREGIVER角色；core申请视图canWork=true；visit /api/caregivers/me档案的id（caregiverId）及userId映射一致。
- schedule显示被分配的visit；work-pack的visit.id、visit.elderId、顶层carePlanId、visit.status=SCHEDULED、visit.version=0及visit.scheduledStart与白名单一致；visit摘要不返回caregiverId，归属由该账号成功读取（服务端强制检查）及主管primary-caregiver映射共同核验，不能要求响应中不存在的字段，plan仍PUBLISHED。
- tasks为0个，serviceInstructions恰1条非空字符串，execution为对象；记录响应UTF-8原始JSON字节数（去压缩后的body大小，不能把传输压缩字节与它混算）。elapsed等动态时间、execution可操作提示随时间变化，不要求原始body逐字相等。
- 同一合成账号访问其自身白名单数据成功。越权负例只在本地或单独批准烟测执行；不用真实老人验证拒绝。出现错误身份、403、错ID/状态/版本、非JSON立即停并分类（包括WAF），不要改expectedVersion让旧配置继续跑。
- 两组后再次核schedule/pack及准备期计数，状态、版本、归属、任务形状应不变；GET允许新增访问审计和会话TTL更新。若被其他人签到/取消/改计划，本组无效，不直接拼接另一批fixture结果。
- 云端路由/数据核验必须保留私有摘要证明；离线guard不读取服务，因此不能替代上述核验。

私有配置actors只使用白名单内的userId/visitId/elderId/status/version/expectedTaskCount；alias与白名单完全相同。usernameEnv/passwordEnv只在运行配置指向私有环境变量，不写入白名单，不保存实际username/password/Cookie到Git。

## 6. 白名单格式和离线拒绝检查

复制 [fixture-whitelist.example.json](../../tools/visit_hpa/fixture-whitelist.example.json) 到私有目录，替换**所有**占位ID、日期、observedAt、responseBytes及Owner。样例2030日期、1000字节和示例ID均是未准备占位，不能提交为真实观察。familyId跨A0/A1一致，runId各组不同。白名单不含凭据；附属清单另存所有新增账号、计划、未来控制visit与审批记录，供保留复核。

```text
python tools/visit_hpa/fixture_guard.py --whitelist /private/fixture-whitelist.json
python tools/visit_hpa/load.py --config /private/run-A0.json
python tools/visit_hpa/fixture_guard.py --whitelist /private/fixture-whitelist.json --config /private/run-A0.json --ready
```

三条均离线，不DNS/登录/造数；第一条可结构检查未审批草稿，pending必须读清。第二条做原负载配置/预算校验，第三条还要求4组独立、配置全覆盖白名单、同应用SHA、固定起始状态/形状、核验在过去15分钟内、窗口当前有效、visit全程未来、Owner及review/retention填齐；失败退出2。即使通过仍 `onlineStateVerified=false / executionApproved=false`，不能当云端GO。

A1前刷新observedAt与实际观察值，检查仍是相同visit、状态/版本/形状，不靠改配置消除变化。guard只是离线输入一致性检查，不是权限证明、通用隐私扫描或自动强制接入load.py的安全边界；D6操作顺序须显式执行它。

## 7. 审计增长、白名单归属和保留

work-pack走visit库的 `audit_log`，action=READ、resource_type=VISIT_WORK_PACK、resource_id=visitId、actor_user_id=护理员userId、result=OK/DENIED/FAILED，独立事务保存。成功读取通常各增加1行，客户端超时/停止不保证没有到达服务端，不能根据客户端2700行反推数据库恰好2700行。

每组预算：2700个负载GET + 每VU预检1个工作包GET（8）=**2708个计划审计来源**；A0+A1=5416。另加准备/4组预检/烟测/组后核验GET及异常尝试；所有入口请求仍按D2的2740/组与otherRequestsPerWindow模型另计。开始前Owner填写附加请求与审计行预算、保留期限；不承诺精确存储字节，行大小/索引需按实际测量。

建议由有只读权限的执行人在visit库按**白名单actor_user_id + resource_id + 实际UTC窗口（转换数据库时区）**分组查询审计增量、result及起止id，并保存准备期与A0/A1边界。不要跨schema猜表归属；core业务登记/审批审计与visit读取审计分别记录。不提供拼接任意真实ID的自动SQL或删除语句。

首轮保留模式固定 `KEEP_MARKED_NO_AUTO_DELETE`：合成对象及关系、审批/计划/visit、审计、已有幂等凭据/Outbox（若准备产生）保留；记录familyId映射、创建人、用途、复核日期、审批人和私有结果位置。两组完成后停止负载并由Zhili恢复实验配置，**保留数据，不清共享库**。

若Owner决定停止未来测试计划，可经单独审批由MANAGER正常POST /api/care-plans/{id}/stop，body含effectiveDate/reason，并核验新审计/取消排班影响；这属于额外业务变更，绝不能放在A0和A1中间。账号是否停用沿现有管理流程/批准方式处理，本包没有一键禁用或删除API。不要DELETE已发布计划（该接口只丢弃草稿），不要删除审计、重置版本/ID、清空Redis或repair迁移史。

## 8. 请Zhili在这些节点回填

| 节点 | 需要答复的具体内容 |
| --- | --- |
| 复审D3/D4时 | 是否接受09d6a3f应用+c5b引擎、scheduled-small-v1（0执行任务/1说明）及结论范围；是否已有4组专用合成对象可复用；实际edge路由是否覆盖准备所需core路径 |
| 发布/造数前 | 四库历史只读执行人及旧report V4核验结果；共享数据新增/审计/保留批准人；实际环境/执行人、保留复核日期、批准范围 |
| 烟测/运行前 | 私有白名单/观察摘要已核验，4组身份/未来时间/形状无差异；附属记录计数与附加请求/审计预算；实际来源IP、WAF背景预算、Z1～Z4和窗口满足 |
| 测试后 | 应用/工具/配置SHA与digest，A0/A1独立结果索引，STOP/异常与业务差异、审计核对摘要、2副本恢复、数据保留状态；业务缺陷附去敏复现给Chenyu |

这些答复可以一份Markdown逐项写“已确认/待确认/阻塞+证据”。不需要Chenyu同时在线点击；权限/部署/监控缺口由Zhili协调，业务规则不清或代码缺陷再找Chenyu。
