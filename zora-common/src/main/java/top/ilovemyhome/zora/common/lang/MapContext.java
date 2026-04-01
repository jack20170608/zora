package top.ilovemyhome.zora.common.lang;

import java.io.Serializable;
import java.util.*;

/**
 * A {@code MapContext} provides a common base for context-based data storage in a {@link Map}.  Type-safe attribute
 * retrieval is provided for subclasses with the {@link #getTypedValue(String, Class)} method.
 *
 * @since 1.0
 */
public class MapContext implements Map<String, Object>, Serializable {

    private static final long serialVersionUID = 5373399119017820322L;

    private final Map<String, Object> backingMap;

    public MapContext() {
        this.backingMap = new HashMap<String, Object>();
    }

    public MapContext(Map<String, Object> map) {
        this();
        if (!MapUtil.isEmpty(map)) {
            this.backingMap.putAll(map);
        }
    }

    /**
     * Performs a {@link #get get} operation but additionally ensures that the value returned is of the specified
     * {@code type}.  If there is no value, {@code null} is returned.
     *
     * @param key  the attribute key to look up a value
     * @param type the expected type of the value
     * @param <E>  the expected type of the value
     * @return the typed value or {@code null} if the attribute does not exist.
     */
    @SuppressWarnings({"unchecked"})
    protected <E> E getTypedValue(String key, Class<E> type) {
        E found = null;
        Object o = backingMap.get(key);
        if (o != null) {
            if (!type.isAssignableFrom(o.getClass())) {
                String msg = "Invalid object found in SubjectContext Map under key [" + key + "].  Expected type "
                        + "was [" + type.getName() + "], but the object under that key is of type "
                        + "[" + o.getClass().getName() + "].";
                throw new IllegalArgumentException(msg);
            }
            found = (E) o;
        }
        return found;
    }

    /**
     * Places a value in this context map under the given key only if the given {@code value} argument is not null.
     *
     * @param key   the attribute key under which the non-null value will be stored
     * @param value the non-null value to store.  If {@code null}, this method does nothing and returns immediately.
     */
    protected void nullSafePut(String key, Object value) {
        if (value != null) {
            put(key, value);
        }
    }

    public int size() {
        return backingMap.size();
    }

    public boolean isEmpty() {
        return backingMap.isEmpty();
    }

    public boolean containsKey(Object o) {
        return backingMap.containsKey(o);
    }

    public boolean containsValue(Object o) {
        return backingMap.containsValue(o);
    }

    public Object get(Object o) {
        return backingMap.get(o);
    }

    public Object put(String s, Object o) {
        return backingMap.put(s, o);
    }

    public Object remove(Object o) {
        return backingMap.remove(o);
    }

    public void putAll(Map<? extends String, ?> map) {
        backingMap.putAll(map);
    }

    public void clear() {
        backingMap.clear();
    }

    public Set<String> keySet() {
        return Collections.unmodifiableSet(backingMap.keySet());
    }

    public Collection<Object> values() {
        return Collections.unmodifiableCollection(backingMap.values());
    }

    public Set<Entry<String, Object>> entrySet() {
        return Collections.unmodifiableSet(backingMap.entrySet());
    }
}
