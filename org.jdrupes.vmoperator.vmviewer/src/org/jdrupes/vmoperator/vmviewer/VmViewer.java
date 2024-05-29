/*
 * VM-Operator
 * Copyright (C) 2023 Michael N. Lipp
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

package org.jdrupes.vmoperator.vmviewer;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
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
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.logging.Level;
import org.jdrupes.json.JsonBeanDecoder;
import org.jdrupes.json.JsonDecodeException;
import org.jdrupes.vmoperator.common.K8sDynamicModel;
import org.jdrupes.vmoperator.common.K8sObserver;
import org.jdrupes.vmoperator.common.VmDefinitionModel;
import org.jdrupes.vmoperator.common.VmDefinitionModel.Permission;
import org.jdrupes.vmoperator.manager.events.ChannelCache;
import org.jdrupes.vmoperator.manager.events.GetDisplayPassword;
import org.jdrupes.vmoperator.manager.events.ModifyVm;
import org.jdrupes.vmoperator.manager.events.VmChannel;
import org.jdrupes.vmoperator.manager.events.VmDefChanged;
import org.jdrupes.vmoperator.util.GsonPtr;
import org.jgrapes.core.Channel;
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
import org.jgrapes.webconsole.base.events.AddConletType;
import org.jgrapes.webconsole.base.events.AddPageResources.ScriptResource;
import org.jgrapes.webconsole.base.events.ConletDeleted;
import org.jgrapes.webconsole.base.events.ConsoleReady;
import org.jgrapes.webconsole.base.events.DeleteConlet;
import org.jgrapes.webconsole.base.events.NotifyConletModel;
import org.jgrapes.webconsole.base.events.NotifyConletView;
import org.jgrapes.webconsole.base.events.OpenModalDialog;
import org.jgrapes.webconsole.base.events.RenderConlet;
import org.jgrapes.webconsole.base.events.RenderConletRequestBase;
import org.jgrapes.webconsole.base.events.SetLocale;
import org.jgrapes.webconsole.base.freemarker.FreeMarkerConlet;

/**
 * The Class VmConlet.
 */
@SuppressWarnings({ "PMD.DataflowAnomalyAnalysis", "PMD.ExcessiveImports",
    "PMD.CouplingBetweenObjects" })
public class VmViewer extends FreeMarkerConlet<VmViewer.ViewerModel> {

    private static final Set<RenderMode> MODES = RenderMode.asSet(
        RenderMode.Preview, RenderMode.Edit);
    private final ChannelCache<String, VmChannel,
            VmDefinitionModel> channelManager = new ChannelCache<>();
    private static ObjectMapper objectMapper
        = new ObjectMapper().registerModule(new JavaTimeModule());
    private Class<?> preferredIpVersion = Inet4Address.class;

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
    public VmViewer(Channel componentChannel) {
        super(componentChannel);
    }

