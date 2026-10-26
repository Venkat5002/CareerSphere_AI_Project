package com.careersphere.ai;

import com.careersphere.dto.CopilotDtos.*;
import com.careersphere.dto.DashboardDtos.StudentDashboardDto;
import com.careersphere.service.DashboardService;
import com.careersphere.service.MatchService;
import com.careersphere.service.SkillService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Career Copilot.
 *
 * With a Gemini key: the model decides which tools to call, the server runs them on the
 * student's real data, and the model writes the answer from those results (function calling).
 * Without a key, or if Gemini fails: a rule-based "offline mode" answers the most common
 * questions with the same tools, so the demo always works.
 */
@Service
public class CopilotService {

    private static final int MAX_TOOL_ROUNDS = 5;

    private static final String SYSTEM = """
            You are Career Copilot inside CareerSphere, a campus placement platform for final-year engineering students in India.
            Help the student decide what to do next for placements.

            Rules:
            - Use the tools for every fact about the student, jobs, skills or applications. Never invent jobs, companies,
              skills, scores, CGPA values, projects or experience. If a tool does not return it, say you don't know.
            - Match score = share of a job's required skills the student has. A job is "within reach" at 70% or more
              and when the student meets its CGPA cutoff. Opportunity Math counts how many more jobs a skill would bring within reach.
              Explain numbers in these terms when it helps.
            - You cannot apply, edit the profile or upload files. To help the student act, call propose_application
              (shows an Apply button) or show_on_map (shows a map simulation button). The student confirms every action.
            - Never promise placement or salary outcomes.
            - Be brief and concrete: at most about 120 words, short paragraphs or a few bullets, plain English.
              Name specific jobs and skills. End with one clear next step when it fits.
            """;

    private final GeminiClient gemini;
    private final CopilotTools tools;
    private final DashboardService dashboard;
    private final SkillService skills;
    private final ObjectMapper mapper;

    public CopilotService(GeminiClient gemini, CopilotTools tools, DashboardService dashboard,
                          SkillService skills, ObjectMapper mapper) {
        this.gemini = gemini;
        this.tools = tools;
        this.dashboard = dashboard;
        this.skills = skills;
        this.mapper = mapper;
    }

    public StatusResponse status() {
        return gemini.isConfigured() ? new StatusResponse("gemini", gemini.model()) : new StatusResponse("offline", null);
    }

    public ChatResponse chat(Long studentId, List<ChatMessage> history) {
        String question = history.get(history.size() - 1).text();
        if (gemini.isConfigured()) {
            try {
                return askGemini(studentId, history);
            } catch (GeminiClient.AiUnavailableException e) {
                ChatResponse offline = offline(studentId, question);
                return new ChatResponse(offline.reply(), offline.actions(), "offline", offline.dataUsed(),
                        e.getMessage() + " Answered in offline mode instead.");
            }
        }
        return offline(studentId, question);
    }

    // ------------------------------------------------------------------ Gemini function-calling loop

    private ChatResponse askGemini(Long studentId, List<ChatMessage> history) {
        CopilotTools.Session session = new CopilotTools.Session(studentId);
        ArrayNode contents = mapper.createArrayNode();
        for (ChatMessage m : history.subList(Math.max(0, history.size() - 10), history.size())) {
            ObjectNode turn = contents.addObject();
            turn.put("role", "assistant".equals(m.role()) ? "model" : "user");
            turn.putArray("parts").addObject().put("text", m.text());
        }
        ArrayNode declarations = tools.declarations();

        for (int round = 0; round <= MAX_TOOL_ROUNDS; round++) {
            JsonNode content = gemini.generate(SYSTEM, contents, declarations);
            // Keep the model's turn exactly as returned (Gemini 3 needs its thought signatures back).
            contents.add(content);

            List<JsonNode> calls = new ArrayList<>();
            StringBuilder text = new StringBuilder();
            for (JsonNode part : content.path("parts")) {
                if (part.has("functionCall")) calls.add(part.get("functionCall"));
                else if (part.has("text") && !part.path("thought").asBoolean(false)) text.append(part.get("text").asText());
            }
            if (calls.isEmpty()) {
                String reply = text.toString().trim();
                if (reply.isEmpty()) reply = "I couldn't put together an answer. Try asking in a different way.";
                return new ChatResponse(reply, dedupe(session.actions()), "gemini", session.dataUsed(), null);
            }

            ObjectNode results = contents.addObject();
            results.put("role", "user");
            ArrayNode parts = results.putArray("parts");
            for (JsonNode call : calls) {
                String name = call.path("name").asText();
                Map<String, Object> out = tools.run(session, name, call.path("args"));
                ObjectNode fr = parts.addObject().putObject("functionResponse");
                fr.put("name", name);
                if (call.has("id")) fr.put("id", call.get("id").asText());
                fr.set("response", mapper.valueToTree(Map.of("result", out)));
            }
        }
        return new ChatResponse("That question needed too many steps. Try asking something more specific.",
                dedupe(session.actions()), "gemini", session.dataUsed(), null);
    }

