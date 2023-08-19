package org.jdrupes.vmoperator.manager;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext;
import io.fabric8.kubernetes.client.Config;

class BasicTests {

    private static KubernetesClient client;
    private static ResourceDefinitionContext vmsContext;

    @BeforeAll
    static void setUpBeforeClass() throws Exception {
        var testCluster = System.getProperty("k8s.testCluster");
        assertNotNull(testCluster);

        // Get client
        client = new KubernetesClientBuilder()
            .withConfig(Config.autoConfigure(testCluster)).build();

        // Context for working with our CR
        vmsContext = new ResourceDefinitionContext.Builder()
            .withGroup("vmoperator.jdrupes.org").withKind("VirtualMachine")
            .withPlural("vms").withNamespaced(true).withVersion("v1").build();

        // Cleanup
        var resourcesInNamespace = client.genericKubernetesResources(vmsContext)
            .inNamespace("vmop-dev");
        resourcesInNamespace.withName("unittest-vm").delete();

        // Update pod by scaling deployment
        client.apps().deployments().inNamespace("vmop-dev")
            .withName("vm-operator").scale(0);
        client.apps().deployments().inNamespace("vmop-dev")
            .withName("vm-operator").scale(1);

        // Wait until available
        for (int i = 0; i < 10; i++) {
            if (client.apps().deployments().inNamespace("vmop-dev")
                .withName("vm-operator").get().getStatus().getConditions()
                .stream().filter(c -> "Available".equals(c.getType())).findAny()
                .isPresent()) {
                return;
            }
            Thread.sleep(1000);
        }
        fail("vm-operator not deployed.");
    }

    @AfterAll
    static void tearDownAfterClass() throws Exception {
        // Bring down manager
        client.apps().deployments().inNamespace("vmop-dev")
            .withName("vm-operator").scale(0);
        client.close();
    }

    @Test
    void test() throws IOException, InterruptedException {
        // Load from Yaml
        var vm = client.genericKubernetesResources(vmsContext)
            .load(Files
                .newInputStream(Path.of("test-resources/unittest-vm.yaml")));
        // Create Custom Resource
        vm.create();

        // Wait for created resources
        assertTrue(waitForConfigMap());
        assertTrue(waitForStatefulSet());

        // Cleanup
        var resourcesInNamespace = client.genericKubernetesResources(vmsContext)
            .inNamespace("vmop-dev");
        resourcesInNamespace.withName("unittest-vm").delete();
    }

    private boolean waitForConfigMap() throws InterruptedException {
        for (int i = 0; i < 10; i++) {
            if (client.configMaps().inNamespace("vmop-dev")
                .withName("vm-operator").get() != null) {
                return true;
            }
            Thread.sleep(1000);
        }
        return false;
    }

    private boolean waitForStatefulSet() throws InterruptedException {
        for (int i = 0; i < 10; i++) {
            if (client.apps().statefulSets().inNamespace("vmop-dev")
                .withName("unittest-vm").get() != null) {
                return true;
            }
            Thread.sleep(1000);
        }
        return false;
    }

}
