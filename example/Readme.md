# Example setup for development

The CRD must be deployed independently. Apart from that, the 
`kustomize.yaml` defines a namespace for the manager (and the VMs
managed by it). You will most likely want to patch the PVC
for the image repository.

The `kustomize.yaml` does not include the test VM. Before creating
the test VM, you will again most likely want to change the
disk definition.
