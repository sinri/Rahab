# Rahab v5 使用说明

更新日期：2026-10-29。适用于 `Rahab-5.0-SNAPSHOT.jar`。本文从已经取得打包 JAR 开始，说明配置、运行、部署和排查方法。

## 1. 选择运行模式

一个 Java 进程运行一种模式；需要多个服务时，分别启动进程并使用不同的配置文件。

| 模式 | 适用场景 | 连接方向 |
| --- | --- | --- |
| `wormhole` | 将本机一个 TCP 端口转发到固定服务 | 访问者 → Wormhole → 目标服务 |
| `proxy-socks5` | 由客户端为每条 TCP 连接指定目标地址 | SOCKS5 客户端 → 代理 → 目标服务 |
| `periscope-mirror` | 在访问者可达的机器上提供反向隧道入口 | 访问者 → Mirror ← Lens |
| `periscope-lens` | 从内网主动连接 Mirror，访问内网目标服务 | Mirror ← Lens → 目标服务 |

当前没有 HTTP CONNECT、UDP 转发、SOCKS5 BIND/UDP ASSOCIATE、Liaison、Consulate 或 HTTP 数据变换模式。Wormhole 可以承载 HTTP 服务的 TCP 流量，但不会解析或改写 HTTP 请求。

## 2. 准备文件与启动

运行环境需要 Java 17 或更高版本，并且 `java` 命令可用：

```sh
java -version
```

将包含运行依赖的 `Rahab-5.0-SNAPSHOT.jar` 放到任意部署目录，再按下文创建所需的配置文件。运行时不需要 Maven，也不需要单独安装 Vert.x 或准备额外依赖目录。若收到的文件还包括 `original-Rahab-5.0-SNAPSHOT.jar`，请使用不带 `original-` 前缀的 JAR。

本文示例均在同一个部署目录下执行，文件按需创建：

```text
rahab/
├── Rahab-5.0-SNAPSHOT.jar
├── wormhole.properties
├── socks5.properties
├── periscope-mirror.properties
└── periscope-lens.properties
```

```sh
# 查看命令格式；-h 与 --help 等价
java -jar Rahab-5.0-SNAPSHOT.jar --help

# 指定要运行的配置文件
java -jar Rahab-5.0-SNAPSHOT.jar wormhole.properties

# 无参数时读取当前工作目录下的 config.properties
java -jar Rahab-5.0-SNAPSHOT.jar
```

仅支持一个可选的配置路径参数。没有 `--mode`、`--port` 或 `--config` 参数。相对路径按启动进程的当前工作目录解析，不按 JAR 所在目录解析；配置文件必须以外部文件提供。也可以同时使用 JAR 和配置文件的绝对路径启动。

启动成功后会向标准输出打印对应模式，例如：

```text
Rahab 5 started: wormhole
```

Wormhole/SOCKS5 的成功提示表示已监听端口，不表示目标服务已连通；目标连接在访问者发起连接时建立。Mirror 的成功提示表示两个端口已监听，不表示 Lens 已注册。Lens 的成功提示表示已连接 Mirror 并完成 token 认证，不表示目标服务已连通。

配置错误、端口绑定失败、Lens 首次连接或认证失败会导致启动失败并以非零状态退出。修改配置后需重启；当前没有热加载。前台运行时按 Ctrl+C 停止，程序会等待清理网络资源，最多等待 10 秒。

## 3. 配置文件通则

配置为 Java `.properties` 格式：

```properties
rahab.program=wormhole
rahab.wormhole.host=127.0.0.1
rahab.wormhole.port=8080
rahab.wormhole.target.host=127.0.0.1
rahab.wormhole.target.port=8000
```

- `rahab.program` 必填，值必须与模式名完全一致。
- 模式配置键以 `rahab.<模式名>.` 开头；其他模式的配置不参与当前模式启动。
- 所有端口均为 1–65535 的十进制整数，不能配置为 `0`。
- 三种监听模式的 `host` 缺省为 `0.0.0.0`；示例显式设为 `127.0.0.1`。Lens 不监听本地端口，也没有生效的 `host` 配置。
- 配置值不要加 shell 式引号；例如 `token="abc"` 的双引号会成为 token 的一部分。程序没有环境变量插值，`${TOKEN}` 会被当作普通文本。
- 配置文件建议使用 ASCII；非 ASCII 字符应写为 `\uXXXX` 转义，例如“中”写为 `\u4E2D`。不要直接填写 UTF-8 中文。反斜杠和续行遵循 Java properties 规则。
- 未识别的配置键不会自动报错；排查配置时应核对完整键名。旧版的 `log.*` 配置已不生效。

