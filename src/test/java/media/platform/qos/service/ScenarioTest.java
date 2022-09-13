package media.platform.qos.service;

import media.platform.qos.manager.RmqTrafficManager;
import org.apache.commons.lang.SerializationUtils;

import java.util.concurrent.ConcurrentHashMap;

/**
 * @author dajin kim
 */
public class ScenarioTest {
    static final RmqTrafficManager manager = RmqTrafficManager.getInstance();

    public void mapTest() {
        ConcurrentHashMap<String, Long> originMap = new ConcurrentHashMap<>();

        System.out.println(originMap.putIfAbsent("A", 123L));
        System.out.println(originMap.putIfAbsent("B", 111L));

        System.out.println(originMap.remove("B"));
        System.out.println(originMap.remove("B"));

        System.out.println(originMap.get("B"));

        // Deep Copy Test
        ConcurrentHashMap<String, Long> cloneMap
                = (ConcurrentHashMap<String, Long>) SerializationUtils.clone(originMap);

        cloneMap.put("A", 999L);
        System.out.println(originMap.get("A"));
        System.out.println(cloneMap.get("A"));
    }




}
