package io.github.sinri.rahab.v5;

import io.github.sinri.rahab.RahabChinaIPFilter;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IPFilterTest {
    @Test void loadsBundledRangesWithoutKeel() {
        RahabChinaIPFilter filter = RahabChinaIPFilter.getInstance();
        assertTrue(filter.isChinaMainlandIP("1.0.1.0"));
        assertTrue(filter.isChinaMainlandIP("1.0.3.255"));
        assertFalse(filter.isChinaMainlandIP("1.0.4.0"));
        assertFalse(filter.isChinaMainlandIP("8.8.8.8"));
        assertFalse(filter.isChinaMainlandIP("0.0.0.0"));
        assertFalse(filter.isChinaMainlandIP("255.255.255.255"));
    }
}
