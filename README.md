[![Java CI with Gradle](https://github.com/mnlipp/VM-Operator/actions/workflows/gradle.yml/badge.svg)](https://github.com/mnlipp/VM-Operator/actions/workflows/gradle.yml)
[![Codacy Badge](https://app.codacy.com/project/badge/Grade/2277842dac894de4b663c6aa2779077e)](https://app.codacy.com/gh/mnlipp/VM-Operator/dashboard?utm_source=gh&utm_medium=referral&utm_content=&utm_campaign=Badge_grade)
![Latest Manager](https://img.shields.io/github/v/tag/mnlipp/vm-operator?filter=manager*&label=latest)
![Latest Runner](https://img.shields.io/github/v/tag/mnlipp/vm-operator?filter=runner-qemu*&label=latest)

# Run QEMU/KVM in Kubernetes Pods

![Overview picture](webpages/index-pic.svg)

This project provides an easy to use and flexible solution for running
QEMU/KVM based VMs in Kubernetes pods.

The central component of this solution is the kubernetes operator that
manages "runners". These run in pods and are used to start and manage
the QEMU/KVM process for the VMs (optionally together with a SW-TPM).

A web GUI for administrators provides an overview of the VMs together
with some basic control over the VMs. A web GUI for users provides an
interface to access and optionally start, stop and reset the VMs.

Advanced features of the operator include pooling of VMs and automatic
login.

See the [project's home page](https://vm-operator.jdrupes.org/)
for details.
