package io.github.sinri.rahab.v4.proxy.socks5;

import io.github.sinri.keel.Keel;
import io.github.sinri.keel.core.controlflow.FutureUntil;
import io.github.sinri.keel.core.logger.KeelLogLevel;
import io.github.sinri.keel.web.tcp.KeelAbstractSocketWrapper;
import io.github.sinri.rahab.v4.proxy.socks5.auth.RahabSocks5AuthMethod;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.datagram.DatagramSocket;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.SocketAddress;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

class TerminalSocketWrapper extends KeelAbstractSocketWrapper {
    private final Map<Byte, RahabSocks5AuthMethod> supportedAuthMethodMap;
    private final NetClient clientToActualServer;
    private NetSocket socketToActualServer;
    private RahabSocks5AuthMethod rahabSocks5AuthMethod;
    private final AtomicReference<ProtocolStepEnum> protocolStepEnum = new AtomicReference<>(ProtocolStepEnum.STEP_1_CONFIRM_METHOD);

    private final byte[] localAddressByteArray;
    private final short localPortShort;
    private DatagramSocket relatedRelayUDP;

    private TerminalSocketWrapper(NetSocket socket, Map<Byte, RahabSocks5AuthMethod> supportedAuthMethodMap, NetClient clientToActualServer) {
        super(socket);
        setLogger(Keel.standaloneLogger("RahabSocks5Proxy"));
        this.supportedAuthMethodMap = supportedAuthMethodMap;
        this.clientToActualServer = clientToActualServer;

        this.localAddressByteArray = Keel.netHelper().convertIPv4ToAddressBytes(this.getLocalAddress().hostAddress());
        this.localPortShort = (short) this.getLocalAddress().port();
    }

    public static TerminalSocketWrapper handle(NetSocket socket, Map<Byte, RahabSocks5AuthMethod> supportedAuthMethodMap, NetClient clientToActualServer) {
        return new TerminalSocketWrapper(socket, supportedAuthMethodMap, clientToActualServer);
    }

    @Override
    protected void whenClose() {
        super.whenClose();
        if (this.relatedRelayUDP != null) {
            this.relatedRelayUDP.close();
        }
    }

    @Override
    protected Future<Void> whenBufferComes(Buffer buffer) {
        // Set a data handler.
        // As data is read, the handler will be called with the data.
        getLogger().setContentPrefix("[" + getSocketID() + "] <" + protocolStepEnum.get() + ">");
        getLogger().info("data received from client " + buffer.length() + " bytes");
        getLogger().buffer(buffer);
        switch (protocolStepEnum.get()) {
            case STEP_1_CONFIRM_METHOD:
                return handlerStep1(buffer);
            case STEP_2_AUTH_METHOD:
                return handlerStep2(buffer);
            case STEP_3_CONFIRM_DEST:
                return handlerStep3(buffer);
            case STEP_4_TRANSFER:
                return handlerStep4(buffer);
            default:
                return Future.failedFuture("protocolStepEnum ???");
        }
    }

    private Future<Void> handlerStep1(Buffer bufferFromClient) {
        // 1. from client VER*1 NumberOfMETHODS*1 METHODS*NumberOfMETHODS
        byte step_1_ver = bufferFromClient.getByte(0); // 5, ignored
        if (step_1_ver != 0x05) {
            getLogger().fatal("STEP 1: VERSION IS NOT 5");
            getLogger().buffer(KeelLogLevel.WARNING, false, bufferFromClient);
            //throw new IllegalArgumentException("STEP 1: VERSION IS NOT 5");
            return close();
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
            return this.write(Buffer.buffer()
                            .appendByte((byte) 0x05)
                            .appendByte((byte) 0xFF)
                    )
                    .eventually(v -> {
                        getLogger().info("TO CLOSE");
                        return close();
                    });
        }

        Byte methodByte = methodsSupportedByBoth.get(0);

        getLogger().info("AUTH METHOD BYTE IS " + methodByte);

        this.rahabSocks5AuthMethod = this.supportedAuthMethodMap.get(methodByte)
                .setSocketWithClient(this)
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
        return this.write(Buffer.buffer()
                        .appendByte((byte) 0x05)
                        .appendByte(methodByte)
                )
                .compose(done -> {
                    getLogger().info("AUTH METHOD CONFIRMATION SENT");
                    return Future.succeededFuture();
                }, throwable -> {
                    getLogger().exception("SEND FAILED, TO CLOSE", throwable);
                    return close();
                });
    }

