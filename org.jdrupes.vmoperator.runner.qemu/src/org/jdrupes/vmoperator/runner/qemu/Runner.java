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

package org.jdrupes.vmoperator.runner.qemu;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import freemarker.core.ParseException;
import freemarker.template.MalformedTemplateNameException;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import freemarker.template.TemplateNotFoundException;
import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.net.UnixDomainSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import static org.jdrupes.vmoperator.runner.qemu.Configuration.BOOT_MODE_SECURE;
import static org.jdrupes.vmoperator.runner.qemu.Configuration.BOOT_MODE_UEFI;
import org.jdrupes.vmoperator.util.ExtendedObjectWrapper;
import org.jgrapes.core.Channel;
import org.jgrapes.core.Component;
import org.jgrapes.core.Components;
import org.jgrapes.core.TypedIdKey;
import org.jgrapes.core.annotation.Handler;
import org.jgrapes.core.events.Start;
import org.jgrapes.core.events.Stop;
import org.jgrapes.io.NioDispatcher;
import org.jgrapes.io.events.ConnectError;
import org.jgrapes.io.events.Input;
import org.jgrapes.io.events.OpenSocketConnection;
import org.jgrapes.io.events.ProcessExited;
import org.jgrapes.io.events.ProcessStarted;
import org.jgrapes.io.events.StartProcess;
import org.jgrapes.io.process.ProcessManager;
import org.jgrapes.io.process.ProcessManager.ProcessChannel;
import org.jgrapes.io.util.ByteBufferWriter;
import org.jgrapes.io.util.LineCollector;
import org.jgrapes.net.SocketConnector;
import org.jgrapes.net.SocketIOChannel;
import org.jgrapes.net.events.ClientConnected;
import org.jgrapes.util.FileSystemWatcher;
import org.jgrapes.util.YamlConfigurationStore;
import org.jgrapes.util.events.ConfigurationUpdate;
import org.jgrapes.util.events.FileChanged;
import org.jgrapes.util.events.FileChanged.Kind;
import org.jgrapes.util.events.WatchFile;

/**
 * The Runner.
 * 
 * @startuml
 * [*] --> Setup
 * Setup --> Setup: InitialConfiguration/configure Runner
 * 
 * state Startup {
 * 
 *     state which <<choice>>
 *     state "Start swtpm" as swtpm
 *     state "Start qemu" as qemu
 *     state "Open monitor" as monitor
 *     state success <<exitPoint>>
 *     state error <<exitPoint>>
 *      
 *     which --> swtpm: [use swtpm]
 *     which --> qemu: [else]
 * 
 *     swtpm: entry/start swtpm
 *     swtpm --> error: StartProcessError/stop
 *     swtpm -> qemu: FileChanged[swtpm socket created]
 * 
 *     qemu: entry/start qemu
 *     qemu --> error: StartProcessError/stop
 *     qemu --> monitor : FileChanged[monitor socket created] 
 * 
 *     monitor: entry/fire OpenSocketConnection
 *     monitor --> success: ClientConnected[for monitor]
 *     monitor --> error: ConnectError[for monitor]
 * }
 * 
 * Setup --> which: Start
 * 
 * success --> Run
 * error --> [*]
 *
 * @enduml
 * 
 * If the log level for `org.jdrupes.vmoperator.runner.qemu.monitor`
 * is set to fine, the messages exchanged on the monitor socket are logged.
 */
@SuppressWarnings("PMD.ExcessiveImports")
public class Runner extends Component {

    private static final String TEMPLATE_DIR = "/usr/share/vmrunner/templates";
    private static final String DEFAULT_TEMPLATE
        = "Standard-VM-latest.ftl.yaml";
    private static final String SAVED_TEMPLATE = "VM.ftl.yaml";
    private static final String FW_FLASH = "fw-flash.fd";

    @SuppressWarnings({ "PMD.FieldNamingConventions",
        "PMD.VariableNamingConventions" })
    private static final Logger monitorLog
        = Logger.getLogger(Runner.class.getPackageName() + ".monitor");

    private static Runner app;

    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    private final JsonNode defaults;
    @SuppressWarnings("PMD.UseConcurrentHashMap")
    private Configuration config = new Configuration();
    private final freemarker.template.Configuration fmConfig;