可在任意模式下添加顶层 DNS 配置：

```properties
# 可选；省略时使用系统 DNS 配置
# 多个服务器以英文逗号或空白分隔
dns.servers=1.1.1.1,8.8.8.8
```

SOCKS5 客户端将域名交给代理时，由代理解析；Wormhole 的目标域名和 Lens 的 Mirror/目标域名则由各自进程解析。配置内含密码或 token 时，应限制文件读取权限，不要把配置文件放入目标 HTTP 服务公开提供下载的目录。

## 4. Wormhole：固定 TCP 转发

在部署目录创建 `wormhole.properties`：

```properties
rahab.program=wormhole
rahab.wormhole.host=127.0.0.1
rahab.wormhole.port=8080
rahab.wormhole.target.host=127.0.0.1
rahab.wormhole.target.port=8000
```

| 配置键（前缀 `rahab.wormhole.`） | 必填 | 默认值或含义 |
| --- | --- | --- |
| `host` | 否 | `0.0.0.0`，本地监听地址 |
| `port` | 是 | 本地监听端口 |
| `target.host` | 是 | 从 Wormhole 所在机器访问的目标地址 |
| `target.port` | 是 | 目标 TCP 端口 |

示例将 `127.0.0.1:8080` 转发至 `127.0.0.1:8000`。如需本地验证，可在一个终端启动临时 HTTP 服务（此验证步骤需要 Python 3，Rahab 本身不依赖 Python）：

```sh
mkdir -p http-demo
python3 -m http.server 8000 --bind 127.0.0.1 --directory http-demo
```

另开一个终端，在部署目录启动 Wormhole：

```sh
java -jar Rahab-5.0-SNAPSHOT.jar wormhole.properties
```

再访问转发入口：

```sh
curl --noproxy '*' http://127.0.0.1:8080/
```

应返回目标 HTTP 服务的页面。目标连接超时为 10 秒；连接失败时关闭对应访问者连接。转发过程中任意端关闭或发生错误，会清理对应另一端。

## 5. SOCKS5：按客户端请求代理 TCP

在部署目录创建 `socks5.properties`：

```properties
rahab.program=proxy-socks5
rahab.proxy-socks5.host=127.0.0.1
rahab.proxy-socks5.port=1080
rahab.proxy-socks5.auth-method=02
rahab.proxy-socks5.auth-method-02.username=example-user
rahab.proxy-socks5.auth-method-02.password=replace-this-password
```

| 配置键（前缀 `rahab.proxy-socks5.`） | 必填 | 默认值或含义 |
| --- | --- | --- |
| `host` | 否 | `0.0.0.0`，本地监听地址 |
| `port` | 是 | SOCKS5 监听端口 |
| `auth-method` | 否 | `00`，匿名；也可设为 `02`，用户名密码认证 |
| `auth-method-02.username` | 使用 `02` 时是 | 单个允许的用户名 |
| `auth-method-02.password` | 使用 `02` 时是 | 对应密码 |

用户名和密码不得为空或全为空白，编码后各不超过 255 个 UTF-8 字节；非 ASCII 配置的写法见配置通则。`02` 模式只接受用户名密码认证，不会回退到匿名。

保留上一节的本地 HTTP 服务，启动示例中的用户名密码代理：

```sh
java -jar Rahab-5.0-SNAPSHOT.jar socks5.properties
```

使用与示例一致的凭据验证：

```sh
curl --noproxy "" \
  --proxy socks5h://127.0.0.1:1080 \
  --proxy-user 'example-user:replace-this-password' \
  http://127.0.0.1:8000/
```

`--noproxy ""` 避免本机的代理绕过配置使请求直接访问目标；`socks5h` 将域名解析交给代理。部署时先更换示例凭据，再同步修改客户端设置。

