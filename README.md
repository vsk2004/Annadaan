# FoodBridge — Food Donation Platform

A full-stack food donation platform connecting food donors with verified NGOs through GPS-based matching.

## Tech Stack

| Layer | Technology |
|---|---|
| Frontend | React 18 + Vite |
| Backend | Spring Boot 3.2.5 (Java 17) |
| Database | MySQL |
| Auth | JWT (JJWT) + BCrypt |
| Distance | Haversine formula (native SQL) |

---

## Prerequisites

- **Java 17+**
- **Maven** (or use `mvnw`)
- **Node.js 18+** and **npm**
- **MySQL 8+** running locally

---

## Quick Start

### 1. Database Setup

```sql
CREATE DATABASE food_donation_db;
```

### 2. Backend

```bash
cd backend

# Copy and edit environment variables (PowerShell)
Copy-Item .env.example .env
# Edit .env with your database credentials and a strong JWT secret

# Run with environment variables loaded
# Option A: set env vars in your shell
$env:DB_PASSWORD="your_password"
$env:JWT_SECRET="your_256_bit_secret"
mvn spring-boot:run

# Option B: use IntelliJ/VSCode with .env file support
```

Backend starts on: **http://localhost:8080**

### Password Reset Email

Password reset links are single-use and expire after 30 minutes. The backend sends them through Brevo's transactional email HTTPS API, so SMTP ports are not required. Set `BREVO_API_KEY` to a Brevo API key and `MAIL_FROM` to a sender verified in Brevo. Set `FRONTEND_URL` to the frontend origin (locally `http://localhost:5173`). Never commit real credentials.

#### Local Testing

1. Create a Brevo account, verify a sender address, and create an API key.
2. From the `backend` directory, copy `.env.example` to `.env` and set `BREVO_API_KEY` to your newly rotated key, `MAIL_FROM` to your Brevo-verified sender, and `FRONTEND_URL` to `http://localhost:5173`. Spring Boot loads this local `.env`; it is git-ignored. Alternatively, set those variables in the backend process environment.
3. Start the backend and frontend, request a reset for a registered address, and open the email's `/reset-password?token=...` link within 30 minutes.
4. Set a new password, sign in with it, then verify the same reset link cannot be used again.

#### Render Deployment

Configure these on the Render backend web service under **Environment**:

| Variable | Value |
|---|---|
| `BREVO_API_KEY` | Brevo API key (secret) |
| `MAIL_FROM` | Sender address verified in Brevo |
| `FRONTEND_URL` | Public frontend URL, without a trailing slash |
| `CORS_ORIGINS` | Public frontend origin, e.g. `https://annadaan-frontend.onrender.com` |
| `JWT_SECRET` | Long random production secret |
| `DB_URL` | Production MySQL JDBC URL |
| `DB_USERNAME` | Production database username |
| `DB_PASSWORD` | Production database password (secret) |
| `DB_DRIVER` | `com.mysql.cj.jdbc.Driver` |
| `HIBERNATE_DIALECT` | `org.hibernate.dialect.MySQLDialect` |

For a Render static frontend, also set `VITE_API_URL` to `https://<backend-service>.onrender.com/api` at build time. Use a persistent external MySQL-compatible database: Render's free service filesystem is ephemeral, so the local file-based H2 default is not suitable for deployed user and reset-token data. Deploy both services, request a reset from the public site, confirm receipt through Brevo, submit the link, then verify login with the new password. Delivery has not been tested until this live check succeeds with valid Brevo credentials.

### 3. Frontend

```bash
cd frontend
npm install    # already done
npm run dev
```

Frontend starts on: **http://localhost:5173**

---

## API Endpoints

