// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {Test} from "forge-std/Test.sol";
import {IAccessControl} from "@openzeppelin/contracts/access/IAccessControl.sol";
import {Pausable} from "@openzeppelin/contracts/utils/Pausable.sol";
import {CampaignEscrow} from "../src/CampaignEscrow.sol";
import {RiseVND} from "../src/RiseVND.sol";

abstract contract CampaignEscrowBase is Test {
    uint256 internal constant T0 = 1_700_000_000;
    uint64 internal constant MIN_DELAY = 5 minutes;
    uint64 internal constant DELAY = 24 hours;
    bytes32 internal constant OPERATOR_ROLE = keccak256("OPERATOR_ROLE");
    bytes32 internal constant APPROVER_ROLE = keccak256("APPROVER_ROLE");

    bytes32 internal constant CAMPAIGN = keccak256("campaign-1");
    bytes32 internal constant M1 = keccak256("milestone-1");
    bytes32 internal constant M2 = keccak256("milestone-2");
    bytes32 internal constant DONATION = keccak256("donation-1");
    bytes32 internal constant REQUEST = keccak256("request-1");
    bytes32 internal constant REFUND = keccak256("refund-1");
    bytes32 internal constant EVIDENCE = keccak256("evidence-1");
    bytes32 internal constant FILE_HASH = keccak256("receipt.pdf");
    bytes32 internal constant DISPUTE = keccak256("dispute-1");

    uint256 internal constant PLANNED_1 = 30_000_000_00;
    uint256 internal constant PLANNED_2 = 50_000_000_00;
    uint256 internal constant TARGET = PLANNED_1 + PLANNED_2;
    uint256 internal constant DONATED = 60_000_000_00;
    uint256 internal constant RESERVE_AMOUNT = 10_000_000_00;

    RiseVND internal token;
    CampaignEscrow internal escrow;

    address internal admin = makeAddr("admin");
    address internal operator = makeAddr("operator");
    address internal approver = makeAddr("approver");
    address internal pauser = makeAddr("pauser");
    address internal treasury = makeAddr("treasury");
    address internal beneficiary = makeAddr("beneficiary");
    address internal stranger = makeAddr("stranger");
    address internal guardian = makeAddr("guardian");

    function setUp() public virtual {
        vm.warp(T0);
        token = new RiseVND(admin);
        escrow = new CampaignEscrow(admin, token, treasury, MIN_DELAY, guardian);
        vm.startPrank(admin);
        escrow.grantRole(OPERATOR_ROLE, operator);
        escrow.grantRole(APPROVER_ROLE, approver);
        escrow.grantRole(escrow.PAUSER_ROLE(), pauser);
        token.mint(treasury, 1_000_000_000_00);
        vm.stopPrank();
        vm.prank(treasury);
        token.approve(address(escrow), type(uint256).max);
    }

    function _ids() internal pure returns (bytes32[] memory ids, uint256[] memory planned) {
        ids = new bytes32[](2);
        planned = new uint256[](2);
        ids[0] = M1;
        ids[1] = M2;
        planned[0] = PLANNED_1;
        planned[1] = PLANNED_2;
    }

    function _open() internal {
        (bytes32[] memory ids, uint256[] memory planned) = _ids();
        vm.prank(operator);
        escrow.openCampaign(CAMPAIGN, beneficiary, TARGET, DELAY, ids, planned);
    }

    function _donate() internal {
        vm.prank(operator);
        escrow.recordContribution(CAMPAIGN, DONATION, DONATED);
    }

    function _reserve() internal {
        vm.prank(operator);
        escrow.reserve(CAMPAIGN, M1, REQUEST, RESERVE_AMOUNT);
    }

    function _acceptEvidence() internal {
        vm.startPrank(operator);
        escrow.anchorEvidence(CAMPAIGN, M1, EVIDENCE, FILE_HASH);
        escrow.decideEvidence(CAMPAIGN, EVIDENCE, true);
        vm.stopPrank();
    }

    function _queue() internal {
        vm.prank(approver);
        escrow.queueRelease(CAMPAIGN, REQUEST);
    }

    function _readyToQueue() internal {
        _open();
        _donate();
        _reserve();
        _acceptEvidence();
    }

    function _unauthorized(address who, bytes32 role) internal pure returns (bytes memory) {
        return abi.encodeWithSelector(IAccessControl.AccessControlUnauthorizedAccount.selector, who, role);
    }
}

