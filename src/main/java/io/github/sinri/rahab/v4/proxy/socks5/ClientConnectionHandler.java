package io.github.sinri.rahab.v4.proxy.socks5;

import io.github.sinri.keel.Keel;
import io.github.sinri.keel.core.logger.KeelLogger;
import io.github.sinri.rahab.v4.proxy.socks5.auth.RahabSocks5AuthMethod;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetSocket;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

class ClientConnectionHandler implements Handler<NetSocket> {

    private final KeelLogger logger;
    private final Map<Byte, RahabSocks5AuthMethod> supportedAuthMethodMap;
    private final NetClient clientToActualServer;
    private NetSocket socketToActualServer;
    private NetSocket socketFromClient;
    private RahabSocks5AuthMethod rahabSocks5AuthMethod;
    private final AtomicReference<ProtocolStepEnum> protocolStepEnum = new AtomicReference<>(ProtocolStepEnum.STEP_1_CONFIRM_METHOD);

    public ClientConnectionHandler(Map<Byte, RahabSocks5AuthMethod> supportedAuthMethodMap, NetClient clientToActualServer) {
        this.logger = Keel.outputLogger("RahabSocks5Proxy");
        this.supportedAuthMethodMap = supportedAuthMethodMap;
        this.clientToActualServer = clientToActualServer;
    }

    protected KeelLogger getLogger() {
        return logger;
    }

    @Override
    public void handle(NetSocket socket) {
        this.socketFromClient = socket;

        // handle socket from client
        String requestID = (new Date().getTime())
                + "-" + socketFromClient.remoteAddress().hostAddress() + ":" + socketFromClient.remoteAddress().port()
                + "-" + socketFromClient.localAddress().hostAddress() + ":" + socketFromClient.localAddress().port();
        this.logger.setContentPrefix("[" + requestID + "]");
        getLogger().info("BEGIN HANDLING REQUEST");

        socketFromClient
                .handler(bufferFromClient -> {
                    // Set a data handler.
                    // As data is read, the handler will be called with the data.
                    this.logger.setContentPrefix("[" + requestID + "] <" + protocolStepEnum.get() + ">");
                    getLogger().info("data received from client " + bufferFromClient.length() + " bytes");
                    getLogger().buffer(bufferFromClient);
                    switch (protocolStepEnum.get()) {
                        case STEP_1_CONFIRM_METHOD:
                            handlerStep1(bufferFromClient);
                            break;
                        case STEP_2_AUTH_METHOD:
                            handlerStep2(bufferFromClient);
                            break;
                        case STEP_3_CONFIRM_DEST:
                            handlerStep3(bufferFromClient);
                            break;
                        case STEP_4_TRANSFER:
                            handlerStep4(bufferFromClient);
                            break;
                    }
                })
                .endHandler(v -> {
                    // Set an end handler.
                    // Once the stream has ended, and there is no more data to be read, this handler will be called.
                    // This handler might be called after the close handler
                    //  when the socket is paused and there are still buffers to deliver.
                    getLogger().notice("SOCKET END, TO CLOSE");
                    // socketFromClient.close();
                })
                .exceptionHandler(throwable -> {
                    // Set an exception handler on the read stream.
                    getLogger().exception("SOCKET EXCEPTION, TO CLOSE", throwable);
                    socketFromClient.close();
                })
                .closeHandler(v -> {
                    getLogger().notice("与客户端的通讯 关闭");
                });
    }

