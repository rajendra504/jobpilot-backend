# JobPilot — Backend API

> Automated job application engine — Spring Boot REST API

JobPilot is a full-stack application that automatically discovers job listings, tailors your resume using AI, and submits applications on your behalf across LinkedIn, Naukri, and company career portals.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.x |
| Security | Spring Security + JWT (stateless) |
| Database | MySQL 8 (Aiven free tier in production) |
| Migrations | Flyway |
| AI | Google Gemini 2.0 Flash API |
| Email | SendGrid |
| Browser Automation | Playwright for Java |
| Deployment | Render (free tier) |

---

## Project Structure

```
src/main/java/com/jobpilot/jobpilot_backend/
├── auth/                   # Module 1 — Register, Login, JWT
├── profile/                # Module 2 — User profile, portal credentials
├── resume/                 # Module 3 — Resume upload, parsing (Apache Tika)
├── preferences/            # Module 4 — Job roles, cities, salary, auto-apply toggle
├── scraper/                # Module 5 — Job discovery (Playwright)
├── ai/                     # Module 6 — Gemini resume tailoring + cover letter
├── application/            # Module 7 — Orchestrates the full apply flow
├── notification/           # Module 8 — SendGrid email alerts
├── common/
│   ├── exception/          # GlobalExceptionHandler, ResourceNotFoundException
│   ├── response/           # ApiResponse wrapper
│   └── security/           # EncryptionService (AES-256-GCM)
└── config/                 # SecurityConfig, CORS
```

---

## Getting Started

### Prerequisites

- Java 21
- Maven 3.9+
- MySQL 8 running locally

### 1. Clone the repo

```bash
git clone https://github.com/YOUR_USERNAME/jobpilot-api.git
cd jobpilot-api
```

### 2. Create local database

```sql
CREATE DATABASE job_pilot;
```

### 3. Set environment variables

In IntelliJ → Run → Edit Configurations → Environment Variables, add:

```
DB_LOCAL_PASSWORD=your_mysql_password
JWT_SECRET=your-base64-encoded-secret-min-32-chars
ENCRYPTION_SECRET=your-long-passphrase-min-32-chars
GEMINI_API_KEY=AIza...
SENDGRID_API_KEY=SG...
```

> **Never hardcode secrets in application.yml — always use env vars.**

### 4. Run

```bash
mvn spring-boot:run
```

Flyway will automatically create all tables on first run.

### 5. Health check

```
GET http://localhost:8080/api/actuator/health
```

---

## API Overview

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/auth/register` | Register new user |
| POST | `/api/auth/login` | Login, returns JWT |
| POST | `/api/users/profile` | Create user profile |
| GET | `/api/users/profile` | Get own profile |
| PUT | `/api/users/profile` | Update profile |
| POST | `/api/users/profile/credentials` | Save portal credentials (encrypted) |
| DELETE | `/api/users/profile/credentials/{portal}` | Remove portal credentials |
| POST | `/api/resumes/upload` | Upload resume (PDF/DOCX) |
| GET | `/api/resumes` | List all resumes |
| GET | `/api/preferences` | Get job preferences |
| PUT | `/api/preferences` | Update preferences |

All endpoints except `/auth/**` and `/actuator/health` require:
```
Authorization: Bearer <jwt_token>
```

---

## Security

- Passwords hashed with BCrypt (cost factor 10)
- Portal credentials encrypted with AES-256-GCM + PBKDF2 key derivation
- JWT tokens expire in 24 hours
- CORS restricted to the Angular frontend origin only
- No secrets committed to version control

---

## Deployment (Render)

1. Push to GitHub main branch
2. Render auto-builds using the JAR
3. Set all environment variables in the Render dashboard
4. Add `SPRING_PROFILES_ACTIVE=prod` in Render env vars
5. Keep-alive: configure cron-job.org to ping `/api/actuator/health` every 10 minutes

---

## Module Build Status

- [x] Module 1 — Auth (Register, Login, JWT, Spring Security)
- [x] Module 2 — User Profile (CRUD, AES-256-GCM portal credentials)
- [ ] Module 3 — Resume Upload
- [ ] Module 4 — Job Preferences
- [ ] Module 5 — Job Scraper (Playwright)
- [ ] Module 6 — AI Engine (Gemini)
- [ ] Module 7 — Application Runner
- [ ] Module 8 — Notifications (SendGrid)

---

## License

Private project — not open source.