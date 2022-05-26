package io.github.sinri.Rahab.test.v3;

import io.github.sinri.Rahab.test.RahabTestKit;
import io.github.sinri.Rahab.v3.consulate.ConsulateClient;
import io.github.sinri.keel.Keel;

public class ConsulateClientTest {
    public static void main(String[] args) {
        RahabTestKit.init();
        new ConsulateClient(7090, "http://127.0.0.1:33333/consulate")
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
