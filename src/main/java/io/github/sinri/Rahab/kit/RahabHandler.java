package io.github.sinri.Rahab.kit;

import io.github.sinri.Rahab.Rahab;
import io.github.sinri.Rahab.logger.RahabLogger;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.streams.Pump;

import java.util.Map;
import java.util.UUID;

abstract public class RahabHandler {

    protected final String requestId;
    protected final Logger logger;
    protected final Buffer bodyBuffer;
    protected HttpServerRequest request;

    public RahabHandler(HttpServerRequest request) {
        this.request = request;
        this.requestId = UUID.randomUUID().toString();

        if (Rahab.vertxLoggingOnly) {
            this.logger = LoggerFactory.getLogger("RH#" + this.requestId);
        } else {
            this.logger = RahabLogger.getLogger("RH#" + this.requestId);
        }
        // 初始化请求的本体
        this.bodyBuffer = Buffer.buffer();

        // 设定请求出现问题时的回调。此处直接关闭请求。
        request.exceptionHandler(exception -> {
            logger.error("网关请求中出现异常", exception);
            abandonIncomingRequest(500, exception.getMessage());
        });
    }

    public void abandonIncomingRequest(int code,String message) {
        // 是否需要像SLB一样设置一个特殊的报错回复报文，比直接关闭更友好一些。
        request.response()
                .setStatusCode(code)
                .setStatusMessage(message)
                .endHandler(event -> {
                    request.response().close();
                    request.connection().close();
                })
                .end();
    }

    public void pumpHttpRequest(String method,String address, int port, String path, Map<String,String> headers, String body){
        // 准备转发器并设置连接回调
        HttpClient client = RahabAgent.getVertx().createHttpClient();
        client.connectionHandler(httpConnection -> logger.info("转发器已连接服务端 " + httpConnection.remoteAddress()));

        // 根据路由准备转发请求的配置
        RequestOptions requestOptions = new RequestOptions()
                .setHost(address)
                .setPort(port)
                .setSsl(port==443)
                .setURI(path);
        // 创建转发请求
        HttpMethod httpMethod=HttpMethod.GET;
        if(method.equalsIgnoreCase("get")){
            httpMethod=HttpMethod.GET;
        }else if (method.equalsIgnoreCase("post")){
            httpMethod=HttpMethod.POST;
        }
        HttpClientRequest requestToSend = client.request(httpMethod, requestOptions, response -> {
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
        requestToSend.setFollowRedirects(false);
        // 如果转发请求出错，直接关闭请求
        requestToSend.exceptionHandler(exception -> {
            logger.error("转发器出现异常将关闭，同时将对网关请求进行报错回复", exception);
            if (requestToSend.connection() != null) requestToSend.connection().close();
            abandonIncomingRequest(500,exception.getMessage());
        });

        requestToSend.putHeader("X-Rahab-Request-Id", requestId);
        headers.forEach(requestToSend::putHeader);

        // 7. respond
        //request.response().end(object.encode());
        if(body==null){
            requestToSend.end();
        }else {
            requestToSend.end(body);
        }
    }

    abstract public void handle();
}
