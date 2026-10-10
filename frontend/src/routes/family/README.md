# 家属端　Family portal

**FM01 / FM02 / FM03 / FM04 / FM05 负责人：** Wang Zhili。申请页面放在 `intake/`，周排程页面放在 `schedule/`，服务进度页面放在 `visits/`，报告页面放在 `reports/`，异常详情放在 `incidents/`；家属布局放在 `components/`。

## 要覆盖的用例

- FM01 建档申请与信息初填
- FM02 排程与人员资质查阅
- FM03 实时服务动态监控
- FM04 周期白话周报查阅
- FM05 突发事件即时知情
- FM06 排班变动协同决策
- FM07 质量抽查审批
- FM08 增值服务管理与审批
- FM09 护工评价与续费决策

## 布局要求

移动优先。读为主，专业术语要转成白话。

## 约定

- 按用例使用独立子目录，新增路由时协调 `index.tsx`，避免覆盖其他成员页面。
- 通用的东西放 `src/shared/`：按钮、表单、表格这类组件放 `shared/components/`，
  样式变量放 `shared/theme/theme.css`，调后端一律走 `shared/api/client.ts`。
  **要改 `shared/` 先在群里说一声**，那是五个人共用的。
- 业务逻辑放 `src/features/<模块>/`，不要堆在页面组件里。
- 老人端与其他三端的差别（字号、对比度、点击区大小）已经做在
  `shared/theme/theme.css` 的 `[data-theme="elder"]` 里，
  用 `<RoleShell theme="elder">` 就能拿到，不用自己写一套。

## 现阶段

FM01 页面已接入现有后端接口：

- `/family` 转到 `/family/home`（首页：待您回复的事项、当前／下一次服务、本周概况）；手机底部标签栏为 Home · Schedule · Reports（周摘要）· Applications（申请）· Menu，Menu 弹出全部页面（按 Care / Needs your answer / Your service 分组，Account 在最后）；桌面侧栏同样分组，Account 固定在底部。待回复数量显示在 Menu 及各页面旁。
- `/family/account`：账户页，显示关注的老人、通知方式和帮助；退出登录只在此页。
- 桌面（≥ 900px）：底部标签栏换成左侧导航栏（关注的老人切换、四个栏目、底部账户入口）。首页、周排程、周摘要为左右两栏，报告详情最宽 1060px，其余页面为 640px 单栏。所选老人在各页共享（`components/FamilyElderContext.tsx`）；报告页仍以 URL 的 `elderId` 为准。桌面版的特殊标记由 `useIsDesktop()` 控制，手机版行为和请求不变。
- `/family/intake`：本人旧建档申请历史列表，按状态筛选、每页 20 条、刷新。
- `/family/intake/:id`：本人旧建档申请详情、审核状态和备注；返回列表保留筛选及页码。
- `/family/intake/new`：兼容旧链接，重定向到新的照护服务申请页。
- `/family/service-applications`：已绑定老人的照护服务申请列表；保留旧建档申请历史入口。
- `/family/service-applications/new`：选择有效 FULL 绑定老人，展示已保存基础资料，只填写服务需求和本次备注；提交关联已有 elderId。姓名、地址、六位邮编缺失时先去 My elders 补全。
- `/family/service-applications/:id`：提交时的基础资料快照、服务、备注、SUBMITTED 待审核状态；读取仍校验当前绑定权限。后续主管审批另行接入。
- 未登录或 Session 失效时返回首页（`/`，唯一的登录页）。进入 `/family/*` 前由 `shared/components/RequireRole` 确认 FAMILY 会话，其他角色转到各自的客户端；之后任一请求返回 401 也返回首页。
- 两个查询接口分别为 GET `/api/intake-applications` 和 GET `/api/intake-applications/{id}`。身份由服务端 Session 确定，不发送申请人编号、角色或 JWT。
- 401、403、404、参数错误和网络故障各有提示；失败时不继续显示此前的申请数据。状态筛选和分页只放在 URL 中，申请内容不写入本地存储。
- 日期显示为新加坡时间。页面使用真实响应，测试样例仅存在于测试文件。

