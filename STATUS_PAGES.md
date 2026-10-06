# Status pages

Open **Status pages** in the dashboard to create a page. Give it a name and a unique address, then choose the monitors to show. You can create several pages, edit them later, or delete a page without deleting its monitors or history.

Each page has its own public link, such as `/#status/website`. These links work without signing in. Only the account that created a page can change it or select its monitors.

## Pin Default

Checking **Pin Default** displays that status page at the homepage instead of the landing page. Pinning another page automatically unpins the previous one. Unpinning or deleting the default restores the landing page. Other pages stay available at their own links.

This changes the homepage for the entire installation, so only the server owner can pin a page. By default this is the first registered account. Set `STATUS_PAGE_OWNER_EMAIL` to an existing account's email to choose a different owner.

The backend locks the owner's database row during page changes, and a unique nullable `default_slot` column guarantees that only one page can be pinned. Page writes require a signed-in session and CSRF protection.

## History

Each selected monitor shows:

- A response-time graph for the last 6 hours, 24 hours or 7 days.
- 90 daily uptime bars. Hover or focus a bar to see its UTC date, uptime and check count.
- Overall uptime for the last 24 hours, 7 days, 30 days and 90 days.

Uptime is successful checks divided by total checks, not a claim about unobserved time. Days without checks are grey and have no percentage. Failed checks count against uptime but do not become zero-millisecond response times. The response graph averages successful checks into small time buckets and leaves gaps where there is no response data. Pages refresh every 30 seconds.

History is aggregated in MongoDB rather than loading all raw checks into the browser. MongoDB 5.0 or newer is required for [`$dateTrunc`](https://www.mongodb.com/docs/manual/reference/operator/aggregation/datetrunc/). For larger histories, create the index declared on `PingLog` in the database used by your installation:

```javascript
db.ping_logs.createIndex({ monitorId: 1, timestamp: 1 }, { name: "monitor_history" })
```

## Code overview

The `statuspage` backend package contains the page entity, repository, service and controller. SQL stores page settings and monitor selections. Existing MongoDB ping logs provide the history through `PingHistoryService` and its repository aggregation.

On the client, `StatusPagesDashboard` handles the page form and list. `HomePage` chooses the pinned page or landing page. `StatusPage` loads a page's selected monitors. `MonitorHistoryPanel` renders uptime, and `ResponseTimeChart` draws a small SVG graph without an extra chart library. All views use the existing shared Tailwind theme.

Public reads: `GET /api/status-pages/default`, `GET /api/status-pages/{slug}`, and `GET /api/monitors/{id}/history?hours=24`.

Authenticated page management: `GET /api/status-pages/mine`, `POST /api/status-pages`, `PUT /api/status-pages/{id}`, `PATCH /api/status-pages/{id}/pin-default`, and `DELETE /api/status-pages/{id}`.
