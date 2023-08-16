---
layout: default
title: VM-Operator Controller
---

# The Controller

The controller component (which is part of the manager) monitors 
custom resources of kind `VirtualMachine`. It creates or modifies 
other resources in the cluster as required to get the VM defined
by the CR up and running. 

Here is the sample definition of a VM from the 
["local-path" example](https://github.com/mnlipp/VM-Operator/tree/main/example/local-path):

```yaml
apiVersion: "vmoperator.jdrupes.org/v1"
kind: VirtualMachine
metadata:
  namespace: vmop-demo
  name: test-vm
spec:

  vm:
    state: Running
    maximumCpus: 4
    currentCpus: 2
    maximumRam: "8 GiB"
    currentRam: "4 GiB"
  
    networks:
    - user: {}
    
    disks:
    - volumeClaimTemplate:
        metadata:
          name: system
        spec:
          storageClassName: ""
          selector:
            matchLabels:
              app.kubernetes.io/name: vmrunner
              app.kubernetes.io/instance: test-vm
              vmrunner.jdrupes.org/disk: system
          resources:
            requests:
              storage: 40Gi
    - cdrom:
        image: ""
        # image: https://download.fedoraproject.org/pub/fedora/linux/releases/38/Workstation/x86_64/iso/Fedora-Workstation-Live-x86_64-38-1.6.iso
        # image: "Fedora-Workstation-Live-x86_64-38-1.6.iso"

    display:
      spice:
        port: 5910
```

The name from .metadata.name is used to generate a stateful set 
with this name that controls the pod with the VM.

## Defining the basics

How to define the number of CPUs and the size of the RAM of the VM
should be obvious from the example. Note that changes of the current
number of CPUs and the current RAM size will be propagated to
running VMs.

## Defining disks

Maybe the most interesting part is the definition of the VM's disks.
This is done by adding one or more `volumeClaimTemplate`s to the
list of disks. As its name suggests, such a template is used by the
controller to generate a PVC.

The example template does not define any storage. Rather it references
some PV that you must have created first. This may be your first approach
if you have existing storage from running the VM outside Kubernetes
(e.g. with libvirtd).

If you have ceph or some other full fledged storage provider installed
and create a new VM, provisioning a disk can happen automatically
as shown in this example:

```yaml
    disks:
    - volumeClaimTemplate:
        metadata:
          name: system
        spec:
          storageClassName: rook-ceph-block
          resources:
            requests:
              storage: 40Gi
```

The disk will be available as "/dev/*name*-disk" in the VM,
using .volumeClaimTemplate.metadata.name for *name*. If 
no name is defined in the metadata, then "/dev/*name*-disk"
is used instead, with *n* being the index of the disk
definition in the list of disks. 

The PVC is generated as part of the stateful set that controls
the pod with the VM. As the controller for stateful sets appends the 
stateful set's name to the contained PVCs, you'll eventually
find the PVC as "*name*-disk-*vmName*" (or "disk-*n*-*vmName*" 
in the Kubernetes resources.

## Further reading

For a detailed description of the available configuration options see the
[CRD](https://github.com/mnlipp/VM-Operator/blob/main/deploy/crds/vms-crd.yaml).
