---
title: "VM-Operator: Auto login — Login users automatically on the guest"
layout: vm-operator
---

# Auto Login

*Since 4.0.0*

When users log into the web GUI, they have already authenticated with the
VM-Operator. In some environments, requiring an additional login on the
guest OS can be cumbersome. To enhance the user experience, the VM-Operator
supports automatic login on the guest operating system, thus eliminating
the need for multiple logins. However, this feature requires specific
support from the guest OS.

## Prepare the VM

Automatic login requires an agent running inside the guest OS. Similar
to QEMU's standard guest agent, the VM-Operator agent communicates with
the host via a tty device (`/dev/virtio-ports/org.jdrupes.vmop_agent.0`). On
modern Linux systems, `udev` can detect this device and trigger the start
of an associated systemd service.

Sample configuration files for a VM-Operator agent are available
[here](https://github.com/mnlipp/VM-Operator/tree/main/dev-example/vmop-agent).
Copy

  * `99-vmop-agent.rules` → `/usr/local/lib/udev/rules.d/99-vmop-agent.rules`,
  * `vmop-agent` → `/usr/local/libexec/vmop-agent` and
  * `vmop-agent.service` → `/usr/local/lib/systemd/system/vmop-agent.service`.

Some of these target directories may not exist by default and must be
created manually. If your system uses SELinux, run `restorecon` to apply
the correct security contexts.

Enable the agent:

```console
# systemctl daemon-reload
# systemctl enable vmop-agent
# udevadm control --reload-rules
# udevadm trigger
 ```

## The VM operator agent

Communication with the VM-Operator agent follows the pattern established by
protocols such as SMTP and FTP. The agent must handle the commands
"`login <username>`" and "`logout`" on its input. In response to
these commands, the agent sends back lines that start with a three
digit number. The first digit determines the type of message: "1" for
informational, "2" for success and "4" or "5" for errors. The second
digit provides information about the category that a response relates
to. The third digit is specific to the command.

While this describes the general pattern, the [runner](runner.html)
only evaluates the following codes:

| Code | Meaning |
| ---- | ------- |
| 220  | Sent by the agent on startup |
| 201  | Login command executed successfully |
| 202  | Logout command executed successfully |

The provided sample script is written for the gnome desktop environment.
It assumes that GDM is running as a service by default. When the agent
receives a login command, it stops GDM and starts a gnome-session for
the specified user. Upon receiving the logout command, it terminates
the session and starts GDM again.

No attempt has been made to make the script configurable. There are too
many possible options. The script should therefore be considered as a
starting point that you may need to adapt to your specific needs.

In addition to starting the desktop for the logged in user, the sample
script automatically creates user accounts if they do not already exist.
The idea behind this behavior is further explained in the
[section about pools](pools.html#vm-pools).

## Enable auto login for a VM

To enable auto login for a VM, specify the user to be logged in in the VM's
definition with "`spec.vm.display.loggedInUser: user-name`". If everything has been
set up correctly, you should be able to open the console and observe the
transition from GDM's login screen to the user's desktop when updating the
VM's spec.
