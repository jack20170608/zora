package top.ilovemyhome.zora.common.serialize;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;

public class JdkSerializeTool {
    private static Logger logger = LoggerFactory.getLogger(JdkSerializeTool.class);


    // ------------------------ serialize and unserialize ------------------------

    public static byte[] serialize(Object object) {
        ObjectOutputStream oos = null;
        ByteArrayOutputStream baos = null;
        try {
            baos = new ByteArrayOutputStream();
            oos = new ObjectOutputStream(baos);
            oos.writeObject(object);
            byte[] bytes = baos.toByteArray();
            return bytes;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        } finally {
            try {
                oos.close();
                baos.close();
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
            }
        }
        return null;
    }


    public static <T> T deserialize(byte[] bytes, Class<T> clazz) {
        if (bytes == null) return null;
        ByteArrayInputStream bais = null;
        ObjectInputStream ois = null;
        try {
            bais = new ByteArrayInputStream(bytes);
            ois = new ObjectInputStream(bais);
            Object obj = ois.readObject();
            return clazz.cast(obj);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        } finally {
            try {
                if (ois != null) ois.close();
                if (bais != null) bais.close();
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
            }
        }
        return null;
    }


}
