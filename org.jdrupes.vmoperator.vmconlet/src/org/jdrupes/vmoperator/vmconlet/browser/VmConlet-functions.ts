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

import { reactive, ref, createApp, computed, onMounted } from "vue";
import JGConsole from "jgconsole";
import JgwcPlugin, { JGWC } from "jgwc";
import { provideApi, getApi } from "aash-plugin";
import l10nBundles from "l10nBundles";

import "./VmConlet-style.scss";

//
// Helpers
//
let unitMap = new Map<string, bigint>();
let unitMappings = new Array<{ key: string; value: bigint }>();
let memorySize = /^\\s*(\\d+(\\.\\d+)?)\\s*([A-Za-z]*)\\s*/;

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

window.orgJDrupesVmOperatorVmConlet.initPreview
    = (previewDom: HTMLElement, isUpdate: boolean) => {
        const app = createApp({});
        app.use(JgwcPlugin, []);
        app.config.globalProperties.window = window;
        app.mount(previewDom);
    };

window.orgJDrupesVmOperatorVmConlet.initView = (viewDom: HTMLElement,
    isUpdate: boolean) => {
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
                ["currentRam", "currentRam"]
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
            }
        }
    });
    app.use(JgwcPlugin);
    app.config.globalProperties.window = window;
    app.mount(viewDom);
};

JGConsole.registerConletFunction("org.jdrupes.vmoperator.vmconlet.VmConlet",
    "updateVm", function(conletId: String, vmDefinition: any) {
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
    "removeVm", function(conletId: String, vmName: String) {
        vmInfos.delete(vmName);
    });
