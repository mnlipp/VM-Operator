# Example setup

The CRD must be deployed independently. 

```sh
kubectl apply -f https://github.com/mnlipp/VM-Operator/raw/main/deploy/crds/vms-crd.yaml
```

Apart from that, the `kustomize.yaml` defines a namespace for the manager 
(and the VMs managed by it) and patches the repository PVC to create
a small volume using a ceph fs.

The `kustomize.yaml` does not include the test VM. Before creating
the test VM, you will again most likely want to change the
disk definition. The sample file claims a ceph block device.
