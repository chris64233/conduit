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

## API 概览

### 管段管理 `/api/pipe-segments`

- `POST /api/pipe-segments`：创建管段，成功返回 201。`code` 忽略大小写全局唯一（重复返回 409）；`utilityType` 支持 `WATER`、`GAS`、`SEWAGE`、`HEAT`；`status` 支持 `ACTIVE`、`OUT_OF_SERVICE`。
- `GET /api/pipe-segments/{id}`：查询管段详情，不存在返回 404。
- `GET /api/pipe-segments`：分页查询管段列表，按 `code` 升序。支持 `utilityType`、`status` 筛选，以及 `minLongitude`、`minLatitude`、`maxLongitude`、`maxLatitude` 矩形范围重叠查询（四个参数需同时提供且最小值小于最大值）；`page`、`size` 控制分页。

参数错误统一返回 400，错误响应格式为 `{"timestamp": ..., "status": ..., "message": ...}`。
