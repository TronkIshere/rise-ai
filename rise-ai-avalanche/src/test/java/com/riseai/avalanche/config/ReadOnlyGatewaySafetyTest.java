package com.riseai.avalanche.config;

import com.riseai.avalanche.chain.ChainClient;
import com.riseai.avalanche.env.AvalancheEnvironment;
import com.riseai.avalanche.env.EnvironmentGuardException;
import com.riseai.avalanche.signer.SignerRegistry;
import com.riseai.avalanche.startup.EnvironmentStartupValidator;
import com.riseai.avalanche.startup.ValidatedEnvironment;
import com.riseai.avalanche.scan.ScanCoordinator;
import com.riseai.avalanche.tx.SubmissionService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class ReadOnlyGatewaySafetyTest {
 @Configuration(proxyBeanMethods=false)
 @EnableConfigurationProperties({AvalancheProperties.class,TxProperties.class})
 static class Wiring {
  @Bean ChainClient chainClient(){return mock(ChainClient.class);}
  @Bean EnvironmentStartupValidator validator(){var v=mock(EnvironmentStartupValidator.class);when(v.validated()).thenReturn(new ValidatedEnvironment(AvalancheEnvironment.AVALANCHE_FUJI,43113,"avalanchego","api.avax-test.network"));return v;}
  @Bean JdbcTemplate avalancheJdbcTemplate(){return mock(JdbcTemplate.class);}
  @Bean TransactionTemplate avalancheTransactionTemplate(){return mock(TransactionTemplate.class);}
 }
 private final ApplicationContextRunner context=new ApplicationContextRunner().withUserConfiguration(Wiring.class,TxConfig.class,ScanConfig.class)
  .withPropertyValues("avalanche.environment=AVALANCHE_FUJI","avalanche.tx.enabled=true","avalanche.tx.reconcile-interval-ms=100","avalanche.signers.enabled=false");
 @Test void readOnlyKeepsReadInfrastructureButRemovesAllBackgroundMutationWorkers(){
  context.withPropertyValues("avalanche.read-only=true","avalanche.scan.enabled=true").run(c->{
   assertThat(c).hasNotFailed();assertThat(c.getBean(AvalancheProperties.class).readOnly()).isTrue();
   assertThat(c).hasBean("deploymentHolder").hasBean("txRepository");
   assertThat(c).doesNotHaveBean("txReconcileScheduler").doesNotHaveBean(ScanCoordinator.class).doesNotHaveBean(SubmissionService.class).doesNotHaveBean(SignerRegistry.class);
   verifyNoInteractions(c.getBean(ChainClient.class));
   var jdbc=c.getBean(JdbcTemplate.class);verify(jdbc).afterPropertiesSet();verifyNoMoreInteractions(jdbc);
  });
 }
 @Test void existingDefaultRetainsTheTransactionWorkerConfiguration(){
  context.withPropertyValues("avalanche.tx.reconcile-interval-ms=0").run(c->{assertThat(c).hasNotFailed().hasBean("txReconcileScheduler");assertThat(c.getBean(AvalancheProperties.class).readOnly()).isFalse();});
 }
 @Test void readonlyWithKeystoreSignersFailsBeforeAnyValidationOrKeyLoading(){
  var p=new AvalancheProperties(AvalancheEnvironment.AVALANCHE_FUJI,null,new AvalancheProperties.Signers(true,"keystore",new AvalancheProperties.Keystore("never-read","never-read","0x0"),null),null,"test-key",true);
  var validator=mock(EnvironmentStartupValidator.class);
  assertThat(assertThrows(EnvironmentGuardException.class,()->new SignerConfig().signerRegistry(p,validator)).code()).isEqualTo("READ_ONLY_SIGNERS_FORBIDDEN");verifyNoInteractions(validator);
 }
}