视觉参考 `docs/family/family.html`，采用 React 和局部 CSS Modules，支持手机窄屏；未复制原型中的固定手机外框。

FM02 周排程入口为 `/family/schedule`，也可通过家属导航进入：

- 选择当前有权查看的老人；用日期选择器选择任意一天，查看该周周一至周日的访视，也可切换本周、上一周、下一周。日期和时间均按新加坡时区显示。
- 请求先通过 GET `/api/auth/me` 确认家属身份，再复用 GET `/api/elders` 数组选择老人，使用 GET `/api/visits?elderId=...&dateFrom=...&dateTo=...&page=...&size=20` 读取排程。资源授权仍由后端执行。
- 每页最多 20 条，显示整周总数、当前显示范围及翻页按钮；切换老人或周次回到第一页。刷新重新检查当前身份和可访问老人。
- 分别显示无有效绑定、本周无访视、加载中和请求失败。401 返回首页；403 清除受保护内容并提供重新查询老人或更换账号（链接到首页）。切换或刷新时取消旧请求，迟到响应不会覆盖新选择。
- 卡片显示服务、计划时间、访视状态和护理员分配情况；结束时间为空时显示待确认。已分配护理员的卡片提供 `View caregiver`，点击后打开手机适配的详情弹窗，读取 GET `/api/caregivers/{id}` 和 GET `/api/caregivers/{id}/credentials`，显示姓名、语言和公开资质；未分配时不请求护理员资料。
- 资料与资质独立加载和重试；空资质与加载失败分别提示。按后端公开状态和当前新加坡日期共同判断，区分当前有效、尚未生效、过期及撤销；`9999-12-31` 显示为无到期日，不能覆盖撤销或尚未生效的提示。
- 关闭详情或切换老人、周次、页码时取消详情请求，重新打开时重新查询。任一详情请求返回 401／403 时清除整个页面的受保护排程、老人和护理员数据，再返回首页（401）或显示权限提示（403）。
- `features/schedule/` 管理 API 参数、类型、请求生命周期和日期展示；`schedule/` 管理页面与 CSS Modules。页面不写入绑定、排班、护理员分配或访视状态，也不在浏览器持久保存排程数据。

FM03 服务进度入口为每张 FM02 排程卡片的 `View progress`，路由 `/family/visits/:visitId`：

- 支持深链接和浏览器刷新。先确认 FAMILY 会话，再独立请求 GET `/api/visits/{id}`、`/timeline`、`/tasks`；每个接口都按服务实际所属老人重新授权。编号以字符串传递并校验正整数及 Long 上限，不转为可能丢失精度的 JavaScript 数字，也不传递 URL 中的身份参数。
- 分区展示实际服务状态、计划与签到／结束时间、APPLIED 时间线和任务。保留空值；不按时钟推进状态、不补造转换历史。只有 DONE 计入完成数，SKIPPED／REFUSED 单独标注；任务名作为纯文本保留换行，空任务不显示完成百分比。
- 页面可见且联网时，每轮会话检查及三个分区读取全部结束后约 15 秒再次刷新；慢请求不会启动重叠轮次。`Refresh progress` 可提前发起一轮并取消原定时器，读取中或暂停时禁用；`Back to schedule` 返回周排程。
- 同一服务、同一家属的上次成功分区仅保留在内存中，刷新中或失败时明确提示可能过期。成功分区独立更新，失败分区保留旧值和原时间，不冒充空记录；初次失败则不显示照护数据。详情 `asOf` 与时间线／任务的客户端接收时间分别标注，均按新加坡时间显示，不表示三个请求是同一快照。检测到不同账号后清除旧分区，再读取新会话允许的内容。
- 任意分区或会话检查发生可重试故障时，下一轮等待 30 秒，再次失败增加到 60 秒并封顶；全部成功后恢复 15 秒。页面隐藏／离线时取消在途请求和定时器并显示暂停提示，恢复可见且联网时立即读取；重复可见／联网事件不会启动多轮请求。手动重试可跳过退避等待。
- 任一请求返回 401／403 时取消其他请求、清空所有受保护内容并停止自动读取，401 返回首页，403 显示权限提示；切换可见／联网状态不会自动解除停止。主动重试后，对同一服务重新授权。400／404 同样清空并停止；网络／服务错误可自动重试，不展示内部错误。切换服务或离开页面清理请求、定时器和事件监听，迟到响应不能恢复旧数据。
- `features/visits/` 管理类型、API 和请求生命周期，`visits/` 管理页面及局部样式。家属读取页面和自动刷新已实现；实现设计及上游依赖见 [FM03 设计说明](../../../../docs/design/family/FM03-implementation.md)。真实 CG03／CG05 写入联调仍在第 7 批。

