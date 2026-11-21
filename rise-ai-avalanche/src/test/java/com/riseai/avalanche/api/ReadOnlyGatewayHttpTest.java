package com.riseai.avalanche.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riseai.avalanche.config.AvalancheProperties;
import com.riseai.avalanche.contract.*;
import com.riseai.avalanche.env.AvalancheEnvironment;
import com.riseai.avalanche.startup.ValidatedEnvironment;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ReadOnlyGatewayHttpTest {
 @RestController static class MutationProbe {
  final AtomicInteger calls=new AtomicInteger();
  @RequestMapping(value="/api/v1/avalanche/probe",method={RequestMethod.POST,RequestMethod.PUT,RequestMethod.PATCH,RequestMethod.DELETE}) String mutate(){calls.incrementAndGet();return "mutated";}
 }
 @Test void everyMutatingVerbIsDeniedEvenWithTheCorrectInternalKey()throws Exception{
  var probe=new MutationProbe();var props=new AvalancheProperties(AvalancheEnvironment.AVALANCHE_FUJI,null,null,null,"unit-key",true);
  MockMvc mvc=MockMvcBuilders.standaloneSetup(probe).addFilters(new InternalApiKeyFilter(props,new ObjectMapper().findAndRegisterModules())).build();
  for(var method:new HttpMethod[]{HttpMethod.POST,HttpMethod.PUT,HttpMethod.PATCH,HttpMethod.DELETE})mvc.perform(request(method,"/api/v1/avalanche/probe").header("X-Internal-Api-Key","unit-key")).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("READ_ONLY_MODE"));
  assertThat(probe.calls).hasValue(0);
 }
 @Test void originalInternalKeyPolicyIsUnchangedWhenReadOnlyIsOff()throws Exception{
  var probe=new MutationProbe();var props=new AvalancheProperties(AvalancheEnvironment.LOCAL_SIMULATION,null,null,null,"unit-key");
  var mvc=MockMvcBuilders.standaloneSetup(probe).addFilters(new InternalApiKeyFilter(props,new ObjectMapper().findAndRegisterModules())).build();
  mvc.perform(post("/api/v1/avalanche/probe")).andExpect(status().isUnauthorized());
  mvc.perform(post("/api/v1/avalanche/probe").header("X-Internal-Api-Key","unit-key")).andExpect(status().isOk());assertThat(probe.calls).hasValue(1);
 }
 @Test void signerlessConfigUsesOnlyVerifiedManifestIdentitiesAndRetainsReadEndpoint()throws Exception{
  var manifest=new DeploymentManifest(2,"AVALANCHE_FUJI",43113,"0x"+"11".repeat(20),"0x"+"22".repeat(20),"RISE_VND","0x"+"33".repeat(20),"0x"+"44".repeat(20),"0x"+"44".repeat(20),"0x"+"55".repeat(20),"0x"+"66".repeat(20),"0x"+"77".repeat(20),60,"2",61053011);
  var service=mock(CampaignCommandService.class);when(service.deployment()).thenReturn(new VerifiedDeployment(manifest));when(service.environment()).thenReturn(new ValidatedEnvironment(AvalancheEnvironment.AVALANCHE_FUJI,43113,"avalanchego","api.avax-test.network"));when(service.reader()).thenReturn(mock(CampaignReader.class));
  var props=new AvalancheProperties(AvalancheEnvironment.AVALANCHE_FUJI,null,null,null,"unit-key",true);
  var mvc=MockMvcBuilders.standaloneSetup(new AvalancheController(service)).addFilters(new InternalApiKeyFilter(props,new ObjectMapper().findAndRegisterModules())).build();
  mvc.perform(get("/api/v1/avalanche/config")).andExpect(status().isOk()).andExpect(jsonPath("$.chainId").value(43113)).andExpect(jsonPath("$.operatorAddress").value(manifest.operator())).andExpect(jsonPath("$.approverAddress").value(manifest.approver())).andExpect(jsonPath("$.backendSignersAvailable").value(false));
  verify(service,never()).signerAddress(any());
 }
}
