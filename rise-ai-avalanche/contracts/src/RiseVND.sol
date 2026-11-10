// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {ERC20} from "@openzeppelin/contracts/token/ERC20/ERC20.sol";
import {Ownable} from "@openzeppelin/contracts/access/Ownable.sol";

/// @notice Simulated Vietnamese dong for Rise AI on LOCAL_SIMULATION and Fuji. It has no value and is not redeemable.
/// Two decimals match the backend ledger's DECIMAL(19,2), so amounts mirror exactly with no conversion.
contract RiseVND is ERC20, Ownable {
    constructor(address owner_) ERC20("Rise Simulated VND", "rVND") Ownable(owner_) {}

    function decimals() public pure override returns (uint8) {
        return 2;
    }

    function mint(address to, uint256 amount) external onlyOwner {
        _mint(to, amount);
    }
}
