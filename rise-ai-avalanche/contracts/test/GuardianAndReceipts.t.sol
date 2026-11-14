// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {IAccessControl} from "@openzeppelin/contracts/access/IAccessControl.sol";
import {CampaignEscrow} from "../src/CampaignEscrow.sol";
import {RiseVND} from "../src/RiseVND.sol";
import {CampaignEscrowBase} from "./CampaignEscrow.t.sol";

contract GuardianHoldTest is CampaignEscrowBase {
    bytes32 internal constant HOLD = keccak256("hold-1");
    bytes32 internal constant REASON = keccak256("public statement");
    bytes32 internal constant GUARDIAN_ROLE = keccak256("GUARDIAN_ROLE");

    function _hold() internal {
        vm.prank(guardian);
        escrow.placeHold(CAMPAIGN, M1, HOLD, REASON);
    }

    function test_constructor_givesTheGuardianItsOwnRoleAdmin() public view {
        assertTrue(escrow.hasRole(GUARDIAN_ROLE, guardian));
        assertEq(escrow.getRoleAdmin(GUARDIAN_ROLE), GUARDIAN_ROLE);
        assertEq(escrow.GUARDIAN_HOLD_MAX(), 14 days);
    }

    function test_constructor_refusesAGuardianThatIsThePlatform() public {
        RiseVND t = new RiseVND(address(this));
        vm.expectRevert(CampaignEscrow.GuardianConflict.selector);
        new CampaignEscrow(admin, t, treasury, MIN_DELAY, admin);
        vm.expectRevert(CampaignEscrow.GuardianConflict.selector);
        new CampaignEscrow(admin, t, treasury, MIN_DELAY, treasury);
        vm.expectRevert(CampaignEscrow.GuardianConflict.selector);
        new CampaignEscrow(admin, t, treasury, MIN_DELAY, address(0));
    }

    function test_holdBlocksQueueing() public {
        _readyToQueue();
        _hold();
        vm.prank(approver);
        vm.expectRevert(CampaignEscrow.MilestoneHeld.selector);
        escrow.queueRelease(CAMPAIGN, REQUEST);
    }

    function test_holdBlocksExecutionDuringAndAfterTheWindow() public {
        _readyToQueue();
        _queue();
        _hold();
        vm.warp(block.timestamp + DELAY);
        vm.expectRevert(CampaignEscrow.MilestoneHeld.selector);
        escrow.executeRelease(CAMPAIGN, REQUEST);
        assertEq(token.balanceOf(beneficiary), 0);
    }

    function test_holdOnOneMilestoneLeavesTheOthersFree() public {
        _readyToQueue();
        vm.prank(guardian);
        escrow.placeHold(CAMPAIGN, M2, HOLD, REASON);
        _queue();
        vm.warp(block.timestamp + DELAY);
        escrow.executeRelease(CAMPAIGN, REQUEST);
        assertEq(token.balanceOf(beneficiary), RESERVE_AMOUNT);
    }

    function test_guardianLiftsAndTheReleaseProceeds() public {
        _readyToQueue();
        _queue();
        _hold();
        vm.prank(guardian);
        vm.expectEmit(address(escrow));
        emit CampaignEscrow.GuardianHoldLifted(CAMPAIGN, M1, HOLD, guardian, false);
        escrow.liftHold(CAMPAIGN, HOLD);
        vm.warp(block.timestamp + DELAY);
        escrow.executeRelease(CAMPAIGN, REQUEST);
        assertEq(token.balanceOf(beneficiary), RESERVE_AMOUNT);
    }

    function test_thePlatformCannotLiftAHoldBeforeItExpires() public {
        _open();
        _hold();
        uint64 expiresAt = uint64(block.timestamp) + 14 days;
        address[3] memory platform = [operator, approver, admin];
        for (uint256 i = 0; i < platform.length; i++) {
            vm.prank(platform[i]);
            vm.expectRevert(abi.encodeWithSelector(CampaignEscrow.HoldNotExpired.selector, expiresAt));
            escrow.liftHold(CAMPAIGN, HOLD);
        }
        vm.warp(expiresAt - 1);
        vm.prank(operator);
        vm.expectRevert(abi.encodeWithSelector(CampaignEscrow.HoldNotExpired.selector, expiresAt));
        escrow.liftHold(CAMPAIGN, HOLD);
    }

    function test_anyoneLiftsAnExpiredHold() public {
        _open();
        _hold();
        vm.warp(block.timestamp + 14 days);
        vm.prank(stranger);
        vm.expectEmit(address(escrow));
        emit CampaignEscrow.GuardianHoldLifted(CAMPAIGN, M1, HOLD, stranger, true);
        escrow.liftHold(CAMPAIGN, HOLD);
        assertEq(escrow.openHoldCount(CAMPAIGN, M1), 0);
        vm.prank(guardian);
        vm.expectRevert(CampaignEscrow.InvalidRecordStatus.selector);
        escrow.liftHold(CAMPAIGN, HOLD);
    }

    function test_onlyTheGuardianPlacesHolds() public {
        _open();
        address[4] memory others = [operator, approver, admin, stranger];
        for (uint256 i = 0; i < others.length; i++) {
            vm.prank(others[i]);
            vm.expectRevert(_unauthorized(others[i], GUARDIAN_ROLE));
            escrow.placeHold(CAMPAIGN, M1, HOLD, REASON);
        }
    }

    function test_placeHoldRecordsTheExpiryAndRejectsBadInput() public {
        _open();
        vm.prank(guardian);
        vm.expectEmit(address(escrow));
        emit CampaignEscrow.GuardianHoldPlaced(CAMPAIGN, M1, HOLD, guardian, REASON, uint64(block.timestamp) + 14 days);
        escrow.placeHold(CAMPAIGN, M1, HOLD, REASON);
        CampaignEscrow.GuardianHold memory h = escrow.getGuardianHold(CAMPAIGN, HOLD);
        assertTrue(h.open);
        assertEq(h.milestoneId, M1);
        assertEq(h.placedAt, block.timestamp);

        vm.startPrank(guardian);
        vm.expectRevert(CampaignEscrow.AlreadyRecorded.selector);
        escrow.placeHold(CAMPAIGN, M1, HOLD, REASON);
        vm.expectRevert(CampaignEscrow.BadTerms.selector);
        escrow.placeHold(CAMPAIGN, M1, bytes32(0), REASON);
        vm.expectRevert(CampaignEscrow.UnknownMilestone.selector);
        escrow.placeHold(CAMPAIGN, keccak256("nope"), keccak256("hold-2"), REASON);
        vm.expectRevert(CampaignEscrow.UnknownCampaign.selector);
        escrow.placeHold(keccak256("nope"), M1, keccak256("hold-3"), REASON);
        vm.stopPrank();
    }

    function test_twoHoldsNeedTwoLifts() public {
        _readyToQueue();
        _hold();
        vm.startPrank(guardian);
        escrow.placeHold(CAMPAIGN, M1, keccak256("hold-2"), REASON);
        escrow.liftHold(CAMPAIGN, HOLD);
        vm.stopPrank();
        vm.prank(approver);
        vm.expectRevert(CampaignEscrow.MilestoneHeld.selector);
        escrow.queueRelease(CAMPAIGN, REQUEST);
    }

    function test_theAdminCanNeitherRevokeNorAppointAGuardian() public {
        vm.startPrank(admin);
        vm.expectRevert(_unauthorized(admin, GUARDIAN_ROLE));
        escrow.revokeRole(GUARDIAN_ROLE, guardian);
        vm.expectRevert(_unauthorized(admin, GUARDIAN_ROLE));
        escrow.grantRole(GUARDIAN_ROLE, stranger);
        vm.stopPrank();
        assertTrue(escrow.hasRole(GUARDIAN_ROLE, guardian));
    }

    function test_rolesNeverMixBetweenGuardianAndPlatform() public {
        vm.prank(admin);
        vm.expectRevert(CampaignEscrow.GuardianConflict.selector);
        escrow.grantRole(OPERATOR_ROLE, guardian);
        vm.prank(guardian);
        vm.expectRevert(CampaignEscrow.GuardianConflict.selector);
        escrow.grantRole(GUARDIAN_ROLE, operator);
    }

    function test_theGuardianCanHandItsRoleToAnotherIndependentWallet() public {
        address next = makeAddr("next-guardian");
        vm.startPrank(guardian);
        escrow.grantRole(GUARDIAN_ROLE, next);
        escrow.renounceRole(GUARDIAN_ROLE, guardian);
        vm.stopPrank();
        assertTrue(escrow.hasRole(GUARDIAN_ROLE, next));
        assertFalse(escrow.hasRole(GUARDIAN_ROLE, guardian));
    }

    function test_theGuardianCannotMoveFunds() public {
        _readyToQueue();
        vm.startPrank(guardian);
        vm.expectRevert(_unauthorized(guardian, APPROVER_ROLE));
        escrow.queueRelease(CAMPAIGN, REQUEST);
        vm.expectRevert(_unauthorized(guardian, OPERATOR_ROLE));
        escrow.cancelReservation(CAMPAIGN, REQUEST);
        vm.expectRevert(_unauthorized(guardian, APPROVER_ROLE));
        escrow.recordAdjustment(CAMPAIGN, keccak256("adj"), false, 1);
        vm.stopPrank();
    }
}

