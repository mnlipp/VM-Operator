---
title: "VM-Operator: Auto login — Login users automatically on the guest"
layout: vm-operator
---

# Auto Login

*Since 4.0.0*

When a user logs in on the web GUI, he has already authenticated with the
VM-Operator. Depending on the environment, it may be tedious to log in again
on the guest. The VM-Operator therefore supports automatic login on the guest
operating system which can streamline the user experience by eliminating
the need for multiple logins. This requires, however, some support from
the guest OS.

## Prepare the VM

Automatic login requires an agent in the guest OS. Similar to QEMU's
standard guest agent, the VM-Operator agent communicates with the host
through a tty device (`/dev/virtio-ports/org.jdrupes.vmop_agent.0`). On a modern
Linux system, the device is detected by `udev` which triggers the start
of a systemd service.

Sample configuration files can be found
[here](https://github.com/mnlipp/VM-Operator/tree/main/dev-example/vmop-agent).
Copy

  * `99-vmop-agent.rules` to `/usr/local/lib/udev/rules.d/99-vmop-agent.rules`,
  * `vmop-agent` to `/usr/local/libexec/vmop-agent` and
  * `vmop-agent.service` to `/usr/local/lib/systemd/system/vmop-agent.service`.

Note that some of the target directories do not exist by default and have to
be created first. Don't forget to run `restorecon` on systems with SELinux.

Enable everything:

```console
# systemctl daemon-reload
# systemctl enable vmop-agent
# udevadm control --reload-rules
# udevadm trigger
 ```

## The VM operator agent

Communication with the VM-Operator agent follows the pattern established
by protocols such as SMTP and FTP. The agent must handle the commands
"`login <username>`" and "`logout`". In response to these commands, the agent
sends back lines that start with a three digit number. The first digit
determines the type of message: "1" for informational, "2" for success
"4" and "5" for errors. The second digit provides information about the
category that the response relates to. The third digit is specific to
the command.

While this describes the general pattern, the only response code that
the runner evaluates are:

| Code | Meaning |
| ---- | ------- |
| 220  | Sent by the agent on startup |
| 201  | Login command executed successfully |
| 202  | Logout command executed successfully |

The sample script is written for the gnome desktop environment. It assumes
that gdm is running as a service by default. On receiving the login command,
it stops gdm and starts a gnome-session for the given user. On receiving the
logout command, it terminates the session and starts gdm again.

No attempt has been made to make the script configurable. There are too
many possible options. The script should therefore be considered as a
starting point that you can adapt to your needs.

The sample script also creates new user accounts if a user does not exist
yet. The idea behind this is further explained in the
[section about pools](pools.html#vm-pools).

## Enable auto login for a VM

To enable auto login for a VM, specify the user to be logged in in the VM's
definition with "`spec.vm.display.loggedInUser: user-name`". If everything has been
set up correctly, you should be able to open the console and observe the
change from gdm's login screen to the user's desktop when updating the
VM's spec.