contract CampaignLifecycleTest is CampaignEscrowBase {
    function test_token_isTwoDecimalSimulatedVnd() public view {
        assertEq(token.decimals(), 2);
        assertEq(token.symbol(), "rVND");
    }

    function test_open_storesFixedTerms() public {
        _open();
        CampaignEscrow.Campaign memory c = escrow.getCampaign(CAMPAIGN);
        assertEq(uint8(c.status), uint8(CampaignEscrow.EscrowStatus.Active));
        assertEq(c.beneficiary, beneficiary);
        assertEq(c.releaseDelay, DELAY);
        assertEq(escrow.getMilestone(CAMPAIGN, M2).planned, PLANNED_2);
    }

    function test_open_rejectsPlannedSumDifferentFromTarget() public {
        (bytes32[] memory ids, uint256[] memory planned) = _ids();
        vm.prank(operator);
        vm.expectRevert(CampaignEscrow.BadTerms.selector);
        escrow.openCampaign(CAMPAIGN, beneficiary, TARGET + 1, DELAY, ids, planned);
    }

    function test_open_rejectsDelayBelowFloor() public {
        (bytes32[] memory ids, uint256[] memory planned) = _ids();
        vm.prank(operator);
        vm.expectRevert(CampaignEscrow.BadTerms.selector);
        escrow.openCampaign(CAMPAIGN, beneficiary, TARGET, MIN_DELAY - 1, ids, planned);
    }

    function test_open_rejectsDuplicateMilestone() public {
        (bytes32[] memory ids, uint256[] memory planned) = _ids();
        ids[1] = ids[0];
        vm.prank(operator);
        vm.expectRevert(CampaignEscrow.BadTerms.selector);
        escrow.openCampaign(CAMPAIGN, beneficiary, TARGET, DELAY, ids, planned);
    }

    function test_open_rejectsTreasuryAsBeneficiary() public {
        (bytes32[] memory ids, uint256[] memory planned) = _ids();
        vm.prank(operator);
        vm.expectRevert(CampaignEscrow.BadTerms.selector);
        escrow.openCampaign(CAMPAIGN, treasury, TARGET, DELAY, ids, planned);
    }

    function test_open_twiceReverts() public {
        _open();
        (bytes32[] memory ids, uint256[] memory planned) = _ids();
        vm.prank(operator);
        vm.expectRevert(CampaignEscrow.AlreadyRecorded.selector);
        escrow.openCampaign(CAMPAIGN, beneficiary, TARGET, DELAY, ids, planned);
    }

    function test_open_onlyOperator() public {
        (bytes32[] memory ids, uint256[] memory planned) = _ids();
        vm.prank(stranger);
        vm.expectRevert(_unauthorized(stranger, OPERATOR_ROLE));
        escrow.openCampaign(CAMPAIGN, beneficiary, TARGET, DELAY, ids, planned);
    }

    function test_status_frozenAlternatesAndSettlementIsFinal() public {
        _open();
        vm.startPrank(operator);
        escrow.changeStatus(CAMPAIGN, CampaignEscrow.EscrowStatus.Frozen);
        escrow.changeStatus(CAMPAIGN, CampaignEscrow.EscrowStatus.Active);
        escrow.changeStatus(CAMPAIGN, CampaignEscrow.EscrowStatus.Settlement);
        vm.expectRevert(abi.encodeWithSelector(CampaignEscrow.InvalidEscrowStatus.selector, CampaignEscrow.EscrowStatus.Settlement));
        escrow.changeStatus(CAMPAIGN, CampaignEscrow.EscrowStatus.Active);
        vm.stopPrank();
    }

    function test_status_settlementRefusedWhileFundsReserved() public {
        _open();
        _donate();
        _reserve();
        vm.prank(operator);
        vm.expectRevert(CampaignEscrow.OutstandingReservations.selector);
        escrow.changeStatus(CAMPAIGN, CampaignEscrow.EscrowStatus.Settlement);
    }
}

