package com.careersphere.repository;

import com.careersphere.entity.AssessmentAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AssessmentAttemptRepository extends JpaRepository<AssessmentAttempt, Long> {
    List<AssessmentAttempt> findByStudentIdOrderByStartedAtDesc(Long studentId);
}
