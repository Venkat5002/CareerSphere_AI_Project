# Putting CareerSphere AI online

The project builds into **one Docker image**: the React app is bundled inside the Spring Boot jar,
so the website and the API share one address (for example `https://careersphere-production.up.railway.app`).
You need two things online: that image, and a MySQL database.

These steps use **Railway**, because it runs both from one dashboard and builds straight from GitHub.
You need a GitHub account and a Railway account. Nothing here costs money up front.

> **Cost (from Railway's pricing page, October 2026; check it again before you sign up):** new accounts get a
> one-time **$5 trial credit**. Running all the time, this app plus MySQL uses roughly $5–8 of resources a month,
> so the trial lasts about three to four weeks. Deploy shortly before your review, not weeks ahead.
> After that, the **Hobby plan is $5/month** including $5 of usage. Delete the project when you are done.

---

## 1. Put the project on GitHub

1. Create a new **private** repository on github.com, for example `careersphere-ai`.
2. In Terminal, in the project folder:

   ```bash
   cd ~/Downloads/careersphere
   git init
   git add .
   git commit -m "CareerSphere AI"
   git branch -M main
   git remote add origin https://github.com/YOUR-USERNAME/careersphere-ai.git
   git push -u origin main
   ```

   `.gitignore` already leaves out `node_modules`, build folders and uploaded resumes.
   **Never commit your Gemini key.** It lives only in `~/.zshrc` on your Mac and, in step 4, in Railway's settings.

## 2. Create the Railway project with MySQL

1. Sign in at **railway.com** with your GitHub account.
2. **New Project → Deploy from GitHub repo →** pick `careersphere-ai`.
   Railway finds the `Dockerfile` and starts building. The first build takes about 5–8 minutes.
   It will fail to start until the database is added, which is expected.
3. In the same project: **+ Create → Database → MySQL**.

## 3. Connect the app to MySQL

Open the **app service** (not MySQL) → **Variables** → **Raw Editor**, paste this, and save:

```
DB_URL=jdbc:mysql://${{MySQL.MYSQLHOST}}:${{MySQL.MYSQLPORT}}/${{MySQL.MYSQLDATABASE}}?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Kolkata
DB_USERNAME=${{MySQL.MYSQLUSER}}
DB_PASSWORD=${{MySQL.MYSQLPASSWORD}}
JWT_SECRET=replace-with-a-long-random-string-of-at-least-32-characters
```

For `JWT_SECRET`, run `openssl rand -base64 48` in Terminal and paste the result.
The `${{MySQL.…}}` parts are filled in by Railway; type them exactly as shown.
(If your database service has another name than `MySQL`, use that name instead.)

## 4. Optional: turn on the AI Copilot

Still in **Variables**, add:

```
GEMINI_API_KEY=your-key
```

Only paste the key into Railway's Variables screen, nowhere else.
Without it the Copilot still works in offline mode.

> The Gemini free tier may use prompts to improve Google's products. The live site only has
> fictional demo data, so that is fine for a demo. Don't put real students' data on it.

## 5. Keep uploaded resumes

Open the app service → **Settings → Volumes → + New Volume**, mount path **`/data`**.
Without a volume, uploaded résumés, certificates and introduction videos disappear when the app restarts (the database rows stay).
Videos are up to 25 MB each: the trial's 0.5 GB volume holds about 15 of them alongside everything else; the Hobby plan's 5 GB holds about 150.

## 6. Get the web address

App service → **Settings → Networking → Generate Domain**. Open the address it gives you.

The first start creates the tables and the demo data, so the demo logins in README.md work:
`student@careersphere.ai / student123`, `recruiter@careersphere.ai / recruiter123`, `admin@careersphere.ai / admin123`.

Check it is healthy: `https://YOUR-ADDRESS/api/health` should show `{"status":"UP"}`.

## Updating the live site

Push to GitHub and Railway rebuilds automatically:

```bash
git add . && git commit -m "Update" && git push
```

## Before you share the link

- Anyone with the link can log in with the demo accounts, which is what you want for a review.
  If you share it more widely, set `SEED_DEMO_DATA=false` before the first start (on an empty database)
  and create your own accounts instead.
- Keep the repository private, or at least never commit keys or passwords.

---

## Try the Docker image on your Mac first (optional)

If you have Docker Desktop installed:

```bash
cd ~/Downloads/careersphere
docker compose up --build
```

Then open http://localhost:8080. This starts MySQL and the app in containers, separate from the
MySQL you already run with Homebrew. Stop with Ctrl+C. `docker compose down -v` deletes the containers' data.

## If something goes wrong

| What you see | What to do |
|---|---|
| Build log: `npm ERR` or `BUILD FAILURE` | Open **Deployments → View logs**, copy the first red lines and send them to me. |
| App log: `Communications link failure` | Step 3 variables are missing or the database name isn't `MySQL`. |
| App log mentions the JWT key or secret | `JWT_SECRET` must be at least 32 characters. |
| Page loads but login says "Cannot reach the server" | Open `/api/health`. If it doesn't load, the app is still starting; wait a minute. |
| App keeps restarting: `OutOfMemoryError` | You are on the Free plan's 0.5 GB limit. Use the trial or Hobby plan. |
