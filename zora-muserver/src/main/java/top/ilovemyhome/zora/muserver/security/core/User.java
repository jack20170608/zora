package top.ilovemyhome.zora.muserver.security.core;


import org.apache.commons.lang3.StringUtils;

import java.security.Principal;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;

public record User(String id
    , String name
    , String displayName
    , Collection<String> roles
    , String passwordHashVal
    , Map<String, Object> attributes) implements Principal {

    public boolean haveRole(final String role) {
        if (Objects.isNull(roles) || StringUtils.isBlank(role)){
            return false;
        }
        return roles.contains(role);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User that = (User) o;
        return Objects.equals(id, that.id) && Objects.equals(getName(), that.getName());
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, getName());
    }

    @Override
    public String toString() {
        return "User{" +
            "id='" + id + '\'' +
            ", name='" + name + '\'' +
            ", displayName='" + displayName + '\'' +
            ", roles=" + roles +
            ", attributes=" + attributes +
            '}';
    }
}
