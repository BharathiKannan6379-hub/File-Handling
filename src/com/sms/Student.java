package com.sms;

import java.io.Serializable;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Domain entity representing a Student.
 * Holds in-memory student data and serialization logic to/from file records.
 */
public class Student implements Serializable {

    private static final long serialVersionUID = 1L;
    public static final String DELIMITER = "|";
    public static final String DELIMITER_REGEX = Pattern.quote(DELIMITER);

    private String studentId;
    private String name;
    private int age;
    private String gender;
    private String department;
    private String email;
    private String phoneNumber;
    private double marks;

    public Student() {
    }

    public Student(String studentId, String name, int age, String gender,
                   String department, String email, String phoneNumber, double marks) {
        this.studentId = studentId;
        this.name = name;
        this.age = age;
        this.gender = gender;
        this.department = department;
        this.email = email;
        this.phoneNumber = phoneNumber;
        this.marks = marks;
    }

    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public double getMarks() {
        return marks;
    }

    public void setMarks(double marks) {
        this.marks = marks;
    }

    /**
     * Serializes student details into a single delimited string for file storage.
     * Example: S101|John Doe|20|Male|Computer Science|john@example.com|9876543210|88.5
     */
    public String toFileRecord() {
        return String.join(DELIMITER,
                studentId,
                name,
                String.valueOf(age),
                gender,
                department,
                email,
                phoneNumber,
                String.valueOf(marks));
    }

    /**
     * Deserializes a delimited string into a Student instance.
     */
    public static Student fromFileRecord(String record) {
        if (record == null || record.isBlank()) {
            throw new IllegalArgumentException("Student record line cannot be null or empty.");
        }

        String[] fields = record.split(DELIMITER_REGEX, -1);
        if (fields.length != 8) {
            throw new IllegalArgumentException("Invalid student record format (expected 8 fields, got " + fields.length + "): " + record);
        }

        try {
            String studentId = fields[0].trim();
            String name = fields[1].trim();
            int age = Integer.parseInt(fields[2].trim());
            String gender = fields[3].trim();
            String department = fields[4].trim();
            String email = fields[5].trim();
            String phoneNumber = fields[6].trim();
            double marks = Double.parseDouble(fields[7].trim());

            return new Student(studentId, name, age, gender, department, email, phoneNumber, marks);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Numeric field parsing failed for record: " + record, e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Student student = (Student) o;
        return Objects.equals(studentId, student.studentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(studentId);
    }

    @Override
    public String toString() {
        return "Student{" +
                "studentId='" + studentId + '\'' +
                ", name='" + name + '\'' +
                ", age=" + age +
                ", gender='" + gender + '\'' +
                ", department='" + department + '\'' +
                ", email='" + email + '\'' +
                ", phoneNumber='" + phoneNumber + '\'' +
                ", marks=" + marks +
                '}';
    }
}
