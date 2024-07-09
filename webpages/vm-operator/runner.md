---
title: VM-Operator: The Runner — Starts and monitors a VM
layout: vm-operator
---

# The Runner

For most use cases, Qemu needs to be started and controlled by another 
program that manages the Qemu process. This program is called the 
runner in this context. 

The most prominent reason for this second program is that it allows
a VM to be shutdown cleanly in response to a TERM signal. Qemu handles
the TERM signal by flushing all buffers and stopping, leaving the disks in
a [crash consistent state](https://gitlab.com/qemu-project/qemu/-/issues/148).
For a graceful shutdown, a parent process must handle the TERM signal, send
the `system_powerdown` command to the qemu process and wait for its completion.

Another reason for having the runner is that another process needs to be started
before qemu if the VM is supposed to include a TPM (software TPM).

Finally, we want some kind of higher level interface for applying runtime
changes to the VM such as changing the CD or configuring the number of
CPUs and the memory.

The runner takes care of all these issues. Although it is intended to
run in a container (which runs in a Kubernetes pod) it does not require
a container. You can start and use it as an ordinary program on any
system, provided that you have the required commands (qemu, swtpm) 
installed.

## Stand-alone Configuration

Upon startup, the runner reads its main configuration file 
which defaults to `/etc/opt/vmrunner/config.yaml` and may be changed
using the `-c` (or `--config`) command line option.

A sample configuration file with annotated options can be found
[here](https://github.com/mnlipp/VM-Operator/blob/main/org.jdrupes.vmoperator.runner.qemu/config-sample.yaml).
As the runner implementation uses the 
[JGrapes](https://jgrapes.org/) framework, the file 
follows the framework's 
[conventions](https://jgrapes.org/latest-release/javadoc/org/jgrapes/util/YamlConfigurationStore.html). The top level "`/Runner`" selects
the component to be configured. Nested within is the information
to be applied to the component.

The main entries in the configuration file are the "template" and
the "vm" information. The runner processes the 
[freemarker template](https://freemarker.apache.org/), using the
"vm" information to derive the qemu command. The idea is that 
the "vm" section provides high level information such as the boot
mode, the number of CPUs, the RAM size and the disks. The template
defines a particular VM type, i.e. it contains the "nasty details"
that do not need to be modified for some given set of VM instances.

The templates provided with the runner can be found 
[here](https://github.com/mnlipp/VM-Operator/tree/main/org.jdrupes.vmoperator.runner.qemu/templates). When details 
of the VM configuration need modification, a new VM type
(i.e. a new template) has to be defined. Authoring a new 
template requires some knowledge about the 
[qemu invocation](https://www.qemu.org/docs/master/system/invocation.html).
Despite many "warnings" that you find in the web, configuring the
invocation arguments of qemu is only a bit (but not much) more
challenging than editing libvirt's XML.

## Running in a Pod

The real purpose of the runner is to run a VM on Kubernetes in a pod.
When running in a Kubernetes pod, `/etc/opt/vmrunner/config.yaml` should be
provided by a
[ConfigMap](https://kubernetes.io/docs/concepts/configuration/configmap/).

If additional templates are required, some ReadOnlyMany PV should
be mounted in `/opt/vmrunner/templates`. The PV should contain copies
of the standard templates as well as the additional templates. Of course, 
a ConfigMap can be used for this purpose again.

Networking options are rather limited. The assumption is that in general
the VM wants full network connectivity. To achieve this, the pod must
run with host networking and the host's networking must provide a
bridge that the VM can attach to. The only currently supported 
alternative is the less performant
"[user networking](https://wiki.qemu.org/Documentation/Networking#User_Networking_(SLIRP))",
which may be used in a stand-alone development configuration.

## Runtime changes

The runner supports adaption to changes of the RAM size (using the
balloon device) and to changes of the number of CPUs. Note that
in order to get new CPUs online on Linux guests, you need a 
[udev rule](https://docs.kernel.org/core-api/cpu_hotplug.html#user-space-notification) which is not installed by default[^simplest].

The runner also changes the images loaded in CDROM drives. If the
drive is locked, i.e. if it doesn't respond to the "open tray" command
the change will be suspended until the VM opens the tray.

Finally, `powerdownTimeout` can be changed while the qemu process runs.

[^simplest]: The simplest form of the rule is probably:
    ```
    ACTION=="add", SUBSYSTEM=="cpu", ATTR{online}="1"
    ```

## Testing with Helm

There is a 
[Helm Chart](https://github.com/mnlipp/VM-Operator/tree/main/org.jdrupes.vmoperator.runner.qemu/helm-test)
for testing the runner.
