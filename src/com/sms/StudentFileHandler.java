package com.sms;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Handles low-level disk I/O operations for student records.
 * Uses high-performance buffered streams, atomic file replacements, and safe temp files.
 */
public class StudentFileHandler {

    private static final int BUFFER_SIZE = 32 * 1024; // 32 KB buffer for optimal disk throughput

    private final Path dataFile;
    private final Path tempFile;

    public StudentFileHandler() {
        this(Path.of("data", "students.txt"), Path.of("data", "students.tmp"));
    }

    public StudentFileHandler(Path dataFile, Path tempFile) {
        this.dataFile = Objects.requireNonNull(dataFile, "dataFile cannot be null");
        this.tempFile = Objects.requireNonNull(tempFile, "tempFile cannot be null");
    }

    public Path getDataFile() {
        return dataFile;
    }

    public Path getTempFile() {
        return tempFile;
    }

    /**
     * Ensures the parent directories and data file exist before performing I/O.
     */
    public void ensureStorageReady() {
        try {
            Path parent = dataFile.getParent();
            if (parent != null && Files.notExists(parent)) {
                Files.createDirectories(parent);
            }
            if (Files.notExists(dataFile)) {
                Files.createFile(dataFile);
            }
        } catch (IOException e) {
            throw new FileStorageException("Failed to initialize storage at: " + dataFile, e);
        }
    }

    /**
     * Reads all student records from the file into memory using buffered streams.
     * Invoked once during application startup.
     * Time Complexity: O(n) where n is the number of records.
     */
    public List<Student> loadAll() {
        ensureStorageReady();
        List<Student> students = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        new BufferedInputStream(Files.newInputStream(dataFile), BUFFER_SIZE),
                        StandardCharsets.UTF_8),
                BUFFER_SIZE)) {

            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue; // Skip blank lines
                }
                try {
                    students.add(Student.fromFileRecord(line));
                } catch (IllegalArgumentException e) {
                    System.err.println("Warning: Skipping corrupt record at line " + lineNumber + ": " + e.getMessage());
                }
            }
            return students;
        } catch (IOException e) {
            throw new FileStorageException("Failed to read student data from: " + dataFile, e);
        }
    }

    /**
     * Appends a single student record to the end of the file.
     * Does not read or rewrite existing records.
     * Time Complexity: O(1) disk write.
     */
    public void append(Student student) {
        ensureStorageReady();
        Objects.requireNonNull(student, "student cannot be null");

        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(
                        new BufferedOutputStream(
                                Files.newOutputStream(dataFile, StandardOpenOption.CREATE, StandardOpenOption.APPEND),
                                BUFFER_SIZE),
                        StandardCharsets.UTF_8),
                BUFFER_SIZE)) {

            writer.write(student.toFileRecord());
            writer.newLine();
        } catch (IOException e) {
            throw new FileStorageException("Failed to append student record to: " + dataFile, e);
        }
    }

    /**
     * Atomically replaces the data file with the updated collection of students.
     * Writes to a temporary file first, then swaps it to ensure data consistency.
     * Time Complexity: O(n) where n is the number of records.
     */
    public void replaceAll(Collection<Student> students) {
        ensureStorageReady();
        Objects.requireNonNull(students, "students collection cannot be null");

        try {
            writeTempFile(students);
            replaceDataFileWithTemp();
        } catch (IOException e) {
            deleteQuietly(tempFile);
            throw new FileStorageException("Failed to replace data file: " + dataFile, e);
        }
    }

    private void writeTempFile(Collection<Student> students) throws IOException {
        Path tempParent = tempFile.getParent();
        if (tempParent != null && Files.notExists(tempParent)) {
            Files.createDirectories(tempParent);
        }

        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(
                        new BufferedOutputStream(
                                Files.newOutputStream(tempFile,
                                        StandardOpenOption.CREATE,
                                        StandardOpenOption.TRUNCATE_EXISTING,
                                        StandardOpenOption.WRITE),
                                BUFFER_SIZE),
                        StandardCharsets.UTF_8),
                BUFFER_SIZE)) {

            for (Student student : students) {
                writer.write(student.toFileRecord());
                writer.newLine();
            }
        }
    }

    private void replaceDataFileWithTemp() throws IOException {
        try {
            // Attempt atomic move first for maximum data consistency
            Files.move(tempFile, dataFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException | UnsupportedOperationException e) {
            // Fallback to standard move if atomic move is unsupported by the OS/filesystem
            Files.move(tempFile, dataFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            // Secondary fallback for platforms with strict file lock semantics
            Files.copy(tempFile, dataFile, StandardCopyOption.REPLACE_EXISTING);
            deleteQuietly(tempFile);
        }
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Best-effort cleanup
        }
    }
}
