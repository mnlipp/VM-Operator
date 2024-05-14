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

import freemarker.template.TemplateMethodModelEx;
import freemarker.template.TemplateModelException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import static org.jdrupes.vmoperator.manager.Constants.VM_OP_NAME;
import org.jdrupes.vmoperator.manager.events.Exit;
import org.jdrupes.vmoperator.util.FsdUtils;
import org.jgrapes.core.Channel;
import org.jgrapes.core.Component;
import org.jgrapes.core.Components;
import org.jgrapes.core.NamedChannel;
import org.jgrapes.core.annotation.Handler;
import org.jgrapes.core.events.HandlingError;
import org.jgrapes.core.events.Stop;
import org.jgrapes.http.HttpConnector;
import org.jgrapes.http.HttpServer;
import org.jgrapes.http.InMemorySessionManager;
import org.jgrapes.http.LanguageSelector;
import org.jgrapes.http.events.Request;
import org.jgrapes.io.NioDispatcher;
import org.jgrapes.io.util.PermitsPool;
import org.jgrapes.net.SocketConnector;
import org.jgrapes.net.SocketServer;
import org.jgrapes.net.SslCodec;
import org.jgrapes.util.ComponentCollector;
import org.jgrapes.util.FileSystemWatcher;
import org.jgrapes.util.YamlConfigurationStore;
import org.jgrapes.util.events.ConfigurationUpdate;
import org.jgrapes.util.events.WatchFile;
import org.jgrapes.webconlet.oidclogin.LoginConlet;
import org.jgrapes.webconlet.oidclogin.OidcClient;
import org.jgrapes.webconsole.base.BrowserLocalBackedKVStore;
import org.jgrapes.webconsole.base.ConletComponentFactory;
import org.jgrapes.webconsole.base.ConsoleWeblet;
import org.jgrapes.webconsole.base.KVStoreBasedConsolePolicy;
import org.jgrapes.webconsole.base.PageResourceProviderFactory;
import org.jgrapes.webconsole.base.WebConsole;
import org.jgrapes.webconsole.rbac.RoleConfigurator;
import org.jgrapes.webconsole.rbac.RoleConletFilter;
import org.jgrapes.webconsole.rbac.UserLogger;
import org.jgrapes.webconsole.vuejs.VueJsConsoleWeblet;

/**
 * The application class.
 */
@SuppressWarnings({ "PMD.DataflowAnomalyAnalysis", "PMD.ExcessiveImports" })
public class Manager extends Component {

    private static String version;
    private static Manager app;
    private String clusterName;
    private String namespace = "unknown";
    private static int exitStatus;

    /**
     * Instantiates a new manager.
     * @param cmdLine 
     *
     * @throws IOException Signals that an I/O exception has occurred.
     * @throws URISyntaxException 
     */
    @SuppressWarnings({ "PMD.TooFewBranchesForASwitchStatement",
        "PMD.NcssCount", "PMD.ConstructorCallsOverridableMethod" })
    public Manager(CommandLine cmdLine) throws IOException, URISyntaxException {
        super(new NamedChannel("manager"));
        // Prepare component tree
        attach(new NioDispatcher());
        attach(new FileSystemWatcher(channel()));
        attach(new Controller(channel()));

        // Configuration store with file in /etc/opt (default)
        File cfgFile = new File(cmdLine.getOptionValue('c',
            "/etc/opt/" + VM_OP_NAME.replace("-", "") + "/config.yaml"));
        logger.config(() -> "Using configuration from: " + cfgFile.getPath());
        // Don't rely on night config to produce a good exception
        // for this simple case
        if (!Files.isReadable(cfgFile.toPath())) {
            throw new IOException("Cannot read configuration file " + cfgFile);
        }
        attach(new YamlConfigurationStore(channel(), cfgFile, false));
        fire(new WatchFile(cfgFile.toPath()), channel());

        // Prepare GUI
        Channel httpTransport = new NamedChannel("guiTransport");
        attach(new SocketServer(httpTransport)
            .setConnectionLimiter(new PermitsPool(300))
            .setMinimalPurgeableTime(1000)
            .setServerAddress(new InetSocketAddress(8080))
            .setName("GuiSocketServer"));

        // Channel for HTTP application layer
        Channel httpChannel = new NamedChannel("guiHttp");

        // Create network channels for client requests.
        Channel requestChannel = attach(new SocketConnector(SELF));
        Channel secReqChannel
            = attach(new SslCodec(SELF, requestChannel, true));
        // Support for making HTTP requests
        attach(new HttpConnector(httpChannel, requestChannel,
            secReqChannel));

        // Create an HTTP server as converter between transport and application
        // layer.
        HttpServer guiHttpServer = attach(new HttpServer(httpChannel,
            httpTransport, Request.In.Get.class, Request.In.Post.class));
        guiHttpServer.setName("GuiHttpServer");

        // Build HTTP application layer
        guiHttpServer.attach(new InMemorySessionManager(httpChannel));
        guiHttpServer.attach(new LanguageSelector(httpChannel));
        URI rootUri;
        try {
            rootUri = new URI("/");
        } catch (URISyntaxException e) {
            // Cannot happen
            return;
        }
        ConsoleWeblet consoleWeblet = guiHttpServer
            .attach(new VueJsConsoleWeblet(httpChannel, SELF, rootUri) {
                @Override
                protected Map<String, Object> createConsoleBaseModel() {
                    return augmentBaseModel(super.createConsoleBaseModel());
                }
            })
            .prependClassTemplateLoader(getClass())
            .prependResourceBundleProvider(getClass())
            .prependConsoleResourceProvider(getClass());
        consoleWeblet.setName("ConsoleWeblet");
        WebConsole console = consoleWeblet.console();
        console.attach(new BrowserLocalBackedKVStore(
            console.channel(), consoleWeblet.prefix().getPath()));
        console.attach(new KVStoreBasedConsolePolicy(console.channel()));
        console.attach(new AvoidEmptyPolicy(console.channel()));
        console.attach(new RoleConfigurator(console.channel()));
        console.attach(new RoleConletFilter(console.channel()));
        console.attach(new LoginConlet(console.channel()));
        console.attach(new OidcClient(console.channel(), httpChannel,
            httpChannel, new URI("/oauth/callback"), 1500));
        console.attach(new UserLogger(console.channel()));

        // Add all available page resource providers
        console.attach(new ComponentCollector<>(
            PageResourceProviderFactory.class, console.channel()));

        // Add all available conlets
        console.attach(new ComponentCollector<>(
            ConletComponentFactory.class, console.channel(), type -> {
                if (LoginConlet.class.getName().equals(type)) {
                    // Explicitly added, see above
                    return Collections.emptyList();
                } else {
                    return Arrays.asList(Collections.emptyMap());
                }
            }));
    }

