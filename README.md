# CommuteMate:https://commutemate-jfk7.onrender.com/


**CommuteMate** is a student-focused carpool and commute-planning web application for Simon Fraser University. It connects SFU drivers and riders, shows live transit and Burnaby Mountain weather information, and supports the complete ride workflow from publishing a ride to requesting a seat, coordinating in chat, confirming arrival, and receiving completion-based rewards.

## Current Features

### Accounts and security

- Registration is restricted to `@sfu.ca` email addresses.
- Users choose one role at registration: **Driver** or **Rider**.
- Passwords are hashed with BCrypt.
- Successful login redirects each user to the dashboard for their role.
- Failed forms preserve safe input values and show field-level validation messages.
- Users can view their profile, update their full name, and change their password.
- Profile name changes are synchronized with existing ride and request records.
- JDBC-backed sessions and a persistent remember-me key support deployment restarts.
- Custom `403`, `404`, `500`, and general error pages are included.

### Rider experience

- Browse upcoming rides with available seats.
- Search by driver, pickup, or destination.
- Filter using pickup and destination dropdowns.
- Sort by recommended order, departure time, price, Eco-Score, or driver rating.
- View ride details, price, occupancy, notes, vehicle information, and route endpoints.
- Request one seat, cancel eligible requests, and track request status.
- Confirm boarding beginning 30 minutes before departure.
- View upcoming and suggested rides on the rider dashboard.

### Driver experience

- Publish a ride using supported SFU-area pickup and destination locations.
- Configure departure date/time, 1–5 seats, price from `$0` to `$10`, and optional notes.
- View and manage upcoming rides from the driver dashboard.
- Confirm or reject rider requests.
- Delete owned rides together with related requests and conversations.
- Confirm arrival after departure to complete rides for boarded riders.
- Earn points and an average Eco-Score only from rides that actually reach completion.

### Ride communication and notifications

- A persistent ride chat is available to the ride owner and riders with confirmed, boarding-confirmed, or completed requests.
- Chat messages are stored in the database and loaded through lightweight browser polling.
- Messages are limited to 1,000 characters.
- Unread chat indicators appear on dashboard chat links and clear after the conversation is opened.
- In-app notifications are created for seat requests, confirmations, rejections, cancellations, boarding confirmations, and completed rides.
- The navigation bell displays an unread count.
- Notifications can be marked read individually or all at once.

### Live commute information and maps

- **TransLink GTFS-Realtime** provides upcoming campus bus departures and relevant service alerts.
- **Open-Meteo** provides current Burnaby Mountain temperature, weather conditions, wind speed, and direction.
- External requests use short connection/read timeouts so the application can render a fallback instead of hanging when an API is unavailable.
- Weather responses are cached for 10 minutes.
- Ride details use **Leaflet** and **OpenStreetMap** to show pickup and destination pins for recognized locations.

## Ride Lifecycle

```text
Rider requests a seat
        |
        v
     PENDING
      /   \
     v     v
CONFIRMED  REJECTED
    |
    | rider confirms boarding
    v
BOARDING_CONFIRMED
    |
    | driver confirms arrival after departure
    v
  COMPLETED
```

A pending or confirmed request may be cancelled before the ride departs. Confirming a request reserves a seat; cancelling a confirmed request releases it. Rewards are counted only when at least one request on the ride reaches `COMPLETED`.

## Tech Stack

| Area | Technology |
| --- | --- |
| Backend | Java 21, Spring Boot 4.1, Spring MVC |
| Views | Thymeleaf server-rendered HTML |
| Styling | Tailwind CSS 4 generated stylesheet |
| Browser code | Vanilla JavaScript |
| Authentication | Spring Security, BCrypt, remember-me |
| Persistence | Spring Data JPA, H2 locally, PostgreSQL in deployment |
| Sessions | Spring Session JDBC |
| API client | Spring `RestClient` |
| Caching | Spring Cache with Caffeine |
| Maps | Leaflet and OpenStreetMap |
| Testing | JUnit, Spring Boot Test, MockMvc, Spring Security Test |
| Coverage | JaCoCo |
| CI | GitHub Actions |
| Deployment | Docker / Render-compatible configuration |

## Prerequisites

- Java Development Kit **21**
- Git
- Node.js and npm only when rebuilding the Tailwind stylesheet
- A TransLink API key for live transit data

The Maven wrapper is included, so a separate Maven installation is not required.

## Run Locally

From the project root:

```bash
chmod +x mvnw
./mvnw spring-boot:run
```

On Windows:

```powershell
mvnw.cmd spring-boot:run
```

Open:

```text
http://localhost:8080
```

The default local database is a file-based H2 database under `data/`. To reset local application data, stop the server and remove that directory.


## Demo Accounts

The application seeds these accounts when they do not already exist:

| Role | Email | Password |
| --- | --- | --- |
| Driver | `driver@sfu.ca` | `demo123` |
| Rider | `rider@sfu.ca` | `demo123` |
| Additional rider | `demo-rider2@sfu.ca` | `demo123` |

Demo rides are enabled by default. Disable only the ride seeder with:

```bash
SEED_DEMO_DATA=false ./mvnw spring-boot:run
```

The demo credentials are intended only for development and course evaluation.

## Configuration

