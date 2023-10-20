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

import com.google.gson.JsonObject;
import freemarker.core.ParseException;
import freemarker.template.MalformedTemplateNameException;
import freemarker.template.Template;
import freemarker.template.TemplateNotFoundException;
import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.util.generic.dynamic.DynamicKubernetesObject;
import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.jdrupes.json.JsonBeanDecoder;
import org.jdrupes.json.JsonDecodeException;
import org.jdrupes.vmoperator.manager.events.StartVm;
import org.jdrupes.vmoperator.manager.events.StopVm;
import org.jdrupes.vmoperator.manager.events.VmChannel;
import org.jdrupes.vmoperator.manager.events.VmDefChanged;
import org.jdrupes.vmoperator.manager.events.VmDefChanged.Type;
import org.jdrupes.vmoperator.util.GsonPtr;
import org.jgrapes.core.Channel;
import org.jgrapes.core.Event;
import org.jgrapes.core.Manager;
import org.jgrapes.core.NamedChannel;
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
 */
@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
public class VmConlet extends FreeMarkerConlet<VmConlet.VmsModel> {

    private static final Set<RenderMode> MODES = RenderMode.asSet(
        RenderMode.Preview, RenderMode.View);
    private final Map<String, DynamicKubernetesObject> vmInfos
        = new ConcurrentHashMap<>();

    /**
     * Creates a new component with its channel set to the given channel.
     * 
     * @param componentChannel the channel that the component's handlers listen
     * on by default and that {@link Manager#fire(Event, Channel...)}
     * sends the event to
     */
    public VmConlet(Channel componentChannel) {
        super(componentChannel);
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
        Set<RenderMode> renderedAs = new HashSet<>();
        boolean sendData = false;
        if (event.renderAs().contains(RenderMode.Preview)) {
            Template tpl
                = freemarkerConfig().getTemplate("VmConlet-preview.ftl.html");
            channel.respond(new RenderConlet(type(), conletId,
                processTemplate(event, tpl,
                    fmModel(event, channel, conletId, conletState)))
                        .setRenderAs(
                            RenderMode.Preview.addModifiers(event.renderAs()))
                        .setSupportedModes(MODES));
            renderedAs.add(RenderMode.View);
            sendData = true;
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
            sendData = true;
        }
        if (sendData) {
            for (var vmInfo : vmInfos.values()) {
                var def = JsonBeanDecoder.create(vmInfo.getRaw().toString())
                    .readObject();
                channel.respond(new NotifyConletView(type(),
                    conletId, "updateVm", def));
            }
        }

        return renderedAs;
    }

    /**
     * Track the VM definitions.
     *
     * @param event the event
     * @param channel the channel
     * @throws JsonDecodeException 
     */
    @Handler(namedChannels = "manager")
    @SuppressWarnings({ "PMD.ConfusingTernary",
        "PMD.AvoidInstantiatingObjectsInLoops" })
    public void onVmDefChanged(VmDefChanged event, VmChannel channel)
            throws JsonDecodeException {
        if (event.type() == Type.DELETED) {
            vmInfos.remove(event.vmDefinition().getMetadata().getName());
            for (var entry : conletIdsByConsoleConnection().entrySet()) {
                for (String conletId : entry.getValue()) {
                    entry.getKey().respond(new NotifyConletView(type(),
                        conletId, "removeVm"));
                }
            }
        } else {
            var vmDef = new DynamicKubernetesObject(
                event.vmDefinition().getRaw().deepCopy());
            GsonPtr.to(vmDef.getRaw()).to("metadata").get(JsonObject.class)
                .remove("managedFields");
            var vmSpec = GsonPtr.to(vmDef.getRaw()).to("spec", "vm");
            vmSpec.set("maximumRam", Quantity.fromString(
                vmSpec.getAsString("maximumRam").orElse("0")).getNumber()
                .toBigInteger().toString());
            vmSpec.set("currentRam", Quantity.fromString(
                vmSpec.getAsString("currentRam").orElse("0")).getNumber()
                .toBigInteger().toString());
            var status = GsonPtr.to(vmDef.getRaw()).to("status");
            status.set("ram", Quantity.fromString(
                status.getAsString("ram").orElse("0")).getNumber()
                .toBigInteger().toString());
            String vmName = event.vmDefinition().getMetadata().getName();
            vmInfos.put(vmName, vmDef);

            // Extract running
            var def = JsonBeanDecoder.create(vmDef.getRaw().toString())
                .readObject();
            for (var entry : conletIdsByConsoleConnection().entrySet()) {
                for (String conletId : entry.getValue()) {
                    entry.getKey().respond(new NotifyConletView(type(),
                        conletId, "updateVm", def));
                }
            }
        }
    }

    @Override
    protected void doUpdateConletState(NotifyConletModel event,
            ConsoleConnection channel, VmsModel conletState)
            throws Exception {
        event.stop();
        switch (event.method()) {
        case "start":
            fire(new StartVm(event.params().asString(0),
                new NamedChannel("manager")));
            break;
        case "stop":
            fire(new StopVm(event.params().asString(0),
                new NamedChannel("manager")));
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