contract ContributionTest is CampaignEscrowBase {
    function test_contribution_pullsFromTreasuryAndCredits() public {
        _open();
        uint256 before = token.balanceOf(treasury);
        _donate();
        assertEq(token.balanceOf(address(escrow)), DONATED);
        assertEq(token.balanceOf(treasury), before - DONATED);
        assertEq(escrow.getCampaign(CAMPAIGN).available, DONATED);
        assertEq(escrow.totalHeld(), DONATED);
    }

    function test_contribution_confirmedOnlyOnce() public {
        _open();
        _donate();
        vm.prank(operator);
        vm.expectRevert(CampaignEscrow.AlreadyRecorded.selector);
        escrow.recordContribution(CAMPAIGN, DONATION, DONATED);
    }

    function test_contribution_refusedWhenFrozen() public {
        _open();
        vm.startPrank(operator);
        escrow.changeStatus(CAMPAIGN, CampaignEscrow.EscrowStatus.Frozen);
        vm.expectRevert(abi.encodeWithSelector(CampaignEscrow.InvalidEscrowStatus.selector, CampaignEscrow.EscrowStatus.Frozen));
        escrow.recordContribution(CAMPAIGN, DONATION, DONATED);
        vm.stopPrank();
    }

    function test_contribution_refusedWhilePaused() public {
        _open();
        vm.prank(pauser);
        escrow.pause();
        vm.prank(operator);
        vm.expectRevert(Pausable.EnforcedPause.selector);
        escrow.recordContribution(CAMPAIGN, DONATION, DONATED);
    }

    function test_contribution_onlyOperator() public {
        _open();
        vm.prank(stranger);
        vm.expectRevert(_unauthorized(stranger, OPERATOR_ROLE));
        escrow.recordContribution(CAMPAIGN, DONATION, DONATED);
    }

    function test_contribution_unknownCampaign() public {
        vm.prank(operator);
        vm.expectRevert(CampaignEscrow.UnknownCampaign.selector);
        escrow.recordContribution(CAMPAIGN, DONATION, DONATED);
    }
}

