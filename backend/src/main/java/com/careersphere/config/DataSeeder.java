package com.careersphere.config;

import com.careersphere.entity.*;
import com.careersphere.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Fills an EMPTY database with demo data: a skill catalog, demo accounts,
 * sample jobs and a batch of students, so the dashboards have real numbers to show.
 * Runs only when there are no users yet. Disable with app.seed.enabled=false.
 *
 * All companies and people here are fictional.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository users;
    private final SkillRepository skills;
    private final StudentProfileRepository studentProfiles;
    private final RecruiterProfileRepository recruiterProfiles;
    private final JobRepository jobs;
    private final ApplicationRepository applications;
    private final ResumeRepository resumes;
    private final CertificateRepository certificates;
    private final com.careersphere.service.FileStore certificateFiles;
    private final PasswordEncoder encoder;
    private final boolean enabled;
    private final Path uploadDir;

    private final Map<String, Skill> skillByName = new HashMap<>();

    public DataSeeder(UserRepository users, SkillRepository skills, StudentProfileRepository studentProfiles,
                      RecruiterProfileRepository recruiterProfiles, JobRepository jobs,
                      ApplicationRepository applications, ResumeRepository resumes, CertificateRepository certificates,
                      UploadsConfig.CertificateFiles certificateFiles, PasswordEncoder encoder,
                      @Value("${app.seed.enabled:true}") boolean enabled,
                      @Value("${app.upload.dir}") String uploadDir) {
        this.users = users;
        this.skills = skills;
        this.studentProfiles = studentProfiles;
        this.recruiterProfiles = recruiterProfiles;
        this.jobs = jobs;
        this.applications = applications;
        this.resumes = resumes;
        this.certificates = certificates;
        this.certificateFiles = certificateFiles.store();
        this.encoder = encoder;
        this.enabled = enabled;
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        if (!enabled || users.count() > 0) return;
        log.info("Empty database - loading CareerSphere demo data");

        seedSkills();

        user("Placement Admin", "admin@careersphere.ai", "admin123", Role.ADMIN);

        User nimbus = recruiter("Priya Nair", "recruiter@careersphere.ai", "Nimbus Technologies",
                "Talent Acquisition Lead", "Hyderabad",
                "Product engineering company building cloud software for logistics and payments.");
        User bright = recruiter("Rahul Verma", "hr@brightpath.dev", "BrightPath Labs",
                "HR Manager", "Bengaluru", "Digital agency and startup studio working on web, data and AI products.");

        // Demo student: profile filled in, no resume and no applications yet,
        // so the live demo can show: upload resume -> apply -> recruiter shortlists.
        User demo = student("Aarav Sharma", "student@careersphere.ai", "CSE", 8.2,
                List.of("Java", "SQL", "Git", "HTML", "CSS", "JavaScript", "Python"), false);

        Job backend   = job(nimbus, "Java Backend Developer", "Hyderabad", "6-8 LPA", "Full-time", "0-1 years", null, 3,
                List.of("Java", "Spring Boot", "SQL", "Git", "REST APIs"),
                "Build and maintain REST services for our logistics platform.");
        Job trainee   = job(nimbus, "Software Engineer Trainee", "Hyderabad", "5-6 LPA", "Full-time", "Freshers", 6.5, 10,
                List.of("Java", "SQL", "Git", "Data Structures"),
                "Six-month structured training followed by placement in a product team.");
        Job payments  = job(nimbus, "Backend Engineer - Payments", "Pune", "8-10 LPA", "Full-time", "0-2 years", 7.0, 2,
                List.of("Java", "Spring Boot", "SQL", "Docker"),
                "Work on the payment gateway integration services.");
        Job analyst   = job(nimbus, "Data Analyst", "Hyderabad", "5-7 LPA", "Full-time", "Freshers", null, 4,
                List.of("Python", "SQL", "Excel", "Power BI"),
                "Turn operations data into dashboards and weekly insights.");
        Job devops    = job(nimbus, "Cloud & DevOps Engineer", "Remote", "7-9 LPA", "Full-time", "0-2 years", null, 2,
                List.of("Linux", "Docker", "Kubernetes", "AWS", "Git"),
                "Own CI/CD pipelines and cloud infrastructure.");
        Job micro     = job(nimbus, "Microservices Developer", "Bengaluru", "9-12 LPA", "Full-time", "1-2 years", 7.5, 2,
                List.of("Java", "Spring Boot", "Docker", "Microservices", "REST APIs"),
                "Design and build microservices for high-traffic APIs.");
        Job fullstack = job(bright, "Full Stack Developer", "Bengaluru", "6-9 LPA", "Full-time", "0-1 years", null, 3,
                List.of("JavaScript", "React", "Java", "Spring Boot", "SQL"),
                "Ship features end to end across React frontends and Java services.");
        Job intern    = job(bright, "Java Developer Intern", "Bengaluru", "25k/month", "Internship", "Freshers", null, 5,
                List.of("Java", "Spring Boot", "Git"),
                "Six-month internship with a pre-placement offer for strong performers.");
        Job frontend  = job(bright, "Frontend Developer", "Remote", "5-8 LPA", "Full-time", "Freshers", null, 2,
                List.of("HTML", "CSS", "JavaScript", "React", "TypeScript"),
                "Build responsive, accessible interfaces for client products.");
        Job ml        = job(bright, "Machine Learning Intern", "Bengaluru", "30k/month", "Internship", "Freshers", 8.5, 2,
                List.of("Python", "Machine Learning", "Pandas", "SQL"),
                "Prototype ML models for recommendation and forecasting.");
        Job qa        = job(bright, "QA Automation Engineer", "Chennai", "4-6 LPA", "Full-time", "Freshers", null, 3,
                List.of("Java", "Selenium", "SQL", "Git"),
                "Automate regression suites for web applications.");
        // Nimbus (the main demo recruiter) owns the demo student's best match, so the live demo
        // can go student applies -> recruiter@careersphere.ai shortlists, without switching recruiters.
        Job web       = job(nimbus, "Web Developer", "Hyderabad", "4-5 LPA", "Full-time", "Freshers", null, 4,
                List.of("HTML", "CSS", "JavaScript", "Git"),
                "Build and maintain marketing sites and landing pages.");

        // Other students, with resumes and applications at different stages.
        Object[][] others = {
                {"Ananya Reddy", "ananya@student.demo", "CSE", 9.1, List.of("Java", "Spring Boot", "SQL", "Git", "REST APIs", "Docker")},
                {"Rohan Gupta", "rohan@student.demo", "IT", 7.4, List.of("JavaScript", "React", "HTML", "CSS", "Git")},
                {"Sneha Iyer", "sneha@student.demo", "CSE", 8.7, List.of("Python", "Machine Learning", "Pandas", "SQL")},
                {"Karthik Rao", "karthik@student.demo", "ECE", 7.9, List.of("Java", "SQL", "Git", "Data Structures")},
                {"Meera Pillai", "meera@student.demo", "CSE", 8.4, List.of("Java", "Spring Boot", "SQL", "Git")},
                {"Vikram Singh", "vikram@student.demo", "IT", 6.9, List.of("HTML", "CSS", "JavaScript", "Git")},
                {"Divya Menon", "divya@student.demo", "CSE", 8.9, List.of("Python", "SQL", "Excel", "Power BI")},
                {"Arjun Patel", "arjun@student.demo", "EEE", 7.2, List.of("Java", "Selenium", "SQL")},
                {"Ishita Das", "ishita@student.demo", "CSE", 8.0, List.of("JavaScript", "React", "TypeScript", "HTML", "CSS")},
                {"Nikhil Joshi", "nikhil@student.demo", "IT", 7.6, List.of("Linux", "Git", "Python", "AWS")},
        };
        List<User> s = new ArrayList<>();
        for (Object[] o : others) {
            @SuppressWarnings("unchecked") List<String> sk = (List<String>) o[4];
            s.add(student((String) o[0], (String) o[1], (String) o[2], (Double) o[3], sk, true));
        }

        // The rest of the final-year batch: profiles only, no resumes or applications yet.
        // They give the placement analytics and the Workshop Planner a realistic batch size.
        Object[][] batch = {
                {"Aditya Kulkarni", "CSE", 7.8, List.of("Java", "SQL", "Git", "HTML")},
                {"Bhavana Rao", "IT", 8.1, List.of("Python", "SQL", "Excel")},
                {"Chirag Mehta", "CSE", 7.1, List.of("Java", "Data Structures", "Git")},
                {"Deepika Nair", "ECE", 8.6, List.of("Python", "Pandas", "Excel")},
                {"Farhan Sheikh", "CSE", 6.8, List.of("HTML", "CSS", "JavaScript")},
                {"Gayathri Iyer", "IT", 9.0, List.of("Java", "SQL", "Data Structures")},
                {"Harsha Vardhan", "CSE", 7.5, List.of("Java", "Spring Boot", "Git")},
                {"Isha Kapoor", "CSE", 8.3, List.of("JavaScript", "HTML", "CSS", "Git")},
                {"Jatin Malhotra", "EEE", 6.6, List.of("Python", "Linux")},
                {"Kavya Menon", "CSE", 8.8, List.of("Python", "SQL", "Pandas")},
                {"Lokesh Reddy", "IT", 7.2, List.of("Java", "SQL", "Git")},
                {"Manasa Gowda", "CSE", 8.0, List.of("Java", "SQL", "REST APIs")},
                {"Naveen Kumar", "ECE", 7.0, List.of("Linux", "Git", "AWS")},
                {"Pooja Shetty", "CSE", 8.4, List.of("HTML", "CSS", "JavaScript", "React")},
                {"Rahul Joshi", "IT", 6.9, List.of("Java", "Git")},
                {"Sahana Bhat", "CSE", 9.2, List.of("Python", "Machine Learning", "SQL")},
                {"Tarun Agarwal", "CSE", 7.7, List.of("Java", "SQL", "Selenium")},
                {"Uma Krishnan", "IT", 8.2, List.of("Excel", "SQL", "Power BI")},
                {"Varun Pillai", "CSE", 7.4, List.of("Java", "SQL", "Data Structures", "Git")},
                {"Yamini Sastry", "ECE", 8.5, List.of("Python", "SQL")},
                {"Zaid Khan", "CSE", 7.9, List.of("JavaScript", "React", "Git")},
                {"Akash Verma", "EEE", 6.7, List.of("Java", "SQL")},
                {"Nandini Hegde", "CSE", 8.7, List.of("Java", "Spring Boot", "SQL")},
                {"Sameer Ali", "IT", 7.3, List.of("HTML", "CSS", "Git")}
        };
        for (Object[] o : batch) {
            String name = (String) o[0];
            String email = name.toLowerCase().replaceAll("[^a-z]+", ".") + "@student.demo";
            @SuppressWarnings("unchecked") List<String> sk = (List<String>) o[3];
            student(name, email, (String) o[1], (Double) o[2], sk, false);
        }

        app(backend, s.get(0), ApplicationStatus.SELECTED, "Offer letter shared by email.");
        app(micro, s.get(0), ApplicationStatus.INTERVIEW_SCHEDULED, "Technical interview on Monday, 11:00 AM.");
        app(frontend, s.get(1), ApplicationStatus.SHORTLISTED, null);
        app(fullstack, s.get(1), ApplicationStatus.REJECTED, "Missing Spring Boot experience for this role.");
        app(ml, s.get(2), ApplicationStatus.SELECTED, "Welcome aboard!");
        app(trainee, s.get(3), ApplicationStatus.UNDER_REVIEW, null);
        app(backend, s.get(3), ApplicationStatus.REJECTED, "Missing Spring Boot and REST API experience.");
        app(intern, s.get(4), ApplicationStatus.SHORTLISTED, null);
        app(backend, s.get(4), ApplicationStatus.APPLIED, null);
        app(web, s.get(5), ApplicationStatus.INTERVIEW_SCHEDULED, "HR round on Wednesday, 3:00 PM.");
        app(analyst, s.get(6), ApplicationStatus.SELECTED, "Joining date: 1 December.");
        app(qa, s.get(7), ApplicationStatus.APPLIED, null);
        app(frontend, s.get(8), ApplicationStatus.UNDER_REVIEW, null);
        app(devops, s.get(9), ApplicationStatus.REJECTED, "Needs hands-on Docker and Kubernetes.");

        // Placement-profile extras: verified skills, and certificates for the placement cell to review.
        verifySkill(s.get(0), "Java", 3);
        verifySkill(s.get(0), "Spring Boot", 2);
        verifySkill(s.get(2), "Python", 2);
        verifySkill(s.get(4), "Java", 2);
        certificate(s.get(0), "Docker Certified Associate", "Docker, Inc. (Mirantis)", "DCA-48213", "Docker", CertificateStatus.VERIFIED, null);
        certificate(s.get(2), "Machine Learning (NPTEL, 12 weeks)", "NPTEL / IIT Madras", "NPTEL24CS81S4523", "Machine Learning", CertificateStatus.PENDING, null);
        certificate(s.get(1), "Meta Front-End Developer", "Coursera", "MFE-9X2K7Q", "React", CertificateStatus.PENDING, null);
        certificate(s.get(6), "Power BI Data Analyst Associate", "Microsoft", null, "Power BI", CertificateStatus.PENDING, null);

        log.info("Demo data ready. Logins: student@careersphere.ai/student123, recruiter@careersphere.ai/recruiter123, admin@careersphere.ai/admin123 (demo user {})", demo.getEmail());
    }

    // ------------------------------------------------------------------ helpers

    private void seedSkills() {
        Map<String, List<String>> catalog = new LinkedHashMap<>();
        catalog.put("Programming", List.of("Java", "Python", "JavaScript", "TypeScript", "C", "C++", "Data Structures"));
        catalog.put("Backend", List.of("Spring Boot", "REST APIs", "Microservices", "Node.js"));
        catalog.put("Frontend", List.of("HTML", "CSS", "React", "Angular"));
        catalog.put("Database", List.of("SQL", "MySQL", "MongoDB"));
        catalog.put("Cloud & DevOps", List.of("Docker", "Kubernetes", "AWS", "Linux"));
        catalog.put("Data & AI", List.of("Machine Learning", "Pandas", "Excel", "Power BI", "NLP"));
        catalog.put("Tools", List.of("Git", "Selenium", "Postman"));
        catalog.put("Soft Skills", List.of("Communication", "Teamwork", "Problem Solving"));
        catalog.forEach((cat, names) -> names.forEach(n -> skillByName.put(n, skills.save(new Skill(n, cat)))));
    }

    private Set<Skill> skillSet(List<String> names) {
        Set<Skill> set = new HashSet<>();
        for (String n : names) set.add(Objects.requireNonNull(skillByName.get(n), "Unknown seed skill " + n));
        return set;
    }

    private User user(String name, String email, String password, Role role) {
        return users.save(new User(name, email, encoder.encode(password), role));
    }

    private User recruiter(String name, String email, String company, String designation, String location, String desc) {
        User u = user(name, email, "recruiter123", Role.RECRUITER);
        RecruiterProfile p = new RecruiterProfile(u);
        p.setCompanyName(company);
        p.setDesignation(designation);
        p.setLocation(location);
        p.setDescription(desc);
        recruiterProfiles.save(p);
        return u;
    }

    private User student(String name, String email, String branch, double cgpa, List<String> skillNames,
                         boolean withResume) throws IOException {
        User u = user(name, email, "student123", Role.STUDENT);
        StudentProfile p = new StudentProfile(u);
        p.setPhone("98480" + String.format("%05d", Math.abs(email.hashCode()) % 100000));
        p.setCollege("CareerSphere Institute of Technology");
        p.setBranch(branch);
        p.setGraduationYear(2027);
        p.setCgpa(cgpa);
        p.setTenthPercentage(88.0 + (Math.abs(email.hashCode()) % 10));
        p.setTwelfthPercentage(84.0 + (Math.abs(email.hashCode() / 7) % 12));
        p.setAbout("Final-year " + branch + " student interested in software engineering.");
        p.setSkills(skillSet(skillNames));
        // Demo levels: a fixed spread of Beginner / Intermediate / Advanced per student and skill.
        // The demo jobs all ask for Beginner, so these levels do not change any demo match score.
        for (Skill sk : p.getSkills()) p.setLevel(sk, 1 + Math.floorMod((email + sk.getName()).hashCode(), 3));
        studentProfiles.save(p);
        if (withResume) {
            seedResume(u, skillNames);
            // These students have already applied, which needs placement consent.
            p.setPlacementConsent(true);
            p.setAbout("Final-year " + branch + " student. Built course projects with " + String.join(", ", skillNames)
                    + " and looking for a role where I can keep learning.");
            p.setGithubUrl("https://github.com/" + email.substring(0, email.indexOf('@')).replace('.', '-'));
            studentProfiles.save(p);
        }
        return u;
    }

    private Job job(User recruiter, String title, String location, String salary, String type, String exp,
                    Double minCgpa, int openings, List<String> skillNames, String description) {
        Job j = new Job(recruiter);
        j.setTitle(title);
        j.setCompanyName(recruiterProfiles.findByUserId(recruiter.getId()).orElseThrow().getCompanyName());
        j.setLocation(location);
        j.setSalary(salary);
        j.setJobType(type);
        j.setExperience(exp);
        j.setMinCgpa(minCgpa);
        j.setOpenings(openings);
        j.setDeadline(LocalDate.now().plusDays(30));
        j.setDescription(description);
        j.setSkills(skillSet(skillNames));
        return jobs.save(j);
    }

    private void verifySkill(User u, String skillName, int level) {
        StudentProfile p = studentProfiles.findByUserId(u.getId()).orElseThrow();
        Skill skill = skillByName.get(skillName);
        p.setLevel(skill, level);
        p.verify(skill, level);
        studentProfiles.save(p);
    }

    private void certificate(User u, String name, String issuer, String credentialId, String skillName,
                             CertificateStatus status, String note) {
        byte[] pdf = simplePdf(List.of("Certificate of Completion", "", u.getFullName(), "", name, "Issued by " + issuer,
                credentialId == null ? "" : "Credential ID " + credentialId, "", "Demo certificate generated by CareerSphere AI seed data."));
        Certificate c = new Certificate(u);
        c.setName(name);
        c.setIssuer(issuer);
        c.setCredentialId(credentialId);
        c.setIssueDate(LocalDate.now().minusMonths(3));
        c.setSkill(skillByName.get(skillName));
        c.setOriginalFileName(name.replaceAll("[^A-Za-z0-9]+", "_") + ".pdf");
        c.setStoredFileName(certificateFiles.write(pdf, "pdf"));
        c.setContentType("application/pdf");
        c.setFileSize(pdf.length);
        if (status != CertificateStatus.PENDING) c.review(status, note);
        certificates.save(c);
        if (status == CertificateStatus.VERIFIED && c.getSkill() != null) {
            StudentProfile p = studentProfiles.findByUserId(u.getId()).orElseThrow();
            Set<Skill> skills = new HashSet<>(p.getSkills());
            skills.add(c.getSkill());
            p.setSkills(skills);
            if (p.levelOf(c.getSkill()) < 2) p.setLevel(c.getSkill(), 2);
            p.verify(c.getSkill(), 2);
            studentProfiles.save(p);
        }
    }

    private void app(Job job, User student, ApplicationStatus status, String note) {
        Application a = new Application(job, student);
        a.setStatus(status);
        a.setRecruiterNote(note);
        applications.save(a);
    }

    /** Writes a small, real one-page PDF so recruiters can open seeded resumes. */
    private void seedResume(User u, List<String> skillNames) throws IOException {
        Files.createDirectories(uploadDir);
        String stored = UUID.randomUUID() + ".pdf";
        byte[] pdf = simplePdf(List.of(u.getFullName(), u.getEmail(), "",
                "Skills: " + String.join(", ", skillNames), "",
                "Demo resume generated by CareerSphere AI seed data."));
        Files.write(uploadDir.resolve(stored), pdf);

        Resume r = new Resume(u);
        String safeName = u.getFullName().replace(' ', '_') + "_Resume.pdf";
        r.setOriginalFileName(safeName);
        r.setStoredFileName(stored);
        r.setContentType("application/pdf");
        r.setFileSize(pdf.length);
        r.setUploadedAt(LocalDateTime.now());
        resumes.save(r);
    }

    private static byte[] simplePdf(List<String> lines) {
        StringBuilder text = new StringBuilder("BT /F1 14 Tf 72 740 Td 18 TL\n");
        for (String line : lines) {
            text.append('(').append(line.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")).append(") Tj T*\n");
        }
        text.append("ET");
        String stream = text.toString();
        String[] objects = {
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
                "<< /Length " + stream.getBytes(StandardCharsets.ISO_8859_1).length + " >>\nstream\n" + stream + "\nendstream"
        };
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<Integer> offsets = new ArrayList<>();
        write(out, "%PDF-1.4\n");
        for (int i = 0; i < objects.length; i++) {
            offsets.add(out.size());
            write(out, (i + 1) + " 0 obj\n" + objects[i] + "\nendobj\n");
        }
        int xref = out.size();
        StringBuilder x = new StringBuilder("xref\n0 " + (objects.length + 1) + "\n0000000000 65535 f \n");
        for (int off : offsets) x.append(String.format("%010d 00000 n \n", off));
        x.append("trailer\n<< /Size ").append(objects.length + 1).append(" /Root 1 0 R >>\nstartxref\n")
                .append(xref).append("\n%%EOF\n");
        write(out, x.toString());
        return out.toByteArray();
    }

    private static void write(ByteArrayOutputStream out, String s) {
        out.writeBytes(s.getBytes(StandardCharsets.ISO_8859_1));
    }
}
