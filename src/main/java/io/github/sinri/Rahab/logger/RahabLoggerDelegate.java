package io.github.sinri.Rahab.logger;

import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.spi.logging.LogDelegate;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class RahabLoggerDelegate implements LogDelegate {
    public static boolean debugModeOpened;
    protected SimpleDateFormat ft = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
    BufferedOutputStream bufferedOutputStream;

    @Override
    public boolean isWarnEnabled() {
        return true;
    }

    @Override
    public boolean isInfoEnabled() {
        return true;
    }

    @Override
    public boolean isDebugEnabled() {
        return debugModeOpened;
    }

    @Override
    public boolean isTraceEnabled() {
        return debugModeOpened;
    }

    @Override
    public void fatal(Object o) {
        LoggerFactory.getLogger("").fatal(o);
        writeLogToPDD("fatal", o.toString());
    }

    @Override
    public void fatal(Object o, Throwable throwable) {
        LoggerFactory.getLogger("").fatal(o, throwable);
        writeLogToPDD("fatal", o.toString() + " | " + throwable.toString());
    }

    @Override
    public void error(Object o) {
        LoggerFactory.getLogger("").error(o);
        writeLogToPDD("error", o.toString());
    }

    @Override
    public void error(Object o, Object... objects) {
        LoggerFactory.getLogger("").error(o, objects);
        StringBuilder objectsExpression = new StringBuilder(o.toString());
        for (Object object : objects) {
            objectsExpression.append(", ").append(object.toString());
        }
        writeLogToPDD("error", objectsExpression.toString());
    }

    @Override
    public void error(Object o, Throwable throwable) {
        LoggerFactory.getLogger("").error(o, throwable);
        writeLogToPDD("error", o.toString() + " | " + throwable.toString());
    }

    @Override
    public void error(Object o, Throwable throwable, Object... objects) {
        LoggerFactory.getLogger("").error(o, throwable, objects);
        StringBuilder objectsExpression = new StringBuilder(o.toString());
        objectsExpression.append(" | ").append(throwable.toString());
        for (Object object : objects) {
            objectsExpression.append(" | ").append(object.toString());
        }
        writeLogToPDD("error", objectsExpression.toString());
    }

    @Override
    public void warn(Object o) {
        LoggerFactory.getLogger("").warn(o);
        writeLogToPDD("warn", o.toString());
    }

    @Override
    public void warn(Object o, Object... objects) {
        LoggerFactory.getLogger("").warn(o, objects);
        StringBuilder objectsExpression = new StringBuilder(o.toString());
        for (Object object : objects) {
            objectsExpression.append(", ").append(object.toString());
        }
        writeLogToPDD("warn", objectsExpression.toString());
    }

    @Override
    public void warn(Object o, Throwable throwable) {
        LoggerFactory.getLogger("").warn(o, throwable);
        writeLogToPDD("warn", o.toString() + " | " + throwable.toString());
    }

    @Override
    public void warn(Object o, Throwable throwable, Object... objects) {
        LoggerFactory.getLogger("").warn(o, throwable, objects);
        StringBuilder objectsExpression = new StringBuilder(o.toString());
        objectsExpression.append(" | ").append(throwable.toString());
        for (Object object : objects) {
            objectsExpression.append(" | ").append(object.toString());
        }
        writeLogToPDD("warn", objectsExpression.toString());
    }

    @Override
    public void info(Object o) {
        LoggerFactory.getLogger("").info(o);
        writeLogToPDD("info", o.toString());
    }

    @Override
    public void info(Object o, Object... objects) {
        LoggerFactory.getLogger("").info(o, objects);
        StringBuilder objectsExpression = new StringBuilder(o.toString());
        for (Object object : objects) {
            objectsExpression.append(", ").append(object.toString());
        }
        writeLogToPDD("info", objectsExpression.toString());
    }

    @Override
    public void info(Object o, Throwable throwable) {
        LoggerFactory.getLogger("").info(o, throwable);
        writeLogToPDD("info", o.toString() + " | " + throwable.toString());
    }

    @Override
    public void info(Object o, Throwable throwable, Object... objects) {
        LoggerFactory.getLogger("").info(o, throwable, objects);
        StringBuilder objectsExpression = new StringBuilder(o.toString());
        objectsExpression.append(" | ").append(throwable.toString());
        for (Object object : objects) {
            objectsExpression.append(" | ").append(object.toString());
        }
        writeLogToPDD("info", objectsExpression.toString());
    }

    @Override
    public void debug(Object o) {
        LoggerFactory.getLogger("").debug(o);
        writeLogToPDD("debug", o.toString());
    }

    @Override
    public void debug(Object o, Object... objects) {
        LoggerFactory.getLogger("").debug(o, objects);
        StringBuilder objectsExpression = new StringBuilder(o.toString());
        for (Object object : objects) {
            objectsExpression.append(", ").append(object.toString());
        }
        writeLogToPDD("debug", objectsExpression.toString());
    }

    @Override
    public void debug(Object o, Throwable throwable) {
        LoggerFactory.getLogger("").debug(o, throwable);
        writeLogToPDD("debug", o.toString() + " | " + throwable.toString());
    }

    @Override
    public void debug(Object o, Throwable throwable, Object... objects) {
        LoggerFactory.getLogger("").debug(o, throwable, objects);
        StringBuilder objectsExpression = new StringBuilder(o.toString());
        objectsExpression.append(" | ").append(throwable.toString());
        for (Object object : objects) {
            objectsExpression.append(" | ").append(object.toString());
        }
        writeLogToPDD("debug", objectsExpression.toString());
    }

    @Override
    public void trace(Object o) {
        LoggerFactory.getLogger("").trace(o);
        writeLogToPDD("trace", o.toString());
    }

    @Override
    public void trace(Object o, Object... objects) {
        LoggerFactory.getLogger("").trace(o, objects);
        StringBuilder objectsExpression = new StringBuilder(o.toString());
        for (Object object : objects) {
            objectsExpression.append(", ").append(object.toString());
        }
        writeLogToPDD("trace", objectsExpression.toString());
    }

    @Override
    public void trace(Object o, Throwable throwable) {
        LoggerFactory.getLogger("").trace(o, throwable);
        writeLogToPDD("trace", o.toString() + " | " + throwable.toString());
    }

    @Override
    public void trace(Object o, Throwable throwable, Object... objects) {
        LoggerFactory.getLogger("").trace(o, throwable, objects);
        StringBuilder objectsExpression = new StringBuilder(o.toString());
        objectsExpression.append(" | ").append(throwable.toString());
        for (Object object : objects) {
            objectsExpression.append(" | ").append(object.toString());
        }
        writeLogToPDD("trace", objectsExpression.toString());
    }

    protected void writeLogToPDD(String level, String message) {
        String sb = "[" + ft.format(new Date()) + "] " + "<" + level + "> " + message + "\n";
//        System.out.println(sb);
        try {
            if (bufferedOutputStream == null) {
                String pddLogPath = "/proc/1/fd/1";
                bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(pddLogPath, true));
                bufferedOutputStream.write(sb.getBytes());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