contract ReservationTest is CampaignEscrowBase {
    function test_reserve_movesAvailableToReserved() public {
        _open();
        _donate();
        _reserve();
        CampaignEscrow.Campaign memory c = escrow.getCampaign(CAMPAIGN);
        assertEq(c.available, DONATED - RESERVE_AMOUNT);
        assertEq(c.reserved, RESERVE_AMOUNT);
        assertEq(escrow.getMilestone(CAMPAIGN, M1).reserved, RESERVE_AMOUNT);
        assertEq(token.balanceOf(address(escrow)), DONATED);
    }

    function test_reserve_cannotExceedAvailable() public {
        _open();
        vm.prank(operator);
        vm.expectRevert(CampaignEscrow.InsufficientAvailable.selector);
        escrow.reserve(CAMPAIGN, M1, REQUEST, 1);
    }

    function test_reserve_cannotExceedMilestonePlanned() public {
        _open();
        _donate();
        vm.prank(operator);
        vm.expectRevert(CampaignEscrow.ExceedsPlanned.selector);
        escrow.reserve(CAMPAIGN, M1, REQUEST, PLANNED_1 + 1);
    }

    function test_reserve_unknownMilestone() public {
        _open();
        _donate();
        vm.prank(operator);
        vm.expectRevert(CampaignEscrow.UnknownMilestone.selector);
        escrow.reserve(CAMPAIGN, keccak256("nope"), REQUEST, 1);
    }

    function test_reserve_sameRequestOnlyOnce() public {
        _open();
        _donate();
        _reserve();
        vm.prank(operator);
        vm.expectRevert(CampaignEscrow.AlreadyRecorded.selector);
        escrow.reserve(CAMPAIGN, M1, REQUEST, 1);
    }

    function test_cancel_returnsFundsEvenWhenFrozen() public {
        _open();
        _donate();
        _reserve();
        vm.startPrank(operator);
        escrow.changeStatus(CAMPAIGN, CampaignEscrow.EscrowStatus.Frozen);
        escrow.cancelReservation(CAMPAIGN, REQUEST);
        vm.stopPrank();
        CampaignEscrow.Campaign memory c = escrow.getCampaign(CAMPAIGN);
        assertEq(c.available, DONATED);
        assertEq(c.reserved, 0);
        assertEq(escrow.getMilestone(CAMPAIGN, M1).reserved, 0);
    }

    function test_cancel_twiceReverts() public {
        _open();
        _donate();
        _reserve();
        vm.startPrank(operator);
        escrow.cancelReservation(CAMPAIGN, REQUEST);
        vm.expectRevert(CampaignEscrow.InvalidRecordStatus.selector);
        escrow.cancelReservation(CAMPAIGN, REQUEST);
        vm.stopPrank();
    }

    function testFuzz_reserve_neverBreaksMilestoneCap(uint256 first, uint256 second) public {
        _open();
        _donate();
        first = bound(first, 1, PLANNED_1);
        second = bound(second, 1, PLANNED_1);
        vm.startPrank(operator);
        escrow.reserve(CAMPAIGN, M1, REQUEST, first);
        if (first + second > PLANNED_1) {
            vm.expectRevert(CampaignEscrow.ExceedsPlanned.selector);
        }
        escrow.reserve(CAMPAIGN, M1, keccak256("request-2"), second);
        vm.stopPrank();
        CampaignEscrow.Milestone memory m = escrow.getMilestone(CAMPAIGN, M1);
        assertLe(m.reserved + m.released, m.planned);
    }
}

