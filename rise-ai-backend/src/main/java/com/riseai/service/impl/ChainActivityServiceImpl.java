package com.riseai.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riseai.client.AvalancheDataApiClient;
import com.riseai.client.AvalancheDataApiClient.IndexedTransaction;
import com.riseai.client.AvalancheGatewayClient.DeploymentInfo;
import com.riseai.configuration.AvalancheEscrowProperties;
import com.riseai.dto.response.ChainActivityResponse;
import com.riseai.repository.ChainCommandRepository;
import com.riseai.service.ChainActivityService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Checks the escrow's activity as the Avalanche Data API indexed it against the platform's own records. A successful transaction
 * signed by a platform key that the backend never recorded is reported as UNRECORDED for review; it is a pattern, not a finding of
 * wrongdoing. Advisory and read-only: it never changes a record, a status or a release.
 */
@Service
public class ChainActivityServiceImpl implements ChainActivityService {
    static final long FUJI_CHAIN_ID = 43113;
    private static final int PAGE_SIZE = 25;
    private static final Duration FIRST_PAGE_CACHE = Duration.ofSeconds(30);

    private final AvalancheDataApiClient dataApi;
    private final GatewayDeploymentCache deployments;
    private final ChainCommandRepository commands;
    private final AvalancheEscrowProperties properties;
    private final Map<String, String> methodNames;
    private volatile CachedPage firstPage;

    private record CachedPage(String escrow, AvalancheDataApiClient.Page page, Instant fetchedAt) {
    }

    public ChainActivityServiceImpl(ObjectProvider<AvalancheDataApiClient> dataApi, GatewayDeploymentCache deployments, ChainCommandRepository commands,
                                    AvalancheEscrowProperties properties, ObjectMapper objectMapper) {
        this.dataApi = dataApi.getIfAvailable();
        this.deployments = deployments;
        this.commands = commands;
        this.properties = properties;
        this.methodNames = loadMethodNames(objectMapper);
    }

    /** selector (8 hex characters, no prefix) to function name, from the selectors exported with the contract ABI. */
    static Map<String, String> loadMethodNames(ObjectMapper objectMapper) {
        try (InputStream in = new ClassPathResource("chain/campaign-escrow-methods.json").getInputStream()) {
            Map<String, String> signatures = objectMapper.readValue(in, new TypeReference<>() {
            });
            Map<String, String> result = new HashMap<>();
            signatures.forEach((signature, selector) -> result.put(selector.toLowerCase(Locale.ROOT), signature.substring(0, signature.indexOf('('))));
            return Map.copyOf(result);
        } catch (IOException ex) {
            throw new IllegalStateException("CampaignEscrow method selectors are missing", ex);
        }
    }

    @Transactional(readOnly = true)
    @Override
    public ChainActivityResponse activity(String pageToken) {
        if (dataApi == null) {
            return ChainActivityResponse.offChain();
        }
        DeploymentInfo deployment = deployments.get();
        if (deployment == null || deployment.escrow() == null) {
            return ChainActivityResponse.unavailable(null);
        }
        String escrow = deployment.escrow().toLowerCase(Locale.ROOT);
        if (deployment.chainId() != FUJI_CHAIN_ID) {
            return ChainActivityResponse.unavailable(escrow);
        }
        AvalancheDataApiClient.Page page;
        try {
            page = page(escrow, pageToken);
        } catch (RuntimeException ex) {
            return ChainActivityResponse.unavailable(escrow);
        }
        Set<String> hashes = new HashSet<>();
        page.transactions().forEach(tx -> hashes.add(tx.txHash()));
        Set<String> recorded = hashes.isEmpty() ? Set.of() : new HashSet<>(commands.findRecordedTxHashes(hashes));
        String beneficiary = lower(properties.beneficiaryAddress());
        List<ChainActivityResponse.Item> items = new ArrayList<>();
        long unrecorded = 0;
        for (IndexedTransaction tx : page.transactions()) {
            String signer = signer(tx.from(), deployment, beneficiary);
            String check;
            if (!tx.success()) {
                check = "REVERTED";
            } else if (!"PLATFORM".equals(signer)) {
                check = "INDEPENDENT";
            } else if (recorded.contains(tx.txHash())) {
                check = "RECORDED";
            } else {
                check = "UNRECORDED";
                unrecorded++;
            }
            items.add(new ChainActivityResponse.Item(tx.txHash(), method(tx.methodHash()), signer, tx.from(), Instant.ofEpochSecond(tx.blockTimestamp()),
                    tx.blockNumber(), tx.success(), check, toBeneficiary(tx, beneficiary), properties.explorerTxUrl() + tx.txHash()));
        }
        return new ChainActivityResponse(true, true, "AVALANCHE_DATA_API", escrow, items, page.nextPageToken(), unrecorded);
    }

    private AvalancheDataApiClient.Page page(String escrow, String pageToken) {
        if (pageToken != null) {
            return dataApi.transactions(FUJI_CHAIN_ID, escrow, PAGE_SIZE, pageToken);
        }
        CachedPage cached = firstPage;
        if (cached != null && cached.escrow().equals(escrow) && cached.fetchedAt().plus(FIRST_PAGE_CACHE).isAfter(Instant.now())) {
            return cached.page();
        }
        AvalancheDataApiClient.Page page = dataApi.transactions(FUJI_CHAIN_ID, escrow, PAGE_SIZE, null);
        firstPage = new CachedPage(escrow, page, Instant.now());
        return page;
    }

    private static String signer(String from, DeploymentInfo deployment, String beneficiary) {
        if (from == null) {
            return "OTHER";
        }
        if (from.equals(lower(deployment.operator())) || from.equals(lower(deployment.approver()))) {
            return "PLATFORM";
        }
        if (from.equals(lower(deployment.guardian()))) {
            return "GUARDIAN";
        }
        return from.equals(beneficiary) ? "BENEFICIARY" : "OTHER";
    }

    private String method(String methodHash) {
        if (methodHash == null || methodHash.length() < 10) {
            return "unknown";
        }
        return methodNames.getOrDefault(methodHash.substring(2, 10), methodHash);
    }

    private static BigDecimal toBeneficiary(IndexedTransaction tx, String beneficiary) {
        BigDecimal total = BigDecimal.ZERO;
        for (AvalancheDataApiClient.TokenTransfer transfer : tx.transfers()) {
            if (beneficiary != null && beneficiary.equals(transfer.to()) && "rVND".equals(transfer.symbol()) && transfer.value() != null) {
                total = total.add(new BigDecimal(transfer.value()).movePointLeft(2));
            }
        }
        return total.signum() == 0 ? null : total;
    }

    private static String lower(String value) {
        return value == null ? null : Objects.requireNonNull(value).toLowerCase(Locale.ROOT);
    }
}
