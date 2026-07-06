# Intranet Portal

A full-stack employee intranet portal covering the daily operational needs of a company: leave management, meeting room booking, equipment (asset) tracking, announcements and a team directory.

Built as part of a software development internship at JForce Bilişim Teknolojileri.

## Tech Stack

**Backend**
- Java 21, Spring Boot 3.5
- SpringDoc OpenAPI (Swagger UI)
- Spring Data JPA / Hibernate
- Spring Security with JWT authentication
- Bean Validation
- MySQL
- Maven
- JUnit 5, Mockito, JaCoCo

**Frontend**
- React (Vite)

## Modules

| Module | Description | Status |
|--------|-------------|--------|
| Authentication & Roles | Login with JWT, role-based menus (Employee / Manager / Admin) | 🔜 Planned |
| Profile & Team Directory | Personal info, skill tags, filterable directory | 🔜 Planned |
| Leave Management | Leave requests with automatic workday calculation (weekends excluded) | 🔜 Planned |
| Approval Screen | Managers approve/reject leave & equipment requests | 🔜 Planned |
| Equipment (Asset) Management | Equipment requests, inventory and assignment tracking | 🔜 Planned |
| Meeting Room Booking | Hourly booking grid with overlap prevention | 🔜 Planned |
| Announcements | Admin posts, likes and comments | 🔜 Planned |
| Dashboard | Summary cards: birthdays, announcements, leave status, room occupancy | 🔜 Planned |

## Project Structure

```
.
├── backend/    # Spring Boot REST API
└── frontend/   # React application
```

## Development

- Commit messages follow [Conventional Commits](https://www.conventionalcommits.org/) — see [CONTRIBUTING.md](CONTRIBUTING.md)
- Daily progress is tracked in [PROGRESS.md](PROGRESS.md)

## Roadmap

1. Database design (ER diagram)
2. Auth + role structure (JWT)
3. Employee CRUD & profile
4. Leave management + approval flow
5. Equipment management (shared approval flow)
6. Meeting room reservations
7. Announcements (likes & comments)
8. Dashboard