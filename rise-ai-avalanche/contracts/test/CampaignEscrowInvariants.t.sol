// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {Test} from "forge-std/Test.sol";
import {StdInvariant} from "forge-std/StdInvariant.sol";
import {CampaignEscrow} from "../src/CampaignEscrow.sol";
import {RiseVND} from "../src/RiseVND.sol";

/// Random sequences of every operation, including time travel and refused calls. Reverts are expected and ignored.
contract CampaignEscrowHandler is Test {
    uint256 internal constant COUNT = 3;
    uint256 internal constant PLANNED = 1_000_000;

    CampaignEscrow public escrow;
    address public operator;
    address public approver;
    address public beneficiary;
    address public guardian;

    uint256 internal nonce;
    bytes32[] public requests;
    bytes32[] public refunds;
    bytes32[] public disputes;
    bytes32[] public donations;
    bytes32[] public holds;
    uint256 public refundReservationsMade;
    /// Releases that executed while their milestone had an open guardian hold. Must stay zero.
    uint256 public releasesDuringHold;

    constructor(CampaignEscrow e, address operator_, address approver_, address beneficiary_, address guardian_) {
        escrow = e;
        operator = operator_;
        approver = approver_;
        beneficiary = beneficiary_;
        guardian = guardian_;
    }

    function campaignId(uint256 i) public pure returns (bytes32) {
        return keccak256(abi.encode("campaign", i % COUNT));
    }

    function milestoneId(uint256 i) public pure returns (bytes32) {
        return keccak256(abi.encode("milestone", i % COUNT));
    }

    function _next() internal returns (bytes32) {
        nonce++;
        return keccak256(abi.encode("id", nonce));
    }

    function contribute(uint256 c, uint256 amount) external {
        amount = bound(amount, 1, PLANNED);
        bytes32 id = _next();
        donations.push(id);
        vm.prank(operator);
        escrow.recordContribution(campaignId(c), id, amount);
    }

    function reserve(uint256 c, uint256 m, uint256 amount) external {
        amount = bound(amount, 1, PLANNED);
        bytes32 id = _next();
        requests.push(id);
        vm.prank(operator);
        escrow.reserve(campaignId(c), milestoneId(m), id, amount);
    }

    function cancel(uint256 c, uint256 r) external {
        if (requests.length == 0) return;
        vm.prank(operator);
        escrow.cancelReservation(campaignId(c), requests[r % requests.length]);
    }

    function queue(uint256 c, uint256 r) external {
        if (requests.length == 0) return;
        vm.prank(approver);
        escrow.queueRelease(campaignId(c), requests[r % requests.length]);
    }

    function execute(uint256 c, uint256 r) external {
        if (requests.length == 0) return;
        bytes32 request = requests[r % requests.length];
        bytes32 milestone = escrow.getReservation(campaignId(c), request).milestoneId;
        bool held = milestone != bytes32(0) && escrow.openHoldCount(campaignId(c), milestone) != 0;
        escrow.executeRelease(campaignId(c), request);
        if (held) releasesDuringHold++;
    }

    function placeHold(uint256 c, uint256 m) external {
        bytes32 id = _next();
        holds.push(id);
        vm.prank(guardian);
        escrow.placeHold(campaignId(c), milestoneId(m), id, keccak256("reason"));
    }

    function liftHold(uint256 c, uint256 h, bool asOperator) external {
        if (holds.length == 0) return;
        vm.prank(asOperator ? operator : guardian);
        escrow.liftHold(campaignId(c), holds[h % holds.length]);
    }

    function dispute(uint256 c, uint256 m) external {
        bytes32 id = _next();
        disputes.push(id);
        vm.prank(operator);
        escrow.openDispute(campaignId(c), milestoneId(m), id);
    }

    function closeDispute(uint256 c, uint256 d) external {
        if (disputes.length == 0) return;
        vm.prank(operator);
        escrow.closeDispute(campaignId(c), disputes[d % disputes.length]);
    }

    function reserveRefund(uint256 c, uint256 d, uint256 amount) external {
        if (donations.length == 0) return;
        amount = bound(amount, 1, PLANNED);
        bytes32 id = _next();
        refunds.push(id);
        vm.prank(operator);
        escrow.reserveRefund(campaignId(c), donations[d % donations.length], id, amount);
    }

    function releaseRefund(uint256 c, uint256 r) external {
        if (refunds.length == 0) return;
        vm.prank(operator);
        escrow.releaseRefundReservation(campaignId(c), refunds[r % refunds.length]);
    }

    function recordRefund(uint256 c, uint256 r) external {
        if (refunds.length == 0) return;
        vm.prank(approver);
        escrow.recordRefund(campaignId(c), refunds[r % refunds.length]);
    }

    function settle(uint256 c, uint256 d, uint256 amount) external {
        if (donations.length == 0) return;
        amount = bound(amount, 1, PLANNED);
        vm.prank(approver);
        escrow.recordSettlementRefund(campaignId(c), _next(), donations[d % donations.length], amount);
    }

    function adjust(uint256 c, bool credit, uint256 amount) external {
        amount = bound(amount, 1, PLANNED);
        vm.prank(approver);
        escrow.recordAdjustment(campaignId(c), _next(), credit, amount);
    }

    function changeStatus(uint256 c, uint8 next) external {
        vm.prank(operator);
        escrow.changeStatus(campaignId(c), CampaignEscrow.EscrowStatus(bound(next, 1, 3)));
    }

    function warp(uint256 seconds_) external {
        vm.warp(block.timestamp + bound(seconds_, 1, 2 days));
    }
}

