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
import com.fasterxml.jackson.databind.DeserializationFeature;
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
import java.io.InputStream;
import java.io.StringWriter;
import java.lang.reflect.UndeclaredThrowableException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.HashMap;
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
import org.jdrupes.vmoperator.runner.qemu.StateController.State;
import org.jdrupes.vmoperator.util.ExtendedObjectWrapper;
import org.jdrupes.vmoperator.util.FsdUtils;
import org.jgrapes.core.Component;
import org.jgrapes.core.Components;
import org.jgrapes.core.TypedIdKey;
import org.jgrapes.core.annotation.Handler;
import org.jgrapes.core.events.Start;
import org.jgrapes.core.events.Started;
import org.jgrapes.core.events.Stop;
import org.jgrapes.io.NioDispatcher;
import org.jgrapes.io.events.Input;
import org.jgrapes.io.events.ProcessExited;
import org.jgrapes.io.events.ProcessStarted;
import org.jgrapes.io.events.StartProcess;
import org.jgrapes.io.process.ProcessManager;
import org.jgrapes.io.process.ProcessManager.ProcessChannel;
import org.jgrapes.io.util.LineCollector;
import org.jgrapes.net.SocketConnector;
import org.jgrapes.util.FileSystemWatcher;
import org.jgrapes.util.YamlConfigurationStore;
import org.jgrapes.util.events.ConfigurationUpdate;
import org.jgrapes.util.events.FileChanged;
import org.jgrapes.util.events.FileChanged.Kind;
import org.jgrapes.util.events.InitialConfiguration;
import org.jgrapes.util.events.WatchFile;

/**
 * The Runner is responsible for managing the Qemu process and
 * optionally a process that emulates a TPM (software TPM). It's
 * main function is best described by the following state diagram.
 * 
 * ![Runner state diagram](RunnerStates.svg)
 * 
 * @startuml RunnerStates.svg
 * [*] --> Initializing
 * Initializing -> Initializing: InitialConfiguration/configure Runner
 * Initializing -> Initializing: Start/start Runner
 * 
 * state "Starting (Processes)" as StartingProcess {
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
 *     swtpm -> qemu: FileChanged[swtpm socket created]
 * 
 *     qemu: entry/start qemu
 *     qemu --> monitor : FileChanged[monitor socket created] 
 * 
 *     monitor: entry/fire OpenSocketConnection
 *     monitor --> success: ClientConnected[for monitor]/set balloon value
 *     monitor -> error: ConnectError[for monitor]
 * }
 * 
 * Initializing --> which: Started
 * 
 * success --> Running
 * 
 * state Terminating {
 *     state terminate <<entryPoint>>
 *     state qemuRunning <<choice>>
 *     state terminated <<exitPoint>>
 *     state "Powerdown qemu" as qemuPowerdown
 *     state "Await process termination" as terminateProcesses
 * 
 *     terminate --> qemuRunning
 *     qemuRunning --> qemuPowerdown:[qemu monitor open]
 *     qemuRunning --> terminateProcesses:[else]
 * 
 *     qemuPowerdown: entry/suspend Stop, send powerdown to qemu, start timer
 *     
 *     qemuPowerdown --> terminateProcesses: Closed[for monitor]/resume Stop,\ncancel Timer
 *     qemuPowerdown --> terminateProcesses: Timeout/resume Stop
 *     terminateProcesses --> terminated
 * }
 * 
 * Running --> terminate: Stop
 * Running --> terminate: ProcessExited[process qemu]
 * error --> terminate
 * StartingProcess --> terminate: ProcessExited
 * 
 * 
 * terminated --> [*]
 *
 * @enduml
 * 
 */
@SuppressWarnings({ "PMD.ExcessiveImports", "PMD.AvoidPrintStackTrace" })
public class Runner extends Component {

