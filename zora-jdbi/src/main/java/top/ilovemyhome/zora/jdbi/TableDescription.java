package top.ilovemyhome.zora.jdbi;


import org.apache.commons.lang3.StringUtils;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public class TableDescription {

    private final boolean idAutoGenerate;
    private final String name;
    private final TreeMap<String, String> fieldColumnMap;
    private final String idField;
    private final String fromClause;
    private final Class<?> entityClass;

    public String getName() {
        return name;
    }

    public Class<?> getEntityClass() {
        return entityClass;
    }

    public String getIdField() {
        return idField;
    }


    public String getFromClause() {
        return fromClause;
    }

    public boolean isIdAutoGenerate() {
        return idAutoGenerate;
    }

    public TreeMap<String, String> getFieldColumnMap() {
        if (fieldColumnMap == null) {
            throw new IllegalStateException("fieldColumnMap is not initialized. Ensure it is set via builder before use.");
        }
        // Return unmodifiable copy to prevent external mutation
        return new TreeMap<>(fieldColumnMap);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private boolean idAutoGenerate;
        private String name;
        private Map<String, String> fieldColumnMap;
        private String idField;
        private String fromClause;
        private Class entityClass;

        private Builder() {
        }

        public Builder withIdAutoGenerate(boolean idAutoGenerate) {
            this.idAutoGenerate = idAutoGenerate;
            return this;
        }

        public Builder withName(String name) {
            this.name = name;
            return this;
        }

        public Builder withFieldColumnMap(Map<String, String> fieldColumnMap) {
            this.fieldColumnMap = fieldColumnMap;
            return this;
        }

        public Builder withIdField(String idField) {
            this.idField = idField;
            return this;
        }

        public Builder withFromClause(String fromClause) {
            this.fromClause = fromClause;
            return this;
        }

        public Builder withEntityClass(Class<?> entityClass) {
            this.entityClass = entityClass;
            return this;
        }

        public TableDescription build() {
            return new TableDescription(name, entityClass, idAutoGenerate, fromClause, fieldColumnMap, idField);
        }
    }

    private TableDescription(String name, Class<?> entityClass, boolean idAutoGenerate, String fromClause, Map<String, String> fieldColumnMap
        , String idField) {
        this.name = name;
        this.idAutoGenerate = idAutoGenerate;
        this.idField = idField;
        this.entityClass = entityClass;
        if (StringUtils.isNotBlank(fromClause)) {
            this.fromClause = fromClause;
        } else {
            this.fromClause = name;
        }
        this.fieldColumnMap = Objects.nonNull(fieldColumnMap) ? new TreeMap<>(fieldColumnMap) : null;
    }

}
