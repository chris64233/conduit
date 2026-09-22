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