FM04 报告列表入口为 `/family/reports`，也可通过家属导航的 `Care reports` 进入：

- 先通过 GET `/api/auth/me` 确认家属身份，再读取 GET `/api/elders`；按选中老人请求 GET `/api/reports?elderId=...&audience=FAMILY&page=...&size=20`。复用报告模块的列表请求和元数据类型，身份与可读状态由服务端验证及过滤，不在前端拉取全量报告再筛选。
- 卡片显示报告周期、发布／归档状态、内容来源及完整性，缺失项作为纯文本显示。`TEMPLATE` 显示为正常的 `Structured template` 内容。周期按 API 的日期文字显示，不受浏览器时区影响；不从创建时间推断归档时间。
- 老人及页码使用 URL 的 `elderId`、`page` 保存；切换老人回到第一页，刷新保留当前老人和页码，并重新检查身份及可访问老人。每页最多 20 条，按服务端总数分页；空的后续页提供返回第一页。
- 无绑定、无报告、加载中和请求失败分别提示。401 返回首页；403 清除报告及老人信息，可重新加载可用老人或更换账号（链接到首页）。网络／服务故障可重试，不显示后端内部错误文字。
- 切换、刷新及离开页面会取消请求；迟到响应不会恢复旧报告。报告和老人数据只保存在页面内存中，不写入浏览器持久存储。FM01／FM02 导航保持可用。
- 每张卡片提供 `Read report`，进入 `/family/reports/:id`，并将当前老人和页码带入 URL；`Back to reports` 返回原列表选择，包括最初未显式选择老人的情况。

FM04 报告详情支持直接打开和浏览器刷新：

- 每次读取先确认 FAMILY 会话，再请求 GET `/api/reports/{id}`。详情授权由服务端根据报告资源判断，URL 的 `elderId`／`page` 仅用于返回列表，不作为身份或授权参数。报告编号按 URL 字符串编码传递。
- 展示已发布／归档状态、周期、老人档案编号、来源、完整性和缺失项；V19 新报告展示概览数字、每日体征范围图、服务统计、访视、护理员记录、异常及评价／抽查，概览后优先显示体征。数字来自 FAMILY 章节的 figures／series，不解析正文推算；图上标记仅表示录入时被标记，并可展开每日范围。旧报告保持原章节顺序及文字格式。更正／跟进按 kind 标记，单独追加，保留原报告正文；免责声明始终显示。正文与更正均通过 React 纯文本渲染，不解析 HTML 或 Markdown。
- 创建和更正时间读取接口中的时区偏移，统一显示为新加坡时间；归档时间为空时不补造。空章节和无更正各有说明，TEMPLATE 报告可正常阅读。
- 401 返回首页，403 显示无权访问并支持重试／更换账号，404 与无权访问分别提示；参数错误、网络或服务故障也有独立反馈。
- 刷新、切换报告及离开页面时取消旧请求并隐藏旧正文，迟到响应不能恢复受保护内容。从周摘要进入时，返回链接保留原来的老人和周次。

FM04 按周入口为列表中的 `Read by week`，路由 `/family/reports/weekly`：

