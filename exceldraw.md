# StatusRobot diagrams for Excalidraw

This file uses the requested name `exceldraw.md`. The drawing tool is called Excalidraw.

Open its Mermaid import, often labelled **Mermaid to Excalidraw** or found under **Text to diagram**. Copy the contents of one code block, starting with `flowchart`, without the Markdown fence lines. Import the diagrams separately so labels remain readable. The diagrams use basic flowchart shapes and avoid styling that depends on a particular converter version.

Excalidraw provides a [Mermaid converter](https://docs.excalidraw.com/docs/@excalidraw/mermaid-to-excalidraw/api). GitHub can also display these [Mermaid blocks](https://docs.github.com/en/get-started/writing-on-github/working-with-advanced-formatting/creating-diagrams). See [architecture.md](architecture.md) for the explanations and practice questions.

## 1. Whole application

```mermaid
flowchart LR
    User[User in browser] --> Client[React pages and components]
    Client --> Axios[Axios services and request helper]
    Axios --> Security[Spring Security session and CSRF]
    Security --> Controllers[Controllers receive HTTP requests]
    Controllers --> Services[Services check roles and feature rules]
    Services --> Repositories[Repositories access stored data]
    Repositories --> SQL[(MySQL accounts and configuration)]
    Repositories --> Mongo[(MongoDB check history)]
    Scheduler[Monitor scheduler] --> Check[HTTP check service]
    Check --> Targets[Monitored websites and APIs]
    Check --> Repositories
    Check --> Incidents[Automatic incidents]
    Check --> Events[Down or recovery event after SQL commit]
    Events --> Discord[Discord alerts]
    Services --> Help[Optional incident help]
    Help --> OpenAI[OpenAI Responses API]
    Providers[GitHub and Discord OAuth] --> Security
```

## 2. React and API requests

```mermaid
flowchart TD
    Form[User changes a form field] --> State[useState stores the value]
    State --> Render[React updates the changed DOM]
    Submit[User submits form] --> Handler[preventDefault and validate fields]
    Handler --> Service[Call the feature API method]
    Service --> CSRF[Get a fresh CSRF header for a write]
    CSRF --> Axios[Axios sends data and session cookie]
    Axios --> Backend[Backend validates and checks permissions]
    Backend --> Response[Return a JSON DTO]
    Response --> Update[Update state or navigate]
    Update --> Render
    Mount[Page opens or workspace changes] --> Effect[useEffect loads current data]
    Effect --> Service
    Leave[Page closes] --> Cleanup[Stop timers and ignore late results]
```

## 3. Backend layers

```mermaid
flowchart TD
    HTTP[HTTP request] --> Security[Session and CSRF checks]
    Security --> DTO[Validate request DTO]
    DTO --> Controller[Controller calls service]
    Controller --> Role[Service checks workspace role]
    Role --> Rules[Service checks feature rules]
    Rules --> Repository[Repository saves or reads entity]
    Repository --> Database[(Database)]
    Database --> Result[Service creates response DTO]
    Result --> JSON[Controller returns JSON]
    Rules --> Error[ResponseStatusException]
    Error --> Handler[ApiErrors returns status and message]
```

## 4. Accounts and team permissions

```mermaid
flowchart TD
    Login[Email and password] --> Bcrypt[Compare with saved bcrypt hash]
    Bcrypt --> Session[Signed-in server session]
    OAuth[GitHub or Discord OAuth] --> Session
    Session --> Request[Request for a workspace record]
    Request --> Owner{Signed-in user owns workspace?}
    Owner -->|Yes| Allowed[Allow]
    Owner -->|No| Accepted{Accepted membership exists?}
    Accepted -->|No| NotFound[Reject with 404]
    Accepted -->|Yes| Write{Changing data or asking for analysis?}
    Write -->|No| Allowed
    Write -->|Yes| Editor{Role is Editor?}
    Editor -->|Yes| Allowed
    Editor -->|No| Forbidden[Reject with 403]
    Change[Owner changes role or removes membership] --> Accepted
```

Only the owner manages the owner's team, status-page configuration and integrations. Editors can publish incidents and maintenance on existing matching status pages. Viewers can read shared operational records, including private notes, but cannot write.

## 5. Database responsibilities

```mermaid
flowchart TD
    SQL[(MySQL)] --> Accounts[app_users]
    SQL --> Monitors[monitors]
    SQL --> Teams[team_members]
    SQL --> Incidents[incidents and incident_updates]
    SQL --> Maintenance[maintenance_windows and maintenance_monitors]
    SQL --> Pages[status_pages and status_page_monitors]
    SQL --> Integrations[discord_integrations and monitor_integrations]
    Mongo[(MongoDB)] --> Logs[ping_logs]
    Accounts --> Owner[Workspace owner ID]
    Owner --> Monitors
    Owner --> Incidents
    Owner --> Maintenance
    Owner --> Pages
    Owner --> Integrations
    Monitors --> Link[monitorId connects configuration to history]
    Link --> Logs
```

These are logical relationships. Not every line is a SQL foreign key. The SQL transaction does not include MongoDB logs or outgoing HTTP requests.

## 6. Monitoring and incidents

```mermaid
flowchart TD
    Timer[Scheduler polls after a 10-second delay] --> Paused{Monitor paused?}
    Paused -->|Yes| Skip[Skip check]
    Paused -->|No| Due{Monitor interval has elapsed?}
    Due -->|No| Skip
    Due -->|Yes| Maintenance{Active maintenance?}
    Maintenance -->|Yes| Skip
    Maintenance -->|No| Check[Send HTTP check]
    Check --> Mongo[Save real check log in MongoDB]
    Mongo --> SQL[Save latest monitor state in MySQL]
    SQL --> Success{HTTP check successful?}
    Success -->|No| Open[Open or keep one automatic incident]
    Success -->|Yes| Close[Close any open automatic incident]
    Open --> Event[Publish event only on down or recovery transition]
    Close --> Event
    Event --> Commit[Commit MySQL transaction]
    Commit --> Discord[Send selected Discord alerts]
```

## 7. Manual incidents and maintenance

```mermaid
flowchart LR
    Editor[Owner or Editor] --> Manual[Create manual incident with first update]
    Manual --> Updates[Add identified or monitoring updates]
    Updates --> Resolve[Resolved update closes manual incident]
    Editor --> Scheduled[Schedule work for monitors in one workspace]
    Scheduled --> Active[Start time arrives]
    Active --> Skip[Skip automatic checks]
    Active --> Completed[End time arrives]
    Scheduled --> Cancelled[Cancel window]
    Active --> Cancelled
    Completed --> Resume[Resume normal due checks]
    Cancelled --> Resume
```

An automatic incident still needs a successful check to close. Maintenance does not close incidents or unpause manually paused monitors. Overlapping active windows keep checks skipped.

## 8. Public status pages and charts

```mermaid
flowchart TD
    Owner[Owner configures status page] --> Select[Choose monitors from own workspace]
    Select --> Page[Public page by slug]
    Published[Published incidents and maintenance] --> Page
    Page --> Status[Latest service state and maintenance labels]
    Page --> History[Read recorded MongoDB history]
    History --> Chart[Response time for 6 hours, 24 hours or 7 days]
    History --> Bars[Daily uptime for 90 UTC days]
    History --> Totals[Uptime for 1, 7, 30 and 90 days]
    ServerOwner[Server owner pins one default page] --> Home[Homepage shows pinned status page]
    NoPin[No page pinned] --> Landing[Homepage shows landing page]
```

Only published notices appear publicly. Skipped and missing checks do not become successful samples. The public page refreshes every 30 seconds.

## 9. Optional OpenAI help

```mermaid
flowchart TD
    User[Owner or Editor asks for help] --> Access[Check workspace access and consent]
    Access --> Limits[Check account, server and running-request limits]
    Limits --> Facts[Select safe check facts and supplied context]
    Instructions[Resources instructions JSON] --> Client[OpenAiClient]
    Facts --> Client
    Client --> API[Responses API with store false and no tools]
    API --> Answer[OpenAiResponse reads completed text]
    Answer --> Suggestions[Show private suggestions]
    Suggestions --> Draft[User may copy into update draft]
    Draft --> Review[User reviews before saving or publishing]
    API --> Finish[Finally release the running-request count]
```

Scheduled checks do not call OpenAI. The key stays in backend settings. Limits count the account making the request. A failed request counts too, and no automatic retry is made. Instructions changes require a rebuild and restart.

## 10. Build and deployment

```mermaid
flowchart TD
    Git[Git source] --> Frontend[npm ci, lint, API tests and build]
    Frontend --> Dist[client dist]
    Java[Java source] --> Maven[Maven backend tests and packaging]
    Dist --> Maven
    Maven --> JAR[app.jar includes frontend static files]
    JAR --> Docker[Dockerfile packages the compiled JAR]
    Docker --> Hub[Publish image to Docker Hub]
    Hub --> Render[Render pulls image after deploy hook]
    Render --> Browser[Serve frontend and API on one port]
    Render --> SQL[(MySQL)]
    Render --> Mongo[(MongoDB)]
```

Publishing runs on pushes to `main` or `production`, not feature branches. Docker Compose currently starts MongoDB only. See [README.md](README.md) for setup.
