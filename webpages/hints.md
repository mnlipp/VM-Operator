---
title: "VM-Operator: Hints — Miscellaneous hints for using VM-Operator"
layout: vm-operator
---

# Hints

## Disable suspend and hibernate

Suspend and hibernate are poorly supported in VMs and usually do not
work as expected. To disable these on systemd based systems, use the
following command:

```console
# systemctl mask sleep.target suspend.target hibernate.target hybrid-sleep.target
```
