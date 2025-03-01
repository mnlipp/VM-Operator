/*
 * VM-Operator
 * Copyright (C) 2023,2024 Michael N. Lipp
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
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
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
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import static org.jdrupes.vmoperator.common.Constants.APP_NAME;
import static org.jdrupes.vmoperator.common.Constants.DATA_DISPLAY_PASSWORD;
import org.jdrupes.vmoperator.runner.qemu.commands.QmpCont;
import org.jdrupes.vmoperator.runner.qemu.commands.QmpReset;
import org.jdrupes.vmoperator.runner.qemu.events.ConfigureQemu;
import org.jdrupes.vmoperator.runner.qemu.events.Exit;
import org.jdrupes.vmoperator.runner.qemu.events.MonitorCommand;
import org.jdrupes.vmoperator.runner.qemu.events.OsinfoEvent;
import org.jdrupes.vmoperator.runner.qemu.events.QmpConfigured;
import org.jdrupes.vmoperator.runner.qemu.events.RunnerStateChange;
import org.jdrupes.vmoperator.runner.qemu.events.RunnerStateChange.RunState;
import org.jdrupes.vmoperator.util.ExtendedObjectWrapper;
import org.jdrupes.vmoperator.util.FsdUtils;
import org.jgrapes.core.Channel;
import org.jgrapes.core.Component;
import org.jgrapes.core.Components;
import org.jgrapes.core.EventPipeline;
import org.jgrapes.core.TypedIdKey;
import org.jgrapes.core.annotation.Handler;
import org.jgrapes.core.events.HandlingError;
import org.jgrapes.core.events.Start;
import org.jgrapes.core.events.Started;
import org.jgrapes.core.events.Stop;
import org.jgrapes.core.internal.EventProcessor;
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
 * The {@link Runner} associates an {@link EventProcessor} with the
 * {@link Start} event. This "runner event processor" must be used
 * for all events related to the application level function. Components
 * that handle events from other sources (and thus event processors)
 * must fire any resulting events on the runner event processor in order
 * to maintain synchronization.
 * 
 * @startuml RunnerStates.svg
 * [*] --> Initializing
 * Initializing -> Initializing: InitialConfiguration/configure Runner
 * Initializing -> Initializing: Start/start Runner
 * 
 * state "Starting (Processes)" as StartingProcess {
 * 
 *     state "Start qemu" as qemu
 *     state "Open monitor" as monitor
 *     state "Configure QMP" as waitForConfigured
 *     state "Configure QEMU" as configure
 *     state success <<exitPoint>>
 *     state error <<exitPoint>>
 *     
 *     state prepFork <<fork>>
 *     state prepJoin <<join>>
 *     state "Generate cloud-init image" as cloudInit
 *     prepFork --> cloudInit: [cloud-init data provided]
 *     swtpm --> prepJoin: FileChanged[swtpm socket created]
 *     state "Start swtpm" as swtpm
 *     prepFork --> swtpm: [use swtpm]
 *     swtpm: entry/start swtpm
 *     cloudInit --> prepJoin: ProcessExited
 *     cloudInit: entry/generate cloud-init image
 *     prepFork --> prepJoin: [else]
 *     
 *     prepJoin --> qemu
 *     
 *     qemu: entry/start qemu
 *     qemu --> monitor : FileChanged[monitor socket created] 
 * 
 *     monitor: entry/fire OpenSocketConnection
 *     monitor --> waitForConfigured: ClientConnected[for monitor]
 *     monitor -> error: ConnectError[for monitor]
 *
 *     waitForConfigured: entry/fire QmpCapabilities
 *     waitForConfigured --> configure: QmpConfigured
 *     
 *     configure: entry/fire ConfigureQemu
 *     configure --> success: ConfigureQemu (last handler)/fire cont command
 * }
 * 
 * Initializing --> prepFork: Started
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
 * state Stopped {
 *     state stopped <<entryPoint>>
 * 
 *     stopped --> [*]
 * }
 * 
 * terminated --> stopped
 *
 * @enduml
 * 
 */
