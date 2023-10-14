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
import java.io.IOException;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;
import org.jdrupes.vmoperator.manager.events.VmChannel;
import org.jdrupes.vmoperator.manager.events.VmDefChanged;
import org.jgrapes.core.Channel;
import org.jgrapes.core.Event;
import org.jgrapes.core.Manager;
import org.jgrapes.core.annotation.Handler;
import org.jgrapes.webconsole.base.Conlet.RenderMode;
import org.jgrapes.webconsole.base.ConsoleConnection;
import org.jgrapes.webconsole.base.events.AddConletType;
import org.jgrapes.webconsole.base.events.AddPageResources.ScriptResource;
import org.jgrapes.webconsole.base.events.ConsoleReady;
import org.jgrapes.webconsole.base.events.RenderConlet;
import org.jgrapes.webconsole.base.events.RenderConletRequestBase;
import org.jgrapes.webconsole.base.events.SetLocale;
import org.jgrapes.webconsole.base.freemarker.FreeMarkerConlet;

/**
 */
public class VmConlet extends FreeMarkerConlet<Serializable> {

    private static final Set<RenderMode> MODES = RenderMode.asSet(
        RenderMode.Preview, RenderMode.View);

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
                    type(), "VmConlet-functions.js")))
//            .addCss(event.renderSupport(),
//                WebConsoleUtils.uriFromPath("VmConlet-style.css"))
        );
    }

    @Override
    protected Set<RenderMode> doRenderConlet(RenderConletRequestBase<?> event,
            ConsoleConnection channel, String conletId,
            Serializable conletState)
            throws Exception {
        Set<RenderMode> renderedAs = new HashSet<>();
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
        }
        return renderedAs;
    }

    /**
     * Track the VM definitions.
     *
     * @param event the event
     * @param channel the channel
     */
    @Handler
    @SuppressWarnings("PMD.ConfusingTernary")
    public void onVmDefChanged(VmDefChanged event, VmChannel channel) {

    }

    @Override
    protected boolean doSetLocale(SetLocale event, ConsoleConnection channel,
            String conletId) throws Exception {
        return true;
    }

}
