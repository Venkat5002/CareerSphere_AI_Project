package com.careersphere.repository;

import com.careersphere.entity.Application;
import com.careersphere.entity.ApplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApplicationRepository extends JpaRepository<Application, Long> {
    boolean existsByJobIdAndStudentId(Long jobId, Long studentId);
    boolean existsByJobId(Long jobId);
    List<Application> findByStudentIdOrderByAppliedAtDesc(Long studentId);
    List<Application> findByJobIdOrderByAppliedAtDesc(Long jobId);
    List<Application> findByJobRecruiterId(Long recruiterId);
    long countByStatus(ApplicationStatus status);
}
