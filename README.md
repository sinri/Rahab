# Rahab 5

> By faith the harlot Rahab perished not with them that believed not, when she had received the spies with peace. (Hebrews 11:31)

Rahab 是直接基于 Vert.x 的异步 TCP 转发、SOCKS5 代理和反向隧道工具。
`v5` 从 `v4` 分支重构，使用 Vert.x **5.2.2**，不再依赖 Keel。
维护范围为原 `v4` 默认入口的四种模式；旧 `v4a` 的 HTTP CONNECT、Liaison、Consulate 和 HTTP 数据变换不再编译或发布，历史源码保留在 `v4` 分支。

## 构建与启动

需要 JDK 17+ 和 Maven 3.9+：

```sh
mvn clean verify
java -jar target/Rahab-5.0-SNAPSHOT.jar --help
java -jar target/Rahab-5.0-SNAPSHOT.jar examples/wormhole.properties
```

无参数时读取当前目录的 `config.properties`。配置错误、端口占用或首次建立隧道失败会以非零状态退出。Ctrl+C 关闭 Vert.x 和网络资源。DNS 默认使用系统配置，可通过顶层 `dns.servers=1.1.1.1,8.8.8.8` 覆盖。日志使用 JDK/Vert.x 默认日志实现，旧 Keel 的 `log.*` 配置已移除。

## 运行模式

| `rahab.program` | 行为 | 配置示例 |
| --- | --- | --- |
| `wormhole` | 双向 TCP 转发到固定目标 | [wormhole.properties](examples/wormhole.properties) |
| `proxy-socks5` | SOCKS5 TCP CONNECT，匿名或用户名密码认证 | [socks5.properties](examples/socks5.properties) |
| `periscope-mirror` | 接受访问者及 Lens 注册，多路复用反向隧道 | [periscope-mirror.properties](examples/periscope-mirror.properties) |
| `periscope-lens` | 主动连接 Mirror，为每个访问者连接目标服务 | [periscope-lens.properties](examples/periscope-lens.properties) |

监听地址由 `rahab.<模式>.host` 控制，默认 `0.0.0.0`；示例仅监听回环地址，部署时按需要调整。端口范围为 1–65535。SOCKS5 支持 IPv4、域名和 IPv6，BIND、UDP ASSOCIATE 返回不支持。握手总时限为 15 秒，目标连接超时为 10 秒。

## Periscope v5 迁移

```text
访问者 → Mirror:port ←→ Mirror:lens.port ← Lens → 目标服务
                                          主动连接
```

功能仍是从外部访问 Lens 可达的目标服务。v5 将访问者端口与 Lens 注册端口分离，避免将任意 TCP 载荷误识别为注册消息，也支持目标先发数据的协议。

- Mirror 新增 `rahab.periscope-mirror.lens.port`，必须与访问者 `port` 不同。
- Lens 的 `mirror.port` 指向 Mirror 的 `lens.port`。
- 两端新增相同的 `token`，拒绝未知 Lens，也拒绝第二条活动隧道抢占。
- 两端必须一起升级；v5 帧协议与 v4 不兼容，详细格式见 [迁移与验证记录](docs/v5-refactoring.md)。
- 每 5 秒交换心跳，20 秒未处理到对端完整帧时关闭隧道。已经认证的连接断开后，每秒尝试重连；旧访问者连接全部关闭，由调用方重新建立，不重放旧数据。

每条隧道最多承载 4096 条连接，单帧上限为 1 MiB，收发待处理队列分别限制为 4 MiB。流控会暂停源端读取；队列超限或非法帧会关闭隧道并清理关联连接。共享隧道采用顺序处理，慢目标会对同一隧道的其他连接产生背压。

`token` 用于注册认证，TCP 隧道本身不提供加密；跨不可信网络部署时，应放在受保护的网络或加密传输通道内。

## 验证与分析

`mvn clean verify` 运行有断言、超时和资源清理的本地回环测试，覆盖 SOCKS5 认证隔离、拆合包、长字段、高端口、连接失败，TCP 流控与双端关闭，以及 Periscope 分帧、认证、并发连接、心跳、断线清理和重连。

原始现状与问题编号见 [项目分析报告](docs/project-analysis.md)，实现与验证对应关系见 [v5 重构记录](docs/v5-refactoring.md)。
