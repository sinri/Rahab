# Rahab 项目分析与后续行动

分析日期：2026-10-28。分析基线：`v4` 分支，提交 `dcc4550`。

本文保留 v4 基线的静态分析结论和验收标准。2026-10-28 已在基于 `dcc4550` 创建的 `v5` 分支实施重构，维护范围经确认仅为 v4 默认入口的四种模式。最新实现与验证记录见 [v5 重构记录](v5-refactoring.md)。下文架构与发现描述针对原 v4；源码链接固定到分析基线。

## 项目定位

Rahab 是基于 Java、Vert.x 和自有 Keel 框架的网络代理与隧道工具。项目采用 Maven 构建，Java 编译目标为 11，直接依赖 `io.github.sinri:Keel:2.8-SNAPSHOT`，通过 Keel 使用异步网络、日志和 Socket 包装能力。

仓库包含两套实现：

| 目录 | 功能 | 启动方式 |
| --- | --- | --- |
| `src/main/java/io/github/sinri/rahab/v4` | TCP 转发、SOCKS5、Periscope 反向隧道 | `config.properties` |
| `src/main/java/io/github/sinri/rahab/v4a` | TCP 转发、HTTP CONNECT、SOCKS5、Liaison、WebSocket 隧道及 HTTP 数据变换 | 命令行 `--mode` |

`pom.xml` 中 Shade 插件的实际入口为 `io.github.sinri.rahab.v4.Rahab`。README 的 `3.0.2.jar -h` 示例及部分设计说明与此入口不一致。目录命名不能单独作为实现先后或维护状态的判断依据；后续需明确两套实现的维护范围。

## v4a 来源与保留价值（补充核查）

Git 历史表明，`v4a` 是重写期间迁移保留的旧实现。迁移事实可以确认，但没有找到明确解释字母 `a` 含义或正式废弃声明的记录。

| 日期 | 提交 | 变化 |
| --- | --- | --- |
| 2022-08-12 | `5205a6f` | 将原 `io.github.sinri.Rahab.v3` 的主体迁移到 `io.github.sinri.rahab.v4`，使用 Keel 2.7.2 |
| 2022-08-22 | `b80b8ab` | 将原有代理、Consulate、Liaison、执行器等主体从 `v4` 迁移到 `v4a`，同时在 `v4` 开始新的 Socket 包装实现和 Periscope，并升级 Keel 2.8-SNAPSHOT |
| 2022-08-22 | `a4f7740` | 将原顶层命令行入口迁移到 `v4a.Rahab`，新增配置驱动的 `v4.Rahab`，将打包入口切换至后者 |
| 2022-08-23 | `dcc4550` | 当前分析基线；迁移后的后续实现工作主要落在 `v4` |

在本次查看的截至 `dcc4550` 的路径历史中，`v4a` 最后一次修改为 `a4f7740`。因此可以将其定位为旧架构保留代码；不能将它理解为比当前 `v4` 更晚的升级版。

`v4a` 仍参与同一个 Maven 模块的编译和打包，不是单独发布的模块。两套目录之间未发现直接的 Java 包引用；当前默认入口不会调用 `v4a`。在成功生成包含依赖的 JAR 后，可以显式选择旧入口，例如：

```sh
java -cp target/Rahab-4.0-SNAPSHOT.jar io.github.sinri.rahab.v4a.Rahab --help
```

以上命令仅依据入口和打包配置给出，尚未运行验证。`v4a` 入口会先读取配置并初始化 Vert.x，再处理参数。

| 能力 | v4a | 当前 v4 |
| --- | --- | --- |
| 固定目标 TCP 转发 | Verticle/Worker 实现 | Socket Wrapper 实现 |
| SOCKS5 CONNECT | 有实现；命令行执行器使用匿名认证 | 有实现；配置入口支持匿名和用户名密码认证 |
| HTTP CONNECT 代理 | 有 | 未见对应实现 |
| TCP 数据的 HTTP 外观封装 | Wormhole Transformer | 未见对应实现 |
| 反向隧道 | Liaison Broker/Source | Periscope Mirror/Lens；不能假定协议兼容 |
| WebSocket 承载 SOCKS5 数据 | Consulate Client/Server | 未见对应实现 |

旧架构主要采用监听 Verticle 加每连接 Worker Verticle；新 `v4` 更多依赖 Keel Socket Wrapper 管理异步数据处理。`v4a` 的 HTTP CONNECT 是基于 TCP 数据和正则解析的隧道实现，不宜将其等同于完整通用 HTTP 正向代理。

后续处置建议：保留 `v4a` 作为功能迁移参考，先确定 HTTP CONNECT、HTTP 封装、Liaison 和 Consulate 是否仍需支持，再逐项迁移或明确退役。不能只因默认入口未引用而直接删除。它的运行可靠性也不能由历史存在或功能数量推定；构建验证仍受 Keel 依赖缺失限制。

