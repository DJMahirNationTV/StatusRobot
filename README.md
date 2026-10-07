# StatusRobot

StatusRobot is an open-source, self-hosted uptime monitoring application for websites and HTTP APIs. It checks your services at a configured interval, records their response times and lets you share their availability through public status pages.

## Features

- Create, edit, pause and delete monitors from the dashboard.
- View uptime history and response-time charts.
- Create multiple public status pages and choose which monitors appear on each page.
- Pin one status page as the default homepage, replacing the landing page.
- Receive Discord webhook notifications when a monitor changes status.
- Track outages and recoveries in the private Incidents tab.
- Sign in with email and password, GitHub or Discord.

The frontend uses React, TypeScript and Tailwind CSS. The backend uses Java and Spring Boot, with MySQL for accounts and configuration and MongoDB for monitoring history.

Maintenance and team management are sidebar placeholders for now.

## Requirements

- Git
- Java JDK 25, with `JAVA_HOME` configured
- Node.js 22 and npm
- MySQL 8.4, or a compatible database
- MongoDB 7 or newer
- Docker Compose, if you want to use the included MongoDB container

The backend includes a Maven wrapper, so installing Maven separately is optional. The first build needs internet access to download dependencies.

## Local setup

The commands below use PowerShell on Windows. On Linux or macOS, use `export NAME=value` for environment variables and `./mvnw` instead of `.\mvnw.cmd`.

### 1. Clone the repository

```powershell
git clone https://github.com/DJMahirNationTV/StatusRobot.git
cd StatusRobot
```

### 2. Prepare the databases

Create a MySQL database named `statusrobot`:

```sql
CREATE DATABASE statusrobot;
```

Use a database account that can read and write data and create or alter tables in this database. Spring Boot currently creates and updates the application tables on startup through Hibernate.

You can use a local database or an existing remote database. The Compose file does not start MySQL or the application itself.

To start the included MongoDB service:

```powershell
docker compose up -d mongodb
```

The included MongoDB credentials are development defaults only. Change them and restrict network access before using this configuration outside your local machine. Its named volume preserves data across container restarts.

### 3. Configure and start the backend

Set these variables in the terminal where you will run the backend. Replace the MySQL account details with your own:

```powershell
$env:DB_HOST = 'localhost'
$env:DB_PORT = '3306'
$env:DB_NAME = 'statusrobot'
$env:DB_USER = 'your-database-user'
$env:DB_PASSWORD = 'your-database-password'
$env:MONGODB_URI = 'mongodb://admin:admin@localhost:27017/statusrobot_logs?authSource=admin'
$env:FRONTEND_URL = 'http://localhost:5173'
$env:OAUTH_REDIRECT_BASE_URL = 'http://localhost:5173'
$env:SPRING_PROFILES_ACTIVE = 'dev'
```

The MongoDB URI above matches the included Compose credentials. `authSource=admin` is needed because that root account is created in the `admin` database. Monitoring history is stored in `statusrobot_logs`.

The `dev` profile allows session cookies over local HTTP. Do not use this profile for a public deployment.

To enable Discord integrations, generate a Base64-encoded 32-byte encryption key:

```powershell
$integrationKeyBytes = New-Object byte[] 32
$integrationKeyGenerator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$integrationKeyGenerator.GetBytes($integrationKeyBytes)
$integrationKeyGenerator.Dispose()
$env:INTEGRATION_ENCRYPTION_KEY = [Convert]::ToBase64String($integrationKeyBytes)
```

Save this key securely and reuse it when restarting the backend. Changing or losing it makes existing encrypted webhook URLs unreadable. Without a key, Discord integration management is unavailable, but the rest of the application can run.

Start the backend:

```powershell
cd server
.\mvnw.cmd spring-boot:run
```

The API runs at `http://localhost:8080`. Environment variables can also be configured in your IDE. Spring Boot does not automatically load a repository `.env` file with the current configuration.

### 4. Start the frontend

Open a second terminal in the repository root:

```powershell
cd client
npm ci
npm run dev
```

Open `http://localhost:5173`, create an account and sign in. Vite forwards API and OAuth requests to the backend during local development.

