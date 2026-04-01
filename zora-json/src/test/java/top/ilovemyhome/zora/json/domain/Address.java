package top.ilovemyhome.zora.json.domain;

import java.util.Objects;

public final class Address {

    private String city;
    private String street;
    private String number;

    public static Address of(String city, String street, String number) {
        Address address = new Address();
        address.city = city;
        address.street = street;
        address.number = number;
        return address;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public void setStreet(String street) {
        this.street = street;
    }

    public void setNumber(String number) {
        this.number = number;
    }

    public String getCity() {
        return city;
    }

    public String getStreet() {
        return street;
    }

    public String getNumber() {
        return number;
    }

    @Override
    public String toString() {
        return "Address{" +
            "city='" + city + '\'' +
            ", street='" + street + '\'' +
            ", number='" + number + '\'' +
            '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Address address = (Address) o;
        return Objects.equals(city, address.city) && Objects.equals(street, address.street) && Objects.equals(number, address.number);
    }

    @Override
    public int hashCode() {
        return Objects.hash(city, street, number);
    }
}
