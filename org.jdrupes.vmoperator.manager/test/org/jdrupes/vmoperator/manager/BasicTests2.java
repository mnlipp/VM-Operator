package org.jdrupes.vmoperator.manager;

import io.kubernetes.client.Discovery.APIResource;
import io.kubernetes.client.openapi.ApiException;
import java.io.FileReader;
import java.io.IOException;
import static org.jdrupes.vmoperator.common.Constants.VM_OP_GROUP;
import static org.jdrupes.vmoperator.common.Constants.VM_OP_KIND_VM;
import org.jdrupes.vmoperator.common.K8s;
import org.jdrupes.vmoperator.common.K8sClient;
import org.jdrupes.vmoperator.common.K8sDynamicStub;
import org.jdrupes.vmoperator.common.K8sGenericStub;
import org.jdrupes.vmoperator.common.K8sV1ConfigMapStub;
import org.jdrupes.vmoperator.common.K8sV1DeploymentStub;
import org.junit.jupiter.api.AfterAll;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BasicTests2 {

    private static K8sClient client;
    private static APIResource vmsContext;
    private static K8sV1DeploymentStub mgrDeployment;

    @BeforeAll
    static void setUpBeforeClass() throws Exception {
        var testCluster = System.getProperty("k8s.testCluster");
        assertNotNull(testCluster);

        // Get client
        client = new K8sClient();

        // Context for working with our CR
        var apiRes = K8s.context(client, VM_OP_GROUP, null, VM_OP_KIND_VM);
        assertTrue(apiRes.isPresent());
        vmsContext = apiRes.get();

        // Cleanup existing VM
        K8sDynamicStub.get(client, vmsContext, "vmop-dev", "unittest-vm")
            .delete();

        // Update manager pod by scaling deployment
        mgrDeployment
            = new K8sV1DeploymentStub(client, "vmop-dev", "vm-operator");
        mgrDeployment.scale(0);
        mgrDeployment.scale(1);

        // Wait until available

        for (int i = 0; i < 10; i++) {
            if (mgrDeployment.model().get().getStatus().getConditions()
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
        mgrDeployment.scale(0);
    }

    @Test
    void test() throws IOException, InterruptedException, ApiException {
        // Load from Yaml
        var rdr = new FileReader("test-resources/unittest-vm.yaml");
        var vmStub = K8sDynamicStub.createFromYaml(client, vmsContext, rdr);

//        // Wait for created resources
//        assertTrue(waitForConfigMap());
//        assertTrue(waitForStatefulSet());
//
//        // Check config map
//        var config = client.configMaps().inNamespace("vmop-dev")
//            .withName("unittest-vm").get();
//        var yaml = new Yaml(new SafeConstructor(new LoaderOptions()))
//            .load((String) config.getData().get("config.yaml"));
//        @SuppressWarnings("unchecked")
//        var currentRam = ((Map<String, Map<String, Map<String, String>>>) yaml)
//            .get("/Runner").get("vm").get("maximumRam");
//        assertEquals("4 GiB", currentRam);
//
//        // Cleanup
//        var resourcesInNamespace = client.genericKubernetesResources(vmsContext)
//            .inNamespace("vmop-dev");
//        resourcesInNamespace.withName("unittest-vm").delete();
    }

//    private boolean waitForConfigMap(K8sClient client)
//            throws InterruptedException {
//        for (int i = 0; i < 10; i++) {
//            if (new K8sV1ConfigMapStub(client, "vmop-dev", "unittest-vm")) {
//                return true;
//            }
//            Thread.sleep(1000);
//        }
//        return false;
//    }

    private boolean waitForStatefulSet() throws InterruptedException {
//        for (int i = 0; i < 10; i++) {
//            if (client.apps().statefulSets().inNamespace("vmop-dev")
//                .withName("unittest-vm").get() != null) {
//                return true;
//            }
//            Thread.sleep(1000);
//        }
        return false;
    }

}