- 默认查看新加坡时间的上一完整周，可选择任意日期查看对应周一至周日，或使用上一周／本周／下一周按钮。复用排程的日历工具，完整周边界与当前报告接口一致；非法输入不发起请求，首末完整周禁用越界导航。
- `elderId`／`weekStart` 保存当前老人和周一，`page` 仅保存返回列表的页码。深链接和刷新保留选择；切换老人清除旧列表页码。URL 中非周一的有效日期归一到该周周一，缺失或非法日期回到上一完整周。
- 每次加载确认 FAMILY 会话并重新读取可用老人，然后 GET `/api/elders/{elderId}/weekly-summary?weekStart=...`，再按响应的 `reportId` GET `/api/reports/{id}`。不把周条件传给报告列表接口，也不在一页列表中筛选周报。
- 摘要和对应详情成功后一起展示；核对响应的老人、周期和报告编号，防止错误组合。新报告复用已授权详情的结构化展示，旧报告摘要保留原始段落；TEMPLATE 显示为正常的 `Structured template`；完整性、缺失项、更正及免责声明随摘要显示，更正不改写原摘要。
- 只有周摘要接口返回 404 才表示该周暂无报告；详情 404、网络／审计等失败显示可重试错误。无绑定、无报告、加载失败分别提示，不借用其他周内容。
- 切换／刷新／离开取消旧请求并隐藏旧内容；任一请求返回 401／403 都清除老人及摘要／详情。重新加载权限时保留周次，重新选择新会话有权查看的老人，不保留旧账号的老人和页码。
- `Read full report` 进入相同报告，`Back to weekly summary` 返回原周；`All reports` 返回原列表页。PDF 仍在后续，本页没有下载按钮。

FM05 家属异常详情入口为 `/family/incidents/:id`，支持直接链接和浏览器刷新：

- 编号保持 URL 字符串，先校验正整数及 Long 上限。确认 FAMILY 会话后读取独立 GET `/api/family/incidents/{id}`；不读取主管详情、内部处理时间线，也不把 URL 的 elderId／familyMemberId／notificationId 当授权或命令参数。
- 显示异常描述、来源、类别、严重度、处理状态、报告／实际解决时间、本人响应期限及首次查看／知悉回执。时间统一显示为新加坡时间，null 不补造。描述和备注保留换行及空白，作为 React 纯文本展示。
- 成功内容提交到页面后才 POST `/api/incidents/{id}/view`，已有首次查看时不重发。查看失败明确提示并提供独立重试；仍可主动确认知悉，不伪造已查看。
- “I am aware” 提交可选 255 长度备注至 POST `/api/incidents/{id}/acknowledge`，Session／CSRF 随请求发送，身份和时间由服务端决定。备注不去除首尾空白；空输入发送 null。初始化 CSRF 至响应返回期间禁用重复提交。只在收到成功回执后显示 Awareness confirmed；失败保留备注，需主动重试，不自动重发。
- 已知悉显示服务器首次时间／备注；已解决、无个人期限或期限经过都不禁止合法知悉。异常处理状态与本人知悉独立。较早的 view 响应不能覆盖较晚的 ack；上述操作不请求或写入通知已读接口。
- 加载和命令重新检查家属会话；账号变化、401／403 清空正文和备注，401 返回首页，403 显示权限提示。加载 400／404 与网络／服务故障分别提示，服务故障可重试。不直接展示服务器内部错误。
- 刷新、切换异常或离开取消旧请求并隐藏旧内容；CSRF 初始化结束后检查取消状态，避免页面离开后继续发出旧命令。取消不能撤销服务器已经提交的写入，重新加载以服务器回执为准。草稿仅在当前页面内存中，不写浏览器存储。
- 离线或页面隐藏时暂停请求并隐藏详情；再次同时在线且可见时，重新确认会话并读取最新个人窗口／回执，重复恢复事件不会重复加载。同一账号的未提交备注保留在本页内存；账号变化或拒绝访问后清除。中断的知悉不自动重发：服务端已有回执则显示首次结果，否则明确提示未能确认保存，保留备注供主动重试。401／403 后不随恢复事件自动重试，403 可手动重新校验权限。
- 新 API／投影及请求生命周期位于 `features/incidents/familyApi.ts`、`familyTypes.ts`、`useFamilyIncident.ts`；页面和局部 CSS 位于 `routes/family/incidents/`。没有修改主管代码或通用铃铛。通知组件作者可将 FAMILY／INCIDENT 通知链接接到 `/family/incidents/{resourceId}`；真实事件来源、旧家属发送交接和铃铛跳转仍是独立联调事项。

