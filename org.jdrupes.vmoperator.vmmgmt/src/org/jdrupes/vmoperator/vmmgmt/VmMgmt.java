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

package org.jdrupes.vmoperator.vmmgmt;

import freemarker.core.ParseException;
import freemarker.template.MalformedTemplateNameException;
import freemarker.template.Template;
import freemarker.template.TemplateNotFoundException;
import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.custom.Quantity.Format;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import org.jdrupes.vmoperator.common.Constants.Status;
import org.jdrupes.vmoperator.common.K8sObserver;
import org.jdrupes.vmoperator.common.VmDefinition;
import org.jdrupes.vmoperator.common.VmDefinition.Permission;
import org.jdrupes.vmoperator.common.VmExtraData;
import org.jdrupes.vmoperator.manager.events.ChannelTracker;
import org.jdrupes.vmoperator.manager.events.ModifyVm;
import org.jdrupes.vmoperator.manager.events.PrepareConsole;
import org.jdrupes.vmoperator.manager.events.ResetVm;
import org.jdrupes.vmoperator.manager.events.VmChannel;
import org.jdrupes.vmoperator.manager.events.VmDefChanged;
import org.jdrupes.vmoperator.util.DataPath;
import org.jgrapes.core.Channel;
import org.jgrapes.core.Event;
import org.jgrapes.core.Manager;
import org.jgrapes.core.annotation.Handler;
import org.jgrapes.util.events.ConfigurationUpdate;
import org.jgrapes.webconsole.base.Conlet.RenderMode;
import org.jgrapes.webconsole.base.ConletBaseModel;
import org.jgrapes.webconsole.base.ConsoleConnection;
import org.jgrapes.webconsole.base.ConsoleRole;
import org.jgrapes.webconsole.base.ConsoleUser;
import org.jgrapes.webconsole.base.WebConsoleUtils;
import org.jgrapes.webconsole.base.events.AddConletType;
import org.jgrapes.webconsole.base.events.AddPageResources.ScriptResource;
import org.jgrapes.webconsole.base.events.ConsoleReady;
import org.jgrapes.webconsole.base.events.DisplayNotification;
import org.jgrapes.webconsole.base.events.NotifyConletModel;
import org.jgrapes.webconsole.base.events.NotifyConletView;
import org.jgrapes.webconsole.base.events.OpenModalDialog;
import org.jgrapes.webconsole.base.events.RenderConlet;
import org.jgrapes.webconsole.base.events.RenderConletRequestBase;
import org.jgrapes.webconsole.base.events.SetLocale;
import org.jgrapes.webconsole.base.freemarker.FreeMarkerConlet;

/**
 * The Class {@link VmMgmt}.
 */
@SuppressWarnings({ "PMD.DataflowAnomalyAnalysis", "PMD.CouplingBetweenObjects",
    "PMD.ExcessiveImports" })
public class VmMgmt extends FreeMarkerConlet<VmMgmt.VmsModel> {

    private Class<?> preferredIpVersion = Inet4Address.class;
    private boolean deleteConnectionFile = true;
    private static final Set<RenderMode> MODES = RenderMode.asSet(
        RenderMode.Preview, RenderMode.View);
    private final ChannelTracker<String, VmChannel,
            VmDefinition> channelTracker = new ChannelTracker<>();
    private final TimeSeries summarySeries = new TimeSeries(Duration.ofDays(1));
    private Summary cachedSummary;

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
    @SuppressWarnings("PMD.ConstructorCallsOverridableMethod")
    public VmMgmt(Channel componentChannel) {
        super(componentChannel);
        setPeriodicRefresh(Duration.ofMinutes(1), () -> new Update());
    }

