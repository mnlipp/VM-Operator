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

import { ref, Ref, nextTick } from "vue";

/**
 * A controller for conditionally shown inputs. "Conditionally shown"
 * means that the value is usually shown using some display element
 * (e.g. `span`). Only when that elements gets the focus, it is replaced
 * with an input element for editing the value.
 */
export default class ConditionlInputController {

    private submitCallback: (selected: string, value: any) => string | null;
    private readonly inputKey = ref("");
    private inputRef: Ref<HTMLInputElement> | Ref<Array<HTMLInputElement>>;
    private errorMessage = ref("");

    /**
     * Creates a new controller. The ref to the displayed element cannot
     * be provided by this object, because binding a `HTMLInputElement` 
     * to a ref works only if the ref is directly defined in the setup.
     */
    constructor(inputRef: Ref<HTMLInputElement> | Ref<Array<HTMLInputElement>>,
        submitCallback: (selected: string, value: string) => string | null) {
        this.inputRef = inputRef;
        this.submitCallback = submitCallback;
    }
    
    get key() {
        return this.inputKey.value;
    }

    get error() {
        return this.errorMessage.value;
    }

    private element(): HTMLInputElement {
        if (this.inputRef.value instanceof Array) {
            return this.inputRef.value[0];
        }
        return this.inputRef.value;
    }
  
    startEdit (key: string, value: any) {
        if (this.inputKey.value != "") {
            return;
        }
        this.errorMessage.value = "";
        this.inputKey.value = key;
        nextTick(() => {
            this.element().value = value;
            this.element().focus();
        });
    }

    endEdit (converter?: (value: string) => any | null) {
        if (typeof converter === 'undefined') {
            this.inputKey.value = "";
            return null;
        }
        let newValue = converter(this.element().value);
        let submitResult = this.submitCallback (this.inputKey.value, newValue);
        if (submitResult !== null) {
            this.errorMessage.value = submitResult;
            // Neither doing it directly nor doing it with nextTick works.
            setTimeout(() => this.element().focus(), 10);
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