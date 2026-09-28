# Conduit

Conduit 是一个面向城市地下管网的 Spring Boot 后端项目，用于管理管网空间数据、巡检任务、隐患整改和爆管应急相关业务。项目已配置 Web、参数校验、JPA 持久化和 H2 数据库，并提供基础应用入口与上下文测试。

## 环境

- Java 21
- Maven Wrapper
- Spring Boot 4.1.1
- H2 内存数据库

## 常用命令

运行测试：

    ./mvnw test

启动应用：

    ./mvnw spring-boot:run

## 应急资源转移

已派发（DISPATCHED）的爆管事件之间可通过 `POST /api/resource-transfers` 转移应急资源，请求体包含
`sourceEventId`、`targetEventId`、`resourceCodes`、`requestNo`、`operator`、`reason`。
资源按编号（大小写不敏感、自动去重）匹配，必须为 BUSY 且当前归属源事件，转移后源事件至少保留一个资源。
`requestNo` 用于幂等：重复提交返回首次结果，同号不同内容返回 409。转移审计随事件详情
（`GET /api/burst-events/{id}` 的 `resourceTransfers`）按操作时间正序返回。

## 应急资源转移的部分撤销

一笔成功的转移可以按资源分多次部分撤销：`POST /api/resource-transfers/{transferId}/reversal`，
请求体包含 `resourceCodes`（本次要回迁到原事件的资源编号子集，编号大小写不敏感、自动去重）、
`requestNo`、`operator`、`reason`。只回迁选中的资源，其余资源与原转移记录继续有效。

允许撤销的前提（任一不满足返回 409，并在同一事务内保持资源、事件和审计记录不变）：

- 原转移记录存在（不存在返回 404）；
- 原事件与目标事件均仍为已派发（DISPATCHED）状态；
- 所选资源必须全部属于原转移范围、当前仍归属目标事件且为 BUSY；
- 所选资源此前未回迁过——每个资源在同一笔转移下只能回迁一次；
- 回迁后目标事件至少保留一个资源。

幂等与并发：撤销 `requestNo` 保证幂等，同号重复提交（资源集合、操作人、原因、转移记录均一致）
返回首次结果；同号但资源集合或其他内容不同返回 409。两个并发请求选择范围重叠时，
通过事件/资源悲观行锁与 `(transfer_id, resource_code_key)` 唯一约束保证重叠资源不会被重复回迁。

响应与查询：撤销响应中的 `resourceCodes` 是本次回迁的资源，`remainingReversibleCodes`
是该笔转移剩余可回迁的资源。事件详情 `GET /api/burst-events/{id}` 中：

- `resourceTransferTimeline` 按操作时间正序混合展示原转移（`TRANSFER`）与历次撤销（`REVERSAL`），
  每条撤销只记录当次回迁的资源；
- `resourceTransfers` 中每笔原转移带有 `reversed`（是否已全部回迁）、`reversedResourceCodes`
  （历次已回迁资源）和 `remainingReversibleResourceCodes`（剩余可撤销资源）。