    private Future<Void> handlerStep2(Buffer bufferFromClient) {
        //atomicAuthMethod.get()
        return this.rahabSocks5AuthMethod.verifyIdentity(bufferFromClient)
                .compose(done -> {
                    getLogger().info("AUTH DONE");
                    protocolStepEnum.set(ProtocolStepEnum.STEP_3_CONFIRM_DEST);
                    return Future.succeededFuture();
                }, throwable -> {
                    getLogger().exception("AUTH FAILED, TO CLOSE", throwable);
                    return close();
                });
    }

    private Future<Void> handlerStep3(Buffer bufferFromClient) {
        int ptr = 0;
        bufferFromClient.getByte(ptr);// version 5 ignored
        ptr += 1;

        // CMD: 0x01表示CONNECT请求 0x02表示BIND请求 0x03表示UDP转发
        byte cmd = bufferFromClient.getByte(ptr);
        ptr += 1;
        bufferFromClient.getByte(ptr);// rsv 0x00
        ptr += 1;
        getLogger().debug("CMD BYTE IS " + cmd);

        RFC1928AddressBytesParser rfc1928AddressBytesParser = new RFC1928AddressBytesParser(bufferFromClient.getBuffer(ptr, bufferFromClient.length()));
        byte addressType = rfc1928AddressBytesParser.getAddressType();
        String destinationAddress = rfc1928AddressBytesParser.getDestinationAddress();
        byte[] rawDestinationAddress = rfc1928AddressBytesParser.getRawDestinationAddress();
        short rawDestinationPort = rfc1928AddressBytesParser.getRawDestinationPort();

//        // ATYP 目标地址类型，DST.ADDR的数据对应这个字段的类型。
//        // 0x01表示IPv4地址，DST.ADDR为4个字节
//        // 0x03表示域名，DST.ADDR是一个可变长度的域名
//        // 0x04表示IPv6地址，DST.ADDR为16个字节长度
//        byte addressType = bufferFromClient.getByte(ptr);
//        ptr += 1;
//
//        byte[] rawDestinationAddress;
//        String destinationAddress;
//        if (addressType == 0x01) {
//            rawDestinationAddress = bufferFromClient.getBytes(ptr, ptr + 4);
//            ptr += 4;
//
//            try {
//                destinationAddress = Inet4Address.getByAddress(rawDestinationAddress).getHostAddress();
//            } catch (UnknownHostException e) {
//                destinationAddress = null;
//            }
//        } else if (addressType == 0x03) {
//            // the address field contains a fully-qualified domain name.
//            // The first octet of the address field contains the number of octets of name that follow,
//            //  there is no terminating NUL octet.
//            byte domainLength = bufferFromClient.getByte(ptr);
//            ptr += 1;
//            rawDestinationAddress = bufferFromClient.getBytes(ptr, ptr + domainLength);
//            ptr += domainLength;
//            destinationAddress = new String(rawDestinationAddress);
//        } else if (addressType == 0x04) {
//            rawDestinationAddress = bufferFromClient.getBytes(ptr, ptr + 16);
//            ptr += 16;
//
//            try {
//                destinationAddress = Inet6Address.getByAddress(rawDestinationAddress).getHostAddress();
//            } catch (UnknownHostException e) {
//                destinationAddress = null;
//            }
//        } else {
//            getLogger().warning("addressType unknown " + addressType);
//            rawDestinationAddress = null;
//            destinationAddress = null;
//        }
//
//        short rawDestinationPort = bufferFromClient.getShort(ptr);
//        ptr += 2;

        getLogger().notice("TARGET " + destinationAddress + ":" + rawDestinationPort);

        if (destinationAddress == null) {
            // send 0x08
            return this.respondInStep3((byte) 0x08, addressType, new byte[]{0}, (short) 0);
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
                getLogger().info("CONNECT", new JsonObject()
                        .put("address", destinationAddress)
                        .put("port", rawDestinationPort)
                );
                return this.clientToActualServer
                        .connect(rawDestinationPort, destinationAddress)
                        .compose(this::handleSocketToActualServer, throwable -> {
                            // cannot !
                            getLogger().exception("CONNECT TO TARGET FAILED, TO RESPOND AND CLOSE", throwable);
                            // send 0x01 general SOCKS server failure
                            return this.respondInStep3((byte) 0x01, addressType, new byte[]{0}, (short) 0);
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
                return this.respondInStep3((byte) 0x07, addressType, new byte[]{0}, (short) 0);
            } else if (cmd == 0x03) {
                // UDP ASSOCIATE
                getLogger().info("UDP ASSOCIATE 准备组建UDP连接");
//                getLogger().warning("目前不支持这玩意");
//                return this.respondInStep3((byte) 0x07, addressType, new byte[]{0}, (short) 0);
                // TODO challenge!
                //
                // 此时DST.ADDR[destinationAddress]和DST.PORT[rawDestinationPort]代表客户端UDP准备发送的地址和端口
                // 用于服务器权限控制（只给DST.ADDR:DST.PORT发出来的udp包代理），当然可以为空即全是0
                this.relatedRelayUDP = Keel.getVertx().createDatagramSocket();
                AtomicInteger listenRetry = new AtomicInteger(0);
                AtomicInteger portRef = new AtomicInteger();
                return FutureUntil.call(new Supplier<Future<Boolean>>() {
                            @Override
                            public Future<Boolean> get() {
                                if (listenRetry.get() > 3) {
                                    return Future.failedFuture("LISTEN RETRY OVER 3 TIMES");
                                }
                                int port = (int) (21000 + Math.random() * 500 * 2);
                                return relatedRelayUDP.listen(port, "0.0.0.0")
                                        .compose(done -> {
                                            portRef.set(port);

                                            relatedRelayUDP.handler(datagramPacket -> {
                                                        SocketAddress sender = datagramPacket.sender();
                                                        Buffer udpDataBuffer = datagramPacket.data();
                                                        getLogger().info("[UDP:" + port + "] RECEIVED " + udpDataBuffer.length() + " bytes FROM " + sender.hostAddress() + ":" + sender.port());
                                                        getLogger().buffer(udpDataBuffer);

                                                        Buffer rsv = udpDataBuffer.getBuffer(0, 2);// Reserved X'0000'
                                                        var frag = udpDataBuffer.getByte(2);//Current fragment number
                                                        RFC1928AddressBytesParser rfc1928AddressBytesParserUDP = new RFC1928AddressBytesParser(udpDataBuffer.getBuffer(3, udpDataBuffer.length()));
                                                        byte addressTypeUDP = rfc1928AddressBytesParserUDP.getAddressType();
                                                        String destinationAddressUDP = rfc1928AddressBytesParserUDP.getDestinationAddress();
                                                        short rawDestinationPortUDP = rfc1928AddressBytesParserUDP.getRawDestinationPort();
                                                        Buffer data = rfc1928AddressBytesParserUDP.getRestBuffer();

                                                        Keel.getVertx().createDatagramSocket()
                                                                .listen(port + 1, "0.0.0.0")
                                                                .compose(datagramSocketToTarget -> {
                                                                    datagramSocketToTarget.handler(datagramPacketFromTarget -> {
                                                                        Buffer bufferToRespond = Buffer.buffer();
                                                                        bufferToRespond.appendByte((byte) 0).appendByte((byte) 0);//rsv
                                                                        bufferToRespond.appendByte((byte) 0);//frag
                                                                        bufferToRespond.appendByte(addressType);
                                                                        bufferToRespond.appendBytes(
                                                                                Keel.netHelper().convertIPv4ToAddressBytes(sender.hostAddress())
                                                                        );
                                                                        bufferToRespond.appendShort((short) sender.port());
                                                                        bufferToRespond.appendBuffer(data);

                                                                        // todo send
                                                                        throw new RuntimeException("TODO");
                                                                    });
                                                                    return Future.succeededFuture();
                                                                });


                                                    })
                                                    .endHandler(end -> {
                                                        getLogger().info("[UDP:" + port + "] END");
                                                    })
                                                    .exceptionHandler(throwable -> {
                                                        getLogger().exception("[UDP:" + port + "] ERROR", throwable);
                                                    });

                                            return Future.succeededFuture(true);
                                        }, throwable -> {
                                            listenRetry.incrementAndGet();
                                            return Future.succeededFuture(false);
                                        });
                            }
                        })
                        .compose(done -> {
                            getLogger().info("prepared UDP server listen on " + (short) portRef.get());
                            this.protocolStepEnum.set(ProtocolStepEnum.STEP_5_UDP_PROXY);
                            return this.respondInStep3((byte) 0x00, addressType, this.localAddressByteArray, (short) portRef.get());
                        }, throwable -> {
                            getLogger().exception("failed to prepared UDP server", throwable);
                            return this.respondInStep3((byte) 0x07, addressType, new byte[]{0}, (short) 0)
                                    .compose(v -> {
                                        return this.close();
                                    });
                        });
            } else {
                return Future.failedFuture("UNSUPPORTED CMD");
            }
        }
    }

    private Future<Void> respondInStep3(Byte repByte, Byte addressType, byte[] address, short port) {
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
        return this.write(bufferToRespond)
                .compose(done -> {
                    getLogger().info("RESPOND <" + desc + "> DONE");
                    if (repByte != 0x00) {
                        getLogger().error("TO CLOSE");
                        return close();
                    } else {
                        return Future.succeededFuture();
                    }
                }, throwable -> {
                    getLogger().exception("RESPOND <" + desc + "> FAILED", throwable);
                    return Future.failedFuture(throwable);
                });
    }

    private Future<Void> handleSocketToActualServer(NetSocket socket) {
        socketToActualServer = socket;
        socketToActualServer
                .handler(bufferFromActualServer -> {
                    getLogger().info("DATA FROM TARGET TO CLIENT, " + bufferFromActualServer.length() + " bytes");

                    this.write(bufferFromActualServer)
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
                    close();
                });

        //atomicSocketToActualServer.set(socketToActualServer);
        getLogger().info("CREATED SOCKET TO TARGET " + socketToActualServer.remoteAddress().toString());
        // send 0x00 succeeded
        protocolStepEnum.set(ProtocolStepEnum.STEP_4_TRANSFER);
        //todo debug: should x1:x2 be
        byte x0 = 0x01;
//        byte[] x1 = new byte[]{0, 0, 0, 0};
//        short x2 = 0;
        byte[] x1 = this.localAddressByteArray;
        short x2 = this.localPortShort;

        // addressType rawDestinationAddress rawDestinationPort
        return this.respondInStep3((byte) 0x00, x0, x1, x2);
    }

    private Future<Void> handlerStep4(Buffer bufferFromClient) {
        return socketToActualServer.write(bufferFromClient)
                .compose(done -> {
                    getLogger().info("TRANSFERRED DATA FROM CLIENT TO TARGET");
                    return Future.succeededFuture();
                }, throwable -> {
                    getLogger().exception("FAILED TO TRANSFER DATA FROM CLIENT TO TARGET", throwable);
                    return socketToActualServer.close();
                });
    }

    /**
     * Buffer Since
     * ↓
     * ATYP [1] | DST.ADDR [Variable] | DST.PORT [2]
     */
    private static class RFC1928AddressBytesParser {
        private final Buffer bufferFromClient;
        private byte addressType;
        private byte[] rawDestinationAddress;
        private String destinationAddress;
        private final short rawDestinationPort;
        private final Buffer restBuffer;

        public RFC1928AddressBytesParser(Buffer buffer) {
            this.bufferFromClient = buffer;
            int ptr = 0;
            ptr = parseAddressType(ptr);

            if (addressType == 0x01) {
                ptr = parseAddressIPv4(ptr);
            } else if (addressType == 0x03) {
                ptr = parseAddressDomain(ptr);
            } else if (addressType == 0x04) {
                ptr = parseAddressIPv6(ptr);
            } else {
                throw new RuntimeException("Address Type Unknown");
            }

            rawDestinationPort = bufferFromClient.getShort(ptr);
            restBuffer = buffer.getBuffer(ptr + 2, buffer.length());
        }

        private int parseAddressType(int ptr) {
            // ATYP 目标地址类型，DST.ADDR的数据对应这个字段的类型。
            // 0x01表示IPv4地址，DST.ADDR为4个字节
            // 0x03表示域名，DST.ADDR是一个可变长度的域名
            // 0x04表示IPv6地址，DST.ADDR为16个字节长度
            this.addressType = bufferFromClient.getByte(ptr);
            return ptr + 1;
        }

        private int parseAddressIPv4(int ptr) {
            rawDestinationAddress = bufferFromClient.getBytes(ptr, ptr + 4);

            try {
                destinationAddress = Inet4Address.getByAddress(rawDestinationAddress).getHostAddress();
            } catch (UnknownHostException e) {
                destinationAddress = null;
            }

            return ptr + 4;
        }

        private int parseAddressIPv6(int ptr) {
            rawDestinationAddress = bufferFromClient.getBytes(ptr, ptr + 16);

            try {
                destinationAddress = Inet6Address.getByAddress(rawDestinationAddress).getHostAddress();
            } catch (UnknownHostException e) {
                destinationAddress = null;
            }

            return ptr + 16;
        }

        private int parseAddressDomain(int ptr) {
            // the address field contains a fully-qualified domain name.
            // The first octet of the address field contains the number of octets of name that follow,
            //  there is no terminating NUL octet.
            byte domainLength = bufferFromClient.getByte(ptr);
            rawDestinationAddress = bufferFromClient.getBytes(ptr + 1, ptr + 1 + domainLength);
            destinationAddress = new String(rawDestinationAddress);
            return ptr + 1 + domainLength;
        }

        public byte getAddressType() {
            return addressType;
        }

        public short getRawDestinationPort() {
            return rawDestinationPort;
        }

        public byte[] getRawDestinationAddress() {
            return rawDestinationAddress;
        }

        public String getDestinationAddress() {
            return destinationAddress;
        }

        public Buffer getRestBuffer() {
            return restBuffer;
        }
    }


}
