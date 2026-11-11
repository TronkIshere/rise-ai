// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {Test} from "forge-std/Test.sol";
import {Deploy} from "../script/Deploy.s.sol";

contract DeployGuardTest is Test {
    Deploy internal script;
    address internal operator = makeAddr("operator");
    address internal approver = makeAddr("approver");

    function setUp() public {
        script = new Deploy();
    }

    function _env(address op, address app) internal {
        vm.setEnv("DEPLOY_ENVIRONMENT", "LOCAL_SIMULATION");
        vm.setEnv("OPERATOR", vm.toString(op));
        vm.setEnv("APPROVER", vm.toString(app));
        vm.setEnv("MIN_RELEASE_DELAY", "300");
    }

    function test_mainnetChainIdsAreRefusedInEveryEnvironment() public {
        string[2] memory envs = ["LOCAL_SIMULATION", "AVALANCHE_FUJI"];
        uint256[2] memory forbidden = [uint256(43114), uint256(1)];
        for (uint256 e = 0; e < 2; e++) {
            for (uint256 c = 0; c < 2; c++) {
                vm.expectRevert(abi.encodeWithSelector(Deploy.MainnetForbidden.selector, forbidden[c]));
                script.guard(envs[e], forbidden[c]);
            }
        }
    }

    function test_onlyTheTwoSupportedChainsAreAcceptedPerEnvironment() public {
        script.guard("LOCAL_SIMULATION", 31337);
        script.guard("LOCAL_SIMULATION", 43113);
        script.guard("AVALANCHE_FUJI", 43113);
        vm.expectRevert(abi.encodeWithSelector(Deploy.UnsupportedChain.selector, uint256(31337)));
        script.guard("AVALANCHE_FUJI", 31337);
        vm.expectRevert(abi.encodeWithSelector(Deploy.UnsupportedChain.selector, uint256(999)));
        script.guard("LOCAL_SIMULATION", 999);
    }

    function test_theEnvironmentMustBeNamedAndKnown() public {
        vm.expectRevert(abi.encodeWithSelector(Deploy.UnknownEnvironment.selector, "MAINNET"));
        script.guard("MAINNET", 43115);
        vm.expectRevert(abi.encodeWithSelector(Deploy.UnknownEnvironment.selector, ""));
        script.guard("", 31337);
    }

    // vm.setEnv is process-wide and forge runs test functions in parallel, so every case that reads the deploy
    // environment runs from this one test, in sequence.
    function test_environmentDrivenRuns() public {
        _runOnTheWrongChainRevertsBeforeDeployingAnything();
        _operatorAndApproverMustDiffer();
        _localRunDeploysGrantsRolesFundsTreasuryAndWritesTheManifest();
    }

    function _runOnTheWrongChainRevertsBeforeDeployingAnything() internal {
        _env(operator, approver);
        vm.chainId(43114);
        vm.expectRevert(abi.encodeWithSelector(Deploy.MainnetForbidden.selector, uint256(43114)));
        script.run();
    }

    function _operatorAndApproverMustDiffer() internal {
        _env(operator, operator);
        vm.chainId(31337);
        vm.expectRevert(Deploy.RolesMustDiffer.selector);
        script.run();
    }

    function _localRunDeploysGrantsRolesFundsTreasuryAndWritesTheManifest() internal {
        _env(operator, approver);
        vm.chainId(31337);
        Deploy.Deployment memory d = script.run();
        assertTrue(d.escrow.hasRole(d.escrow.OPERATOR_ROLE(), operator));
        assertTrue(d.escrow.hasRole(d.escrow.APPROVER_ROLE(), approver));
        assertFalse(d.escrow.hasRole(d.escrow.APPROVER_ROLE(), operator));
        assertTrue(d.anchor.hasRole(d.anchor.ANCHORER_ROLE(), operator));
        assertEq(address(d.escrow.token()), address(d.token));
        assertEq(d.escrow.minReleaseDelay(), 300);
        address treasury = d.escrow.treasury();
        assertEq(d.token.allowance(treasury, address(d.escrow)), type(uint256).max);
        assertGt(d.token.balanceOf(treasury), 0);
        string memory manifest = vm.readFile("./deployments/LOCAL_SIMULATION.json");
        assertEq(vm.parseJsonUint(manifest, ".schemaVersion"), 2);
        assertEq(vm.parseJsonString(manifest, ".tokenKind"), "RISE_VND");
        assertEq(vm.parseJsonAddress(manifest, ".escrow"), address(d.escrow));
        assertEq(vm.parseJsonAddress(manifest, ".anchor"), address(d.anchor));
    }
}
