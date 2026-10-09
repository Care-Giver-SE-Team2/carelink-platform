# 护理员端　Caregiver client

**负责人：**（待认领）

## 要覆盖的用例

- CG01 查看班表与资质预警
- CG02 提交请假与设置接单偏好
- CG03 执行上门访视
- CG04 上报照护异常
- CG05 离场核销与服务确认
- CG06 回溯历史工单自证

## 布局要求

移动优先。单手操作，现场使用，可能戴手套、可能网络不好。

## 约定

- **这个文件夹只有你一个人改。** 别人不会碰这里的文件，你也不要去改别人的角色目录。
- 通用的东西放 `src/shared/`：按钮、表单、表格这类组件放 `shared/components/`，
  样式变量放 `shared/theme/theme.css`，调后端一律走 `shared/api/client.ts`。
  **要改 `shared/` 先在群里说一声**，那是五个人共用的。
- 业务逻辑放 `src/features/<模块>/`，不要堆在页面组件里。
- 老人端与其他三端的差别（字号、对比度、点击区大小）已经做在
  `shared/theme/theme.css` 的 `[data-theme="elder"]` 里，
  用 `<RoleShell theme="elder">` 就能拿到，不用自己写一套。

## 现阶段

三个只读切片已连接真实后端：登录 → 我的班表 → 已分配 Visit 工作包、续证资质提醒、班表变化与取消保护。
仅护理员本人可读；不包含签到、任务提交、证据上传和签退。
完整启动、演示账号与人工验收见仓库 `docs/caregiver/slice-1-demo.md`。
续证场景见 `docs/caregiver/slice-2-credential-alerts.md`；统一 CG-01 验收及变化演示见 `docs/caregiver/slice-3-schedule-changes.md`。
不要在页面中写死演示数据；演示数据通过独立 SQL 加入本地数据库。

## 本地运行

```bash
cd frontend
npm ci
npm run dev
```

然后打开 http://localhost:5173/caregiver

## Batch 1A: caregiver leave self-service

`/caregiver/absences` lets the signed-in caregiver submit whole-day leave and read
their own pending, approved and rejected requests. It reuses the MG04 endpoints;
approval and explicit re-rostering remain separate manager actions. Requests are
not retried automatically. Refresh or return to the page to read decisions.

The caregiver navigation preserves the schedule date range when moving between
the schedule, a work pack and self-service pages. Leave does not include editing,
withdrawal, work preferences or availability in this slice.

## Batch 1B: spot-check conclusions and responses

`/caregiver/spot-checks` reads only the signed-in caregiver's completed checks.
The caregiver can respond or update their current response (up to 500 characters),
without changing the manager's finding. Drafts survive refresh in this page's
memory and are discarded on access loss or navigation away.

The notification bell opens `/caregiver/spot-checks?spotCheckId=<id>`. The page
locates that id only within the authorized collection; no manager detail endpoint
is used. Managers see saved responses by refreshing their existing Quality page.