代码位置：

- `features/reports/useFamilyReportPage.ts`、`useFamilyReport.ts`、`useFamilyWeeklySummary.ts`：家属报告列表、详情和周摘要的身份校验、请求和取消；`api.ts`／`types.ts` 明确家属详情及摘要投影，更正不含内部作者 ID；`routes/family/reports/` 管理页面、URL 选择、状态反馈与样式，`ReportNotes.tsx` 复用详情与摘要的完整性和更正展示。
- `routes/family/intake/`：旧建档申请历史的页面与样式；浏览器 URL 的分页和筛选状态留在页面中管理。
- `features/intake/api.ts`：旧建档申请列表、详情接口的路径、参数编码和返回类型。
- `features/intake/useIntakeQueries.ts`：`useIntakeApplications` 与 `useIntakeApplication` 管理查询、刷新、错误和取消；页面只传业务参数。
- `features/intake/types.ts`、`presentation.ts`：响应类型和展示转换。
- `features/auth/api.ts`、`types.ts`：`initialiseCsrf` 初始化 CSRF，`signInWithSession` 封装 Session 登录，并声明登录参数和返回用户类型。登录表单只在首页。

共用 `shared/api/client.ts` 增加 `ApiError.status` 并支持空成功响应；原有调用方式、错误 message、Session 和 CSRF 行为保留。合并时请同步这一公共改动。

## 本地运行

```bash
cd frontend
npm ci
npm run dev
```

然后打开 http://localhost:5173/family

需要先启动后端（8080）及其数据库，使用已有 FAMILY 账号登录；账号还需对应 `family_member` 记录。
前端通过 Vite 的 `/api` 代理访问后端。浏览器和 Apifox 不共享登录 Cookie，需要在网页内登录。

手机实机预览：执行 `npm run dev -- --host 0.0.0.0`，手机连接同一局域网后访问 `http://电脑局域网IP:Vite实际端口/family`。
若 5173 已占用，使用终端显示的实际端口，不需要调整后端代理地址。

## 验证

```bash
npm run lint
npm run test:coverage
npm run build
```

`FamilyHome.test.tsx` 从页面入口验证列表、分页、筛选、详情、登录、权限失效、请求取消和失败恢复；
`ServiceApplications.test.tsx` 验证绑定老人选择、资料补全、提交、权限变化和不确定结果处理；
`shared/api/client.test.ts` 验证 Cookie/CSRF 请求、空响应和 HTTP 错误状态。测试仅替换网络边界，不依赖本地数据库。

`FamilySchedulePage.test.tsx` 验证周排程路由、选择与分页、权限失效、登录恢复和旧请求取消；
`features/schedule/api.test.ts` 和 `presentation.test.ts` 验证 API 参数、新加坡周界、跨月跨年及可空字段展示。
`FamilyCaregiverDetails.test.tsx` 验证按需加载资料与资质、独立重试、权限失效和详情取消；
`features/schedule/caregiverApi.test.ts`、`credentialPresentation.test.ts` 验证公开接口和资质的日期、状态展示规则。

`FamilyVisitProgressPage.test.tsx` 从真实路由、组件和 API client 的网络边界验证 76 个场景。除排程入口、状态、空记录、任务计数、纯文本、时区、授权及迟到响应等读取行为外，可控时钟／网络验证 15 秒刷新、慢请求无重叠、手动刷新取消旧定时器、30／60 秒退避与恢复、分区旧值／时间和过期提示、离线／隐藏暂停与恢复、账号变化、401／403 停止及主动恢复、StrictMode 和卸载清理。浏览器验收连接真实后端与隔离 MySQL，验证自动读到夹具变化、真实断网恢复及会话／绑定失权，并检查 320／390／1440px 的正常及过期提示布局。护理事实由隔离夹具准备，不代表 CG03／CG05 写入已经实现。

