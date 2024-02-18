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
    maximumRam: 8Gi
    currentRam: 4Gi
  
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

The central resource created by the controller is a 
[stateful set](https://kubernetes.io/docs/concepts/workloads/controllers/statefulset/)
with the same name as the VM (metadata.name). Its number of replicas is
set to 1 if `spec.vm.state` is "Running" (default is "Stopped" which sets replicas
to 0).

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
using the string from `.volumeClaimTemplate.metadata.name` as *name*. 
If no name is defined in the metadata, then "/dev/disk-*n*"
is used instead, with *n* being the index of the disk 
definition in the list of disks. 

Apart from appending "-disk" to the name (or generating the name) the
`volumeClaimTemplate` is simply copied into the stateful set definition 
for the VM (with some additional labels, see below). The controller 
for stateful sets appends the started pod's name to the name of the 
volume claim templates when it creates the PVCs. Therefore you'll 
eventually find the PVCs as "*name*-disk-*vmName*-0"
(or "disk-*n*-*vmName*-0"). 

PVCs generated from stateful set definitions are considered "precious"
and never removed automatically. This behavior fits perfectly for VMs.
Usually, you do not want the disks to be removed automatically when
you (maybe accidentally) remove the CR for the VM. To simplify the lookup
for an eventual (manual) removal, all PVCs are labeled with 
"app.kubernetes.io/name: vm-runner", "app.kubernetes.io/instance: *vmName*",
and "app.kubernetes.io/managed-by: vm-operator".

## Choosing an image for the runner

The image used for the runner can be configured with 
[`spec.image`](https://github.com/mnlipp/VM-Operator/blob/7e094e720b7b59a5e50f4a9a4ad29a6000ec76e6/deploy/crds/vms-crd.yaml#L19).
This is a mapping with either a single key `source` or a detailed
configuration using the keys `repository`, `path` etc.

Currently two runner images are maintained. One that is based on 
Arch Linux (`ghcr.io/mnlipp/org.jdrupes.vmoperator.runner.qemu-arch`) and a 
second one based on Alpine (`ghcr.io/mnlipp/org.jdrupes.vmoperator.runner.qemu-alpine`).

Starting with release 1.0, all versions of runner images and managers 
that have the same major release number are guaranteed to be compatible.

## Generating cloud-init data

*Since: 2.3.0* 

The optional object `.spec.cloudInit` with sub-objects `.cloudInit.metaData`
and `.cloudInit.userData` can be used to provide data for
[cloud-init](https://cloudinit.readthedocs.io/en/latest/index.html).
The data from the CRD will be made available to the VM by the runner
as a vfat formatted disk (see the description of 
[NoCloud](https://cloudinit.readthedocs.io/en/latest/reference/datasources/nocloud.html)).

If `.metaData.instance-id` is not defined, the controller automatically
generates it from the CRD's `resourceVersion`. If `.metaData.local-hostname`
is not defined, the controller adds this property using the value from
`metadata.name`.

Note that there is no schema definition available for `.userData`.
Whatever is defined in the CRD is copied to the corresponding
cloud-init file without any checks. (The introductory comment
`#cloud-config` required in that file is generated automatically by
the runner.)

## Further reading

For a detailed description of the available configuration options see the
[CRD](https://github.com/mnlipp/VM-Operator/blob/main/deploy/crds/vms-crd.yaml).
