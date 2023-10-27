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

import { reactive, ref, createApp, computed, onMounted, onUnmounted,
         watch } from "vue";
import JGConsole from "jgconsole";
import JgwcPlugin, { JGWC } from "jgwc";
import { Chart } from "chartjs";
import l10nBundles from "l10nBundles";

import "./VmConlet-style.scss";

//
// Helpers
//
let unitMap = new Map<string, bigint>();
let unitMappings = new Array<{ key: string; value: bigint }>();
let memorySize = /^\s*(\d+(\.\d+)?)\s*([A-Za-z]*)\s*/;

// SI units and common abbreviations
let factor = BigInt("1");
unitMap.set("", factor);
let scale = BigInt("1000");
for (let unit of ["B", "kB", "MB", "GB", "TB", "PB", "EB"]) {
    unitMap.set(unit, factor);
    factor = factor * scale;
}
// Binary units
factor = BigInt("1024");
scale = BigInt("1024");
for (let unit of ["KiB", "MiB", "GiB", "TiB", "PiB", "EiB"]) {
    unitMap.set(unit, factor);
    factor = factor * scale;
}
unitMap.forEach((value: bigint, key: string) => {
    unitMappings.push({ key, value });
});
unitMappings.sort((a, b) => a.value < b.value ? 1 : a.value > b.value ? -1 : 0);

function formatMemory(size: bigint): string {
    for (let mapping of unitMappings) {
        if (size >= mapping.value
            && (size % mapping.value) === BigInt("0")) {
            return (size / mapping.value + " " + mapping.key).trim();
        }
    }
    return size.toString();
}

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

class TimeSeries {
    timestamps: Date[] = [];
    series: number[][];

    constructor(nbOfSeries: number) {
        this.series = [];
        while (this.series.length < nbOfSeries) {
            this.series.push([]);
        }
    }

    clear() {
        this.timestamps.length = 0;
        for (let values of this.series) {
            values.length = 0;
        }
    }

    push(time: Date, ...values: number[]) {
        let adjust = false;
        if (this.timestamps.length >= 2) {
            adjust = true;
            for (let i = 0; i < values.length; i++) {
                if (values[i] !== this.series[i][this.series[i].length - 1]
                || values[i] !== this.series[i][this.series[i].length - 2]) {
                    adjust = false;
                    break;
                }
            }
        }
        if (adjust) {
            this.timestamps[this.timestamps.length - 1] = time;
        } else {        
            this.timestamps.push(time);
            for (let i = 0; i < values.length; i++) {
                this.series[i].push(values[i]);
            }
        }
        
        // Purge
        let limit = Date.now() - 24 * 3600 * 1000;
        while (this.timestamps.length > 2
            && this.timestamps[0].getTime() < limit
            && this.timestamps[1].getTime() < limit) {
            this.timestamps.shift();
            for (let values of this.series) {
                values.shift();
            }
        }
    }

    getTimes(): Date[] {
        return this.timestamps;
    }

    getSeries(n: number): number[] {
        return this.series[n];
    }
}

// Cannot be reactive, leads to infinite recursion.
let chartData = new TimeSeries(2);
let chartDateUpdate = ref<Date>(null);

class CpuRamChart extends Chart {
    
    period: ref<string>;
    
    constructor(canvas: HTMLCanvasElement, period: ref<string>) {
        super(canvas.getContext('2d')!, {
            // The type of chart we want to create
            type: 'line',

            // The data for our datasets
            data: {
                labels: chartData.getTimes(),
                datasets: [{
                    // See localize
                    data: chartData.getSeries(0),
                    yAxisID: 'cpus'
                }, {
                    // See localize
                    data: chartData.getSeries(1),
                    yAxisID: 'ram'
                }]
            },

            // Configuration options go here
            options: {
                animation: false,
                maintainAspectRatio: false,
                scales: {
                    x: {
                        type: 'time',
                        time: { minUnit: 'minute' },
                        adapters: {
                            date: {
                                // See localize
                            }
                        }
                    },
                    cpus: {
                        type: 'linear',
                        display: true,
                        position: 'left',
                        min: 0
                    },
                    ram: {
                        type: 'linear',
                        display: true,
                        position: 'right',
                        min: 0,
                        grid: { drawOnChartArea: false },
                        ticks: {
                            stepSize: 1024 * 1024 * 1024,
                            callback: function(value, _index, _values) {
                                return formatMemory(BigInt(Math.round(Number(value))));
                            }
                        }
                    }
                }
            }
        });
        
        let css = getComputedStyle(canvas);
        this.setPropValue("options.plugins.legend.labels.font.family", css.fontFamily);
        this.setPropValue("options.plugins.legend.labels.color", css.color);
        this.setPropValue("options.scales.x.ticks.font.family", css.fontFamily);
        this.setPropValue("options.scales.x.ticks.color", css.color);
        this.setPropValue("options.scales.cpus.ticks.font.family", css.fontFamily);
        this.setPropValue("options.scales.cpus.ticks.color", css.color);
        this.setPropValue("options.scales.ram.ticks.font.family", css.fontFamily);
        this.setPropValue("options.scales.ram.ticks.color", css.color);
        this.localize();

        this.period = period;        
        watch(period, (_period) => this.update())
    }

    setPropValue(path: string, value: any) {
        let ptr: any = this;
        let segs = path.split(".");
        let lastSeg = segs.pop()!;
        for (let seg of segs) {
            let cur = ptr[seg];
            if (!cur) {
                ptr[seg] = {};
            }
            // ptr[seg] = ptr[seg] || {}
            ptr = ptr[seg];
        }
        ptr[lastSeg] = value;
    }

    localize() {
        (<any>this.options.scales?.x).adapters.date.locale = JGWC.lang();
        this.data.datasets[0].label = localize("Used CPUs");
        this.data.datasets[1].label = localize("Used RAM");
    }
    
    shift() {
        this.setPropValue("options.scales.x.max", Date.now());
        this.update();
    }
    
    update() {
        let hours = 24;
        // super constructor calls update when period isn't initialized yet
        if (this.period && this.period.value === "hour") {
            hours = 1;
        }
        this.setPropValue("options.scales.x.min",
            Date.now() - hours * 3600 * 1000);
        super.update();
    }
}

window.orgJDrupesVmOperatorVmConlet.initPreview = (previewDom: HTMLElement,
    _isUpdate: boolean) => {
    const app = createApp({
        setup(_props: any) {
            const conletId: string
                = (<HTMLElement>previewDom.parentNode!).dataset["conletId"]!;

            const period = ref<string>("day");

            let chart: CpuRamChart | null = null;
            onMounted(() => {
                let canvas: HTMLCanvasElement
                    = previewDom.querySelector(":scope .vmsChart")!;
                chart = new CpuRamChart(canvas, period);
            })

            watch(chartDateUpdate, (_) => {
                chart?.update();
            })

            watch(JGWC.langRef(), (_) => {
                chart?.localize();
                chart?.update();
            })

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

            const localize = (key: string) => {
                return JGConsole.localize(
                    l10nBundles, JGWC.lang() || "en", key);
            };

            const controller = reactive(new JGConsole.TableController([
                ["name", "vmname"],
                ["running", "running"],
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

            return {
                controller, vmInfos, filteredData, detailsByName,
                localize, formatMemory, vmAction,
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
        vmDefinition.currentRam = BigInt(vmDefinition.status.ram);
        for (let condition of vmDefinition.status.conditions) {
            if (condition.type === "Running") {
                vmDefinition.running = condition.status === "True";
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

