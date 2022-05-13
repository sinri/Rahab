package io.github.sinri.Rahab.v2.proxy.socks5;

import io.github.sinri.Rahab.v2.proxy.socks5.auth.RahabSocks5AuthMethod;
import io.github.sinri.keel.Keel;
import io.github.sinri.keel.core.KeelHelper;
import io.github.sinri.keel.core.logger.KeelLogLevel;
import io.github.sinri.keel.core.logger.KeelLogger;
import io.github.sinri.keel.verticles.KeelVerticle;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetSocket;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @since 3.0.0
 */
class RahabSocks5ProxyWorkerVerticle extends KeelVerticle {
    private final NetSocket socketFromClient;
    private final String requestID;

    /**
     * 连接目标服务器的客户端
     */
    protected NetClient clientToActualServer;
    /**
     * 本代理支持的鉴权方法
     */
    protected Map<Byte, RahabSocks5AuthMethod> supportedAuthMethodMap;

    public RahabSocks5ProxyWorkerVerticle(NetSocket socketFromClient, String requestID, Map<Byte, RahabSocks5AuthMethod> supportedAuthMethodMap) {
        this.socketFromClient = socketFromClient;
        this.requestID = requestID;

        this.clientToActualServer = Keel.getVertx().createNetClient();
        this.supportedAuthMethodMap = supportedAuthMethodMap;
    }

