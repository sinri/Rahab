package io.github.sinri.Rahab.test;

import io.github.sinri.Rahab.Rahab;
import io.github.sinri.Rahab.kit.RahabAgent;

public class LocalTest {
    public static void main(String[] args){
        RahabAgent.setPort(8001);
        RahabAgent.setMode(RahabAgent.MODE_LOCAL_AGENT);
        RahabAgent.setRemoteAddress("remote.passover.de");
        RahabAgent.setRemotePort(8002);

        RahabAgent.initializeVertx(5);

        (new RahabAgent()).run();
    }
}