### Auth (`/api/auth`)
| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/register` | Public | Register donor/NGO |
| POST | `/login` | Public | Login, returns JWT |
| GET | `/me` | JWT | Current user profile |

### Donor (`/api/donor`) — requires `ROLE_DONOR`
| Method | Path | Description |
|---|---|---|
| POST | `/donations` | Create donation (multipart) |
| GET | `/donations` | My donations |
| GET | `/stats` | Dashboard stats |
| PATCH | `/donations/{id}/cancel` | Cancel AVAILABLE donation |

### NGO (`/api/ngo`) — requires `ROLE_NGO` + `VERIFIED`
| Method | Path | Description |
|---|---|---|
| GET | `/donations/nearby?radiusKm=5` | Nearby donations (5 or 10 km) |
| POST | `/donations/{id}/accept` | Accept a donation |
| PATCH | `/donations/{id}/delivered` | Mark as delivered |
| GET | `/donations` | My accepted donations |
| GET | `/stats` | NGO stats |
| GET | `/notification-preferences` | Get NGO SMS and Email alert preferences |
| PUT | `/notification-preferences` | Update NGO SMS/Email alert opt-in preferences |
| GET | `/sms-notifications` | View dispatched SMS alert logs for current NGO |
| GET | `/email-notifications` | View dispatched Email alert logs for current NGO |

---

## Automated Email & SMS Notifications for Nearby NGOs

Annadaan! includes automated Email (Brevo transactional API) and SMS (Twilio / Mock) notifications that alert eligible nearby NGOs immediately when a donor posts an available food donation.

### Workflow & Trigger
1. **Donor Submission**: Donor submits valid donation form.
2. **Persistence**: Donation is saved with status `AVAILABLE` in MySQL / H2.
3. **Transaction Commit**: Once the database transaction commits, a `DonationCreatedEvent` is published.
4. **Asynchronous Dispatch**:
   - `DonationEmailNotificationListener` triggers `DonationEmailService.sendNearbyDonationAlertEmails`, which identifies eligible nearby NGOs within matching radius (Haversine formula), validates email preferences (`isEmailNotificationsEnabled`), enforces idempotency per `(donation_id, recipient_id)`, and sends responsive HTML + plain-text alert emails with claim links via Brevo.
   - `DonationNotificationListener` evaluates NGO eligibility for SMS alerts within geographic radius, ensures opt-in consent (`isSmsNotificationsEnabled`), and dispatches SMS alerts via Twilio/Mock.
5. **Fault Isolation**: If Brevo or the SMS provider encounters a failure, the donation remains `AVAILABLE` and unaffected. Failure for one recipient does not prevent alerts to other recipients.
6. **Idempotency**: Enforced at both the service layer and database logging tables (`email_notification_logs` and `sms_notification_logs`) to prevent duplicate deliveries.

### SMS Environment Variables

| Variable | Default | Description |
|---|---|---|
| `SMS_ENABLED` | `false` | Master toggle to enable SMS dispatch (`true` / `false`) |
| `SMS_PROVIDER` | `mock` | Active provider: `mock` (development) or `twilio` (production) |
| `TWILIO_ACCOUNT_SID` | *(empty)* | Twilio Account SID (starts with `AC...`) |
| `TWILIO_AUTH_TOKEN` | *(empty)* | Twilio Auth Token (keep secret) |
| `TWILIO_FROM_NUMBER` | *(empty)* | Twilio SMS-enabled phone number in E.164 format (e.g. `+12025550123`) |
| `TWILIO_MESSAGING_SERVICE_SID` | *(empty)* | Optional Twilio Messaging Service SID (starts with `MG...`) |
| `APP_BASE_URL` | `http://localhost:5173` | Frontend URL included in SMS action links |
| `APP_SMS_NOTIFICATION_RADIUS_KM` | `10.0` | Maximum matching radius for nearby NGO notifications |

### Twilio Setup Guide

Twilio provides direct, global transactional SMS without requiring complex DLT registrations:

1. **Twilio Account Setup**:
   - Sign up or log in at [twilio.com](https://www.twilio.com).
   - In the Twilio Console Dashboard, find your **Account SID** and **Auth Token**.
2. **Phone Number or Messaging Service**:
   - Buy or configure an SMS-capable phone number under **Phone Numbers > Manage > Active Numbers**.
   - Copy the number in E.164 format (e.g. `+12025550123`) and set `TWILIO_FROM_NUMBER`.
   - Alternatively, if using a Messaging Service, configure `TWILIO_MESSAGING_SERVICE_SID` (e.g. `MG...`).
3. **Configure Environment Variables in `backend/.env`**:
   ```bash
   SMS_ENABLED=true
   SMS_PROVIDER=twilio
   TWILIO_ACCOUNT_SID=ACXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
   TWILIO_AUTH_TOKEN=your_auth_token_here
   TWILIO_FROM_NUMBER=+12025550123
   ```
4. **Outgoing Message Format**:
   - SMS messages are automatically generated and dispatched to eligible nearby NGOs when a food donation is created:
     ```text
     Annadaan Alert: Fresh Biryani (15 MEALS) is available ~3.5 km away. Pickup before: 06:30 PM, 10 Nov. Claim: http://localhost:5173/ngo/dashboard
     ```

### NGO Consent & Opt-In Settings
- Under **NGO Profile > Notification Preferences**, NGOs can enable or disable:
  `"Receive SMS alerts for nearby food donations"`
- By default, SMS notifications remain disabled until the NGO explicitly opts in.
- NGOs can opt out anytime from their profile page.
- Inactive accounts, unverified NGOs, NGOs without a valid Indian mobile number (`91XXXXXXXXXX`), and NGOs outside the matching radius are automatically excluded.

### Local Testing with Mock Mode
During development, keep `SMS_PROVIDER=mock`:
- Set `SMS_ENABLED=true` in `backend/.env`.
- Post a new food donation from a donor account.
- Check backend console logs to see the simulated dispatch with masked recipient phone numbers and generated `mock-UUID` tracking IDs:
  ```text
  [MOCK SMS] Simulated alert sent to 9198*****210 for donation #101: Hot Meals (25.0 MEALS) ~2.4 km away. Pickup by: 08:30 PM, 10 Nov. Link: http://localhost:5173/ngo/dashboard
  ```
- No real SMS charges or external network calls are made in mock mode.

---

## Project Structure

```
food-donation-app/
├── backend/
│   ├── src/main/java/com/fooddonation/backend/
│   │   ├── BackendApplication.java
│   │   ├── config/         (SecurityConfig, WebConfig)
│   │   ├── controller/     (AuthController, DonorController, NgoController)
│   │   ├── dto/            (Request/Response DTOs)
│   │   ├── entity/         (User, Donation)
│   │   ├── repository/     (UserRepository, DonationRepository)
│   │   ├── security/       (JwtUtils, JwtAuthFilter, UserDetailsServiceImpl)
│   │   └── service/        (AuthService, DonationService)
│   ├── .env.example
│   └── pom.xml
└── frontend/
    ├── src/
    │   ├── api/            (axios client)
    │   ├── components/     (Navbar, Footer, DonationCard, DonationFormModal, ProtectedRoute)
    │   ├── context/        (AuthContext)
    │   └── pages/          (LandingPage, LoginPage, RegisterPage, DonorDashboard, NgoDashboard)
    ├── .env
    └── index.html
```

---

## Security Notes

- Passwords hashed with BCrypt (strength 10)
- JWT tokens expire in 24 hours (configurable via `JWT_EXPIRATION_MS`)
- User identity always obtained from Spring Security context, never from request body
- CORS restricted to allowed origins via `CORS_ORIGINS` env var
- Admin registration via public endpoint is **blocked**
- NGO role cannot accept donations until `verificationStatus = VERIFIED`
- Allowed radius values validated server-side (5 or 10 km only)

---

## Distance Calculation

Uses the **Haversine formula** in a native MySQL query:

```sql
6371 * ACOS(
  COS(RADIANS(ngoLat)) * COS(RADIANS(pickup_latitude))
  * COS(RADIANS(pickup_longitude) - RADIANS(ngoLng))
  + SIN(RADIANS(ngoLat)) * SIN(RADIANS(pickup_latitude))
)
```

Only AVAILABLE donations with a future pickup deadline, within the selected radius, are returned.
"# Annadaan" 
