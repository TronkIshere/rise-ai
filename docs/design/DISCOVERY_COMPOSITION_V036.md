# Campaign Discovery — Approved Composition v0.3.6

**Approval scope:** layout hierarchy and information structure at desktop 1440px. This is a **written baseline**, not a reconstructed high-fidelity design.

## Composition order

1. Keep **Hero and 01–02–03** according to **v0.2.1**; do not substitute v0.2.2 or v0.2.3.
2. Immediately below the journey band, show **8 first-level campaign categories in 2 rows × 4**, with a discrete category recognition line for each item. Colors are *illustrative and not yet approved tokens*.
3. Below categories, provide an introduction on the **left** and a **primary campaign on the right**, following the v0.3.4 A+C card structure.
4. Below this row, place **two compact A+C cards side by side**, following v0.3.5.
5. Do not use arbitrary extra rounded containers, section backgrounds, typography, decorative illustrations, or low-contrast secondary copy as if approved.

## Canonical category values

| API value | UI label |
|---|---|
| EDUCATION | Giáo dục |
| COMMUNITY | Cộng đồng |
| FOOD_SECURITY | Lương thực |
| WATER_SANITATION | Nước sạch |
| SCHOLARSHIP | Học bổng |
| INFRASTRUCTURE | Cơ sở vật chất |
| HEALTH | Y tế |
| OTHER | Khác |

Labels such as "Bữa ăn", "Thư viện" and "Thiết bị học tập" are descriptive subjects of demo campaigns; they are **not** new backend subcategories.

## Primary card (v0.3.4)

- Image region on the left, **approximately 37.5%**.
- Main content and finance on the right, **approximately 62.5%**.
- Top content: category, title, purpose, responsible organization when supplied, beneficiaries when supplied.
- Lower content: **net raised / target / funding percent with progress bar** and the campaign detail action.
- An empty or invalid finance field shows "Chưa có dữ liệu", not a fabricated 0%.

## Compact card (v0.3.5)

- Two cards side by side.
- Upper region: image left around **38%** and core campaign information right around **62%**.
- Lower region: **full-width funding summary and progress bar**, plus clear detail action.
- Long names and descriptions must fit without shrinking text to unreadable sizes.

## Seed-only content used to test layout

These are examples from `DemoDataSeeder.java`, **not verified live API values**.

| Demo | Official category | Net raised (simulated) | Target (simulated) |
|---|---|---:|---:|
| Hope Kitchen Weekly Meals (Demo) | FOOD_SECURITY | 22,000,000 VND | 60,000,000 VND |
| Riverside Community Library (Demo) | COMMUNITY | 45,000,000 VND | 80,000,000 VND |
| Laptops and Learning Kits for Highland Pupils (Demo) | EDUCATION | 75,000,000 VND | 120,000,000 VND |

For implementation, use `CampaignResponse` fields `category`, `shortDescription`, `organization`, `beneficiaryDescription`, `funding.netRaised`, `targetAmount`, `funding.progressPercent`, and `currency` as available. Filtering supports `q` and `category` in the public campaigns API.

## What was NOT approved

- Exact appearance of the AI-generated v0.3.6 screenshot (which may include invented organization names).
- Card border, radius, shadow, icon style, visual artwork or cover photography.
- Final category color values, full typography system, animation, mobile composition.
- Backend/API changes, React implementation, PR merge, or deployment.

Do not accidentally treat a visually generated composition screenshot as a validated source of data.
