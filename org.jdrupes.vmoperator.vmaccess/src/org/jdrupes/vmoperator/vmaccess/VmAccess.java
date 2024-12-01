/*
 * VM-Operator
 * Copyright (C) 2023,2024 Michael N. Lipp
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.jdrupes.vmoperator.vmaccess;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.gson.JsonSyntaxException;
import freemarker.core.ParseException;
import freemarker.template.MalformedTemplateNameException;
import freemarker.template.Template;
import freemarker.template.TemplateNotFoundException;
import io.kubernetes.client.util.Strings;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Base64;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;
import org.bouncycastle.util.Objects;
import org.jdrupes.vmoperator.common.K8sObserver;
import org.jdrupes.vmoperator.common.VmDefinition;
import org.jdrupes.vmoperator.common.VmDefinition.Permission;
import org.jdrupes.vmoperator.common.VmPool;
import org.jdrupes.vmoperator.manager.events.ChannelTracker;
import org.jdrupes.vmoperator.manager.events.GetDisplayPassword;
import org.jdrupes.vmoperator.manager.events.ModifyVm;
import org.jdrupes.vmoperator.manager.events.ResetVm;
import org.jdrupes.vmoperator.manager.events.VmChannel;
import org.jdrupes.vmoperator.manager.events.VmDefChanged;
import org.jdrupes.vmoperator.manager.events.VmPoolChanged;
import org.jgrapes.core.Channel;
import org.jgrapes.core.Components;
import org.jgrapes.core.Event;
import org.jgrapes.core.Manager;
import org.jgrapes.core.annotation.Handler;
import org.jgrapes.http.Session;
import org.jgrapes.util.events.ConfigurationUpdate;
import org.jgrapes.util.events.KeyValueStoreQuery;
import org.jgrapes.util.events.KeyValueStoreUpdate;
import org.jgrapes.webconsole.base.Conlet.RenderMode;
import org.jgrapes.webconsole.base.ConletBaseModel;
import org.jgrapes.webconsole.base.ConsoleConnection;
import org.jgrapes.webconsole.base.ConsoleRole;
import org.jgrapes.webconsole.base.ConsoleUser;
import org.jgrapes.webconsole.base.WebConsoleUtils;
import org.jgrapes.webconsole.base.events.AddConletRequest;
import org.jgrapes.webconsole.base.events.AddConletType;
import org.jgrapes.webconsole.base.events.AddPageResources.ScriptResource;
import org.jgrapes.webconsole.base.events.ConletDeleted;
import org.jgrapes.webconsole.base.events.ConsoleConfigured;
import org.jgrapes.webconsole.base.events.ConsolePrepared;
import org.jgrapes.webconsole.base.events.ConsoleReady;
import org.jgrapes.webconsole.base.events.DeleteConlet;
import org.jgrapes.webconsole.base.events.NotifyConletModel;
import org.jgrapes.webconsole.base.events.NotifyConletView;
import org.jgrapes.webconsole.base.events.OpenModalDialog;
import org.jgrapes.webconsole.base.events.RenderConlet;
import org.jgrapes.webconsole.base.events.RenderConletRequestBase;
import org.jgrapes.webconsole.base.events.SetLocale;
import org.jgrapes.webconsole.base.events.UpdateConletType;
import org.jgrapes.webconsole.base.freemarker.FreeMarkerConlet;

/**
 * The Class {@link VmAccess}. The component supports the following
 * configuration properties:
 * 
 *   * `displayResource`: a map with the following entries:
 *       - `preferredIpVersion`: `ipv4` or `ipv6` (default: `ipv4`).
 *         Determines the IP addresses uses in the generated
 *         connection file.
 *   * `deleteConnectionFile`: `true` or `false` (default: `true`).
 *     If `true`, the downloaded connection file will be deleted by
 *     the remote viewer when opened.
 *   * `syncPreviewsFor`: a list objects with either property `user` or
 *     `role` and the associated name (default: `[]`).
 *     The remote viewer will synchronize the previews for the specified
 *     users and roles.
 *
 */
@SuppressWarnings({ "PMD.DataflowAnomalyAnalysis", "PMD.ExcessiveImports",
    "PMD.CouplingBetweenObjects", "PMD.GodClass", "PMD.TooManyMethods",
    "PMD.CyclomaticComplexity" })