### 5. Create monitors and status pages

Use the Monitoring tab to add an HTTP or HTTPS monitor. Add a Discord webhook under Integrations & API, then select it when creating or editing a monitor.

Under Status Pages, create a page and select the monitors you want to publish. Public pages are available at `/#status/your-page-slug`.

Only the server owner can use **Pin Default**. Set `STATUS_PAGE_OWNER_EMAIL` to that account's email before starting the backend. If it is not set, the earliest registered account is treated as the owner. Only one page can be the default at a time; unpinning it restores the landing page.

## Incidents

The Incidents tab lists outages for your own monitors. A failed HTTP check opens an incident. Repeated failures stay in the same incident, and the next successful response closes it. A slow but successful response counts as recovery, even if the monitor is still marked as degraded.

Open an incident to see the first failure, detection time, recovery time and duration. Use the open and resolved filters to browse the history. Lists show 20 incidents per page. Refresh to load the latest checks.

Pausing a monitor does not close its incident. Recovery is confirmed by a successful check after monitoring resumes. Deleting a monitor removes its check history and incidents too.

Incidents are stored in the MySQL `incidents` table. Hibernate creates it on startup with the existing `spring.jpa.hibernate.ddl-auto=update` setting. No new environment variables are required. The database account needs permission to create the table and its indexes.

The monitoring check saves the monitor state and incident in one database transaction. A unique open-monitor ID prevents two open incidents for the same monitor. Incident responses contain a short failure description, not raw exception messages or webhook details.

The API requires a signed-in session:

- `GET /api/incidents?status=all&page=0` lists your incidents. Status can be `all`, `open` or `resolved`, and page numbers start at zero.
- `GET /api/incidents/{id}` returns one of your incidents. Another user's incident returns 404.

History starts with checks after this feature is installed. Existing MongoDB logs are not converted into incidents. Manual updates, public status-page incidents and OpenAI explanations are not included yet.

## Optional OAuth sign-in

Set the following backend environment variables for each provider you want to enable:

| Provider | Variables | Local callback URL |
| --- | --- | --- |
| GitHub | `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET` | `http://localhost:5173/login/oauth2/code/github` |
| Discord | `DISCORD_CLIENT_ID`, `DISCORD_CLIENT_SECRET` | `http://localhost:5173/login/oauth2/code/discord` |

Register the matching callback URL with the provider. A provider is enabled only when both its client ID and secret are configured. Email and password sign-in does not require OAuth credentials.

## Build and checks

From `server`:

```powershell
.\mvnw.cmd test
.\mvnw.cmd clean package
```

From `client`:

```powershell
npm run lint
npm run build
```

The backend build produces `server/target/app.jar`. The frontend build produces `client/dist`.

## Docker and deployment

The root Dockerfile packages an already compiled backend JAR. After building the backend, run these commands from the repository root:

```powershell
docker build -t statusrobot .
```

Supply the backend environment variables when running the image and publish port `8080`. Inside a container, `localhost` refers to that container, not your host or another database container.

The image currently contains only the backend. Deploy `client/dist` separately, or configure a web server to serve it and forward `/api`, `/oauth2` and `/login/oauth2` to the backend. Vite's development proxy is not included in the frontend production build. For a separately hosted API, set `VITE_API_URL` before building the frontend.

For production, use HTTPS, keep secure session cookies enabled and set `FRONTEND_URL` and `OAUTH_REDIRECT_BASE_URL` to your actual frontend URL. Keep database credentials, OAuth secrets and the integration encryption key out of Git. Back up both databases and the encryption key.

The GitHub workflow builds the frontend and backend, publishes the backend image to Docker Hub and triggers a Render deployment on pushes to `main` or `production`. It requires these repository Actions secrets:

- `DOCKER_USERNAME`: Docker Hub username.
- `DOCKER_PASSWORD`: Docker Hub access token with permission to push images.
- `RENDER_DEPLOY_HOOK_URL`: the service's private Render deploy hook URL.
- `SONAR_TOKEN`: token for the configured Sonar project analysis.

## License

MIT License

Copyright (c) 2026 DJMahirNationTV

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