contract CampaignEscrowInvariantTest is StdInvariant, Test {
    uint256 internal constant COUNT = 3;
    uint256 internal constant SUPPLY = 1_000_000_000_000;

    RiseVND internal token;
    CampaignEscrow internal escrow;
    CampaignEscrowHandler internal handler;

    address internal treasury = makeAddr("treasury");
    address internal operator = makeAddr("operator");
    address internal approver = makeAddr("approver");
    address internal beneficiary = makeAddr("beneficiary");
    address internal guardian = makeAddr("guardian");

    function setUp() public {
        token = new RiseVND(address(this));
        escrow = new CampaignEscrow(address(this), token, treasury, 1 hours, guardian);
        escrow.grantRole(escrow.OPERATOR_ROLE(), operator);
        escrow.grantRole(escrow.APPROVER_ROLE(), approver);
        token.mint(treasury, SUPPLY);
        vm.prank(treasury);
        token.approve(address(escrow), type(uint256).max);

        handler = new CampaignEscrowHandler(escrow, operator, approver, beneficiary, guardian);

        bytes32[] memory ids = new bytes32[](COUNT);
        uint256[] memory planned = new uint256[](COUNT);
        for (uint256 m = 0; m < COUNT; m++) {
            ids[m] = handler.milestoneId(m);
            planned[m] = 1_000_000;
        }
        for (uint256 c = 0; c < COUNT; c++) {
            bytes32 id = handler.campaignId(c);
            vm.startPrank(operator);
            escrow.openCampaign(id, beneficiary, 3_000_000, 1 hours, ids, planned);
            for (uint256 m = 0; m < COUNT; m++) {
                bytes32 evidenceId = keccak256(abi.encode("evidence", c, m));
                escrow.anchorEvidence(id, ids[m], evidenceId, keccak256(abi.encode("hash", c, m)));
                escrow.decideEvidence(id, evidenceId, true);
            }
            vm.stopPrank();
        }
        targetContract(address(handler));
    }

    function invariant_balanceIdentityHoldsForEveryCampaign() public view {
        for (uint256 c = 0; c < COUNT; c++) {
            CampaignEscrow.Campaign memory x = escrow.getCampaign(handler.campaignId(c));
            assertEq(
                x.confirmed + x.adjustmentCredits,
                x.available + x.reserved + x.refundReserved + x.disbursed + x.refunded + x.adjustmentDebits
            );
        }
    }

    function invariant_contractHoldsExactlyTheSumOfCampaignBalances() public view {
        uint256 sum;
        for (uint256 c = 0; c < COUNT; c++) {
            sum += escrow.heldBalance(handler.campaignId(c));
        }
        assertEq(sum, escrow.totalHeld());
        assertEq(token.balanceOf(address(escrow)), escrow.totalHeld());
    }

    function invariant_valueIsOnlyEverInTheThreeAllowedPlaces() public view {
        assertEq(token.balanceOf(treasury) + token.balanceOf(address(escrow)) + token.balanceOf(beneficiary), SUPPLY);
    }

    function invariant_beneficiaryReceivedExactlyWhatWasDisbursed() public view {
        uint256 disbursed;
        for (uint256 c = 0; c < COUNT; c++) {
            disbursed += escrow.getCampaign(handler.campaignId(c)).disbursed;
        }
        assertEq(token.balanceOf(beneficiary), disbursed);
    }

    function invariant_noReleaseExecutesWhileAGuardianHoldIsOpen() public view {
        assertEq(handler.releasesDuringHold(), 0);
    }

    function invariant_theGuardianNeverHoldsFundsOrAPlatformRole() public view {
        assertEq(token.balanceOf(guardian), 0);
        assertFalse(escrow.hasRole(escrow.OPERATOR_ROLE(), guardian));
        assertFalse(escrow.hasRole(escrow.APPROVER_ROLE(), guardian));
        assertTrue(escrow.hasRole(escrow.GUARDIAN_ROLE(), guardian));
    }

    function invariant_milestoneNeverExceedsPlanned() public view {
        for (uint256 c = 0; c < COUNT; c++) {
            for (uint256 m = 0; m < COUNT; m++) {
                CampaignEscrow.Milestone memory x = escrow.getMilestone(handler.campaignId(c), handler.milestoneId(m));
                assertLe(x.reserved + x.released, x.planned);
            }
        }
    }
}