public class VmAccess extends FreeMarkerConlet<VmAccess.ResourceModel> {

    private static final String VM_NAME_PROPERTY = "vmName";
    private static final String RENDERED
        = VmAccess.class.getName() + ".rendered";
    private static final String PENDING
        = VmAccess.class.getName() + ".pending";
    private static final Set<RenderMode> MODES = RenderMode.asSet(
        RenderMode.Preview, RenderMode.Edit);
    private static final Set<RenderMode> MODES_FOR_GENERATED = RenderMode.asSet(
        RenderMode.Preview, RenderMode.StickyPreview);
    private final ChannelTracker<String, VmChannel,
            VmDefinition> channelTracker = new ChannelTracker<>();
    private static ObjectMapper objectMapper
        = new ObjectMapper().registerModule(new JavaTimeModule());
    private Class<?> preferredIpVersion = Inet4Address.class;
    private Set<String> syncUsers = Collections.emptySet();
    private Set<String> syncRoles = Collections.emptySet();
    private boolean deleteConnectionFile = true;
    @SuppressWarnings("PMD.UseConcurrentHashMap")
    private final Map<String, VmPool> vmPools = new HashMap<>();

    /**
     * The periodically generated update event.
     */
    public static class Update extends Event<Void> {
    }

    /**
     * Creates a new component with its channel set to the given channel.
     * 
     * @param componentChannel the channel that the component's handlers listen
     * on by default and that {@link Manager#fire(Event, Channel...)}
     * sends the event to
     */
    public VmAccess(Channel componentChannel) {
        super(componentChannel);
    }

    /**
     * Configure the component. 
     * 
     * @param event the event
     */
    @SuppressWarnings({ "unchecked", "PMD.AvoidDuplicateLiterals" })
    @Handler
    public void onConfigurationUpdate(ConfigurationUpdate event) {
        event.structured(componentPath())
            .or(() -> {
                var oldConfig = event.structured("/Manager/GuiHttpServer"
                    + "/ConsoleWeblet/WebConsole/ComponentCollector/VmViewer");
                if (oldConfig.isPresent()) {
                    logger.warning(() -> "Using configuration with old "
                        + "component name \"VmViewer\", please update to "
                        + "\"VmAccess\"");
                }
                return oldConfig;
            })
            .ifPresent(c -> {
                try {
                    var dispRes = (Map<String, Object>) c
                        .getOrDefault("displayResource",
                            Collections.emptyMap());
                    switch ((String) dispRes.getOrDefault("preferredIpVersion",
                        "")) {
                    case "ipv6":
                        preferredIpVersion = Inet6Address.class;
                        break;
                    case "ipv4":
                    default:
                        preferredIpVersion = Inet4Address.class;
                        break;
                    }

                    // Delete connection file
                    deleteConnectionFile
                        = Optional.ofNullable(c.get("deleteConnectionFile"))
                            .filter(v -> v instanceof String)
                            .map(v -> (String) v)
                            .map(Boolean::parseBoolean).orElse(true);

                    // Users or roles for which previews should be synchronized
                    syncUsers = ((List<Map<String, String>>) c.getOrDefault(
                        "syncPreviewsFor", Collections.emptyList())).stream()
                            .map(m -> m.get("user"))
                            .filter(s -> s != null).collect(Collectors.toSet());
                    logger.finest(() -> "Syncing previews for users: "
                        + syncUsers.toString());
                    syncRoles = ((List<Map<String, String>>) c.getOrDefault(
                        "syncPreviewsFor", Collections.emptyList())).stream()
                            .map(m -> m.get("role"))
                            .filter(s -> s != null).collect(Collectors.toSet());
                    logger.finest(() -> "Syncing previews for roles: "
                        + syncRoles.toString());
                } catch (ClassCastException e) {
                    logger.config("Malformed configuration: " + e.getMessage());
                }
            });
    }

    private boolean syncPreviews(Session session) {
        return WebConsoleUtils.userFromSession(session)
            .filter(u -> syncUsers.contains(u.getName())).isPresent()
            || WebConsoleUtils.rolesFromSession(session).stream()
                .filter(cr -> syncRoles.contains(cr.getName())).findAny()
                .isPresent();
    }

