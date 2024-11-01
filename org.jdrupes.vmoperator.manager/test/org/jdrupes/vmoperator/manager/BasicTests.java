package org.jdrupes.vmoperator.manager;

import io.kubernetes.client.Discovery.APIResource;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.util.generic.options.ListOptions;
import java.io.FileReader;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.jdrupes.vmoperator.common.Constants.APP_NAME;
import static org.jdrupes.vmoperator.common.Constants.VM_OP_GROUP;
import static org.jdrupes.vmoperator.common.Constants.VM_OP_KIND_VM;
import static org.jdrupes.vmoperator.common.Constants.VM_OP_NAME;
import org.jdrupes.vmoperator.common.DataPath;
import org.jdrupes.vmoperator.common.K8s;
import org.jdrupes.vmoperator.common.K8sClient;
import org.jdrupes.vmoperator.common.K8sDynamicStub;
import org.jdrupes.vmoperator.common.K8sV1ConfigMapStub;
import org.jdrupes.vmoperator.common.K8sV1DeploymentStub;
import org.jdrupes.vmoperator.common.K8sV1PvcStub;
import org.junit.jupiter.api.AfterAll;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

class BasicTests {

    private static K8sClient client;
    private static APIResource vmsContext;
    private static K8sV1DeploymentStub mgrDeployment;
    private static final Object EXISTS = new Object();

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
            = K8sV1DeploymentStub.get(client, "vmop-dev", "vm-operator");
        mgrDeployment.scale(0);
        mgrDeployment.scale(1);
        waitForManager();

        // Load from Yaml
        var rdr = new FileReader("test-resources/basic-vm.yaml");
        var vmStub = K8sDynamicStub.createFromYaml(client, vmsContext, rdr);
        assertTrue(vmStub.model().isPresent());

        // Wait for created resources
        waitForConfigMap(client);
        waitForRunnerPvc(client);
    }

    private static void waitForManager()
            throws ApiException, InterruptedException {
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

    private static void waitForConfigMap(K8sClient client)
            throws InterruptedException, ApiException {
        var stub = K8sV1ConfigMapStub.get(client, "vmop-dev", "unittest-vm");
        for (int i = 0; i < 10; i++) {
            if (stub.model().isPresent()) {
                return;
            }
            Thread.sleep(1000);
        }
        fail("config map not deployed.");
    }

    private static void waitForRunnerPvc(K8sClient client)
            throws InterruptedException, ApiException {
        var stub
            = K8sV1PvcStub.get(client, "vmop-dev", "unittest-vm-runner-data");
        for (int i = 0; i < 10; i++) {
            if (stub.model().isPresent()) {
                return;
            }
            Thread.sleep(1000);
        }
        fail("runner data pvc not deployed.");
    }

    @AfterAll
    static void tearDownAfterClass() throws Exception {
        // Cleanup
        K8sDynamicStub.get(client, vmsContext, "vmop-dev", "unittest-vm")
            .delete();
        ListOptions listOpts = new ListOptions();
        listOpts.setLabelSelector(
            "app.kubernetes.io/managed-by=" + VM_OP_NAME + ","
                + "app.kubernetes.io/name=" + APP_NAME + ","
                + "app.kubernetes.io/instance=unittest-vm");
        var knownPvcs = K8sV1PvcStub.list(client, "vmop-dev", listOpts);
        for (var pvc : knownPvcs) {
            pvc.delete();
        }

        // Bring down manager
        mgrDeployment.scale(0);
    }

    @Test
    void testConfigMap()
            throws IOException, InterruptedException, ApiException {
        // Check config map
        var config = K8sV1ConfigMapStub.get(client, "vmop-dev", "unittest-vm")
            .model().get();
        Map<List<? extends Object>, Object> toCheck = Map.of(
            List.of("namespace"), "vmop-dev",
            List.of("name"), "unittest-vm",
            List.of("labels", "app.kubernetes.io/name"), Constants.APP_NAME,
            List.of("labels", "app.kubernetes.io/instance"), "unittest-vm",
            List.of("labels", "app.kubernetes.io/managed-by"),
            Constants.VM_OP_NAME,
            List.of("annotations", "vmoperator.jdrupes.org/version"), EXISTS,
            List.of("ownerReferences", 0, "apiVersion"),
            vmsContext.getGroup() + "/" + vmsContext.getVersions().get(0),
            List.of("ownerReferences", 0, "kind"), Constants.VM_OP_KIND_VM,
            List.of("ownerReferences", 0, "name"), "unittest-vm",
            List.of("ownerReferences", 0, "uid"), EXISTS);
        checkProps(config.getMetadata(), toCheck);

        toCheck = new LinkedHashMap<>();
        toCheck.put(List.of("/Runner", "guestShutdownStops"), false);
        toCheck.put(List.of("/Runner", "cloudInit", "metaData", "instance-id"),
            EXISTS);
        toCheck.put(
            List.of("/Runner", "cloudInit", "metaData", "local-hostname"),
            "unittest-vm");
        toCheck.put(List.of("/Runner", "cloudInit", "userData"), Map.of());
        toCheck.put(List.of("/Runner", "vm", "maximumRam"), "4 GiB");
        toCheck.put(List.of("/Runner", "vm", "currentRam"), "2 GiB");
        toCheck.put(List.of("/Runner", "vm", "maximumCpus"), 4);
        toCheck.put(List.of("/Runner", "vm", "currentCpus"), 2);
        toCheck.put(List.of("/Runner", "vm", "powerdownTimeout"), 1);
        toCheck.put(List.of("/Runner", "vm", "network", 0, "type"), "user");
        toCheck.put(List.of("/Runner", "vm", "drives", 0, "type"), "ide-cd");
        toCheck.put(List.of("/Runner", "vm", "drives", 0, "file"),
            "https://test.com/test.iso");
        toCheck.put(List.of("/Runner", "vm", "drives", 0, "bootindex"), 0);
        toCheck.put(List.of("/Runner", "vm", "drives", 1, "type"), "ide-cd");
        toCheck.put(List.of("/Runner", "vm", "drives", 1, "file"),
            "/var/local/vmop-image-repository/image.iso");
        toCheck.put(List.of("/Runner", "vm", "display", "outputs"), 2);
        toCheck.put(List.of("/Runner", "vm", "display", "spice", "port"), 5812);
        toCheck.put(
            List.of("/Runner", "vm", "display", "spice", "usbRedirects"), 2);
        var cm = new Yaml(new SafeConstructor(new LoaderOptions()))
            .load(config.getData().get("config.yaml"));
        checkProps(cm, toCheck);
    }

    private void checkProps(Object obj,
            Map<? extends List<? extends Object>, Object> toCheck) {
        for (var entry : toCheck.entrySet()) {
            var prop = DataPath.get(obj, entry.getKey().toArray());
            assertTrue(prop.isPresent(), () -> "Property " + entry.getKey()
                + " not found in " + obj);

            // Check for existance only
            if (entry.getValue() == EXISTS) {
                continue;
            }
            assertEquals(entry.getValue(), prop.get());
        }
    }

}
