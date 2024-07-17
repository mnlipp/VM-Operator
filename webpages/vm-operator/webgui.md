---
title: "VM-Operator: Web user interface — Provides easy access to VM management"
layout: vm-operator
---

# The Web-GUI

The manager component provides a GUI via a web server. This web user interface is
implemented using components from the
[JGrapes WebConsole](https://jgrapes.org/WebConsole.html)
project. Configuration of the GUI therefore follows the conventions
of that framework.

The structure of the configuration information should be easy to 
understand from the examples provided. In general, configuration values
are applied to the individual components that make up an application.
The hierarchy of the components is reflected in the configuration
information because components are "addressed" by their position in
that hierarchy. (See
[the package description](latest-release/javadoc/org/jdrupes/vmoperator/manager/package-summary.html)
for information about the complete component structure.)

## Network access

By default, the service is made available at port 8080 of the manager 
pod. Of course, a kubernetes service and an ingress configuration must
be added as required by the environment. (See the 
[definition](https://github.com/mnlipp/VM-Operator/blob/main/deploy/vmop-service.yaml)
from the
[sample deployment](https://github.com/mnlipp/VM-Operator/tree/main/deploy)).

## User Access

Access to the web user interface is controlled by the login conlet. The framework
does not include sophisticated components for user management. Rather,
it assumes that an OIDC provider is responsible for user authentication
and role management.

```yaml
"/Manager":
  # "/GuiSocketServer":
  #   port: 8080
  "/GuiHttpServer":
    # This configures the GUI
    "/ConsoleWeblet":
      "/WebConsole":
        "/LoginConlet":
          # Starting with version 2.3.0 the preferred approach is to
          # configure an OIDC provider for user management and
          # authorization. See the text for details.
          oidcProviders: {}
          
          # Support for "local" users is provided as a fallback mechanism.
          # Note that up to Version 2.2.x "users" was an object with user names
          # as its properties. Starting with 2.3.0 it is a list as shown.
          users:
            - name: admin
              fullName: Administrator
              password: "Generate hash with bcrypt"
            - name: test
              fullName: Test Account
              password: "Generate hash with bcrypt"
              
        # Required for using OIDC, see the text for details.
        "/OidcClient":
          redirectUri: https://my.server.here/oauth/callback"
          
        # May be used for assigning roles to both local users and users from
        # the OIDC provider. Not needed if roles are managed by the OIDC provider.
        "/RoleConfigurator":
          rolesByUser:
            # User admin has role admin
            admin:
            - admin
            # Non-privileged users are users
            test:
            - user
            # All users have role other
            "*":
            - other
          replace: false
          
        # Manages the permissions for the roles.
        "/RoleConletFilter":
          conletTypesByRole:
            # Admins can use all conlets
            admin:
            - "*"
            # Users can use the viewer conlet
            user:
            - org.jdrupes.vmoperator.vmviewer.VmViewer
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
[login conlet](https://jgrapes.org/javadoc-webconsole/org/jgrapes/webconlet/oidclogin/LoginConlet.html).
Details about the `RoleConfigurator` and `RoleConletFilter` can also be found
in the documentation of the
[JGrapes WebConsole](https://jgrapes.org/WebConsole.html)
project.

The configuration above allows all users with role "admin" to use all
GUI components and users with role "user" to only use the viewer conlet,
i.e. the [User view](user-gui.html). The fallback role "other" allows
all users to use the login conlet to log out.

## Views

The configuration of the components that provide the manager and 
users views is explained in the respective sections.
