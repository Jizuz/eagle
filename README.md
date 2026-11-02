# Eagle 配置中心

基于 **ZooKeeper（推送通道）+ MySQL（权威存储）** 的自研轻量配置中心。

## 架构

```
┌─────────────┐   REST(管理)    ┌──────────────┐    写入JSON     ┌───────────────┐
│  管理后台页面  │ ──────────────▶ │  eagle-admin │ ──────────────▶ │  ZooKeeper集群 │
└─────────────┘                 │  (8080)      │  /config/{env}  │  2181,2182,2183│
                                └──────┬───────┘  /{group}/{key} └───────┬───────┘
                                       │ upsert/快照/发布记录                     │ watch推送
                                       ▼                                        ▼
                                ┌──────────────┐                        ┌──────────────┐
                                │    MySQL     │                        │ eagle-client │
                                │  eagle库3表   │                        │  内存+磁盘缓存  │
                                └──────────────┘                        └──────────────┘
```

- **eagle-admin**（8080）：管理REST服务。发布事务 = 类型强校验 → DB事务（主表upsert + 版本快照 + 发布记录）→ 事务提交后幂等写ZK；ZK失败标记失败并告警，支持手动重试。
- **eagle-client**：客户端SDK。CuratorCache监听`/config/{env}/{group}`子树秒级感知变更；内存缓存 + 磁盘快照（`~/.eagle/cache/`）双级降级；断连自动降级、重连自动对账；Spring Boot自动装配 + `@RefreshScope`实时刷新。
- **eagle-common**：枚举/DTO/类型校验器/统一响应。
- **eagle-demo**（8081）：order-service演示应用。

## 快速开始

### 1. 环境准备

```bash
# MySQL: 创建库表（本地已初始化可跳过）
mysql -uroot -p < sql/init.sql

# ZooKeeper 3节点集群（Docker）
docker run -d --name zk1 -p 2181:2181 zookeeper:3.8.4
docker run -d --name zk2 -p 2182:2181 zookeeper:3.8.4
docker run -d --name zk3 -p 2183:2181 zookeeper:3.8.4
```

### 2. 构建

```bash
mvn clean package -DskipTests
```

### 3. 启动

```bash
# 管理服务
java -jar eagle-admin/target/eagle-admin-1.0.0.jar

# 演示应用（默认8081，若端口被占用可覆盖：--server.port=18081）
java -jar eagle-demo/target/eagle-demo-1.0.0.jar
```

### 4. 发布配置（API示例）

```bash
# 新增配置（立即推送ZK）
curl -X POST http://localhost:8080/api/config \
  -H 'Content-Type: application/json' \
  -d '{"env":"dev","groupId":"order-service","configKey":"switch","valueType":"boolean","configValue":"true","description":"支付开关","operator":"tester"}'

# 修改配置
curl -X PUT http://localhost:8080/api/config/{id} \
  -H 'Content-Type: application/json' \
  -d '{"valueType":"number","configValue":"5000","operator":"tester"}'

# 配置列表 / 详情 / 历史版本 / 发布预览
curl 'http://localhost:8080/api/config/page?env=dev&groupId=order-service'
curl http://localhost:8080/api/config/{id}
curl http://localhost:8080/api/config/{id}/histories
curl http://localhost:8080/api/config/{id}/preview

# 回滚到历史版本
curl -X POST 'http://localhost:8080/api/config/{id}/rollback/{historyId}?operator=tester'

# 删除配置（软删 + DELETE快照 + ZK节点删除）
curl -X DELETE 'http://localhost:8080/api/config/{id}?operator=tester'

# 发布记录 / 失败重试
curl 'http://localhost:8080/api/publish/page?publishStatus=2'
curl -X POST http://localhost:8080/api/publish/{id}/retry
```

### 5. 客户端验证（demo）

```bash
curl http://localhost:8081/demo/config    # SDK泛型取值 + 降级状态
curl http://localhost:8081/demo/value     # @RefreshScope注入值（变更后自动刷新）
curl http://localhost:8081/demo/changes   # 最近变更事件
```

## 客户端SDK接入

```yaml
eagle:
  config:
    enabled: true
    zk-address: 127.0.0.1:2181,127.0.0.1:2182,127.0.0.1:2183
    env: dev
    group-id: order-service
```

```java
@Autowired
private EagleConfigClient client;

Boolean enabled = client.getConfig("switch", Boolean.class, false);
Integer timeout = client.getConfig("timeout", Integer.class, 3000);

client.addChangeListener(event ->
        log.info("配置{}: {} -> {}", event.getKey(), event.getOldNode(), event.getNewNode()));
```

`@Value` + `@RefreshScope` 实时刷新：

```java
@Component
@RefreshScope
public class PayService {
    @Value("${switch:false}")
    private Boolean switchFlag;  // 后台改配置后自动更新
}
```

## 类型强校验规则

| 类型   | 规则                                 |
|--------|--------------------------------------|
| string | 无严格限制，允许空字符串              |
| number | `^-?\d+(\.\d+)?$`（整数或小数，可负） |
| boolean| 仅小写 `true` / `false`               |

新增和修改（含修改类型）时后端强校验；客户端SDK按服务端类型做安全转换。

## 降级与恢复

- ZK断连 → 客户端标记`degraded`，继续用内存缓存提供服务
- 服务重启且ZK不可用 → 加载磁盘快照`~/.eagle/cache/{env}/{group}.json`
- ZK恢复 → 自动退出降级并全量对账，期间变更不丢失
- admin发布时ZK不可用 → DB落库成功、发布记录标记失败 + 告警日志，`POST /api/publish/{id}/retry`恢复
