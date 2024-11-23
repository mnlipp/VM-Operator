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

import { ref, nextTick } from "vue";

/**
 * A controller for conditionally shown inputs. "Conditionally shown"
 * means that the value is usually shown using some display element
 * (e.g. `span`). Only when that elements gets the focus, it is replaced
 * with an input element for editing the value.
 */
export default class ConditionlInputController {

    private submitCallback: (selected: string, value: number | null) 
        => string | null;
    private readonly inputKey = ref("");
    private startValue: string | null = null;
    private inputElement: HTMLInputElement | null = null;
    private errorMessage = ref("");

    /**
     * Creates a new controller.
     */
    constructor(submitCallback: (selected: string, value: number | null) 
        => string | null) {
        // this.inputRef = inputRef;
        this.submitCallback = submitCallback;
    }
    
    get key() {
        return this.inputKey.value;
    }

    get error() {
        return this.errorMessage.value;
    }

    set input(element: HTMLInputElement) {
        this.inputElement = element;
    }

    startEdit (key: string, value: string) {
        if (this.inputKey.value != "") {
            return;
        }
        this.startValue = value;
        this.errorMessage.value = "";
        this.inputKey.value = key;
        nextTick(() => {
            this.inputElement!.value = value;
            this.inputElement!.focus();
        });
    }

    endEdit (converter?: (value: string) => number | null) : boolean {
        if (typeof converter === 'undefined') {
            this.inputKey.value = "";
            return false;
        }
        const newValue = converter(this.inputElement!.value);
        if (newValue === this.startValue) {
            this.inputKey.value = "";
            return false;
        }
        const submitResult = this.submitCallback (this.inputKey.value, newValue);
        if (submitResult !== null) {
            this.errorMessage.value = submitResult;
            // Neither doing it directly nor doing it with nextTick works.
            setTimeout(() => this.inputElement!.focus(), 10);
        } else {
            this.inputKey.value = "";
        }
        
        // In case it is called by form action
        return false;
    }
    
    get parseNumber() {
        return (value: string): number | null => {
            if (value.match(/^\d+$/)) {
                return Number(value);
            }
            return null;
        }
    }

}