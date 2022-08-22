package io.github.sinri.rahab.v4.liaison.meta;

public class SourceOptions {
    private String sourceName = "Anonymous";
    private String brokerHost = "127.0.0.1";
    private int brokerPort = 20001;
    private String proxyHost = "127.0.0.1";
    private int proxyPort = 20002;

    public String getProxyHost() {
        return proxyHost;
    }

    public SourceOptions setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
        return this;
    }

    public int getProxyPort() {
        return proxyPort;
    }

    public SourceOptions setProxyPort(int proxyPort) {
        this.proxyPort = proxyPort;
        return this;
    }

    public int getBrokerPort() {
        return brokerPort;
    }

    public SourceOptions setBrokerPort(int brokerPort) {
        this.brokerPort = brokerPort;
        return this;
    }

    public String getBrokerHost() {
        return brokerHost;
    }

    public SourceOptions setBrokerHost(String brokerHost) {
        this.brokerHost = brokerHost;
        return this;
    }

    public String getSourceName() {
        return sourceName;
    }

    public SourceOptions setSourceName(String sourceName) {
        this.sourceName = sourceName;
        return this;
    }
}
