package com.careersphere.controller;

import com.careersphere.dto.DashboardDtos.AdminStatsDto;
import com.careersphere.dto.DashboardDtos.AdminUserDto;
import com.careersphere.dto.ReadinessDtos.CertificateDto;
import com.careersphere.dto.ReadinessDtos.LeaderboardDto;
import com.careersphere.dto.ReadinessDtos.ReviewRequest;
import com.careersphere.entity.CertificateStatus;
import com.careersphere.security.AuthUser;
import com.careersphere.service.CertificateService;
import com.careersphere.service.DashboardService;
import com.careersphere.service.ReadinessService;
import com.careersphere.service.WorkshopService;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final DashboardService dashboardService;
    private final WorkshopService workshopService;
    private final CertificateService certificateService;
    private final ReadinessService readinessService;

    public AdminController(DashboardService dashboardService, WorkshopService workshopService,
                           CertificateService certificateService, ReadinessService readinessService) {
        this.certificateService = certificateService;
        this.readinessService = readinessService;
        this.dashboardService = dashboardService;
        this.workshopService = workshopService;
    }

    /** Workshop Planner: which skill trainings would help the most students. ?rounds=3&goal=REACH|MATCHES */
    @GetMapping("/workshops")
    public WorkshopService.WorkshopPlan workshops(@RequestParam(defaultValue = "3") int rounds,
                                                  @RequestParam(defaultValue = "REACH") WorkshopService.Goal goal) {
        return workshopService.plan(rounds, goal);
    }

    @GetMapping("/stats")
    public AdminStatsDto stats() {
        return dashboardService.admin();
    }

    @GetMapping("/users")
    public List<AdminUserDto> users() {
        return dashboardService.adminUsers();
    }

    /** Certificates to verify. ?status=PENDING (default), VERIFIED, REJECTED or ALL */
    @GetMapping("/certificates")
    public List<CertificateDto> certificates(@RequestParam(defaultValue = "PENDING") String status) {
        return certificateService.forReview("ALL".equalsIgnoreCase(status) ? null : CertificateStatus.valueOf(status.toUpperCase()));
    }

    @PatchMapping("/certificates/{id}")
    public CertificateDto review(@PathVariable Long id, @Valid @RequestBody ReviewRequest req) {
        return certificateService.review(id, req.status(), req.note());
    }

    @GetMapping("/certificates/{id}/file")
    public ResponseEntity<Resource> certificateFile(@PathVariable Long id) {
        return StudentController.certificateResponse(certificateService, certificateService.forAdminFile(id));
    }

    /** Whole batch ranked by readiness points, with full names. */
    @GetMapping("/leaderboard")
    public LeaderboardDto leaderboard(@AuthenticationPrincipal AuthUser me) {
        return readinessService.leaderboard(me.id(), true);
    }
}