    /**
     * Instantiates a new runner.
     *
     * @throws IOException Signals that an I/O exception has occurred.
     */
    public Runner() throws IOException {
        super(new Context());
        // Get defaults
        defaults = mapper.readValue(
            Runner.class.getResourceAsStream("defaults.yaml"), JsonNode.class);

        // Configure freemarker library
        fmConfig = new freemarker.template.Configuration(
            freemarker.template.Configuration.VERSION_2_3_32);
        fmConfig.setDirectoryForTemplateLoading(new File("/"));
        fmConfig.setDefaultEncoding("utf-8");
        fmConfig.setObjectWrapper(new ExtendedObjectWrapper(
            fmConfig.getIncompatibleImprovements(), mapper));
        fmConfig.setTemplateExceptionHandler(
            TemplateExceptionHandler.RETHROW_HANDLER);
        fmConfig.setLogTemplateExceptions(false);

        // Prepare component tree
        attach(new NioDispatcher());
        attach(new FileSystemWatcher(channel()));
        attach(new ProcessManager(channel()));
        attach(new SocketConnector(channel()));

        // Configuration store with file in /etc (default)
        File config = new File(System.getProperty(
            getClass().getPackageName().toString() + ".config",
            "/etc/vmrunner/config.yaml"));
        attach(new YamlConfigurationStore(channel(), config, false));
        fire(new WatchFile(config.toPath()));
    }

    /**
     * On configuration update.
     *
     * @param event the event
     */
    @Handler
    public void onConfigurationUpdate(ConfigurationUpdate event) {
        event.structured(componentPath()).ifPresent(c -> {
            try {
                config = mapper.convertValue(c, Configuration.class);
            } catch (IllegalArgumentException e) {
                logger.log(Level.SEVERE, e, () -> "Invalid configuration: "
                    + e.getMessage());
                // Don't use default configuration
                config = null;
            }
        });
    }

    /**
     * Handle the start event.
     *
     * @param event the event
     */
    @Handler
    @SuppressWarnings({ "PMD.SystemPrintln" })
    public void onStart(Start event) {
        try {
            if (config == null || !config.check()) {
                // Invalid configuration, fail
                fire(new Stop());
                return;
            }

            // Prepare firmware files and add to config
            setFirmwarePaths();

            // Obtain more data from template
            var tplData = dataFromTemplate();

            // Get process definitions etc. from processed data
            Context context = (Context) channel();
            context.swtpmDefinition = Optional.ofNullable(tplData.get("swtpm"))
                .map(d -> new CommandDefinition("swtpm", d)).orElse(null);
            context.qemuDefinition = Optional.ofNullable(tplData.get("qemu"))
                .map(d -> new CommandDefinition("qemu", d)).orElse(null);
            config.monitorMessages = tplData.get("monitorMessages");

            // Files to watch for
            Files.deleteIfExists(config.swtpmSocket);
            fire(new WatchFile(config.swtpmSocket));
            Files.deleteIfExists(config.monitorSocket);
            fire(new WatchFile(config.monitorSocket));

            // Start first
            if (config.vm.useTpm && context.swtpmDefinition != null) {
                startProcess(context, context.swtpmDefinition);
                return;
            }
            startProcess(context, context.qemuDefinition);
        } catch (IOException | TemplateException e) {
            logger.log(Level.SEVERE, e,
                () -> "Cannot configure runner: " + e.getMessage());
            fire(new Stop());
        }
    }

    private void setFirmwarePaths() throws IOException {
        // Get file for firmware ROM
        JsonNode codePaths = defaults.path("firmware").path("rom");
        for (var paths = codePaths.elements(); paths.hasNext();) {
            var path = Path.of(paths.next().asText());
            if (Files.exists(path)) {
                config.firmwareRom = path;
                break;
            }
        }
        // Get file for firmware flash, if necessary
        config.firmwareFlash = Path.of(config.dataDir, FW_FLASH);
        if (!Files.exists(config.firmwareFlash)) {
            JsonNode srcPaths = null;
            if (BOOT_MODE_UEFI.equals(config.vm.bootMode)) {
                srcPaths = defaults.path("firmware").path("flash");
            } else if (BOOT_MODE_SECURE.equals(config.vm.bootMode)) {
                srcPaths = defaults.path("firmware")
                    .path("secure").path("flash");
            }
            // If UEFI boot, srcPaths != null
            if (srcPaths != null) {
                for (var paths = srcPaths.elements(); paths.hasNext();) {
                    var path = Path.of(paths.next().asText());
                    if (Files.exists(path)) {
                        Files.copy(path, config.firmwareFlash);
                        break;
                    }
                }
            }
        }
    }

