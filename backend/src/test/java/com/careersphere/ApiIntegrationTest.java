package com.careersphere;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end API tests: the real application with an in-memory H2 database and the demo seed data.
 * Every request goes through Spring Security and the JWT filter exactly as the browser's would.
 *
 * Run with:  mvn test
 * Each test registers its own new users, so tests do not depend on each other.
 */
@SpringBootTest(properties = {
        "app.upload.dir=${java.io.tmpdir}/careersphere-test-uploads",
        "app.ai.gemini.api-key="          // force offline mode: tests never call a real AI service
})
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class ApiIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    // ------------------------------------------------------------------ helpers

    private String body(Object o) throws Exception { return json.writeValueAsString(o); }

    private JsonNode read(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }

    private String login(String email, String password) throws Exception {
        return read(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())).get("token").asText();
    }

    private String newStudent() throws Exception {
        String email = "test-" + UUID.randomUUID() + "@test.local";
        return read(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("fullName", "Test Student", "email", email, "password", "secret123", "role", "STUDENT"))))
                .andExpect(status().isCreated())).get("token").asText();
    }

    private static String bearer(String token) { return "Bearer " + token; }

    private void consent(String token) throws Exception {
        mvc.perform(put("/api/student/consent").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("consent", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consent").value(true));
    }

    private byte[] samplePdf() throws Exception {
        return new ClassPathResource("sample-resume.pdf").getInputStream().readAllBytes();
    }

    private ResultActions uploadResume(String token, String name, byte[] bytes) throws Exception {
        return mvc.perform(multipart("/api/student/resume")
                .file(new MockMultipartFile("resume", name, "application/octet-stream", bytes))
                .header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    private long jobId(String token, String title) throws Exception {
        JsonNode jobs = read(mvc.perform(get("/api/student/jobs").header(HttpHeaders.AUTHORIZATION, bearer(token))));
        for (JsonNode j : jobs) if (j.get("title").asText().equals(title)) return j.get("id").asLong();
        throw new AssertionError("Seed job not found: " + title);
    }

    // ------------------------------------------------------------------ authentication

    @Test
    @DisplayName("TC-01 Register returns a token; the same email cannot register twice")
    void registerAndDuplicate() throws Exception {
        String email = "dup-" + UUID.randomUUID() + "@test.local";
        Map<String, String> req = Map.of("fullName", "Dup", "email", email, "password", "secret123", "role", "STUDENT");
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token", not(emptyString())))
                .andExpect(jsonPath("$.user.role").value("STUDENT"));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body(req)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("TC-02 Nobody can register as an admin")
    void cannotRegisterAdmin() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("fullName", "X", "email", "x-" + UUID.randomUUID() + "@t.l", "password", "secret123", "role", "ADMIN"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("TC-03 Wrong password gives 401 with a clear message")
    void wrongPassword() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", "student@careersphere.ai", "password", "wrong"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password."));
    }

    @Test
    @DisplayName("TC-04 Protected APIs need a valid token")
    void needsToken() throws Exception {
        mvc.perform(get("/api/student/profile")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/student/profile").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("TC-05 Each role can only use its own APIs (checked on the server)")
    void roleBasedAccess() throws Exception {
        String student = login("student@careersphere.ai", "student123");
        String recruiter = login("recruiter@careersphere.ai", "recruiter123");
        String admin = login("admin@careersphere.ai", "admin123");
        mvc.perform(get("/api/admin/stats").header(HttpHeaders.AUTHORIZATION, bearer(student))).andExpect(status().isForbidden());
        mvc.perform(get("/api/student/jobs").header(HttpHeaders.AUTHORIZATION, bearer(recruiter))).andExpect(status().isForbidden());
        mvc.perform(get("/api/recruiter/jobs").header(HttpHeaders.AUTHORIZATION, bearer(student))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/stats").header(HttpHeaders.AUTHORIZATION, bearer(admin))).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ profile

    @Test
    @DisplayName("TC-06 Profile saves and reloads; invalid CGPA is rejected per field")
    void profileSaveAndValidation() throws Exception {
        String token = newStudent();
        Map<String, Object> bad = Map.of("fullName", "Test Student", "cgpa", 11.5);
        mvc.perform(put("/api/student/profile").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body(bad)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.cgpa").exists());

        Map<String, Object> good = Map.of("fullName", "Test Student", "branch", "CSE", "cgpa", 8.4,
                "skills", List.of("java", "SQL", "Git"));
        mvc.perform(put("/api/student/profile").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body(good)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/student/profile").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.cgpa").value(8.4))
                .andExpect(jsonPath("$.skills", hasItem("Java")))   // typed "java", stored with the catalog spelling
                .andExpect(jsonPath("$.branch").value("CSE"));
    }

    // ------------------------------------------------------------------ resume

    @Test
    @DisplayName("TC-07 Resume upload accepts a real PDF and rejects other files")
    void resumeUpload() throws Exception {
        String token = newStudent();
        uploadResume(token, "virus.exe", new byte[]{1, 2, 3}).andExpect(status().isBadRequest());
        uploadResume(token, "fake.pdf", "not really a pdf".getBytes())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("does not look like")));
        uploadResume(token, "my resume.pdf", samplePdf())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalFileName").value("my resume.pdf"));
        mvc.perform(get("/api/student/resume/file").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC-08 Resume skill finder suggests skills with evidence, and adds only confirmed ones")
    void resumeSkills() throws Exception {
        String token = newStudent();
        uploadResume(token, "cv.pdf", samplePdf()).andExpect(status().isOk());
        mvc.perform(post("/api/student/resume/skills").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("offline"))
                .andExpect(jsonPath("$.suggestions[?(@.skill == 'Spring Boot')].evidence",
                        hasItem(containsString("Spring Boot"))));
        mvc.perform(get("/api/student/profile").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.skills", not(hasItem("Spring Boot"))));   // nothing added yet
        mvc.perform(post("/api/student/profile/skills").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("skills", List.of("Spring Boot")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skills", hasItem("Spring Boot")));
    }

    // ------------------------------------------------------------------ applications

    @Test
    @DisplayName("TC-09 Applying needs a resume and placement consent, works once, and is blocked the second time")
    void applyRules() throws Exception {
        String token = newStudent();
        long job = jobId(token, "Web Developer");
        Map<String, Long> req = Map.of("jobId", job);
        mvc.perform(post("/api/student/applications").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("resume")));
        uploadResume(token, "cv.pdf", samplePdf()).andExpect(status().isOk());
        mvc.perform(post("/api/student/applications").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("consent")));
        consent(token);
        mvc.perform(post("/api/student/applications").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("APPLIED"));
        mvc.perform(post("/api/student/applications").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body(req)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("TC-10 Only the recruiter who posted a job can update its applications; the student sees the update")
    void recruiterOwnership() throws Exception {
        String student = newStudent();
        uploadResume(student, "cv.pdf", samplePdf()).andExpect(status().isOk());
        consent(student);
        long job = jobId(student, "QA Automation Engineer");   // posted by BrightPath Labs
        long application = read(mvc.perform(post("/api/student/applications").header(HttpHeaders.AUTHORIZATION, bearer(student))
                .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("jobId", job))))
                .andExpect(status().isCreated())).get("id").asLong();

        Map<String, String> update = Map.of("status", "SHORTLISTED", "note", "Great profile");
        String otherRecruiter = login("recruiter@careersphere.ai", "recruiter123");   // Nimbus, not BrightPath
        mvc.perform(patch("/api/recruiter/applications/" + application + "/status").header(HttpHeaders.AUTHORIZATION, bearer(otherRecruiter))
                        .contentType(MediaType.APPLICATION_JSON).content(body(update)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/recruiter/applications/" + application + "/resume").header(HttpHeaders.AUTHORIZATION, bearer(otherRecruiter)))
                .andExpect(status().isForbidden());

        String owner = login("hr@brightpath.dev", "recruiter123");
        mvc.perform(patch("/api/recruiter/applications/" + application + "/status").header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(body(update)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/student/applications").header(HttpHeaders.AUTHORIZATION, bearer(student)))
                .andExpect(jsonPath("$[0].status").value("SHORTLISTED"))
                .andExpect(jsonPath("$[0].recruiterNote").value("Great profile"));
    }

    // ------------------------------------------------------------------ intelligence features

    @Test
    @DisplayName("TC-11 What-if simulation never lowers the number of jobs within reach")
    void whatIf() throws Exception {
        String token = login("student@careersphere.ai", "student123");
        JsonNode r = read(mvc.perform(get("/api/student/what-if").param("skills", "Spring Boot,React")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))).andExpect(status().isOk()));
        if (r.get("reachableAfter").asInt() < r.get("reachableBefore").asInt()) {
            throw new AssertionError("Learning skills reduced the jobs within reach");
        }
    }

    @Test
    @DisplayName("TC-12 Copilot answers from the student's data in offline mode")
    void copilotOffline() throws Exception {
        String token = login("student@careersphere.ai", "student123");
        mvc.perform(post("/api/student/copilot").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("messages", List.of(Map.of("role", "user", "text", "Which skill should I learn next?"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("offline"))
                .andExpect(jsonPath("$.reply", containsString("Learn")))
                .andExpect(jsonPath("$.dataUsed", hasItem(containsString("Opportunity Math"))));
    }

    @Test
    @DisplayName("TC-13 Workshop Planner returns a plan for the admin and is closed to students")
    void workshopPlanner() throws Exception {
        String admin = login("admin@careersphere.ai", "admin123");
        mvc.perform(get("/api/admin/workshops").param("rounds", "3").param("goal", "REACH")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalStudents", greaterThanOrEqualTo(35)))
                .andExpect(jsonPath("$.plan.length()", lessThanOrEqualTo(3)))
                .andExpect(jsonPath("$.plan[0].invited", greaterThan(0)))
                .andExpect(jsonPath("$.method").value("EXACT"));
        String student = login("student@careersphere.ai", "student123");
        mvc.perform(get("/api/admin/workshops").header(HttpHeaders.AUTHORIZATION, bearer(student)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("TC-14 Skill levels and nice-to-have skills are saved and used in the match score")
    void skillLevels() throws Exception {
        String recruiter = login("recruiter@careersphere.ai", "recruiter123");
        // CGPA 10.0 so no seeded student is eligible and the other tests' numbers stay the same.
        Map<String, Object> req = new java.util.HashMap<>();
        req.put("title", "Level Test Role");
        req.put("minCgpa", 10.0);
        req.put("openings", 1);
        req.put("skills", List.of("Java", "Spring Boot", "Docker"));
        req.put("skillLevels", Map.of("Java", 3));
        req.put("niceToHave", List.of("Docker"));
        long job = read(mvc.perform(post("/api/recruiter/jobs").header(HttpHeaders.AUTHORIZATION, bearer(recruiter))
                        .contentType(MediaType.APPLICATION_JSON).content(body(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requirements[?(@.skill == 'Java')].minLevel", hasItem(3)))
                .andExpect(jsonPath("$.requirements[?(@.skill == 'Docker')].mustHave", hasItem(false))))
                .get("id").asLong();

        String student = newStudent();
        Map<String, Object> profile = Map.of("fullName", "Test Student", "cgpa", 8.0,
                "skills", List.of("Java", "Spring Boot"), "skillLevels", Map.of("java", 2));
        mvc.perform(put("/api/student/profile").header(HttpHeaders.AUTHORIZATION, bearer(student))
                        .contentType(MediaType.APPLICATION_JSON).content(body(profile)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skillLevels.Java").value(2))
                .andExpect(jsonPath("$.skillLevels['Spring Boot']").value(1));

        // Java below Advanced: half of 2; Spring Boot: full 2; Docker (nice to have, weight 1) missing.
        // (2 + 4 + 0) half-units out of 10 = 60%.
        mvc.perform(get("/api/student/jobs/" + job).header(HttpHeaders.AUTHORIZATION, bearer(student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.match.score").value(60))
                .andExpect(jsonPath("$.match.levelGaps[0].skill").value("Java"))
                .andExpect(jsonPath("$.match.niceToHave", hasItem("Docker")));

        req.put("niceToHave", List.of("Java", "Spring Boot", "Docker"));
        mvc.perform(put("/api/recruiter/jobs/" + job).header(HttpHeaders.AUTHORIZATION, bearer(recruiter))
                        .contentType(MediaType.APPLICATION_JSON).content(body(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TC-15 Placement profile: consent needs a résumé; the assessment unlocks after consent and is graded on the server")
    void placementSteps() throws Exception {
        String token = newStudent();
        mvc.perform(put("/api/student/consent").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("consent", true))))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/student/readiness").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextStep").value("RESUME"))
                .andExpect(jsonPath("$.breakdown.length()").value(10));
        mvc.perform(get("/api/student/assessments").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.unlocked").value(false));

        uploadResume(token, "cv.pdf", samplePdf()).andExpect(status().isOk());
        consent(token);
        mvc.perform(get("/api/student/readiness").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.nextStep").value("ASSESSMENT"));

        JsonNode attempt = read(mvc.perform(post("/api/student/assessments/start").param("skill", "Java")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))).andExpect(status().isOk()));
        if (attempt.toString().contains("\"correct")) throw new AssertionError("The test must not send the answers");
        if (attempt.get("questions").size() != 8) throw new AssertionError("Expected 8 questions");
        mvc.perform(post("/api/student/assessments/" + attempt.get("attemptId").asLong() + "/submit")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("answers", List.of(0, 0, 0, 0, 0, 0, 0, 0)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(8))
                .andExpect(jsonPath("$.correctByQuestion.length()").value(8));
    }

    @Test
    @DisplayName("TC-16 A certificate goes to the placement cell; verifying it adds the skill as verified")
    void certificateReview() throws Exception {
        String token = newStudent();
        long id = read(mvc.perform(multipart("/api/student/certificates")
                        .file(new MockMultipartFile("file", "aws.pdf", "application/pdf", samplePdf()))
                        .param("name", "AWS Certified Cloud Practitioner").param("issuer", "Amazon Web Services")
                        .param("skill", "AWS")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))).get("id").asLong();

        mvc.perform(multipart("/api/student/certificates")
                        .file(new MockMultipartFile("file", "fake.pdf", "application/pdf", "not a pdf".getBytes()))
                        .param("name", "X").param("issuer", "Y")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest());

        mvc.perform(patch("/api/admin/certificates/" + id).header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("status", "VERIFIED"))))
                .andExpect(status().isForbidden());

        String admin = login("admin@careersphere.ai", "admin123");
        mvc.perform(get("/api/admin/certificates").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + ")].name", hasItem("AWS Certified Cloud Practitioner")));
        mvc.perform(patch("/api/admin/certificates/" + id).header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("status", "VERIFIED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VERIFIED"));
        mvc.perform(get("/api/student/profile").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.skills", hasItem("AWS")))
                .andExpect(jsonPath("$.verifiedLevels.AWS").value(2));
        mvc.perform(get("/api/student/leaderboard").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.you.verifiedCertificates").value(1));
    }

    @Test
    @DisplayName("TC-17 Skill roadmap for a student; Hidden Talent and invitations for a recruiter")
    void roadmapAndTalent() throws Exception {
        String aarav = login("student@careersphere.ai", "student123");
        mvc.perform(get("/api/student/roadmap").header(HttpHeaders.AUTHORIZATION, bearer(aarav)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openJobs", greaterThanOrEqualTo(12)))
                .andExpect(jsonPath("$.bundles[0].newJobs", greaterThan(0)));

        String token = newStudent();
        mvc.perform(put("/api/student/profile").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("fullName", "Talent Test", "cgpa", 8.0, "skills", List.of("HTML", "CSS", "JavaScript")))))
                .andExpect(status().isOk());
        uploadResume(token, "cv.pdf", samplePdf()).andExpect(status().isOk());
        consent(token);
        long studentId = read(mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))).get("id").asLong();

        String recruiter = login("recruiter@careersphere.ai", "recruiter123");
        long job = jobId(token, "Web Developer");
        mvc.perform(get("/api/recruiter/jobs/" + job + "/talent").header(HttpHeaders.AUTHORIZATION, bearer(recruiter)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.withinReach[?(@.studentId == " + studentId + ")].score", hasItem(75)));
        mvc.perform(post("/api/recruiter/jobs/" + job + "/invite/" + studentId).header(HttpHeaders.AUTHORIZATION, bearer(recruiter))
                        .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("message", "Your profile fits this role."))))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/recruiter/jobs/" + job + "/invite/" + studentId).header(HttpHeaders.AUTHORIZATION, bearer(recruiter))
                        .contentType(MediaType.APPLICATION_JSON).content(body(Map.of())))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/student/invitations").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$[0].job").value("Web Developer"))
                .andExpect(jsonPath("$[0].applied").value(false));

        String other = login("hr@brightpath.dev", "recruiter123");
        mvc.perform(get("/api/recruiter/jobs/" + job + "/talent").header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isForbidden());
    }
}