    private void handlerStep1(Buffer bufferFromClient) {
        // 1. from client VER*1 NumberOfMETHODS*1 METHODS*NumberOfMETHODS
        byte step_1_ver = bufferFromClient.getByte(0); // 5, ignored
        if (step_1_ver != 0x05) {
            getLogger().fatal("STEP 1: VERSION IS NOT 5");
            //throw new IllegalArgumentException("STEP 1: VERSION IS NOT 5");
            closeSocket();
            return;
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
            getLogger().warning("AUTH METHOD NOT MATCHED, RESPOND THIS TO CLIENT");
            this.writeToSocketFromClient(Buffer.buffer()
                    .appendByte((byte) 0x05)
                    .appendByte((byte) 0xFF)
            ).onComplete(voidAsyncResult -> {
                getLogger().info("TO CLOSE");
                closeSocket();
            });
            return;
        }

        Byte methodByte = methodsSupportedByBoth.get(0);

        getLogger().info("AUTH METHOD BYTE IS " + methodByte);

        this.rahabSocks5AuthMethod = this.supportedAuthMethodMap.get(methodByte)
                .setSocketWithClient(socketFromClient)
                .setLogger(getLogger());
        //atomicAuthMethod.set(rahabSocks5AuthMethod);

        if (methodByte == 0x00) {
            //NO AUTHENTICATION REQUIRED
            //atomicIdentity.set("Anonymous");
            getLogger().info("AUTH <NO AUTHENTICATION REQUIRED> PASSOVER");
            protocolStepEnum.set(ProtocolStepEnum.STEP_3_CONFIRM_DEST);
        } else {
            protocolStepEnum.set(ProtocolStepEnum.STEP_2_AUTH_METHOD);
        }
        this.writeToSocketFromClient(Buffer.buffer()
                        .appendByte((byte) 0x05)
                        .appendByte(methodByte)
                )
                .onComplete(asyncResult -> {
                    if (asyncResult.failed()) {
                        getLogger().exception("SEND FAILED, TO CLOSE", asyncResult.cause());
                        closeSocket();
                    } else {
                        getLogger().info("AUTH METHOD CONFIRMATION SENT");
                    }
                });
    }

    private void handlerStep2(Buffer bufferFromClient) {
        //atomicAuthMethod.get()
        this.rahabSocks5AuthMethod.verifyIdentity(bufferFromClient)
                .onComplete(stringAsyncResult -> {
                    if (stringAsyncResult.failed()) {
                        getLogger().exception("AUTH FAILED, TO CLOSE", stringAsyncResult.cause());
                        closeSocket();
                    } else {
                        getLogger().info("AUTH DONE");
                        protocolStepEnum.set(ProtocolStepEnum.STEP_3_CONFIRM_DEST);
                    }
                });
    }

    private void handlerStep3(Buffer bufferFromClient) {
        int ptr = 0;
        bufferFromClient.getByte(ptr);// version 5 ignored
        ptr += 1;

        // CMD: 0x01表示CONNECT请求 0x02表示BIND请求 0x03表示UDP转发
        byte cmd = bufferFromClient.getByte(ptr);
        ptr += 1;
        bufferFromClient.getByte(ptr);// rsv 0x00
        ptr += 1;
        getLogger().debug("CMD BYTE IS " + cmd);

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
            getLogger().warning("addressType unknown " + addressType);
            rawDestinationAddress = null;
            destinationAddress = null;
        }

        short rawDestinationPort = bufferFromClient.getShort(ptr);
        ptr += 2;

        getLogger().debug("TARGET " + destinationAddress + ":" + rawDestinationPort);

        if (destinationAddress == null) {
            // send 0x08
            this.respondInStep3((byte) 0x08, addressType, new byte[]{0}, (short) 0);
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
                getLogger().info("CONNECT 准备连接目标服务");
                this.clientToActualServer
                        .connect(rawDestinationPort, destinationAddress)
                        .onComplete(netSocketAsyncResult -> {
                            if (netSocketAsyncResult.failed()) {
                                // cannot !
                                getLogger().exception("CONNECT TO TARGET FAILED, TO RESPOND AND CLOSE", netSocketAsyncResult.cause());
                                // send 0x01 general SOCKS server failure
                                this.respondInStep3((byte) 0x01, addressType, new byte[]{0}, (short) 0);
                            } else {
                                handleSocketToActualServer(netSocketAsyncResult.result());
                            }
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

                getLogger().info("BIND 准备反向连接客户端");
                getLogger().warning("目前不支持这玩意");
                this.respondInStep3((byte) 0x07, addressType, new byte[]{0}, (short) 0);
            } else if (cmd == 0x03) {
                // UDP ASSOCIATE
                getLogger().info("UDP ASSOCIATE 准备组建UDP连接");
                getLogger().warning("目前不支持这玩意");
                this.respondInStep3((byte) 0x07, addressType, new byte[]{0}, (short) 0);
            }
        }
    }

