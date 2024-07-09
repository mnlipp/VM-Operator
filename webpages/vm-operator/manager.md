---
title: VM-Operator: The Manager — Reconciles CRDs and provides a Web-GUI
layout: vm-operator
---

# The Manager

The Manager is the program that provides the controller from the
[operator pattern](https://github.com/cncf/tag-app-delivery/blob/eece8f7307f2970f46f100f51932db106db46968/operator-wg/whitepaper/Operator-WhitePaper_v1-0.md#operator-components-in-kubernetes)
together with a Web-GUI. It should be run in a container in the cluster. 

## Installation

A manager instance manages the VMs in its own namespace. The only
common (and therefore cluster scoped) resource used by all instances
is the CRD. It is available 
[here](https://github.com/mnlipp/VM-Operator/raw/main/deploy/crds/vms-crd.yaml)
and must be created first.

```sh
kubectl apply -f https://github.com/mnlipp/VM-Operator/raw/main/deploy/crds/vms-crd.yaml
```

The example above uses the CRD from the main branch. This is okay if
you apply it once. If you want to preserve the link for automatic
upgrades, you should use a link that points to one of the release branches.

The next step is to create a namespace for the manager and the VMs, e.g. 
`vmop-demo`.

```sh
kubectl create namespace vmop-demo
```

Finally you have to create an account, the role, the binding etc. The 
default files for creating these resources using the default namespace 
can be found in the 
[deploy](https://github.com/mnlipp/VM-Operator/tree/main/deploy)
directory. I recommend to use 
[kustomize](https://kubernetes.io/docs/tasks/manage-kubernetes-objects/kustomization/) to create your own configuration. 

## Initial Configuration

Use one of the `kustomize.yaml` files from the
[example](https://github.com/mnlipp/VM-Operator/tree/main/example) directory 
as a starting point. The directory contains two examples. Here's the file
from subdirectory `local-path`:

```yaml
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization

resources:
# Again, I recommend to use the deploy directory from a
# release branch for anything but test environments.
- https://github.com/mnlipp/VM-Operator/deploy

namespace: vmop-demo

patches:
- patch: |-
    kind: PersistentVolumeClaim
    apiVersion: v1
    metadata:
      name: vmop-image-repository
    spec:
      # Default is ReadOnlyMany
      accessModes:
      - ReadWriteOnce
      resources:
        requests:
          # Default is 100Gi
          storage: 10Gi
      # Default is to use the default storage class
      storageClassName: local-path

- patch: |-
    kind: ConfigMap
    apiVersion: v1
    metadata:
      name: vm-operator
    data:
      config.yaml: |
        "/Manager":
          # "/GuiHttpServer":
            # See section about the GUI
          "/Controller":
            "/Reconciler":
              runnerDataPvc:
                # Default is to use the default storage class
                storageClassName: local-path
```

The sample file adds a namespace (`vmop-demo`) to all resource 
definitions and patches the PVC `vmop-image-repository`. This is a volume
that is mounted into all pods that run a VM. The volume is intended 
to be used as a common repository for CDROM images. The PVC must exist
and it must be bound before any pods can run.

The second patch affects the small volume that is created for each
runner and contains the VM's configuration data such as the EFI vars.
The manager's default configuration causes the PVC for this volume
to be created with no storage class (which causes the default storage
class to be used). The patch provides a new configuration file for 
the manager that makes the reconciler use local-path as storage 
class for this PVC. Details about the manager configuration can be 
found in the next section.

Note that you need none of the patches if you are fine with using your 
cluster's default storage class and this class supports ReadOnlyMany as 
access mode.

Check that the pod with the manager is running:

```sh
kubectl -n vmop-demo get pods -l app.kubernetes.io/name=vm-operator
```

Proceed to the description of [the controller](controller.html)
for creating your first VM.

## Configuration Details

The [config map](https://github.com/mnlipp/VM-Operator/blob/main/deploy/vmop-config-map.yaml) 
for the manager may provide a configuration file (`config.yaml`) and 
a file with logging properties (`logging.properties`). Both files are mounted
into the container that runs the manager and are evaluated by the manager
on startup. If no files are provided, the manager uses built-in defaults.

The configuration file for the Manager follows the conventions of
the [JGrapes](https://jgrapes.org/) component framework.
The keys that start with a slash select the component within the 
application's component hierarchy. The mapping associated with the
selected component configures this component's properties.

The available configuration options for the components can be found
in their respective JavaDocs (e.g. 
[here](latest-release/javadoc/org/jdrupes/vmoperator/manager/Reconciler.html)
for the Reconciler).

## Development Configuration

The [dev-example](https://github.com/mnlipp/VM-Operator/tree/main/dev-example)
directory contains a `kustomize.yaml` that uses the development namespace 
`vmop-dev` and creates a deployment for the manager with 0 replicas.

This environment can be used for running the manager in the IDE. As the 
namespace to manage cannot be detected from the environment, you must use
 `-c ../dev-example/config.yaml` as argument when starting the manager. This 
configures it to use the namespace `vmop-dev`.
