package io.github.sinri.Rahab.test.v2;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.dns.DnsClient;
import io.vertx.core.dns.DnsClientOptions;

public class DNSTest {
    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        DnsClient dnsClient = vertx.createDnsClient(new DnsClientOptions()
                .setHost("114.114.114.114")
                .setPort(53)
                .setQueryTimeout(1000 * 10)
        );
        dnsClient.lookup4("www.leqee.com")
                .onSuccess(s -> {
                    System.out.println("dns ok: " + s);
                })
                .onFailure(throwable -> {
                    System.out.println("dns failed: " + throwable.getMessage());
                })
                .eventually(v -> {
                    vertx.close();
                    return Future.succeededFuture();
                });
    }
}