    @Override
    public void start() throws Exception {
        KeelLogger socketLogger = Keel.standaloneLogger("Socks5ServerSocket").setCategoryPrefix(requestID);
        setLogger(socketLogger);

        socketLogger.notice("新通讯 已被建立 自 客户端 " + socketFromClient.remoteAddress().toString());

        AtomicReference<RahabSocks5ProxyWorkerVerticle.ProtocolStepEnum> protocolStepEnum = new AtomicReference<>(RahabSocks5ProxyWorkerVerticle.ProtocolStepEnum.STEP_1_CONFIRM_METHOD);
        AtomicReference<RahabSocks5AuthMethod> atomicAuthMethod = new AtomicReference<>();
        AtomicReference<String> atomicIdentity = new AtomicReference<>();
        AtomicReference<NetSocket> atomicSocketToActualServer = new AtomicReference<>();

        socketFromClient
                .handler(bufferFromClient -> {
                    socketLogger.info("阶段【" + protocolStepEnum + "】 自 客户端 发来的数据 共" + bufferFromClient.length() + " 字节");
                    switch (protocolStepEnum.get()) {
                        case STEP_1_CONFIRM_METHOD:
                            socketLogger.print(KeelLogLevel.DEBUG, KeelHelper.bufferToHexMatrix(bufferFromClient, 20));

                            // 1. from client VER*1 NumberOfMETHODS*1 METHODS*NumberOfMETHODS
                            byte step_1_ver = bufferFromClient.getByte(0); // 5, ignored
                            if (step_1_ver != 0x05) {
                                throw new IllegalArgumentException("STEP 1: VERSION IS NOT 5");
                            }
                            byte numberOfMethods = bufferFromClient.getByte(1);
                            List<Byte> methodsSupportedByBoth = new ArrayList<>();
                            for (var i = 0; i < numberOfMethods; i++) {
                                Byte methodByte = bufferFromClient.getByte(i + 2);
                                if (this.supportedAuthMethodMap.get(methodByte) == null) {
                                    continue;
                                }
                                methodsSupportedByBoth.add(methodByte);
                            }
                            if (methodsSupportedByBoth.size() == 0) {
                                socketFromClient.write(Buffer.buffer()
                                        .appendByte((byte) 0x05)
                                        .appendByte((byte) 0xFF)
                                ).onComplete(voidAsyncResult -> {
                                    socketLogger.notice("服务器和客户端在认证方法上没有交集，准备关闭与客户端之间的通讯");
                                    socketFromClient.close();
                                });
                                return;
                            }

                            Byte methodByte = methodsSupportedByBoth.get(0);

                            socketLogger.info("使用的 认证方法 字节 为" + methodByte);

                            RahabSocks5AuthMethod rahabSocks5AuthMethod = this.supportedAuthMethodMap.get(methodByte)
                                    .setSocketWithClient(socketFromClient)
                                    .setLogger(socketLogger);
                            atomicAuthMethod.set(rahabSocks5AuthMethod);

                            if (methodByte == 0x00) {
                                //NO AUTHENTICATION REQUIRED
                                atomicIdentity.set("Anonymous");
                                socketLogger.info("使用的 认证方法 为 NO AUTHENTICATION REQUIRED，认证对方为 " + atomicIdentity.get() + " 跳过认证");
                                protocolStepEnum.set(RahabSocks5ProxyWorkerVerticle.ProtocolStepEnum.STEP_3_CONFIRM_DEST);
                            } else {
                                protocolStepEnum.set(RahabSocks5ProxyWorkerVerticle.ProtocolStepEnum.STEP_2_AUTH_METHOD);
                            }
                            socketFromClient.write(Buffer.buffer()
                                            .appendByte((byte) 0x05)
                                            .appendByte(methodByte)
                                    )
                                    .onFailure(throwable -> {
                                        socketLogger.exception("发送认证方式确认数据包失败", throwable);
                                        socketLogger.error("即将关闭与客户端的通讯");
                                        socketFromClient.close();
                                    })
                                    .onSuccess(v -> {
                                        socketLogger.info("发送认证方式确认数据包成功");
                                    });
                            break;
                        case STEP_2_AUTH_METHOD:
                            socketLogger.print(KeelLogLevel.DEBUG, KeelHelper.bufferToHexMatrix(bufferFromClient, 20));

                            atomicAuthMethod.get()
                                    .verifyIdentity(bufferFromClient)
                                    .onFailure(throwable -> {
                                        socketLogger.exception("认证失败", throwable);
                                        socketLogger.error("即将关闭与客户端的通讯");
                                        socketFromClient.close();
                                    })
                                    .compose(identity -> {
                                        atomicIdentity.set(identity);
                                        socketLogger.info("认证对方为 " + atomicIdentity.get());
                                        protocolStepEnum.set(RahabSocks5ProxyWorkerVerticle.ProtocolStepEnum.STEP_3_CONFIRM_DEST);
                                        return Future.succeededFuture();
                                    });
                            break;
                        case STEP_3_CONFIRM_DEST:
                            socketLogger.print(KeelLogLevel.DEBUG, KeelHelper.bufferToHexMatrix(bufferFromClient, 20));

                            int ptr = 0;
                            bufferFromClient.getByte(ptr);// version 5 ignored
                            ptr += 1;

                            // CMD: 0x01表示CONNECT请求 0x02表示BIND请求 0x03表示UDP转发
                            byte cmd = bufferFromClient.getByte(ptr);
                            ptr += 1;
                            bufferFromClient.getByte(ptr);// rsv 0x00
                            ptr += 1;
                            socketLogger.debug("CMD 字节为 " + cmd);

                            // ATYP 目标地址类型，DST.ADDR的数据对应这个字段的类型。
                            // 0x01表示IPv4地址，DST.ADDR为4个字节
                            // 0x03表示域名，DST.ADDR是一个可变长度的域名
                            // 0x04表示IPv6地址，DST.ADDR为16个字节长度
                            byte addressType = bufferFromClient.getByte(ptr);
                            ptr += 1;

                            byte[] rawDestinationAddress;
                            String destinationAddress;
                            if (addressType == 0x01) {
                                rawDestinationAddress = bufferFromClient.getBytes(ptr, ptr + 4);
                                ptr += 4;

                                try {
                                    destinationAddress = Inet4Address.getByAddress(rawDestinationAddress).getHostAddress();
                                } catch (UnknownHostException e) {
                                    destinationAddress = null;
                                }
                            } else if (addressType == 0x03) {
                                // the address field contains a fully-qualified domain name.
                                // The first octet of the address field contains the number of octets of name that follow,
                                //  there is no terminating NUL octet.
                                byte domainLength = bufferFromClient.getByte(ptr);
                                ptr += 1;
                                rawDestinationAddress = bufferFromClient.getBytes(ptr, ptr + domainLength);
                                ptr += domainLength;
                                destinationAddress = new String(rawDestinationAddress);
                            } else if (addressType == 0x04) {
                                rawDestinationAddress = bufferFromClient.getBytes(ptr, ptr + 16);
                                ptr += 16;

                                try {
                                    destinationAddress = Inet6Address.getByAddress(rawDestinationAddress).getHostAddress();
                                } catch (UnknownHostException e) {
                                    destinationAddress = null;
                                }
                            } else {
                                socketLogger.warning("addressType unknown " + addressType);
                                rawDestinationAddress = null;
                                destinationAddress = null;
                            }

                            short rawDestinationPort = bufferFromClient.getShort(ptr);
                            ptr += 2;

                            socketLogger.debug("连接目标为 " + destinationAddress + " 端口 " + rawDestinationPort);

                            if (destinationAddress == null) {
                                // send 0x08
                                this.respondInStep3((byte) 0x08, addressType, new byte[]{0}, (short) 0, socketFromClient, socketLogger);
                            } else {
                                // generate connection by client
                                if (cmd == 0x01) {
                                    // CONNECT
                                    // In the reply to a CONNECT, BND.PORT contains the port number
                                    //  that the server assigned to connect to the target host,
                                    //  while `BND.ADDR` contains the associated IP address.
                                    // The supplied `BND.ADDR` is often different from the IP address
                                    //  that the client uses to reach the SOCKS server,
                                    //  since such servers are often multi-homed.
                                    // It is expected that the SOCKS server will use `DST.ADDR` and DST.PORT,
                                    //   and the client-side source address and port in evaluating the CONNECT request.
                                    socketLogger.info("CONNECT 准备连接目标服务");
                                    this.clientToActualServer
                                            .connect(rawDestinationPort, destinationAddress)
                                            .onFailure(throwable -> {
                                                socketLogger.exception("建立与目标服务的通讯失败", throwable);
                                                // send 0x01 general SOCKS server failure
                                                this.respondInStep3((byte) 0x01, addressType, new byte[]{0}, (short) 0, socketFromClient, socketLogger);
                                            })
                                            .compose(socketToActualServer -> {
                                                socketToActualServer
                                                        .handler(bufferFromActualServer -> {
                                                            socketLogger.info("自 目标服务 发给 代理客户端 的数据包 共 " + bufferFromActualServer.length() + " 字节");
                                                            socketLogger.print(KeelLogLevel.DEBUG, KeelHelper.bufferToHexMatrix(bufferFromActualServer, 20));
                                                            socketLogger.print(KeelLogLevel.DEBUG, bufferFromActualServer.toString());

                                                            socketFromClient.write(bufferFromActualServer)
                                                                    .onComplete(voidAsyncResult -> {
                                                                        if (voidAsyncResult.failed()) {
                                                                            socketLogger.exception("转发 来自 目标服务 的数据包给 客户端 失败", voidAsyncResult.cause());
                                                                            socketLogger.error("即将关闭 与 目标服务 的通讯");
                                                                            socketToActualServer.close();
                                                                        } else {
                                                                            socketLogger.info("转发 来自 目标服务 的数据包给 客户端 成功");
                                                                        }
                                                                    });
                                                        })
                                                        .endHandler(v -> {
                                                            socketLogger.notice("与 目标服务 的通讯 结束");
                                                        })
                                                        .exceptionHandler(throwable -> {
                                                            socketLogger.exception("目标服务 与 代理客户端 的通讯出错", throwable);
                                                            socketLogger.error("即将关闭 与 目标服务 的通讯");
                                                            socketToActualServer.close();
                                                        })
                                                        .closeHandler(v -> {
                                                            socketLogger.notice("与 目标服务 的通讯关闭。即将关闭与 客户端 的通讯。");
                                                            socketFromClient.close();
                                                        });

                                                atomicSocketToActualServer.set(socketToActualServer);
                                                socketLogger.info("已建立 连接到 目标服务 的通讯， 地址 " + socketToActualServer.remoteAddress().toString());
                                                // send 0x00 succeeded
                                                protocolStepEnum.set(RahabSocks5ProxyWorkerVerticle.ProtocolStepEnum.STEP_4_TRANSFER);
                                                //todo debug
                                                byte x0 = 0x01;
                                                byte[] x1 = new byte[]{0, 0, 0, 0};
                                                short x2 = 0;

                                                // addressType rawDestinationAddress rawDestinationPort
                                                this.respondInStep3((byte) 0x00, x0, x1, x2, socketFromClient, socketLogger);
                                                return Future.succeededFuture();
                                            });
                                } else if (cmd == 0x02) {
                                    // BIND
                                    // IT SHOULD FROM ACTUAL SERVER
                                    //
                                    // The BIND request is used in protocols which require the client to
                                    //   accept connections from the server.  FTP is a well-known example,
                                    //   which uses the primary client-to-server connection for commands and
                                    //   status reports, but may use a server-to-client connection for
                                    //   transferring data on demand (e.g. LS, GET, PUT).
                                    //
                                    //   It is expected that the client side of an application protocol will
                                    //   use the BIND request only to establish secondary connections after a
                                    //   primary connection is established using CONNECT.
                                    //   In is expected that a SOCKS server will use DST.ADDR and DST.PORT
                                    //   in evaluating the BIND request.
                                    //
                                    //   Two replies are sent from the SOCKS server to the client during a BIND operation.
                                    //   The first is sent after the server creates and binds a new socket.
                                    //   The `BND.PORT` field contains the port number
                                    //      that the SOCKS server assigned to listen for an incoming connection.
                                    //   The `BND.ADDR` field contains the associated IP address.
                                    //   The client will typically use these pieces of information to notify
                                    //      (via the primary or control connection) the application server
                                    //      of the rendezvous address.
                                    //   The second reply occurs only after the anticipated incoming connection succeeds or fails.
                                    //
                                    //   In the second reply, the BND.PORT and BND.ADDR fields contain the
                                    //   address and port number of the connecting host.

                                    socketLogger.info("BIND 准备反向连接客户端");
                                    socketLogger.warning("目前不支持这玩意");
                                    this.respondInStep3((byte) 0x07, addressType, new byte[]{0}, (short) 0, socketFromClient, socketLogger);

//                                                this.clientToActualServer
//                                                        .connect(rawDestinationPort, destinationAddress)
//                                                        .onFailure(throwable -> {
//                                                            socketLogger.exception("建立反向连接客户端的通讯失败", throwable);
//                                                            this.respondInStep3((byte) 0x01,addressType,new byte[]{0}, (short) 0,socketFromClient,socketLogger);
//                                                        })
//                                                        .compose(socketBoundBackToClient->{
//                                                            //
//                                                        });
                                } else if (cmd == 0x03) {
                                    // UDP ASSOCIATE
                                    socketLogger.info("UDP ASSOCIATE 准备组建UDP连接");
                                    socketLogger.warning("目前不支持这玩意");
                                    this.respondInStep3((byte) 0x07, addressType, new byte[]{0}, (short) 0, socketFromClient, socketLogger);
                                }
                            }
                            break;
                        case STEP_4_TRANSFER:
                            NetSocket socketToActualServer = atomicSocketToActualServer.get();

                            socketLogger.print(KeelLogLevel.DEBUG, KeelHelper.bufferToHexMatrix(bufferFromClient, 20));
                            socketLogger.print(KeelLogLevel.DEBUG, bufferFromClient.toString());

                            socketToActualServer.write(bufferFromClient)
                                    .onComplete(voidAsyncResult -> {
                                        if (voidAsyncResult.failed()) {
                                            socketLogger.exception("转发 来自 客户端 的数据包 到 目标服务 失败", voidAsyncResult.cause());
                                            socketLogger.error("即将关闭 与 目标服务 的通讯");
                                            socketToActualServer.close();
                                        } else {
                                            socketLogger.info("转发 来自 客户端 的数据包 到 目标服务 成功");
                                        }
                                    });
                            break;
                    }
                })
                .endHandler(v -> {
                    socketLogger.notice("与客户端的通讯 结束，即将关闭");
                    socketFromClient.close();
                })
                .exceptionHandler(throwable -> {
                    socketLogger.exception("与客户端的通讯 出错，即将关闭", throwable);
                    socketFromClient.close();
                })
                .closeHandler(v -> {
                    socketLogger.notice("与客户端的通讯 关闭");

                    undeployMe();
                });
    }