@SuppressWarnings({ "PMD.ExcessiveImports", "PMD.AvoidPrintStackTrace",
    "PMD.DataflowAnomalyAnalysis", "PMD.TooManyMethods",
    "PMD.CouplingBetweenObjects" })
public class Runner extends Component {

    private static final String QEMU = "qemu";
    private static final String SWTPM = "swtpm";
    private static final String CLOUD_INIT_IMG = "cloudInitImg";
    private static final String TEMPLATE_DIR
        = "/opt/" + APP_NAME.replace("-", "") + "/templates";
    private static final String DEFAULT_TEMPLATE
        = "Standard-VM-latest.ftl.yaml";
    private static final String SAVED_TEMPLATE = "VM.ftl.yaml";
    private static final String FW_VARS = "fw-vars.fd";
    private static int exitStatus;

    private final EventPipeline rep = newEventPipeline();
    private final ObjectMapper yamlMapper = new ObjectMapper(YAMLFactory
        .builder().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
        .build());
    private final JsonNode defaults;
    @SuppressWarnings("PMD.UseConcurrentHashMap")
    private final File configFile;
    private final Path configDir;
    private Configuration config = new Configuration();
    private final freemarker.template.Configuration fmConfig;
    private CommandDefinition swtpmDefinition;
    private CommandDefinition cloudInitImgDefinition;
    private CommandDefinition qemuDefinition;
    private final QemuMonitor qemuMonitor;
    private final GuestAgentClient guestAgentClient;
    private final VmopAgentClient vmopAgentClient;
    private Integer resetCounter;
    private RunState state = RunState.INITIALIZING;

    /** Preparatory actions for QEMU start */
    @SuppressWarnings("PMD.FieldNamingConventions")
    private enum QemuPreps {
        Config,
        Tpm,
        CloudInit
    }

    private final Set<QemuPreps> qemuLatch = EnumSet.noneOf(QemuPreps.class);

    /**
     * Instantiates a new runner.
     *
     * @param cmdLine the cmd line
     * @throws IOException Signals that an I/O exception has occurred.
     */
    @SuppressWarnings({ "PMD.SystemPrintln",
        "PMD.ConstructorCallsOverridableMethod" })
    public Runner(CommandLine cmdLine) throws IOException {
        yamlMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
            false);

        // Get defaults
        defaults = yamlMapper.readValue(
            Runner.class.getResourceAsStream("defaults.yaml"), JsonNode.class);

        // Get the config
        configFile = new File(cmdLine.getOptionValue('c',
            "/etc/opt/" + APP_NAME.replace("-", "") + "/config.yaml"));
        // Don't rely on night config to produce a good exception
        // for this simple case
        if (!Files.isReadable(configFile.toPath())) {
            throw new IOException(
                "Cannot read configuration file " + configFile);
        }
        configDir = configFile.getParentFile().toPath().toRealPath();

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
        attach(qemuMonitor = new QemuMonitor(channel(), configDir));
        attach(guestAgentClient = new GuestAgentClient(channel()));
        attach(vmopAgentClient = new VmopAgentClient(channel()));
        attach(new StatusUpdater(channel()));
        attach(new YamlConfigurationStore(channel(), configFile, false));
        fire(new WatchFile(configFile.toPath()));
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
     * On configuration update.
     *
     * @param event the event
     */
    @Handler
    public void onConfigurationUpdate(ConfigurationUpdate event) {
        event.structured(componentPath()).ifPresent(c -> {
            var newConf = yamlMapper.convertValue(c, Configuration.class);

            // Add some values from other sources to configuration
            newConf.asOf = Instant.ofEpochSecond(configFile.lastModified());
            Path dsPath = configDir.resolve(DATA_DISPLAY_PASSWORD);
            newConf.hasDisplayPassword = dsPath.toFile().canRead();

            // Special actions for initial configuration (startup)
            if (event instanceof InitialConfiguration) {
                processInitialConfiguration(newConf);
                return;
            }
            logger.fine(() -> "Updating configuration");
            rep.fire(new ConfigureQemu(newConf, state));
        });
    }