contract ReleaseTest is CampaignEscrowBase {
    function test_release_fullFlowPaysOnlyTheBeneficiary() public {
        _readyToQueue();
        _queue();
        vm.warp(T0 + DELAY);
        vm.prank(stranger);
        escrow.executeRelease(CAMPAIGN, REQUEST);
        assertEq(token.balanceOf(beneficiary), RESERVE_AMOUNT);
        assertEq(token.balanceOf(stranger), 0);
        CampaignEscrow.Campaign memory c = escrow.getCampaign(CAMPAIGN);
        assertEq(c.disbursed, RESERVE_AMOUNT);
        assertEq(c.reserved, 0);
        assertEq(escrow.getMilestone(CAMPAIGN, M1).released, RESERVE_AMOUNT);
        assertEq(escrow.totalHeld(), DONATED - RESERVE_AMOUNT);
    }

    function test_queue_operatorCannotApprove() public {
        _readyToQueue();
        vm.prank(operator);
        vm.expectRevert(_unauthorized(operator, APPROVER_ROLE));
        escrow.queueRelease(CAMPAIGN, REQUEST);
    }

    function test_queue_refusedWithSameActorAsRequester() public {
        _open();
        _donate();
        vm.prank(admin);
        escrow.grantRole(OPERATOR_ROLE, approver);
        vm.prank(approver);
        escrow.reserve(CAMPAIGN, M1, REQUEST, RESERVE_AMOUNT);
        _acceptEvidence();
        vm.prank(approver);
        vm.expectRevert(CampaignEscrow.SameActor.selector);
        escrow.queueRelease(CAMPAIGN, REQUEST);
    }

    function test_queue_needsAcceptedEvidence() public {
        _open();
        _donate();
        _reserve();
        vm.prank(approver);
        vm.expectRevert(CampaignEscrow.MissingAcceptedEvidence.selector);
        escrow.queueRelease(CAMPAIGN, REQUEST);
    }

    function test_queue_rejectedEvidenceDoesNotCount() public {
        _open();
        _donate();
        _reserve();
        vm.startPrank(operator);
        escrow.anchorEvidence(CAMPAIGN, M1, EVIDENCE, FILE_HASH);
        escrow.decideEvidence(CAMPAIGN, EVIDENCE, false);
        vm.stopPrank();
        vm.prank(approver);
        vm.expectRevert(CampaignEscrow.MissingAcceptedEvidence.selector);
        escrow.queueRelease(CAMPAIGN, REQUEST);
    }

    function test_queue_refusedDuringDispute() public {
        _readyToQueue();
        vm.prank(operator);
        escrow.openDispute(CAMPAIGN, M1, DISPUTE);
        vm.prank(approver);
        vm.expectRevert(CampaignEscrow.MilestoneDisputed.selector);
        escrow.queueRelease(CAMPAIGN, REQUEST);
    }

    function test_execute_refusedBeforeWindowEnds() public {
        _readyToQueue();
        _queue();
        uint64 due = uint64(T0 + DELAY);
        vm.warp(T0 + DELAY - 1);
        vm.expectRevert(abi.encodeWithSelector(CampaignEscrow.ReleaseNotDue.selector, due));
        escrow.executeRelease(CAMPAIGN, REQUEST);
    }

    function test_execute_requiresQueue() public {
        _readyToQueue();
        vm.warp(T0 + DELAY);
        vm.expectRevert(CampaignEscrow.InvalidRecordStatus.selector);
        escrow.executeRelease(CAMPAIGN, REQUEST);
    }

    function test_execute_disputeDuringWindowBlocksEvenIfBackendTries() public {
        _readyToQueue();
        _queue();
        vm.prank(operator);
        escrow.openDispute(CAMPAIGN, M1, DISPUTE);
        vm.warp(T0 + DELAY);
        vm.prank(operator);
        vm.expectRevert(CampaignEscrow.MilestoneDisputed.selector);
        escrow.executeRelease(CAMPAIGN, REQUEST);
        assertEq(token.balanceOf(beneficiary), 0);
    }

    function test_execute_allowedAfterLastDisputeCloses() public {
        _readyToQueue();
        _queue();
        bytes32 second = keccak256("dispute-2");
        vm.startPrank(operator);
        escrow.openDispute(CAMPAIGN, M1, DISPUTE);
        escrow.openDispute(CAMPAIGN, M1, second);
        escrow.closeDispute(CAMPAIGN, DISPUTE);
        vm.stopPrank();
        vm.warp(T0 + DELAY);
        vm.expectRevert(CampaignEscrow.MilestoneDisputed.selector);
        escrow.executeRelease(CAMPAIGN, REQUEST);
        vm.prank(operator);
        escrow.closeDispute(CAMPAIGN, second);
        escrow.executeRelease(CAMPAIGN, REQUEST);
        assertEq(token.balanceOf(beneficiary), RESERVE_AMOUNT);
    }

    function test_execute_upheldDisputeCancelsQueuedRelease() public {
        _readyToQueue();
        _queue();
        vm.startPrank(operator);
        escrow.openDispute(CAMPAIGN, M1, DISPUTE);
        escrow.cancelReservation(CAMPAIGN, REQUEST);
        escrow.closeDispute(CAMPAIGN, DISPUTE);
        vm.stopPrank();
        vm.warp(T0 + DELAY);
        vm.expectRevert(CampaignEscrow.InvalidRecordStatus.selector);
        escrow.executeRelease(CAMPAIGN, REQUEST);
        assertEq(escrow.getCampaign(CAMPAIGN).available, DONATED);
    }

    function test_execute_notTwice() public {
        _readyToQueue();
        _queue();
        vm.warp(T0 + DELAY);
        escrow.executeRelease(CAMPAIGN, REQUEST);
        vm.expectRevert(CampaignEscrow.InvalidRecordStatus.selector);
        escrow.executeRelease(CAMPAIGN, REQUEST);
    }

    function test_execute_refusedWhenFrozen() public {
        _readyToQueue();
        _queue();
        vm.prank(operator);
        escrow.changeStatus(CAMPAIGN, CampaignEscrow.EscrowStatus.Frozen);
        vm.warp(T0 + DELAY);
        vm.expectRevert(abi.encodeWithSelector(CampaignEscrow.InvalidEscrowStatus.selector, CampaignEscrow.EscrowStatus.Frozen));
        escrow.executeRelease(CAMPAIGN, REQUEST);
    }

    function test_execute_refusedWhilePaused() public {
        _readyToQueue();
        _queue();
        vm.prank(pauser);
        escrow.pause();
        vm.warp(T0 + DELAY);
        vm.expectRevert(Pausable.EnforcedPause.selector);
        escrow.executeRelease(CAMPAIGN, REQUEST);
    }
}

