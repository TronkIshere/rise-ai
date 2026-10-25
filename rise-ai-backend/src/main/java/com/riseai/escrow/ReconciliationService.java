package com.riseai.escrow;

import com.riseai.audit.AuditService;
import com.riseai.common.Codes;
import com.riseai.common.exception.ApplicationException;
import com.riseai.escrow.EscrowDtos.ReconciliationReport;
import com.riseai.escrow.EscrowDtos.ReconciliationRunResponse;
import com.riseai.outbox.DomainEventPublisher;
import com.riseai.outbox.RiseEvents;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class ReconciliationService {
    private static final String ENTITY = "RECONCILIATION_RUN";
    private static final int MAX_ERROR = 500;

    private final EscrowQueryService escrowQueryService;
    private final ReconciliationRunRepository runRepository;
    private final AuditService auditService;
    private final DomainEventPublisher domainEvents;
    private final TransactionTemplate transactionTemplate;

    public ReconciliationService(EscrowQueryService escrowQueryService, ReconciliationRunRepository runRepository, AuditService auditService,
                                 DomainEventPublisher domainEvents, PlatformTransactionManager transactionManager) {
        this.escrowQueryService = escrowQueryService;
        this.runRepository = runRepository;
        this.auditService = auditService;
        this.domainEvents = domainEvents;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public ReconciliationRunResponse run(ReconciliationTrigger trigger, UUID actorId) {
        Instant startedAt = Instant.now();
        ReconciliationRun run = new ReconciliationRun();
        run.setRunCode(Codes.generate("REC"));
        run.setTriggerType(trigger);
        run.setTriggeredBy(actorId);
        run.setStartedAt(startedAt);
        try {
            List<ReconciliationReport> reports = escrowQueryService.reconcileAll();
            List<ReconciliationReport> findings = reports.stream().filter(r -> !r.consistent()).toList();
            run.setAccountsChecked(reports.size());
            run.setInconsistentAccounts(findings.size());
            run.setFindings(findings);
            run.setRunStatus(findings.isEmpty() ? ReconciliationRunStatus.CONSISTENT : ReconciliationRunStatus.INCONSISTENT);
            for (ReconciliationReport finding : findings) {
                log.error("ALERT escrow reconciliation mismatch run={} campaign={} account={} stored={} computed={}",
                        run.getRunCode(), finding.campaignId(), finding.escrowAccountId(), finding.stored(), finding.computedFromLedger());
            }
        } catch (RuntimeException ex) {
            log.error("ALERT escrow reconciliation run {} failed", run.getRunCode(), ex);
            run.setAccountsChecked(0);
            run.setInconsistentAccounts(0);
            run.setRunStatus(ReconciliationRunStatus.FAILED);
            String message = ex.getClass().getSimpleName() + (ex.getMessage() == null ? "" : ": " + ex.getMessage());
            run.setErrorMessage(message.length() > MAX_ERROR ? message.substring(0, MAX_ERROR) : message);
        }
        run.setFinishedAt(Instant.now());
        save(run);
        log.info("Escrow reconciliation run {} finished: status={} accounts={} inconsistent={}",
                run.getRunCode(), run.getRunStatus(), run.getAccountsChecked(), run.getInconsistentAccounts());
        return ReconciliationRunResponse.from(run);
    }

    @Transactional(readOnly = true)
    public Page<ReconciliationRunResponse> history(ReconciliationRunStatus status, Pageable pageable) {
        Page<ReconciliationRun> page = status == null ? runRepository.findAll(pageable) : runRepository.findByRunStatus(status, pageable);
        return page.map(ReconciliationRunResponse::from);
    }

    @Transactional(readOnly = true)
    public ReconciliationRunResponse get(UUID id) {
        return runRepository.findById(id).map(ReconciliationRunResponse::from).orElseThrow(() -> ApplicationException.notFound("Reconciliation run"));
    }

    private void save(ReconciliationRun run) {
        transactionTemplate.executeWithoutResult(status -> {
            runRepository.save(run);
            auditService.record(run.getTriggeredBy(), "RECONCILIATION_RUN_COMPLETED", ENTITY, run.getId(), null, run.getRunStatus().name(),
                    AuditService.meta("runCode", run.getRunCode(), "trigger", run.getTriggerType(), "accountsChecked", run.getAccountsChecked(),
                            "inconsistentAccounts", run.getInconsistentAccounts()));
            domainEvents.publish(RiseEvents.reconciliationCompleted(run));
        });
    }
}