    @SuppressWarnings("PMD.LambdaCanBeMethodReference")
    private void processInitialConfiguration(Configuration newConfig) {
        try {
            config = newConfig;
            if (!config.check()) {
                // Invalid configuration, not used, problems already logged.
                config = null;
            }

            // Prepare firmware files and add to config
            setFirmwarePaths();

            // Obtain more context data from template
            var tplData = dataFromTemplate();
            swtpmDefinition = Optional.ofNullable(tplData.get(SWTPM))
                .map(d -> new CommandDefinition(SWTPM, d)).orElse(null);
            logger.finest(() -> swtpmDefinition.toString());
            qemuDefinition = Optional.ofNullable(tplData.get(QEMU))
                .map(d -> new CommandDefinition(QEMU, d)).orElse(null);
            logger.finest(() -> qemuDefinition.toString());
            cloudInitImgDefinition
                = Optional.ofNullable(tplData.get(CLOUD_INIT_IMG))
                    .map(d -> new CommandDefinition(CLOUD_INIT_IMG, d))
                    .orElse(null);
            logger.finest(() -> cloudInitImgDefinition.toString());

            // Forward some values to child components
            qemuMonitor.configure(config.monitorSocket,
                config.vm.powerdownTimeout);
            configureAgentClient(guestAgentClient, "guest-agent-socket");
            configureAgentClient(vmopAgentClient, "vmop-agent-socket");
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
        if (codePaths.iterator().hasNext() && config.firmwareRom == null) {
            throw new IllegalArgumentException("No ROM found, candidates were: "
                + StreamSupport.stream(codePaths.spliterator(), false)
                    .map(JsonNode::asText).collect(Collectors.joining(", ")));
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
            logger.fine(() -> "Using template " + sourcePath);
        } else {
            logger.fine(() -> "Using saved template.");
        }

        // Configure data model
        var model = new HashMap<String, Object>();
        model.put("dataDir", config.dataDir);
        model.put("runtimeDir", config.runtimeDir);
        model.put("firmwareRom", Optional.ofNullable(config.firmwareRom)
            .map(Object::toString).orElse(null));
        model.put("firmwareVars", Optional.ofNullable(config.firmwareVars)
            .map(Object::toString).orElse(null));
        model.put("hasDisplayPassword", config.hasDisplayPassword);
        model.put("cloudInit", config.cloudInit);
        model.put("vm", config.vm);
        logger.finest(() -> "Processing template with model: " + model);

        // Combine template and data and parse result
        // (tempting, but no need to use a pipe here)
        var fmTemplate = fmConfig.getTemplate(templatePath.toString());
        StringWriter out = new StringWriter();
        fmTemplate.process(model, out);
        logger.finest(() -> "Result of processing template: " + out);
        return yamlMapper.readValue(out.toString(), JsonNode.class);
    }

    /**
     * Handle the start event.
     *
     * @param event the event
     */
    @Handler(priority = 100)
    public void onStart(Start event) {
        if (config == null) {
            // Missing configuration, fail
            event.cancel(true);
            fire(new Stop());
            return;
        }

        // Make sure to use thread specific client
        // https://github.com/kubernetes-client/java/issues/100
        io.kubernetes.client.openapi.Configuration.setDefaultApiClient(null);

        // Provide specific event pipeline to avoid concurrency.
        event.setAssociated(EventPipeline.class, rep);
        try {
            // Store process id
            try (var pidFile = Files.newBufferedWriter(
                config.runtimeDir.resolve("runner.pid"))) {
                pidFile.write(ProcessHandle.current().pid() + "\n");
            }

            // Files to watch for
            Files.deleteIfExists(config.swtpmSocket);
            fire(new WatchFile(config.swtpmSocket));

            // Helper files
            var ticket = Optional.ofNullable(config.vm.display)
                .map(d -> d.spice).map(s -> s.ticket);
            if (ticket.isPresent()) {
                Files.write(config.runtimeDir.resolve("ticket.txt"),
                    ticket.get().getBytes());
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, e,
                () -> "Cannot start runner: " + e.getMessage());
            fire(new Stop());
        }
    }

    @SuppressWarnings("PMD.CognitiveComplexity")
    private void configureAgentClient(AgentConnector client, String chardev) {
        String id = null;
        Path path = null;
        for (var arg : qemuDefinition.command) {
            if (arg.startsWith("virtserialport,")
                && arg.contains("chardev=" + chardev)) {
                for (var prop : arg.split(",")) {
                    if (prop.startsWith("id=")) {
                        id = prop.substring(3);
                    }
                }
            }
            if (arg.startsWith("socket,")
                && arg.contains("id=" + chardev)) {
                for (var prop : arg.split(",")) {
                    if (prop.startsWith("path=")) {
                        path = Path.of(prop.substring(5));
                    }
                }
            }
        }
        if (id == null || path == null) {
            logger.warning(() -> "Definition of chardev " + chardev
                + " missing in runner template.");
            return;
        }
        client.configure(id, path);
    }

    /**
     * Handle the started event.
     *
     * @param event the event
     */
    @Handler
    public void onStarted(Started event) {
        state = RunState.STARTING;
        rep.fire(new RunnerStateChange(state, "RunnerStarted",
            "Runner has been started"));
        // Start first process(es)
        qemuLatch.add(QemuPreps.Config);
        if (config.vm.useTpm && swtpmDefinition != null) {
            startProcess(swtpmDefinition);
            qemuLatch.add(QemuPreps.Tpm);
        }
        if (config.cloudInit != null) {
            generateCloudInitImg();
            qemuLatch.add(QemuPreps.CloudInit);
        }
        mayBeStartQemu(QemuPreps.Config);
    }

    @SuppressWarnings("PMD.AvoidSynchronizedStatement")
    private void mayBeStartQemu(QemuPreps done) {
        synchronized (qemuLatch) {
            if (qemuLatch.isEmpty()) {
                return;
            }
            qemuLatch.remove(done);
            if (qemuLatch.isEmpty()) {
                startProcess(qemuDefinition);
            }
        }
    }

    private void generateCloudInitImg() {
        try {
            var cloudInitDir = config.dataDir.resolve("cloud-init");
            cloudInitDir.toFile().mkdir();
            try (var metaOut
                = Files.newBufferedWriter(cloudInitDir.resolve("meta-data"))) {
                if (config.cloudInit.metaData != null) {
                    yamlMapper.writer().writeValue(metaOut,
                        config.cloudInit.metaData);
                }
            }
            try (var userOut
                = Files.newBufferedWriter(cloudInitDir.resolve("user-data"))) {
                userOut.write("#cloud-config\n");
                if (config.cloudInit.userData != null) {
                    yamlMapper.writer().writeValue(userOut,
                        config.cloudInit.userData);
                }
            }
            if (config.cloudInit.networkConfig != null) {
                try (var networkConfig = Files.newBufferedWriter(
                    cloudInitDir.resolve("network-config"))) {
                    yamlMapper.writer().writeValue(networkConfig,
                        config.cloudInit.networkConfig);
                }
            }
            startProcess(cloudInitImgDefinition);
        } catch (IOException e) {
            logger.log(Level.SEVERE, e,
                () -> "Cannot start runner: " + e.getMessage());
            fire(new Stop());
        }
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
            // swtpm running, maybe start qemu
            mayBeStartQemu(QemuPreps.Tpm);
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
     * When the monitor is ready, send QEMU its initial configuration.  
     * 
     * @param event the event
     */
    @Handler
    public void onQmpConfigured(QmpConfigured event) {
        rep.fire(new ConfigureQemu(config, state));
    }

    /**
     * Whenever a new QEMU configuration is available, check if it
     * is supposed to trigger a reset.
     *
     * @param event the event
     */
    @Handler
    public void onConfigureQemu(ConfigureQemu event) {
        if (state.vmActive()) {
            if (resetCounter != null
                && event.configuration().resetCounter != null
                && event.configuration().resetCounter > resetCounter) {
                fire(new MonitorCommand(new QmpReset()));
            }
            resetCounter = event.configuration().resetCounter;
        }
    }

    /**
     * As last step when handling a new configuration, check if
     * QEMU is suspended after startup and should be continued. 
     * 
     * @param event the event
     */
    @Handler(priority = -1000)
    public void onConfigureQemuFinal(ConfigureQemu event) {
        if (state == RunState.STARTING) {
            state = RunState.BOOTING;
            fire(new MonitorCommand(new QmpCont()));
            rep.fire(new RunnerStateChange(state, "VmStarted",
                "Qemu has been configured and is continuing"));
        }
    }

    /**
     * Receiving the OSinfo means that the OS has been booted.
     *
     * @param event the event
     */
    @Handler
    public void onOsinfo(OsinfoEvent event) {
        if (state == RunState.BOOTING) {
            state = RunState.BOOTED;
            rep.fire(new RunnerStateChange(state, "VmBooted",
                "The VM has started the guest agent."));
        }
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
            if (procDef.equals(cloudInitImgDefinition)
                && event.exitValue() == 0) {
                // Cloud-init ISO generation was successful.
                mayBeStartQemu(QemuPreps.CloudInit);
                return;
            }

            // No other process(es) may exit during startup
            if (state == RunState.STARTING) {
                logger.severe(() -> "Process " + procDef.name
                    + " has exited with value " + event.exitValue()
                    + " during startup.");
                rep.fire(new Stop());
                return;
            }

            // No processes may exit while the VM is running normally
            if (procDef.equals(qemuDefinition) && state.vmActive()) {
                rep.fire(new Exit(event.exitValue()));
            }
            logger.info(() -> "Process " + procDef.name
                + " has exited with value " + event.exitValue());
        });
    }

