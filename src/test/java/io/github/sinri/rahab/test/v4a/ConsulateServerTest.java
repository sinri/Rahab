package io.github.sinri.rahab.test.v4a;

import io.github.sinri.keel.Keel;
import io.github.sinri.rahab.test.RahabTestKit;
import io.github.sinri.rahab.v4a.consulate.ConsulateServer;

public class ConsulateServerTest {
    public static void main(String[] args) {
        RahabTestKit.init();


        new ConsulateServer("/consulate", 33333, 20000, "116.62.78.192", null, null)
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
