---
layout: default
title: Upgrading
---

# Upgrading

## To version 2.4.0

Starting with version 2.4.0, the web GUI uses a login conlet that
supports OIDC providers. This effects the configuration of the 
web GUI components.

## To version 2.2.0 

Version 2.2.0 sets the stateful set's `.spec.updateStrategy.type` to
"OnDelete". This fails for no apparent reason if a definition of 
the stateful set with the default value "RollingUpdate" already exists.
In order to fix this, either the stateful set or the complete VM definition
must be deleted and the manager must be restarted.