    /**
     * On {@link ConsoleReady}, fire the {@link AddConletType}.
     *
     * @param event the event
     * @param channel the channel
     * @throws TemplateNotFoundException the template not found exception
     * @throws MalformedTemplateNameException the malformed template name
     *             exception
     * @throws ParseException the parse exception
     * @throws IOException Signals that an I/O exception has occurred.
     */
    @Handler
    public void onConsoleReady(ConsoleReady event, ConsoleConnection channel)
            throws TemplateNotFoundException, MalformedTemplateNameException,
            ParseException, IOException {
        // Add conlet resources to page
        channel.respond(new AddConletType(type())
            .setDisplayNames(
                localizations(channel.supportedLocales(), "conletName"))
            .addRenderMode(RenderMode.Preview)
            .addScript(new ScriptResource().setScriptType("module")
                .setScriptUri(event.renderSupport().conletResource(
                    type(), "VmAccess-functions.js"))));
        channel.session().put(RENDERED, new HashSet<>());
    }

    /**
     * On console configured.
     *
     * @param event the event
     * @param connection the console connection
     * @throws InterruptedException the interrupted exception
     */
    @Handler
    public void onConsoleConfigured(ConsoleConfigured event,
            ConsoleConnection connection) throws InterruptedException,
            IOException {
        @SuppressWarnings("unchecked")
        final var rendered
            = (Set<ResourceModel>) connection.session().get(RENDERED);
        connection.session().remove(RENDERED);
        if (!syncPreviews(connection.session())) {
            return;
        }

        addMissingVms(event, connection, rendered);
    }

    @SuppressWarnings({ "PMD.AvoidInstantiatingObjectsInLoops",
        "PMD.AvoidDuplicateLiterals" })
    private void addMissingVms(ConsoleConfigured event,
            ConsoleConnection connection, final Set<ResourceModel> rendered) {
        boolean foundMissing = false;
        for (var vmName : accessibleVms(connection)) {
            if (rendered.stream()
                .anyMatch(r -> r.type() == ResourceModel.Type.VM
                    && r.name().equals(vmName))) {
                continue;
            }
            if (!foundMissing) {
                // Suspending to allow rendering of conlets to be noticed
                var failSafe = Components.schedule(t -> event.resumeHandling(),
                    Duration.ofSeconds(1));
                event.suspendHandling(failSafe::cancel);
                connection.setAssociated(PENDING, event);
                foundMissing = true;
            }
            fire(new AddConletRequest(event.event().event().renderSupport(),
                VmAccess.class.getName(),
                RenderMode.asSet(RenderMode.Preview))
                    .addProperty(VM_NAME_PROPERTY, vmName),
                connection);
        }
    }

    /**
     * On console prepared.
     *
     * @param event the event
     * @param connection the connection
     */
    @Handler
    public void onConsolePrepared(ConsolePrepared event,
            ConsoleConnection connection) {
        if (syncPreviews(connection.session())) {
            connection.respond(new UpdateConletType(type()));
        }
    }

    private String storagePath(Session session, String conletId) {
        return "/" + WebConsoleUtils.userFromSession(session)
            .map(ConsoleUser::getName).orElse("")
            + "/" + VmAccess.class.getName() + "/" + conletId;
    }

    @Override
    protected Optional<ResourceModel> createNewState(AddConletRequest event,
            ConsoleConnection connection, String conletId) throws Exception {
        var model = new ResourceModel(conletId);
        model.setType(ResourceModel.Type.VM);
        model
            .setName((String) event.properties().get(VM_NAME_PROPERTY));
        String jsonState = objectMapper.writeValueAsString(model);
        connection.respond(new KeyValueStoreUpdate().update(
            storagePath(connection.session(), model.getConletId()), jsonState));
        return Optional.of(model);
    }

    @Override
    protected Optional<ResourceModel> createStateRepresentation(Event<?> event,
            ConsoleConnection connection, String conletId) throws Exception {
        var model = new ResourceModel(conletId);
        String jsonState = objectMapper.writeValueAsString(model);
        connection.respond(new KeyValueStoreUpdate().update(
            storagePath(connection.session(), model.getConletId()), jsonState));
        return Optional.of(model);
    }

