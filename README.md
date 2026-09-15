# CommuteMate

A carpool and commute-planning web app for the Simon Fraser University community. Find a ride, coordinate with fellow commuters, and check live transit and weather in one place.

[Live demo](https://commutemate-km3h.onrender.com/)

## Features

- **Accounts and profiles** — SFU email registration, driver and rider roles, secure sign-in, and profile updates.
- **Ride discovery** — Search, filter, and sort available rides; view pickup and destination locations on a map.
- **Ride management** — Publish rides, manage seat requests, confirm boarding, and mark trips complete.
- **Chat and notifications** — Persistent ride conversations, unread indicators, and updates throughout each trip.
- **Commute dashboard** — Live TransLink departures and alerts, plus current Burnaby Mountain weather.
- **Rewards** — Completion-based driver points and Eco-Scores.

## Tech Stack

| Layer | Technologies |
| --- | --- |
| Backend | Java 21, Spring Boot 4.1, Spring MVC |
| Frontend | Thymeleaf, Tailwind CSS 4, JavaScript |
| Security | Spring Security, BCrypt, Spring Session JDBC |
| Data | Spring Data JPA, H2 for local development, PostgreSQL for deployment |
| Integrations | TransLink GTFS-Realtime, Open-Meteo, Leaflet, OpenStreetMap |
| Testing and delivery | JUnit, MockMvc, JaCoCo, GitHub Actions, Docker |

## Getting Started

Install **JDK 21** and Git. The Maven wrapper is included; Node.js and npm are only needed to rebuild the stylesheet.

```bash
git clone https://github.com/this-acorn/CommuteMate.git
cd CommuteMate
```

**macOS / Linux**

```bash
chmod +x mvnw
./mvnw spring-boot:run
```

**Windows PowerShell**

```powershell
.\mvnw.cmd spring-boot:run
```

Open [localhost:8080](http://localhost:8080). Local data is stored in an H2 database under `data/`.

### Demo Accounts

Demo accounts and rides are seeded by default. These credentials are for trying the app locally.

| Role | Email | Password |
| --- | --- | --- |
| Driver | `driver@sfu.ca` | `demo123` |
| Rider | `rider@sfu.ca` | `demo123` |
| Additional rider | `demo-rider2@sfu.ca` | `demo123` |

Set `SEED_DEMO_DATA=false` to stop creating demo accounts and rides. This does not remove existing demo data.

## Configuration

Set environment variables before starting the application. The defaults support local development.

| Variable | Purpose | Default |
| --- | --- | --- |
| `PORT` | HTTP port | `8080` |
| `TRANSLINK_API_KEY` | Live transit data | Empty |
| `SPRING_DATASOURCE_URL` | JDBC database URL | `jdbc:h2:file:./data/commutemate` |
| `SPRING_DATASOURCE_USERNAME` | Database username | `sa` |
| `SPRING_DATASOURCE_PASSWORD` | Database password | Empty |
| `REMEMBER_ME_KEY` | Remember-me cookie signing key | Local development key |
| `SEED_DEMO_DATA` | Create demo accounts and rides | `true` |
| `APP_TIME_ZONE` | Application time zone | `America/Vancouver` |
| `SHOW_SQL` | Hibernate SQL logging | `false` |

Live transit requires a TransLink API key. The app still starts without one and shows an unavailable state for transit data. Open-Meteo weather does not require a key; external service failures are handled with fallbacks.

## Ride Workflow

```text
Seat requested -> Driver confirms -> Rider confirms boarding -> Driver confirms arrival
    PENDING         CONFIRMED          BOARDING_CONFIRMED             COMPLETED
```

Drivers can reject pending requests. Riders can cancel pending or confirmed requests before departure. Confirming a request reserves a seat; cancelling a confirmed request releases it. Rewards count only rides with at least one completed request.

## Development

### Tests and Coverage

```bash
./mvnw clean verify
```

On Windows, use `.\mvnw.cmd clean verify`.

Tests cover authentication, profiles, ride coordination, rewards, chat, notifications, and external API handling. The coverage report is generated at `target/site/jacoco/index.html`. GitHub Actions runs the same verification for pushes and pull requests to `master`.

### Rebuild Styles

The generated stylesheet is committed. After changing Tailwind classes or styles, run:

```bash
npm ci
npx @tailwindcss/cli -i tailwind/entry.css -o src/main/resources/static/css/commutemate.css
```

### Project Structure

```text
src/main/java/project/group1/commutemate/
  Config/        Security, caching, and time configuration
  User/          Accounts, registration, and profiles
  controller/    HTTP endpoints
  model/         Domain models
  repository/    Database access
  service/       Application logic and external integrations
src/main/resources/
  templates/     Thymeleaf pages
  static/        CSS and JavaScript
  translink/     Transit reference data
src/test/        Automated tests
tailwind/        Stylesheet source
```

## Deployment

The included `Dockerfile` builds and starts the app. For Render or another container host, connect a persistent PostgreSQL database and set:

```text
SPRING_DATASOURCE_URL=jdbc:postgresql://HOST/DATABASE
SPRING_DATASOURCE_USERNAME=USER
SPRING_DATASOURCE_PASSWORD=PASSWORD
REMEMBER_ME_KEY=<long stable random value>
TRANSLINK_API_KEY=<your key>
SEED_DEMO_DATA=false
```

Use the JDBC URL format shown above, with credentials in their separate variables. PostgreSQL stores both application data and sessions. Keep `REMEMBER_ME_KEY` stable across deployments so remember-me cookies remain valid. The default H2 file is intended for local development and does not survive replacement of an ephemeral container.

## Current Limitations

- Registration checks the `@sfu.ca` domain but does not verify email ownership.
- Maps display supported route endpoints and a straight-line connection; they do not calculate driving directions.
- Driver ratings and vehicle labels are ride metadata; user-submitted reviews are not implemented.
- Live transit depends on a valid API key and third-party availability.
