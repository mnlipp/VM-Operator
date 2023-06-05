[![Java CI with Gradle](https://github.com/mnlipp/VM-Operator/actions/workflows/gradle.yml/badge.svg)](https://github.com/mnlipp/VM-Operator/actions/workflows/gradle.yml)

# Run Qemu in Kubernetes Pods

The goal of this project is to provide the means for running Qemu
based VMs in Kubernetes pods. 

The project was triggered by a remark in the discussion about RedHat
[dropping SPICE support](https://bugzilla.redhat.com/show_bug.cgi?id=2030592) 
from the RHEL packages. 
[One comment](https://bugzilla.redhat.com/show_bug.cgi?id=2030592#c4) 
mentioned that the [KubeVirt](https://kubevirt.io/) project isn't
interested in supporting SPICE.

Time to have a look at alternatives. Libvirt has become a common
tool to configure and run Qemu. But some of its functionality, notably
the management of storage for the VMs and networking is already provided
by Kubernetes. Therefore this project takes a fresh approach of
running Qemu in a pod using a simple, lightweight manager called "runner".
The runner makes use of the Kubernetes features for resource management as
much as possible.

The project does in no way attempt to replace kubevirt. Its goal is 
to provide a simple solution for the use case of running a virtual 
machine in a common configuration in a Kubernetes cluster.
 