    /**
     * Configure the component.
     *
     * @param event the event
     */
    @Handler
    public void onConfigurationUpdate(ConfigurationUpdate event) {
        event.structured(componentPath()).ifPresent(c -> {
            @SuppressWarnings("unchecked")
            var dispRes = (Map<String, Object>) c
                .getOrDefault("displayResource", Collections.emptyMap());
            switch ((String) dispRes.getOrDefault("preferredIpVersion", "")) {
            case "ipv6":
                preferredIpVersion = Inet6Address.class;
                break;
            case "ipv4":
            default:
                preferredIpVersion = Inet4Address.class;
                break;
            }
        });
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
                    type(), "VmViewer-functions.js"))));
    }

    private String storagePath(Session session, String conletId) {
        return "/" + WebConsoleUtils.userFromSession(session)
            .map(ConsoleUser::getName).orElse("")
            + "/" + VmViewer.class.getName() + "/" + conletId;
    }

    @Override
    protected Optional<ViewerModel> createStateRepresentation(Event<?> event,
            ConsoleConnection connection, String conletId) throws Exception {
        var model = new ViewerModel(conletId);
        String jsonState = objectMapper.writeValueAsString(model);
        connection.respond(new KeyValueStoreUpdate().update(
            storagePath(connection.session(), model.getConletId()), jsonState));
        return Optional.of(model);
    }

    @Override
    @SuppressWarnings("PMD.EmptyCatchBlock")
    protected Optional<ViewerModel> recreateState(Event<?> event,
            ConsoleConnection channel, String conletId) throws Exception {
        KeyValueStoreQuery query = new KeyValueStoreQuery(
            storagePath(channel.session(), conletId), channel);
        newEventPipeline().fire(query, channel);
        try {
            if (!query.results().isEmpty()) {
                var json = query.results().get(0).values().stream().findFirst()
                    .get();
                ViewerModel model
                    = objectMapper.readValue(json, ViewerModel.class);
                return Optional.of(model);
            }
        } catch (InterruptedException e) {
            // Means we have no result.
        }

        // Fall back to creating default state.
        return createStateRepresentation(event, channel, conletId);
    }

    @Override
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    protected Set<RenderMode> doRenderConlet(RenderConletRequestBase<?> event,
            ConsoleConnection channel, String conletId, ViewerModel conletState)
            throws Exception {
        ResourceBundle resourceBundle = resourceBundle(channel.locale());
        Set<RenderMode> renderedAs = new HashSet<>();
        if (event.renderAs().contains(RenderMode.Preview)) {
            Template tpl
                = freemarkerConfig().getTemplate("VmViewer-preview.ftl.html");
            channel.respond(new RenderConlet(type(), conletId,
                processTemplate(event, tpl,
                    fmModel(event, channel, conletId, conletState)))
                        .setRenderAs(
                            RenderMode.Preview.addModifiers(event.renderAs()))
                        .setSupportedModes(MODES));
            renderedAs.add(RenderMode.Preview);
            if (!Strings.isNullOrEmpty(conletState.vmName())) {
                updateConfig(channel, conletState);
            }
        }
        if (event.renderAs().contains(RenderMode.Edit)) {
            Template tpl = freemarkerConfig()
                .getTemplate("VmViewer-edit.ftl.html");
            var fmModel = fmModel(event, channel, conletId, conletState);
            fmModel.put("vmNames", channelManager.associated().stream()
                .filter(d -> !permissions(d, channel.session()).isEmpty())
                .map(d -> d.getMetadata().getName()).toList());
            channel.respond(new OpenModalDialog(type(), conletId,
                processTemplate(event, tpl, fmModel))
                    .addOption("cancelable", true)
                    .addOption("okayLabel",
                        resourceBundle.getString("okayLabel")));
        }
        return renderedAs;
    }

    private Set<Permission> permissions(VmDefinitionModel vmDef,
            Session session) {
        var user = WebConsoleUtils.userFromSession(session)
            .map(ConsoleUser::getName).orElse(null);
        var roles = WebConsoleUtils.rolesFromSession(session)
            .stream().map(ConsoleRole::getName).toList();
        return vmDef.permissionsFor(user, roles);
    }

    private void updateConfig(ConsoleConnection channel, ViewerModel model) {
        channel.respond(new NotifyConletView(type(),
            model.getConletId(), "updateConfig", model.vmName()));
        updateVmDef(channel, model);
    }

    private void updateVmDef(ConsoleConnection channel, ViewerModel model) {
        if (Strings.isNullOrEmpty(model.vmName())) {
            return;
        }
        channelManager.associated(model.vmName()).ifPresent(vmDef -> {
            try {
                var def = JsonBeanDecoder.create(vmDef.data().toString())
                    .readObject();
                def.setField("userPermissions",
                    permissions(vmDef, channel.session()).stream()
                        .map(Permission::toString).toList());
                channel.respond(new NotifyConletView(type(),
                    model.getConletId(), "updateVmDefinition", def));
            } catch (JsonDecodeException e) {
                logger.log(Level.SEVERE, e,
                    () -> "Failed to serialize VM definition");
            }
        });
    }

    @Override
    protected void doConletDeleted(ConletDeleted event,
            ConsoleConnection channel, String conletId, ViewerModel conletState)
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
     * @throws JsonDecodeException the json decode exception
     * @throws IOException 
     */
    @Handler(namedChannels = "manager")
    @SuppressWarnings({ "PMD.ConfusingTernary", "PMD.CognitiveComplexity",
        "PMD.AvoidInstantiatingObjectsInLoops", "PMD.AvoidDuplicateLiterals",
        "PMD.ConfusingArgumentToVarargsMethod" })
    public void onVmDefChanged(VmDefChanged event, VmChannel channel)
            throws JsonDecodeException, IOException {
        var vmDef = new VmDefinitionModel(channel.client().getJSON()
            .getGson(), event.vmDefinition().data());
        GsonPtr.to(vmDef.data()).to("metadata").get(JsonObject.class)
            .remove("managedFields");
        var vmName = vmDef.getMetadata().getName();
        if (event.type() == K8sObserver.ResponseType.DELETED) {
            channelManager.remove(vmName);
        } else {
            channelManager.put(vmName, channel, vmDef);
        }
        for (var entry : conletIdsByConsoleConnection().entrySet()) {
            var connection = entry.getKey();
            for (var conletId : entry.getValue()) {
                var model = stateFromSession(connection.session(), conletId);
                if (model.isEmpty() || !model.get().vmName().equals(vmName)) {
                    continue;
                }
                if (event.type() == K8sObserver.ResponseType.DELETED) {
                    connection.respond(
                        new DeleteConlet(conletId, Collections.emptySet()));
                } else {
                    updateVmDef(connection, model.get());
                }
            }
        }
    }

    @Override
    @SuppressWarnings({ "PMD.AvoidDecimalLiteralsInBigDecimalConstructor",
        "PMD.ConfusingArgumentToVarargsMethod" })
    protected void doUpdateConletState(NotifyConletModel event,
            ConsoleConnection channel, ViewerModel model)
            throws Exception {
        event.stop();
        var vmName = event.params().asString(0);
        var vmChannel = channelManager.channel(vmName).orElse(null);
        if (vmChannel == null) {
            return;
        }
        var vmDef = channelManager.associated(vmName);
        var perms = vmDef.map(d -> permissions(d, channel.session()))
            .orElse(Collections.emptySet());
        switch (event.method()) {
        case "selectedVm":
            model.setVmName(event.params().asString(0));
            String jsonState = objectMapper.writeValueAsString(model);
            channel.respond(new KeyValueStoreUpdate().update(storagePath(
                channel.session(), model.getConletId()), jsonState));
            updateConfig(channel, model);
            break;
        case "start":
            if (perms.contains(Permission.START)) {
                fire(new ModifyVm(vmName, "state", "Running", vmChannel));
            }
            break;
        case "stop":
            if (perms.contains(Permission.STOP)) {
                fire(new ModifyVm(vmName, "state", "Stopped", vmChannel));
            }
            break;
        case "openConsole":
            if (perms.contains(Permission.ACCESS_CONSOLE)) {
                channelManager.channel(vmName).ifPresent(
                    vc -> fire(Event.onCompletion(
                        new GetDisplayPassword(vmName),
                        ds -> openConsole(vmName, channel, model, ds)), vc));
            }
            break;
        default:// ignore
            break;
        }
    }

    private void openConsole(String vmName, ConsoleConnection connection,
            ViewerModel model, GetDisplayPassword pwQuery) {
        var vmDef = channelManager.associated(vmName).orElse(null);
        if (vmDef == null) {
            return;
        }
        var addr = displayIp(vmDef);
        if (addr.isEmpty()) {
            logger.severe(() -> "Failed to find display IP for " + vmName);
            return;
        }
        var port = GsonPtr.to(vmDef.data()).get(JsonPrimitive.class, "spec",
            "vm", "display", "spice", "port");
        if (port.isEmpty()) {
            logger.severe(() -> "No port defined for display of " + vmName);
            return;
        }
        var proxyUrl = GsonPtr.to(vmDef.data()).get(JsonPrimitive.class, "spec",
            "vm", "display", "spice", "proxyUrl");
        StringBuffer data = new StringBuffer(100)
            .append("[virt-viewer]\ntype=spice\nhost=")
            .append(addr.get().getHostAddress()).append("\nport=")
            .append(Integer.toString(port.get().getAsInt())).append('\n');
        pwQuery.password().ifPresent(p -> {
            data.append("password=").append(p).append('\n');
        });
        proxyUrl.map(JsonPrimitive::getAsString).ifPresent(u -> {
            if (!Strings.isNullOrEmpty(u)) {
                data.append("proxy=").append(u).append('\n');
            }
        });
        connection.respond(new NotifyConletView(type(),
            model.getConletId(), "openConsole", "application/x-virt-viewer",
            Base64.getEncoder().encodeToString(data.toString().getBytes())));
    }

    private Optional<InetAddress> displayIp(K8sDynamicModel vmDef) {
        var server = GsonPtr.to(vmDef.data()).get(JsonPrimitive.class, "spec",
            "vm", "display", "spice", "server");
        if (server.isPresent()) {
            var srv = server.get().getAsString();
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
        var addrs = GsonPtr.to(vmDef.data()).getAsListOf(JsonPrimitive.class,
            "nodeAddresses").stream().map(JsonPrimitive::getAsString)
            .map(a -> {
                try {
                    return InetAddress.getByName(a);
                } catch (UnknownHostException e) {
                    logger.warning(() -> "Invalid IP address: " + a);
                    return null;
                }
            }).filter(a -> a != null).toList();
        logger.fine(() -> "Known IP addresses for "
            + vmDef.getMetadata().getName() + ": " + addrs);
        return addrs.stream()
            .filter(a -> preferredIpVersion.isAssignableFrom(a.getClass()))
            .findFirst().or(() -> addrs.stream().findFirst());
    }

    @Override
    protected boolean doSetLocale(SetLocale event, ConsoleConnection channel,
            String conletId) throws Exception {
        return true;
    }

    /**
     * The Class VmsModel.
     */
    public static class ViewerModel extends ConletBaseModel {

        private String vmName;

        /**
         * Instantiates a new vms model.
         *
         * @param conletId the conlet id
         */
        public ViewerModel(@JsonProperty("conletId") String conletId) {
            super(conletId);
        }

        /**
         * Gets the vm name.
         *
         * @return the vmName
         */
        @JsonGetter("vmName")
        public String vmName() {
            return vmName;
        }

        /**
         * Sets the vm name.
         *
         * @param vmName the vmName to set
         */
        public void setVmName(String vmName) {
            this.vmName = vmName;
        }

    }
}