    public static final String APP_NAME = "vmrunner";
    private static final String TEMPLATE_DIR
        = "/opt/" + APP_NAME + "/templates";
    private static final String DEFAULT_TEMPLATE
        = "Standard-VM-latest.ftl.yaml";
    private static final String SAVED_TEMPLATE = "VM.ftl.yaml";
    private static final String FW_VARS = "fw-vars.fd";

    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    private final JsonNode defaults;
    @SuppressWarnings("PMD.UseConcurrentHashMap")
    private Configuration config = new Configuration();
    private final freemarker.template.Configuration fmConfig;
    private final StateController state;
    private CommandDefinition swtpmDefinition;
    private CommandDefinition qemuDefinition;
    private final QemuMonitor qemuMonitor;

    /**
     * Instantiates a new runner.
     * @param cmdLine 
     *
     * @throws IOException Signals that an I/O exception has occurred.
     */
    @SuppressWarnings("PMD.SystemPrintln")
    public Runner(CommandLine cmdLine) throws IOException {
        state = new StateController(this);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
            false);

        // Get defaults
        defaults = mapper.readValue(
            Runner.class.getResourceAsStream("defaults.yaml"), JsonNode.class);

        // Configure freemarker library
        fmConfig = new freemarker.template.Configuration(
            freemarker.template.Configuration.VERSION_2_3_32);
        fmConfig.setDirectoryForTemplateLoading(new File("/"));
        fmConfig.setDefaultEncoding("utf-8");
        fmConfig.setObjectWrapper(new ExtendedObjectWrapper(
            fmConfig.getIncompatibleImprovements()));
        fmConfig.setTemplateExceptionHandler(
            TemplateExceptionHandler.RETHROW_HANDLER);
        fmConfig.setLogTemplateExceptions(false);

        // Prepare component tree
        attach(new NioDispatcher());
        attach(new FileSystemWatcher(channel()));
        attach(new ProcessManager(channel()));
        attach(new SocketConnector(channel()));
        attach(qemuMonitor = new QemuMonitor(channel()));