`FamilyReportListPage.test.tsx` 从页面及网络边界验证家属报告列表的 30 个场景：身份／老人范围、服务端分页、URL 恢复、完整性及纯文本、401／403 清理、CSRF 登录、重试、慢响应和请求取消，以及 FM01／FM02 导航回归。
`FamilyReportDetailPage.test.tsx` 覆盖 26 个详情场景：深链接、列表往返、归档／模板、原文与更正、时区、401／403／404、重新登录后原报告授权、服务故障重试、旧响应隔离及取消、报告编号编码和列表返回参数。
`FamilyWeeklySummaryPage.test.tsx` 覆盖 50 个按周阅读场景：新加坡周界、跨月跨年、日期范围、老人／周次和列表往返、摘要与详情一致性、完整性与追加更正、无报告和失败区分、权限清理／登录恢复、慢响应隔离与各阶段取消。测试只替换网络边界。

日期选择仅接受 API 支持范围内的完整周，首末周的越界导航自动禁用；非法输入保留当前周。页面及日期测试覆盖年份边界和正常跨月、跨年切换。

后端 `FamilyScheduleWorkflowIT` 通过真实 HTTP、Session Cookie、CSRF 和 Testcontainers MySQL 验证登录 → 老人列表 → 排程分页 → 护理员资料／资质的完整读取流程，另覆盖绑定撤销、退出后旧会话失效、主管老人数组及 FM01 提交／查询兼容。测试使用隔离数据，不依赖本地已有账号。从项目根目录运行：

```bash
cd backend
./mvnw verify -Pintegration -Dit.test=FamilyScheduleWorkflowIT
```

需要 JDK 25 与可运行 Testcontainers 的 Docker。完整后端回归使用 `./mvnw verify -Pintegration`。这些查询测试不代替绑定确认、排程生成和护理员分配等上游写入流程的联调。

后端 `FamilyReportWorkflowIT` 验证 MG07 真实生成三种受众报告 → 家属登录 → 老人范围及报告分页 → 详情 → 精确周摘要的跨流程行为。7 个场景覆盖：服务／体征／观察／事件原文与缺失项、追加更正不改写原件或摘要、READ_ONLY 与归档读取、家属及受众隔离、同一会话下撤销／精确到期失权、退出后旧 Cookie 不可复用，以及主管、FM01／FM02 接口兼容。请求走真实 HTTP、Session／CSRF 和隔离 MySQL；数据库仅用于准备上游护理事实／绑定，并核对业务数据不变及审计持久化。

```bash
cd backend
./mvnw verify -Pintegration -Dit.test=FamilyReportWorkflowIT -Duser.timezone=UTC
```

测试使用固定的新加坡周界时间、生产 JDBC `connectionTimeZone=Asia/Singapore`，并让 MySQL 使用不同的默认时区，验证读取不依赖 JVM／数据库默认时区一致。报告及更正通过主管 API 创建；绑定撤销／到期与归档状态由隔离数据准备，不代表这些上游写入页面已经联调。FM04 首轮交付为列表、详情和 TEMPLATE 周摘要；模型摘要、周报通知及 PDF 下载仍未包含。

后端 `FamilyVisitWorkflowIT` 将真实登录、老人/排程选择和三个 FM03 分区串成 HTTP 流程，覆盖同一会话分区间撤销/精确到期、READ_ONLY 空记录、跨家属隔离、退出与旧 Cookie 重放、审计故障恢复，以及主管详情/护理员工作包/其他家属读取兼容。核对业务表不变和审计持久化；上游变化仍由隔离 SQL 模拟。

```bash
cd backend
./mvnw verify -Pintegration -Dit.test=FamilyVisitWorkflowIT -Duser.timezone=UTC
```

PR 的前端任务执行完整覆盖率测试并上传 `frontend-coverage`（包含 LCOV，保留 7 天）；后端上传 JaCoCo 并送入现有 Sonar。Sonar 当前只统计后端，前端覆盖率应查看独立报告，不混用两个口径。
