package top.ilovemyhome.zora.json.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

@JsonDeserialize(builder = People.Builder.class)
public class People {

    private final Long id;
    private final String firstName;
    private final String lastName;

    private final LocalDate birthDate;

    private final LocalDateTime insertDt;

    private final Address address;
    private final Sex sex;


    public Long getId() {
        return id;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public Sex getSex() {
        return sex;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public LocalDateTime getInsertDt() {
        return insertDt;
    }

    public Address getAddress() {
        return address;
    }

    private People(Long id, String firstName, String lastName, Sex sex, LocalDate birthDate, LocalDateTime insertDt, Address address) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.sex = sex;
        this.birthDate = birthDate;
        this.insertDt = insertDt;
        this.address = address;
    }

    @Override
    public String toString() {
        return "People{" +
            "id=" + id +
            ", firstName='" + firstName + '\'' +
            ", lastName='" + lastName + '\'' +
            ", sex='" + sex + '\'' +
            ", birthDate=" + birthDate +
            ", insertDt=" + insertDt +
            ", address=" + address +
            '}';
    }


    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        People people = (People) o;
        return Objects.equals(id, people.id) && Objects.equals(firstName, people.firstName) && Objects.equals(lastName, people.lastName) && Objects.equals(birthDate, people.birthDate) && Objects.equals(insertDt, people.insertDt) && Objects.equals(address, people.address) && sex == people.sex;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, firstName, lastName, birthDate, insertDt, address, sex);
    }

    @JsonPOJOBuilder(withPrefix = "with")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class Builder {
        private Long id;
        private String firstName;
        private String lastName;
        private Sex sex;
        private LocalDate birthDate;
        private LocalDateTime insertDt;
        private Address address;

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

        public Builder withSex(Sex sex){
            this.sex = sex;
            return this;
        }

        public Builder withBirthDate(LocalDate birthDate) {
            this.birthDate = birthDate;
            return this;
        }

        public Builder withInsertDt(LocalDateTime insertDt) {
            this.insertDt = insertDt;
            return this;
        }

        public Builder withAddress(Address address) {
            this.address = address;
            return this;
        }

        public People build() {
            return new People(id, firstName, lastName, sex, birthDate, insertDt, address);
        }
    }
}
