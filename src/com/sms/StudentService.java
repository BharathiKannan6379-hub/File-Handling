package com.sms;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Business logic layer managing student records and operations.
 * Maintains an in-memory LinkedHashMap index for O(1) average lookups
 * and coordinates data synchronization and consistency with StudentFileHandler.
 */
public class StudentService {

    private static final Pattern ID_PATTERN = Pattern.compile("^[A-Za-z0-9]{3,20}$");
    private static final Pattern NAME_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z .'-]{1,49}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^[0-9]{10}$");
    private static final Pattern DEPT_PATTERN = Pattern.compile("^[A-Za-z0-9 &.-]{2,50}$");

    private static final int MIN_AGE = 15;
    private static final int MAX_AGE = 100;
    private static final double MIN_MARKS = 0.0;
    private static final double MAX_MARKS = 100.0;

    private final StudentFileHandler fileHandler;
    private final Map<String, Student> studentsById;

    public StudentService() {
        this(new StudentFileHandler());
    }

    public StudentService(StudentFileHandler fileHandler) {
        this.fileHandler = Objects.requireNonNull(fileHandler, "fileHandler cannot be null");
        this.studentsById = new LinkedHashMap<>();
        loadIndexFromFile();
    }

    /**
     * Loads student records from disk once during startup to initialize the in-memory index.
     * Time Complexity: O(n) where n is the number of records.
     */
    private void loadIndexFromFile() {
        studentsById.clear();
        List<Student> loaded = fileHandler.loadAll();
        for (Student student : loaded) {
            String id = student.getStudentId();
            if (studentsById.containsKey(id)) {
                System.err.println("Warning: Duplicate student ID '" + id + "' found in file. Keeping first entry.");
                continue;
            }
            studentsById.put(id, student);
        }
    }

    /**
     * Adds a new student record.
     * Validates fields, checks for duplicate ID, appends to file, and updates in-memory index.
     * Time Complexity: O(1) average memory insertion + O(1) disk append.
     */
    public synchronized void addStudent(Student student) {
        Objects.requireNonNull(student, "student cannot be null");
        validateStudent(student);

        String id = student.getStudentId();
        if (studentsById.containsKey(id)) {
            throw new ValidationException("Student ID already exists: " + id);
        }

        // Persist to disk first in append mode
        fileHandler.append(student);

        // Update in-memory index only after disk persistence succeeds
        studentsById.put(id, student);
    }

    /**
     * Retrieves all student records in insertion order.
     * Time Complexity: O(n).
     */
    public synchronized List<Student> getAllStudents() {
        return new ArrayList<>(studentsById.values());
    }

    /**
     * Searches for a student by ID using the in-memory index.
     * Avoids reading the disk entirely.
     * Time Complexity: O(1) average.
     */
    public synchronized Student findById(String studentId) {
        String normalizedId = validateStudentId(studentId);
        Student student = studentsById.get(normalizedId);
        if (student == null) {
            throw new StudentNotFoundException(normalizedId);
        }
        return student;
    }

    /**
     * Checks if a student with the given ID exists.
     * Time Complexity: O(1) average.
     */
    public synchronized boolean exists(String studentId) {
        if (studentId == null || studentId.isBlank()) {
            return false;
        }
        return studentsById.containsKey(studentId.trim());
    }

    /**
     * Updates an existing student's details.
     * Validates changes, writes all records to a temp file, atomically replaces the data file,
     * and synchronizes the in-memory index. Rolls back memory state on disk failure.
     * Time Complexity: O(n) disk rewrite + O(1) index update.
     */
    public synchronized void updateStudent(Student updatedStudent) {
        Objects.requireNonNull(updatedStudent, "updatedStudent cannot be null");
        validateStudent(updatedStudent);

        String id = updatedStudent.getStudentId();
        Student previous = findById(id);

        // Tentatively update memory
        studentsById.put(id, updatedStudent);

        try {
            // Write to temp file and atomically replace data file
            fileHandler.replaceAll(studentsById.values());
        } catch (RuntimeException e) {
            // Rollback memory state on persistence failure
            studentsById.put(id, previous);
            throw e;
        }
    }

    /**
     * Deletes a student by ID.
     * Removes the student from the index, writes remaining records to a temp file,
     * and atomically replaces the data file. Rolls back memory state on disk failure.
     * Time Complexity: O(n) disk rewrite + O(1) index removal.
     */
    public synchronized void deleteStudent(String studentId) {
        Student previous = findById(studentId);
        String id = previous.getStudentId();

        // Tentatively remove from memory
        studentsById.remove(id);

        try {
            // Rewrite data file via atomic temp file swap
            fileHandler.replaceAll(studentsById.values());
        } catch (RuntimeException e) {
            // Rollback memory state on persistence failure
            studentsById.put(id, previous);
            throw e;
        }
    }

