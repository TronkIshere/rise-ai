// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {AccessControl} from "@openzeppelin/contracts/access/AccessControl.sol";

/// @notice Append-only commitments to off-chain records (ledger, audit log, monitoring findings). Only hashes are stored, never
/// personal data or finding content. Each anchor names the root it follows, so history cannot be rewritten or forked.
/// Nothing in the escrow reads this contract: monitoring stays advisory.
contract RiseAnchor is AccessControl {
    bytes32 public constant ANCHORER_ROLE = keccak256("ANCHORER_ROLE");

    bytes32 public constant KIND_LEDGER = keccak256("LEDGER");
    bytes32 public constant KIND_AUDIT = keccak256("AUDIT");
    bytes32 public constant KIND_FINDINGS = keccak256("FINDINGS");

    struct Anchor {
        bytes32 root;
        uint64 recordCount;
        uint64 anchoredAt;
    }

    event Anchored(bytes32 indexed kind, uint256 indexed sequence, bytes32 root, bytes32 previousRoot, uint64 recordCount);

    error BadAnchor();
    error StalePreviousRoot(bytes32 expected);
    error RecordCountDecreased(uint64 latest);

    mapping(bytes32 kind => Anchor[]) private anchors;

    constructor(address admin) {
        if (admin == address(0)) revert BadAnchor();
        _grantRole(DEFAULT_ADMIN_ROLE, admin);
    }

    function anchor(bytes32 kind, bytes32 root, bytes32 previousRoot, uint64 recordCount) external onlyRole(ANCHORER_ROLE) {
        if (kind == bytes32(0) || root == bytes32(0) || recordCount == 0) revert BadAnchor();
        Anchor[] storage list = anchors[kind];
        uint256 length = list.length;
        bytes32 expected = length == 0 ? bytes32(0) : list[length - 1].root;
        if (previousRoot != expected) revert StalePreviousRoot(expected);
        if (length != 0 && recordCount < list[length - 1].recordCount) revert RecordCountDecreased(list[length - 1].recordCount);
        list.push(Anchor({root: root, recordCount: recordCount, anchoredAt: uint64(block.timestamp)}));
        emit Anchored(kind, length, root, previousRoot, recordCount);
    }

    function anchorCount(bytes32 kind) external view returns (uint256) {
        return anchors[kind].length;
    }

    function anchorAt(bytes32 kind, uint256 sequence) external view returns (Anchor memory) {
        return anchors[kind][sequence];
    }
}