## 当前默认入口与数据链路

入口为 [v4/Rahab.java](https://github.com/sinri/Rahab/blob/dcc4550fcca1d25732eb8516dc02bf91402aa7c2/src/main/java/io/github/sinri/rahab/v4/Rahab.java)，读取 `config.properties`，初始化 Keel/Vert.x 和 DNS，然后根据 `rahab.program` 选择模式。

| 模式 | 行为 |
| --- | --- |
| `wormhole` | 将进入的 TCP 数据转发到固定目标 |
| `proxy-socks5` | SOCKS5 CONNECT，支持匿名或用户名密码认证；BIND、UDP ASSOCIATE 未实现 |
| `periscope-mirror` | 接受访问者连接和 Lens 注册，按连接标识中转数据 |
| `periscope-lens` | 主动连接 Mirror，将访问者数据转发至配置的目标服务 |

Periscope 的连接关系：

```text
访问者 → Mirror ← Lens → 目标服务
                 主动连接
```

Mirror 与 Lens 之间使用 `Photon` 帧区分不同访问者。编码包含总长度、连接标识长度、连接标识、内容长度和内容，从而在同一条隧道连接中承载多个访问者的数据。

主要代码位置：

- [TCP 转发](https://github.com/sinri/Rahab/blob/dcc4550fcca1d25732eb8516dc02bf91402aa7c2/src/main/java/io/github/sinri/rahab/v4/wormhole/TerminalSocketWrapper.java)
- [SOCKS5 协议状态与转发](https://github.com/sinri/Rahab/blob/dcc4550fcca1d25732eb8516dc02bf91402aa7c2/src/main/java/io/github/sinri/rahab/v4/proxy/socks5/TerminalSocketWrapper.java)
- [用户名密码认证](https://github.com/sinri/Rahab/blob/dcc4550fcca1d25732eb8516dc02bf91402aa7c2/src/main/java/io/github/sinri/rahab/v4/proxy/socks5/auth/impl/RahabSocks5AuthMethod02.java)
- [Periscope Mirror](https://github.com/sinri/Rahab/blob/dcc4550fcca1d25732eb8516dc02bf91402aa7c2/src/main/java/io/github/sinri/rahab/v4/periscope/PeriscopeMirror.java)
- [Periscope Lens](https://github.com/sinri/Rahab/blob/dcc4550fcca1d25732eb8516dc02bf91402aa7c2/src/main/java/io/github/sinri/rahab/v4/periscope/PeriscopeLens.java)
- [Photon 编码](https://github.com/sinri/Rahab/blob/dcc4550fcca1d25732eb8516dc02bf91402aa7c2/src/main/java/io/github/sinri/rahab/v4/periscope/Photon.java)及[解析](https://github.com/sinri/Rahab/blob/dcc4550fcca1d25732eb8516dc02bf91402aa7c2/src/main/java/io/github/sinri/rahab/v4/periscope/PhotonProcessor.java)

## 静态分析发现

以下编号用于后续任务追踪。优先级是建议修复顺序，不代表已经完成运行时严重性评估。

| 编号 | 优先级 | 代码证据与问题 | 可能影响 |
| --- | --- | --- | --- |
| A01 | P0 | `RahabSocks5AuthMethod02.verifyIdentity()` 在校验为 `false` 时发送失败响应，但写入成功后仍返回成功 Future；`handlerStep2()` 随后进入目标确认阶段 | 客户端忽略失败响应后可能继续请求连接，认证状态未阻断 |
| A02 | P0 | SOCKS5 服务共享认证实例；`handlerStep1()` 对实例调用 `setSocketWithClient(this)`，修改其连接字段 | 多个客户端交错执行时，认证响应可能写入其他连接 |
| A03 | P1 | `RFC1928AddressBytesParser` 用 `getShort()` 读取端口，并将 `short` 直接传给 `connect()`；域名、认证字段长度和方法数量使用 `byte` | 高端口被解释为负数，长度大于 127 时可能解析错误 |
| A04 | P1 | SOCKS5 各阶段直接解析单次 Buffer，未见完整消息累积和消费机制；地址解析器保存 `restBuffer`，调用方未处理 | 拆包可能越界；同一 Buffer 中的后续消息或载荷可能丢失 |
| A05 | P1 | Wormhole 在目标写队列满时调用目标 Socket 的 `pause()`，排空时恢复的也是目标 Socket | 未暂停客户端输入，无法有效控制该方向的数据积压，同时可能阻塞反向读取 |
| A06 | P1 | Mirror 用首个 Buffer 的完整字符串匹配注册标记；`PhotonProcessor` 只排除空 Buffer 就读取 8 字节长度，且未完整校验长度范围与一致性 | 注册拆合包导致误分类；短帧或非法长度可能导致解析异常 |
| A07 | P1 | Mirror 收到 Lens 心跳后，将 `photon.toBuffer().toString()` 与 `PING` 比较 | 比较对象包含编码头，正常心跳无法匹配预期分支 |
| A08 | P1 | SOCKS5 的 `whenClose()` 仅调用父类，未显式关闭其持有的目标连接；Periscope 未见完整的跨端连接关闭通知 | 目标连接可能在访问者退出后继续存活，资源回收依赖其他关闭事件 |
| A09 | P2 | README 示例、POM 版本与实际入口不一致；两套实现的维护状态未说明 | 使用者容易按照不适用的参数启动程序 |
| A10 | P1 | `src/test` 主要是 `main()` 启动式手动测试，未见常规自动断言测试 | 无法稳定回归协议边界、并发认证及断线行为 |

Keel 的 `KeelAbstractSocketWrapper`、`KeelBasicSocketWrapper` 和 `KeelPieceKit` 承担了部分调度、缓冲与错误处理。涉及这些能力的端到端影响，需要结合准确版本的 Keel 源码和运行测试确认；不应直接推定父类已补齐所有处理。

## 构建与验证记录

本次执行 `mvn -o test`，在依赖解析阶段失败：本机未缓存 `Keel 2.8-SNAPSHOT`，离线模式无法从 Maven 配置中的仓库获取该依赖。

因此，本次未验证编译成功、测试通过或实际网络行为。该结果只说明离线构建条件不满足，不能据此判断代码是否可编译，也不能判断在线仓库是否仍提供该版本。

## 后续行动清单

以下任务已在 v5 按确认的维护范围处理；逐项证据见文末和 v5 重构记录。原 v4 的离线构建失败记录保留为历史事实。

- [x] **恢复可重复构建**：按新要求直接移除 Keel，使用 Maven Central 的 Vert.x 5.2.2；JDK 17 完整构建通过，未以其他 Keel 版本冒充原依赖。
- [x] **明确维护范围（A09）**：确认以 `v4` 为默认主线还是调整入口，说明 `v4a` 的用途，更新 README 和可用配置示例。
- [x] **修复认证流程（A01、A02）**：认证失败后关闭连接或明确终止状态推进；每个连接拥有独立认证上下文。验收需覆盖错误密码后继续发送 CONNECT，以及两个客户端交错认证。
- [x] **修复字段与分帧（A03、A04）**：端口和长度按无符号数解释；累积不完整协议消息并保留剩余字节。验收覆盖高端口、长域名、逐字节输入、合并输入和截断输入。
- [x] **完善流控与清理（A05、A08）**：目标写队列满时暂停对应源端读取，排空后恢复；统一双端关闭和错误路径。验收覆盖慢接收端、目标连接失败、客户端先关闭及目标先关闭。
- [x] **完善 Periscope（A06、A07、A08）**：实现可拆包的注册识别，校验帧头和长度，修正心跳内容判断，补齐连接关闭通知与重连后的状态清理。验收覆盖帧头拆分、多帧合并、非法长度、心跳往返及隧道断线。
- [x] **建立自动回归（A10）**：为上述行为添加有断言、有超时且能自动释放资源的测试；优先使用本地回环服务，减少外部网络依赖。

## v5 实施结果（2026-10-28）

基线提交为 `dcc4550fcca1d25732eb8516dc02bf91402aa7c2`；本轮实现及验证记录随 `v5` 首次重构提交一并纳入版本管理。v4/v4a 旧实现已从 v5 源码删除，历史保留在 v4 分支。保留原 IP 范围数据及辅助工具，仅移除其 Keel 调用。

| 项目 | v5 结果及证据 |
| --- | --- |
| A01、A02 | 独立 `Socks5Session`，失败响应后终止并关闭；错误密码后发送 CONNECT、双客户端交错认证均有断言 |
| A03、A04 | 无符号长度/端口和增量消费；长凭据、长域名、高端口、逐字节 CONNECT、合并握手与载荷测试通过 |
| A05、A08 | `TcpRelay` 双向 pipe 和关闭联动；慢目标、连接拒绝、客户端/目标先关闭测试通过 |
| A06、A07、A08 | Periscope 独立注册端口、共享 token、长度校验、显式 OPEN/DATA/CLOSE/PING/PONG；并发、目标先发、异常帧、心跳、断线清理及重连测试通过 |
| A09 | README 改为实际 v5 入口，提供四份经校验的配置，注明 Periscope 协议和配置不兼容点 |
| A10 | JUnit 自动测试共 28 项，JDK 17 下 `clean verify` 全部通过；可执行 JAR 的四种模式另作进程级冒烟验证 |

验证限于本地回环网络和当前配置；不据此推断生产环境容量、所有 IPv6/DNS 部署环境或长期稳定性。测试中曾出现沙箱禁止绑定端口的环境失败，已在获准使用本地网络的环境下完成验证。另一次新增 IP 工具测试误将原示例中的地址认定为范围内地址，核对 2022 年数据后改为明确的范围边界断言；未修改原 IP 数据来迁就测试。
