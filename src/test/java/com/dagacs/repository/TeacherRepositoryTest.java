package com.dagacs.repository;

import com.dagacs.entity.Teacher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
@Rollback
class TeacherRepositoryTest {

    @Autowired
    private TeacherRepository teacherRepository;

    @Test
    void findByEmail_returnsTeacher() {
        String email = "t" + System.nanoTime() + "@dagacs.local";
        teacherRepository.save(Teacher.builder()
                .email(email).password("Temp#123").fullName("Teacher")
                .phone("1234567890").designation("Professor").status("ACTIVE")
                .avatarUrl("url")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());

        Optional<Teacher> found = teacherRepository.findByEmail(email);
        assertTrue(found.isPresent());
        assertEquals(email, found.get().getEmail());
    }

    @Test
    void findByEmail_unknown_returnsEmpty() {
        Optional<Teacher> found = teacherRepository.findByEmail("nobody" + System.nanoTime() + "@dagacs.local");
        assertTrue(found.isEmpty());
    }
}
