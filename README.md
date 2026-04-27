# Room Booking System

Semestrální práce pro předměty **BTDD** (TDD/BDD/CI) a **DevOps** (Docker, Kubernetes, CI/CD).

Aplikace pro rezervaci místností s webovým rozhraním, Spring Security autentizací a plným DevOps pipeline.

---

## Architektura

```
[ Prohlížeč ]
      │  HTTP
      ▼
[ Thymeleaf Controllers ]   ← RoomController, BookingController
      │
      ▼
[ BookingService ]          ← business logika, 5 pravidel
      │
      ▼
[ Repository vrstva ]       ← Spring Data JPA
      │
      ▼
[ Databáze ]                ← H2 (dev) / PostgreSQL (prod, staging)
```

**Hlavní komponenty:**

| Vrstva | Třídy |
|---|---|
| Controller | `RoomController`, `BookingController`, `AuthController` |
| Service | `BookingService` — všechna business pravidla |
| Repository | `BookingRepository`, `RoomRepository`, `UserRepository` |
| Domain | `Room`, `User`, `Booking`, `BookingStatus`, `UserRole` |
| Config | `SecurityConfig`, `ClockConfig`, `DataInitializer` |

---

## Doménový model

| Entita | Popis |
|---|---|
| `Room` | Místnost (název, kapacita, popis) |
| `User` | Uživatel (username, heslo, role USER/ADMIN) |
| `Booking` | Rezervace (místnost, uživatel, čas od/do, stav PENDING/CONFIRMED/CANCELLED) |

---

## Business pravidla (BookingService)

| # | Pravidlo |
|---|---|
| 1 | Začátek rezervace nesmí být v minulosti |
| 2 | Délka rezervace musí být 30 minut až 4 hodiny |
| 3 | Rezervace se nesmí časově překrývat s existující |
| 4 | Rezervaci může zrušit pouze vlastník nebo admin |
| 5 | Rezervaci nelze zrušit méně než 2 hodiny před začátkem |

---

## Prostředí

| | Dev | Staging | Prod |
|---|---|---|---|
| Databáze | H2 in-memory | PostgreSQL (K8s) | PostgreSQL (K8s) |
| Spring profil | `dev` | `prod` | `prod` |
| Namespace | — | `room-booking-staging` | `room-booking` |
| Logging | INFO | DEBUG | INFO |
| CPU limit | — | 250m | 500m |
| Memory limit | — | 256Mi | 512Mi |
| Nasazení | ruční (`mvn spring-boot:run`) | **automatické (CD pipeline)** | ruční (`kubectl apply`) |
| Demo data | ✅ DataInitializer | ✅ DataInitializer | ✅ DataInitializer |

---

## Spuštění — lokální vývoj (H2)

```bash
./mvnw spring-boot:run
```

Aplikace poběží na http://localhost:8080

**Výchozí účty:**

| Username | Heslo | Role |
|---|---|---|
| admin | admin123 | ADMIN |
| jan | jan123 | USER |

---

## Spuštění — Docker Compose (PostgreSQL)

```bash
cp .env.example .env
# uprav .env — nastav DB_PASSWORD
docker compose up --build
```

Aplikace poběží na http://localhost:8080

---

## Spuštění — Kubernetes (minikube)

```bash
minikube start
minikube addons enable ingress

./mvnw package -DskipTests
docker build -t ghcr.io/kryxuscz/room-booking:latest .
minikube image load ghcr.io/kryxuscz/room-booking:latest

# Prod
kubectl apply -f k8s/prod/

# Staging
kubectl apply -f k8s/staging/

# Přístup
kubectl port-forward -n room-booking service/room-booking-service 8081:80
```

Prod: http://localhost:8081 | Staging: `kubectl port-forward -n room-booking-staging service/room-booking-service 8082:80`

---

## Testy

```bash
./mvnw verify          # všechny testy + JaCoCo report
./mvnw spotbugs:check  # statická analýza
```

Coverage report: `target/site/jacoco/index.html`

### Testovací strategie

| Třída | Typ | Co testuje | Co se mockuje |
|---|---|---|---|
| `BookingServiceTest` | Unit (Mockito) | všech 5 business pravidel, hraniční stavy | `BookingRepository` (stub), `Clock` (fake pevný čas) |
| `BookingRepositoryIntegrationTest` | Integrační (H2) | JPA dotazy `findByRoomAndStatusNot`, `findByUser` | nic — reálná H2 DB |
| `BookingControllerIntegrationTest` | Integrační (MockMvc) | HTTP endpointy, Spring Security, validace formuláře | `Clock` (@MockitoBean) |

**Proč se mockuje Clock:**
`LocalDateTime.now()` je volatilní závislost — v testech potřebujeme deterministický čas. `Clock` bean je injektován do `BookingService`, v testech nahrazen mockem s pevnou hodnotou. V produkci běží `Clock.systemDefaultZone()`.

**Proč se mockuje BookingRepository v unit testech:**
Unit testy testují pouze business logiku (BookingService), ne databázi. Mock repozitáře izoluje test od infrastruktury — testy jsou rychlé (FIRST: Fast) a nezávislé na DB.

---

## TDD proces

Projekt byl vyvíjen metodou **red → green → refactor** viditelnou v git historii:

1. `test(red)` — napsán failing test pro business pravidlo
2. `feat(green)` — minimální implementace aby test prošel
3. `refactor` — vyčištění kódu bez změny chování (private metody, pojmenování)

---

## CI/CD Pipeline

```
push na větev
      │
      ▼
  [test] mvn verify + JaCoCo report (artifact)
      │
      ├──▶ [analyse] SpotBugs statická analýza
      │
      └──▶ [docker] build + push ghcr.io  (pouze main)
                │
                ▼
         [deploy-staging] kubectl apply -f k8s/staging/
         (self-hosted runner, pouze main)
```

**Prod** se nasazuje ručně: `kubectl apply -f k8s/prod/`

---

## Struktura projektu

```
src/
├── main/java/cz/upce/roombooking/
│   ├── domain/       # Room, User, Booking, enums
│   ├── repository/   # Spring Data JPA
│   ├── service/      # BookingService — business logika
│   ├── controller/   # HTTP controllery (Thymeleaf)
│   ├── dto/          # BookingRequest
│   ├── exception/    # vlastní výjimky
│   └── config/       # Security, Clock, DataInitializer
├── main/resources/
│   ├── templates/    # Thymeleaf HTML šablony
│   ├── application.properties
│   ├── application-dev.properties    # H2 in-memory
│   └── application-prod.properties   # PostgreSQL
└── test/             # unit + integrační testy
k8s/
├── prod/             # Kubernetes manifesty pro produkci
└── staging/          # Kubernetes manifesty pro staging
.github/workflows/    # GitHub Actions CI/CD pipeline
Dockerfile            # multi-stage build, non-root user, healthcheck
docker-compose.yml    # lokální prod prostředí s PostgreSQL
```

---

## Bezpečnost

- Hesla hashována BCryptem (nikdy plaintext v DB)
- Kubernetes Secret používá base64 — plaintext hesla nejsou v repozitáři
- CI/CD používá `GITHUB_TOKEN` — žádné manuálně spravované tokeny
- Aplikace běží pod non-root uživatelem v Dockeru
- `/actuator/health` je jediný veřejný endpoint mimo login stránku