    @Override
    @SuppressWarnings("PMD.EmptyCatchBlock")
    protected Optional<ResourceModel> recreateState(Event<?> event,
            ConsoleConnection channel, String conletId) throws Exception {
        KeyValueStoreQuery query = new KeyValueStoreQuery(
            storagePath(channel.session(), conletId), channel);
        newEventPipeline().fire(query, channel);
        try {
            if (!query.results().isEmpty()) {
                var json = query.results().get(0).values().stream().findFirst()
                    .get();
                ResourceModel model
                    = objectMapper.readValue(json, ResourceModel.class);
                return Optional.of(model);
            }
        } catch (InterruptedException e) {
            // Means we have no result.
        }

        // Fall back to creating default state.
        return createStateRepresentation(event, channel, conletId);
    }

    @Override
    @SuppressWarnings({ "PMD.AvoidInstantiatingObjectsInLoops" })
    protected Set<RenderMode> doRenderConlet(RenderConletRequestBase<?> event,
            ConsoleConnection channel, String conletId, ResourceModel model)
            throws Exception {
        if (event.renderAs().contains(RenderMode.Preview)) {
            return renderPreview(event, channel, conletId, model);
        }

        // Render edit
        ResourceBundle resourceBundle = resourceBundle(channel.locale());
        Set<RenderMode> renderedAs = EnumSet.noneOf(RenderMode.class);
        if (event.renderAs().contains(RenderMode.Edit)) {
            Template tpl = freemarkerConfig()
                .getTemplate("VmAccess-edit.ftl.html");
            var fmModel = fmModel(event, channel, conletId, model);
            fmModel.put("vmNames", accessibleVms(channel));
            fmModel.put("poolNames", accessiblePools(channel));
            channel.respond(new OpenModalDialog(type(), conletId,
                processTemplate(event, tpl, fmModel))
                    .addOption("cancelable", true)
                    .addOption("okayLabel",
                        resourceBundle.getString("okayLabel")));
        }
        return renderedAs;
    }

    @SuppressWarnings("unchecked")
    private Set<RenderMode> renderPreview(RenderConletRequestBase<?> event,
            ConsoleConnection channel, String conletId, ResourceModel model)
            throws TemplateNotFoundException, MalformedTemplateNameException,
            ParseException, IOException {
        channel.associated(PENDING, Event.class)
            .ifPresent(e -> {
                e.resumeHandling();
                channel.setAssociated(PENDING, null);
            });

        if (model.type() == ResourceModel.Type.VM && model.name() != null) {
            // Remove conlet if VM definition has been removed
            // or user has not at least one permission
            Optional<VmDefinition> vmDef
                = channelTracker.associated(model.name());
            if (vmDef.isEmpty()
                || vmPermissions(vmDef.get(), channel.session()).isEmpty()) {
                channel.respond(
                    new DeleteConlet(conletId, Collections.emptySet()));
                return Collections.emptySet();
            }
        }

        if (model.type() == ResourceModel.Type.POOL && model.name() != null) {
            // Remove conlet if pool definition has been removed
            // or user has not at least one permission
            VmPool pool = vmPools.get(model.name());
            if (pool == null
                || poolPermissions(pool, channel.session()).isEmpty()) {
                channel.respond(
                    new DeleteConlet(conletId, Collections.emptySet()));
                return Collections.emptySet();
            }
        }

        // Render
        Template tpl
            = freemarkerConfig().getTemplate("VmAccess-preview.ftl.html");
        channel.respond(new RenderConlet(type(), conletId,
            processTemplate(event, tpl,
                fmModel(event, channel, conletId, model)))
                    .setRenderAs(
                        RenderMode.Preview.addModifiers(event.renderAs()))
                    .setSupportedModes(syncPreviews(channel.session())
                        ? MODES_FOR_GENERATED
                        : MODES));
        if (!Strings.isNullOrEmpty(model.name())) {
            Optional.ofNullable(channel.session().get(RENDERED))
                .ifPresent(s -> ((Set<ResourceModel>) s).add(model));
            updateConfig(channel, model);
        }
        return EnumSet.of(RenderMode.Preview);
    }

    private List<String> accessibleVms(ConsoleConnection channel) {
        return channelTracker.associated().stream()
            .filter(d -> !vmPermissions(d, channel.session()).isEmpty())
            .map(d -> d.getMetadata().getName()).sorted().toList();
    }

