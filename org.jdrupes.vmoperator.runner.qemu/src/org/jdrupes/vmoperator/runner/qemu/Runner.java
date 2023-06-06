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
import java.io.StringWriter;
import java.lang.reflect.UndeclaredThrowableException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import static org.jdrupes.vmoperator.runner.qemu.Configuration.BOOT_MODE_SECURE;
import static org.jdrupes.vmoperator.runner.qemu.Configuration.BOOT_MODE_UEFI;
import org.jdrupes.vmoperator.runner.qemu.StateController.State;
import org.jdrupes.vmoperator.util.ExtendedObjectWrapper;
import org.jgrapes.core.Channel;
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
 *     monitor --> success: ClientConnected[for monitor]
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
 *     qemuPowerdown --> terminateProcesses: Closed[for monitor]/resume Stop
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
@SuppressWarnings("PMD.ExcessiveImports")
public class Runner extends Component {

    private static final String TEMPLATE_DIR = "/usr/share/vmrunner/templates";
    private static final String DEFAULT_TEMPLATE
        = "Standard-VM-latest.ftl.yaml";
    private static final String SAVED_TEMPLATE = "VM.ftl.yaml";
    private static final String FW_FLASH = "fw-flash.fd";

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
     *
     * @throws IOException Signals that an I/O exception has occurred.
     */
    public Runner() throws IOException {
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

        // Configuration store with file in /etc (default)
        File config = new File(System.getProperty(
            getClass().getPackageName().toString() + ".config",
            "/etc/vmrunner/config.yaml"));
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
        // Get file for firmware ROM
        JsonNode codePaths = defaults.path("firmware").path("rom");
        for (var p : codePaths) {
            var path = Path.of(p.asText());
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
                for (var p : srcPaths) {
                    var path = Path.of(p.asText());
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
                Path.of(config.runtimeDir, "runner.pid"))) {
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
                    Path.of(config.runtimeDir, procDef.name + ".pid"))) {
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

    /**
     * The main method.
     *
     * @param args the command
     */
    public static void main(String[] args) {
        // The Runner is the root component
        try {
            var app = new Runner();

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
