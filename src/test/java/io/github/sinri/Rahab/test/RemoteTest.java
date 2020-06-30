package io.github.sinri.Rahab.test;

import io.github.sinri.Rahab.kit.RahabAgent;

public class RemoteTest {
    public static void main(String[] args){
        RahabAgent.setPort(8002);
        RahabAgent.setMode(RahabAgent.MODE_REMOTE_AGENT);
        //RahabAgent.setRemoteAddress("remote.passover.de");

        RahabAgent.initializeVertx(5);

        (new RahabAgent()).run();
    }
}