    /**
     * Returns total count of students currently managed.
     * Time Complexity: O(1).
     */
    public synchronized int getStudentCount() {
        return studentsById.size();
    }

    // ---------------------------------------------------------
    // Input Validation Helpers
    // ---------------------------------------------------------

    public void validateStudent(Student student) {
        if (student == null) {
            throw new ValidationException("Student details cannot be null.");
        }
        student.setStudentId(validateStudentId(student.getStudentId()));
        student.setName(validateName(student.getName()));
        student.setAge(validateAge(student.getAge()));
        student.setGender(validateGender(student.getGender()));
        student.setDepartment(validateDepartment(student.getDepartment()));
        student.setEmail(validateEmail(student.getEmail()));
        student.setPhoneNumber(validatePhoneNumber(student.getPhoneNumber()));
        student.setMarks(validateMarks(student.getMarks()));
        checkDelimiterIntegrity(student);
    }

    public String validateStudentId(String studentId) {
        String val = requireNotBlank("Student ID", studentId);
        if (!ID_PATTERN.matcher(val).matches()) {
            throw new ValidationException("Student ID must be 3-20 alphanumeric characters (e.g., S101, CS202).");
        }
        return val;
    }

    public String validateName(String name) {
        String val = requireNotBlank("Name", name);
        if (!NAME_PATTERN.matcher(val).matches()) {
            throw new ValidationException("Name must be 2-50 characters containing letters, spaces, dot, or apostrophe.");
        }
        return val;
    }

    public int validateAge(int age) {
        if (age < MIN_AGE || age > MAX_AGE) {
            throw new ValidationException("Age must be between " + MIN_AGE + " and " + MAX_AGE + ".");
        }
        return age;
    }

    public String validateGender(String gender) {
        String val = requireNotBlank("Gender", gender);
        if (val.equalsIgnoreCase("male") || val.equalsIgnoreCase("m")) {
            return "Male";
        }
        if (val.equalsIgnoreCase("female") || val.equalsIgnoreCase("f")) {
            return "Female";
        }
        if (val.equalsIgnoreCase("other") || val.equalsIgnoreCase("o")) {
            return "Other";
        }
        throw new ValidationException("Gender must be 'Male', 'Female', or 'Other'.");
    }

    public String validateDepartment(String department) {
        String val = requireNotBlank("Department", department);
        if (!DEPT_PATTERN.matcher(val).matches()) {
            throw new ValidationException("Department must be 2-50 characters (e.g., Computer Science, IT).");
        }
        return val;
    }

    public String validateEmail(String email) {
        String val = requireNotBlank("Email", email);
        if (!EMAIL_PATTERN.matcher(val).matches()) {
            throw new ValidationException("Invalid email format (e.g., student@example.com).");
        }
        return val;
    }

    public String validatePhoneNumber(String phoneNumber) {
        String val = requireNotBlank("Phone number", phoneNumber);
        if (!PHONE_PATTERN.matcher(val).matches()) {
            throw new ValidationException("Phone number must be exactly 10 digits (e.g., 9876543210).");
        }
        return val;
    }

    public double validateMarks(double marks) {
        if (Double.isNaN(marks) || marks < MIN_MARKS || marks > MAX_MARKS) {
            throw new ValidationException("Marks must be between " + MIN_MARKS + " and " + MAX_MARKS + ".");
        }
        return marks;
    }

    public int parseAge(String ageStr) {
        try {
            return Integer.parseInt(requireNotBlank("Age", ageStr));
        } catch (NumberFormatException e) {
            throw new ValidationException("Age must be a valid integer number.");
        }
    }

    public double parseMarks(String marksStr) {
        try {
            return Double.parseDouble(requireNotBlank("Marks", marksStr));
        } catch (NumberFormatException e) {
            throw new ValidationException("Marks must be a valid numeric decimal or integer.");
        }
    }

    private String requireNotBlank(String fieldName, String value) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(fieldName + " cannot be empty.");
        }
        return value.trim();
    }

    private void checkDelimiterIntegrity(Student student) {
        String delim = Student.DELIMITER;
        String[] fields = {
                student.getStudentId(),
                student.getName(),
                student.getGender(),
                student.getDepartment(),
                student.getEmail(),
                student.getPhoneNumber()
        };
        for (String field : fields) {
            if (field != null && field.contains(delim)) {
                throw new ValidationException("Fields cannot contain the delimiter '" + delim + "'.");
            }
        }
    }
}
