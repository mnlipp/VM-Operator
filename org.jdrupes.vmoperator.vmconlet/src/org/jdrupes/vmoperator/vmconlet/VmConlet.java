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

package org.jdrupes.vmoperator.vmconlet;

import freemarker.core.ParseException;
import freemarker.template.MalformedTemplateNameException;
import freemarker.template.Template;
import freemarker.template.TemplateNotFoundException;
import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.custom.Quantity.Format;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jdrupes.vmoperator.common.K8sObserver;
import org.jdrupes.vmoperator.common.VmDefinition;
import org.jdrupes.vmoperator.manager.events.ChannelTracker;
import org.jdrupes.vmoperator.manager.events.ModifyVm;
import org.jdrupes.vmoperator.manager.events.VmChannel;
import org.jdrupes.vmoperator.manager.events.VmDefChanged;
import org.jdrupes.vmoperator.util.DataPath;
import org.jgrapes.core.Channel;
import org.jgrapes.core.Event;
import org.jgrapes.core.Manager;
import org.jgrapes.core.annotation.Handler;
import org.jgrapes.webconsole.base.Conlet.RenderMode;
import org.jgrapes.webconsole.base.ConletBaseModel;
import org.jgrapes.webconsole.base.ConsoleConnection;
import org.jgrapes.webconsole.base.events.AddConletRequest;
import org.jgrapes.webconsole.base.events.AddConletType;
import org.jgrapes.webconsole.base.events.AddPageResources.ScriptResource;
import org.jgrapes.webconsole.base.events.ConsoleReady;
import org.jgrapes.webconsole.base.events.NotifyConletModel;
import org.jgrapes.webconsole.base.events.NotifyConletView;
import org.jgrapes.webconsole.base.events.RenderConlet;
import org.jgrapes.webconsole.base.events.RenderConletRequestBase;
import org.jgrapes.webconsole.base.events.SetLocale;
import org.jgrapes.webconsole.base.freemarker.FreeMarkerConlet;

/**
 * The Class VmConlet.
 */
@SuppressWarnings({ "PMD.DataflowAnomalyAnalysis",
    "PMD.CouplingBetweenObjects" })
public class VmConlet extends FreeMarkerConlet<VmConlet.VmsModel> {

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
    public VmConlet(Channel componentChannel) {
        super(componentChannel);
        setPeriodicRefresh(Duration.ofMinutes(1), () -> new Update());
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
                    type(), "VmConlet-functions.js"))));
    }

    @Override
    protected Optional<VmsModel> createNewState(AddConletRequest event,
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
                = freemarkerConfig().getTemplate("VmConlet-preview.ftl.html");
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
                = freemarkerConfig().getTemplate("VmConlet-view.ftl.html");
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
                channel.respond(new NotifyConletView(type(),
                    conletId, "updateVm",
                    simplifiedVmDefinition(item.associated())));
            }
        }

        return renderedAs;
    }

    @SuppressWarnings("PMD.AvoidDuplicateLiterals")
    private Map<String, Object> simplifiedVmDefinition(VmDefinition vmDef) {
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
        status.put("ram", Quantity.fromString(
            DataPath.<String> get(status, "ram").orElse("0")).getNumber()
            .toBigInteger());

        // Build result
        return Map.of("metadata",
            Map.of("namespace", vmDef.namespace(),
                "name", vmDef.name()),
            "spec", spec,
            "status", status,
            "nodeName", vmDef.extra("nodeName"));
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
                    entry.getKey().respond(new NotifyConletView(type(),
                        conletId, "updateVm", simplifiedVmDefinition(vmDef)));
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
            var status = vmDef.status();
            summary.usedCpus += DataPath.<Number> get(status, "cpus")
                .map(Number::intValue).orElse(0);
            summary.usedRam = summary.usedRam
                .add(DataPath.<String> get(status, "ram")
                    .map(r -> Quantity.fromString(r).getNumber().toBigInteger())
                    .orElse(BigInteger.ZERO));
            summary.runningVms = DataPath
                .<List<Map<String, Object>>> get(vmDef.status(), "conditions")
                .orElse(Collections.emptyList()).stream()
                .filter(cond -> DataPath.get(cond, "type")
                    .map(t -> "Running".equals(t)).orElse(false)
                    && DataPath.get(cond, "status")
                        .map(s -> "True".equals(s)).orElse(false))
                .count();
        }
        cachedSummary = summary;
        return summary;
    }

    @Override
    @SuppressWarnings("PMD.AvoidDecimalLiteralsInBigDecimalConstructor")
    protected void doUpdateConletState(NotifyConletModel event,
            ConsoleConnection channel, VmsModel conletState)
            throws Exception {
        event.stop();
        String vmName = event.param(0);
        var vmChannel = channelTracker.channel(vmName).orElse(null);
        if (vmChannel == null) {
            return;
        }
        switch (event.method()) {
        case "start":
            fire(new ModifyVm(vmName, "state", "Running", vmChannel));
            break;
        case "stop":
            fire(new ModifyVm(vmName, "state", "Stopped", vmChannel));
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
