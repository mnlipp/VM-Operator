package org.jdrupes.vmoperator.manager;

import io.kubernetes.client.Discovery.APIResource;
import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.util.generic.options.ListOptions;
import io.kubernetes.client.util.generic.options.PatchOptions;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.jdrupes.vmoperator.common.Constants.APP_NAME;
import static org.jdrupes.vmoperator.common.Constants.COMP_DISPLAY_SECRET;
import static org.jdrupes.vmoperator.common.Constants.VM_OP_GROUP;
import static org.jdrupes.vmoperator.common.Constants.VM_OP_KIND_VM;
import static org.jdrupes.vmoperator.common.Constants.VM_OP_NAME;
import org.jdrupes.vmoperator.common.K8s;
import org.jdrupes.vmoperator.common.K8sClient;
import org.jdrupes.vmoperator.common.K8sDynamicStub;
import org.jdrupes.vmoperator.common.K8sV1ConfigMapStub;
import org.jdrupes.vmoperator.common.K8sV1DeploymentStub;
import org.jdrupes.vmoperator.common.K8sV1PodStub;
import org.jdrupes.vmoperator.common.K8sV1PvcStub;
import org.jdrupes.vmoperator.common.K8sV1SecretStub;
import org.jdrupes.vmoperator.common.K8sV1ServiceStub;
import org.jdrupes.vmoperator.util.DataPath;
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
    private static K8sDynamicStub vmStub;
    private static final String VM_NAME = "unittest-vm";
    private static final Object EXISTS = new Object();

    @BeforeAll
    static void setUpBeforeClass() throws Exception {
        var testCluster = System.getProperty("k8s.testCluster");
        assertNotNull(testCluster);

        // Get client
        client = new K8sClient();

        // Update manager pod by scaling deployment
        mgrDeployment
            = K8sV1DeploymentStub.get(client, "vmop-dev", "vm-operator");
        mgrDeployment.scale(0);
        mgrDeployment.scale(1);
        waitForManager();

        // Context for working with our CR
        var apiRes = K8s.context(client, VM_OP_GROUP, null, VM_OP_KIND_VM);
        assertTrue(apiRes.isPresent());
        vmsContext = apiRes.get();

        // Cleanup existing VM
        K8sDynamicStub.get(client, vmsContext, "vmop-dev", VM_NAME)
            .delete();
        ListOptions listOpts = new ListOptions();
        listOpts.setLabelSelector("app.kubernetes.io/name=" + APP_NAME + ","
            + "app.kubernetes.io/instance=" + VM_NAME + ","
            + "app.kubernetes.io/component=" + COMP_DISPLAY_SECRET);
        var secrets = K8sV1SecretStub.list(client, "vmop-dev", listOpts);
        for (var secret : secrets) {
            secret.delete();
        }
        deletePvcs();

        // Load from Yaml
        var rdr = new FileReader("test-resources/basic-vm.yaml");
        vmStub = K8sDynamicStub.createFromYaml(client, vmsContext, rdr);
        assertTrue(vmStub.model().isPresent());
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

    private static void deletePvcs() throws ApiException {
        ListOptions listOpts = new ListOptions();
        listOpts.setLabelSelector(
            "app.kubernetes.io/managed-by=" + VM_OP_NAME + ","
                + "app.kubernetes.io/name=" + APP_NAME + ","
                + "app.kubernetes.io/instance=" + VM_NAME);
        var knownPvcs = K8sV1PvcStub.list(client, "vmop-dev", listOpts);
        for (var pvc : knownPvcs) {
            pvc.delete();
        }
    }

    @AfterAll
    static void tearDownAfterClass() throws Exception {
        // Cleanup
        K8sDynamicStub.get(client, vmsContext, "vmop-dev", VM_NAME)
            .delete();
        deletePvcs();

        // Bring down manager
        mgrDeployment.scale(0);
    }

    @Test
    void testConfigMap()
            throws IOException, InterruptedException, ApiException {
        K8sV1ConfigMapStub stub
            = K8sV1ConfigMapStub.get(client, "vmop-dev", VM_NAME);
        for (int i = 0; i < 10; i++) {
            if (stub.model().isPresent()) {
                break;
            }
            Thread.sleep(1000);
        }
        // Check config map
        var config = stub.model().get();
        Map<List<? extends Object>, Object> toCheck = Map.of(
            List.of("namespace"), "vmop-dev",
            List.of("name"), VM_NAME,
            List.of("labels", "app.kubernetes.io/name"), Constants.APP_NAME,
            List.of("labels", "app.kubernetes.io/instance"), VM_NAME,
            List.of("labels", "app.kubernetes.io/managed-by"),
            Constants.VM_OP_NAME,
            List.of("annotations", "vmoperator.jdrupes.org/version"), EXISTS,
            List.of("ownerReferences", 0, "apiVersion"),
            vmsContext.getGroup() + "/" + vmsContext.getVersions().get(0),
            List.of("ownerReferences", 0, "kind"), Constants.VM_OP_KIND_VM,
            List.of("ownerReferences", 0, "name"), VM_NAME,
            List.of("ownerReferences", 0, "uid"), EXISTS);
        checkProps(config.getMetadata(), toCheck);

        toCheck = new LinkedHashMap<>();
        toCheck.put(List.of("/Runner", "guestShutdownStops"), false);
        toCheck.put(List.of("/Runner", "cloudInit", "metaData", "instance-id"),
            EXISTS);
        toCheck.put(
            List.of("/Runner", "cloudInit", "metaData", "local-hostname"),
            VM_NAME);
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
        toCheck.put(List.of("/Runner", "vm", "drives", 2, "type"), "raw");
        toCheck.put(List.of("/Runner", "vm", "drives", 2, "resource"),
            "/dev/system-disk");
        toCheck.put(List.of("/Runner", "vm", "drives", 3, "type"), "raw");
        toCheck.put(List.of("/Runner", "vm", "drives", 3, "resource"),
            "/dev/disk-1");
        toCheck.put(List.of("/Runner", "vm", "display", "outputs"), 2);
        toCheck.put(List.of("/Runner", "vm", "display", "spice", "port"), 5812);
        toCheck.put(
            List.of("/Runner", "vm", "display", "spice", "usbRedirects"), 2);
        var cm = new Yaml(new SafeConstructor(new LoaderOptions()))
            .load(config.getData().get("config.yaml"));
        checkProps(cm, toCheck);
    }

    @Test
    void testDisplaySecret() throws ApiException, InterruptedException {
        ListOptions listOpts = new ListOptions();
        listOpts.setLabelSelector("app.kubernetes.io/name=" + APP_NAME + ","
            + "app.kubernetes.io/instance=" + VM_NAME + ","
            + "app.kubernetes.io/component=" + COMP_DISPLAY_SECRET);
        Collection<K8sV1SecretStub> secrets = null;
        for (int i = 0; i < 10; i++) {
            secrets = K8sV1SecretStub.list(client, "vmop-dev", listOpts);
            if (secrets.size() > 0) {
                break;
            }
            Thread.sleep(1000);
        }
        assertEquals(1, secrets.size());
        var secretData = secrets.iterator().next().model().get().getData();
        checkProps(secretData, Map.of(
            List.of("display-password"), EXISTS));
        assertEquals("now", new String(secretData.get("password-expiry")));
    }

    @Test
    void testRunnerPvc() throws ApiException, InterruptedException {
        var stub
            = K8sV1PvcStub.get(client, "vmop-dev", VM_NAME + "-runner-data");
        for (int i = 0; i < 10; i++) {
            if (stub.model().isPresent()) {
                break;
            }
            Thread.sleep(1000);
        }
        var pvc = stub.model().get();
        checkProps(pvc.getMetadata(), Map.of(
            List.of("labels", "app.kubernetes.io/name"), Constants.APP_NAME,
            List.of("labels", "app.kubernetes.io/instance"), VM_NAME,
            List.of("labels", "app.kubernetes.io/managed-by"),
            Constants.VM_OP_NAME));
        checkProps(pvc.getSpec(), Map.of(
            List.of("resources", "requests", "storage"),
            Quantity.fromString("1Mi")));
    }

    @Test
    void testSystemDiskPvc() throws ApiException, InterruptedException {
        var stub
            = K8sV1PvcStub.get(client, "vmop-dev", VM_NAME + "-system-disk");
        for (int i = 0; i < 10; i++) {
            if (stub.model().isPresent()) {
                break;
            }
            Thread.sleep(1000);
        }
        var pvc = stub.model().get();
        checkProps(pvc.getMetadata(), Map.of(
            List.of("labels", "app.kubernetes.io/name"), Constants.APP_NAME,
            List.of("labels", "app.kubernetes.io/instance"), VM_NAME,
            List.of("labels", "app.kubernetes.io/managed-by"),
            Constants.VM_OP_NAME,
            List.of("annotations", "use_as"), "system-disk"));
        checkProps(pvc.getSpec(), Map.of(
            List.of("resources", "requests", "storage"),
            Quantity.fromString("1Gi")));
    }

    @Test
    void testDisk1Pvc() throws ApiException, InterruptedException {
        var stub
            = K8sV1PvcStub.get(client, "vmop-dev", VM_NAME + "-disk-1");
        for (int i = 0; i < 10; i++) {
            if (stub.model().isPresent()) {
                break;
            }
            Thread.sleep(1000);
        }
        var pvc = stub.model().get();
        checkProps(pvc.getMetadata(), Map.of(
            List.of("labels", "app.kubernetes.io/name"), Constants.APP_NAME,
            List.of("labels", "app.kubernetes.io/instance"), VM_NAME,
            List.of("labels", "app.kubernetes.io/managed-by"),
            Constants.VM_OP_NAME));
        checkProps(pvc.getSpec(), Map.of(
            List.of("resources", "requests", "storage"),
            Quantity.fromString("1Gi")));
    }

    @Test
    void testPod() throws ApiException, InterruptedException {
        PatchOptions opts = new PatchOptions();
        opts.setForce(true);
        opts.setFieldManager("kubernetes-java-kubectl-apply");
        assertTrue(vmStub.patch(V1Patch.PATCH_FORMAT_JSON_PATCH,
            new V1Patch("[{\"op\": \"replace\", \"path\": \"/spec/vm/state"
                + "\", \"value\": \"Running\"}]"),
            client.defaultPatchOptions()).isPresent());
        var stub = K8sV1PodStub.get(client, "vmop-dev", VM_NAME);
        for (int i = 0; i < 20; i++) {
            if (stub.model().isPresent()) {
                break;
            }
            Thread.sleep(1000);
        }
        var pod = stub.model().get();
        checkProps(pod.getMetadata(), Map.of(
            List.of("labels", "app.kubernetes.io/name"), APP_NAME,
            List.of("labels", "app.kubernetes.io/instance"), VM_NAME,
            List.of("labels", "app.kubernetes.io/component"), APP_NAME,
            List.of("labels", "app.kubernetes.io/managed-by"),
            Constants.VM_OP_NAME,
            List.of("annotations", "vmrunner.jdrupes.org/cmVersion"), EXISTS,
            List.of("annotations", "vmoperator.jdrupes.org/version"), EXISTS,
            List.of("ownerReferences", 0, "apiVersion"),
            vmsContext.getGroup() + "/" + vmsContext.getVersions().get(0),
            List.of("ownerReferences", 0, "kind"), Constants.VM_OP_KIND_VM,
            List.of("ownerReferences", 0, "name"), VM_NAME,
            List.of("ownerReferences", 0, "uid"), EXISTS));
        checkProps(pod.getSpec(), Map.of(
            List.of("containers", 0, "image"), EXISTS,
            List.of("containers", 0, "name"), VM_NAME,
            List.of("containers", 0, "resources", "requests", "cpu"),
            Quantity.fromString("1")));
    }

    @Test
    public void testLoadBalancer() throws ApiException, InterruptedException {
        var stub = K8sV1ServiceStub.get(client, "vmop-dev", VM_NAME);
        for (int i = 0; i < 10; i++) {
            if (stub.model().isPresent()) {
                break;
            }
            Thread.sleep(1000);
        }
        var svc = stub.model().get();
        checkProps(svc.getMetadata(), Map.of(
            List.of("labels", "app.kubernetes.io/name"), APP_NAME,
            List.of("labels", "app.kubernetes.io/instance"), VM_NAME,
            List.of("labels", "app.kubernetes.io/managed-by"), VM_OP_NAME,
            List.of("labels", "label1"), "label1",
            List.of("labels", "label2"), "replaced",
            List.of("labels", "label3"), "added",
            List.of("annotations", "metallb.universe.tf/loadBalancerIPs"),
            "192.168.168.1",
            List.of("annotations", "anno1"), "added"));
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