    // ------------------------------------------------------------------ offline mode (rule-based)

    /** Recognises the most common questions and answers them from the same tools, without an AI model. */
    ChatResponse offline(Long studentId, String question) {
        CopilotTools.Session s = new CopilotTools.Session(studentId);
        String q = question.toLowerCase(Locale.ROOT);
        List<String> namedSkills = skillsIn(q);
        String reply;

        if ((q.contains("what if") || q.contains("if i learn") || q.contains("simulate")) && !namedSkills.isEmpty()) {
            reply = whatIfReply(s, namedSkills);
        } else if (q.contains("why") || q.contains("explain") || q.contains("low") || q.contains("match for")) {
            Optional<String> r = explainReply(s, q);
            reply = r.orElseGet(() -> learnReply(s));
        } else if (q.contains("learn") || q.contains("skill") || q.contains("focus") || q.contains("improve") || q.contains("next")) {
            reply = !namedSkills.isEmpty() && q.contains("learn") ? whatIfReply(s, namedSkills) : learnReply(s);
        } else if (q.contains("status") || q.contains("application") || q.contains("applied") || q.contains("shortlist")) {
            reply = applicationsReply(s);
        } else if (q.contains("apply") || q.contains("job") || q.contains("eligible") || q.contains("best")) {
            reply = applyReply(s);
        } else if (q.contains("profile") || q.contains("resume") || q.contains("complete")) {
            reply = profileReply(s);
        } else {
            reply = """
                    In offline mode I can answer these from your real data:
                    - Which skill should I learn next?
                    - What if I learn Docker and React?
                    - Why is my match low for Java Backend Developer?
                    - Which jobs can I apply to now?
                    - What's the status of my applications?""";
        }
        return new ChatResponse(reply, dedupe(s.actions()), "offline", s.dataUsed(), null);
    }

    @SuppressWarnings("unchecked")
    private String learnReply(CopilotTools.Session s) {
        List<Map<String, Object>> ranking = (List<Map<String, Object>>) tools.opportunities(s).get("ranking");
        if (ranking.isEmpty()) return "You already have every skill the open jobs ask for. Focus on applying.";
        Map<String, Object> top = ranking.get(0);
        int unlocked = (int) top.get("jobsUnlocked");
        if (unlocked == 0) {
            return "No single skill brings a new job within reach right now. The most requested skill you're missing is "
                    + top.get("skill") + " (asked for by " + top.get("openJobsRequiringIt") + " open jobs).";
        }
        String second = ranking.size() > 1 && (int) ranking.get(1).get("jobsUnlocked") > 0
                ? " Next best is " + ranking.get(1).get("skill") + " (+" + ranking.get(1).get("jobsUnlocked") + ")." : "";
        tools.showOnMap(s, List.of((String) top.get("simulate")));
        String action = !"None".equals(top.get("currentLevel"))
                ? "Level up " + top.get("skill") + " to " + top.get("targetLevel")
                : "Learn " + top.get("skill") + ("Beginner".equals(top.get("targetLevel")) ? "" : " to " + top.get("targetLevel"));
        return action + " first. It would bring " + unlocked + " more open job" + (unlocked == 1 ? "" : "s")
                + " above the " + MatchService.MATCH_THRESHOLD + "% match threshold, including "
                + String.join(" and ", ((List<String>) top.get("exampleJobs")).stream().limit(2).toList()) + "." + second;
    }

    @SuppressWarnings("unchecked")
    private String whatIfReply(CopilotTools.Session s, List<String> names) {
        Map<String, Object> r = tools.simulate(s, names);
        int before = (int) r.get("reachableBefore"), after = (int) r.get("reachableAfter");
        tools.showOnMap(s, names);
        List<Map<String, Object>> newly = (List<Map<String, Object>>) r.get("newlyReachable");
        String list = newly.stream().map(m -> m.get("title") + " (" + m.get("oldScore") + "% to " + m.get("newScore") + "%)")
                .collect(Collectors.joining(", "));
        return "With " + String.join(" + ", names) + ", jobs within reach go from " + before + " to " + after + " of "
                + r.get("openJobs") + "." + (newly.isEmpty() ? " No new job crosses the threshold, so pick a different skill." : " New: " + list + ".");
    }

