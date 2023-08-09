---
layout: default
title: VM-Operator Controller
---

# The Controller

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