contract EvidenceTest is CampaignEscrowBase {
    function test_anchor_storesHashAndTime() public {
        _open();
        vm.prank(operator);
        escrow.anchorEvidence(CAMPAIGN, M1, EVIDENCE, FILE_HASH);
        CampaignEscrow.Evidence memory e = escrow.getEvidence(CAMPAIGN, EVIDENCE);
        assertEq(e.contentHash, FILE_HASH);
        assertEq(e.anchoredAt, T0);
    }

    function test_anchor_cannotBeRewritten() public {
        _open();
        vm.startPrank(operator);
        escrow.anchorEvidence(CAMPAIGN, M1, EVIDENCE, FILE_HASH);
        vm.expectRevert(CampaignEscrow.AlreadyRecorded.selector);
        escrow.anchorEvidence(CAMPAIGN, M1, EVIDENCE, keccak256("other.pdf"));
        vm.stopPrank();
    }

    function test_anchor_reusedHashNamesFirstEvidence() public {
        _open();
        bytes32 second = keccak256("evidence-2");
        vm.startPrank(operator);
        escrow.anchorEvidence(CAMPAIGN, M1, EVIDENCE, FILE_HASH);
        vm.expectEmit(address(escrow));
        emit CampaignEscrow.EvidenceAnchored(CAMPAIGN, M2, second, FILE_HASH, EVIDENCE);
        escrow.anchorEvidence(CAMPAIGN, M2, second, FILE_HASH);
        vm.stopPrank();
    }

    function test_anchor_zeroHashRefused() public {
        _open();
        vm.prank(operator);
        vm.expectRevert(CampaignEscrow.ZeroHash.selector);
        escrow.anchorEvidence(CAMPAIGN, M1, EVIDENCE, bytes32(0));
    }

    function test_decide_isFinal() public {
        _open();
        vm.startPrank(operator);
        escrow.anchorEvidence(CAMPAIGN, M1, EVIDENCE, FILE_HASH);
        escrow.decideEvidence(CAMPAIGN, EVIDENCE, true);
        vm.expectRevert(CampaignEscrow.InvalidRecordStatus.selector);
        escrow.decideEvidence(CAMPAIGN, EVIDENCE, false);
        vm.stopPrank();
        assertEq(escrow.getMilestone(CAMPAIGN, M1).acceptedEvidence, 1);
    }
}

