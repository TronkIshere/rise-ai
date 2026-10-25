package com.riseai.support;

import com.riseai.dto.request.CampaignRequest;
import com.riseai.dto.request.CreateDisbursementRequest;
import com.riseai.dto.request.CreateDonationRequest;
import com.riseai.dto.request.EvidenceRequest;
import com.riseai.dto.request.MilestoneRequest;
import com.riseai.dto.request.OrganizationRequest;
import com.riseai.entity.User;
import com.riseai.enums.CampaignCategory;
import com.riseai.enums.EvidenceType;
import com.riseai.enums.OrganizationType;
import com.riseai.enums.Role;
import com.riseai.enums.UserStatus;
import com.riseai.exception.ApplicationException;
import com.riseai.exception.ErrorCode;
import com.riseai.repository.AuditLogRepository;
import com.riseai.repository.DisbursementRepository;
import com.riseai.repository.DonationRepository;
import com.riseai.repository.EscrowAccountRepository;
import com.riseai.repository.MilestoneRepository;
import com.riseai.repository.UserRepository;
import com.riseai.security.AuthUser;
import com.riseai.service.AdjustmentService;
import com.riseai.service.CampaignService;
import com.riseai.service.DisbursementService;
import com.riseai.service.DisputeService;
import com.riseai.service.DonationService;
import com.riseai.service.EscrowQueryService;
import com.riseai.service.EvidenceService;
import com.riseai.service.MilestoneService;
import com.riseai.service.OrganizationService;
import com.riseai.service.ReconciliationService;
import com.riseai.service.RefundService;
import com.riseai.service.SettlementService;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractMySqlIntegrationTest {
    protected static final String PASSWORD = "Password123";
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
            .withCommand("--log-bin-trust-function-creators=1");

    static {
        MYSQL.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired protected UserRepository userRepository;
    @Autowired protected PasswordEncoder passwordEncoder;
    @Autowired protected OrganizationService organizationService;
    @Autowired protected CampaignService campaignService;
    @Autowired protected MilestoneService milestoneService;
    @Autowired protected MilestoneRepository milestoneRepository;
    @Autowired protected DonationService donationService;
    @Autowired protected DonationRepository donationRepository;
    @Autowired protected EvidenceService evidenceService;
    @Autowired protected DisbursementService disbursementService;
    @Autowired protected DisbursementRepository disbursementRepository;
    @Autowired protected DisputeService disputeService;
    @Autowired protected RefundService refundService;
    @Autowired protected EscrowQueryService escrowQueryService;
    @Autowired protected AuditLogRepository auditLogRepository;
    @Autowired protected EscrowAccountRepository escrowAccountRepository;
    @Autowired protected SettlementService settlementService;
    @Autowired protected AdjustmentService adjustmentService;
    @Autowired protected ReconciliationService reconciliationService;
    @Autowired protected JdbcTemplate jdbcTemplate;

    protected record ActiveCampaign(UUID id, List<UUID> milestones) {
    }

    protected AuthUser user(Role... roles) {
        User user = new User();
        user.setEmail("u-" + UUID.randomUUID() + "@test.example");
        user.setFullName("Test User");
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setStatus(UserStatus.ACTIVE);
        user.getRoles().addAll(Set.of(roles));
        userRepository.saveAndFlush(user);
        return new AuthUser(user.getId(), user.getEmail(), Set.of(roles));
    }

    protected UUID verifiedOrganization(AuthUser creator, AuthUser admin) {
        UUID id = unverifiedOrganization(creator);
        organizationService.submitForVerification(creator, id);
        organizationService.verify(admin, id, "Test verification");
        return id;
    }

    protected UUID unverifiedOrganization(AuthUser creator) {
        return organizationService.create(creator, new OrganizationRequest("Org " + UUID.randomUUID(), "Test organization",
                OrganizationType.NGO, "org@test.example", null, null)).id();
    }

    protected CampaignRequest campaignRequest(UUID organizationId, long target) {
        Instant now = Instant.now();
        return new CampaignRequest(organizationId, "Campaign " + UUID.randomUUID(), "Short description", "Full description",
                null, null, CampaignCategory.EDUCATION, "Beneficiaries", vnd(target), "VND", vnd(10_000),
                now.minus(1, ChronoUnit.DAYS), now.plus(60, ChronoUnit.DAYS), "Test location", 10,
                "Problem", "Solution", "Impact", null);
    }

    protected MilestoneRequest milestoneRequest(long amount) {
        Instant now = Instant.now();
        return new MilestoneRequest("Milestone " + amount, null, vnd(amount), now, now.plus(30, ChronoUnit.DAYS), "Conditions", "Deliverables");
    }

    protected ActiveCampaign draftCampaign(AuthUser creator, UUID organizationId, long... milestoneAmounts) {
        long total = 0;
        for (long amount : milestoneAmounts) {
            total += amount;
        }
        UUID campaignId = campaignService.create(creator, campaignRequest(organizationId, total)).id();
        List<UUID> milestones = new ArrayList<>();
        for (long amount : milestoneAmounts) {
            milestones.add(milestoneService.create(creator, campaignId, milestoneRequest(amount)).id());
        }
        return new ActiveCampaign(campaignId, milestones);
    }

    protected ActiveCampaign activeCampaign(AuthUser creator, AuthUser admin, long... milestoneAmounts) {
        ActiveCampaign campaign = draftCampaign(creator, verifiedOrganization(creator, admin), milestoneAmounts);
        campaignService.submit(creator, campaign.id());
        campaignService.approve(admin, campaign.id(), "ok");
        campaignService.publish(creator, campaign.id());
        return campaign;
    }

    protected UUID pendingDonation(AuthUser donor, UUID campaignId, long amount) {
        return donationService.create(donor, campaignId, new CreateDonationRequest(vnd(amount), "VND", null, false, null), null).id();
    }

    protected UUID confirmedDonation(AuthUser donor, AuthUser admin, UUID campaignId, long amount) {
        UUID id = pendingDonation(donor, campaignId, amount);
        donationService.confirm(admin, id, null);
        return id;
    }

    protected UUID submittedEvidence(AuthUser creator, UUID milestoneId) {
        return evidenceService.submit(creator, milestoneId, null, new EvidenceRequest(EvidenceType.INVOICE, "Invoice", null,
                "sim://evidence/" + UUID.randomUUID() + ".pdf", null, null)).id();
    }

    protected UUID acceptedEvidence(AuthUser creator, AuthUser admin, UUID milestoneId) {
        UUID id = submittedEvidence(creator, milestoneId);
        evidenceService.review(admin, id, true, "ok");
        return id;
    }

    protected UUID requestFunds(AuthUser creator, UUID milestoneId, long amount) {
        return disbursementService.create(creator, milestoneId, new CreateDisbursementRequest(vnd(amount), "Test request")).id();
    }

    protected UUID approvedRequest(AuthUser creator, AuthUser admin, UUID milestoneId, long amount) {
        UUID id = requestFunds(creator, milestoneId, amount);
        disbursementService.startReview(admin, id);
        disbursementService.approve(admin, id, "ok");
        return id;
    }

    protected UUID releasedRequest(AuthUser creator, AuthUser admin, UUID milestoneId, long amount) {
        acceptedEvidence(creator, admin, milestoneId);
        UUID id = approvedRequest(creator, admin, milestoneId, amount);
        disbursementService.release(admin, id);
        return id;
    }

    protected void moveEndDateToPast(UUID campaignId) {
        jdbcTemplate.update("update campaigns set start_at = utc_timestamp(6) - interval 10 day, end_at = utc_timestamp(6) - interval 1 minute where id = ?",
                campaignId.toString());
    }

    protected static BigDecimal vnd(long amount) {
        return BigDecimal.valueOf(amount).setScale(2);
    }

    protected static void assertError(ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call).isInstanceOf(ApplicationException.class).extracting("errorCode").isEqualTo(expected);
    }
}
