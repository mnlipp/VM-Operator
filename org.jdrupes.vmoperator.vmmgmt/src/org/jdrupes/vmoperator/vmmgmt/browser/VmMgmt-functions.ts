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

import {
    reactive, ref, Ref, createApp, computed, onMounted, watch
} from "vue";
import JGConsole from "jgconsole";
import JgwcPlugin, { JGWC } from "jgwc";
import l10nBundles from "l10nBundles";
import TimeSeries from "./TimeSeries";
import { formatMemory, parseMemory } from "./MemorySize";
import CpuRamChart from "./CpuRamChart";
import ConditionlInputController from "./ConditionalInputController";

import "./VmMgmt-style.scss";

// For global access
declare global {
    interface Window {
        orgJDrupesVmOperatorVmMgmt: { 
            initPreview?: (previewDom: HTMLElement, isUpdate: boolean) => void,
            initView?: (viewDom: HTMLElement, isUpdate: boolean) => void
        }
    }
}

window.orgJDrupesVmOperatorVmMgmt = {};

const vmInfos = reactive(new Map());
const vmSummary = reactive({
    totalVms: 0,
    runningVms: 0,
    usedCpus: 0,
    usedRam: ""
});

const localize = (key: string) => {
    return JGConsole.localize(
        l10nBundles, JGWC.lang(), key);
};

const shortDateTime = (time: Date) => {
    // https://stackoverflow.com/questions/63958875/why-do-i-get-rangeerror-date-value-is-not-finite-in-datetimeformat-format-w
    return new Intl.DateTimeFormat(JGWC.lang(),
        { dateStyle: "short", timeStyle: "short" }).format(new Date(time));
};

// Cannot be reactive, leads to infinite recursion.
const chartData = new TimeSeries(2);
const chartDateUpdate = ref<Date>(null);

window.orgJDrupesVmOperatorVmMgmt.initPreview = (previewDom: HTMLElement,
    _isUpdate: boolean) => {
    const app = createApp({
        setup(_props: object) {
            let chart: CpuRamChart | null = null;
            onMounted(() => {
                const canvas: HTMLCanvasElement
                    = previewDom.querySelector(":scope .vmsChart")!;
                chart = new CpuRamChart(canvas, chartData);
            })

            watch(chartDateUpdate, (_) => {
                chart?.update();
            })

            watch(JGWC.langRef(), (_) => {
                chart?.localizeChart();
            })

            const period: Ref<string> = ref<string>("day");
            
            watch(period, (_) => { 
                const hours = (period.value === "day") ? 24 : 1;
                chart?.setPeriod(hours * 3600 * 1000);
            });

            return { localize, formatMemory, vmSummary, period };
        }
    });
    app.use(JgwcPlugin, []);
    app.config.globalProperties.window = window;
    app.mount(previewDom);
};

window.orgJDrupesVmOperatorVmMgmt.initView = (viewDom: HTMLElement,
    _isUpdate: boolean) => {
    const app = createApp({
        setup(_props: object) {
            const conletId: string
                = (<HTMLElement>viewDom.parentNode!).dataset["conletId"]!;

            const controller = reactive(new JGConsole.TableController([
                ["name", "vmname"],
                ["running", "running"],
                ["runningConditionSince", "since"],
                ["currentCpus", "currentCpus"],
                ["currentRam", "currentRam"],
                ["nodeName", "nodeName"],
                ["assignedTo", "assignedTo"],
                ["usedBy", "usedBy"]
            ], {
                sortKey: "name",
                sortOrder: "up"
            }));

            const filteredData = computed(() => {
                const infos = Array.from(vmInfos.values());
                return controller.filter(infos);
            });

            const vmAction = (vmName: string, action: string) => {
                JGConsole.notifyConletModel(conletId, action, vmName);
            };

            const idScope = JGWC.createIdScope();
            const detailsByName = reactive(new Set());
            
            const submitCallback = (selected: string, value: number | null) => {
                if (value === null) {
                    return localize("Illegal format");
                }
                const vmName = selected.substring(0, selected.lastIndexOf(":"));
                const property = selected.substring(selected.lastIndexOf(":") + 1);
                const vmDef = vmInfos.get(vmName);
                const maxValue = vmDef.spec.vm["maximum" 
                    + property.substring(0, 1).toUpperCase() + property.substring(1)];
                if (value > maxValue) {
                    return localize("Value is above maximum");
                }
                JGConsole.notifyConletModel(conletId, property, vmName, value);
                return null;
            }

            const cic = new ConditionlInputController(submitCallback);

            // eslint-disable-next-line @typescript-eslint/no-explicit-any
            const maximumCpus = (vmDef: any) => {
                if (vmDef.spec.vm["maximumCpus"]) {
                    return vmDef.spec.vm.maximumCpus;
                }
                const topo = vmDef.spec.vm.cpuTopology;
                return Math.max(1, topo.coresPerDie)
                    * Math.max(1, topo.diesPerSocket)
                    * Math.max(1, topo.sockets)
                    * Math.max(1, topo.threadsPerCore);
            }

            return {
                controller, vmInfos, filteredData, detailsByName, localize, 
                shortDateTime, formatMemory, vmAction, cic, parseMemory,
                maximumCpus,
                scopedId: (id: string) => { return idScope.scopedId(id); }
            };
        }
    });
    app.use(JgwcPlugin);
    app.config.globalProperties.window = window;
    app.mount(viewDom);
};

JGConsole.registerConletFunction("org.jdrupes.vmoperator.vmmgmt.VmMgmt",
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    "updateVm", function(_conletId: string, vmDefinition: any) {
        // Add some short-cuts for table controller
        vmDefinition.name = vmDefinition.metadata.name;
        vmDefinition.currentCpus = vmDefinition.status.cpus;
        vmDefinition.currentRam = Number(vmDefinition.status.ram);
        vmDefinition.usedFrom = vmDefinition.status.consoleClient || "";
        vmDefinition.usedBy = vmDefinition.status.consoleUser || "";
        vmDefinition.assignedTo = vmDefinition.status.assignment?.user || "";
        for (const condition of vmDefinition.status.conditions) {
            if (condition.type === "Running") {
                vmDefinition.running = condition.status === "True";
                vmDefinition.runningConditionSince 
                    = new Date(condition.lastTransitionTime);
                break;
            }
        }
        vmInfos.set(vmDefinition.name, vmDefinition);
    });

JGConsole.registerConletFunction("org.jdrupes.vmoperator.vmmgmt.VmMgmt",
    "removeVm", function(_conletId: string, vmName: string) {
        vmInfos.delete(vmName);
    });

JGConsole.registerConletFunction("org.jdrupes.vmoperator.vmmgmt.VmMgmt",
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    "summarySeries", function(_conletId: string, series: any[]) {
        chartData.clear();
        for (const entry of series) {
            chartData.push(new Date(entry.time * 1000),
                entry.values[0], entry.values[1]);
        }
        chartDateUpdate.value = new Date();
});

JGConsole.registerConletFunction("org.jdrupes.vmoperator.vmmgmt.VmMgmt",
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    "updateSummary", function(_conletId: string, summary: any) {
        chartData.push(new Date(), summary.usedCpus, Number(summary.usedRam));
        chartDateUpdate.value = new Date();
        Object.assign(vmSummary, summary);
});

