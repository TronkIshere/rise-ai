// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {AccessControl} from "@openzeppelin/contracts/access/AccessControl.sol";
import {Pausable} from "@openzeppelin/contracts/utils/Pausable.sol";
import {ReentrancyGuard} from "@openzeppelin/contracts/utils/ReentrancyGuard.sol";
import {IERC20} from "@openzeppelin/contracts/token/ERC20/IERC20.sol";
import {SafeERC20} from "@openzeppelin/contracts/token/ERC20/utils/SafeERC20.sol";

/// @notice On-chain form of the Rise AI EscrowEngine: one shared immutable contract, balances isolated per campaign.
/// The backend orchestrates through OPERATOR_ROLE, which cannot move funds out. Value leaves only through APPROVER_ROLE
/// (human-held multisig or KMS, ADR-005) and only to the campaign's fixed beneficiary or the simulation treasury.
/// Monitoring and AI hold no role. Amounts are simulated; identifiers are backend UUIDs encoded as bytes32.
/// GUARDIAN_ROLE is an independent party outside the platform: it can hold a milestone's releases directly on-chain, attest the
/// bank reserves backing the escrow, and nothing else. The platform can neither lift its holds early nor revoke it.
contract CampaignEscrow is AccessControl, Pausable, ReentrancyGuard {
    using SafeERC20 for IERC20;

    bytes32 public constant OPERATOR_ROLE = keccak256("OPERATOR_ROLE");
    bytes32 public constant APPROVER_ROLE = keccak256("APPROVER_ROLE");
    bytes32 public constant PAUSER_ROLE = keccak256("PAUSER_ROLE");
    bytes32 public constant GUARDIAN_ROLE = keccak256("GUARDIAN_ROLE");

    uint64 public constant MAX_RELEASE_DELAY = 30 days;
    uint256 public constant MAX_MILESTONES = 64;
    /// @notice After this long anyone may lift a guardian hold, so a lost guardian key cannot freeze a milestone forever.
    uint64 public constant GUARDIAN_HOLD_MAX = 14 days;

    enum EscrowStatus {
        None,
        Active,
        Frozen,
        Settlement
    }

    enum ReservationStatus {
        None,
        Reserved,
        Queued,
        Released,
        Cancelled
    }

    enum RefundStatus {
        None,
        Reserved,
        Cancelled,
        Recorded
    }

    enum EvidenceStatus {
        None,
        Submitted,
        Accepted,
        Rejected
    }

    struct Campaign {
        EscrowStatus status;
        address beneficiary;
        uint64 releaseDelay;
        uint256 confirmed;
        uint256 available;
        uint256 reserved;
        uint256 refundReserved;
        uint256 disbursed;
        uint256 refunded;
        uint256 adjustmentCredits;
        uint256 adjustmentDebits;
    }

    struct Milestone {
        uint256 planned;
        uint256 reserved;
        uint256 released;
        uint32 openDisputes;
        uint32 acceptedEvidence;
    }

    struct Reservation {
        ReservationStatus status;
        bytes32 milestoneId;
        uint256 amount;
        address reservedBy;
        uint64 releaseAt;
    }

    struct Contribution {
        uint256 amount;
        uint256 refunded;
        uint256 refundReserved;
    }

    struct Refund {
        RefundStatus status;
        bytes32 donationId;
        uint256 amount;
    }

    struct Evidence {
        EvidenceStatus status;
        bytes32 milestoneId;
        bytes32 contentHash;
        uint64 anchoredAt;
    }

    struct Dispute {
        bool open;
        bool exists;
        bytes32 milestoneId;
    }

    struct GuardianHold {
        bool open;
        bool exists;
        bytes32 milestoneId;
        uint64 placedAt;
    }

    /// @dev bankBalance is what the guardian saw on the escrow bank account; heldAtAttestation is totalHeld at that moment.
    struct ReserveAttestation {
        uint64 period;
        uint64 attestedAt;
        address attester;
        uint256 bankBalance;
        uint256 heldAtAttestation;
        bytes32 statementHash;
    }

    /// @dev bankReferenceHash identifies the off-chain bank transfer that paid out a release; the beneficiary confirms receiving it.
    struct Payout {
        bytes32 bankReferenceHash;
        uint64 recordedAt;
        uint64 confirmedAt;
    }

    event CampaignOpened(bytes32 indexed campaignId, address indexed beneficiary, uint256 target, uint64 releaseDelay, uint256 milestoneCount);
    event EscrowStatusChanged(bytes32 indexed campaignId, EscrowStatus previous, EscrowStatus current);
    event ContributionConfirmed(bytes32 indexed campaignId, bytes32 indexed donationId, uint256 amount);
    event FundsReserved(bytes32 indexed campaignId, bytes32 indexed milestoneId, bytes32 indexed requestId, uint256 amount);
    event ReservationCancelled(bytes32 indexed campaignId, bytes32 indexed milestoneId, bytes32 indexed requestId, uint256 amount);
    event ReleaseQueued(bytes32 indexed campaignId, bytes32 indexed milestoneId, bytes32 indexed requestId, uint256 amount, uint64 releaseAt);
    event FundsReleased(bytes32 indexed campaignId, bytes32 indexed milestoneId, bytes32 indexed requestId, uint256 amount, address beneficiary);
    event RefundReserved(bytes32 indexed campaignId, bytes32 indexed donationId, bytes32 indexed refundId, uint256 amount);
    event RefundReservationReleased(bytes32 indexed campaignId, bytes32 indexed donationId, bytes32 indexed refundId, uint256 amount);
    event RefundRecorded(bytes32 indexed campaignId, bytes32 indexed donationId, bytes32 indexed refundId, uint256 amount);
    event SettlementRefundRecorded(bytes32 indexed campaignId, bytes32 indexed settlementId, bytes32 indexed donationId, uint256 amount);
    event AdjustmentApplied(bytes32 indexed campaignId, bytes32 indexed adjustmentId, bool credit, uint256 amount);
    event DisputeOpened(bytes32 indexed campaignId, bytes32 indexed milestoneId, bytes32 indexed disputeId);
    event DisputeClosed(bytes32 indexed campaignId, bytes32 indexed milestoneId, bytes32 indexed disputeId);
    /// @dev `priorEvidenceId` is the first evidence that used this hash, or zero. Reuse can be legitimate; it only prompts review.
    event EvidenceAnchored(
        bytes32 indexed campaignId, bytes32 indexed milestoneId, bytes32 indexed evidenceId, bytes32 contentHash, bytes32 priorEvidenceId
    );
    event EvidenceDecided(bytes32 indexed campaignId, bytes32 indexed milestoneId, bytes32 indexed evidenceId, bool accepted);
    /// @dev `reasonHash` points to the guardian's public statement, kept off-chain.
    event GuardianHoldPlaced(
        bytes32 indexed campaignId, bytes32 indexed milestoneId, bytes32 indexed holdId, address guardian, bytes32 reasonHash, uint64 expiresAt
    );
    event GuardianHoldLifted(bytes32 indexed campaignId, bytes32 indexed milestoneId, bytes32 indexed holdId, address by, bool expired);
    event ReservesAttested(uint64 indexed period, address indexed attester, uint256 bankBalance, uint256 held, bytes32 statementHash);
    event PayoutRecorded(bytes32 indexed campaignId, bytes32 indexed requestId, bytes32 bankReferenceHash);
    event ReceiptConfirmed(bytes32 indexed campaignId, bytes32 indexed requestId, address indexed beneficiary);

    error BadTerms();
    error BadAmount();
    error ZeroHash();
    error UnknownCampaign();
    error UnknownMilestone();
    error UnknownRecord();
    error AlreadyRecorded();
    error InvalidRecordStatus();
    error InvalidEscrowStatus(EscrowStatus current);
    error InsufficientAvailable();
    error ExceedsPlanned();
    error ExceedsRefundable();
    error OutstandingReservations();
    error MissingAcceptedEvidence();
    error MilestoneDisputed();
    error SameActor();
    error ReleaseNotDue(uint64 releaseAt);
    error InvariantViolated();
    error MilestoneHeld();
    error HoldNotExpired(uint64 expiresAt);
    error GuardianConflict();
    error NotBeneficiary();

    IERC20 public immutable token;
    address public immutable treasury;
    uint64 public immutable minReleaseDelay;

    /// @notice Sum of every campaign's held balance. Equals this contract's token balance unless tokens were sent to it directly.
    uint256 public totalHeld;

    mapping(bytes32 campaignId => Campaign) private campaigns;
    mapping(bytes32 campaignId => mapping(bytes32 milestoneId => Milestone)) private milestones;
    mapping(bytes32 campaignId => mapping(bytes32 milestoneId => bool)) private milestoneExists;
    mapping(bytes32 campaignId => mapping(bytes32 requestId => Reservation)) private reservations;
    mapping(bytes32 campaignId => mapping(bytes32 donationId => Contribution)) private contributions;
    mapping(bytes32 campaignId => mapping(bytes32 refundId => Refund)) private refunds;
    mapping(bytes32 campaignId => mapping(bytes32 evidenceId => Evidence)) private evidence;
    mapping(bytes32 campaignId => mapping(bytes32 disputeId => Dispute)) private disputes;
    mapping(bytes32 campaignId => mapping(bytes32 key => bool)) private settlementRecorded;
    mapping(bytes32 campaignId => mapping(bytes32 adjustmentId => bool)) private adjustmentRecorded;
    mapping(bytes32 contentHash => bytes32 evidenceId) private firstEvidenceForHash;
    mapping(bytes32 campaignId => mapping(bytes32 holdId => GuardianHold)) private holds;
    mapping(bytes32 campaignId => mapping(bytes32 milestoneId => uint32)) private openHolds;
    mapping(bytes32 campaignId => mapping(bytes32 requestId => Payout)) private payouts;

    ReserveAttestation private latestReserves;

    /// @notice The guardian administers its own role: the platform's admin can neither grant nor revoke it.
    constructor(address admin, IERC20 token_, address treasury_, uint64 minReleaseDelay_, address guardian) {
        if (admin == address(0) || address(token_) == address(0) || treasury_ == address(0)) revert BadTerms();
        if (guardian == address(0) || guardian == admin || guardian == treasury_) revert GuardianConflict();
        if (minReleaseDelay_ > MAX_RELEASE_DELAY) revert BadTerms();
        token = token_;
        treasury = treasury_;
        minReleaseDelay = minReleaseDelay_;
        _grantRole(DEFAULT_ADMIN_ROLE, admin);
        _setRoleAdmin(GUARDIAN_ROLE, GUARDIAN_ROLE);
        _grantRole(GUARDIAN_ROLE, guardian);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Campaign lifecycle
    // ---------------------------------------------------------------------------------------------------------------

    /// @notice Opens the escrow when a campaign is published. Milestone budgets are final by then, and the beneficiary is fixed.
    function openCampaign(
        bytes32 campaignId,
        address beneficiary,
        uint256 target,
        uint64 releaseDelay,
        bytes32[] calldata milestoneIds,
        uint256[] calldata planned
    ) external onlyRole(OPERATOR_ROLE) {
        if (campaignId == bytes32(0) || beneficiary == address(0) || beneficiary == treasury || beneficiary == address(this)) revert BadTerms();
        if (campaigns[campaignId].status != EscrowStatus.None) revert AlreadyRecorded();
        if (releaseDelay < minReleaseDelay || releaseDelay > MAX_RELEASE_DELAY) revert BadTerms();
        uint256 count = milestoneIds.length;
        if (count == 0 || count > MAX_MILESTONES || count != planned.length) revert BadTerms();

        uint256 sum;
        for (uint256 i = 0; i < count; i++) {
            bytes32 id = milestoneIds[i];
            if (id == bytes32(0) || planned[i] == 0 || milestoneExists[campaignId][id]) revert BadTerms();
            milestoneExists[campaignId][id] = true;
            milestones[campaignId][id].planned = planned[i];
            sum += planned[i];
        }
        if (sum != target) revert BadTerms();

        Campaign storage c = campaigns[campaignId];
        c.status = EscrowStatus.Active;
        c.beneficiary = beneficiary;
        c.releaseDelay = releaseDelay;
        emit CampaignOpened(campaignId, beneficiary, target, releaseDelay, count);
    }

    /// @notice ACTIVE and FROZEN alternate; SETTLEMENT is final and needs every disbursement reservation closed.
    function changeStatus(bytes32 campaignId, EscrowStatus next) external onlyRole(OPERATOR_ROLE) {
        Campaign storage c = _campaign(campaignId);
        EscrowStatus current = c.status;
        bool allowed = (current == EscrowStatus.Active && next == EscrowStatus.Frozen)
            || (current == EscrowStatus.Frozen && next == EscrowStatus.Active)
            || ((current == EscrowStatus.Active || current == EscrowStatus.Frozen) && next == EscrowStatus.Settlement);
        if (!allowed) revert InvalidEscrowStatus(current);
        if (next == EscrowStatus.Settlement && c.reserved != 0) revert OutstandingReservations();
        c.status = next;
        emit EscrowStatusChanged(campaignId, current, next);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Contributions: written when an admin confirms a donation, never when it is created
    // ---------------------------------------------------------------------------------------------------------------

    function recordContribution(bytes32 campaignId, bytes32 donationId, uint256 amount)
        external
        onlyRole(OPERATOR_ROLE)
        whenNotPaused
        nonReentrant
    {
        Campaign storage c = _active(campaignId);
        if (amount == 0 || donationId == bytes32(0)) revert BadAmount();
        Contribution storage d = contributions[campaignId][donationId];
        if (d.amount != 0) revert AlreadyRecorded();
        d.amount = amount;
        c.confirmed += amount;
        c.available += amount;
        totalHeld += amount;
        _checkIdentity(c);
        emit ContributionConfirmed(campaignId, donationId, amount);
        token.safeTransferFrom(treasury, address(this), amount);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Evidence anchoring: proves when a hash was recorded, not that spending was legitimate
    // ---------------------------------------------------------------------------------------------------------------

    function anchorEvidence(bytes32 campaignId, bytes32 milestoneId, bytes32 evidenceId, bytes32 contentHash)
        external
        onlyRole(OPERATOR_ROLE)
    {
        _campaign(campaignId);
        _milestone(campaignId, milestoneId);
        if (contentHash == bytes32(0) || evidenceId == bytes32(0)) revert ZeroHash();
        Evidence storage e = evidence[campaignId][evidenceId];
        if (e.status != EvidenceStatus.None) revert AlreadyRecorded();
        e.status = EvidenceStatus.Submitted;
        e.milestoneId = milestoneId;
        e.contentHash = contentHash;
        e.anchoredAt = uint64(block.timestamp);

        bytes32 prior = firstEvidenceForHash[contentHash];
        if (prior == bytes32(0)) firstEvidenceForHash[contentHash] = evidenceId;
        emit EvidenceAnchored(campaignId, milestoneId, evidenceId, contentHash, prior);
    }

    function decideEvidence(bytes32 campaignId, bytes32 evidenceId, bool accepted) external onlyRole(OPERATOR_ROLE) {
        Evidence storage e = evidence[campaignId][evidenceId];
        if (e.status == EvidenceStatus.None) revert UnknownRecord();
        if (e.status != EvidenceStatus.Submitted) revert InvalidRecordStatus();
        e.status = accepted ? EvidenceStatus.Accepted : EvidenceStatus.Rejected;
        if (accepted) milestones[campaignId][e.milestoneId].acceptedEvidence += 1;
        emit EvidenceDecided(campaignId, e.milestoneId, evidenceId, accepted);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Disputes: an open dispute on a milestone stops its releases
    // ---------------------------------------------------------------------------------------------------------------

    function openDispute(bytes32 campaignId, bytes32 milestoneId, bytes32 disputeId) external onlyRole(OPERATOR_ROLE) {
        _campaign(campaignId);
        Milestone storage m = _milestone(campaignId, milestoneId);
        if (disputeId == bytes32(0)) revert BadTerms();
        Dispute storage d = disputes[campaignId][disputeId];
        if (d.exists) revert AlreadyRecorded();
        d.exists = true;
        d.open = true;
        d.milestoneId = milestoneId;
        m.openDisputes += 1;
        emit DisputeOpened(campaignId, milestoneId, disputeId);
    }

    function closeDispute(bytes32 campaignId, bytes32 disputeId) external onlyRole(OPERATOR_ROLE) {
        Dispute storage d = disputes[campaignId][disputeId];
        if (!d.exists) revert UnknownRecord();
        if (!d.open) revert InvalidRecordStatus();
        d.open = false;
        milestones[campaignId][d.milestoneId].openDisputes -= 1;
        emit DisputeClosed(campaignId, d.milestoneId, disputeId);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Disbursement: reserve, queue (human approver), public objection window, execute
    // ---------------------------------------------------------------------------------------------------------------

    function reserve(bytes32 campaignId, bytes32 milestoneId, bytes32 requestId, uint256 amount)
        external
        onlyRole(OPERATOR_ROLE)
        whenNotPaused
    {
        Campaign storage c = _active(campaignId);
        Milestone storage m = _milestone(campaignId, milestoneId);
        if (amount == 0 || requestId == bytes32(0)) revert BadAmount();
        Reservation storage r = reservations[campaignId][requestId];
        if (r.status != ReservationStatus.None) revert AlreadyRecorded();
        if (c.available < amount) revert InsufficientAvailable();
        if (m.reserved + m.released + amount > m.planned) revert ExceedsPlanned();

        r.status = ReservationStatus.Reserved;
        r.milestoneId = milestoneId;
        r.amount = amount;
        r.reservedBy = msg.sender;
        c.available -= amount;
        c.reserved += amount;
        m.reserved += amount;
        _checkIdentity(c);
        emit FundsReserved(campaignId, milestoneId, requestId, amount);
    }

    /// @notice Also cancels a queued release, which is how an upheld dispute or a rejection stops it during the window.
    function cancelReservation(bytes32 campaignId, bytes32 requestId) external onlyRole(OPERATOR_ROLE) {
        Campaign storage c = _campaign(campaignId);
        Reservation storage r = reservations[campaignId][requestId];
        if (r.status == ReservationStatus.None) revert UnknownRecord();
        if (r.status != ReservationStatus.Reserved && r.status != ReservationStatus.Queued) revert InvalidRecordStatus();
        Milestone storage m = milestones[campaignId][r.milestoneId];
        uint256 amount = r.amount;
        r.status = ReservationStatus.Cancelled;
        c.reserved -= amount;
        c.available += amount;
        m.reserved -= amount;
        _checkIdentity(c);
        emit ReservationCancelled(campaignId, r.milestoneId, requestId, amount);
    }

    /// @notice A human approver starts the objection window. Needs accepted evidence and no open dispute on the milestone.
    function queueRelease(bytes32 campaignId, bytes32 requestId) external onlyRole(APPROVER_ROLE) whenNotPaused {
        Campaign storage c = _active(campaignId);
        Reservation storage r = reservations[campaignId][requestId];
        if (r.status == ReservationStatus.None) revert UnknownRecord();
        if (r.status != ReservationStatus.Reserved) revert InvalidRecordStatus();
        if (r.reservedBy == msg.sender) revert SameActor();
        Milestone storage m = milestones[campaignId][r.milestoneId];
        if (m.acceptedEvidence == 0) revert MissingAcceptedEvidence();
        if (m.openDisputes != 0) revert MilestoneDisputed();
        if (openHolds[campaignId][r.milestoneId] != 0) revert MilestoneHeld();

        r.status = ReservationStatus.Queued;
        r.releaseAt = uint64(block.timestamp) + c.releaseDelay;
        emit ReleaseQueued(campaignId, r.milestoneId, requestId, r.amount, r.releaseAt);
    }

    /// @notice Anyone may execute once the window has passed. The funds can only reach the campaign's fixed beneficiary.
    function executeRelease(bytes32 campaignId, bytes32 requestId) external whenNotPaused nonReentrant {
        Campaign storage c = _active(campaignId);
        Reservation storage r = reservations[campaignId][requestId];
        if (r.status == ReservationStatus.None) revert UnknownRecord();
        if (r.status != ReservationStatus.Queued) revert InvalidRecordStatus();
        if (block.timestamp < r.releaseAt) revert ReleaseNotDue(r.releaseAt);
        Milestone storage m = milestones[campaignId][r.milestoneId];
        if (m.openDisputes != 0) revert MilestoneDisputed();
        if (openHolds[campaignId][r.milestoneId] != 0) revert MilestoneHeld();

        uint256 amount = r.amount;
        address to = c.beneficiary;
        r.status = ReservationStatus.Released;
        c.reserved -= amount;
        c.disbursed += amount;
        m.reserved -= amount;
        m.released += amount;
        totalHeld -= amount;
        _checkIdentity(c);
        emit FundsReleased(campaignId, r.milestoneId, requestId, amount, to);
        token.safeTransfer(to, amount);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Payout receipts: the platform records the bank transfer of a release, and only the beneficiary can confirm receiving it
    // ---------------------------------------------------------------------------------------------------------------

    function recordPayout(bytes32 campaignId, bytes32 requestId, bytes32 bankReferenceHash) external onlyRole(OPERATOR_ROLE) {
        _campaign(campaignId);
        if (bankReferenceHash == bytes32(0)) revert ZeroHash();
        Reservation storage r = reservations[campaignId][requestId];
        if (r.status == ReservationStatus.None) revert UnknownRecord();
        if (r.status != ReservationStatus.Released) revert InvalidRecordStatus();
        Payout storage p = payouts[campaignId][requestId];
        if (p.recordedAt != 0) revert AlreadyRecorded();
        p.bankReferenceHash = bankReferenceHash;
        p.recordedAt = uint64(block.timestamp);
        emit PayoutRecorded(campaignId, requestId, bankReferenceHash);
    }

    function confirmReceipt(bytes32 campaignId, bytes32 requestId) external {
        Campaign storage c = _campaign(campaignId);
        if (msg.sender != c.beneficiary) revert NotBeneficiary();
        Payout storage p = payouts[campaignId][requestId];
        if (p.recordedAt == 0) revert UnknownRecord();
        if (p.confirmedAt != 0) revert AlreadyRecorded();
        p.confirmedAt = uint64(block.timestamp);
        emit ReceiptConfirmed(campaignId, requestId, msg.sender);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Guardian: an independent party that can hold releases and attest reserves, and cannot move funds
    // ---------------------------------------------------------------------------------------------------------------

    /// @notice Holds every release of a milestone until the guardian lifts it, or until GUARDIAN_HOLD_MAX has passed.
    function placeHold(bytes32 campaignId, bytes32 milestoneId, bytes32 holdId, bytes32 reasonHash) external onlyRole(GUARDIAN_ROLE) {
        _campaign(campaignId);
        _milestone(campaignId, milestoneId);
        if (holdId == bytes32(0)) revert BadTerms();
        GuardianHold storage h = holds[campaignId][holdId];
        if (h.exists) revert AlreadyRecorded();
        h.exists = true;
        h.open = true;
        h.milestoneId = milestoneId;
        h.placedAt = uint64(block.timestamp);
        openHolds[campaignId][milestoneId] += 1;
        emit GuardianHoldPlaced(campaignId, milestoneId, holdId, msg.sender, reasonHash, h.placedAt + GUARDIAN_HOLD_MAX);
    }

    /// @notice A guardian may lift a hold at any time; anyone may lift it once it has expired.
    function liftHold(bytes32 campaignId, bytes32 holdId) external {
        GuardianHold storage h = holds[campaignId][holdId];
        if (!h.exists) revert UnknownRecord();
        if (!h.open) revert InvalidRecordStatus();
        uint64 expiresAt = h.placedAt + GUARDIAN_HOLD_MAX;
        bool expired = block.timestamp >= expiresAt;
        if (!expired && !hasRole(GUARDIAN_ROLE, msg.sender)) revert HoldNotExpired(expiresAt);
        h.open = false;
        openHolds[campaignId][h.milestoneId] -= 1;
        emit GuardianHoldLifted(campaignId, h.milestoneId, holdId, msg.sender, expired);
    }

    /// @notice The guardian states what it saw on the escrow bank account. A balance below totalHeld is a public reserve gap.
    function attestReserves(uint64 period, uint256 bankBalance, bytes32 statementHash) external onlyRole(GUARDIAN_ROLE) {
        if (statementHash == bytes32(0)) revert ZeroHash();
        if (period <= latestReserves.period) revert BadTerms();
        latestReserves = ReserveAttestation(period, uint64(block.timestamp), msg.sender, bankBalance, totalHeld, statementHash);
        emit ReservesAttested(period, msg.sender, bankBalance, totalHeld, statementHash);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Refunds: never blocked by a pause or a frozen campaign; donors hold no wallet, so value returns to the treasury
    // ---------------------------------------------------------------------------------------------------------------

    function reserveRefund(bytes32 campaignId, bytes32 donationId, bytes32 refundId, uint256 amount) external onlyRole(OPERATOR_ROLE) {
        Campaign storage c = _campaign(campaignId);
        if (amount == 0 || refundId == bytes32(0)) revert BadAmount();
        Refund storage rf = refunds[campaignId][refundId];
        if (rf.status != RefundStatus.None) revert AlreadyRecorded();
        Contribution storage d = contributions[campaignId][donationId];
        if (d.amount == 0) revert UnknownRecord();
        if (amount > d.amount - d.refunded - d.refundReserved) revert ExceedsRefundable();
        if (c.available < amount) revert InsufficientAvailable();

        rf.status = RefundStatus.Reserved;
        rf.donationId = donationId;
        rf.amount = amount;
        d.refundReserved += amount;
        c.available -= amount;
        c.refundReserved += amount;
        _checkIdentity(c);
        emit RefundReserved(campaignId, donationId, refundId, amount);
    }

    function releaseRefundReservation(bytes32 campaignId, bytes32 refundId) external onlyRole(OPERATOR_ROLE) {
        Campaign storage c = _campaign(campaignId);
        Refund storage rf = _reservedRefund(campaignId, refundId);
        uint256 amount = rf.amount;
        rf.status = RefundStatus.Cancelled;
        contributions[campaignId][rf.donationId].refundReserved -= amount;
        c.refundReserved -= amount;
        c.available += amount;
        _checkIdentity(c);
        emit RefundReservationReleased(campaignId, rf.donationId, refundId, amount);
    }

    function recordRefund(bytes32 campaignId, bytes32 refundId) external onlyRole(APPROVER_ROLE) nonReentrant {
        Campaign storage c = _campaign(campaignId);
        Refund storage rf = _reservedRefund(campaignId, refundId);
        uint256 amount = rf.amount;
        Contribution storage d = contributions[campaignId][rf.donationId];
        rf.status = RefundStatus.Recorded;
        d.refundReserved -= amount;
        d.refunded += amount;
        c.refundReserved -= amount;
        c.refunded += amount;
        totalHeld -= amount;
        _checkIdentity(c);
        emit RefundRecorded(campaignId, rf.donationId, refundId, amount);
        token.safeTransfer(treasury, amount);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Settlement and adjustments: proposal and dual control stay in the backend (ADR-006); only the approved effect lands here
    // ---------------------------------------------------------------------------------------------------------------

    function recordSettlementRefund(bytes32 campaignId, bytes32 settlementId, bytes32 donationId, uint256 amount)
        external
        onlyRole(APPROVER_ROLE)
        nonReentrant
    {
        Campaign storage c = _campaign(campaignId);
        if (c.status != EscrowStatus.Settlement) revert InvalidEscrowStatus(c.status);
        if (amount == 0) revert BadAmount();
        bytes32 key = keccak256(abi.encode(settlementId, donationId));
        if (settlementRecorded[campaignId][key]) revert AlreadyRecorded();
        Contribution storage d = contributions[campaignId][donationId];
        if (d.amount == 0) revert UnknownRecord();
        if (amount > d.amount - d.refunded - d.refundReserved) revert ExceedsRefundable();
        if (c.available < amount) revert InsufficientAvailable();

        settlementRecorded[campaignId][key] = true;
        d.refunded += amount;
        c.available -= amount;
        c.refunded += amount;
        totalHeld -= amount;
        _checkIdentity(c);
        emit SettlementRefundRecorded(campaignId, settlementId, donationId, amount);
        token.safeTransfer(treasury, amount);
    }

    function recordAdjustment(bytes32 campaignId, bytes32 adjustmentId, bool credit, uint256 amount)
        external
        onlyRole(APPROVER_ROLE)
        nonReentrant
    {
        Campaign storage c = _campaign(campaignId);
        if (amount == 0 || adjustmentId == bytes32(0)) revert BadAmount();
        if (adjustmentRecorded[campaignId][adjustmentId]) revert AlreadyRecorded();
        adjustmentRecorded[campaignId][adjustmentId] = true;

        if (credit) {
            c.adjustmentCredits += amount;
            c.available += amount;
            totalHeld += amount;
            _checkIdentity(c);
            emit AdjustmentApplied(campaignId, adjustmentId, true, amount);
            token.safeTransferFrom(treasury, address(this), amount);
        } else {
            if (c.available < amount) revert InsufficientAvailable();
            c.adjustmentDebits += amount;
            c.available -= amount;
            totalHeld -= amount;
            _checkIdentity(c);
            emit AdjustmentApplied(campaignId, adjustmentId, false, amount);
            token.safeTransfer(treasury, amount);
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Administration: a pause stops contributions, reservations and releases, never refunds or cancellations
    // ---------------------------------------------------------------------------------------------------------------

    function pause() external onlyRole(PAUSER_ROLE) {
        _pause();
    }

    function unpause() external onlyRole(DEFAULT_ADMIN_ROLE) {
        _unpause();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Views
    // ---------------------------------------------------------------------------------------------------------------

    function getCampaign(bytes32 campaignId) external view returns (Campaign memory) {
        return campaigns[campaignId];
    }

    function getMilestone(bytes32 campaignId, bytes32 milestoneId) external view returns (Milestone memory) {
        return milestones[campaignId][milestoneId];
    }

    function getReservation(bytes32 campaignId, bytes32 requestId) external view returns (Reservation memory) {
        return reservations[campaignId][requestId];
    }

    function getContribution(bytes32 campaignId, bytes32 donationId) external view returns (Contribution memory) {
        return contributions[campaignId][donationId];
    }

    function getRefund(bytes32 campaignId, bytes32 refundId) external view returns (Refund memory) {
        return refunds[campaignId][refundId];
    }

    function getEvidence(bytes32 campaignId, bytes32 evidenceId) external view returns (Evidence memory) {
        return evidence[campaignId][evidenceId];
    }

    function getGuardianHold(bytes32 campaignId, bytes32 holdId) external view returns (GuardianHold memory) {
        return holds[campaignId][holdId];
    }

    function openHoldCount(bytes32 campaignId, bytes32 milestoneId) external view returns (uint32) {
        return openHolds[campaignId][milestoneId];
    }

    function getPayout(bytes32 campaignId, bytes32 requestId) external view returns (Payout memory) {
        return payouts[campaignId][requestId];
    }

    function getReserveAttestation() external view returns (ReserveAttestation memory) {
        return latestReserves;
    }

    function heldBalance(bytes32 campaignId) public view returns (uint256) {
        Campaign storage c = campaigns[campaignId];
        return c.available + c.reserved + c.refundReserved;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Internals
    // ---------------------------------------------------------------------------------------------------------------

    function _campaign(bytes32 campaignId) private view returns (Campaign storage c) {
        c = campaigns[campaignId];
        if (c.status == EscrowStatus.None) revert UnknownCampaign();
    }

    function _active(bytes32 campaignId) private view returns (Campaign storage c) {
        c = _campaign(campaignId);
        if (c.status != EscrowStatus.Active) revert InvalidEscrowStatus(c.status);
    }

    function _milestone(bytes32 campaignId, bytes32 milestoneId) private view returns (Milestone storage) {
        if (!milestoneExists[campaignId][milestoneId]) revert UnknownMilestone();
        return milestones[campaignId][milestoneId];
    }

    function _reservedRefund(bytes32 campaignId, bytes32 refundId) private view returns (Refund storage rf) {
        rf = refunds[campaignId][refundId];
        if (rf.status == RefundStatus.None) revert UnknownRecord();
        if (rf.status != RefundStatus.Reserved) revert InvalidRecordStatus();
    }

    /// @dev The guardian never holds a platform role, and no platform signer can become guardian.
    function _grantRole(bytes32 role, address account) internal override returns (bool) {
        if (role == GUARDIAN_ROLE) {
            if (
                hasRole(OPERATOR_ROLE, account) || hasRole(APPROVER_ROLE, account) || hasRole(PAUSER_ROLE, account)
                    || hasRole(DEFAULT_ADMIN_ROLE, account)
            ) revert GuardianConflict();
        } else if (hasRole(GUARDIAN_ROLE, account)) {
            revert GuardianConflict();
        }
        return super._grantRole(role, account);
    }

    /// @dev Same identity as the backend's verifyInvariants and the ck_escrow_balance_identity CHECK.
    function _checkIdentity(Campaign storage c) private view {
        if (c.confirmed + c.adjustmentCredits != c.available + c.reserved + c.refundReserved + c.disbursed + c.refunded + c.adjustmentDebits) {
            revert InvariantViolated();
        }
    }
}
