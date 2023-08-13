---
layout: default
title: VM-Operator Controller
---

# The Controller

The controller component (which is part of the manager) does precisely 
what the picture from the 
[Operator Whitepaper](https://github.com/cncf/tag-app-delivery/blob/eece8f7307f2970f46f100f51932db106db46968/operator-wg/whitepaper/Operator-WhitePaper_v1-0.md#operator-components-in-kubernetes) illustrates.

<img src="02_2_operator.png" width="90%"/>

To get anything started, you therefore have to first create a custom 
resource of kind `VirtualMachine`. Here is the sample definition from the 
["local-path" example](https://github.com/mnlipp/VM-Operator/tree/main/example/local-path):

```yaml
apiVersion: "vmoperator.jdrupes.org/v1"
kind: VirtualMachine
metadata:
  namespace: vmop-demo
  name: test-vm
spec:

  # image:
    # Defaults:
    # repository: ghcr.io
    # path: mnlipp/org.jdrupes.vmoperator.runner.qemu-arch
    # version: latest
    # pullPolicy: Always

  vm:
    maximumCpus: 4
    currentCpus: 2
    maximumRam: "8 GiB"
    currentRam: "4 GiB"
  
    networks:
    - user: {}
    
    disks:
    - volumeClaimTemplate:
        metadata:
          name: test-vm-system
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

## Defining disks

Maybe the most important part is the definition of the VM's disks.
This is done by adding one or more `volumeClaimTemplate`s to the
list of disks. As its name suggests, such a template is used by the
controller to generate a PVC. The example does not use storage from
local-path. Rather is references some PV that you must have created 
first. 

Provided that you have enough storage space of class "local-path"
available, you can use "local-path" as "storageClassName" and delete
the "selector".

If you have ceph or some other full fledged storage solution installed,
provisioning a disk can happen automatically as shown in this example:

```yaml
    disks:
    - volumeClaimTemplate:
        metadata:
          name: test-vm-system
        spec:
          storageClassName: rook-ceph-block
          resources:
            requests:
              storage: 40Gi
```

The disk will be available as "/dev/disk-*n*" in the VM, were 
*n* is the index of the disk definition in the list of disks. 
If .volumeClaimTemplate.metadata.name is defined, then "/dev/*name*-disk"
is used instead.

## Further reading

For a detailed description of the available configuration options see the
[CRD](https://github.com/mnlipp/VM-Operator/blob/main/deploy/crds/vms-crd.yaml).
