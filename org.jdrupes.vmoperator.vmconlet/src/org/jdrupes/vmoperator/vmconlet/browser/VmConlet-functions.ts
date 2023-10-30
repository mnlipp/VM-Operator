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
    reactive, ref, Ref, createApp, computed, onMounted, watch, nextTick
} from "vue";
import JGConsole from "jgconsole";
import JgwcPlugin, { JGWC } from "jgwc";
import l10nBundles from "l10nBundles";
import TimeSeries from "./TimeSeries";
import { formatMemory, parseMemory } from "./MemorySize";
import CpuRamChart from "./CpuRamChart";
import ConditionlInputController from "./ConditionalInputController";

import "./VmConlet-style.scss";

// For global access
declare global {
    interface Window {
        orgJDrupesVmOperatorVmConlet: any;
    }
}

window.orgJDrupesVmOperatorVmConlet = {};

let vmInfos = reactive(new Map());
let vmSummary = reactive({
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
let chartData = new TimeSeries(2);
let chartDateUpdate = ref<Date>(null);

window.orgJDrupesVmOperatorVmConlet.initPreview = (previewDom: HTMLElement,
    _isUpdate: boolean) => {
    const app = createApp({
        setup(_props: any) {
            const conletId: string
                = (<HTMLElement>previewDom.parentNode!).dataset["conletId"]!;

            let chart: CpuRamChart | null = null;
            onMounted(() => {
                let canvas: HTMLCanvasElement
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
                let hours = (period.value === "day") ? 24 : 1;
                chart?.setPeriod(hours * 3600 * 1000);
            });

            return { localize, formatMemory, vmSummary, period };
        }
    });
    app.use(JgwcPlugin, []);
    app.config.globalProperties.window = window;
    app.mount(previewDom);
};

window.orgJDrupesVmOperatorVmConlet.initView = (viewDom: HTMLElement,
    _isUpdate: boolean) => {
    const app = createApp({
        setup(_props: any) {
            const conletId: string
                = (<HTMLElement>viewDom.parentNode!).dataset["conletId"]!;

            const controller = reactive(new JGConsole.TableController([
                ["name", "vmname"],
                ["running", "running"],
                ["runningConditionSince", "since"],
                ["currentCpus", "currentCpus"],
                ["currentRam", "currentRam"],
                ["nodeName", "nodeName"]
            ], {
                sortKey: "name",
                sortOrder: "up"
            }));

            let filteredData = computed(() => {
                let infos = Array.from(vmInfos.values());
                return controller.filter(infos);
            });

            const vmAction = (vmName: string, action: string) => {
                JGConsole.notifyConletModel(conletId, action, vmName);
            };

            const idScope = JGWC.createIdScope();
            const detailsByName = reactive(new Set());
            
            const submitCallback = (selected: string, value: any) => {
                if (value === null) {
                    return localize("Illegal format");
                }
                let vmName = selected.substring(0, selected.lastIndexOf(":"));
                let property = selected.substring(selected.lastIndexOf(":") + 1);
                var vmDef = vmInfos.get(vmName);
                let maxValue = vmDef.spec.vm["maximum" 
                    + property.substring(0, 1).toUpperCase() + property.substring(1)];
                if (value > maxValue) {
                    return localize("Value is above maximum");
                }
                JGConsole.notifyConletModel(conletId, property, vmName, value);
                return null;
            }

            const cicInput = ref(null);
            const cic = reactive
                (new ConditionlInputController(cicInput, submitCallback));

            return {
                controller, vmInfos, filteredData, detailsByName, localize, 
                shortDateTime, formatMemory, vmAction, cicInput, cic, 
                parseMemory,
                scopedId: (id: string) => { return idScope.scopedId(id); }
            };
        }
    });
    app.use(JgwcPlugin);
    app.config.globalProperties.window = window;
    app.mount(viewDom);
};

JGConsole.registerConletFunction("org.jdrupes.vmoperator.vmconlet.VmConlet",
    "updateVm", function(_conletId: String, vmDefinition: any) {
        // Add some short-cuts for table controller
        vmDefinition.name = vmDefinition.metadata.name;
        vmDefinition.currentCpus = vmDefinition.status.cpus;
        vmDefinition.currentRam = Number(vmDefinition.status.ram);
        for (let condition of vmDefinition.status.conditions) {
            if (condition.type === "Running") {
                vmDefinition.running = condition.status === "True";
                vmDefinition.runningConditionSince 
                    = new Date(condition.lastTransitionTime);
                break;
            }
        }
        vmInfos.set(vmDefinition.name, vmDefinition);
    });

JGConsole.registerConletFunction("org.jdrupes.vmoperator.vmconlet.VmConlet",
    "removeVm", function(_conletId: String, vmName: String) {
        vmInfos.delete(vmName);
    });

JGConsole.registerConletFunction("org.jdrupes.vmoperator.vmconlet.VmConlet",
    "summarySeries", function(_conletId: String, series: any[]) {
        chartData.clear();
        for (let entry of series) {
            chartData.push(new Date(entry.time.epochSecond * 1000
                + entry.time.nano / 1000000),
                entry.values[0], entry.values[1]);
        }
        chartDateUpdate.value = new Date();
});

JGConsole.registerConletFunction("org.jdrupes.vmoperator.vmconlet.VmConlet",
    "updateSummary", function(_conletId: String, summary: any) {
        chartData.push(new Date(), summary.usedCpus, Number(summary.usedRam));
        chartDateUpdate.value = new Date();
        Object.assign(vmSummary, summary);
});

