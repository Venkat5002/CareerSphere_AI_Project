package com.careersphere.repository;

import com.careersphere.entity.Certificate;
import com.careersphere.entity.CertificateStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CertificateRepository extends JpaRepository<Certificate, Long> {
    List<Certificate> findByStudentIdOrderBySubmittedAtDesc(Long studentId);
    List<Certificate> findByStatusOrderBySubmittedAtAsc(CertificateStatus status);
    List<Certificate> findAllByOrderBySubmittedAtDesc();
    long countByStudentIdAndStatus(Long studentId, CertificateStatus status);
}