    private void respondInStep3(Byte repByte, Byte addressType, byte[] address, short port) {
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
        this.writeToSocketFromClient(bufferToRespond)
                .onComplete(asyncResult -> {
                    if (asyncResult.failed()) {
                        getLogger().exception("RESPOND <" + desc + "> FAILED", asyncResult.cause());
                    } else {
                        getLogger().info("RESPOND <" + desc + "> DONE");
                    }
                    if (repByte != 0x00) {
                        getLogger().error("TO CLOSE");
                        closeSocket();
                    }
                });
    }

    private void handleSocketToActualServer(NetSocket socket) {
        socketToActualServer = socket;
        socketToActualServer
                .handler(bufferFromActualServer -> {
                    getLogger().info("DATA FROM TARGET TO CLIENT, " + bufferFromActualServer.length() + " bytes");

                    this.writeToSocketFromClient(bufferFromActualServer)
                            .onComplete(voidAsyncResult -> {
                                if (voidAsyncResult.failed()) {
                                    getLogger().exception("FAILED TO TRANSFER DATA FROM TARGET TO CLIENT, TO CLOSE", voidAsyncResult.cause());
                                    socketToActualServer.close();
                                } else {
                                    getLogger().info("TRANSFERRED DATA FROM TARGET TO CLIENT");
                                }
                            });
                })
                .endHandler(v -> {
                    getLogger().notice("END READ TARGET");
                })
                .exceptionHandler(throwable -> {
                    getLogger().exception("EXCEPTION WITH TARGET, TO CLOSE", throwable);
                    socketToActualServer.close();
                })
                .closeHandler(v -> {
                    getLogger().notice("CLOSE WITH TARGET, TO CLOSE WITH CLIENT");
                    closeSocket();
                });

        //atomicSocketToActualServer.set(socketToActualServer);
        getLogger().info("CREATED SOCKET TO TARGET " + socketToActualServer.remoteAddress().toString());
        // send 0x00 succeeded
        protocolStepEnum.set(ProtocolStepEnum.STEP_4_TRANSFER);
        //todo debug
        byte x0 = 0x01;
        byte[] x1 = new byte[]{0, 0, 0, 0};
        short x2 = 0;

        // addressType rawDestinationAddress rawDestinationPort
        this.respondInStep3((byte) 0x00, x0, x1, x2);
    }

    private void handlerStep4(Buffer bufferFromClient) {
        socketToActualServer.write(bufferFromClient)
                .onComplete(voidAsyncResult -> {
                    if (voidAsyncResult.failed()) {
                        getLogger().exception("FAILED TO TRANSFER DATA FROM CLIENT TO TARGET", voidAsyncResult.cause());
                        socketToActualServer.close();
                    } else {
                        getLogger().info("TRANSFERRED DATA FROM CLIENT TO TARGET");
                    }
                });
    }

    private Future<Void> writeToSocketFromClient(Buffer buffer) {
        return this.socketFromClient.write(buffer)
                .compose(written -> {
                    getLogger().debug("write done to client " + buffer.length() + " bytes");
                    getLogger().buffer(buffer);
                    return Future.succeededFuture();
                }, throwable -> {
                    getLogger().exception("write failed to client " + buffer.length() + " bytes", throwable);
                    return Future.failedFuture(throwable);
                });
    }

    private void closeSocket() {
        this.socketFromClient.close(asyncResult -> {
            if (asyncResult.failed()) {
                getLogger().exception("close socket failed", asyncResult.cause());
            } else {
                getLogger().info("close socket done");
            }
        });
    }
}