    private Set<Permission> vmPermissions(VmDefinition vmDef,
            Session session) {
        var user = WebConsoleUtils.userFromSession(session)
            .map(ConsoleUser::getName).orElse(null);
        var roles = WebConsoleUtils.rolesFromSession(session)
            .stream().map(ConsoleRole::getName).toList();
        return vmDef.permissionsFor(user, roles);
    }

    private List<String> accessiblePools(ConsoleConnection channel) {
        return vmPools.values().stream()
            .filter(d -> !poolPermissions(d, channel.session()).isEmpty())
            .map(d -> d.name()).sorted().toList();
    }

    private Set<Permission> poolPermissions(VmPool pool,
            Session session) {
        var user = WebConsoleUtils.userFromSession(session)
            .map(ConsoleUser::getName).orElse(null);
        var roles = WebConsoleUtils.rolesFromSession(session)
            .stream().map(ConsoleRole::getName).toList();
        return pool.permissionsFor(user, roles);
    }

    private void updateConfig(ConsoleConnection channel, ResourceModel model) {
        channel.respond(new NotifyConletView(type(),
            model.getConletId(), "updateConfig", model.type(), model.name()));
        updateVmDef(channel, model);
    }

    private void updateVmDef(ConsoleConnection channel, ResourceModel model) {
        if (Strings.isNullOrEmpty(model.name())) {
            return;
        }
        channelTracker.value(model.name()).ifPresent(item -> {
            try {
                var vmDef = item.associated();
                var data = Map.of("metadata",
                    Map.of("namespace", vmDef.namespace(),
                        "name", vmDef.name()),
                    "spec", vmDef.spec(),
                    "status", vmDef.getStatus(),
                    "userPermissions",
                    vmPermissions(vmDef, channel.session()).stream()
                        .map(VmDefinition.Permission::toString).toList());
                channel.respond(new NotifyConletView(type(),
                    model.getConletId(), "updateVmDefinition", data));
            } catch (JsonSyntaxException e) {
                logger.log(Level.SEVERE, e,
                    () -> "Failed to serialize VM definition");
            }
        });
    }

    @Override
    protected void doConletDeleted(ConletDeleted event,
            ConsoleConnection channel, String conletId,
            ResourceModel conletState)
            throws Exception {
        if (event.renderModes().isEmpty()) {
            channel.respond(new KeyValueStoreUpdate().delete(
                storagePath(channel.session(), conletId)));
        }
    }

    /**
     * Track the VM definitions.
     *
     * @param event the event
     * @param channel the channel
     * @throws IOException 
     */
    @Handler(namedChannels = "manager")
    @SuppressWarnings({ "PMD.ConfusingTernary", "PMD.CognitiveComplexity",
        "PMD.AvoidInstantiatingObjectsInLoops", "PMD.AvoidDuplicateLiterals",
        "PMD.ConfusingArgumentToVarargsMethod" })
    public void onVmDefChanged(VmDefChanged event, VmChannel channel)
            throws IOException {
        var vmDef = event.vmDefinition();
        var vmName = vmDef.name();
        if (event.type() == K8sObserver.ResponseType.DELETED) {
            channelTracker.remove(vmName);
        } else {
            channelTracker.put(vmName, channel, vmDef);
        }

        // Update known conlets
        for (var entry : conletIdsByConsoleConnection().entrySet()) {
            var connection = entry.getKey();
            for (var conletId : entry.getValue()) {
                var model = stateFromSession(connection.session(), conletId);
                if (model.isEmpty()
                    || model.get().type() != ResourceModel.Type.VM
                    || !Objects.areEqual(model.get().name(), vmName)) {
                    continue;
                }
                if (event.type() == K8sObserver.ResponseType.DELETED
                    || vmPermissions(vmDef, connection.session()).isEmpty()) {
                    connection.respond(
                        new DeleteConlet(conletId, Collections.emptySet()));
                } else {
                    updateVmDef(connection, model.get());
                }
            }
        }
    }

