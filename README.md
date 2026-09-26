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

已完成的转移可通过 `POST /api/resource-transfers/{transferId}/reversals` 撤销，请求体包含
`requestNo`、`operator`、`reason`。撤销将原转移涉及的全部资源在同一事务中原子移回原事件，
资源保持 BUSY，并写入撤销审计；任一步失败都不会留下部分回迁或孤立审计。
仅成功且未撤销的转移可撤销：原事件与目标事件必须仍为 DISPATCHED，涉及资源必须仍归属目标事件
且为 BUSY，撤销后目标事件至少保留一个资源，否则返回 409 业务冲突。
撤销 `requestNo` 全局唯一并支持幂等：同号同内容重复提交返回首次结果，同号不同内容返回 409；
数据库唯一约束（请求号、转移记录）保证同一笔转移只发生一次实际撤销。
撤销审计随事件详情（`GET /api/burst-events/{id}` 的 `resourceTransferReversals`）按操作时间正序返回，
内容包含关联转移、涉及事件与资源、操作人、原因及时间。