    private void respondInStep3(Byte repByte, Byte addressType, byte[] address, short port, NetSocket socketFromClient, KeelLogger socketLogger) {
        // REP    Reply field:
        //             o  X'00' succeeded
        //             o  X'01' general SOCKS server failure
        //             o  X'02' connection not allowed by ruleset
        //             o  X'03' Network unreachable
        //             o  X'04' Host unreachable
        //             o  X'05' Connection refused
        //             o  X'06' TTL expired
        //             o  X'07' Command not supported
        //             o  X'08' Address type not supported
        //             o  X'09' to X'FF' unassigned

        final String desc;
        if (repByte == 0x08) {
            desc = "Address type not supported";
        } else if (repByte == 0x01) {
            desc = "general SOCKS server failure";
        } else if (repByte == 0x00) {
            desc = "succeeded";
        } else if (repByte == 0x07) {
            desc = "Command not supported";
        } else {
            desc = "UNKNOWN";
        }

        // send repByte
        Buffer bufferToRespond = Buffer.buffer()
                .appendByte((byte) 0x05)
                .appendByte(repByte)
                .appendByte((byte) 0x00)
                .appendByte(addressType)
                .appendBytes(address)
                .appendShort(port);
        socketFromClient.write(bufferToRespond)
                .onFailure(throwable -> {
                    socketLogger.exception("发送 " + desc + " 给 客户端 失败", throwable);
                    socketLogger.print(KeelLogLevel.DEBUG, KeelHelper.bufferToHexMatrix(bufferToRespond, 20));
                })
                .onSuccess(v -> {
                    socketLogger.info("发送 " + desc + " 给 客户端 成功");
                    socketLogger.print(KeelLogLevel.DEBUG, KeelHelper.bufferToHexMatrix(bufferToRespond, 20));
                })
                .eventually(v -> {
                    if (repByte != 0x00) {
                        socketLogger.error("即将关闭 与 客户端 的 通讯");
                        socketFromClient.close();
                    }
                    return Future.succeededFuture();
                });
    }

    public enum ProtocolStepEnum {
        STEP_1_CONFIRM_METHOD,// client -[VER | NM | Ms]-> server -[VER | M]-> client
        STEP_2_AUTH_METHOD,
        STEP_3_CONFIRM_DEST,// client -[VER | CMD |  RSV  | ATYP | DST.ADDR | DST.PORT ]-> server -[VER | REP |  RSV  | ATYP | BND.ADDR | BND.PORT]-> client
        STEP_4_TRANSFER // transfer data
    }
}
