package com.careersphere.repository;

import com.careersphere.entity.Job;
import com.careersphere.entity.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JobRepository extends JpaRepository<Job, Long> {
    List<Job> findByStatusOrderByCreatedAtDesc(JobStatus status);
    List<Job> findByRecruiterIdOrderByCreatedAtDesc(Long recruiterId);
    long countByStatus(JobStatus status);
    long countByRecruiterIdAndStatus(Long recruiterId, JobStatus status);
}