contract RefundTest is CampaignEscrowBase {
    uint256 internal constant REFUND_AMOUNT = 5_000_000_00;

    function _reserveRefund() internal {
        vm.prank(operator);
        escrow.reserveRefund(CAMPAIGN, DONATION, REFUND, REFUND_AMOUNT);
    }

    function test_refund_reserveThenRecordReturnsToTreasury() public {
        _open();
        _donate();
        uint256 before = token.balanceOf(treasury);
        _reserveRefund();
        assertEq(escrow.getCampaign(CAMPAIGN).refundReserved, REFUND_AMOUNT);
        vm.prank(approver);
        escrow.recordRefund(CAMPAIGN, REFUND);
        CampaignEscrow.Campaign memory c = escrow.getCampaign(CAMPAIGN);
        assertEq(c.refundReserved, 0);
        assertEq(c.refunded, REFUND_AMOUNT);
        assertEq(c.available, DONATED - REFUND_AMOUNT);
        assertEq(token.balanceOf(treasury), before + REFUND_AMOUNT);
    }

    function test_refund_recordOnlyApprover() public {
        _open();
        _donate();
        _reserveRefund();
        vm.prank(operator);
        vm.expectRevert(_unauthorized(operator, APPROVER_ROLE));
        escrow.recordRefund(CAMPAIGN, REFUND);
    }

    function test_refund_recordOnlyOnce() public {
        _open();
        _donate();
        _reserveRefund();
        vm.startPrank(approver);
        escrow.recordRefund(CAMPAIGN, REFUND);
        vm.expectRevert(CampaignEscrow.InvalidRecordStatus.selector);
        escrow.recordRefund(CAMPAIGN, REFUND);
        vm.stopPrank();
    }

    function test_refund_cannotExceedDonation() public {
        _open();
        _donate();
        vm.prank(operator);
        vm.expectRevert(CampaignEscrow.ExceedsRefundable.selector);
        escrow.reserveRefund(CAMPAIGN, DONATION, REFUND, DONATED + 1);
    }

    function test_refund_outstandingReservationCountsAgainstDonation() public {
        _open();
        _donate();
        vm.startPrank(operator);
        escrow.reserveRefund(CAMPAIGN, DONATION, REFUND, DONATED);
        vm.expectRevert(CampaignEscrow.ExceedsRefundable.selector);
        escrow.reserveRefund(CAMPAIGN, DONATION, keccak256("refund-2"), 1);
        vm.stopPrank();
    }

    function test_refund_reservedFundsCannotBeDisbursed() public {
        _open();
        _donate();
        vm.startPrank(operator);
        escrow.reserveRefund(CAMPAIGN, DONATION, REFUND, DONATED);
        vm.expectRevert(CampaignEscrow.InsufficientAvailable.selector);
        escrow.reserve(CAMPAIGN, M1, REQUEST, 1);
        vm.stopPrank();
    }

    function test_refund_releaseReservationRestoresAvailable() public {
        _open();
        _donate();
        _reserveRefund();
        vm.prank(operator);
        escrow.releaseRefundReservation(CAMPAIGN, REFUND);
        assertEq(escrow.getCampaign(CAMPAIGN).available, DONATED);
        assertEq(escrow.getContribution(CAMPAIGN, DONATION).refundReserved, 0);
    }

    function test_refund_worksWhilePausedAndFrozen() public {
        _open();
        _donate();
        _reserveRefund();
        vm.prank(operator);
        escrow.changeStatus(CAMPAIGN, CampaignEscrow.EscrowStatus.Frozen);
        vm.prank(pauser);
        escrow.pause();
        vm.prank(approver);
        escrow.recordRefund(CAMPAIGN, REFUND);
        assertEq(escrow.getCampaign(CAMPAIGN).refunded, REFUND_AMOUNT);
    }
}

