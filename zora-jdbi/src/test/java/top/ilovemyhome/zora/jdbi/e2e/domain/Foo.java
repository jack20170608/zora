package top.ilovemyhome.zora.jdbi.e2e.domain;


import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Foo {

    public enum Field {
        id("ID", true),
        firstName("FIRST_NAME"),
        lastName("LAST_NAME"),
        country("COUNTRY"),
        birthDate("BIRTH_DATE"),
        age("AGE"),
        lastModified("LAST_MODIFIED"),
        others("OTHERS");

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

    public static final String ID_FIELD = Field.id.name();


    private Long id;
    private String firstName;
    private String lastName;
    private String country;
    private LocalDate birthDate;
    private int age;
    private LocalDateTime lastModified;
    private String others;

    private Foo(Long id, String firstName, String lastName, String country, LocalDate birthDate, int age, LocalDateTime lastModified, String others) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.country = country;
        this.birthDate = birthDate;
        this.age = age;
        this.lastModified = lastModified;
        this.others = others;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getCountry() {
        return country;
    }
    public LocalDate getBirthDate() {
        return birthDate;
    }

    public int getAge() {
        return age;
    }

    public LocalDateTime getLastModified() {
        return lastModified;
    }

    public String getOthers() {
        return others;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getId() {
        return id;
    }


    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(Foo old) {
        Builder builder = new Builder();
        return builder
            .withId(old.getId())
            .withFirstName(old.getFirstName())
            .withLastName(old.getLastName())
            .withCountry(old.getCountry())
            .withBirthDate(old.getBirthDate())
            .withAge(old.getAge())
            .withLastModified(old.getLastModified())
            .withOthers(old.getOthers());
    }

    public static final class Builder {
        private Long id;
        private String firstName;
        private String lastName;
        private String country;
        private LocalDate birthDate;
        private int age;
        private LocalDateTime lastModified;
        private String others;

        private Builder() {
        }

        public Builder withId(Long id) {
            this.id = id;
            return this;
        }

        public Builder withFirstName(String firstName) {
            this.firstName = firstName;
            return this;
        }

        public Builder withLastName(String lastName) {
            this.lastName = lastName;
            return this;
        }

        public Builder withCountry(String country) {
            this.country = country;
            return this;
        }

        public Builder withBirthDate(LocalDate birthDate) {
            this.birthDate = birthDate;
            return this;
        }

        public Builder withAge(int age) {
            this.age = age;
            return this;
        }

        public Builder withLastModified(LocalDateTime lastModified) {
            this.lastModified = lastModified;
            return this;
        }

        public Builder withOthers(String others) {
            this.others = others;
            return this;
        }

        public Foo build() {
            return new Foo(id, firstName, lastName, country, birthDate, age, lastModified, others);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Foo foo = (Foo) o;
        return age == foo.age && Objects.equals(id, foo.id) && Objects.equals(firstName, foo.firstName) && Objects.equals(lastName, foo.lastName) && Objects.equals(country, foo.country) && Objects.equals(birthDate, foo.birthDate) && Objects.equals(lastModified, foo.lastModified) && Objects.equals(others, foo.others);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, firstName, lastName, country, birthDate, age, lastModified, others);
    }

    @Override
    public String toString() {
        return "Foo{" +
            "id=" + id +
            ", firstName='" + firstName + '\'' +
            ", lastName='" + lastName + '\'' +
            ", country='" + country + '\'' +
            ", birthDate=" + birthDate +
            ", age=" + age +
            ", lastModified=" + lastModified +
            ", others='" + others + '\'' +
            '}';
    }
}
