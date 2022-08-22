package io.github.sinri.rahab.test.v4a;

import io.github.sinri.keel.Keel;
import io.github.sinri.rahab.test.RahabTestKit;
import io.github.sinri.rahab.v4a.consulate.ConsulateClient;

public class ConsulateClientTest {
    public static void main(String[] args) {
        RahabTestKit.init();
        new ConsulateClient(7090, "127.0.0.1", 33333, "/consulate")
                .deployMe()
                .onComplete(stringAsyncResult -> {
                    if (stringAsyncResult.failed()) {
                        Keel.outputLogger("ConsulateClientTest").exception("ERROR", stringAsyncResult.cause());
                    } else {
                        Keel.outputLogger("ConsulateClientTest").info("START " + stringAsyncResult.result() + " listen on 7090");
                    }
                });
    }
}
