package top.ilovemyhome.zora.config.bean;

import java.util.List;
import java.util.Map;

public class FlywayConfig {

    private boolean enabled;
    private String driver;
    private String url;
    private String user;
    private String password;
    private String metaTable;
    private List<String> locations;
    private Map<String, Object> placeHolders;
    private List<String> callbacks;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDriver() {
        return driver;
    }

    public void setDriver(String driver) {
        this.driver = driver;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getMetaTable() {
        return metaTable;
    }

    public void setMetaTable(String metaTable) {
        this.metaTable = metaTable;
    }

    public List<String> getLocations() {
        return locations;
    }

    public void setLocations(List<String> locations) {
        this.locations = locations;
    }

    public Map<String, Object> getPlaceHolders() {
        return placeHolders;
    }

    public void setPlaceHolders(Map<String, Object> placeHolders) {
        this.placeHolders = placeHolders;
    }

    public List<String> getCallbacks() {
        return callbacks;
    }

    public void setCallbacks(List<String> callbacks) {
        this.callbacks = callbacks;
    }

    @Override
    public String toString() {
        return "FlywayConfig{" +
            "enabled=" + enabled +
            ", driver='" + driver + '\'' +
            ", url='" + url + '\'' +
            ", user='" + user + '\'' +
            ", password='" + password + '\'' +
            ", metaTable='" + metaTable + '\'' +
            ", locations=" + locations +
            ", placeHolders=" + placeHolders +
            ", callbacks=" + callbacks +
            '}';
    }
}
