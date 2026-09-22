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

## 爆管应急资源转移

在两个均处于 `DISPATCHED` 状态的爆管事件之间转移应急资源：

    POST /api/burst-events/resource-transfers

请求体包含 `sourceEventId`、`targetEventId`、`resourceCodes`、`requestNo`、`operator`、`reason`。
接口通过 `requestNo` 支持幂等，成功后在事件详情（`GET /api/burst-events/{id}`）的 `transfers` 字段中
返回与该事件相关、按操作时间正序排列的转移审计记录。