若需匿名模式，可复制示例为本地 `config.properties`，将 `rahab.proxy-socks5.auth-method` 改为 `00`；原用户名密码项不再参与认证。启动后客户端命令去掉 `--proxy-user` 即可。

当前支持 IPv4、域名、IPv6 的 TCP CONNECT；BIND 和 UDP ASSOCIATE 返回“不支持”。握手总时限 15 秒，目标连接超时 10 秒。浏览器等客户端应选择 SOCKS5 代理类型，而不是 HTTP 代理类型。此模式没有内置目标地址白名单。

## 6. Periscope：从外部访问内网 TCP 服务

### 6.1 连接关系与端口

```text
访问者 ──TCP──> Mirror:8080
                   │
                   │ 已注册的多路复用隧道
                   │
                Mirror:8081 <──主动连接── Lens ──TCP──> 目标服务:8000
```

Mirror 部署在访问者和 Lens 都能访问的机器上；Lens 部署在能访问目标服务的机器上。Lens 主动向 Mirror 建立连接，Mirror 不需要主动访问 Lens。访问者使用目标服务原本的协议连接 Mirror 的访问者端口，无需理解隧道协议。

| 配置键（前缀 `rahab.periscope-mirror.`） | 必填 | 默认值或含义 |
| --- | --- | --- |
| `host` | 否 | `0.0.0.0`，同时用于以下两个监听端口 |
| `port` | 是 | 访问者入口端口 |
| `lens.port` | 是 | Lens 注册端口，必须与 `port` 不同 |
| `token` | 是 | 与 Lens 完全一致的共享 token，非空且最多 4096 个 UTF-8 字节 |

| 配置键（前缀 `rahab.periscope-lens.`） | 必填 | 含义 |
| --- | --- | --- |
| `mirror.host` | 是 | 从 Lens 所在机器访问的 Mirror 地址 |
| `mirror.port` | 是 | Mirror 的 **`lens.port`**，不是访问者入口端口 |
| `target.host` | 是 | 从 Lens 所在机器访问的目标地址 |
| `target.port` | 是 | 目标服务 TCP 端口 |
| `token` | 是 | 与 Mirror 完全一致，长度限制同上 |

每个 Mirror 当前只接受一条活动 Lens 隧道。第二个 Lens 即使 token 正确也会被拒绝，不能用多个 Lens 给同一 Mirror 做负载均衡。Mirror 的 token 只认证 Lens；访问者端口没有单独的 Rahab 认证，应由目标服务或部署网络控制访问。

### 6.2 在同一台机器上验证

继续运行 `127.0.0.1:8000` 的临时 HTTP 服务。**先停止 Wormhole**，因为本例同样使用 8080。

在部署目录创建 `periscope-mirror.properties`：

```properties
rahab.program=periscope-mirror
rahab.periscope-mirror.host=127.0.0.1
rahab.periscope-mirror.port=8080
rahab.periscope-mirror.lens.port=8081
rahab.periscope-mirror.token=replace-with-a-long-random-shared-secret
```

再创建 `periscope-lens.properties`，将 `mirror.port` 指向注册端口 8081：

```properties
rahab.program=periscope-lens
rahab.periscope-lens.mirror.host=127.0.0.1
rahab.periscope-lens.mirror.port=8081
rahab.periscope-lens.target.host=127.0.0.1
rahab.periscope-lens.target.port=8000
rahab.periscope-lens.token=replace-with-a-long-random-shared-secret
```

先在一个终端启动 Mirror：

```sh
java -jar Rahab-5.0-SNAPSHOT.jar periscope-mirror.properties
```

再在另一个终端启动 Lens，等待其打印启动成功：

```sh
java -jar Rahab-5.0-SNAPSHOT.jar periscope-lens.properties
```

通过 Mirror 访问目标：

```sh
curl --noproxy '*' http://127.0.0.1:8080/
```

应收到 Lens 目标服务的页面。没有活动 Lens 时，Mirror 会直接关闭新访问者连接，不会等待 Lens 上线。

### 6.3 分别部署到外部机器和内网机器

在可访问的 Mirror 机器上复制并编辑 Mirror 示例，将 `host` 改为所需网卡地址或 `0.0.0.0`，配置访问者端口、独立的 Lens 注册端口及新的随机 token。网络需允许访问者访问 `port`，并允许 Lens 访问 `lens.port`；两个端口共享同一个绑定地址，不能在当前配置中分别指定网卡。

