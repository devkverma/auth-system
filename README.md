# 🔐 Auth Service

A secure, session-aware authentication service built with **Spring Boot** and **PostgreSQL**. Implements JWT-based authentication with server-side session tracking, revocation, and concurrent-session limits.

## ✨ Features

- **User Authentication:** Registration, password hashing, and login.
- **JWT Security:** Signed tokens with expiration and session identifiers.
- **Session Management:** View active sessions and revoke individual sessions.
- **Global Logout:** Log out from all devices with one request.
- **Session Limits:** Restrict concurrent active sessions per user.
- **Authentication Logging:** Track rejected requests and session revocations.
- **Development Workflow:** Automated builds and server restarts when source files change.

## 🛠️ Tech Stack

| Technology | Purpose |
|---|---|
| Java | Programming language |
| Spring Boot | Application framework |
| Spring Security | Request authentication and authorization |
| Spring Data JPA | Database access and ORM |
| PostgreSQL | Persistent storage |
| JWT (JJWT) | Token generation and validation |
| Maven | Build and dependency management |

## 🚀 Getting Started

### Prerequisites

- Java version configured by the project
- PostgreSQL
- Maven Wrapper (`mvnw` and `mvnw.cmd`)
- Bash for Linux and macOS
- Windows PowerShell for Windows

### Configuration

Create a `.env` file in the project root:

```dotenv
JWT_SECRET=your-base64-encoded-secret
```

Configure your database connection and JWT settings in `src/main/resources/application.properties`.

Reference the JWT secret using:

```properties
app.jwt.secret=${JWT_SECRET}
app.jwt.expiration-ms=900000
```

Ensure the database exists and PostgreSQL is running before starting the application.

**Never commit `.env` or real credentials to Git.**

### Run the Application

The project includes platform-specific scripts that load environment variables, build the application, start the server, and monitor source files for changes.

#### Linux

```bash
chmod +x run.sh
./run.sh
```

#### macOS

```bash
chmod +x run.sh
./run.sh
```

#### Windows PowerShell

```powershell
.\run.ps1
```

If PowerShell blocks script execution, allow scripts for the current PowerShell process:

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
.\run.ps1
```

#### Windows Command Prompt

```bat
run.bat
```

### Development Workflow

The scripts perform the following operations:

1. Load configuration from `.env` without overriding existing environment variables.
2. Validate that `JWT_SECRET` is configured.
3. Build the project using the Maven Wrapper.
4. Start the Spring Boot application.
5. Monitor source files and `pom.xml` for changes.
6. Rebuild and restart the application when changes are detected.

Press `Ctrl+C` to stop the development runner.

**Note:** The scripts use basic file polling for change detection. They are intended for local development, not production process supervision.

## 🔑 API Endpoints

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/auth/register` | Register a user |
| `POST` | `/auth/login` | Authenticate and create a session |
| `POST` | `/auth/logout` | Revoke the current session |
| `GET` | `/auth/sessions` | List active sessions |
| `DELETE` | `/auth/sessions/{sessionId}` | Revoke a specific session |
| `POST` | `/auth/logout-all` | Revoke all active sessions |

Protected endpoints require the following header:

```http
Authorization: Bearer <access-token>
```

## 🗺️ What's Next?

- [ ] Add automated unit and integration tests.
- [ ] Test concurrent logins and session-limit enforcement.
- [ ] Add device identification and meaningful `lastSeenAt` tracking.
- [ ] Introduce Redis for session-validation caching.
- [ ] Add login rate limiting and brute-force protection.
- [ ] Standardize API error responses.
- [ ] Add Docker Compose for local development.
- [ ] Add CI checks for tests, formatting, and security.

## 🔒 Security Notes

- Passwords are hashed before storage.
- JWTs are signed and expire.
- Revoked sessions cannot authenticate subsequent requests.
- Session ownership is checked before revocation.
- Secrets must be supplied through environment variables.
- Keep `.env` out of version control.
- Replace any previously exposed JWT secret before using the service beyond local development.

---

*Built as a practical exploration of authentication, session lifecycle management, and backend security.*