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
common (cluster) resource is the CRD. It is available 
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

Finally you have to create an account, the role, the binding etc. Sample 
files for creating these resources using the default namespace can be found 
in the [deploy](https://github.com/mnlipp/VM-Operator/tree/main/deploy)
directory. I recommend to use 
[kustomize](https://kubernetes.io/docs/tasks/manage-kubernetes-objects/kustomization/) with a copy of the file from the 
[example](https://github.com/mnlipp/VM-Operator/tree/main/example) directory.

The file adds a namespace to all resource definitions and allows you to
patch the PVC for a volume that is mounted into all pods that run a VM
and can be used as a common repository for CDROM images. This PVC must
exist and it must be bound before any pods can be run.

## Running during development