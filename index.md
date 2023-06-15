---
layout: default
title: VM-Operator by mnlipp
description: A Kubernetes operator for running VMs as pods
---

# Welcome to VM-Operator

The goal of this project is to provide the means for running Qemu
based VMs in Kubernetes pods. 

The project was triggered by a remark in the discussion about RedHat
[dropping SPICE support](https://bugzilla.redhat.com/show_bug.cgi?id=2030592) 
from the RHEL packages. Which means that you have to run Qemu in a
container on RHEL and derivatives if you want to continue using Spice.
So KubeVirt comes to mind. But
[one comment](https://bugzilla.redhat.com/show_bug.cgi?id=2030592#c4) 
mentioned that the [KubeVirt](https://kubevirt.io/) project isn't
interested in supporting SPICE either.

Time to have a look at alternatives. Libvirt has become a common
tool to configure and run Qemu. But some of its functionality, notably
the management of storage for the VMs and networking is already provided
by Kubernetes. Therefore this project takes a fresh approach of
running Qemu in a pod using a simple, lightweight manager called "runner".
Providing resources to the VM is left to Kubernetes mechanisms as
much as possible.

The project does in no way attempt to replace kubevirt. Its goal is 
to provide a simple solution for the use case of running a virtual 
machine in a common configuration in a Kubernetes cluster.

## VMs and Pods

VMs are not the typical workload managed by Kubernetes. You can neither
have replicas nor can the containers simply be restarted without a major 
impact on the "application". Therefore the managing features provided
by deployments etc. cannot be used. Qemu in its container must be
run using a simple pod, which is managed according to its own, 
rather special requirements. Therefore something simpler such as Docker 
or Podman might be considered sufficient. 

A second look, however, reveals that Kubernetes has more to offer.
* It has a well defined API for managing pods.
* It provides access to different kinds of managed storage for the VMs.
* Its managing features *are* useful for running the component that
manages the pods with the VMs.

And if you use Kubernetes anyway, well then the VMs within Kubernetes 
provide you with a unified view on all (or more of) your workloads,
which simplifies the maintenance of your platform.
