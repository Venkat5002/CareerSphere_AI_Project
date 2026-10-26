# CareerSphere AI

Explainable job matching and placement management for campus recruitment.

> **Other tools tell students what they're missing. CareerSphere tells them which skill opens the most doors, and proves it with data.**

Three roles: **Student**, **Recruiter**, **Placement Admin**.
Stack: React + Vite, Spring Boot 3 (Java 17), MySQL, Spring Security + JWT, BCrypt, Google Gemini (Career Copilot).

**What makes it different**
- **Career Map**: the student sits at the center and every open job is a point. Its distance is (100 − match %), so the 70% "within reach" ring shows at a glance which jobs are realistic.
- **Skill levers**: try on a skill and watch jobs move inside the ring (Opportunity Math).
- **Resume skill finder**: reads the uploaded resume and suggests skills the profile is missing, each with the exact line it was found in. The server drops any AI suggestion whose quote isn't really in the resume, and the student confirms before anything is added.
- **Workshop Planner** (placement cell): Opportunity Math for the whole batch. Skills pay off in combination, so it checks every combination of k workshops, picks the best one, and invites each student only to the workshops they need. It also shows what picking one workshop at a time would have given.
- **Skill levels**: students mark each skill Beginner, Intermediate or Advanced; recruiters set a minimum level and mark skills must-have or nice to have. Must-haves weigh twice as much, and a skill below the level asked gets half credit. With the defaults the score is exactly the old "share of required skills", so nothing changes until someone uses levels.
- **Placement profile**: the same guided flow as a university placement portal. Upload a résumé, give placement consent (needed to apply; recruiters only see students who agreed), take a skill assessment, record a one-minute introduction video in the browser. A résumé score out of 100 shows every point and how to earn the rest.
- **Skill assessments**: 8 multiple-choice questions in 10 minutes for 15 skills, graded on the server (the browser never receives the answers). 8/8 verifies Advanced, 6-7 Intermediate, 4-5 Beginner; the verified level is used for matching and shown to recruiters.
- **Certifications**: students upload outside certificates (NPTEL, Coursera, AWS...); the placement cell verifies or rejects them with a note. A verified certificate adds its skill as verified.
- **Skill Roadmap** (new): checks every plan of 1, 2 and 3 learning steps (about 1,000 per student) and shows the best of each size, marking skills that only help together. On the demo batch: 1.43 jobs gained with one step, 4.71 with three; it helps all 7 students for whom no single skill helps. It also lists the cheapest path to each nearby job, or says when a CGPA cutoff is the blocker.
- **Hidden Talent** (new): on each job's applicants page, recruiters see consented students who fit the job (or are one step away) but never applied, and can invite them. The invitation appears on the student's Jobs page.
- **Leaderboard**: readiness points (résumé score + verified skills + verified certificates + jobs within reach), rank in the batch and branch.
- **Career Copilot**: an AI assistant that answers from the student's own data through function calling, and can set up actions (apply, show on map) that the student confirms.

---

## Quick start (after the one-time setup below)

There are four ways to run CareerSphere: Terminal, Xcode, VS Code and Android Studio. They all use the same backend (port 8080) and web app (port 5173).

**From Terminal**
```bash
cd careersphere
./run.sh        # starts MySQL, opens the backend and frontend in their own Terminal windows, opens the browser
./stop.sh       # stops the backend and frontend
```

**From Xcode**
1. Double-click `CareerSphere.xcodeproj`.
2. At the top of the Xcode window, set the run destination to **My Mac**.
3. Press **▶ Run** (Cmd + R).

The first build step starts MySQL and opens the backend and frontend in their own Terminal windows, then waits until both answer, so the first run takes a few minutes. The CareerSphere app then opens in a Mac window. If something is missing (Maven, Node, MySQL), the build stops with a red error that says what to install. No Apple developer account is needed: the app is signed to run locally.

To run it on an iPhone simulator instead, pick an iPhone as the run destination (Xcode may ask to download the iOS simulator first). The backend and frontend still run on the Mac.

Stopping the app in Xcode does not stop the servers; close their Terminal windows or run `./stop.sh`.