contract ReserveAttestationTest is CampaignEscrowBase {
    bytes32 internal constant STATEMENT = keccak256("bank statement 2026-11");

    function test_attestationRecordsTheHeldTotalAtThatMoment() public {
        _open();
        _donate();
        vm.prank(guardian);
        vm.expectEmit(address(escrow));
        emit CampaignEscrow.ReservesAttested(1, guardian, DONATED - 1, DONATED, STATEMENT);
        escrow.attestReserves(1, DONATED - 1, STATEMENT);
        CampaignEscrow.ReserveAttestation memory a = escrow.getReserveAttestation();
        assertEq(a.period, 1);
        assertEq(a.attester, guardian);
        assertEq(a.bankBalance, DONATED - 1);
        assertEq(a.heldAtAttestation, DONATED);
        assertEq(a.statementHash, STATEMENT);
        assertEq(a.attestedAt, block.timestamp);
    }

    function test_periodsOnlyMoveForward() public {
        vm.startPrank(guardian);
        escrow.attestReserves(5, 0, STATEMENT);
        vm.expectRevert(CampaignEscrow.BadTerms.selector);
        escrow.attestReserves(5, 0, STATEMENT);
        vm.expectRevert(CampaignEscrow.BadTerms.selector);
        escrow.attestReserves(4, 0, STATEMENT);
        vm.expectRevert(CampaignEscrow.ZeroHash.selector);
        escrow.attestReserves(6, 0, bytes32(0));
        vm.stopPrank();
    }

    function test_onlyTheGuardianAttests() public {
        address[3] memory platform = [operator, approver, admin];
        for (uint256 i = 0; i < platform.length; i++) {
            vm.prank(platform[i]);
            vm.expectRevert(
                abi.encodeWithSelector(IAccessControl.AccessControlUnauthorizedAccount.selector, platform[i], keccak256("GUARDIAN_ROLE"))
            );
            escrow.attestReserves(1, 0, STATEMENT);
        }
    }
}