        // Configuration store with file in /etc/opt (default)
        File config = new File(cmdLine.getOptionValue('c',
            "/etc/opt/" + APP_NAME + "/config.yaml"));
        // Don't rely on night config to produce a good exception
        // for this simple case
        if (!Files.isReadable(config.toPath())) {
            throw new IOException("Cannot read configuration file " + config);
        }
        attach(new YamlConfigurationStore(channel(), config, false));
        fire(new WatchFile(config.toPath()));
    }

    /* default */ ObjectMapper mapper() {
        return mapper;
    }

    /**
     * On configuration update.
     *
     * @param event the event
     */
    @Handler
    public void onConfigurationUpdate(ConfigurationUpdate event) {
        event.structured(componentPath()).ifPresent(c -> {
            if (event instanceof InitialConfiguration) {
                processInitialConfiguration(c);
            }
        });
    }

    private void processInitialConfiguration(
            Map<String, Object> runnerConfiguration) {
        try {
            config = mapper.convertValue(runnerConfiguration,
                Configuration.class);
            if (!config.check()) {
                // Invalid configuration, not used, problems already logged.
                config = null;
            }

            // Prepare firmware files and add to config
            setFirmwarePaths();

            // Obtain more context data from template
            var tplData = dataFromTemplate();
            swtpmDefinition = Optional.ofNullable(tplData.get("swtpm"))
                .map(d -> new CommandDefinition("swtpm", d)).orElse(null);
            qemuDefinition = Optional.ofNullable(tplData.get("qemu"))
                .map(d -> new CommandDefinition("qemu", d)).orElse(null);

            // Forward some values to child components
            qemuMonitor.configure(config.monitorSocket,
                config.vm.powerdownTimeout);
        } catch (IllegalArgumentException | IOException | TemplateException e) {
            logger.log(Level.SEVERE, e, () -> "Invalid configuration: "
                + e.getMessage());
            // Don't use default configuration
            config = null;
        }
    }

    @SuppressWarnings({ "PMD.CognitiveComplexity",
        "PMD.DataflowAnomalyAnalysis" })
    private void setFirmwarePaths() throws IOException {
        JsonNode firmware = defaults.path("firmware").path(config.vm.firmware);
        // Get file for firmware ROM
        JsonNode codePaths = firmware.path("rom");
        for (var p : codePaths) {
            var path = Path.of(p.asText());
            if (Files.exists(path)) {
                config.firmwareRom = path;
                break;
            }
        }
        // Get file for firmware vars, if necessary
        config.firmwareVars = config.dataDir.resolve(FW_VARS);
        if (!Files.exists(config.firmwareVars)) {
            for (var p : firmware.path("vars")) {
                var path = Path.of(p.asText());
                if (Files.exists(path)) {
                    Files.copy(path, config.firmwareVars);
                    break;
                }
            }
        }
    }

    private JsonNode dataFromTemplate()
            throws IOException, TemplateNotFoundException,
            MalformedTemplateNameException, ParseException, TemplateException,
            JsonProcessingException, JsonMappingException {
        // Try saved template, copy if not there (or to be updated)
        Path templatePath = config.dataDir.resolve(SAVED_TEMPLATE);
        if (!Files.isReadable(templatePath) || config.updateTemplate) {
            // Get template
            Path sourcePath = Paths.get(TEMPLATE_DIR).resolve(Optional
                .ofNullable(config.template).orElse(DEFAULT_TEMPLATE));
            Files.deleteIfExists(templatePath);
            Files.copy(sourcePath, templatePath);
        }

        // Configure data model
        var model = new HashMap<String, Object>();
        model.put("dataDir", config.dataDir);
        model.put("runtimeDir", config.runtimeDir);
        model.put("firmwareRom", Optional.ofNullable(config.firmwareRom)
            .map(Object::toString).orElse(null));
        model.put("firmwareVars", Optional.ofNullable(config.firmwareVars)
            .map(Object::toString).orElse(null));
        model.put("vm", config.vm);

        // Combine template and data and parse result
        // (tempting, but no need to use a pipe here)
        var fmTemplate = fmConfig.getTemplate(templatePath.toString());
        StringWriter out = new StringWriter();
        fmTemplate.process(model, out);
        return mapper.readValue(out.toString(), JsonNode.class);
    }

    /**
     * Handle the start event.
     *
     * @param event the event
     */
    @Handler
    public void onStart(Start event) {
        try {
            if (config == null) {
                // Missing configuration, fail
                fire(new Stop());
                return;
            }

            // Store process id
            try (var pidFile = Files.newBufferedWriter(
                config.runtimeDir.resolve("runner.pid"))) {
                pidFile.write(ProcessHandle.current().pid() + "\n");
            }

            // Files to watch for
            Files.deleteIfExists(config.swtpmSocket);
            fire(new WatchFile(config.swtpmSocket));
        } catch (IOException e) {
            logger.log(Level.SEVERE, e,
                () -> "Cannot start runner: " + e.getMessage());
            fire(new Stop());
        }
    }

    /**
     * Handle the started event.
     *
     * @param event the event
     */
    @Handler
    public void onStarted(Started event) {
        state.set(State.STARTING);
        // Start first process
        if (config.vm.useTpm && swtpmDefinition != null) {
            startProcess(swtpmDefinition);
            return;
        }
        startProcess(qemuDefinition);
    }

    private boolean startProcess(CommandDefinition toStart) {
        logger.info(
            () -> "Starting process: " + String.join(" ", toStart.command));
        fire(new StartProcess(toStart.command)
            .setAssociated(CommandDefinition.class, toStart));
        return true;
    }

    /**
     * Watch for the creation of the swtpm socket and start the
     * qemu process if it has been created.
     *
     * @param event the event
     */
    @Handler
    public void onFileChanged(FileChanged event) {
        if (event.change() == Kind.CREATED
            && event.path().equals(config.swtpmSocket)) {
            // swtpm running, start qemu
            startProcess(qemuDefinition);
            return;
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
        event.startEvent().associated(CommandDefinition.class)
            .ifPresent(procDef -> {
                channel.setAssociated(CommandDefinition.class, procDef);
                try (var pidFile = Files.newBufferedWriter(
                    config.runtimeDir.resolve(procDef.name + ".pid"))) {
                    pidFile.write(channel.process().toHandle().pid() + "\n");
                } catch (IOException e) {
                    throw new UndeclaredThrowableException(e);
                }

                // Associate the channel with a line collector (one for
                // each stream) for logging the process's output.
                TypedIdKey.associate(channel, 1,
                    new LineCollector().nativeCharset()
                        .consumer(line -> logger
                            .info(() -> procDef.name() + "(out): " + line)));
                TypedIdKey.associate(channel, 2,
                    new LineCollector().nativeCharset()
                        .consumer(line -> logger
                            .info(() -> procDef.name() + "(err): " + line)));
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
     * On qemu monitor started.
     *
     * @param event the event
     */
    @Handler
    public void onQemuMonitorOpened(QemuMonitorOpened event) {
        Optional.ofNullable(config.vm.currentRam)
            .ifPresent(qemuMonitor::setCurrentRam);
        state.set(State.RUNNING);
    }

    /**
     * On process exited.
     *
     * @param event the event
     * @param channel the channel
     */
    @Handler
    public void onProcessExited(ProcessExited event, ProcessChannel channel) {
        channel.associated(CommandDefinition.class).ifPresent(procDef -> {
            // No process(es) may exit during startup
            if (state.get() == State.STARTING) {
                logger.severe(() -> "Process " + procDef.name
                    + " has exited with value " + event.exitValue()
                    + " during startup.");
                fire(new Stop());
                return;
            }
            if (procDef.equals(qemuDefinition)
                && state.get() == State.RUNNING) {
                fire(new Stop());
            }
            logger.info(() -> "Process " + procDef.name
                + " has exited with value " + event.exitValue());
        });
    }

    /**
     * On stop.
     *
     * @param event the event
     */
    @Handler(priority = 10_000)
    public void onStop(Stop event) {
        state.set(State.TERMINATING);
    }

    private void shutdown() {
        if (state.get() != State.TERMINATING) {
            fire(new Stop());
        }
        try {
            Components.awaitExhaustion();
        } catch (InterruptedException e) {
            logger.log(Level.WARNING, e, () -> "Proper shutdown failed.");
        }

        Optional.ofNullable(config).map(c -> c.runtimeDir)
            .ifPresent(runtimeDir -> {
                try {
                    Files.walk(runtimeDir).sorted(Comparator.reverseOrder())
                        .map(Path::toFile).forEach(File::delete);
                } catch (IOException e) {
                    logger.warning(() -> String.format(
                        "Cannot delete runtime directory \"%s\".",
                        runtimeDir));
                }
            });
    }

    static {
        try {
            InputStream props;
            var path = FsdUtils.findConfigFile(Runner.APP_NAME,
                "logging.properties");
            if (path.isPresent()) {
                props = Files.newInputStream(path.get());
            } else {
                props = Runner.class.getResourceAsStream("logging.properties");
            }
            LogManager.getLogManager().readConfiguration(props);
        } catch (IOException e) {
            e.printStackTrace();
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
            CommandLineParser parser = new DefaultParser();
            // parse the command line arguments
            final Options options = new Options();
            options.addOption(new Option("c", "config", true, "The configu"
                + "ration file (defaults to /etc/opt/vmrunner/config.yaml)."));
            CommandLine cmd = parser.parse(options, args);
            var app = new Runner(cmd);

            // Prepare Stop
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                app.shutdown();
            }));

            // Start the application
            Components.start(app);
        } catch (IOException | InterruptedException
                | org.apache.commons.cli.ParseException e) {
            Logger.getLogger(Runner.class.getName()).log(Level.SEVERE, e,
                () -> "Failed to start runner: " + e.getMessage());
        }
    }
}
