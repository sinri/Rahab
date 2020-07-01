package io.github.sinri.Rahab.kit;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;

import java.util.HashMap;

public class HandlerForRemote extends RahabHandler {

    public HandlerForRemote(HttpServerRequest request) {
        super(request);
    }

    @Override
    public void handle() {
        // 1. decode
        request.handler(buffer->{
            logger.info("囤积Body数据到Buffer，size: " + buffer.length());
            bodyBuffer.appendBuffer(buffer);
        });
        // 2. rebuild request
        request.endHandler(event->{
            logger.info("Remote Side received: " + bodyBuffer);

            if (bodyBuffer.length() < 0) {
                request.response()
                        .setStatusCode(200)
                        .end("Under the sun");
                return;
            }

            JsonObject object = bodyBuffer.toJsonObject();
            JsonObject header = object.getJsonObject("header");
            String method = object.getString("method");
            String schema = object.getString("schema");
            String host = object.getString("host");
            String path = object.getString("path");
            String query = object.getString("query");
            String body = object.getString("body");

            //debug
            request.response().write(object.toBuffer()).write("\n");

            // 3. execute request
            HashMap<String, String> headers = new HashMap<>();
            header.forEach(entry -> {
                headers.put(entry.getKey(), (String) entry.getValue());
            });
            this.pumpHttpRequest(
                    method,
                    host,
                    schema.equalsIgnoreCase("https") ? 443 : 80,
                    path + ((query == null || query.equalsIgnoreCase("")) ? "" : ("?" + query)),
                    headers,
                    body
            );
        });

        // 4. respond
        //request.response().end("Good Bye");
    }
}