    /**
     * On exit.
     *
     * @param event the event
     */
    @Handler(priority = 10_001)
    public void onExit(Exit event) {
        if (exitStatus == 0) {
            exitStatus = event.exitStatus();
        }
    }

    /**
     * On stop.
     *
     * @param event the event
     */
    @Handler(priority = 10_000)
    public void onStopFirst(Stop event) {
        state = RunState.TERMINATING;
        rep.fire(new RunnerStateChange(state, "VmTerminating",
            "The VM is being shut down", exitStatus != 0));
    }

    /**
     * On stop.
     *
     * @param event the event
     */
    @Handler(priority = -10_000)
    public void onStopLast(Stop event) {
        state = RunState.STOPPED;
        rep.fire(new RunnerStateChange(state, "VmStopped",
            "The VM has been shut down"));
    }

    @SuppressWarnings("PMD.ConfusingArgumentToVarargsMethod")
    private void shutdown() {
        if (!Set.of(RunState.TERMINATING, RunState.STOPPED).contains(state)) {
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
            var path = FsdUtils.findConfigFile(APP_NAME.replace("-", ""),
                "logging.properties");
            if (path.isPresent()) {
                props = Files.newInputStream(path.get());
            } else {
                props = Runner.class.getResourceAsStream("logging.properties");
            }
            LogManager.getLogManager().readConfiguration(props);
            Logger.getLogger(Runner.class.getName()).log(Level.CONFIG,
                () -> path.isPresent()
                    ? "Using logging configuration from " + path.get()
                    : "Using default logging configuration");
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
            var logger = Logger.getLogger(Runner.class.getName());
            logger.fine(() -> "Version: "
                + Runner.class.getPackage().getImplementationVersion());
            logger.fine(() -> "running on " + System.getProperty("java.vm.name")
                + " (" + System.getProperty("java.vm.version") + ")"
                + " from " + System.getProperty("java.vm.vendor"));
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

            // Wait for (regular) termination
            Components.awaitExhaustion();
            System.exit(exitStatus);

        } catch (IOException | InterruptedException
                | org.apache.commons.cli.ParseException e) {
            Logger.getLogger(Runner.class.getName()).log(Level.SEVERE, e,
                () -> "Failed to start runner: " + e.getMessage());
        }
    }
}
