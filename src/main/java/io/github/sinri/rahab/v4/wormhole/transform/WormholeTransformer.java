package io.github.sinri.rahab.v4.wormhole.transform;

import io.github.sinri.keel.core.logger.KeelLogger;
import io.vertx.core.buffer.Buffer;

import java.util.ArrayList;
import java.util.List;

public abstract class WormholeTransformer {
    protected KeelLogger logger = KeelLogger.silentLogger();

    protected Buffer unhandledBuffer = Buffer.buffer();

    abstract protected Buffer transformOnce(Buffer inputBuffer);

    public final void setLogger(KeelLogger logger) {
        //such as Keel.outputLogger("TransformerFromHttpRequestToRaw").setLowestLevel(KeelLogLevel.DEBUG);
        this.logger = logger;
    }

    final public List<Buffer> transform(Buffer inputBuffer) {
        List<Buffer> bufferList = new ArrayList<>();

        Buffer buffer = this.transformOnce(inputBuffer);
        if (buffer.length() > 0) {
            bufferList.add(buffer);
        }

        while (true) {
            Buffer moreBuffer = this.transformOnce(Buffer.buffer());
            if (moreBuffer.length() > 0) {
                bufferList.add(moreBuffer);
                continue;
            }
            break;
        }

        return bufferList;
    }
}
