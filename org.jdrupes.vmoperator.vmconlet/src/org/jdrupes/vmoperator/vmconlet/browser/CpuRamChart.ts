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

import { Chart } from "chartjs";
import TimeSeries from "./TimeSeries";
import { formatMemory } from "./MemorySize";
import JGConsole from "jgconsole";
import l10nBundles from "l10nBundles";
import { JGWC } from "jgwc";

export default class CpuRamChart extends Chart {
    
    private period = 24 * 3600 * 1000;
    
    constructor(canvas: HTMLCanvasElement, series: TimeSeries) {
        super(canvas.getContext('2d')!, {
            // The type of chart we want to create
            type: 'line',

            // The data for our datasets
            data: {
                labels: series.getTimes(),
                datasets: [{
                    // See localize
                    data: series.getSeries(0),
                    yAxisID: 'cpus'
                }, {
                    // See localize
                    data: series.getSeries(1),
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
                                return formatMemory(Math.round(Number(value)));
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
        
        this.localizeChart();
    }

    setPeriod(period: number) {
        this.period = period;
        this.update();
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

    localizeChart() {
        (<any>this.options.scales?.x).adapters.date.locale = JGWC.lang();
        this.data.datasets[0].label 
            = JGConsole.localize(l10nBundles, JGWC.lang(), "Used CPUs")        
        this.data.datasets[1].label 
            = JGConsole.localize(l10nBundles, JGWC.lang(), "Used RAM")
        this.update();        
    }
    
    shift() {
        this.setPropValue("options.scales.x.max", Date.now());
        this.update();
    }
    
    update() {
        this.setPropValue("options.scales.x.min", Date.now() - this.period);
        super.update();
    }
}

