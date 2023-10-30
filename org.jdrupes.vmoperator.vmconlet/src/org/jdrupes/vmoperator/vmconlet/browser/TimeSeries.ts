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

type OnChangeCallback = ((ts: TimeSeries) => void) | null;

export default class TimeSeries {
    private timestamps: Date[] = [];
    private series: number[][];
    private period: number;
    private onChange: OnChangeCallback;

    constructor(nbOfSeries: number, period = 24 * 3600 * 1000,
        onChange: OnChangeCallback = null) {
        this.period = period;
        this.onChange = onChange;
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
        if (this.onChange) {
            this.onChange(this);
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
        let limit = time.getTime() - this.period;
        while (this.timestamps.length > 2
            && this.timestamps[0].getTime() < limit
            && this.timestamps[1].getTime() < limit) {
            this.timestamps.shift();
            for (let values of this.series) {
                values.shift();
            }
        }
        if (this.onChange) {
            this.onChange(this);
        }
    }

    getTimes(): Date[] {
        return this.timestamps;
    }

    getSeries(n: number): number[] {
        return this.series[n];
    }
}

