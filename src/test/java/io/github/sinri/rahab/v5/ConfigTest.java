package io.github.sinri.rahab.v5;

import org.junit.jupiter.api.Test;
import java.util.Properties;
import static org.junit.jupiter.api.Assertions.*;

class ConfigTest {
    @Test void rejectsUnknownModesMissingTargetsAndInvalidPorts() {
        Properties p = new Properties();
        assertThrows(IllegalArgumentException.class,() -> new Rahab.Config(p).validate());
        p.setProperty("rahab.program","wormhole");
        p.setProperty("rahab.wormhole.port","65536");
        assertThrows(IllegalArgumentException.class,() -> new Rahab.Config(p).validate());
        p.setProperty("rahab.wormhole.port","8080");
        assertThrows(IllegalArgumentException.class,() -> new Rahab.Config(p).validate());
    }
    @Test void rejectsUnknownAuthenticationAndMissingCredentials() {
        Properties p = new Properties(); p.setProperty("rahab.program","proxy-socks5");
        p.setProperty("rahab.proxy-socks5.port","1080");
        p.setProperty("rahab.proxy-socks5.auth-method","03");
        assertThrows(IllegalArgumentException.class,() -> new Rahab.Config(p).validate());
        p.setProperty("rahab.proxy-socks5.auth-method","02");
        assertThrows(IllegalArgumentException.class,() -> new Rahab.Config(p).validate());
    }
    @Test void mirrorRequiresSeparatePortsAndToken() {
        Properties p = new Properties(); p.setProperty("rahab.program","periscope-mirror");
        p.setProperty("rahab.periscope-mirror.port","8000");
        p.setProperty("rahab.periscope-mirror.lens.port","8000");
        p.setProperty("rahab.periscope-mirror.token","secret");
        assertThrows(IllegalArgumentException.class,() -> new Rahab.Config(p).validate());
        p.setProperty("rahab.periscope-mirror.lens.port","8001");
        assertDoesNotThrow(() -> new Rahab.Config(p).validate());
        p.remove("rahab.periscope-mirror.token");
        assertThrows(IllegalArgumentException.class,() -> new Rahab.Config(p).validate());
    }
    @Test void allPublishedExamplesAreValid() throws Exception {
        for (String name : new String[]{"wormhole", "socks5", "periscope-mirror", "periscope-lens"}) {
            Properties p = new Properties();
            try (java.io.InputStream input = java.nio.file.Files.newInputStream(java.nio.file.Path.of("examples",name + ".properties"))) {
                p.load(input);
            }
            assertDoesNotThrow(() -> new Rahab.Config(p).validate(), name);
        }
    }

}
