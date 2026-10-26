package com.careersphere.repository;

import com.careersphere.entity.Invitation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InvitationRepository extends JpaRepository<Invitation, Long> {
    List<Invitation> findByStudentIdOrderByInvitedAtDesc(Long studentId);
    List<Invitation> findByJobId(Long jobId);
    boolean existsByJobIdAndStudentId(Long jobId, Long studentId);
}
