package io.github.sinri.Rahab.logger;

import io.vertx.core.logging.Logger;
import io.vertx.core.spi.logging.LogDelegate;

import java.util.HashMap;

public class RahabLogger extends Logger {

    protected static HashMap<String, RahabLogger> loggerCache = new HashMap<>();

    public RahabLogger(LogDelegate delegate) {
        super(delegate);
    }

    public static RahabLogger getLogger(Class<?> clazz) {
        String name = clazz.isAnonymousClass() ? clazz.getEnclosingClass().getCanonicalName() : clazz.getCanonicalName();
        return getLogger(name);
    }

    public static RahabLogger getLogger(String name) {
        RahabLogger logger = loggerCache.get(name);
        if (logger == null) {
            logger = new RahabLogger(new RahabLoggerDelegate(name));
            loggerCache.put(name, logger);
        }
        return logger;
    }
}
