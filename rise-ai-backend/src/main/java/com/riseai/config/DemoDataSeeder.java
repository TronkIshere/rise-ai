package com.riseai.config;

import com.riseai.campaign.BudgetItem;
import com.riseai.campaign.CampaignCategory;
import com.riseai.campaign.CampaignDtos.CampaignRequest;
import com.riseai.campaign.CampaignRepository;
import com.riseai.campaign.CampaignService;
import com.riseai.common.Slugs;
import com.riseai.disbursement.DisbursementDtos.CreateDisbursementRequest;
import com.riseai.disbursement.DisbursementService;
import com.riseai.dispute.DisputeDtos.OpenDisputeRequest;
import com.riseai.dispute.DisputeService;
import com.riseai.dispute.DisputeType;
import com.riseai.donation.DonationDtos.CreateDonationRequest;
import com.riseai.donation.DonationService;
import com.riseai.donation.PaymentMethod;
import com.riseai.evidence.EvidenceDtos.EvidenceRequest;
import com.riseai.evidence.EvidenceService;
import com.riseai.evidence.EvidenceType;
import com.riseai.milestone.MilestoneDtos.MilestoneRequest;
import com.riseai.milestone.MilestoneService;
import com.riseai.organization.Organization;
import com.riseai.organization.OrganizationDtos.OrganizationRequest;
import com.riseai.organization.OrganizationRepository;
import com.riseai.organization.OrganizationService;
import com.riseai.organization.OrganizationType;
import com.riseai.organization.VerificationStatus;
import com.riseai.refund.RefundDtos.CreateRefundRequest;
import com.riseai.refund.RefundService;
import com.riseai.security.AuthUser;
import com.riseai.user.Role;
import com.riseai.user.User;
import com.riseai.user.UserRepository;
import com.riseai.user.UserStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "rise.seed", name = "enabled", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {
    public static final String DEMO_NOTE = "[SIMULATED DEMO DATA] ";
    public static final String ADMIN_EMAIL = "admin@riseai.example";
    public static final List<String> CAMPAIGN_TITLES = List.of(
            "Laptops and Learning Kits for Highland Pupils (Demo)",
            "Riverside Community Library (Demo)",
            "Hope Kitchen Weekly Meals (Demo)",
            "Mekong Village Clean Water Filters (Demo)",
            "First-Generation University Scholarships (Demo)",
            "Green Village Community Centre Repairs (Demo)");

    private record DemoUser(String alias, String email, String fullName, Role role, UserStatus status) {
    }

    private static final List<DemoUser> USERS = List.of(
            new DemoUser("admin", ADMIN_EMAIL, "Demo Platform Admin", Role.ADMIN, UserStatus.ACTIVE),
            new DemoUser("donor1", "donor1@riseai.example", "An Nguyen (Demo Donor)", Role.DONOR, UserStatus.ACTIVE),
            new DemoUser("donor2", "donor2@riseai.example", "Binh Tran (Demo Donor)", Role.DONOR, UserStatus.ACTIVE),
            new DemoUser("donor3", "donor3@riseai.example", "Chi Le (Demo Donor)", Role.DONOR, UserStatus.ACTIVE),
            new DemoUser("creator1", "creator1@riseai.example", "Dung Pham (Demo Creator)", Role.CAMPAIGN_CREATOR, UserStatus.ACTIVE),
            new DemoUser("creator2", "creator2@riseai.example", "Hoa Vo (Demo Creator)", Role.CAMPAIGN_CREATOR, UserStatus.ACTIVE),
            new DemoUser("creator3", "creator3@riseai.example", "Khanh Do (Demo Creator)", Role.CAMPAIGN_CREATOR, UserStatus.ACTIVE),
            new DemoUser("suspended", "suspended.donor@riseai.example", "Suspended Demo Donor", Role.DONOR, UserStatus.SUSPENDED));

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final CampaignRepository campaignRepository;
    private final PasswordEncoder passwordEncoder;
    private final OrganizationService organizationService;
    private final CampaignService campaignService;
    private final MilestoneService milestoneService;
    private final DonationService donationService;
    private final EvidenceService evidenceService;
    private final DisbursementService disbursementService;
    private final DisputeService disputeService;
    private final RefundService refundService;
    private final TransactionTemplate transactionTemplate;
    private final RiseProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        seed();
    }

    public void seed() {
        Map<String, AuthUser> users = transactionTemplate.execute(status -> seedUsers());
        AuthUser admin = users.get("admin");
        AuthUser donor1 = users.get("donor1");
        AuthUser donor2 = users.get("donor2");
        AuthUser donor3 = users.get("donor3");
        AuthUser creator1 = users.get("creator1");
        AuthUser creator2 = users.get("creator2");
        AuthUser creator3 = users.get("creator3");

        UUID sunrise = organization(creator1, admin, "Sunrise Learning Fund (Demo)", OrganizationType.CHARITY_FUND,
                "Fictional education fund used for demo data.", "contact@sunrise-learning.example", "District 3, Ho Chi Minh City (fictional)", VerificationStatus.VERIFIED);
        UUID greenVillage = organization(creator2, admin, "Green Village Community Group (Demo)", OrganizationType.COMMUNITY_GROUP,
                "Fictional neighbourhood association used for demo data.", "hello@green-village.example", "Thu Duc, Ho Chi Minh City (fictional)", VerificationStatus.VERIFIED);
        UUID hopeKitchen = organization(creator3, admin, "Hope Kitchen Network (Demo)", OrganizationType.NGO,
                "Fictional meal-support network used for demo data.", "team@hope-kitchen.example", "Hai Ba Trung, Ha Noi (fictional)", VerificationStatus.VERIFIED);
        UUID mekong = organization(creator3, admin, "Mekong Clean Water Initiative (Demo)", OrganizationType.SOCIAL_ENTERPRISE,
                "Fictional water-access initiative awaiting platform review.", "info@mekong-water.example", "Can Tho (fictional)", VerificationStatus.PENDING_REVIEW);
        organization(creator1, admin, "Bright Steps Youth Club (Demo)", OrganizationType.OTHER,
                "Fictional youth club that has not requested review.", "club@bright-steps.example", "Da Nang (fictional)", VerificationStatus.UNVERIFIED);

        seedCampaign(CAMPAIGN_TITLES.get(0), () -> educationCampaign(admin, creator1, donor1, donor2, donor3, sunrise));
        seedCampaign(CAMPAIGN_TITLES.get(1), () -> libraryCampaign(admin, creator2, donor1, donor2, donor3, greenVillage));
        seedCampaign(CAMPAIGN_TITLES.get(2), () -> mealCampaign(admin, creator3, donor1, donor2, donor3, hopeKitchen));
        seedCampaign(CAMPAIGN_TITLES.get(3), () -> waterCampaign(creator3, mekong));
        seedCampaign(CAMPAIGN_TITLES.get(4), () -> scholarshipCampaign(creator1, sunrise));
        seedCampaign(CAMPAIGN_TITLES.get(5), () -> facilityCampaign(admin, creator2, donor1, donor2, greenVillage));
        log.info("Demo data seeding finished (simulated data only)");
    }

    private Map<String, AuthUser> seedUsers() {
        Map<String, AuthUser> result = new HashMap<>();
        for (DemoUser demo : USERS) {
            User user = userRepository.findByEmailIgnoreCase(demo.email()).orElseGet(() -> {
                User created = new User();
                created.setEmail(demo.email());
                created.setFullName(demo.fullName());
                created.setPasswordHash(passwordEncoder.encode(properties.seed().demoPassword()));
                created.setStatus(demo.status());
                created.setEmailVerified(true);
                created.getRoles().add(demo.role());
                return userRepository.saveAndFlush(created);
            });
            result.put(demo.alias(), new AuthUser(user.getId(), user.getEmail(), user.getRoles()));
        }
        return result;
    }

    private UUID organization(AuthUser owner, AuthUser admin, String name, OrganizationType type, String description, String email,
                              String address, VerificationStatus target) {
        return organizationRepository.findBySlug(Slugs.slugify(name)).map(Organization::getId).orElseGet(() -> transactionTemplate.execute(status -> {
            UUID id = organizationService.create(owner, new OrganizationRequest(name, DEMO_NOTE + description, type, email, "+84 28 0000 0000", address)).id();
            if (target != VerificationStatus.UNVERIFIED) {
                organizationService.submitForVerification(owner, id);
            }
            if (target == VerificationStatus.VERIFIED) {
                organizationService.verify(admin, id, "Simulated platform review of fictional demo data. This is not a legal verification of a real organization.");
            }
            return id;
        }));
    }

    private void seedCampaign(String title, Runnable scenario) {
        if (campaignRepository.existsBySlug(Slugs.slugify(title))) {
            return;
        }
        transactionTemplate.executeWithoutResult(status -> scenario.run());
        log.info("Seeded demo campaign: {}", title);
    }

    private void educationCampaign(AuthUser admin, AuthUser creator, AuthUser donor1, AuthUser donor2, AuthUser donor3, UUID org) {
        UUID c = campaignService.create(creator, campaign(org, CAMPAIGN_TITLES.get(0),
                "Laptops, tablets and furnished learning rooms for 180 pupils in a remote highland school.",
                CampaignCategory.EDUCATION, 120_000_000, 50_000, "Lao Cai Province (fictional school)", 180,
                "Pupils aged 9-15 at a fictional highland primary and lower-secondary school.",
                "Pupils share one ageing computer and study in rooms without adequate furniture.",
                "Buy shared devices, furnish two learning rooms and train teachers to use them.",
                "Every pupil gets two hours of supervised device time weekly; two rooms equipped; eight teachers trained.",
                List.of(budget("Laptops and tablets", 60_000_000), budget("Desks, chairs and storage", 35_000_000), budget("Teacher training", 25_000_000)))).id();
        UUID m1 = milestone(creator, c, "Procure laptops and tablets", 40_000_000, -20, 10,
                "Supplier invoice and delivery record accepted by a reviewer", "20 laptops and 10 tablets delivered to the school");
        UUID m2 = milestone(creator, c, "Furnish two learning rooms", 50_000_000, 5, 60,
                "Quotation, invoice and installation photos accepted by a reviewer", "Two rooms furnished for 40 pupils each");
        milestone(creator, c, "Teacher digital-skills training", 30_000_000, 50, 140,
                "Training attendance records and trainer invoice accepted", "Eight teachers complete a 20-hour course");
        activate(creator, admin, c);
        confirmed(admin, donor1, c, 30_000_000, false, "For the kids in the highlands!");
        confirmed(admin, donor2, c, 25_000_000, true, null);
        confirmed(admin, donor3, c, 20_000_000, false, "Good luck with the rollout.");
        donate(donor1, c, 5_000_000, false, "Awaiting simulated bank confirmation");
        milestoneService.start(creator, m1);
        UUID invoice = evidence(creator, m1, EvidenceType.INVOICE, "Supplier invoice for laptops and tablets", "sim://evidence/edu-m1-invoice.pdf");
        evidenceService.review(admin, invoice, true, "Invoice metadata matches the milestone plan (simulated review).");
        UUID r1 = disbursementService.create(creator, m1, new CreateDisbursementRequest(vnd(40_000_000), "Pay the device supplier")).id();
        disbursementService.startReview(admin, r1);
        disbursementService.approve(admin, r1, "Evidence accepted and amount within the milestone budget");
        disbursementService.release(admin, r1);
        milestoneService.complete(admin, m1, "Delivery confirmed in simulated review");
        milestoneService.start(creator, m2);
        evidence(creator, m2, EvidenceType.PROGRESS_REPORT, "Furniture quotation and room layout", "sim://evidence/edu-m2-quotation.pdf");
        UUID r2 = disbursementService.create(creator, m2, new CreateDisbursementRequest(vnd(20_000_000), "Deposit for desks and chairs")).id();
        disbursementService.startReview(admin, r2);
    }

    private void libraryCampaign(AuthUser admin, AuthUser creator, AuthUser donor1, AuthUser donor2, AuthUser donor3, UUID org) {
        UUID c = campaignService.create(creator, campaign(org, CAMPAIGN_TITLES.get(1),
                "Turn an unused riverside hall into a free community library and reading space.",
                CampaignCategory.COMMUNITY, 80_000_000, 50_000, "Thu Duc, Ho Chi Minh City (fictional)", 1200,
                "Residents of a fictional riverside ward, especially children and older adults.",
                "The ward has no public library and the nearest one is 9 km away.",
                "Renovate the hall, stock 3,000 books and run a weekly reading programme.",
                "A library open six days a week with 3,000 titles and 50 weekly programme participants.",
                List.of(budget("Shelving and renovation", 30_000_000), budget("Books", 30_000_000), budget("Reading programme", 20_000_000)))).id();
        UUID m1 = milestone(creator, c, "Renovate hall and install shelving", 30_000_000, -20, 20,
                "Contractor receipts and completion photos accepted", "Hall repainted, lit and shelved");
        milestone(creator, c, "Purchase and catalogue books", 30_000_000, 15, 80,
                "Publisher invoices and catalogue export accepted", "3,000 titles catalogued");
        milestone(creator, c, "Run weekly reading programme", 20_000_000, 70, 140,
                "Attendance logs and facilitator reports accepted", "24 weekly sessions delivered");
        activate(creator, admin, c);
        confirmed(admin, donor2, c, 20_000_000, false, "Books change lives.");
        confirmed(admin, donor3, c, 15_000_000, false, null);
        confirmed(admin, donor1, c, 10_000_000, true, null);
        milestoneService.start(creator, m1);
        UUID receipt = evidence(creator, m1, EvidenceType.RECEIPT, "Contractor receipt for shelving", "sim://evidence/lib-m1-receipt.pdf");
        evidenceService.review(admin, receipt, true, "Receipt metadata accepted (simulated review).");
        UUID photo = evidence(creator, m1, EvidenceType.PHOTO, "Shelving installation photo", "sim://evidence/lib-m1-photo.jpg");
        evidenceService.review(admin, photo, false, "Photo is too blurry to confirm installation; please resubmit.");
        UUID r1 = disbursementService.create(creator, m1, new CreateDisbursementRequest(vnd(15_000_000), "First contractor instalment")).id();
        disbursementService.startReview(admin, r1);
        disbursementService.approve(admin, r1, "Receipt accepted");
        disbursementService.release(admin, r1);
        UUID dispute = disputeService.open(donor3, c, new OpenDisputeRequest(m1, null, DisputeType.MISSING_EVIDENCE,
                "Installation photos are missing",
                DEMO_NOTE + "The released instalment is not yet backed by clear installation photos.",
                List.of("Community page post dated last week (simulated reference)"))).id();
        disputeService.assign(admin, dispute, admin.id());
        disputeService.addMessage(creator, dispute, "We will upload clear photos after the second shelving delivery this week.");
    }

    private void mealCampaign(AuthUser admin, AuthUser creator, AuthUser donor1, AuthUser donor2, AuthUser donor3, UUID org) {
        UUID c = campaignService.create(creator, campaign(org, CAMPAIGN_TITLES.get(2),
                "Weekly cooked meals and dry-food packs for elderly residents living alone.",
                CampaignCategory.FOOD_SECURITY, 60_000_000, 50_000, "Hai Ba Trung, Ha Noi (fictional)", 150,
                "Elderly residents living alone in a fictional urban ward.",
                "Many elderly residents skip meals because of mobility and income constraints.",
                "Volunteers cook and deliver meals weekly and distribute monthly dry-food packs.",
                "150 residents receive one cooked meal weekly and one dry-food pack monthly for six months.",
                List.of(budget("Rice and staples", 36_000_000), budget("Cooking and delivery", 15_000_000), budget("Volunteer coordination", 9_000_000)))).id();
        milestone(creator, c, "Months 1-2 meal service", 20_000_000, -10, 50,
                "Food supplier invoices and delivery logs accepted", "1,200 meals delivered");
        milestone(creator, c, "Months 3-4 meal service", 20_000_000, 45, 110,
                "Food supplier invoices and delivery logs accepted", "1,200 meals delivered");
        milestone(creator, c, "Months 5-6 meal service", 20_000_000, 105, 145,
                "Food supplier invoices and delivery logs accepted", "1,200 meals delivered");
        activate(creator, admin, c);
        confirmed(admin, donor1, c, 10_000_000, false, "Warm meals for everyone.");
        UUID donor2Donation = confirmed(admin, donor2, c, 8_000_000, false, null);
        UUID donor3Donation = confirmed(admin, donor3, c, 6_000_000, false, null);
        donationService.fail(admin, donate(donor1, c, 1_000_000, false, null), "Simulated bank transfer was not received");
        donationService.cancel(donor2, donate(donor2, c, 500_000, false, null));
        UUID refund = refundService.request(donor3, donor3Donation, new CreateRefundRequest(vnd(2_000_000), "Donated twice by mistake")).id();
        refundService.approve(admin, refund, "Duplicate donation confirmed (simulated)");
        refundService.record(admin, refund);
        refundService.request(donor2, donor2Donation, new CreateRefundRequest(vnd(3_000_000), "Personal financial hardship"));
    }

    private void waterCampaign(AuthUser creator, UUID org) {
        UUID c = campaignService.create(creator, campaign(org, CAMPAIGN_TITLES.get(3),
                "Household water filters and two community storage tanks for a riverside village.",
                CampaignCategory.WATER_SANITATION, 150_000_000, 100_000, "Can Tho (fictional village)", 320,
                "320 households in a fictional riverside village relying on untreated river water.",
                "Seasonal contamination causes frequent stomach illness, especially among children.",
                "Install household filters and two storage tanks, train residents and monitor water quality.",
                "320 households with filtered water; two tanks installed; quarterly water-quality checks.",
                List.of(budget("Household filters", 70_000_000), budget("Storage tanks", 50_000_000),
                        budget("Resident training", 20_000_000), budget("Water-quality monitoring", 10_000_000)))).id();
        milestone(creator, c, "Filter procurement", 40_000_000, 10, 50, "Supplier invoice accepted", "320 filters procured");
        milestone(creator, c, "Filter installation", 50_000_000, 45, 90, "Installation logs accepted", "320 filters installed");
        milestone(creator, c, "Storage tank construction", 40_000_000, 60, 120, "Contractor invoice and photos accepted", "Two tanks built");
        milestone(creator, c, "Training and monitoring", 20_000_000, 90, 145, "Training attendance and lab reports accepted", "Two trainings; two quality reports");
        campaignService.submit(creator, c);
    }

    private void scholarshipCampaign(AuthUser creator, UUID org) {
        UUID c = campaignService.create(creator, campaign(org, CAMPAIGN_TITLES.get(4),
                "Tuition and living support for ten first-generation university students.",
                CampaignCategory.SCHOLARSHIP, 90_000_000, 100_000, "Ho Chi Minh City (fictional)", 10,
                "Ten first-generation students from low-income families (fictional).",
                "Talented students drop out in the first year because of tuition and transport costs.",
                "Pay first-year tuition directly to universities and provide a books-and-transport stipend.",
                "Ten students complete their first academic year.",
                List.of(budget("Tuition", 60_000_000), budget("Books and transport", 30_000_000)))).id();
        milestone(creator, c, "Semester 1 tuition and stipend", 45_000_000, 10, 60, "University tuition receipts accepted", "Ten students enrolled");
        milestone(creator, c, "Semester 2 tuition and stipend", 45_000_000, 60, 140, "University tuition receipts accepted", "Ten students complete year one");
    }

    private void facilityCampaign(AuthUser admin, AuthUser creator, AuthUser donor1, AuthUser donor2, UUID org) {
        UUID c = campaignService.create(creator, campaign(org, CAMPAIGN_TITLES.get(5),
                "Roof repair and accessible restrooms for a volunteer-run community centre.",
                CampaignCategory.INFRASTRUCTURE, 70_000_000, 50_000, "Thu Duc, Ho Chi Minh City (fictional)", 400,
                "Families and elderly residents who use a fictional community centre for classes and health checks.",
                "The roof leaks during the rainy season and the restrooms are not wheelchair accessible.",
                "Repair the roof and rebuild two restrooms to accessibility standards.",
                "Centre usable year-round; two accessible restrooms.",
                List.of(budget("Roof repair", 35_000_000), budget("Accessible restrooms", 35_000_000)))).id();
        UUID m1 = milestone(creator, c, "Roof repair", 35_000_000, -15, 30, "Contractor invoice and inspection photos accepted", "Roof repaired and leak-tested");
        milestone(creator, c, "Accessible restrooms", 35_000_000, 25, 120, "Contractor invoice and accessibility checklist accepted", "Two accessible restrooms");
        activate(creator, admin, c);
        confirmed(admin, donor1, c, 20_000_000, false, null);
        confirmed(admin, donor2, c, 10_000_000, false, "Happy to help the centre.");
        milestoneService.start(creator, m1);
        UUID delivery = evidence(creator, m1, EvidenceType.DELIVERY_RECORD, "Roofing material delivery note", "sim://evidence/facility-m1-delivery.pdf");
        evidenceService.review(admin, delivery, true, "Delivery note accepted (simulated review).");
        UUID r1 = disbursementService.create(creator, m1, new CreateDisbursementRequest(vnd(15_000_000), "Contractor mobilisation payment")).id();
        disbursementService.startReview(admin, r1);
        disbursementService.requestMoreEvidence(admin, r1, "Please add the contractor's signed quotation.");
        campaignService.pause(admin, c, "Simulated pause: on-site facility inspection scheduled");
    }

    private void activate(AuthUser creator, AuthUser admin, UUID campaignId) {
        campaignService.submit(creator, campaignId);
        campaignService.approve(admin, campaignId, "Simulated platform review of fictional demo campaign");
        campaignService.publish(creator, campaignId);
    }

    private CampaignRequest campaign(UUID org, String title, String shortDescription, CampaignCategory category, long target, long minimum,
                                     String location, int beneficiaries, String beneficiaryDescription, String problem, String solution,
                                     String impact, List<BudgetItem> budget) {
        Instant now = Instant.now();
        String image = "https://placehold.co/1200x630/png?text=" + Slugs.slugify(title);
        return new CampaignRequest(org, title, shortDescription, DEMO_NOTE + shortDescription + " " + solution, image,
                List.of(image + "-gallery-1", image + "-gallery-2"), category, beneficiaryDescription, vnd(target), "VND", vnd(minimum),
                now.minus(20, ChronoUnit.DAYS), now.plus(150, ChronoUnit.DAYS), location, beneficiaries, problem, solution, impact, budget);
    }

    private UUID milestone(AuthUser creator, UUID campaignId, String title, long amount, int startDay, int dueDay, String conditions, String deliverables) {
        Instant now = Instant.now();
        return milestoneService.create(creator, campaignId, new MilestoneRequest(title, DEMO_NOTE + title, vnd(amount),
                now.plus(startDay, ChronoUnit.DAYS), now.plus(dueDay, ChronoUnit.DAYS), conditions, deliverables)).id();
    }

    private UUID donate(AuthUser donor, UUID campaignId, long amount, boolean anonymous, String message) {
        return donationService.create(donor, campaignId, new CreateDonationRequest(vnd(amount), "VND", PaymentMethod.SIMULATED_BANK_TRANSFER, anonymous, message), null).id();
    }

    private UUID confirmed(AuthUser admin, AuthUser donor, UUID campaignId, long amount, boolean anonymous, String message) {
        UUID id = donate(donor, campaignId, amount, anonymous, message);
        donationService.confirm(admin, id, null);
        return id;
    }

    private UUID evidence(AuthUser creator, UUID milestoneId, EvidenceType type, String title, String file) {
        return evidenceService.submit(creator, milestoneId, null, new EvidenceRequest(type, title,
                DEMO_NOTE + "Simulated file reference; no real document exists.", file, sha256(file), null)).id();
    }

    private static BudgetItem budget(String item, long amount) {
        return new BudgetItem(item, vnd(amount), "Planned expense (simulated)");
    }

    private static BigDecimal vnd(long amount) {
        return BigDecimal.valueOf(amount).setScale(2);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
