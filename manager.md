---
layout: default
title: VM-Operator Manager
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

Use one of the `kustomize.yaml` files from the
[example](https://github.com/mnlipp/VM-Operator/tree/main/example) directory as starting point.
The directory contains two examples. 

```yaml
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization

resources:
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
          "/Controller":
            runnerData:
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
By default the PVC for this volume is created with the default
storage class configured. The patch effectively provides a new
configuration file for the manager that makes the controller
use local-path as storage class for this PVC.

Check that the pod with the manager is running:

```sh
kubectl -n vmop-demo get pods -l app.kubernetes.io/name=vm-operator
```

Proceed to the description of [the controller](controller.html)
for creating your first VM.

## Running during development

The [dev-example](https://github.com/mnlipp/VM-Operator/tree/main/dev-example)
directory contains a `kustomize.yaml` that uses the development namespace 
`vmop-dev` and creates a deployment for the manager with 0 replicas.

This environment can be used for running the manager in the IDE. As the 
namespace to manage cannot be detected from the environment, you must use
 `-c ../dev-example/config.yaml` as argument when starting the manager. This 
configures it to use the namespace `vmop-dev`.