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

package org.jdrupes.vmoperator.manager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.ResourceBundle;
import java.util.stream.Collectors;
import org.jgrapes.core.Channel;
import org.jgrapes.core.Component;
import org.jgrapes.core.annotation.Handler;
import org.jgrapes.webconlet.markdowndisplay.MarkdownDisplayConlet;
import org.jgrapes.webconsole.base.Conlet.RenderMode;
import org.jgrapes.webconsole.base.ConsoleConnection;
import org.jgrapes.webconsole.base.events.AddConletRequest;
import org.jgrapes.webconsole.base.events.ConsoleConfigured;
import org.jgrapes.webconsole.base.events.ConsoleReady;
import org.jgrapes.webconsole.base.events.RenderConlet;

/**
 * 
 */
public class AvoidEmptyPolicy extends Component {

    private final String renderedFlagName = getClass().getName() + ".rendered";

    /**
     * Creates a new component with its channel set to the given channel.
     * 
     * @param componentChannel
     */
    public AvoidEmptyPolicy(Channel componentChannel) {
        super(componentChannel);
    }

    /**
     * On console ready.
     *
     * @param event the event
     * @param connection the connection
     */
    @Handler
    public void onConsoleReady(ConsoleReady event,
            ConsoleConnection connection) {
        connection.session().put(renderedFlagName, false);
    }

    /**
     * On render conlet.
     *
     * @param event the event
     * @param connection the connection
     */
    @Handler(priority = 100)
    public void onRenderConlet(RenderConlet event,
            ConsoleConnection connection) {
        if (event.renderAs().contains(RenderMode.Preview)
            || event.renderAs().contains(RenderMode.View)) {
            connection.session().put(renderedFlagName, true);
        }
    }

    /**
     * On console configured.
     *
     * @param event the event
     * @param connection the console connection
     * @throws InterruptedException the interrupted exception
     */
    @Handler(priority = -100)
    public void onConsoleConfigured(ConsoleConfigured event,
            ConsoleConnection connection) throws InterruptedException,
            IOException {
        if ((Boolean) connection.session().getOrDefault(renderedFlagName,
            false)) {
            return;
        }
        var resourceBundle = ResourceBundle.getBundle(
            getClass().getPackage().getName() + ".l10n", connection.locale(),
            getClass().getClassLoader(),
            ResourceBundle.Control.getNoFallbackControl(
                ResourceBundle.Control.FORMAT_DEFAULT));
        var locale = resourceBundle.getLocale().toString();
        String shortDesc;
        try (BufferedReader shortDescReader
            = new BufferedReader(new InputStreamReader(
                AvoidEmptyPolicy.class.getResourceAsStream(
                    "ManagerIntro-Preview" + (locale.isEmpty() ? ""
                        : "_" + locale) + ".md"),
                "utf-8"))) {
            shortDesc
                = shortDescReader.lines().collect(Collectors.joining("\n"));
        }
        fire(new AddConletRequest(event.event().event().renderSupport(),
            MarkdownDisplayConlet.class.getName(),
            RenderMode.asSet(RenderMode.Preview))
                .addProperty(MarkdownDisplayConlet.CONLET_ID,
                    getClass().getName())
                .addProperty(MarkdownDisplayConlet.TITLE,
                    resourceBundle.getString("consoleTitle"))
                .addProperty(MarkdownDisplayConlet.PREVIEW_SOURCE,
                    shortDesc)
                .addProperty(MarkdownDisplayConlet.DELETABLE, true)
                .addProperty(MarkdownDisplayConlet.EDITABLE_BY,
                    Collections.EMPTY_SET),
            connection);
    }

}
