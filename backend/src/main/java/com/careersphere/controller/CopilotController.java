package com.careersphere.controller;

import com.careersphere.ai.CopilotService;
import com.careersphere.dto.CopilotDtos.ChatRequest;
import com.careersphere.dto.CopilotDtos.ChatResponse;
import com.careersphere.dto.CopilotDtos.StatusResponse;
import com.careersphere.security.AuthUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Career Copilot for students. Lives under /api/student, so only STUDENT accounts can use it. */
@RestController
@RequestMapping("/api/student/copilot")
public class CopilotController {

    private final CopilotService copilot;

    public CopilotController(CopilotService copilot) {
        this.copilot = copilot;
    }

    @GetMapping("/status")
    public StatusResponse status() {
        return copilot.status();
    }

    @PostMapping
    public ChatResponse chat(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody ChatRequest req) {
        return copilot.chat(me.id(), req.messages());
    }
}