The application reads configuration from environment variables while keeping development-friendly defaults.

| Variable | Purpose | Default |
| --- | --- | --- |
| `PORT` | HTTP server port | `8080` |
| `SPRING_DATASOURCE_URL` | JDBC database URL | `jdbc:h2:file:./data/commutemate` |
| `SPRING_DATASOURCE_USERNAME` | Database username | `sa` |
| `SPRING_DATASOURCE_PASSWORD` | Database password | empty |
| `REMEMBER_ME_KEY` | Signs remember-me cookies | local development key |
| `TRANSLINK_API_KEY` | Enables live TransLink data | empty |
| `SEED_DEMO_DATA` | Enables demo ride seeding | `true` |
| `APP_TIME_ZONE` | Application time zone | `America/Vancouver` |
| `SHOW_SQL` | Displays Hibernate SQL logs | `false` |

Open-Meteo does not require an API key.

## Main Pages

| Path | Access | Purpose |
| --- | --- | --- |
| `/` | Public | Landing page; signed-in users are redirected to their dashboard |
| `/auth` | Public | Login and registration |
| `/dashboard/rider` | Rider | Requests, next ride, suggested rides, transit, and weather |
| `/dashboard/driver` | Driver | Rider requests, owned rides, rewards, and weather |
| `/rides/available` | Rider | Search, filter, sort, and browse available rides |
| `/rides/create` | Driver | Publish a ride |
| `/rides/{rideId}` | Authenticated | Ride details and request controls |
| `/rides/{rideId}/chat` | Ride participants | Persistent ride conversation |
| `/notifications` | Authenticated | Notification center |
| `/profile` | Authenticated | Account details, name update, and password update |

## External Services

### TransLink

Set `TRANSLINK_API_KEY` locally or in the deployment environment. Without a key, CommuteMate still starts and the interface reports that live transit information is temporarily unavailable.

### Open-Meteo

Current weather is requested for Burnaby Mountain. Failed or incomplete responses are logged and omitted from the page without breaking the dashboard.

### Leaflet and OpenStreetMap

The ride detail page loads Leaflet from a CDN and OpenStreetMap tiles in the browser. The current map displays recognized route endpoints and a straight-line visual connection; it does not calculate a road route.

## Testing and Coverage

Run the complete test suite and generate the coverage report:

```bash
./mvnw clean verify
```

The JaCoCo HTML report is generated at:

```text
target/site/jacoco/index.html
```

The tests cover authentication and authorization, form validation, profiles, rides, filtering, request coordination, post-ride completion, rewards, notifications, persistent chat, unread indicators, external API parsing/fallbacks, and custom error pages.

GitHub Actions runs `./mvnw clean verify` for pushes and pull requests targeting `master`, then uploads test reports and the JaCoCo report as workflow artifacts.

## Project Structure

```text
src/main/java/project/group1/commutemate/
├── Config/          Spring Security, caching, and time configuration
├── User/            Accounts, registration, current-user, and profile updates
├── controller/      MVC and chat HTTP endpoints
├── exception/       Domain-specific application exceptions
├── model/           Rides, requests, notifications, chat, transit, and weather models
├── repository/      Spring Data JPA repositories
├── service/         Ride, coordination, chat, notification, transit, and weather logic
└── RewardService.java

src/main/resources/
├── templates/       Thymeleaf pages and shared fragments
├── static/css/      Generated Tailwind stylesheet
├── static/js/       General interactions and ride-chat polling
├── translink/       Route reference data
└── application.properties
```

## Deploying to Render

Render replaces the service container during deploys and restarts, so production data must not use the local H2 file. Create a managed PostgreSQL database and configure:

```text
SPRING_DATASOURCE_URL=jdbc:postgresql://HOST/DATABASE
SPRING_DATASOURCE_USERNAME=USER
SPRING_DATASOURCE_PASSWORD=PASSWORD
REMEMBER_ME_KEY=<long stable random value>
TRANSLINK_API_KEY=<your key>
```

Render may display an internal database URL in this form:

```text
postgresql://USER:PASSWORD@HOST/DATABASE
```

Spring expects a JDBC URL, so add the `jdbc:` prefix and place the username and password in their separate environment variables.

The same PostgreSQL database stores application data and Spring Session records. Keep `REMEMBER_ME_KEY` unchanged across deploys so existing remember-me cookies remain valid.

Build and start commands are already defined in the included `Dockerfile`.

## Current Scope Notes

- Registration validates the `@sfu.ca` domain but does not send an email-verification message.
- The map supports a fixed catalog of recognized SFU-area locations and visualizes endpoints rather than calculating turn-by-turn directions.
- Driver ratings and vehicle labels are displayed as ride metadata; a user-submitted review system is outside the current implementation.
- Live TransLink information depends on a valid API key and third-party service availability.

## Team

| Member | Primary area |
| --- | --- |
| Aleena Gul | Epic 1 — Authentication and profiles |
| Dou Gwon | Epic 2 — Real-time commute dashboard |
| Jaskarn Deogun | Epic 3 — Ride browsing, maps, and matching |
| Yasmin Turyssova | Epic 4 — Rewards and Eco-Score |
| Roman Kalinichenko | Epic 5 — Scheduling, communication, and notifications |

CommuteMate is a student project developed for **CMPT 276**.