**From VS Code**
1. In Terminal: `cd careersphere && code .` (opening it from Terminal lets VS Code see your Gemini key). No `code` command? In VS Code press Cmd + Shift + P and run *Shell Command: Install 'code' command in PATH*.
2. Accept **Install recommended extensions** (Java and Spring Boot support).
3. Press **Cmd + Shift + B**. This runs the task *CareerSphere: start everything*: the backend and the web app start in two terminal panels.
4. Open http://localhost:5173.

Other tasks (Terminal > Run Task): *Run backend tests*, *Stop servers*. To debug with breakpoints, use the Run and Debug panel: *Debug backend* (Java) and *Open web app in Chrome* (React).

**From Android Studio** (Android emulator or phone)
1. Start the servers first, with `./run.sh` or the VS Code task. Android Studio only runs the phone app.
2. In Android Studio: **Open** and choose the `android` folder inside the project (not the project folder itself). Wait for "Gradle sync" to finish; the first time it downloads a few hundred MB.
3. **Device Manager > Create Virtual Device**: pick a Pixel phone and the newest system image it offers.
4. Press **▶ Run**. The app opens CareerSphere at `http://10.0.2.2:5173`, which is how the emulator reaches your Mac.

On a real Android phone: turn on *Developer options > USB debugging*, connect it, pick it as the device, and when the app says it can't connect, type your Mac's Wi-Fi address (System Settings > Wi-Fi > Details), for example `http://192.168.1.5:5173`. Phone and Mac must be on the same Wi-Fi. You can also type the deployed address (https://...) once the site is online.

The same trick works for a real iPhone from Xcode, but the iOS app currently opens `localhost`, so use the simulator there.

---

## 1. One-time setup on a Mac

Open **Terminal** and run these one by one.

**Homebrew** (skip if `brew -v` already works):
```bash
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
```
At the end, Homebrew prints two `echo ... >> ~/.zprofile` lines. Copy and run them, then close and reopen Terminal.

**Java 17, Maven, Node.js and MySQL:**
```bash
brew install openjdk@17 maven node mysql
sudo ln -sfn $(brew --prefix)/opt/openjdk@17/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-17.jdk
brew services start mysql
```

**Check everything:**
```bash
java -version    # should say 17
mvn -v
node -v          # 18 or newer
mysql -u root -e "SELECT 1"
```
If the last command asks for a password, your MySQL root user has one. Then start the backend with
`DB_PASSWORD=yourpassword mvn spring-boot:run` instead of the plain command below.

---

## 2. Run the project

You need **two Terminal windows**.

**Window 1 – backend** (first run downloads dependencies, takes a few minutes):
```bash
cd careersphere/backend
mvn spring-boot:run
```
Wait for `Started CareerSphereApplication`. The database `careersphere`, all tables and the demo data are created automatically.
Check: open http://localhost:8080/api/health → `{"status":"UP"}`

**Window 2 – frontend:**
```bash
cd careersphere/frontend
npm install
npm run dev
```
Open **http://localhost:5173**

### Turn on the AI Copilot (optional, free)
1. Open https://aistudio.google.com/apikey, sign in with a Google account, and create an API key.
2. Start the backend with the key:
   ```bash
   GEMINI_API_KEY=your-key-here mvn spring-boot:run
   ```
   The Copilot header then says "Powered by Gemini". Without a key it says "Offline mode" and answers the five common questions with built-in rules, so the demo works either way.
3. Never put the key in the frontend or commit it to GitHub. It only lives in the backend's environment.
4. Note: on Gemini's free tier, Google may use prompts to improve its products. Use demo data, not real student records, on the free tier.

The default model is `gemini-flash-latest`. If Google reports it busy (503), the backend retries twice, then tries `gemini-flash-lite-latest` before falling back to offline mode. To change them: `GEMINI_MODEL=...` and `GEMINI_FALLBACK_MODEL=...`.

**No MySQL yet?** You can try the app with an in-memory database (data is lost on restart):
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=h2
```

### Demo accounts (local only)
| Role | Email | Password |
|---|---|---|
| Student | student@careersphere.ai | student123 |
| Recruiter | recruiter@careersphere.ai | recruiter123 |
| Admin | admin@careersphere.ai | admin123 |

The login page has buttons that fill these in. The demo batch has 35 students: 10 with resumes and applications (`ananya@student.demo` etc., password `student123`) and 24 profile-only students, so the analytics and Workshop Planner have real numbers. All companies and people are fictional.

**Reset demo data:** stop the backend, run `mysql -u root -e "DROP DATABASE careersphere"`, start it again.

---

## 3. Review demo script (about 7 minutes)

1. **Student login** opens the **Career map**: "3 of 12 open jobs are within your reach." Next move: *Upload your resume*.
2. Click the **Spring Boot** lever: four jobs slide inside the saffron ring along dotted trails, and the counter shows 3 to 7.
3. Ask the **Copilot** "Which skill should I learn next?". It answers with the same numbers, and *Show Spring Boot on my map* replays the animation. Point out "Based on Opportunity Math over all open jobs" under the answer: it's grounded in data, not guessed.
4. Ask "Why is my match low for Java Backend Developer?". It lists the exact missing skills.
5. **Profile**: upload `demo/Aarav_Sharma_Resume.pdf` from this project. CareerSphere reads it and finds 6 skills the profile is missing (Spring Boot, REST APIs, MySQL, React, Data Structures, Pandas), each with the line it came from. Untick one to show the student is in control, then add the rest.
6. **See your map**: jobs within reach jump from 3 to 8. The story: the resume already showed Spring Boot in a project, and the profile undersold the student.
7. Ask the Copilot "Which jobs can I apply to now?" and click **Apply to Web Developer**. The Copilot never applies on its own; the student confirms. Applying needs placement consent: if it's off, the Copilot shows a *Give placement consent* button that opens the placement profile, where one switch turns it on.
8. **Applications** shows the placement journey.
9. **Recruiter login** → My jobs → *Web Developer*: applicants ranked by match, resume opens, set *Shortlisted* with a note.
10. **Student again**: Applications shows *Shortlisted* and the note.
10b. **Placement profile** (2 minutes): as Aarav, open *Placement profile*. Upload the résumé, switch on consent and watch the steps advance; take the Java assessment; add a certificate on *Certifications*. As the admin, open *Verify certificates* and verify it; Aarav's *Leaderboard* rank moves up.
10a. **Skill levels** (1 minute): as the recruiter, edit *Web Developer*, click the level on CSS until it says *Min. Advanced*, and mark HTML *Nice to have*. As Aarav, open the job: CSS shows ½ (Intermediate → Advanced) and the score explains the half credit. On **Opportunity Math**, "Level up CSS to Advanced" now appears as a step.
11. **Admin login**: curriculum vs. market gap, then **Plan workshops**. With 3 workshops (Spring Boot, Java, Git), all 35 students get a job within reach (from 26) and matches go from 44 to 138. Point at the line under it: picking one workshop at a time would reach only 32 students and 76 matches, because Java and Spring Boot only pay off together. Then choose 2 workshops and switch the goal: *Reach every student* picks Data Structures + Java (33 students, 65 matches), *Most matches overall* picks Spring Boot + Git (29 students, 87 matches). That tradeoff is the placement cell's call, and the tool makes it visible.

## Tests

```bash
cd backend
mvn test
```

56 tests: 39 unit tests (matching, skill levels, résumé score, skill assessments, Opportunity Math, Skill Roadmap, Hidden Talent, Workshop Planner, resume skill finder) and 17 API tests (TC-01 to TC-17) that start the whole app on an in-memory database and call it through Spring Security, exactly like the browser. API tests never call Gemini. Details and the test-case table are in the project documentation.

## Project documents (`docs/`)

| Folder | Contents |
|---|---|
| `docs/diagrams/` | Architecture, ER, use case, sequence diagrams (login, apply, Copilot, resume skills, roadmap) and the workshop algorithm, as PNG plus editable Mermaid/SVG sources |
| `docs/evaluation/careersphere-evaluation-kit.xlsx` | 60 student-job pairs for 3 people to label blind; formulas compare CareerSphere with keyword matching (accuracy, precision, recall, F1). The online *Fit Review* page does the same with one pair at a time and adds Fleiss' kappa and a McNemar test |
| `deploy/DEPLOY.md` | Putting the project online (Railway: one Docker image + MySQL) |
| `docs/diagrams/09-tech-mindmap.png` | Mind map of the project: features for each role, the algorithms, and every technology (open the `.html` next to it for the clickable version) |
| `docs/report/` | Thesis (Word + PDF), IEEE research paper (Word + PDF), and a 6-page key-points guide for the viva |
| `docs/brand/` | Logo and app icon |

## Put it online

See [`deploy/DEPLOY.md`](deploy/DEPLOY.md). The `Dockerfile` builds the web app into the backend jar, so one service serves everything at one address; add a MySQL database and four settings. With Docker Desktop you can also run `docker compose up --build` and open http://localhost:8080.

## 4. Feature status

| Feature | Status | Type | How it works |
|---|---|---|---|
| Register / login / logout | Complete | Real | JWT (8 h), BCrypt hashes, role in token |
| Role-based access | Complete | Real | Enforced by Spring Security on every `/api/student`, `/api/recruiter`, `/api/admin` call |
| Student profile + skills | Complete | Database | One profile per user, always UPDATE, validation on both sides |
| Resume upload / replace / view / delete | Complete | Real | Multipart field `resume`, PDF/DOC/DOCX, 5 MB, file signature check, UUID file names |
| Jobs (recruiter CRUD, open/close) | Complete | Database | Recruiters can only touch their own jobs |
| Applications + status updates | Complete | Database | Unique (job, student) constraint blocks duplicates; resume required to apply |
| Recruiter views applicant resume | Complete | Real | Only for applications to that recruiter's jobs |
| Explainable job matching | Complete | Rule-based | Must-have skills weigh 2, nice-to-have 1; full credit at or above the level asked, half below it. With defaults: required skills you have ÷ required skills. CGPA is a separate eligibility check |
| **Opportunity Math** | Complete | Rule-based | For each missing skill, or skill below the level asked: learn it or level it up, re-score all open jobs, count jobs that cross 70% |
| What-if simulator | Complete | Rule-based | Same calculation for any combination of skills |
| Next Best Action | Complete | Rule-based | Priority rules on real data: profile → resume → skills → apply → learn |
| Candidate ranking | Complete | Rule-based | Applicants sorted by match score with explanation |
| Admin analytics + skill gap | Complete | Database | Demand % (open jobs) − supply % (students) per skill |
| Career Map + skill levers | Complete | Rule-based | Distance = 100 − match %; levers call the what-if API and jobs animate to their new distance |
| Resume skill finder | Complete | **Real AI** (Gemini) + keyword fallback | PDFBox/POI read the text; Gemini returns skills with quoted evidence; server keeps only quotes found in the resume; student confirms |
| Workshop Planner | Complete | Rule-based (exact search) | Tries every combination of k skills (bit masks, under 0.25 s for k ≤ 6 on the demo batch), keeps the best for the goal, invites each student to the smallest set of workshops they need; falls back to one-at-a-time when there are too many combinations |
| Skill Roadmap | Complete | Rule-based (exact search) | Every bundle of up to 3 actions (one per skill); most jobs within reach, then least effort (levels gained); "works together" when the bundle beats the sum of its steps |
| Hidden Talent + invitations | Complete | Rule-based | Scores each job against every consented student who hasn't applied; within reach or one action away; unique (job, student) invitation |
| Career Copilot | Complete | **Real AI** (Gemini) + rule-based fallback | Gemini function calling over 8 read-only tools on the student's data; actions need the student's click |
| Mock interview, resume scoring | Pending | AI | Left out on purpose: generic, and not part of the story |
| Evaluation vs. keyword baseline | Kit ready | Research | Labelling kit and Fit Review page ready; labels still to be collected |

Nothing in the app is labelled AI unless it really is. The Copilot always shows whether Gemini or offline mode answered. Matching is deliberately transparent and rule-based, so every number can be explained in the viva.

---

### Logo and app icon
| File | Used for |
|---|---|
| `docs/brand/careersphere-logo.png` | Full logo (name + tagline): report cover, slides, posters |
| `docs/brand/careersphere-icon-1024.png` | Symbol only, high resolution |
| `frontend/src/assets/logo-full.png` | Login and register pages |
| `frontend/src/assets/logo-mark.png` | Top bar |
| `frontend/public/favicon.ico`, `favicon-16/32.png` | Browser tab |
| `frontend/public/apple-touch-icon.png` | iPhone/iPad "Add to Home Screen" |
| `frontend/public/icon-192/512.png`, `icon-maskable-512.png`, `site.webmanifest` | Android home screen / installed app |

Small icons use the symbol only (the C, cap and chart), because the name and tagline become unreadable below about 64 px.

Sample files: `demo/Aarav_Sharma_Resume.pdf` is a fictional resume made for the demo.

## 5. Project structure

```
backend/src/main/java/com/careersphere/
  entity/        User, StudentProfile, Skill, Resume, RecruiterProfile, Job, Application (+ enums)
  repository/    Spring Data JPA interfaces
  ai/            GeminiClient, ResumeTextExtractor + ResumeSkillService (resume skill finder), CopilotTools (what the AI may read), CopilotService (function-calling loop + offline mode)
  service/       Business logic: Auth, Profile, Resume, Job, Application, Match (Opportunity Math), Dashboard
  controller/    REST endpoints: Auth, Student, Recruiter, Admin
  security/      JwtService, JwtAuthFilter, AuthUser
  config/        SecurityConfig, WebAppConfig (serves the bundled web app), DataSeeder
  exception/     ApiException, GlobalExceptionHandler (one JSON error format)

app/CareerSphere/   SwiftUI app for Xcode (Mac, iPhone, iPad): a native window around the web app
android/            Kotlin app for Android Studio: the web app in a WebView, with file upload, resume viewer, pull to refresh
.vscode/            VS Code tasks (start, test, stop), debug configurations and recommended extensions
scripts/            start-servers.sh (used by run.sh and Xcode), backend.command, frontend.command
run.sh, stop.sh     start / stop everything from Terminal
Dockerfile, docker-compose.yml, deploy/   one-image build and the hosting guide

frontend/src/
  services/      api.js (Axios + JWT), one file per area - components never call Axios directly
  context/       AuthContext (session restore via /api/auth/me)
  components/    Layout, ProtectedRoute, SkillInput, shared UI
  pages/         student/, recruiter/, admin/, Login, Register
```

## 6. API

| Method | Path | Who |
|---|---|---|
| POST | `/api/auth/register`, `/api/auth/login` | Anyone |
| GET | `/api/auth/me`, `/api/skills` | Logged in |
| GET | `/api/student/dashboard`, `/opportunities`, `/what-if?skills=A,B` | Student |
| GET/PUT | `/api/student/profile` | Student |
| POST/GET/DELETE | `/api/student/resume` (POST = multipart `resume`), GET `/api/student/resume/file` | Student |
| GET | `/api/student/jobs?q=&sort=match\|newest`, `/api/student/jobs/{id}` | Student |
| POST/GET | `/api/student/applications` | Student |
| GET | `/api/recruiter/dashboard`, GET/PUT `/api/recruiter/profile` | Recruiter |
| GET/POST | `/api/recruiter/jobs`; PUT/DELETE `/api/recruiter/jobs/{id}`; PATCH `/api/recruiter/jobs/{id}/status` | Recruiter |
| GET | `/api/recruiter/jobs/{id}/applicants` | Recruiter |
| PATCH | `/api/recruiter/applications/{id}/status`; GET `/api/recruiter/applications/{id}/resume` | Recruiter |
| POST | `/api/student/resume/skills` (suggest, changes nothing); POST `/api/student/profile/skills` (add confirmed) | Student |
| GET | `/api/student/copilot/status`; POST `/api/student/copilot` | Student |
| GET | `/api/admin/stats`, `/api/admin/users` | Admin |
| GET | `/api/admin/workshops?rounds=3&goal=REACH\|MATCHES` | Admin |
| GET | `/api/student/roadmap`, `/api/student/invitations` | Student |
| GET | `/api/recruiter/jobs/{id}/talent`; POST `/api/recruiter/jobs/{id}/invite/{studentId}` (optional body `{ message }`) | Recruiter |

Errors always come back as `{ status, error, message, fields? }`.

---

## 7. Troubleshooting

| Problem | Fix |
|---|---|
| `Access denied for user 'root'` | Your MySQL has a password: `DB_PASSWORD=yourpassword mvn spring-boot:run` |
| `Communications link failure` | MySQL isn't running: `brew services start mysql` |
| `Port 8080 already in use` | `lsof -ti:8080 \| xargs kill` |
| Frontend says "Cannot reach the server" | Backend isn't running, or not on port 8080 |
| `release version 17 not supported` | `java -version` isn't 17. Re-run the `sudo ln` command from setup |
