package io.github.sinri.rahab.v4.liaison.meta;

public class BrokerOptions {
    private int proxyRegistrationPort = 20001;
    private int terminalServicePort = 20000;

    public BrokerOptions setProxyRegistrationPort(int proxyRegistrationPort) {
        this.proxyRegistrationPort = proxyRegistrationPort;
        return this;
    }

    public BrokerOptions setTerminalServicePort(int terminalServicePort) {
        this.terminalServicePort = terminalServicePort;
        return this;
    }

    public int getProxyRegistrationPort() {
        return proxyRegistrationPort;
    }

    public int getTerminalServicePort() {
        return terminalServicePort;
    }
}
