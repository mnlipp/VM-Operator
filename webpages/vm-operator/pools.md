---
title: "VM-Operator: VM pools — assigning VMs to users dynamically"
layout: vm-operator
---

# VM Pools

*Since 4.0.0*

## Prepare the VM

### Shared file system

Mount a shared file system as home file system on all VMs in the pool.
If you want to use the sample script for logging in a user, the filesystem
must support POSIX file access control lists (ACLs).

### Restrict access

The VMs should only be accessible via a desktop started by the VM-Operator.

 * Disable the display manager.
 
   ```console
   # systemctl disable gdm
   # systemctl stop gdm
   ```
   
 * Disable `getty` on tty1.
 
   ```console
   # systemctl mask getty@tty1
   # systemctl stop getty@tty1
   ```

You can, of course, disable `getty` completely. If you do this, make sure
that you can still access your master VM through `ssh`, else you have
locked yourself out.

Strictly speaking, it is not necessary to disable these services, because
the sample script includes a `Conflicts=` directive in the systemd service
that starts the desktop for the user. However, this is mainly intended for
development purposes and not for production.
   
The following should actually be configured for any VM.
   
 * Prevent suspend/hibernate, because it will lock the VM.
 
   ```console
   # systemctl mask sleep.target suspend.target hibernate.target hybrid-sleep.target
   ```
 
### Install the VM-Operator agent

The VM-Operator agent runs as a systemd service. Sample configuration
files can be found
[here](https://github.com/mnlipp/VM-Operator/tree/main/dev-example/vmop-agent).
Copy 

  * `99-vmop-agent.rules` to `/usr/local/lib/udev/rules.d/99-vmop-agent.rules`,
  * `vmop-agent` to `/usr/local/libexec/vmop-agent` and
  * `vmop-agent.service` to `/usr/local/lib/systemd/system/vmop-agent.service`.

Note that some of the target directories do not exist by default and have to
be created first. Don't forget to run `restorecon` on systems with SELinux.

Enable everything:

```console
# udevadm control --reload-rules
# systemctl enable vmop-agent
# udevadm trigger
 ```
