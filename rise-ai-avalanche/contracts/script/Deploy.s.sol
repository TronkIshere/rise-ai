// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {Script} from "forge-std/Script.sol";
import {CampaignEscrow} from "../src/CampaignEscrow.sol";
import {RiseAnchor} from "../src/RiseAnchor.sol";
import {RiseVND} from "../src/RiseVND.sol";

/// Deploys simulated VND, the campaign escrow and the anchor, then writes a deployment manifest.
/// The script holds NO key material: the broadcaster is whatever signer forge is given on the command line (an unlocked Anvil
/// account locally; a human-operated wallet for Fuji). The broadcaster becomes admin and simulation treasury.
/// It refuses every chain except 31337 and 43113 and names Mainnet explicitly.
/// Required environment: DEPLOY_ENVIRONMENT (LOCAL_SIMULATION or AVALANCHE_FUJI), OPERATOR, APPROVER, MIN_RELEASE_DELAY (seconds).
/// Optional: ANCHORER (defaults to OPERATOR), PAUSER, TREASURY_SUPPLY (base units, two decimals).
contract Deploy is Script {
    bytes32 internal constant LOCAL = keccak256("LOCAL_SIMULATION");
    bytes32 internal constant FUJI = keccak256("AVALANCHE_FUJI");
    uint256 internal constant DEFAULT_SUPPLY = 100_000_000_000_000;

    error MainnetForbidden(uint256 chainId);
    error UnsupportedChain(uint256 chainId);
    error UnknownEnvironment(string environment);
    error RolesMustDiffer();

    struct Deployment {
        RiseVND token;
        CampaignEscrow escrow;
        RiseAnchor anchor;
    }

    function guard(string memory environment, uint256 chainId) public pure {
        if (chainId == 43114 || chainId == 1) revert MainnetForbidden(chainId);
        bytes32 h = keccak256(bytes(environment));
        if (h == LOCAL) {
            // a LOCAL fork of Fuji reports 43113, so the environment is explicit and the chain id alone never decides
            if (chainId != 31337 && chainId != 43113) revert UnsupportedChain(chainId);
        } else if (h == FUJI) {
            if (chainId != 43113) revert UnsupportedChain(chainId);
        } else {
            revert UnknownEnvironment(environment);
        }
    }

    function run() external returns (Deployment memory d) {
        string memory environment = vm.envString("DEPLOY_ENVIRONMENT");
        guard(environment, block.chainid);
        address operator = vm.envAddress("OPERATOR");
        address approver = vm.envAddress("APPROVER");
        if (operator == approver) revert RolesMustDiffer();
        uint64 minReleaseDelay = uint64(vm.envUint("MIN_RELEASE_DELAY"));
        address anchorer = vm.envOr("ANCHORER", operator);
        address pauser = vm.envOr("PAUSER", address(0));
        uint256 supply = vm.envOr("TREASURY_SUPPLY", DEFAULT_SUPPLY);
        uint256 startBlock = block.number;

        vm.startBroadcast();
        (, address broadcaster,) = vm.readCallers();
        d.token = new RiseVND(broadcaster);
        d.escrow = new CampaignEscrow(broadcaster, d.token, broadcaster, minReleaseDelay);
        d.anchor = new RiseAnchor(broadcaster);
        d.escrow.grantRole(d.escrow.OPERATOR_ROLE(), operator);
        d.escrow.grantRole(d.escrow.APPROVER_ROLE(), approver);
        d.anchor.grantRole(d.anchor.ANCHORER_ROLE(), anchorer);
        if (pauser != address(0)) {
            d.escrow.grantRole(d.escrow.PAUSER_ROLE(), pauser);
        }
        d.token.mint(broadcaster, supply);
        d.token.approve(address(d.escrow), type(uint256).max);
        vm.stopBroadcast();

        _writeManifest(environment, d, broadcaster, operator, approver, anchorer, minReleaseDelay, startBlock);
    }

    function _writeManifest(
        string memory environment,
        Deployment memory d,
        address broadcaster,
        address operator,
        address approver,
        address anchorer,
        uint64 minReleaseDelay,
        uint256 startBlock
    ) private {
        string memory key = "manifest";
        vm.serializeUint(key, "schemaVersion", 2);
        vm.serializeString(key, "environment", environment);
        vm.serializeUint(key, "chainId", block.chainid);
        vm.serializeAddress(key, "escrow", address(d.escrow));
        vm.serializeAddress(key, "token", address(d.token));
        vm.serializeString(key, "tokenKind", "RISE_VND");
        vm.serializeAddress(key, "anchor", address(d.anchor));
        vm.serializeAddress(key, "admin", broadcaster);
        vm.serializeAddress(key, "treasury", broadcaster);
        vm.serializeAddress(key, "operator", operator);
        vm.serializeAddress(key, "approver", approver);
        vm.serializeAddress(key, "anchorer", anchorer);
        vm.serializeUint(key, "minReleaseDelay", minReleaseDelay);
        vm.serializeString(key, "contractVersion", "1");
        string memory json = vm.serializeUint(key, "startBlock", startBlock);
        vm.writeJson(json, string.concat("./deployments/", environment, ".json"));
    }
}
