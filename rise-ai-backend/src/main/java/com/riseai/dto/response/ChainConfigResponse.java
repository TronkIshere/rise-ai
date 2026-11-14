package com.riseai.dto.response;

/**
 * Which chain the escrow mirror runs on, for links and badges. onChain is false with the simulated engine; available is false when the
 * gateway cannot be reached, in which case only the explorer links are known. Addresses only: no keys, no signer details.
 * guardianAddress is the independent wallet that can hold releases on-chain; guardianHoldMaxSeconds is how long a hold lasts at most.
 */
public record ChainConfigResponse(boolean onChain, boolean available, String network, Long chainId, String escrowContract, String tokenContract,
                                  String tokenKind, String anchorContract, String contractVersion, Boolean paused, String beneficiaryAddress,
                                  String explorerTxUrl, String explorerAddressUrl, String guardianAddress, Long guardianHoldMaxSeconds) {

    public static ChainConfigResponse offChain() {
        return new ChainConfigResponse(false, false, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }
}
