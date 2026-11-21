package com.riseai.avalanche.chain;
import org.junit.jupiter.api.Test;
import org.web3j.protocol.Web3j;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
class ReadOnlyChainClientTest {
 @Test void accidentalRawSubmissionIsBlockedBeforePacingOrAnyTransportInteraction(){
  var web3j=mock(Web3j.class);var pacer=mock(RpcPacer.class);var client=new ChainClient(web3j,pacer,true);
  assertThat(assertThrows(ChainClientException.class,()->client.sendRawTransaction("not-a-signed-transaction")).getMessage()).contains("READ_ONLY_MODE");verifyNoInteractions(web3j,pacer);
 }
}