    private JsonNode dataFromTemplate()
            throws IOException, TemplateNotFoundException,
            MalformedTemplateNameException, ParseException, TemplateException,
            JsonProcessingException, JsonMappingException {
        // Try saved template, copy if not there (or to be updated)
        Path templatePath = Path.of(config.dataDir, SAVED_TEMPLATE);
        if (!Files.isReadable(templatePath) || config.updateTemplate) {
            // Get template
            Path sourcePath = Paths.get(TEMPLATE_DIR).resolve(Optional
                .ofNullable(config.template).orElse(DEFAULT_TEMPLATE));
            Files.deleteIfExists(templatePath);
            Files.copy(sourcePath, templatePath);
        }

        // Configure data model
        var model = new HashMap<String, Object>();
        model.put("runtimeDir", config.runtimeDir);
        model.put("firmwareRom", config.firmwareRom.toString());
        model.put("firmwareFlash", config.firmwareFlash.toString());
        model.put("vm", config.vm);

        // Combine template and data and parse result
        // (tempting, but no need to use a pipe here)
        var fmTemplate = fmConfig.getTemplate(templatePath.toString());
        StringWriter out = new StringWriter();
        fmTemplate.process(model, out);
        return mapper.readValue(out.toString(), JsonNode.class);
    }

    private boolean startProcess(Context context, CommandDefinition toStart) {
        logger.fine(
            () -> "Starting process: " + String.join(" ", toStart.command));
        fire(new StartProcess(toStart.command)
            .setAssociated(Context.class, context)
            .setAssociated(CommandDefinition.class, toStart), channel());
        return true;
    }

    /**
     * Watch for the creation of the swtpm socket and start the
     * qemu process if it has been created.
     *
     * @param event the event
     * @param context the context
     */
    @Handler
    public void onFileChanged(FileChanged event, Context context) {
        if (event.change() == Kind.CREATED
            && event.path()
                .equals(Path.of(config.runtimeDir, "swtpm-sock"))) {
            // swtpm running, start qemu
            startProcess(context, context.qemuDefinition);
            return;
        }
        var monSockPath = Path.of(config.runtimeDir, "monitor.sock");
        if (event.change() == Kind.CREATED
            && event.path().equals(monSockPath)) {
            // qemu running, open socket
            fire(new OpenSocketConnection(
                UnixDomainSocketAddress.of(monSockPath))
                    .setAssociated(Context.class, context));
        }
    }

    /**
     * Associate required data with the process channel and register the
     * channel in the context.
     *
     * @param event the event
     * @param channel the channel
     * @throws InterruptedException the interrupted exception
     */
    @Handler
    @SuppressWarnings({ "PMD.SwitchStmtsShouldHaveDefault",
        "PMD.TooFewBranchesForASwitchStatement" })
    public void onProcessStarted(ProcessStarted event, ProcessChannel channel)
            throws InterruptedException {
        event.startEvent().associated(Context.class).ifPresent(context -> {
            // Associate the process channel with the general context
            // and with its process definition (both carried over by
            // the start event).
            channel.setAssociated(Context.class, context);
            CommandDefinition procDef
                = event.startEvent().associated(CommandDefinition.class).get();
            channel.setAssociated(CommandDefinition.class, procDef);

            // Associate the channel with a line collector (one for
            // each stream) for logging the process's output.
            TypedIdKey.associate(channel, 1, new LineCollector().nativeCharset()
                .consumer(line -> logger
                    .info(() -> procDef.name() + "(out): " + line)));
            TypedIdKey.associate(channel, 2, new LineCollector().nativeCharset()
                .consumer(line -> logger
                    .info(() -> procDef.name() + "(err): " + line)));

            // Register the channel in the context.
            switch (procDef.name) {
            case "swtpm":
                context.swtpmChannel = channel;
                break;
            case "qemu":
                context.qemuChannel = channel;
                break;
            }
        });
    }

