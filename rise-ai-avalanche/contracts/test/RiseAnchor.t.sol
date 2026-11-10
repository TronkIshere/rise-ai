// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {Test} from "forge-std/Test.sol";
import {IAccessControl} from "@openzeppelin/contracts/access/IAccessControl.sol";
import {RiseAnchor} from "../src/RiseAnchor.sol";

contract RiseAnchorTest is Test {
    bytes32 internal constant ROLE = keccak256("ANCHORER_ROLE");
    bytes32 internal constant KIND = keccak256("LEDGER");
    bytes32 internal constant ROOT_1 = keccak256("root-1");
    bytes32 internal constant ROOT_2 = keccak256("root-2");

    RiseAnchor internal anchors;
    address internal anchorer = makeAddr("anchorer");
    address internal stranger = makeAddr("stranger");

    function setUp() public {
        anchors = new RiseAnchor(address(this));
        anchors.grantRole(ROLE, anchorer);
    }

    function test_anchor_appendsInOrder() public {
        vm.warp(1_700_000_000);
        vm.startPrank(anchorer);
        anchors.anchor(KIND, ROOT_1, bytes32(0), 10);
        anchors.anchor(KIND, ROOT_2, ROOT_1, 12);
        vm.stopPrank();
        assertEq(anchors.anchorCount(KIND), 2);
        RiseAnchor.Anchor memory a = anchors.anchorAt(KIND, 1);
        assertEq(a.root, ROOT_2);
        assertEq(a.recordCount, 12);
        assertEq(a.anchoredAt, 1_700_000_000);
    }

    function test_anchor_refusesForkOfHistory() public {
        vm.startPrank(anchorer);
        anchors.anchor(KIND, ROOT_1, bytes32(0), 10);
        vm.expectRevert(abi.encodeWithSelector(RiseAnchor.StalePreviousRoot.selector, ROOT_1));
        anchors.anchor(KIND, ROOT_2, bytes32(0), 12);
        vm.stopPrank();
    }

    function test_anchor_refusesShrinkingRecordCount() public {
        vm.startPrank(anchorer);
        anchors.anchor(KIND, ROOT_1, bytes32(0), 10);
        vm.expectRevert(abi.encodeWithSelector(RiseAnchor.RecordCountDecreased.selector, uint64(10)));
        anchors.anchor(KIND, ROOT_2, ROOT_1, 9);
        vm.stopPrank();
    }

    function test_anchor_kindsAreIndependent() public {
        bytes32 other = keccak256("FINDINGS");
        vm.startPrank(anchorer);
        anchors.anchor(KIND, ROOT_1, bytes32(0), 10);
        anchors.anchor(other, ROOT_2, bytes32(0), 1);
        vm.stopPrank();
        assertEq(anchors.anchorCount(KIND), 1);
        assertEq(anchors.anchorCount(other), 1);
    }

    function test_anchor_rejectsEmptyValues() public {
        vm.startPrank(anchorer);
        vm.expectRevert(RiseAnchor.BadAnchor.selector);
        anchors.anchor(KIND, bytes32(0), bytes32(0), 1);
        vm.expectRevert(RiseAnchor.BadAnchor.selector);
        anchors.anchor(KIND, ROOT_1, bytes32(0), 0);
        vm.stopPrank();
    }

    function test_anchor_onlyAnchorer() public {
        vm.prank(stranger);
        vm.expectRevert(abi.encodeWithSelector(IAccessControl.AccessControlUnauthorizedAccount.selector, stranger, ROLE));
        anchors.anchor(KIND, ROOT_1, bytes32(0), 1);
    }
}
