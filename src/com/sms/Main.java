package com.sms;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

/**
 * Console user interface for the Student Management System.
 * Handles user interactions, display formatting, and input parsing.
 */
public class Main {

    private static final String TABLE_SEPARATOR =
            "+----------+------------------------+-----+--------+--------------------+----------------------------+--------------+-------+";
    private static final String TABLE_FORMAT =
            "| %-8s | %-22s | %3s | %-6s | %-18s | %-26s | %-12s | %5s |%n";

    private final StudentService studentService;
    private final BufferedReader reader;

    public Main(StudentService studentService, BufferedReader reader) {
        this.studentService = studentService;
        this.reader = reader;
    }

    public static void main(String[] args) {
        try {
            StudentService service = new StudentService();
            BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));
            new Main(service, consoleReader).start();
        } catch (Exception e) {
            System.err.println("Fatal startup error: " + e.getMessage());
        }
    }

    public void start() {
        System.out.println("=========================================================================================");
        System.out.println("               WELCOME TO STUDENT MANAGEMENT SYSTEM (FILE-HANDLING ENGINE)              ");
        System.out.println("=========================================================================================");

        boolean running = true;
        while (running) {
            printMenu();
            String choice = readInput("Enter your choice (1-6): ");
            if (choice == null) {
                System.out.println("\nExiting application. Goodbye!");
                break;
            }

            System.out.println();
            try {
                switch (choice.trim()) {
                    case "1" -> handleAddStudent();
                    case "2" -> handleViewAllStudents();
                    case "3" -> handleSearchStudent();
                    case "4" -> handleUpdateStudent();
                    case "5" -> handleDeleteStudent();
                    case "6" -> {
                        System.out.println("Thank you for using Student Management System. Goodbye!");
                        running = false;
                    }
                    default -> System.out.println("Invalid option! Please enter a number between 1 and 6.");
                }
            } catch (ValidationException | StudentNotFoundException | FileStorageException e) {
                System.out.println("[Error] " + e.getMessage());
            } catch (Exception e) {
                System.out.println("[Unexpected Error] " + e.getMessage());
            }

            if (running) {
                System.out.println();
            }
        }
    }

    private void printMenu() {
        System.out.println("----------------- MAIN MENU -----------------");
        System.out.println(" 1. Add Student");
        System.out.println(" 2. View All Students");
        System.out.println(" 3. Search Student by ID");
        System.out.println(" 4. Update Student");
        System.out.println(" 5. Delete Student");
        System.out.println(" 6. Exit");
        System.out.println("---------------------------------------------");
    }

    private void handleAddStudent() {
        System.out.println(">>> 1. ADD STUDENT <<<");

        String studentId = readRequired("Enter Student ID (e.g., S101): ");
        if (studentService.exists(studentId)) {
            throw new ValidationException("Student with ID '" + studentId + "' already exists.");
        }

        String name = readRequired("Enter Name: ");
        int age = studentService.parseAge(readRequired("Enter Age: "));
        String gender = readRequired("Enter Gender (Male/Female/Other): ");
        String department = readRequired("Enter Department: ");
        String email = readRequired("Enter Email: ");
        String phoneNumber = readRequired("Enter Phone Number: ");
        double marks = studentService.parseMarks(readRequired("Enter Marks (0-100): "));

        Student student = new Student(studentId, name, age, gender, department, email, phoneNumber, marks);
        studentService.addStudent(student);

        System.out.println("Success: Student record added permanently. ID: " + student.getStudentId());
    }

    private void handleViewAllStudents() {
        System.out.println(">>> 2. ALL STUDENTS <<<");
        List<Student> students = studentService.getAllStudents();
        if (students.isEmpty()) {
            System.out.println("No student records found.");
            return;
        }

        System.out.println("Total student records: " + students.size());
        printTableHeader();
        for (Student s : students) {
            printStudentRow(s);
        }
        System.out.println(TABLE_SEPARATOR);
    }

    private void handleSearchStudent() {
        System.out.println(">>> 3. SEARCH STUDENT BY ID <<<");
        String studentId = readRequired("Enter Student ID: ");
        Student student = studentService.findById(studentId);

        System.out.println("Record found:");
        printTableHeader();
        printStudentRow(student);
        System.out.println(TABLE_SEPARATOR);
    }

    private void handleUpdateStudent() {
        System.out.println(">>> 4. UPDATE STUDENT <<<");
        String studentId = readRequired("Enter Student ID to update: ");
        Student existing = studentService.findById(studentId);

        System.out.println("Current details for " + studentId + ":");
        printTableHeader();
        printStudentRow(existing);
        System.out.println(TABLE_SEPARATOR);
        System.out.println("(Press Enter directly to retain the current value)");

        String name = readOptional("Enter Name", existing.getName());
        String ageStr = readOptional("Enter Age", String.valueOf(existing.getAge()));
        int age = ageStr.equals(String.valueOf(existing.getAge())) ? existing.getAge() : studentService.parseAge(ageStr);

        String gender = readOptional("Enter Gender", existing.getGender());
        String department = readOptional("Enter Department", existing.getDepartment());
        String email = readOptional("Enter Email", existing.getEmail());
        String phoneNumber = readOptional("Enter Phone", existing.getPhoneNumber());

        String marksStr = readOptional("Enter Marks", String.valueOf(existing.getMarks()));
        double marks = marksStr.equals(String.valueOf(existing.getMarks())) ? existing.getMarks() : studentService.parseMarks(marksStr);

        Student updated = new Student(existing.getStudentId(), name, age, gender, department, email, phoneNumber, marks);
        studentService.updateStudent(updated);

        System.out.println("Success: Student record updated successfully. ID: " + studentId);
    }

    private void handleDeleteStudent() {
        System.out.println(">>> 5. DELETE STUDENT <<<");
        String studentId = readRequired("Enter Student ID to delete: ");
        Student existing = studentService.findById(studentId);

        System.out.println("Target student found:");
        printTableHeader();
        printStudentRow(existing);
        System.out.println(TABLE_SEPARATOR);

        String confirm = readRequired("Are you sure you want to delete this record? Type 'YES' to confirm: ");
        if (!"YES".equalsIgnoreCase(confirm)) {
            System.out.println("Deletion aborted.");
            return;
        }

        studentService.deleteStudent(studentId);
        System.out.println("Success: Student " + studentId + " deleted successfully.");
    }

    // ---------------------------------------------------------
    // Output Formatting & Console Helpers
    // ---------------------------------------------------------

    private void printTableHeader() {
        System.out.println(TABLE_SEPARATOR);
        System.out.printf(TABLE_FORMAT, "ID", "Name", "Age", "Gender", "Department", "Email", "Phone", "Marks");
        System.out.println(TABLE_SEPARATOR);
    }

    private void printStudentRow(Student s) {
        System.out.printf(TABLE_FORMAT,
                truncate(s.getStudentId(), 8),
                truncate(s.getName(), 22),
                String.valueOf(s.getAge()),
                truncate(s.getGender(), 6),
                truncate(s.getDepartment(), 18),
                truncate(s.getEmail(), 26),
                truncate(s.getPhoneNumber(), 12),
                String.format("%.1f", s.getMarks()));
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen - 2) + "..";
    }

    private String readInput(String prompt) {
        System.out.print(prompt);
        try {
            return reader.readLine();
        } catch (IOException e) {
            throw new FileStorageException("Console input read error", e);
        }
    }

    private String readRequired(String prompt) {
        String input = readInput(prompt);
        if (input == null || input.isBlank()) {
            throw new ValidationException("Input cannot be empty.");
        }
        return input.trim();
    }

    private String readOptional(String label, String currentVal) {
        String input = readInput(label + " [" + currentVal + "]: ");
        if (input == null || input.isBlank()) {
            return currentVal;
        }
        return input.trim();
    }
}
