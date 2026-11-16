package com.sms;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class StudentSystemTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) throws Exception {
        System.out.println("Running Student Management System Verification Tests...\n");

        Path tempDir = Files.createTempDirectory("sms-test-suite");
        Path dataFile = tempDir.resolve("students.txt");
        Path tempFile = tempDir.resolve("students.tmp");

        StudentFileHandler handler = new StudentFileHandler(dataFile, tempFile);
        StudentService service = new StudentService(handler);

        Student s1 = new Student("S101", "Alice Smith", 20, "Female", "Computer Science", "alice@example.com", "9876543210", 92.5);
        Student s2 = new Student("S102", "Bob Jones", 21, "Male", "Information Tech", "bob@example.com", "9876543211", 84.0);

        // Test 1: Add student
        test("Add first student", () -> {
            service.addStudent(s1);
            assertEqual(1, service.getStudentCount());
            Student found = service.findById("S101");
            assertEqual("Alice Smith", found.getName());
        });

        // Test 2: Reject duplicate ID
        test("Reject duplicate ID", () -> {
            try {
                service.addStudent(new Student("S101", "Alice Clone", 22, "Female", "IT", "clone@example.com", "9876543212", 75.0));
                throw new AssertionError("Duplicate ID should have thrown ValidationException");
            } catch (ValidationException e) {
                assertTrue(e.getMessage().contains("already exists"));
            }
        });

        // Test 3: Add second student
        test("Add second student", () -> {
            service.addStudent(s2);
            assertEqual(2, service.getStudentCount());
        });

        // Test 4: Search student
        test("Search student by ID (O(1) in-memory)", () -> {
            Student found = service.findById("S102");
            assertEqual("Bob Jones", found.getName());
            assertEqual(84.0, found.getMarks());
        });

        // Test 5: Search missing student
        test("Search non-existent student", () -> {
            try {
                service.findById("S999");
                throw new AssertionError("Non-existent ID should throw StudentNotFoundException");
            } catch (StudentNotFoundException e) {
                assertTrue(e.getMessage().contains("S999"));
            }
        });

        // Test 6: Update student and check persistence
        test("Update student and persist atomically", () -> {
            Student updatedBob = new Student("S102", "Bob Jones", 21, "Male", "Information Tech", "bob@example.com", "9876543211", 89.5);
            service.updateStudent(updatedBob);
            assertEqual(89.5, service.findById("S102").getMarks());

            String fileContent = Files.readString(dataFile);
            assertTrue(fileContent.contains("S102|Bob Jones|21|Male|Information Tech|bob@example.com|9876543211|89.5"));
        });

        // Test 7: Delete student and check persistence
        test("Delete student and synchronize disk", () -> {
            service.deleteStudent("S101");
            assertEqual(1, service.getStudentCount());
            assertFalse(service.exists("S101"));

            String fileContent = Files.readString(dataFile);
            assertFalse(fileContent.contains("S101"));
            assertTrue(fileContent.contains("S102"));
        });

        // Test 8: Reload service from disk
        test("Reload service from disk into memory index", () -> {
            StudentService reloaded = new StudentService(new StudentFileHandler(dataFile, tempFile));
            assertEqual(1, reloaded.getStudentCount());
            Student bob = reloaded.findById("S102");
            assertEqual("Bob Jones", bob.getName());
            assertEqual(89.5, bob.getMarks());
        });

        // Test 9: High-volume performance test (1,000 records)
        test("High-volume record append and indexed lookup (1,000 records)", () -> {
            long startTime = System.currentTimeMillis();
            for (int i = 1000; i < 2000; i++) {
                service.addStudent(new Student(
                        "S" + i,
                        "Student Name Alpha",
                        20 + (i % 5),
                        (i % 2 == 0) ? "Male" : "Female",
                        "Department",
                        "student" + i + "@university.edu",
                        "987650" + String.format("%04d", i % 10000),
                        70.0 + (i % 30)
                ));
            }
            long addDuration = System.currentTimeMillis() - startTime;
            System.out.printf("    [Bench] 1,000 append operations completed in %d ms%n", addDuration);

            // Lookup benchmark
            long lookupStart = System.nanoTime();
            Student sample = service.findById("S1500");
            long lookupDuration = System.nanoTime() - lookupStart;
            assertEqual("Student Name Alpha", sample.getName());
            System.out.printf("    [Bench] O(1) in-memory lookup completed in %d ns%n", lookupDuration);
        });

        System.out.println("\n-------------------------------------------");
        System.out.printf("Test Results: %d passed, %d failed%n", passed, failed);
        System.out.println("-------------------------------------------");

        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void test(String name, TestCase testCase) {
        try {
            testCase.run();
            passed++;
            System.out.println("[PASS] " + name);
        } catch (Throwable t) {
            failed++;
            System.err.println("[FAIL] " + name + " -> " + t.getMessage());
            t.printStackTrace();
        }
    }

    private static void assertTrue(boolean condition) {
        if (!condition) throw new AssertionError("Expected true but was false");
    }

    private static void assertFalse(boolean condition) {
        if (condition) throw new AssertionError("Expected false but was true");
    }

    private static void assertEqual(Object expected, Object actual) {
        if (expected instanceof Double e && actual instanceof Double a) {
            if (Math.abs(e - a) > 0.0001) {
                throw new AssertionError("Expected " + expected + " but was " + actual);
            }
            return;
        }
        if (!String.valueOf(expected).equals(String.valueOf(actual))) {
            throw new AssertionError("Expected " + expected + " but was " + actual);
        }
    }

    @FunctionalInterface
    interface TestCase {
        void run() throws Throwable;
    }
}
