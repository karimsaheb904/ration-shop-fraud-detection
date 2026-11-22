package model;

public class Person {
    public int beneficiaryId;
    public String familyId;
    public String name;
    public int age;
    public boolean governmentEmployee;
    public boolean active;

    public Person(String name, int age, boolean governmentEmployee, boolean active) {
        this.name = name;
        this.age = age;
        this.governmentEmployee = governmentEmployee;
        this.active = active;
    }

    public Person(int beneficiaryId, String familyId, String name, int age, boolean governmentEmployee, boolean active) {
        this.beneficiaryId = beneficiaryId;
        this.familyId = familyId;
        this.name = name;
        this.age = age;
        this.governmentEmployee = governmentEmployee;
        this.active = active;
    }
}

