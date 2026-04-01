package top.ilovemyhome.zora.common.lang;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MapUtilTest {

    @Test
    void toStringMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("key1", "value1");
        map.put("key2", 2);
        map.put("key3", true);
        Map<String, String> stringMap = MapUtil.toStringMap(map);
        assertEquals("value1", stringMap.get("key1"));
        assertEquals("2", stringMap.get("key2"));
        assertEquals("true", stringMap.get("key3"));
    }
}