在内网 Lens 机器上复制并编辑 Lens 示例，将 `mirror.host` 改为实际 Mirror 地址，`mirror.port` 填 Mirror 的 `lens.port`。将 `target.host`、`target.port` 改为 Lens 可达的服务，并设置相同 token。目标不在 Lens 本机时，不能保留示例中的 `127.0.0.1`。

两台机器分别使用自己的配置绝对路径启动同一个 v5 JAR，顺序为 Mirror、Lens、访问者。访问者连接 Mirror 的 `port`，无需直接访问内网目标或 Lens。

TCP 隧道和 SOCKS5 用户名密码认证本身均未加密；Periscope token 也不是加密机制。跨不可信网络时使用受保护的网络或额外加密传输通道。示例 token 仅用于演示，应在部署前更换。

### 6.4 断线与容量行为

- 隧道约每 5 秒发送心跳；超过 20 秒未处理到对端完整帧时，在周期检查中关闭隧道。
- 首次连接或认证失败会退出，需在 Mirror 就绪、配置正确后重新启动 Lens。
- 成功认证过的隧道断开后，Lens 等待约 1 秒发起重连；每次重连失败后再等待约 1 秒重试，并非保证每秒完成一次尝试。
- 隧道断开会关闭旧访问者和目标连接；重连成功后只能接受新连接，旧 TCP 会话不会恢复或重放。
- 每条隧道最多 4096 条连接，单帧上限 1 MiB，收发待处理队列各限制为 4 MiB。这些上限当前不可通过配置调整。
- 多条连接共享顺序处理的隧道，慢目标可能拖慢其他连接。队列超限、非法帧或心跳超时会关闭整条隧道并清理关联连接。

## 7. 日常运行与排查

程序前台运行。需要长期托管时，可由部署环境的进程管理器负责启动、标准输出/错误收集和进程重启；当前版本没有内置守护进程、配置重载、管理 API 或健康检查端点。Wormhole、SOCKS5、Mirror 不会因单条业务连接失败而整体退出；Lens 初次连接失败与运行后重连的行为见上文。

| 现象 | 检查方向 |
| --- | --- |
| 找不到 `config.properties` | 检查当前工作目录，或传入实际配置文件的绝对路径 |
| `UnsupportedClassVersionError` | 运行 JAR 的 Java 必须为 17+；确认启动时实际使用的 Java 路径 |
| `Missing rahab.…` / `Unknown rahab.program` | 核对模式名、完整键前缀和必填项；命令行不支持 `--mode` |
| `Address already in use` | 端口已占用；停止占用进程或更换配置端口。Mirror 的两个端口都需要可绑定 |
| 本机能访问，其他机器不能访问 | 示例只监听 `127.0.0.1`；检查绑定地址以及网络、防火墙和端口可达性 |
| Wormhole 已启动但访问失败 | 从 Wormhole 所在机器确认目标地址和端口可达；启动时不会预连目标 |
| SOCKS5 认证失败 | 核对 `00`/`02`、客户端提供的方法、用户名密码；留意配置中的引号和字符编码 |
| Lens 启动失败 | 确认 Mirror 先启动、连接的是 `lens.port`、两端 token 完全相同，且没有其他活动 Lens |
| Mirror 入口连接立即断开 | 确认 Lens 已认证成功；再从 Lens 所在机器确认目标可达，并检查连接数上限 |
| 网络恢复后原连接仍不可用 | 属于当前行为；隧道重连不会恢复原有 TCP 会话，客户端需重新连接 |
| 域名连接失败 | 检查对应进程的系统 DNS 或 `dns.servers`；SOCKS5 要区分本地解析和代理解析 |

## 8. 从 v4 迁移

Wormhole、SOCKS5 保留原来的 `rahab.program` 和模式配置前缀，按上文确认字段后使用新的 JAR。

Periscope 的 Mirror 和 Lens 必须一起升级：Mirror 新增独立 `lens.port`；Lens 的 `mirror.port` 指向该端口；两端新增相同 token。v4 注册标记和帧协议与 v5 不兼容，不可混用。

