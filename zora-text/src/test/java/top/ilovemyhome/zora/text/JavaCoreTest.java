package top.ilovemyhome.zora.text;

import org.junit.jupiter.api.Test;

public class JavaCoreTest {

    @Test
    void testInstanceOf(){
        for (int i = 0; i < 2000; i++) {
            if (Integer.valueOf(i) != Integer.valueOf(i)) {
                System.out.println(i + ": " + (Integer.valueOf(i) == Integer.valueOf(i)));
            }
        }
    }
}