contract SettlementAndAdjustmentTest is CampaignEscrowBase {
    bytes32 internal constant SETTLEMENT = keccak256("settlement-1");
    bytes32 internal constant ADJUSTMENT = keccak256("adjustment-1");

    function _toSettlement() internal {
        vm.prank(operator);
        escrow.changeStatus(CAMPAIGN, CampaignEscrow.EscrowStatus.Settlement);
    }

    function test_settlement_onlyInSettlementStatus() public {
        _open();
        _donate();
        vm.prank(approver);
        vm.expectRevert(abi.encodeWithSelector(CampaignEscrow.InvalidEscrowStatus.selector, CampaignEscrow.EscrowStatus.Active));
        escrow.recordSettlementRefund(CAMPAIGN, SETTLEMENT, DONATION, 1);
    }

    function test_settlement_paysShareOnceToTreasury() public {
        _open();
        _donate();
        _toSettlement();
        uint256 before = token.balanceOf(treasury);
        vm.startPrank(approver);
        escrow.recordSettlementRefund(CAMPAIGN, SETTLEMENT, DONATION, DONATED);
        vm.expectRevert(CampaignEscrow.AlreadyRecorded.selector);
        escrow.recordSettlementRefund(CAMPAIGN, SETTLEMENT, DONATION, 1);
        vm.stopPrank();
        assertEq(token.balanceOf(treasury), before + DONATED);
        assertEq(escrow.getCampaign(CAMPAIGN).refunded, DONATED);
        assertEq(escrow.totalHeld(), 0);
    }

    function test_settlement_cannotExceedRefundable() public {
        _open();
        _donate();
        _toSettlement();
        vm.prank(approver);
        vm.expectRevert(CampaignEscrow.ExceedsRefundable.selector);
        escrow.recordSettlementRefund(CAMPAIGN, SETTLEMENT, DONATION, DONATED + 1);
    }

    function test_adjustment_creditAndDebitMoveOnlyThroughTreasury() public {
        _open();
        _donate();
        vm.startPrank(approver);
        escrow.recordAdjustment(CAMPAIGN, ADJUSTMENT, true, 100);
        assertEq(escrow.getCampaign(CAMPAIGN).available, DONATED + 100);
        escrow.recordAdjustment(CAMPAIGN, keccak256("adjustment-2"), false, 40);
        vm.stopPrank();
        CampaignEscrow.Campaign memory c = escrow.getCampaign(CAMPAIGN);
        assertEq(c.available, DONATED + 60);
        assertEq(c.adjustmentCredits, 100);
        assertEq(c.adjustmentDebits, 40);
        assertEq(token.balanceOf(address(escrow)), DONATED + 60);
    }

    function test_adjustment_debitCannotExceedAvailable() public {
        _open();
        _donate();
        vm.prank(approver);
        vm.expectRevert(CampaignEscrow.InsufficientAvailable.selector);
        escrow.recordAdjustment(CAMPAIGN, ADJUSTMENT, false, DONATED + 1);
    }

    function test_adjustment_notReplayable() public {
        _open();
        _donate();
        vm.startPrank(approver);
        escrow.recordAdjustment(CAMPAIGN, ADJUSTMENT, true, 1);
        vm.expectRevert(CampaignEscrow.AlreadyRecorded.selector);
        escrow.recordAdjustment(CAMPAIGN, ADJUSTMENT, true, 1);
        vm.stopPrank();
    }

    function test_adjustment_operatorCannotMoveFunds() public {
        _open();
        _donate();
        vm.prank(operator);
        vm.expectRevert(_unauthorized(operator, APPROVER_ROLE));
        escrow.recordAdjustment(CAMPAIGN, ADJUSTMENT, false, 1);
    }
}

contract ConstructorTest is Test {
    function test_constructor_rejectsDelayFloorAboveMaximum() public {
        RiseVND token = new RiseVND(address(this));
        vm.expectRevert(CampaignEscrow.BadTerms.selector);
        new CampaignEscrow(address(this), token, address(0xBEEF), 31 days, address(0x6A4D));
    }
}
