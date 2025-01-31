---
title: "VM-Operator: Controller — Reconciles the VM CRs"
layout: vm-operator
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
  guestShutdownStops: false

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
        # Since 3.0.0:
        # generateSecret: false
```

## Pod management

The central resource created by the controller is a
[`Pod`](https://kubernetes.io/docs/concepts/workloads/pods/)
with the same name as the VM (`metadata.name`). The pod is created only
if `spec.vm.state` is "Running" (default is "Stopped" which deletes the
pod)[^oldSts].

Property `spec.guestShutdownStops` (since 2.2.0) controls the effect of a
shutdown initiated by the guest. If set to `false` (default) the pod
and thus the VM is automatically restarted. If set to `true`, the
VM's state is set to "Stopped" when the VM terminates and the pod is
deleted.

[^oldSts]: Before version 3.4, the operator created a
    [stateful set](https://kubernetes.io/docs/concepts/workloads/controllers/statefulset/)
    that in turn created the pod and the PVCs (see below).

## Defining the basics

How to define the number of CPUs and the size of the RAM of the VM
should be obvious from the example. Note that changes of the current
number of CPUs and the current RAM size will be propagated to
running VMs.

## Defining disks

Maybe the most interesting part is the definition of the VM's disks.
This is done by adding one or more `volumeClaimTemplate`s to the
list of disks. As its name suggests, such a template is used by the
controller to generate a
[`PVC`](https://kubernetes.io/docs/concepts/storage/persistent-volumes/).

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
is used instead, with *n* being the index of the volume claim
template in the list of disks.

The name of the generated PVC is the VM's name with "-*name*-disk"
(or the generated name) appended: "*vmName*-*name*-disk"
(or "*vmName*-disk-*n*"). The definition of the PVC is simply a copy
of the information from the `volumeClaimTemplate` (with some additional
labels, see below)[^oldStsDisks].

[^oldStsDisks]: Before version 3.4 the `volumeClaimTemplate`s were
    copied in the definition of the stateful set. As a stateful set
    appends the started pod's name to the name of the volume claim
    templates when it creates the PVCs, the PVCs' name were
    "*name*-disk-*vmName*-0" (or "disk-*n*-*vmName*-0").

PVCs are never removed automatically. Usually, you do not want your
VMs disks to be removed when you (maybe accidentally) remove the CR
for the VM. To simplify the lookup for an eventual (manual) removal,
all PVCs are labeled with "app.kubernetes.io/name: vm-runner",
"app.kubernetes.io/instance: *vmName*", and
"app.kubernetes.io/managed-by: vm-operator", making it easy to select
the PVCs by label in a delete command.

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

*Since: 2.2.0*

The optional object `.spec.cloudInit` with sub-objects `.cloudInit.metaData`,
`.cloudInit.userData` and `.cloudInit.networkConfig` can be used to provide
data for
[cloud-init](https://cloudinit.readthedocs.io/en/latest/index.html).
The data from the CRD will be made available to the VM by the runner
as a vfat formatted disk (see the description of
[NoCloud](https://cloudinit.readthedocs.io/en/latest/reference/datasources/nocloud.html)).

If `.metaData.instance-id` is not defined, the controller automatically
generates it from the CRD's `resourceVersion`. If `.metaData.local-hostname`
is not defined, the controller adds this property using the value from
`metadata.name`.

Note that there are no schema definitions available for `.userData`
and `.networkConfig`. Whatever is defined in the CRD is copied to
the corresponding cloud-init file without any checks. (The introductory
comment `#cloud-config` required at the beginning of `.userData` is
generated automatically by the runner.)

## Display secret/password

*Since: 2.3.0*

You can define a display password using a Kubernetes secret.
When you start a VM, the controller checks if there is a secret
with labels "app.kubernetes.io/name: vm-runner,
app.kubernetes.io/component: display-secret,
app.kubernetes.io/instance: *vmname*" in the namespace of the
VM definition. The name of the secret can be chosen freely.

```yaml
kind: Secret
apiVersion: v1
metadata:
  name: test-vm-display-secret
  namespace: vmop-demo
  labels:
    app.kubernetes.io/name: vm-runner
    app.kubernetes.io/instance: test-vm
    app.kubernetes.io/component: display-secret
type: Opaque
data:
  display-password: dGVzdC12bQ==
  # Since 3.0.0:
  # password-expiry: bmV2ZXI=
```

If such a secret for the VM is found, the VM is configured to use
the display password specified. The display password in the secret
can be updated while the VM runs[^delay]. Activating/deactivating
the display password while a VM runs is not supported by Qemu and
therefore requires stopping the VM, adding/removing the secret and
restarting the VM.

[^delay]: Be aware of the possible delay, see e.g.
    [here](https://web.archive.org/web/20240223073838/https://ahmet.im/blog/kubernetes-secret-volumes-delay/).

*Since: 3.0.0*

The secret's `data` can have an additional property `data.password-expiry` which
specifies a (base64 encoded) expiry date for the password. Supported
values are those defined by qemu (`+n` seconds from now, `n` Unix
timestamp, `never` and `now`).

Unless `spec.vm.display.spice.generateSecret` is set to `false` in the VM
definition (CRD), the controller creates a secret for the display
password automatically if none is found. The secret is created
with a random password that expires immediately, which makes the
display effectively inaccessible until the secret is modified.
Note that a password set manually may be overwritten by components
of the manager unless the password-expiry is set to "never" or
some time in the future.

## Further reading

For a detailed description of the available configuration options see the
[CRD](https://github.com/mnlipp/VM-Operator/blob/main/deploy/crds/vms-crd.yaml).
