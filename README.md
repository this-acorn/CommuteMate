# CommuteMate

CommuteMate is a web-based application designed to help SFU students commute to the Burnaby campus more efficiently, reliably, and affordably. The platform connects student drivers and riders who have similar routes and schedules, making carpooling easier within the SFU community. Unlike traditional navigation applications, CommuteMate combines real-time transit information, weather conditions on Burnaby Mountain, and student-focused ride matching in a single platform. By improving access to transportation alternatives during transit disruptions and adverse weather conditions, the application aims to reduce commuting costs, alleviate parking congestion, and improve the overall student experience.

## Tech Stack

CommuteMate is a **Spring Boot** application (Java 21) that serves server-rendered **Thymeleaf** HTML pages styled with plain **CSS** and enhanced with vanilla **JavaScript**. User accounts, rides, and ride requests are stored in H2 for local development and in **Postgres** when deployed. The external APIs (TransLink, Open-Meteo, and OpenRouteService) are planned for later iterations.

## Running CommuteMate

From the `group-project-1` directory:

```bash
./mvnw spring-boot:run        # or mvnw.cmd spring-boot:run on Windows
```

Then open <http://localhost:8080>. To build a runnable jar instead: `./mvnw clean package` then `java -jar target/commutemate-0.0.1-SNAPSHOT.jar`.

Demo rides are enabled by default so a fresh checkout is immediately browsable. Set `SEED_DEMO_DATA=false` to disable them.

Locally the database is an H2 file under `data/`, which is git-ignored. Delete that folder to start from a clean database.

## Deploying to Render

Render rebuilds the container filesystem on every deploy, on every restart, and whenever a free instance wakes from an idle spin-down. Anything written inside the container — including an H2 file — is erased each time, so the deployment **must** point at a managed Postgres instance or every registered account disappears.

Create a Postgres instance in Render, then set these environment variables on the web service:

| Variable | Value |
| --- | --- |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://HOST/DATABASE` |
| `SPRING_DATASOURCE_USERNAME` | the database user |
| `SPRING_DATASOURCE_PASSWORD` | the database password |
| `REMEMBER_ME_KEY` | any long random string |
| `TRANSLINK_API_KEY` | the TransLink key |

Render's dashboard shows an *Internal Database URL* in the form `postgresql://USER:PASSWORD@HOST/DATABASE`. That is **not** a JDBC URL — split it into the three `SPRING_DATASOURCE_*` values above, keeping the `jdbc:postgresql://` prefix and dropping the credentials from the URL itself.

`REMEMBER_ME_KEY` signs the "Keep me signed in" cookie. It has to stay the same across deploys; if it changes, every outstanding cookie is invalidated and members are signed out. Login sessions themselves are stored in the database (`SPRING_SESSION`), so they survive restarts too.

## Screens

| Path | Description | Epic |
| --- | --- | --- |
| `/` | Landing page — hero, live mountain/transit snapshot, feature grid | — |
| `/auth` | Sign in / sign up with `@sfu.ca` validation (login/sign-up toggle, role picker) | 1 |
| `/dashboard/rider` | Rider dashboard — next ride, suggested rides, weather, live transit, commute stats | 2 |
| `/dashboard/driver` | Driver dashboard — earnings/points/eco stats, upcoming rides, rating | 2 & 4 |
| `/rides/available` | Browse rides with live client-side search + sort (also works without JS) | 3 |
| `/rides/create` | Publish a ride with a live preview and estimated-rewards panel | 5 |

The visual design (SFU-red / mountain-teal theme, card layout, and copy) was prototyped separately and ported here into Thymeleaf + CSS + JS. Shared styles live in `src/main/resources/static/css/commutemate.css` and interactions in `static/js/commutemate.js`; the page-shared header/footer are Thymeleaf fragments in `templates/fragments/layout.html`.

## Problem Statement

Commuting to SFU Burnaby presents several challenges for students. Many students rely heavily on a limited number of transit routes, such as the R5 and 145 buses. Service disruptions, delays, mechanical issues, or labour disputes can significantly impact access to campus. In addition, Burnaby Mountain experiences weather conditions that often differ from those in the surrounding metropolitan area. Snow, ice, and poor road conditions can create sudden transportation difficulties for both transit users and drivers.

Although many students choose to drive to avoid these uncertainties, individual vehicle use contributes to parking shortages, increased transportation expenses, and greater environmental impact. While carpooling represents a practical solution, students currently lack a convenient and trusted platform specifically designed to coordinate rides with other SFU students.

## Existing Solutions and Competitive Analysis

Students currently rely on services such as Google Maps, Apple Maps, Uber, Lyft, and Poparide to plan their commutes. These platforms provide valuable route planning and transportation services but are not designed specifically for the needs of SFU students. Navigation applications focus on route optimization rather than connecting students with similar schedules or destinations. Ride-hailing services offer transportation on demand but are often too expensive for regular student use. Ride-sharing platforms such as Poparide primarily support long-distance travel and do not integrate local transit information or campus-specific weather conditions.

CommuteMate addresses these limitations by combining carpool matching, transit monitoring, and weather information into a single platform tailored to the SFU community. Rather than replacing existing navigation tools, the application complements them by helping students make more informed commuting decisions.

## Target Audience

The primary target audience consists of SFU students who regularly travel to the Burnaby campus. This includes students who depend on public transit, students who drive personal vehicles, and students who are interested in reducing transportation costs through ride sharing. By restricting access to verified SFU students, the platform aims to create a trusted and community-oriented environment.

## Epics

The first epic focuses on user authentication and profile management, allowing students to register, log in, and create personal profiles. 

The second epic is a real-time commute dashboard that provides transit updates, service alerts, and weather information relevant to Burnaby Mountain.

The third epic involves an interactive map and carpool matching system that connects drivers and riders travelling along similar routes. 

The fourth epic focuses on incentives and rewards. It will include a virtual point system. Drivers can earn points from Riders or an Eco-Score based on completed rides. In future versions, these points could be connected to campus-related rewards such as parking discounts or cafe coupons through potential partnerships with SFU or student organizations.

The fifth epic focuses on ride scheduling and coordination, allowing users to create ride requests, confirm trips, and communicate with one another.

## API Usage

The TransLink API will provide real-time transit information, including bus locations and service alerts relevant to SFU commuters. 

The Open-Meteo API will provide weather information and forecasts for Burnaby Mountain, helping users anticipate snow, ice, and other conditions that may affect travel. 

OpenRouteService will be used to calculate routes, travel distances, and estimated travel times for drivers and riders.

The team may also use SFU Course Outlines API to support schedule-based ride matching. If feasible, this feature would allow students to identify potential carpool partners based on overlapping class schedules.

## First Group Meeting

Due to scheduling conflicts and personal commitments, only two group members were able to attend the initial meeting. During the meeting, the attendees created the group's GitHub repository, initialized the Spring Boot project, and discussed several potential project ideas. 

To ensure that all members could contribute, notes were shared through the group's private Discord channel. Team members introduced themselves, shared project ideas, and provided feedback asynchronously on Discord. A more detailed discussion regarding individual backgrounds, technical experience, and role assignments will be discussed after the midterm.
