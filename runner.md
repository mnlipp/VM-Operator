---
layout: default
title: VM-Operator Runner
---

# The Runner

For most use cases, Qemu needs to be started and controlled by a manager
process, which is called runner in this context. 

The most prominent reason
is that this is the only way to shutdown a VM cleanly. Qemu handles
the TERM signal by flushing all buffers and stopping, leaving the disks in
a [crash consistent state](https://gitlab.com/qemu-project/qemu/-/issues/148).
For a graceful shutdown, a parent process must handle the TERM signal, send
the `system_powerdown` command to the qemu process and wait for its completion.

Another reason is for having a manager process is that you need a second
process besides qemu if you want to provide a TPM (software TPM) to the
VM.

Finally, we want some kind of higher level interface for applying runtime
changes to the VM such as changing the CD or configuring the number of
CPUs and the memory.

The runner takes care of all these issues. Although it is intended to
run in a container (which runs in a Kubernetes pod) it does not require
a container. You can start and use it as an ordinary process on any
system, provided that you have the required commands (qemu, swtpm) 
installed.

## Configuration

Upon startup, the runner reads its main configuration file 
which defaults to `/etc/vmrunner/config.yaml` and may be changed
using the `-C` (or `--config`) command line option.

A sample configuration file with annotated options can be found
[here](https://github.com/mnlipp/VM-Operator/blob/main/org.jdrupes.vmoperator.runner.qemu/config-sample.yaml).
As the runner implementation uses the 
[JGrapes](https://mnlipp.github.io/jgrapes/) framework, the file 
follows the frameworks 
[conventions](https://mnlipp.github.io/jgrapes/latest-release/javadoc/org/jgrapes/util/YamlConfigurationStore.html). The top level "`/Runner`" addresses
the component to be configured. Nested within is the actual information.