contract PayoutReceiptTest is CampaignEscrowBase {
    bytes32 internal constant BANK_REF = keccak256("SIMULATED-PAYOUT-1");

    function _released() internal {
        _readyToQueue();
        _queue();
        vm.warp(block.timestamp + DELAY);
        escrow.executeRelease(CAMPAIGN, REQUEST);
    }

    function test_payoutThenBeneficiaryConfirms() public {
        _released();
        vm.prank(operator);
        vm.expectEmit(address(escrow));
        emit CampaignEscrow.PayoutRecorded(CAMPAIGN, REQUEST, BANK_REF);
        escrow.recordPayout(CAMPAIGN, REQUEST, BANK_REF);
        vm.warp(block.timestamp + 1 hours);
        vm.prank(beneficiary);
        vm.expectEmit(address(escrow));
        emit CampaignEscrow.ReceiptConfirmed(CAMPAIGN, REQUEST, beneficiary);
        escrow.confirmReceipt(CAMPAIGN, REQUEST);
        CampaignEscrow.Payout memory p = escrow.getPayout(CAMPAIGN, REQUEST);
        assertEq(p.bankReferenceHash, BANK_REF);
        assertEq(p.confirmedAt, block.timestamp);
        assertEq(p.recordedAt, block.timestamp - 1 hours);
    }

    function test_onlyAReleasedRequestGetsAPayout() public {
        _readyToQueue();
        vm.startPrank(operator);
        vm.expectRevert(CampaignEscrow.InvalidRecordStatus.selector);
        escrow.recordPayout(CAMPAIGN, REQUEST, BANK_REF);
        vm.expectRevert(CampaignEscrow.UnknownRecord.selector);
        escrow.recordPayout(CAMPAIGN, keccak256("nope"), BANK_REF);
        vm.stopPrank();
    }

    function test_payoutIsRecordedOnceWithAReference() public {
        _released();
        vm.startPrank(operator);
        vm.expectRevert(CampaignEscrow.ZeroHash.selector);
        escrow.recordPayout(CAMPAIGN, REQUEST, bytes32(0));
        escrow.recordPayout(CAMPAIGN, REQUEST, BANK_REF);
        vm.expectRevert(CampaignEscrow.AlreadyRecorded.selector);
        escrow.recordPayout(CAMPAIGN, REQUEST, keccak256("other"));
        vm.stopPrank();
    }

    function test_onlyTheBeneficiaryConfirmsAndOnlyOnce() public {
        _released();
        vm.prank(beneficiary);
        vm.expectRevert(CampaignEscrow.UnknownRecord.selector);
        escrow.confirmReceipt(CAMPAIGN, REQUEST);
        vm.prank(operator);
        escrow.recordPayout(CAMPAIGN, REQUEST, BANK_REF);
        address[3] memory others = [operator, approver, stranger];
        for (uint256 i = 0; i < others.length; i++) {
            vm.prank(others[i]);
            vm.expectRevert(CampaignEscrow.NotBeneficiary.selector);
            escrow.confirmReceipt(CAMPAIGN, REQUEST);
        }
        vm.startPrank(beneficiary);
        escrow.confirmReceipt(CAMPAIGN, REQUEST);
        vm.expectRevert(CampaignEscrow.AlreadyRecorded.selector);
        escrow.confirmReceipt(CAMPAIGN, REQUEST);
        vm.stopPrank();
    }

    function test_onlyTheOperatorRecordsPayouts() public {
        _released();
        vm.prank(stranger);
        vm.expectRevert(_unauthorized(stranger, OPERATOR_ROLE));
        escrow.recordPayout(CAMPAIGN, REQUEST, BANK_REF);
    }
}
