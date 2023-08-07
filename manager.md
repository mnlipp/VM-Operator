---
layout: default
title: VM-Operator Manager
---

# The Manager

The Manager is a program that provides the controller, the core 
of the operator, together with a Web-GUI. It should be run in a 
container in the cluster. Sample files for deploying the manager 
can be found
[here](https://github.com/mnlipp/VM-Operator/tree/main/deploy).

## The Controller

The controller watches for custom resources of type VirtualMachine
as defined by the
[CRD](https://github.com/mnlipp/VM-Operator/blob/main/deploy/crds/vms-crd.yaml).

Here's an example of a simple VM definition:

```yaml
apiVersion: "vmoperator.jdrupes.org/v1"
kind: VirtualMachine
metadata:
  namespace: qemu-vms
  name: test-vm
spec:
  vm:
    state: Running
    maximumCpus: 4
    currentCpus: 2
    maximumRam: "8 GiB"
    currentRam: "4 GiB"
    networks:
    - tap: {}
    disks:
    - volumeClaimTemplate:
        spec:
          storageClassName: ""
          resources:
            requests:
              storage: 40Gi
    display:
      spice:
        port: 5910
```

*To be continued.*

## The Web-GUI

*To be continued.*
