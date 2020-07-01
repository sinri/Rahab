package io.github.sinri.Rahab.logger;

import io.vertx.core.logging.Logger;
import io.vertx.core.spi.logging.LogDelegate;

public class RahabLogger extends Logger {
    protected static RahabLoggerDelegate delegate;
    protected static RahabLogger logger;

    public RahabLogger(LogDelegate delegate) {
        super(delegate);
    }

    public static RahabLogger getLogger() {
        if (delegate == null) {
            delegate = new RahabLoggerDelegate();
        }
        if (logger == null) {
            logger = new RahabLogger(delegate);
        }
        return logger;
    }
}
