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

## 应急资源转移撤销

成功的转移可通过 `POST /api/resource-transfers/{transferId}/reversal` 按资源部分撤销，请求体包含
`requestNo`、`resourceCodes`、`operator`、`reason`。`resourceCodes` 为本次要回迁的资源编号子集
（大小写不敏感、自动去重），必须全部来自原转移记录；未选中的资源和原转移记录继续有效，
可对剩余资源再次撤销，直至全部回迁。

允许撤销的状态：

- 源事件与目标事件都必须仍处于已派发（DISPATCHED）状态；
- 所选资源必须仍归属目标事件且为 BUSY（未被再次转走或改派释放）；
- 每个资源在同一转移下只能回迁一次，重复选择返回 409；
- 撤销完成后目标事件至少保留一个资源。

任一校验失败时，资源、事件和审计记录在同一事务中保持不变。`requestNo` 用于幂等：重复提交返回首次
结果，同号但资源集合、操作人或原因不同返回 409。并发撤销范围重叠时，事件行锁保证重叠资源只会
回迁一次，后到请求返回 409。

查询方式：事件详情 `GET /api/burst-events/{id}` 中——

- `resourceTransfers`：每笔转移附带 `reversedResourceCodes`（已回迁）、`remainingResourceCodes`
  （剩余可撤销）和 `reversed`（是否全部回迁）；
- `resourceTransferTimeline`：按操作时间正序展示原转移（TRANSFER）与历次撤销（REVERSAL），
  撤销条目只包含当次实际回迁的资源编号。

