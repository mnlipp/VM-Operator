---
title: Upgrading
layout: vm-operator
---

# Upgrading

## To version 3.0.0

All configuration files are backward compatible to version 2.3.0.
Note that in order to make use of the new viewer component, 
[permissions](https://mnlipp.github.io/VM-Operator/user-gui.html#control-access-to-vms)
must be configured in the CR definition. Also note that 
[display secrets](https://mnlipp.github.io/VM-Operator/user-gui.html#securing-access)
are automatically created unless explicitly disabled.

## To version 2.3.0

Starting with version 2.3.0, the web GUI uses a login conlet that
supports OIDC providers. This effects the configuration of the 
web GUI components.

## To version 2.2.0 

Version 2.2.0 sets the stateful set's `.spec.updateStrategy.type` to
"OnDelete". This fails for no apparent reason if a definition of 
the stateful set with the default value "RollingUpdate" already exists.
In order to fix this, either the stateful set or the complete VM definition
must be deleted and the manager must be restarted.