    /**
     * On vm pool changed.
     *
     * @param event the event
     * @param channel the channel
     */
    @Handler(namedChannels = "manager")
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    public void onVmPoolChanged(VmPoolChanged event) {
        var poolName = event.vmPool().name();
        if (event.deleted()) {
            vmPools.remove(poolName);
        } else {
            vmPools.put(poolName, event.vmPool());
        }

        // Update known conlets
        for (var entry : conletIdsByConsoleConnection().entrySet()) {
            var connection = entry.getKey();
            for (var conletId : entry.getValue()) {
                var model = stateFromSession(connection.session(), conletId);
                if (model.isEmpty()
                    || model.get().type() != ResourceModel.Type.POOL
                    || !Objects.areEqual(model.get().name(), poolName)) {
                    continue;
                }
                if (event.deleted()
                    || poolPermissions(event.vmPool(), connection.session())
                        .isEmpty()) {
                    connection.respond(
                        new DeleteConlet(conletId, Collections.emptySet()));
                }
            }
        }
    }

    @Override
    @SuppressWarnings({ "PMD.AvoidDecimalLiteralsInBigDecimalConstructor",
        "PMD.ConfusingArgumentToVarargsMethod", "PMD.NcssCount",
        "PMD.AvoidLiteralsInIfCondition" })
    protected void doUpdateConletState(NotifyConletModel event,
            ConsoleConnection channel, ResourceModel model)
            throws Exception {
        event.stop();
        if ("selectedResource".equals(event.method())) {
            selectResource(event, channel, model);
            return;
        }

        // Handle command for selected VM
        var both = Optional.ofNullable(model.name())
            .flatMap(vm -> channelTracker.value(vm));
        if (both.isEmpty()) {
            return;
        }
        var vmChannel = both.get().channel();
        var vmDef = both.get().associated();
        var vmName = vmDef.metadata().getName();
        var perms = vmPermissions(vmDef, channel.session());
        var resourceBundle = resourceBundle(channel.locale());
        switch (event.method()) {
        case "start":
            if (perms.contains(VmDefinition.Permission.START)) {
                fire(new ModifyVm(vmName, "state", "Running", vmChannel));
            }
            break;
        case "stop":
            if (perms.contains(VmDefinition.Permission.STOP)) {
                fire(new ModifyVm(vmName, "state", "Stopped", vmChannel));
            }
            break;
        case "reset":
            if (perms.contains(VmDefinition.Permission.RESET)) {
                confirmReset(event, channel, model, resourceBundle);
            }
            break;
        case "resetConfirmed":
            if (perms.contains(VmDefinition.Permission.RESET)) {
                fire(new ResetVm(vmName), vmChannel);
            }
            break;
        case "openConsole":
            if (perms.contains(VmDefinition.Permission.ACCESS_CONSOLE)) {
                var user = WebConsoleUtils.userFromSession(channel.session())
                    .map(ConsoleUser::getName).orElse("");
                var pwQuery
                    = Event.onCompletion(new GetDisplayPassword(vmDef, user),
                        e -> openConsole(vmName, channel, model,
                            e.password().orElse(null)));
                fire(pwQuery, vmChannel);
            }
            break;
        default:// ignore
            break;
        }
    }

    @SuppressWarnings({ "PMD.AvoidLiteralsInIfCondition",
        "PMD.UseLocaleWithCaseConversions" })
    private void selectResource(NotifyConletModel event,
            ConsoleConnection channel, ResourceModel model)
            throws JsonProcessingException {
        try {
            model.setType(ResourceModel.Type
                .valueOf(event.<String> param(0).toUpperCase()));
            model.setName(event.param(1));
            String jsonState = objectMapper.writeValueAsString(model);
            channel.respond(new KeyValueStoreUpdate().update(storagePath(
                channel.session(), model.getConletId()), jsonState));
            updateConfig(channel, model);
        } catch (IllegalArgumentException e) {
            logger.warning(() -> "Invalid resource type: " + e.getMessage());
        }
    }

