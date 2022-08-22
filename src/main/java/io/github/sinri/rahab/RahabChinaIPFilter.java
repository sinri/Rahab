package io.github.sinri.rahab;

import io.github.sinri.keel.Keel;
import io.github.sinri.keel.core.logger.KeelLogger;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Data based on IP2Location LITE, download on 2022-08-12
 */
public class RahabChinaIPFilter {
    private final static RahabChinaIPFilter instance = new RahabChinaIPFilter();

    private final List<Long> list;

    private RahabChinaIPFilter() {
        list = new ArrayList<>();
        try {
            byte[] bytes = Keel.fileHelper().readFileAsByteArray("ip.china.json", true);
            JsonArray array = new JsonArray(Buffer.buffer(bytes));
            array.forEach(item -> list.add(Long.parseLong(item.toString())));
        } catch (IOException exception) {
            Keel.outputLogger("RahabChinaIPFilter").exception(exception);
        }
//        for (var item : list) {
//            System.out.println(item+ " : "+Keel.netHelper().convertNumberToIPv4(item));
//        }
    }

    public static RahabChinaIPFilter getInstance() {
        return instance;
    }

    public boolean isChinaMainlandIP(String ip) {
        KeelLogger logger = Keel.outputLogger("RahabChinaIPFilter");
        if (ip == null) {
            logger.warning("ip is null");
        }
        Long x = Keel.netHelper().convertIPv4ToNumber(ip);
        if (x == null) {
            logger.warning("ip [" + ip + "] is invalid");
            return true;
        }
        int total = this.list.size();
        if (total == 0 || total % 2 != 0) {
            logger.warning("TOTAL ERROR: " + total);
            return true;
        }
        if (x < this.list.get(0)) {
            return false;
        }
        if (x > this.list.get(total - 1)) {
            return false;
        }
        if (x.equals(this.list.get(0)) || x.equals(this.list.get(total - 1))) {
            return true;
        }

        int left = 0;

        int right = total - 1;
        while (true) {
            int mid = (left + right) / 2;
            if (x < this.list.get(mid)) {
                // [left] x [mid] [right]
                right = mid;
            } else if (x.equals(this.list.get(mid))) {
                // [left] x=[mid]=x [right]
                return true;
            } else {
                // [left] [mid] x [right]
                left = mid;
            }
            if (left >= right) return false;
            if (left + 1 == right) {
                return left % 2 == 0;
            }
        }
    }

    public static void main(String[] args) {
        JsonArray ips = new JsonArray()
                .add("212.64.1.0")
                .add("223.120.127.0")
                .add("185.25.48.95");
        ips.forEach(ip -> {
            long t1 = new Date().getTime();
            boolean isChinaMainlandIP = RahabChinaIPFilter.getInstance()
                    .isChinaMainlandIP((String) ip);
            long t2 = new Date().getTime();
            System.out.println(ip + " is of China? " + isChinaMainlandIP + " time:" + (t2 - t1));
        });
    }
}
