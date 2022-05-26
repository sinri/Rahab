package io.github.sinri.Rahab.test.v3;

import io.github.sinri.Rahab.test.RahabTestKit;
import io.github.sinri.Rahab.v3.consulate.ConsulateServer;
import io.github.sinri.keel.Keel;

public class ConsulateServerTest {
    public static void main(String[] args) {
        RahabTestKit.init();
        new ConsulateServer("/consulate", 33333, 20000, "116.62.78.192")
                .deployMe()
                .onComplete(stringAsyncResult -> {
                    if (stringAsyncResult.failed()) {
                        Keel.outputLogger("ConsulateServerTest").exception("ConsulateServerTest ERROR", stringAsyncResult.cause());
                    } else {
                        Keel.outputLogger("ConsulateServerTest").info("ConsulateServerTest START " + stringAsyncResult.result() + " listen on 33333 -> 116.62.78.192:20000");
                    }
                });
    }
}
