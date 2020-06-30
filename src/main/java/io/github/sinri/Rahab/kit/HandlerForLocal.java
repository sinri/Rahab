package io.github.sinri.Rahab.kit;

import io.vertx.core.http.*;
import io.vertx.core.json.JsonObject;
import io.vertx.core.streams.Pump;

import java.util.HashMap;

public class HandlerForLocal extends RahabHandler {

    public HandlerForLocal(HttpServerRequest request) {
        super(request);
    }

    @Override
    public void handle() {
        // 1. fetch header
        HashMap<String, String> cachedHeaderMap = new HashMap<>();
        request.headers().forEach(entry -> {
            this.logger.info("original header [" + entry.getKey() + "] : " + entry.getValue());
            if (entry.getKey().equalsIgnoreCase("content-type")) {
                cachedHeaderMap.put(entry.getKey(), entry.getValue());
            }
        });
        cachedHeaderMap.forEach((k, v) -> {
            this.logger.info("cachedHeaderMap [" + k + "] : " + v);
        });
        // 2. fetch domain and path
        String method = request.method().name();
        String schema = "http";
        String host = request.host();
        Integer port = 80;
        if (host.contains(":")) {
            String[] x = host.split(":");
            host = x[0];
            port = Integer.parseInt(x[1]);
        }
        String path = request.path();
        this.logger.info("method: " + method);
        this.logger.info("host: " + host + " port: " + port);
        this.logger.info("path: " + path);
        // 3. fetch query string
        String query = request.query();
        this.logger.info("query: " + query);
        // 4. fetch request body (for POST)
        request.handler(buffer -> {
            logger.info("囤积Body数据到Buffer，size: " + buffer.length());
            bodyBuffer.appendBuffer(buffer);
        });
        // 5. encode
        String finalHost = host;
        request.endHandler(event -> {
            logger.info("Request Fetch End. Body: " + bodyBuffer);

            JsonObject object = new JsonObject();
            object.put("header", cachedHeaderMap);
            object.put("method", method);
            object.put("schema", schema);
            object.put("host", finalHost);
            object.put("path", path);
            object.put("query", query);
            object.put("body", bodyBuffer.toString());
            logger.info("Packaged: " + object.encode());
            // 6. send to remote
            // 7. respond
            HashMap<String, String> headerToSend = new HashMap<>();
            headerToSend.put("Content-Type", "application/json");
            this.pumpHttpRequest(
                    "post",
                    RahabAgent.getRemoteAddress(),
                    RahabAgent.getRemotePort(),
                    "handleRequestFromLocalSide",
                    headerToSend,
                    object.encode()
            );
        });
        /*
        // 准备转发器并设置连接回调
        HttpClient client = RahabAgent.getVertx().createHttpClient();
        client.connectionHandler(httpConnection -> logger.info("转发器已连接服务端 " + httpConnection.remoteAddress()));

        // 根据路由准备转发请求的配置
        RequestOptions requestOptions = new RequestOptions()
                .setHost(RahabAgent.getRemoteAddress())
                .setPort(RahabAgent.getRemotePort())
                .setSsl(RahabAgent.getRemotePort()==443)
                .setURI("handleRequestFromLocalSide");
        // 创建转发请求
        HttpClientRequest requestToRemote = client.request(request.method(), requestOptions, response -> {
            logger.info("转发器收到服务端报文 " + response.statusCode() + " " + response.statusMessage());
            request.response()
                    .setStatusCode(response.statusCode())
                    .setStatusMessage(response.statusMessage());
            StringBuilder headersForLog = new StringBuilder();
            response.headers().forEach(pair -> {
                headersForLog.append(pair.getKey()).append(" : ").append(pair.getValue()).append("\n");
                request.response().putHeader(pair.getKey(), pair.getValue());
            });
            logger.info("转发器收到服务器报文Headers如下\n" + headersForLog);
            Pump.pump(response, request.response()).start();
            response.endHandler(pumpEnd -> {
                logger.info("转发器转发服务端报文完毕，同时结束网关请求");
                request.response().end();
            });

        });

        // 转发请求不需要跟踪30x转移指令
        requestToRemote.setFollowRedirects(false);
        // 如果转发请求出错，直接关闭请求
        requestToRemote.exceptionHandler(exception -> {
            logger.error("转发器出现异常将关闭，同时将对网关请求进行报错回复", exception);
            if (requestToRemote.connection() != null) requestToRemote.connection().close();
            abandonIncomingRequest(500,exception.getMessage());
        });

        requestToRemote.putHeader("X-Rahab-Request-Id", requestId);

        // 7. respond
        //request.response().end(object.encode());
        requestToRemote.end(object.encode());

         */
    }
}
