# Visit HPA D3/D4：本地实施与验证记录

后续状态：Docker已恢复，真实本地D4及D6已补验通过，见[最新记录](visit-hpa-d4-d6-local-validation.md)。下文保留D3/D4当轮的离线通过与Docker阻塞历史，不追溯改写。

更新：2026-10-19。范围仅工具、模板、交接文档；应用/公共认证/迁移SQL/部署配置未修改，没有请求线上、创建共享数据、推送或建PR。

## 交付内容

- **D3源码侧完成**：应用固定09d6a3f9ec453906f613efd0af7f99fad4fdd8d3；引擎固定c5b13e2d91b9e5daa8cb536b8427fa184f60d394；板块二候选引用ade044a8bf47c97bb0017dd4445405552ce97b07。新D3/D4材料所在本地提交见files-2/plans完成表；不以材料SHA冒充应用已部署版本。
- release-lock.json列出14个应用/发布路径的Git对象、3个引擎文件摘要及41份迁移的Git SHA256/Flyway checksum；core28/visit5/report5/notification3。
- 源码确认：旧report证据V4与新证据V5正文相同；main任务分发V4保留。旧证据V4在共享库是否执行仍UNKNOWN，由Zhili安排具名只读执行人核对；发现即停，不repair/改历史。
- **D4材料与离线核验完成**：scheduled-small-v1四组独立合成账号/老人/计划/未来visit；状态SCHEDULED:0、执行任务0/服务说明1。准备期正常注册/主管审批/发布/排班，A只读取，附属visit也登记保留。说明列出实际入口、payload、返回字段和预检/组后核验。
- 业务Owner=Chenyu；批准环境中的准备/执行/归档=Zhili；共享库只读执行人、共享数据/审计保留审批人仍待Zhili具名确认。白名单与凭据私有，默认保留标记数据、不自动删除。

入口：[D3版本/迁移前提](visit-hpa-release-prerequisites.md)、[D4合成数据步骤](visit-hpa-fixture-runbook.md)、[固定清单](../../tools/visit_hpa/release-lock.json)、[白名单样例](../../tools/visit_hpa/fixture-whitelist.example.json)。

## 本轮实际结果

| 验证 | 结果 | 证明范围 |
| --- | --- | --- |
| 新增离线工具测试 | **32通过/0失败/0错误/0跳过** | 历史/脚本/校验和、旧V4、前缀缺口/未知版本、源码/引擎改变拒绝；4组身份/归属/状态/形状/时区/有效期/15分钟核验/保留与越界/畸形输入拒绝，无DNS/socket |
| CLI正常及拒绝路径 | **8/8通过** | 固定清单、未准备样例结构通过但ready拒绝、4组配置预算与映射、越界拒绝、合成完整历史及旧V4拒绝；所有输入为人工合成单元测试数据 |
| 固定应用/引擎匹配 | **通过** | 当前应用路径及负载引擎匹配锁定值；不是云端镜像查询 |
| 缓存构建来源 | **2174文件一致，0差异** | 原本地构建源码副本与固定Git应用内容一致；本轮不重新构建、不运行JAR |
| 四个缓存生产JAR迁移 | **41/41匹配** | core28/visit5/report5/notification3，无重复旧reportV4；保存JAR/迁移条目摘要；不能代替完整字节码重建证明 |
| 实际Flyway校验器 | **41/41匹配** | Java25.0.4直接路径 + Flyway12.4.0；真实Java计算结果与清单有符号CRC32一致；SHA256另计 |
| 原D1/D2/D5回归 | **沿用70项历史通过** | 引擎和既有测试未修改，本轮未重复执行；不能声称本轮完整102项一起通过 |
| 真实core/visit/MySQL/Redis A夹具准备/读取/审计 | **阻塞，未执行** | Docker初次缺失引擎管道，尝试后台启动后仍超时；未启动业务服务、未建数据库或数据；历史真实联调不代填本轮 |
| 云端旧V4/镜像/真实fixture与保留审批 | **待确认，未连接** | 由Zhili/具名执行人提供，不属于本地工具通过证明 |

首次28项通过后补充源码变化、引擎篡改、畸形输入等4项，最终32项通过；两份计数保留。当前没有未解决的离线测试失败。8个CLI用例中的退出2是预期拒绝，不算测试失败。Docker查询阻塞单列，不算业务断言失败。

本轮还发现默认Java shim的版本查询阻塞，已精确结束本轮查询；直接JDK25.0.4可用。Docker只尝试启动，没有修复/重置Docker、拉镜像或清容器；未启动业务应用。既有local_smoke.py包含同日签到B，不当作首轮未来visit A专用验收；Docker恢复后需按本轮A步骤补验证。

## 证据与复现

证据：[去敏目录](evidence/visit-hpa-d3-d4-20261019/)。targeted-tests.json为本轮32项；initial-28为先前小范围通过；cached-build-check.json记录四JAR摘要；flyway-checksum-proof.json为实际计算器结果；cli-checks.json中的fixture/历史均为合成单元输入；runtime-blocker.json保留真实阻塞；source-hashes.json以CRLF→LF归一化的UTF-8源码SHA256固定本轮文件（便于不同系统复核），JAR/ZIP条目摘要仍为原始字节SHA256。

```text
python tools/visit_hpa/release_lock.py --manifest tools/visit_hpa/release-lock.json
python -m unittest discover -s tools/visit_hpa/tests -p test_release_fixture.py -v
python tools/visit_hpa/fixture_guard.py --whitelist tools/visit_hpa/fixture-whitelist.example.json
python tools/visit_hpa/fixture_guard.py --whitelist tools/visit_hpa/fixture-whitelist.example.json --ready
```

最后一条预期退出2（未准备/未审批占位数据）。所有命令离线。完整D6独立执行走查、真实本地A、最终PR/推送、Zhili复审与Z1～Z4仍未完成；没有正式HPA/性能结论。
