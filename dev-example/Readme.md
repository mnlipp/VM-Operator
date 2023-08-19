# Example setup for development

The CRD must be deployed independently. Apart from that, the 
`kustomize.yaml` 

*   creates a small image repository and
 
*   deploys the operator in namespace `vmop-dev` with a replica of 0.
 
This allows you to run the manager in your IDE.

The `kustomize.yaml` also changes the image repository for the
operator to a private repository for development. You have to 
either remove this or adapt it to your own repository if you
also want to test your development version in a container.

If you want to run the unittests, this setup must be run with a private
repository and the private repository must match the one configured
for gradle pushImages.