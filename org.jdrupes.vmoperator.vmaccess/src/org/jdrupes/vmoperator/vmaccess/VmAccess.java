/*
 * VM-Operator
 * Copyright (C) 2023,2025 Michael N. Lipp
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
import java.time.Duration;
import java.util.Collections;
import java.util.EnumSet;
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
import org.jdrupes.vmoperator.manager.events.AssignVm;
import org.jdrupes.vmoperator.manager.events.GetDisplayPassword;
import org.jdrupes.vmoperator.manager.events.GetPools;
import org.jdrupes.vmoperator.manager.events.GetVms;
import org.jdrupes.vmoperator.manager.events.GetVms.VmData;
import org.jdrupes.vmoperator.manager.events.ModifyVm;
import org.jdrupes.vmoperator.manager.events.ResetVm;
import org.jdrupes.vmoperator.manager.events.VmChannel;
import org.jdrupes.vmoperator.manager.events.VmDefChanged;
import org.jdrupes.vmoperator.manager.events.VmPoolChanged;
import org.jgrapes.core.Channel;
import org.jgrapes.core.Components;
import org.jgrapes.core.Event;
import org.jgrapes.core.EventPipeline;
import org.jgrapes.core.Manager;
import org.jgrapes.core.annotation.Handler;
import org.jgrapes.core.events.Start;
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
import org.jgrapes.webconsole.base.events.DisplayNotification;
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
    private static final String POOL_NAME_PROPERTY = "poolName";
    private static final String RENDERED
        = VmAccess.class.getName() + ".rendered";
    private static final String PENDING
        = VmAccess.class.getName() + ".pending";
    private static final Set<RenderMode> MODES = RenderMode.asSet(
        RenderMode.Preview, RenderMode.Edit);
    private static final Set<RenderMode> MODES_FOR_GENERATED = RenderMode.asSet(
        RenderMode.Preview, RenderMode.StickyPreview);
    private EventPipeline appPipeline;
    private static ObjectMapper objectMapper
        = new ObjectMapper().registerModule(new JavaTimeModule());
    private Class<?> preferredIpVersion = Inet4Address.class;
    private Set<String> syncUsers = Collections.emptySet();
    private Set<String> syncRoles = Collections.emptySet();
    private boolean deleteConnectionFile = true;

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
     * On start.
     *
     * @param event the event
     */
    @Handler
    public void onStart(Start event) {
        appPipeline = event.processedBy().get();
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
                            .map(Object::toString).map(Boolean::parseBoolean)
                            .orElse(true);

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
        addMissingConlets(event, connection, rendered);
    }

    @SuppressWarnings({ "PMD.AvoidInstantiatingObjectsInLoops",
        "PMD.AvoidDuplicateLiterals" })
    private void addMissingConlets(ConsoleConfigured event,
            ConsoleConnection connection, final Set<ResourceModel> rendered)
            throws InterruptedException {
        var session = connection.session();

        // Evaluate missing VMs
        var missingVms = appPipeline.fire(new GetVms().accessibleFor(
            WebConsoleUtils.userFromSession(session)
                .map(ConsoleUser::getName).orElse(null),
            WebConsoleUtils.rolesFromSession(session).stream()
                .map(ConsoleRole::getName).toList()))
            .get().stream().map(d -> d.definition().name())
            .collect(Collectors.toCollection(HashSet::new));
        missingVms.removeAll(rendered.stream()
            .filter(r -> r.mode() == ResourceModel.Mode.VM)
            .map(ResourceModel::name).toList());

        // Evaluate missing pools
        var missingPools = appPipeline.fire(new GetPools().accessibleFor(
            WebConsoleUtils.userFromSession(session)
                .map(ConsoleUser::getName).orElse(null),
            WebConsoleUtils.rolesFromSession(session).stream()
                .map(ConsoleRole::getName).toList()))
            .get().stream().map(VmPool::name)
            .collect(Collectors.toCollection(HashSet::new));
        missingPools.removeAll(rendered.stream()
            .filter(r -> r.mode() == ResourceModel.Mode.POOL)
            .map(ResourceModel::name).toList());

        // Nothing to do
        if (missingVms.isEmpty() && missingPools.isEmpty()) {
            return;
        }

        // Suspending to allow rendering of conlets to be noticed
        var failSafe = Components.schedule(t -> event.resumeHandling(),
            Duration.ofSeconds(1));
        event.suspendHandling(failSafe::cancel);
        connection.setAssociated(PENDING, event);

        // Create conlets for VMs and pools that haven't been rendered
        for (var vmName : missingVms) {
            fire(new AddConletRequest(event.event().event().renderSupport(),
                VmAccess.class.getName(), RenderMode.asSet(RenderMode.Preview))
                    .addProperty(VM_NAME_PROPERTY, vmName),
                connection);
        }
        for (var poolName : missingPools) {
            fire(new AddConletRequest(event.event().event().renderSupport(),
                VmAccess.class.getName(), RenderMode.asSet(RenderMode.Preview))
                    .addProperty(POOL_NAME_PROPERTY, poolName),
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
        var poolName = (String) event.properties().get(POOL_NAME_PROPERTY);
        if (poolName != null) {
            model.setMode(ResourceModel.Mode.POOL);
            model.setName(poolName);
        } else {
            model.setMode(ResourceModel.Mode.VM);
            model.setName((String) event.properties().get(VM_NAME_PROPERTY));
        }
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
            var session = channel.session();
            var vmNames = appPipeline.fire(new GetVms().accessibleFor(
                WebConsoleUtils.userFromSession(session)
                    .map(ConsoleUser::getName).orElse(null),
                WebConsoleUtils.rolesFromSession(session).stream()
                    .map(ConsoleRole::getName).toList()))
                .get().stream().map(d -> d.definition().name()).sorted()
                .toList();
            var poolNames = appPipeline.fire(new GetPools().accessibleFor(
                WebConsoleUtils.userFromSession(session)
                    .map(ConsoleUser::getName).orElse(null),
                WebConsoleUtils.rolesFromSession(session).stream()
                    .map(ConsoleRole::getName).toList()))
                .get().stream().map(VmPool::name).sorted().toList();
            Template tpl
                = freemarkerConfig().getTemplate("VmAccess-edit.ftl.html");
            var fmModel = fmModel(event, channel, conletId, model);
            fmModel.put("vmNames", vmNames);
            fmModel.put("poolNames", poolNames);
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
            ParseException, IOException, InterruptedException {
        channel.associated(PENDING, Event.class)
            .ifPresent(e -> {
                e.resumeHandling();
                channel.setAssociated(PENDING, null);
            });

        VmDefinition vmDef = null;
        if (model.mode() == ResourceModel.Mode.VM && model.name() != null) {
            // Remove conlet if VM definition has been removed
            // or user has not at least one permission
            vmDef = getVmData(model, channel).map(VmData::definition)
                .orElse(null);
            if (vmDef == null) {
                channel.respond(
                    new DeleteConlet(conletId, Collections.emptySet()));
                return Collections.emptySet();
            }
        }

        if (model.mode() == ResourceModel.Mode.POOL && model.name() != null) {
            // Remove conlet if pool definition has been removed
            // or user has not at least one permission
            VmPool pool = appPipeline
                .fire(new GetPools().withName(model.name())).get()
                .stream().findFirst().orElse(null);
            if (pool == null
                || permissions(pool, channel.session()).isEmpty()) {
                channel.respond(
                    new DeleteConlet(conletId, Collections.emptySet()));
                return Collections.emptySet();
            }
            vmDef = getVmData(model, channel).map(VmData::definition)
                .orElse(null);
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
            updatePreview(channel, model, vmDef);
        }
        return EnumSet.of(RenderMode.Preview);
    }

    private Optional<VmData> getVmData(ResourceModel model,
            ConsoleConnection channel) throws InterruptedException {
        if (model.mode() == ResourceModel.Mode.VM) {
            // Get the VM data by name.
            var session = channel.session();
            return appPipeline.fire(new GetVms().withName(model.name())
                .accessibleFor(WebConsoleUtils.userFromSession(session)
                    .map(ConsoleUser::getName).orElse(null),
                    WebConsoleUtils.rolesFromSession(session).stream()
                        .map(ConsoleRole::getName).toList()))
                .get().stream().findFirst();
        }

        // Look for an (already) assigned VM
        var user = WebConsoleUtils.userFromSession(channel.session())
            .map(ConsoleUser::getName).orElse(null);
        return appPipeline.fire(new GetVms().assignedFrom(model.name())
            .assignedTo(user)).get().stream().findFirst();
    }

    private Set<Permission> permissions(VmDefinition vmDef, Session session) {
        var user = WebConsoleUtils.userFromSession(session)
            .map(ConsoleUser::getName).orElse(null);
        var roles = WebConsoleUtils.rolesFromSession(session)
            .stream().map(ConsoleRole::getName).toList();
        return vmDef.permissionsFor(user, roles);
    }

    private Set<Permission> permissions(VmPool pool, Session session) {
        var user = WebConsoleUtils.userFromSession(session)
            .map(ConsoleUser::getName).orElse(null);
        var roles = WebConsoleUtils.rolesFromSession(session)
            .stream().map(ConsoleRole::getName).toList();
        return pool.permissionsFor(user, roles);
    }

    private Set<Permission> permissions(ResourceModel model, Session session,
            VmPool pool, VmDefinition vmDef) throws InterruptedException {
        var user = WebConsoleUtils.userFromSession(session)
            .map(ConsoleUser::getName).orElse(null);
        var roles = WebConsoleUtils.rolesFromSession(session)
            .stream().map(ConsoleRole::getName).toList();
        if (model.mode() == ResourceModel.Mode.POOL) {
            if (pool == null) {
                pool = appPipeline.fire(new GetPools()
                    .withName(model.name())).get().stream().findFirst()
                    .orElse(null);
            }
            if (pool == null) {
                return Collections.emptySet();
            }
            return pool.permissionsFor(user, roles);
        }
        if (vmDef == null) {
            vmDef = appPipeline.fire(new GetVms().assignedFrom(model.name())
                .assignedTo(user)).get().stream().map(VmData::definition)
                .findFirst().orElse(null);
        }
        if (vmDef == null) {
            return Collections.emptySet();
        }
        return vmDef.permissionsFor(user, roles);
    }

    private void updatePreview(ConsoleConnection channel, ResourceModel model,
            VmDefinition vmDef) throws InterruptedException {
        updateConfig(channel, model, vmDef);
        updateVmDef(channel, model, vmDef);
    }

    private void updateConfig(ConsoleConnection channel, ResourceModel model,
            VmDefinition vmDef) throws InterruptedException {
        channel.respond(new NotifyConletView(type(),
            model.getConletId(), "updateConfig", model.mode(), model.name(),
            permissions(model, channel.session(), null, vmDef).stream()
                .map(VmDefinition.Permission::toString).toList()));
    }

    private void updateVmDef(ConsoleConnection channel, ResourceModel model,
            VmDefinition vmDef) throws InterruptedException {
        Map<String, Object> data = null;
        if (vmDef == null) {
            model.setAssignedVm(null);
        } else {
            model.setAssignedVm(vmDef.name());
            try {
                data = Map.of("metadata",
                    Map.of("namespace", vmDef.namespace(),
                        "name", vmDef.name()),
                    "spec", vmDef.spec(),
                    "status", vmDef.getStatus());
            } catch (JsonSyntaxException e) {
                logger.log(Level.SEVERE, e,
                    () -> "Failed to serialize VM definition");
                return;
            }
        }
        channel.respond(new NotifyConletView(type(),
            model.getConletId(), "updateVmDefinition", data));
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
     * Track the VM definitions and update conlets.
     *
     * @param event the event
     * @param channel the channel
     * @throws IOException 
     * @throws InterruptedException 
     */
    @Handler(namedChannels = "manager")
    @SuppressWarnings({ "PMD.ConfusingTernary", "PMD.CognitiveComplexity",
        "PMD.AvoidInstantiatingObjectsInLoops", "PMD.AvoidDuplicateLiterals",
        "PMD.ConfusingArgumentToVarargsMethod" })
    public void onVmDefChanged(VmDefChanged event, VmChannel channel)
            throws IOException, InterruptedException {
        var vmDef = event.vmDefinition();

        // Update known conlets
        for (var entry : conletIdsByConsoleConnection().entrySet()) {
            var connection = entry.getKey();
            for (var conletId : entry.getValue()) {
                var model = stateFromSession(connection.session(), conletId);
                if (model.isEmpty()
                    || Strings.isNullOrEmpty(model.get().name())) {
                    continue;
                }
                if (model.get().mode() == ResourceModel.Mode.VM) {
                    // Check if this VM is used by conlet
                    if (!Objects.areEqual(model.get().name(), vmDef.name())) {
                        continue;
                    }
                    if (event.type() == K8sObserver.ResponseType.DELETED
                        || permissions(vmDef, connection.session()).isEmpty()) {
                        connection.respond(
                            new DeleteConlet(conletId, Collections.emptySet()));
                        continue;
                    }
                } else {
                    // Check if VM is used by pool conlet or to be assigned to
                    // it
                    var user
                        = WebConsoleUtils.userFromSession(connection.session())
                            .map(ConsoleUser::getName).orElse(null);
                    var toBeUsedByConlet = vmDef.assignedFrom()
                        .map(p -> p.equals(model.get().name())).orElse(false)
                        && vmDef.assignedTo().map(u -> u.equals(user))
                            .orElse(false);
                    if (!Objects.areEqual(model.get().assignedVm(),
                        vmDef.name()) && !toBeUsedByConlet) {
                        continue;
                    }

                    // Now unassigned if VM is deleted or no longer to be used
                    if (event.type() == K8sObserver.ResponseType.DELETED
                        || !toBeUsedByConlet) {
                        updateVmDef(connection, model.get(), null);
                        continue;
                    }
                }

                // Full update because permissions may have changed
                updatePreview(connection, model.get(), vmDef);
            }
        }
    }

    /**
     * On vm pool changed.
     *
     * @param event the event
     * @throws InterruptedException the interrupted exception
     */
    @Handler(namedChannels = "manager")
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    public void onVmPoolChanged(VmPoolChanged event)
            throws InterruptedException {
        var poolName = event.vmPool().name();
        // Update known conlets
        for (var entry : conletIdsByConsoleConnection().entrySet()) {
            var connection = entry.getKey();
            for (var conletId : entry.getValue()) {
                var model = stateFromSession(connection.session(), conletId);
                if (model.isEmpty()
                    || model.get().mode() != ResourceModel.Mode.POOL
                    || !Objects.areEqual(model.get().name(), poolName)) {
                    continue;
                }
                if (event.deleted()
                    || permissions(event.vmPool(), connection.session())
                        .isEmpty()) {
                    connection.respond(
                        new DeleteConlet(conletId, Collections.emptySet()));
                    continue;
                }
                updateConfig(connection, model.get(), null);
            }
        }
    }

    @SuppressWarnings({ "PMD.NcssCount", "PMD.CognitiveComplexity",
        "PMD.AvoidLiteralsInIfCondition" })
    @Override
    protected void doUpdateConletState(NotifyConletModel event,
            ConsoleConnection channel, ResourceModel model) throws Exception {
        event.stop();
        if ("selectedResource".equals(event.method())) {
            selectResource(event, channel, model);
            return;
        }

        Optional<VmData> vmData = getVmData(model, channel);
        if (vmData.isEmpty()) {
            if (model.mode() == ResourceModel.Mode.VM) {
                return;
            }
            if ("start".equals(event.method())) {
                // Assign a VM.
                var user = WebConsoleUtils.userFromSession(channel.session())
                    .map(ConsoleUser::getName).orElse(null);
                vmData = Optional.ofNullable(appPipeline
                    .fire(new AssignVm(model.name(), user)).get());
                if (vmData.isEmpty()) {
                    ResourceBundle resourceBundle
                        = resourceBundle(channel.locale());
                    channel.respond(new DisplayNotification(
                        resourceBundle.getString("poolEmptyNotification"),
                        Map.of("autoClose", 10_000, "type", "Error")));
                    return;
                }
            }
        }

        // Handle command for selected VM
        var vmChannel = vmData.get().channel();
        var vmDef = vmData.get().definition();
        var vmName = vmDef.metadata().getName();
        var perms = permissions(model, channel.session(), null, vmDef);
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
                openConsole(channel, model, vmChannel, vmDef, perms);
            }
            break;
        default:// ignore
            break;
        }
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

    private void openConsole(ConsoleConnection channel, ResourceModel model,
            VmChannel vmChannel, VmDefinition vmDef, Set<Permission> perms) {
        var resourceBundle = resourceBundle(channel.locale());
        var user = WebConsoleUtils.userFromSession(channel.session())
            .map(ConsoleUser::getName).orElse("");
        if (!vmDef.consoleAccessible(user, perms)) {
            channel.respond(new DisplayNotification(
                resourceBundle.getString("consoleTakenNotification"),
                Map.of("autoClose", 5_000, "type", "Warning")));
            return;
        }
        var pwQuery = Event.onCompletion(new GetDisplayPassword(vmDef, user),
            e -> {
                var data = vmDef.connectionFile(e.password().orElse(null),
                    preferredIpVersion, deleteConnectionFile);
                if (data == null) {
                    return;
                }
                channel.respond(new NotifyConletView(type(),
                    model.getConletId(), "openConsole", data));
            });
        fire(pwQuery, vmChannel);
    }

    @SuppressWarnings({ "PMD.AvoidLiteralsInIfCondition",
        "PMD.UseLocaleWithCaseConversions" })
    private void selectResource(NotifyConletModel event,
            ConsoleConnection channel, ResourceModel model)
            throws JsonProcessingException, InterruptedException {
        try {
            model.setMode(ResourceModel.Mode
                .valueOf(event.<String> param(0).toUpperCase()));
            model.setName(event.param(1));
            String jsonState = objectMapper.writeValueAsString(model);
            channel.respond(new KeyValueStoreUpdate().update(storagePath(
                channel.session(), model.getConletId()), jsonState));
            updatePreview(channel, model,
                getVmData(model, channel).map(VmData::definition).orElse(null));
        } catch (IllegalArgumentException e) {
            logger.warning(() -> "Invalid resource type: " + e.getMessage());
        }
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
        public enum Mode {
            VM, POOL
        }

        private Mode mode;
        private String name;
        private String assignedVm;

        /**
         * Instantiates a new resource model.
         *
         * @param conletId the conlet id
         */
        public ResourceModel(@JsonProperty("conletId") String conletId) {
            super(conletId);
        }

        /**
         * Returns the mode.
         *
         * @return the resourceType
         */
        @JsonGetter("mode")
        public Mode mode() {
            return mode;
        }

        /**
         * Sets the mode.
         *
         * @param mode the resource mode to set
         */
        public void setMode(Mode mode) {
            this.mode = mode;
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
         * Gets the assigned vm.
         *
         * @return the string
         */
        @JsonGetter("assignedVm")
        public String assignedVm() {
            return assignedVm;
        }

        /**
         * Sets the assigned vm.
         *
         * @param name the assigned vm
         */
        public void setAssignedVm(String name) {
            this.assignedVm = name;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = super.hashCode();
            result = prime * result + java.util.Objects.hash(mode, name);
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
            return mode == other.mode
                && java.util.Objects.equals(name, other.name);
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder(50);
            builder.append("AccessModel [mode=").append(mode)
                .append(", name=").append(name).append(']');
            return builder.toString();
        }
    }
}