    /**
     * Forward output from the processes to to the log.
     *
     * @param event the event
     * @param channel the channel
     */
    @Handler
    public void onInput(Input<?> event, ProcessChannel channel) {
        event.associated(FileDescriptor.class, Integer.class).ifPresent(
            fd -> TypedIdKey.associated(channel, LineCollector.class, fd)
                .ifPresent(lc -> lc.feed(event)));
    }

    /**
     * Handle data from qemu monitor connection.
     *
     * @param event the event
     * @param channel the channel
     */
    @Handler
    public void onInput(Input<?> event, SocketIOChannel channel) {
        channel.associated(LineCollector.class).ifPresent(collector -> {
            collector.feed(event);
        });
    }

    /**
     * On process exited.
     *
     * @param event the event
     * @param channel the channel
     */
    @Handler
    public void onProcessExited(ProcessExited event, ProcessChannel channel) {
        int i = 0;
    }

    /**
     * Check if this is from opening the monitor socket and if true,
     * save the socket in the context and associate the channel with
     * the context. Then send the initial message to the socket.
     *
     * @param event the event
     * @param channel the channel
     */
    @Handler
    public void onClientConnected(ClientConnected event,
            SocketIOChannel channel) {
        if (event.openEvent().address() instanceof UnixDomainSocketAddress addr
            && addr.getPath()
                .equals(Path.of(config.runtimeDir, "monitor.sock"))) {
            event.openEvent().associated(Context.class).ifPresent(context -> {
                context.monitorChannel = channel;
                channel.setAssociated(Context.class, context);
                channel.setAssociated(LineCollector.class,
                    new LineCollector().consumer(line -> {
                        monitorLog.fine(() -> "monitor(in): " + line);
                    }));
                channel.setAssociated(Writer.class, new ByteBufferWriter(
                    channel).nativeCharset());
                writeToMonitor(context,
                    config.monitorMessages.get("connect").asText());
            });
        }
    }

    @Handler
    public void onConnectError(ConnectError event, SocketIOChannel channel) {
        if (event.event() instanceof OpenSocketConnection openEvent
            && openEvent.address() instanceof UnixDomainSocketAddress addr
            && addr.getPath()
                .equals(Path.of(config.runtimeDir, "monitor.sock"))) {
            openEvent.associated(Context.class).ifPresent(context -> {
                fire(new Stop());
            });
        }
    }

    private void writeToMonitor(Context context, String message) {
        monitorLog.fine(() -> "monitor(out): " + message);
        context.monitorChannel.associated(Writer.class)
            .ifPresent(writer -> {
                try {
                    writer.append(message).append('\n').flush();
                } catch (IOException e) {
                    // Cannot happen, but...
                    logger.log(Level.WARNING, e, () -> e.getMessage());
                }
            });
    }

    /**
     * The context.
     */
    private static class Context implements Channel {
        public CommandDefinition swtpmDefinition;
        public CommandDefinition qemuDefinition;
        public ProcessChannel swtpmChannel;
        public ProcessChannel qemuChannel;
        public SocketIOChannel monitorChannel;

        @Override
        public Object defaultCriterion() {
            return "ProcMgr";
        }

        @Override
        public String toString() {
            return "ProcMgr";
        }
    }

    /**
     * The main method.
     *
     * @param args the command
     */
    public static void main(String[] args) {
        // The Runner is the root component
        try {
            app = new Runner();

            // Prepare Stop
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    app.fire(new Stop(), Channel.BROADCAST);
                    Components.awaitExhaustion();
                } catch (InterruptedException e) {
                    // Cannot do anything about this.
                }
            }));

            // Start the application
            Components.start(app);
        } catch (IOException | InterruptedException e) {
            Logger.getLogger(Runner.class.getName()).log(Level.SEVERE, e,
                () -> "Failed to start runner: " + e.getMessage());
        }

    }
}
