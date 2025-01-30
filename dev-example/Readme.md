# Example setup for development

The CRD must be deployed independently. Apart from that, the
`kustomize.yaml` 

* creates a small cdrom image repository and

* deploys the operator in namespace `vmop-dev` with a replica of 0.

This allows you to run the manager in your IDE.

The `kustomize.yaml` also changes the container image repository for
the operator to a private repository for development. You have to
adapt this to your own repository if you also want to test your
development version in a container.

If you want to run the unittests, this setup *must* be used with a private
container image repository which must match the one configured
for gradle pushImages.