    /**
     * Configure the component. 
     * 
     * @param event the event
     */
    @SuppressWarnings({ "unchecked", "PMD.AvoidDuplicateLiterals" })
    @Handler
    public void onConfigurationUpdate(ConfigurationUpdate event) {
        event.structured("/Manager/GuiHttpServer"
            + "/ConsoleWeblet/WebConsole/ComponentCollector/VmAccess")
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
                } catch (ClassCastException e) {
                    logger.config("Malformed configuration: " + e.getMessage());
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
                    type(), "VmMgmt-functions.js"))));
    }

    @Override
    protected Optional<VmsModel> createStateRepresentation(Event<?> event,
            ConsoleConnection connection, String conletId) throws Exception {
        return Optional.of(new VmsModel(conletId));
    }

    @Override
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    protected Set<RenderMode> doRenderConlet(RenderConletRequestBase<?> event,
            ConsoleConnection channel, String conletId, VmsModel conletState)
            throws Exception {
        Set<RenderMode> renderedAs = EnumSet.noneOf(RenderMode.class);
        boolean sendVmInfos = false;
        if (event.renderAs().contains(RenderMode.Preview)) {
            Template tpl
                = freemarkerConfig().getTemplate("VmMgmt-preview.ftl.html");
            channel.respond(new RenderConlet(type(), conletId,
                processTemplate(event, tpl,
                    fmModel(event, channel, conletId, conletState)))
                        .setRenderAs(
                            RenderMode.Preview.addModifiers(event.renderAs()))
                        .setSupportedModes(MODES));
            renderedAs.add(RenderMode.Preview);
            channel.respond(new NotifyConletView(type(),
                conletId, "summarySeries", summarySeries.entries()));
            var summary = evaluateSummary(false);
            channel.respond(new NotifyConletView(type(),
                conletId, "updateSummary", summary));
            sendVmInfos = true;
        }
        if (event.renderAs().contains(RenderMode.View)) {
            Template tpl
                = freemarkerConfig().getTemplate("VmMgmt-view.ftl.html");
            channel.respond(new RenderConlet(type(), conletId,
                processTemplate(event, tpl,
                    fmModel(event, channel, conletId, conletState)))
                        .setRenderAs(
                            RenderMode.View.addModifiers(event.renderAs()))
                        .setSupportedModes(MODES));
            renderedAs.add(RenderMode.View);
            sendVmInfos = true;
        }
        if (sendVmInfos) {
            for (var item : channelTracker.values()) {
                updateVm(channel, conletId, item.associated());
            }
        }
        return renderedAs;
    }

    private void updateVm(ConsoleConnection channel, String conletId,
            VmDefinition vmDef) {
        var user = WebConsoleUtils.userFromSession(channel.session())
            .map(ConsoleUser::getName).orElse(null);
        var roles = WebConsoleUtils.rolesFromSession(channel.session())
            .stream().map(ConsoleRole::getName).toList();
        channel.respond(new NotifyConletView(type(), conletId, "updateVm",
            simplifiedVmDefinition(vmDef, user, roles)));
    }

    @SuppressWarnings("PMD.AvoidDuplicateLiterals")
    private Map<String, Object> simplifiedVmDefinition(VmDefinition vmDef,
            String user, List<String> roles) {
        // Convert RAM sizes to unitless numbers
        var spec = DataPath.deepCopy(vmDef.spec());
        var vmSpec = DataPath.<Map<String, Object>> get(spec, "vm").get();
        vmSpec.put("maximumRam", Quantity.fromString(
            DataPath.<String> get(vmSpec, "maximumRam").orElse("0")).getNumber()
            .toBigInteger());
        vmSpec.put("currentRam", Quantity.fromString(
            DataPath.<String> get(vmSpec, "currentRam").orElse("0")).getNumber()
            .toBigInteger());
        var status = DataPath.deepCopy(vmDef.status());
        status.put(Status.RAM, Quantity.fromString(
            DataPath.<String> get(status, Status.RAM).orElse("0")).getNumber()
            .toBigInteger());

        // Build result
        return Map.of("metadata",
            Map.of("namespace", vmDef.namespace(),
                "name", vmDef.name()),
            "spec", spec,
            "status", status,
            "nodeName", vmDef.extra().map(VmExtraData::nodeName).orElse(""),
            "permissions", vmDef.permissionsFor(user, roles).stream()
                .map(VmDefinition.Permission::toString).toList());
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
        var vmName = event.vmDefinition().name();
        if (event.type() == K8sObserver.ResponseType.DELETED) {
            channelTracker.remove(vmName);
            for (var entry : conletIdsByConsoleConnection().entrySet()) {
                for (String conletId : entry.getValue()) {
                    entry.getKey().respond(new NotifyConletView(type(),
                        conletId, "removeVm", vmName));
                }
            }
        } else {
            var vmDef = event.vmDefinition();
            channelTracker.put(vmName, channel, vmDef);
            for (var entry : conletIdsByConsoleConnection().entrySet()) {
                for (String conletId : entry.getValue()) {
                    updateVm(entry.getKey(), conletId, vmDef);
                }
            }
        }

        var summary = evaluateSummary(true);
        summarySeries.add(Instant.now(), summary.usedCpus, summary.usedRam);
        for (var entry : conletIdsByConsoleConnection().entrySet()) {
            for (String conletId : entry.getValue()) {
                entry.getKey().respond(new NotifyConletView(type(),
                    conletId, "updateSummary", summary));
            }
        }
    }

    /**
     * Handle the periodic update event by sending {@link NotifyConletView}
     * events.
     *
     * @param event the event
     * @param connection the console connection
     */
    @Handler
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    public void onUpdate(Update event, ConsoleConnection connection) {
        var summary = evaluateSummary(false);
        summarySeries.add(Instant.now(), summary.usedCpus, summary.usedRam);
        for (String conletId : conletIds(connection)) {
            connection.respond(new NotifyConletView(type(),
                conletId, "updateSummary", summary));
        }
    }

    /**
     * The Class Summary.
     */
    @SuppressWarnings("PMD.DataClass")
    public static class Summary {

        /** The total vms. */
        public int totalVms;

        /** The running vms. */
        public long runningVms;

        /** The used cpus. */
        public long usedCpus;

        /** The used ram. */
        public BigInteger usedRam = BigInteger.ZERO;

        /**
         * Gets the total vms.
         *
         * @return the totalVms
         */
        public int getTotalVms() {
            return totalVms;
        }

        /**
         * Gets the running vms.
         *
         * @return the runningVms
         */
        public long getRunningVms() {
            return runningVms;
        }

        /**
         * Gets the used cpus.
         *
         * @return the usedCpus
         */
        public long getUsedCpus() {
            return usedCpus;
        }

        /**
         * Gets the used ram. Returned as String for Json rendering.
         *
         * @return the usedRam
         */
        public String getUsedRam() {
            return usedRam.toString();
        }

    }

    @SuppressWarnings({ "PMD.AvoidLiteralsInIfCondition",
        "PMD.LambdaCanBeMethodReference" })
    private Summary evaluateSummary(boolean force) {
        if (!force && cachedSummary != null) {
            return cachedSummary;
        }
        Summary summary = new Summary();
        for (var vmDef : channelTracker.associated()) {
            summary.totalVms += 1;
            summary.usedCpus += vmDef.<Number> fromStatus(Status.CPUS)
                .map(Number::intValue).orElse(0);
            summary.usedRam = summary.usedRam
                .add(vmDef.<String> fromStatus(Status.RAM)
                    .map(r -> Quantity.fromString(r).getNumber().toBigInteger())
                    .orElse(BigInteger.ZERO));
            if (vmDef.conditionStatus("Running").orElse(false)) {
                summary.runningVms += 1;
            }
        }
        cachedSummary = summary;
        return summary;
    }

    @Override
    @SuppressWarnings({ "PMD.AvoidDecimalLiteralsInBigDecimalConstructor",
        "PMD.NcssCount" })
    protected void doUpdateConletState(NotifyConletModel event,
            ConsoleConnection channel, VmsModel model) throws Exception {
        event.stop();
        String vmName = event.param(0);
        var value = channelTracker.value(vmName);
        var vmChannel = value.map(v -> v.channel()).orElse(null);
        var vmDef = value.map(v -> v.associated()).orElse(null);
        if (vmDef == null) {
            return;
        }
        var user = WebConsoleUtils.userFromSession(channel.session())
            .map(ConsoleUser::getName).orElse("");
        var roles = WebConsoleUtils.rolesFromSession(channel.session())
            .stream().map(ConsoleRole::getName).toList();
        var perms = vmDef.permissionsFor(user, roles);
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
                confirmReset(event, channel, model, vmName);
            }
            break;
        case "resetConfirmed":
            if (perms.contains(VmDefinition.Permission.RESET)) {
                fire(new ResetVm(vmName), vmChannel);
            }
            break;
        case "openConsole":
            if (perms.contains(VmDefinition.Permission.ACCESS_CONSOLE)) {
                openConsole(channel, model, vmChannel, vmDef, user, perms);
            }
            break;
        case "cpus":
            fire(new ModifyVm(vmName, "currentCpus",
                new BigDecimal(event.param(1).toString()).toBigInteger(),
                vmChannel));
            break;
        case "ram":
            fire(new ModifyVm(vmName, "currentRam",
                new Quantity(new BigDecimal(event.param(1).toString()),
                    Format.BINARY_SI).toSuffixedString(),
                vmChannel));
            break;
        default:// ignore
            break;
        }
    }

    private void confirmReset(NotifyConletModel event,
            ConsoleConnection channel, VmsModel model, String vmName)
            throws TemplateNotFoundException,
            MalformedTemplateNameException, ParseException, IOException {
        Template tpl = freemarkerConfig()
            .getTemplate("VmMgmt-confirmReset.ftl.html");
        ResourceBundle resourceBundle = resourceBundle(channel.locale());
        var fmModel = fmModel(event, channel, model.getConletId(), model);
        fmModel.put("vmName", vmName);
        channel.respond(new OpenModalDialog(type(), model.getConletId(),
            processTemplate(event, tpl, fmModel))
                .addOption("cancelable", true).addOption("closeLabel", "")
                .addOption("title",
                    resourceBundle.getString("confirmResetTitle")));
    }

    private void openConsole(ConsoleConnection channel, VmsModel model,
            VmChannel vmChannel, VmDefinition vmDef, String user,
            Set<Permission> perms) {
        ResourceBundle resourceBundle = resourceBundle(channel.locale());
        if (!vmDef.consoleAccessible(user, perms)) {
            channel.respond(new DisplayNotification(
                resourceBundle.getString("consoleTakenNotification"),
                Map.of("autoClose", 5_000, "type", "Warning")));
            return;
        }
        var pwQuery = Event.onCompletion(new PrepareConsole(vmDef, user),
            e -> gotPassword(channel, model, vmDef, e));
        fire(pwQuery, vmChannel);
    }

    private void gotPassword(ConsoleConnection channel, VmsModel model,
            VmDefinition vmDef, PrepareConsole event) {
        if (!event.passwordAvailable()) {
            return;
        }
        vmDef.extra().map(xtra -> xtra.connectionFile(event.password(),
            preferredIpVersion, deleteConnectionFile)).ifPresent(
                cf -> channel.respond(new NotifyConletView(type(),
                    model.getConletId(), "openConsole", cf)));
    }

    @Override
    protected boolean doSetLocale(SetLocale event, ConsoleConnection channel,
            String conletId) throws Exception {
        return true;
    }

    /**
     * The Class VmsModel.
     */
    public class VmsModel extends ConletBaseModel {

        /**
         * Instantiates a new vms model.
         *
         * @param conletId the conlet id
         */
        public VmsModel(String conletId) {
            super(conletId);
        }

    }
}
