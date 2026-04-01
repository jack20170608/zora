package top.ilovemyhome.zora.jdbi.e2e.domain;


import java.time.YearMonth;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Bar {

    private Long id;
    private String name;
    private String others;
    private UUID uuid;
    private YearMonth billingMonth;
    private Map<String,String> attributes;
    private Map<String, Integer> intAttributes;

    public Bar(Long id, String name, String others,
               UUID uuid, YearMonth billingMonth, Map<String, String> attributes,
        Map<String,Integer> intAttributes) {
        this.id = id;
        this.name = name;
        this.others = others;
        this.uuid = uuid;
        this.billingMonth = billingMonth;
        this.attributes = attributes;
        this.intAttributes = intAttributes;
    }

    public static Bar of(String name, String others){
        return of(name, others,  UUID.randomUUID(), null, null, null);
    }

    public static Bar of(String name, String others, UUID uuid, YearMonth billingMonth
        , Map<String, String> attributes, Map<String, Integer> intAttributes){
        return new Bar(null, name, others, uuid, billingMonth, attributes, intAttributes);
    }

    public enum Field {
        id("ID", true),
        name("NAME"),
        others("OTHERS"),
        uuid("UUID"),
        billingMonth("BILLING_MONTH"),
        attributes("ATTRIBUTES"),
        intAttributes("INT_ATTRIBUTES"),
        ;

        private final String dbColumn;
        private final boolean isId;

        Field(String dbColumn) {
            this.dbColumn = dbColumn;
            this.isId = false;
        }

        Field(String dbColumn, boolean isId) {
            this.dbColumn = dbColumn;
            this.isId = isId;
        }

        public String getDbColumn() {
            return dbColumn;
        }

        public boolean isId() {
            return isId;
        }
    }

    public static final Map<String, String> FIELD_COLUMN_MAP
        = Collections.unmodifiableMap(Stream.of(Field.values())
        .collect(Collectors.toMap(Field::name, Field::getDbColumn)));

    public static final String ID_FIELD = Foo.Field.id.name();



    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public String getOthers() {
        return others;
    }

    public Long getId() {
        return id;
    }

    public UUID getUuid() {
        return uuid;
    }

    public YearMonth getBillingMonth() {
        return billingMonth;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    public Map<String, Integer> getIntAttributes() {
        return intAttributes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Bar bar = (Bar) o;
        return Objects.equals(id, bar.id) && Objects.equals(name, bar.name) && Objects.equals(others, bar.others);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, others);
    }
}