    private void openConsole(String vmName, ConsoleConnection connection,
            ResourceModel model, String password) {
        var vmDef = channelTracker.associated(vmName).orElse(null);
        if (vmDef == null) {
            return;
        }
        var addr = displayIp(vmDef);
        if (addr.isEmpty()) {
            logger.severe(() -> "Failed to find display IP for " + vmName);
            return;
        }
        var port = vmDef.<Number> fromVm("display", "spice", "port")
            .map(Number::longValue);
        if (port.isEmpty()) {
            logger.severe(() -> "No port defined for display of " + vmName);
            return;
        }
        StringBuffer data = new StringBuffer(100)
            .append("[virt-viewer]\ntype=spice\nhost=")
            .append(addr.get().getHostAddress()).append("\nport=")
            .append(port.get().toString())
            .append('\n');
        if (password != null) {
            data.append("password=").append(password).append('\n');
        }
        vmDef.<String> fromVm("display", "spice", "proxyUrl")
            .ifPresent(u -> {
                if (!Strings.isNullOrEmpty(u)) {
                    data.append("proxy=").append(u).append('\n');
                }
            });
        if (deleteConnectionFile) {
            data.append("delete-this-file=1\n");
        }
        connection.respond(new NotifyConletView(type(),
            model.getConletId(), "openConsole", "application/x-virt-viewer",
            Base64.getEncoder().encodeToString(data.toString().getBytes())));
    }

    private Optional<InetAddress> displayIp(VmDefinition vmDef) {
        Optional<String> server = vmDef.fromVm("display", "spice", "server");
        if (server.isPresent()) {
            var srv = server.get();
            try {
                var addr = InetAddress.getByName(srv);
                logger.fine(() -> "Using IP address from CRD for "
                    + vmDef.getMetadata().getName() + ": " + addr);
                return Optional.of(addr);
            } catch (UnknownHostException e) {
                logger.log(Level.SEVERE, e, () -> "Invalid server address "
                    + srv + ": " + e.getMessage());
                return Optional.empty();
            }
        }
        var addrs = Optional.<List<String>> ofNullable(vmDef
            .extra("nodeAddresses")).orElse(Collections.emptyList()).stream()
            .map(a -> {
                try {
                    return InetAddress.getByName(a);
                } catch (UnknownHostException e) {
                    logger.warning(() -> "Invalid IP address: " + a);
                    return null;
                }
            }).filter(a -> a != null).toList();
        logger.fine(() -> "Known IP addresses for "
            + vmDef.name() + ": " + addrs);
        return addrs.stream()
            .filter(a -> preferredIpVersion.isAssignableFrom(a.getClass()))
            .findFirst().or(() -> addrs.stream().findFirst());
    }

    private void confirmReset(NotifyConletModel event,
            ConsoleConnection channel, ResourceModel model,
            ResourceBundle resourceBundle) throws TemplateNotFoundException,
            MalformedTemplateNameException, ParseException, IOException {
        Template tpl = freemarkerConfig()
            .getTemplate("VmAccess-confirmReset.ftl.html");
        channel.respond(new OpenModalDialog(type(), model.getConletId(),
            processTemplate(event, tpl,
                fmModel(event, channel, model.getConletId(), model)))
                    .addOption("cancelable", true).addOption("closeLabel", "")
                    .addOption("title",
                        resourceBundle.getString("confirmResetTitle")));
    }

    @Override
    protected boolean doSetLocale(SetLocale event, ConsoleConnection channel,
            String conletId) throws Exception {
        return true;
    }

    /**
     * The Class AccessModel.
     */
    @SuppressWarnings("PMD.DataClass")
    public static class ResourceModel extends ConletBaseModel {

        /**
         * The Enum ResourceType.
         */
        @SuppressWarnings("PMD.ShortVariable")
        public enum Type {
            VM, POOL
        }

        private Type type;
        private String name;

        /**
         * Instantiates a new resource model.
         *
         * @param conletId the conlet id
         */
        public ResourceModel(@JsonProperty("conletId") String conletId) {
            super(conletId);
        }

        /**
         * Gets the resource name.
         *
         * @return the string
         */
        @JsonGetter("name")
        public String name() {
            return name;
        }

        /**
         * Sets the name.
         *
         * @param name the resource name to set
         */
        public void setName(String name) {
            this.name = name;
        }

        /**
         * @return the resourceType
         */
        @JsonGetter("type")
        public Type type() {
            return type;
        }

        /**
         * Sets the type.
         *
         * @param type the resource type to set
         */
        public void setType(Type type) {
            this.type = type;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = super.hashCode();
            result = prime * result + java.util.Objects.hash(name, type);
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!super.equals(obj)) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            ResourceModel other = (ResourceModel) obj;
            return java.util.Objects.equals(name, other.name)
                && type == other.type;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder(50);
            builder.append("AccessModel [resourceType=").append(type)
                .append(", resourceName=").append(name).append(']');
            return builder.toString();
        }

    }
}
