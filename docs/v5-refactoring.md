# v5 重构与验证记录

日期：2026-10-28。分支：`v5`；从 `v4` 的 `dcc4550fcca1d25732eb8516dc02bf91402aa7c2` 创建。本记录随 v5 首次重构提交一并纳入版本管理。

## 维护范围与依赖

按确认仅维护 v4 默认入口的 `wormhole`、`proxy-socks5`、`periscope-mirror`、`periscope-lens`。删除 v4/v4a 旧源码和手动启动式测试，历史可从 v4 分支检索。保留 `RahabChinaIPFilter` 和原始 IP 数据，使用 JDK 资源读取和日志移除 Keel 依赖；IP 数据仍为原 2022 年快照，不作为代理路由规则启用。

运行代码直接依赖 `io.vertx:vertx-core:5.2.2`，测试使用 JUnit Jupiter。版本按 [Vert.x 官方发布列表](https://vertx.io/blog/) 及 [官方 Starter](https://start.vertx.io/) 核实为本次最新稳定版本，未选择 5.3.0-SNAPSHOT。Java 编译目标提升为 17，迁移采用 Vert.x 5 Future API，参考 [官方迁移指南](https://vertx.io/docs/guides/vertx-5-migration-guide/)。

入口为 `io.github.sinri.rahab.v5.Rahab`，支持可选配置路径及 `--help`。先校验必填字段、端口、认证方式、凭据字节数，再部署到 Vert.x 上下文；配置、监听或首次隧道连接失败会关闭运行时并向进程报告失败。Shutdown hook 等待 Vert.x 释放资源。

## 实现与原报告对应关系

- **A01/A02**：SOCKS5 每个连接持有独立状态与缓冲区。错误凭据后发送失败响应，停止读取并关闭连接，不再进入 CONNECT。
- **A03/A04**：使用无符号长度与端口，完整消息到齐后才消费；逐阶段保留余下数据，目标连接成功后按顺序写出合并载荷。支持 IPv4、域名、IPv6；响应包含实际绑定地址和端口。不支持的命令/地址返回标准失败码。握手时限 15 秒，缓冲上限 1 MiB，连接超时 10 秒。
- **A05/A08**：TCP 两方向使用原生 `pipeTo`，由 Vert.x 将背压施加到源端。连接中暂停源端，连接失败、双方关闭和异常路径均回收另一端。连接回调发现源端已关闭时立即关闭新建目标。
- **A06/A07/A08**：Periscope 使用显式帧和连接生命周期，支持零长度控制载荷和目标先发数据。帧头分段及多帧合并均增量解析；验证长度、类型和标识长度，限制收发队列。两个监听器与共享连接表归属同一 Vert.x 上下文。Lens 顺序处理 OPEN/connect/DATA，避免重复创建目标或乱序写入；CLOSE、隧道断开及重连清理原连接。
- **A09/A10**：统一入口和 README，提供四份配置与自动校验；用 JUnit 替换依赖外部网络、没有断言的手动测试。

## Periscope 协议及部署变化

v4 在同一端口检查首个 TCP Buffer 是否恰好等于注册标记，无法可靠区分访问者载荷与拆分的标记，也无法识别未发送数据的访问者。v5 使用两个端口：`port` 服务访问者，`lens.port` 接受 Lens。Lens 的 `mirror.port` 必须指向后者。注册需要配置相同 token，且只接受一条活动 Lens 隧道，拒绝抢占。

v5 帧按网络字节序编码：

| 字段 | 长度 | 含义 |
| --- | --- | --- |
| bodyLength | 4 字节 | 有符号 int，要求 3 ≤ 长度 ≤ 1 MiB，不含本字段 |
| type | 1 字节 | 1 OPEN，2 DATA，3 CLOSE，4 PING，5 PONG，6 AUTH，7 READY |
| idLength | 2 字节 | 无符号整数，要求 ≤ 256 且不超过 bodyLength 剩余空间 |
| id | idLength 字节 | UTF-8 连接标识；控制帧使用空标识 |
| payload | 余下字节 | TCP 数据或 AUTH token；允许为空 |

Lens 发 AUTH，Mirror 常量时间比较 token 后回 READY；Lens 收到 READY 才报告启动成功。Mirror 为每个访问者生成 UUID，发 OPEN；Lens 建立目标连接后处理 DATA。任意端关闭会发送 CLOSE，连接失败也关闭对应访问者。每 5 秒发送心跳，20 秒未处理到完整对端帧则关闭隧道。初次连接或认证失败报告启动失败；已建立的隧道断开后每秒重连，重连失败继续重试。

最多 4096 条连接；每条隧道的收发待处理队列分别限制为 4 MiB。发送堵塞会暂停数据源；接收顺序等待 connect/write 完成后才继续读，避免无界累积。共享隧道存在队头阻塞；超限或心跳超时会关闭整条隧道。不会恢复或重放旧 TCP 会话。

这是一项有意的协议变更：Mirror/Lens 必须成对升级，新增独立端口和 token；v4 帧不兼容。TCP 通道未加密，token 是注册认证而非加密机制。配置示例默认监听回环地址；实际部署需调整绑定地址、网络可达性和共享 token。

## 验证

环境：macOS arm64，Maven 3.9.16。使用独立 Maven Central 配置与空的 `/tmp/rahab-m2` 依赖仓库，避免本机历史 SNAPSHOT 和私有仓库影响依赖验证。最终以 Temurin **17.0.20.1** 执行 `mvn clean verify`（另传 `-s /tmp/rahab-maven-settings.xml -Dmaven.repo.local=/tmp/rahab-m2`），全部通过：

| 测试类 | 数量 | 覆盖 |
| --- | ---: | --- |
| `NetworkTest` | 12 | 独立认证、认证失败后 CONNECT、长凭据、高端口、长域名、本地 hosts 解析、拆合包与载荷、不支持命令/地址、连接拒绝、双端关闭、慢目标及大流字节完整性 |
| `PhotonTest` | 3 | 逐字节帧头/载荷、多帧合并、截断等待、负数/过大/不一致长度及非法类型 |
| `PeriscopeTest` | 8 | 多路复用、目标先发、认证与拒绝抢占、缺少 Lens、目标失败、非法帧、心跳不建立目标、隧道断线清理与 Mirror 重启重连 |
| `ConfigTest` | 4 | 缺少字段、未知模式/认证、非法端口、注册端口及 token、四份发布配置 |
| `IPFilterTest` | 1 | 原 IP 数据读取、明确范围边界及范围外地址 |
| **合计** | **28** | **0 失败、0 错误、0 跳过** |

另外以 JDK 17 启动实际打包的 `target/Rahab-5.0-SNAPSHOT.jar`，对全部四种模式执行独立 Java 进程级回环冒烟：Wormhole 和 SOCKS5 字节往返，Mirror/Lens 成对启动及字节往返，最终终止进程并等待退出。

运行代码和 POM 已无 Keel import 或依赖，Shade 合并服务注册文件并排除依赖签名。构建产物约 7.4 MiB。原报告的 v4 离线构建失败仍作为历史记录保留，不视为 v5 的验证结果。

验证边界：本地回环测试不等同于生产负载、跨网络长期稳定性或全部 IPv6/DNS 环境验证。macOS 上 Netty 会提示缺少可选原生 DNS 模块并回退系统实现；测试未依赖外部 DNS。尚未执行远程部署。
