package com.careersphere.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Shapes for the Career Copilot chat. */
public final class CopilotDtos {

    private CopilotDtos() {}

    /** One chat turn. role is "user" or "assistant". */
    public record ChatMessage(@NotBlank String role, @NotBlank @Size(max = 2000) String text) {}

    /** The last few turns of the conversation; the newest message is last. */
    public record ChatRequest(@NotEmpty @Size(max = 20) List<ChatMessage> messages) {}

    /**
     * Something the student can do with one click, proposed by the Copilot.
     * The Copilot never acts on its own; the student confirms every action.
     * type: APPLY (jobId, title), OPEN_JOB (jobId, title), SIMULATE (skills), OPEN_PAGE (link, title)
     */
    public record CopilotAction(String type, Long jobId, String title, List<String> skills, String link) {
        public static CopilotAction apply(Long jobId, String title) { return new CopilotAction("APPLY", jobId, title, null, null); }
        public static CopilotAction openJob(Long jobId, String title) { return new CopilotAction("OPEN_JOB", jobId, title, null, null); }
        public static CopilotAction simulate(List<String> skills) { return new CopilotAction("SIMULATE", null, null, skills, null); }
        public static CopilotAction openPage(String link, String title) { return new CopilotAction("OPEN_PAGE", null, title, null, link); }
    }

    /**
     * mode: "gemini" when the AI answered, "offline" for the built-in rule-based answers.
     * dataUsed: which of the student's data the answer was based on (shown under the reply).
     */
    public record ChatResponse(String reply, List<CopilotAction> actions, String mode, List<String> dataUsed, String notice) {}

    public record StatusResponse(String mode, String model) {}
}