    private Map<String, Object> augmentBaseModel(Map<String, Object> base) {
        base.put("version", version);
        base.put("clusterName", new TemplateMethodModelEx() {
            @Override
            public Object exec(@SuppressWarnings("rawtypes") List arguments)
                    throws TemplateModelException {
                return clusterName;
            }
        });
        base.put("namespace", new TemplateMethodModelEx() {
            @Override
            public Object exec(@SuppressWarnings("rawtypes") List arguments)
                    throws TemplateModelException {
                return namespace;
            }
        });
        return base;
    }

    /**
     * Configure the component.
     *
     * @param event the event
     */
    @Handler
    @SuppressWarnings("PMD.DataflowAnomalyAnalysis")
    public void onConfigurationUpdate(ConfigurationUpdate event) {
        event.structured(componentPath()).ifPresent(c -> {
            if (c.containsKey("clusterName")) {
                clusterName = (String) c.get("clusterName");
            } else {
                clusterName = null;
            }
        });
        event.structured(componentPath() + "/Controller").ifPresent(c -> {
            if (c.containsKey("namespace")) {
                namespace = (String) c.get("namespace");
            }
        });
    }

    /**
     * Log the exception when a handling error is reported.
     *
     * @param event the event
     */
    @Handler(channels = Channel.class, priority = -10_000)
    @SuppressWarnings("PMD.GuardLogStatement")
    public void onHandlingError(HandlingError event) {
        logger.log(Level.WARNING, event.throwable(),
            () -> "Problem invoking handler with " + event.event() + ": "
                + event.message());
        event.stop();
    }

    /**
     * On exit.
     *
     * @param event the event
     */
    @Handler
    public void onExit(Exit event) {
        exitStatus = event.exitStatus();
    }

    /**
     * On stop.
     *
     * @param event the event
     */
    @Handler(priority = -1000)
    public void onStop(Stop event) {
        logger.fine(() -> "Application stopped.");
    }

    static {
        try {
            // Get logging properties from file and put them in effect
            InputStream props;
            var path = FsdUtils.findConfigFile(VM_OP_NAME.replace("-", ""),
                "logging.properties");
            if (path.isPresent()) {
                props = Files.newInputStream(path.get());
            } else {
                props
                    = Manager.class.getResourceAsStream("logging.properties");
            }
            LogManager.getLogManager().readConfiguration(props);
        } catch (IOException e) {
            e.printStackTrace(); // NOPMD
        }
    }

    /**
     * The main method.
     *
     * @param args the arguments
     * @throws Exception the exception
     */
    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    public static void main(String[] args) {
        try {
            // Instance logger is not available yet.
            var logger = Logger.getLogger(Manager.class.getName());
            version = Optional.ofNullable(
                Manager.class.getPackage().getImplementationVersion())
                .orElse("unknown");
            logger.config(() -> "Version: " + version);
            logger.config(() -> "running on "
                + System.getProperty("java.vm.name")
                + " (" + System.getProperty("java.vm.version") + ")"
                + " from " + System.getProperty("java.vm.vendor"));

            // Parse the command line arguments
            CommandLineParser parser = new DefaultParser();
            final Options options = new Options();
            options.addOption(new Option("c", "config", true, "The configura"
                + "tion file (defaults to /etc/opt/vmoperator/config.yaml)."));
            CommandLine cmd = parser.parse(options, args);

            // The Manager is the root component
            app = new Manager(cmd);

            // Prepare generation of Stop event
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    app.fire(new Stop());
                    Components.awaitExhaustion();
                } catch (InterruptedException e) { // NOPMD
                    // Cannot do anything about this.
                }
            }));

            // Start the application
            Components.start(app);

            // Wait for (regular) termination
            Components.awaitExhaustion();
            System.exit(exitStatus);
        } catch (IOException | InterruptedException | URISyntaxException
                | org.apache.commons.cli.ParseException e) {
            Logger.getLogger(Manager.class.getName()).log(Level.SEVERE, e,
                () -> "Failed to start manager: " + e.getMessage());
        }
    }

}
