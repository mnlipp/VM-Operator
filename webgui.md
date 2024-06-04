---
layout: default
title: VM-Operator Web-GUI
---

# The Web-GUI

The manager component provides a GUI via a web server.

## Manager view

An overview display shows the current CPU and RAM usage and a graph
with recent changes.

![VM-Operator GUI](VM-Operator-GUI-preview.png)

The detail display lists all VMs. From here you can start and stop
the VMs and adjust the CPU and RAM usages (modifies the definition
in kubernetes).

![VM-Operator GUI](VM-Operator-GUI-view.png)

By default, the service is made available at port 8080 of the manager 
pod. Of course, a kubernetes service and an ingress configuration must
be added as required by the environment. (See the 
[definition](https://github.com/mnlipp/VM-Operator/blob/main/deploy/vmop-service.yaml)
from the
[sample deployment](https://github.com/mnlipp/VM-Operator/tree/main/deploy)).

## User view

*Since 3.0.0*

## Configuration

The web GUI is implemented using components from the
[JGrapes WebConsole](https://mnlipp.github.io/jgrapes/WebConsole.html)
project. Configuration of the GUI therefore follows the conventions
of that framework. (See
[the package description](latest-release/javadoc/org/jdrupes/vmoperator/manager/package-summary.html)
for information about the complete components structure.)

```yaml
"/Manager":
  # "/GuiSocketServer":
  #   port: 8080
  "/GuiHttpServer":
    # This configures the GUI
    "/ConsoleWeblet":
      "/WebConsole":
        "/LoginConlet":
          # Up to Version 2.2.x "users" was an object with user names
          # as its properties. Starting with 2.3.0 it is a list.
          users:
            - name: admin
              fullName: Administrator
              password: "Generate hash with bcrypt"
            # Not needed, provided as example only:
            - name: test
              fullName: Test Account
              password: "Generate hash with bcrypt"
          oidcProviders: {}
        # Only required when using OIDC, see below.
        "/OidcClient":
          redirectUri: https://my.server.here/oauth/callback"
        "/RoleConfigurator":
          rolesByUser:
            # User admin has role admin
            admin:
            - admin
            # All users have role other
            "*":
            - other
          replace: false
        "/RoleConletFilter":
          conletTypesByRole:
            # Admins can use all conlets
            admin:
            - "*"
            # Others cannot use any conlet (except login conlet to log out)
            other:
            # Up to version 2.2.x
            # - org.jgrapes.webconlet.locallogin.LoginConlet
            # Starting with version 2.3.0
            - org.jgrapes.webconlet.oidclogin.LoginConlet
```

How local users can be configured should be obvious from the example.
The configuration of OIDC providers for user authentication (and 
optionally for role assignment) is explained in the documentation of the 
[login conlet](https://mnlipp.github.io/jgrapes/javadoc-webconsole/org/jgrapes/webconlet/oidclogin/LoginConlet.html).
Details about the `RoleConfigurator` and `RoleConletFilter` can also be found
in the documentation of the
[JGrapes WebConsole](https://mnlipp.github.io/jgrapes/WebConsole.html)
project.
