package com.riseai.common;

import com.riseai.security.AuthUser;
import com.riseai.support.AbstractMySqlIntegrationTest;
import com.riseai.user.Role;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class ListQueryCountIntegrationTest extends AbstractMySqlIntegrationTest {
    @Autowired
    EntityManagerFactory entityManagerFactory;

    Statistics statistics;
    AuthUser admin;
    AuthUser creator;

    @BeforeEach
    void setUp() {
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        admin = user(Role.ADMIN);
        creator = user(Role.CAMPAIGN_CREATOR);
    }

    @Test
    void donationListsUseAConstantNumberOfStatementsRegardlessOfDistinctDonors() {
        ActiveCampaign small = activeCampaign(creator, admin, 100_000_000);
        ActiveCampaign large = activeCampaign(creator, admin, 100_000_000);
        confirmedDonation(user(Role.DONOR), admin, small.id(), 100_000);
        for (int i = 0; i < 25; i++) {
            confirmedDonation(user(Role.DONOR), admin, large.id(), 100_000);
        }

        long smallCount = statements(() -> donationService.publicList(null, small.id(), PageRequest.of(0, 50)).getContent().size());
        long largeCount = statements(() -> donationService.publicList(null, large.id(), PageRequest.of(0, 50)).getContent().size());
        long manageCount = statements(() -> donationService.manageList(admin, large.id(), null, PageRequest.of(0, 50)).getContent().size());

        assertThat(largeCount).isEqualTo(smallCount);
        assertThat(manageCount).isLessThanOrEqualTo(smallCount + 1);
        assertThat(largeCount).isLessThanOrEqualTo(5);
    }

    @Test
    void campaignSearchUsesAConstantNumberOfStatementsRegardlessOfPageSize() {
        for (int i = 0; i < 8; i++) {
            activeCampaign(user(Role.CAMPAIGN_CREATOR), admin, 1_000_000);
        }

        long one = statements(() -> campaignService.search(admin, null, null, null, null, PageRequest.of(0, 1)).getContent().size());
        long many = statements(() -> campaignService.search(admin, null, null, null, null, PageRequest.of(0, 7)).getContent().size());

        assertThat(many).isEqualTo(one);
        assertThat(many).isLessThanOrEqualTo(4);
    }

    private long statements(Supplier<Integer> call) {
        statistics.clear();
        assertThat(call.get()).isPositive();
        return statistics.getPrepareStatementCount();
    }
}
