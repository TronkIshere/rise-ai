# Demo Credentials — DEVELOPMENT / DEMO ONLY

**Never use these accounts or this password outside a local development database.** All people, organizations and campaigns are fictional.

Default password for every account: `Demo@12345` (override with env `RISE_DEMO_PASSWORD` before first seeding).

| Email | Role | Status | Display name |
|---|---|---|---|
| admin@riseai.example | ADMIN | ACTIVE | Demo Platform Admin |
| donor1@riseai.example | DONOR | ACTIVE | An Nguyen (Demo Donor) |
| donor2@riseai.example | DONOR | ACTIVE | Binh Tran (Demo Donor) |
| donor3@riseai.example | DONOR | ACTIVE | Chi Le (Demo Donor) |
| creator1@riseai.example | CAMPAIGN_CREATOR | ACTIVE | Dung Pham (Demo Creator) |
| creator2@riseai.example | CAMPAIGN_CREATOR | ACTIVE | Hoa Vo (Demo Creator) |
| creator3@riseai.example | CAMPAIGN_CREATOR | ACTIVE | Khanh Do (Demo Creator) |
| suspended.donor@riseai.example | DONOR | SUSPENDED | Suspended Demo Donor (login is rejected) |

User ids are generated UUIDs; emails are the stable identifiers.

Login:
```bash
curl -s -X POST localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"admin@riseai.example","password":"Demo@12345"}'
```