    @SuppressWarnings("unchecked")
    private Optional<String> explainReply(CopilotTools.Session s, String q) {
        String query = q.replaceAll(".*(match for|low for|for the|for)\\s+", "").replaceAll("[?.!]", "").trim();
        Map<String, Object> job = tools.explainJob(s, query);
        if (job.containsKey("error")) return Optional.empty();
        List<String> missing = (List<String>) job.get("missingSkills");
        String reply = job.get("title") + " at " + job.get("company") + " is a " + job.get("matchScore") + "% match. " + job.get("explanation");
        if (!missing.isEmpty()) {
            // Suggest the missing skill that Opportunity Math ranks highest, not just the first one alphabetically.
            List<Map<String, Object>> ranking = (List<Map<String, Object>>) tools.opportunities(s).get("ranking");
            String best = ranking.stream().map(r -> (String) r.get("skill")).filter(missing::contains).findFirst().orElse(missing.get(0));
            reply += " Learning " + best + " is the quickest way to raise it, and it helps with other jobs too.";
            tools.showOnMap(s, List.of(best));
        }
        return Optional.of(reply);
    }

    @SuppressWarnings("unchecked")
    private String applyReply(CopilotTools.Session s) {
        List<Map<String, Object>> reach = (List<Map<String, Object>>) tools.jobMatches(s, true, 10).get("jobs");
        List<Map<String, Object>> notApplied = reach.stream().filter(j -> "NOT_APPLIED".equals(j.get("applicationStatus"))).toList();
        if (notApplied.isEmpty()) {
            return reach.isEmpty() ? "No open job is within reach yet. Ask me which skill to learn first."
                    : "You've already applied to every job within reach. Ask me which skill would unlock more.";
        }
        Map<String, Object> best = notApplied.get(0);
        tools.proposeApplication(s, ((Number) best.get("id")).longValue());
        String others = notApplied.stream().skip(1).map(j -> j.get("title") + " (" + j.get("matchScore") + "%)").collect(Collectors.joining(", "));
        return "Your best match you haven't applied to is " + best.get("title") + " at " + best.get("company") + " ("
                + best.get("matchScore") + "%)." + (others.isEmpty() ? "" : " Also within reach: " + others + ".");
    }

    @SuppressWarnings("unchecked")
    private String applicationsReply(CopilotTools.Session s) {
        List<Map<String, Object>> apps = (List<Map<String, Object>>) tools.myApplications(s).get("applications");
        if (apps.isEmpty()) return "You haven't applied to any job yet. Ask me which jobs are within reach.";
        return apps.stream().map(a -> "- " + a.get("job") + ": " + a.get("status").toString().replace('_', ' ').toLowerCase()
                + (a.get("recruiterNote") == null ? "" : " (\"" + a.get("recruiterNote") + "\")")).collect(Collectors.joining("\n"));
    }

    private String profileReply(CopilotTools.Session s) {
        Map<String, Object> p = tools.snapshot(s);
        StudentDashboardDto d = dashboard.student(s.studentId);
        if (d.nextBestAction().link() != null && d.nextBestAction().link().startsWith("/student/profile")) {
            s.actions.add(CopilotAction.openPage("/student/profile", "Open profile"));
        }
        return "Your profile is " + p.get("profileCompletionPercent") + "% complete."
                + (((List<?>) p.get("missingProfileItems")).isEmpty() ? "" : " Missing: " + String.join(", ", (List<String>) p.get("missingProfileItems")) + ".")
                + " Next step: " + d.nextBestAction().title() + ".";
    }

    private List<String> skillsIn(String q) {
        return skills.all().stream().map(k -> k.getName())
                .filter(n -> q.matches(".*(^|[^a-z])" + java.util.regex.Pattern.quote(n.toLowerCase(Locale.ROOT)) + "([^a-z]|$).*"))
                .toList();
    }

    private static List<CopilotAction> dedupe(List<CopilotAction> actions) {
        Map<String, CopilotAction> unique = new LinkedHashMap<>();
        for (CopilotAction a : actions) unique.putIfAbsent(a.type() + a.jobId() + a.skills() + a.link(), a);
        return new ArrayList<>(unique.values());
    }
}
