/*
 * VM-Operator
 * Copyright (C) 2024 Michael N. Lipp
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
    reactive, ref, createApp, computed, watch
} from "vue";
import JGConsole from "jgconsole";
import JgwcPlugin, { JGWC } from "jgwc";
import { provideApi, getApi } from "aash-plugin";
import l10nBundles from "l10nBundles";

import "./VmAccess-style.scss";

// For global access
declare global {
    interface Window {
        orgJDrupesVmOperatorVmAccess: { 
            initPreview?: (previewDom: HTMLElement, isUpdate: boolean) => void,
            initEdit?: (viewDom: HTMLElement, isUpdate: boolean) => void,
            applyEdit?: (viewDom: HTMLElement, apply: boolean) => void,
            confirmReset?: (conletType: string, conletId: string) => void
        }
    }
}

window.orgJDrupesVmOperatorVmAccess = {};

interface Api {
    /* eslint-disable @typescript-eslint/no-explicit-any */
    vmName: string;
    vmDefinition: any;
    poolName: string | null;
    permissions: string[];
}

const localize = (key: string) => {
    return JGConsole.localize(
        l10nBundles, JGWC.lang(), key);
};

window.orgJDrupesVmOperatorVmAccess.initPreview = (previewDom: HTMLElement,
    _isUpdate: boolean) => {
    const app = createApp({
        setup(_props: object) {
            const conletId = (<HTMLElement>previewDom.closest(
                "[data-conlet-id]")!).dataset["conletId"]!;
            const resourceBase = (<HTMLElement>previewDom.closest(
                "*[data-conlet-resource-base]")!).dataset.conletResourceBase;

            const previewApi: Api = reactive({
                vmName: "",
                vmDefinition: {},
                poolName: null,
                permissions: []
            });
            const poolName = computed(() => previewApi.poolName);
            const vmName = computed(() => previewApi.vmDefinition.name);
            const configured = computed(() => previewApi.vmDefinition.spec);
            const busy = computed(() => previewApi.vmDefinition.spec
                && (previewApi.vmDefinition.spec.vm.state === 'Running'
                        && !previewApi.vmDefinition.running
                    || previewApi.vmDefinition.spec.vm.state === 'Stopped' 
                        && previewApi.vmDefinition.running));
            const startable = computed(() => previewApi.vmDefinition.spec
                && previewApi.vmDefinition.spec.vm.state !== 'Running' 
                && !previewApi.vmDefinition.running
                && previewApi.permissions.includes('start')
                || previewApi.poolName !== null && !previewApi.vmDefinition.name);
            const stoppable = computed(() => previewApi.vmDefinition.spec &&
                previewApi.vmDefinition.spec.vm.state !== 'Stopped' 
                && previewApi.vmDefinition.running);
            const running = computed(() => previewApi.vmDefinition.running);
            const inUse = computed(() => previewApi.vmDefinition.usedBy != '');
            const permissions = computed(() => previewApi.permissions);

            watch(previewApi, (api: Api) => {
                JGConsole.instance.updateConletTitle(conletId, 
                    api.poolName || api.vmDefinition.name || "");
            });
        
            provideApi(previewDom, previewApi);

            const vmAction = (action: string) => {
                JGConsole.notifyConletModel(conletId, action);
            };
        
            return { localize, resourceBase, vmAction, poolName, vmName, 
                configured, busy, startable, stoppable, running, inUse,
                permissions };
        },
        template: `
          <table>
            <tbody>
              <tr>
                <td rowspan="2" style="position: relative"><span
                  style="position: absolute;" :class="{ busy: busy }"
                  ><img role=button :aria-disabled="!running
                      || !permissions.includes('accessConsole')" 
                  v-on:click="vmAction('openConsole')"
                  :src="resourceBase + (running
                      ? (inUse ? 'computer-in-use.svg' : 'computer.svg') 
                      : 'computer-off.svg')"
                  :title="localize('Open console')"></span><span
                  style="visibility: hidden;"><img
                  :src="resourceBase + 'computer.svg'"></span></td>
                <td v-if="!poolName" style="padding: 0;"></td>
                <td v-else>{{ vmName }}</td>
              </tr>
              <tr>
                <td class="jdrupes-vmoperator-vmaccess-preview-action-list">
                  <span role="button" :aria-disabled="!startable" 
                    tabindex="0" class="fa fa-play" :title="localize('Start VM')"
                    v-on:click="vmAction('start')"></span>
                  <span role="button"
                    :aria-disabled="!stoppable || !permissions.includes('stop')" 
                    tabindex="0" class="fa fa-stop" :title="localize('Stop VM')"
                    v-on:click="vmAction('stop')"></span>
                  <span role="button"
                    :aria-disabled="!running || !permissions.includes('reset')" 
                    tabindex="0" class="svg-icon" :title="localize('Reset VM')"
                    v-on:click="vmAction('reset')">
                    <svg viewBox="0 0 1541.33 1535.5083">
                      <path d="m 0,127.9968 v 448 c 0,35 29,64 64,64 h 448 c 35,0 64,-29 64,-64 0,-17 -6.92831,-33.07213 -19,-45 C 264.23058,241.7154 337.19508,314.89599 109,82.996795 c -11.999999,-12 -28,-19 -45,-19 -35,0 -64,29 -64,64.000005 z" />
                      <path d="m 772.97656,1535.5046 c 117.57061,0.3623 236.06134,-26.2848 345.77544,-81.4687 292.5708,-147.1572 459.8088,-465.37411 415.5214,-790.12504 C 1489.9861,339.15993 1243.597,77.463924 922.29883,14.342498 601.00067,-48.778928 274.05699,100.37563 110.62891,384.39133 c -34.855139,60.57216 -14.006492,137.9313 46.5664,172.78516 60.57172,34.85381 137.92941,14.00532 172.78321,-46.56641 109.97944,-191.12927 327.69604,-290.34657 543.53515,-247.94336 215.83913,42.40321 380.18953,216.77543 410.00973,435.44141 29.8203,218.66598 -81.8657,430.94957 -278.4863,529.84567 -196.6206,98.8962 -432.84043,61.8202 -589.90233,-92.6777 -24.91016,-24.5038 -85.48587,-83.3326 -119.02246,-52.9832 -24.01114,21.7292 -35.41741,29.5454 -59.9209,54.4559 -24.50381,24.9102 -35.33636,36.9034 -57.54543,60.4713 -38.1335,40.4667 34.10761,93.9685 59.01808,118.472 145.96311,143.5803 339.36149,219.2087 535.3125,219.8125 z"/>
                    </svg>
                  </span>
                </td>
              </tr>
            </tbody>
          </table>`
    });
    app.use(JgwcPlugin, []);
    app.config.globalProperties.window = window;
    app.mount(previewDom);
};

JGConsole.registerConletFunction("org.jdrupes.vmoperator.vmaccess.VmAccess",
    "updateConfig",
    function(conletId: string, type: string, resource: string,
        permissions: []) {
        const conlet = JGConsole.findConletPreview(conletId);
        if (!conlet) {
            return;
        }
        const api = getApi<Api>(conlet.element().querySelector(
            ":scope .jdrupes-vmoperator-vmaccess-preview"))!;
        if (type === "VM") {
            api.vmName = resource;
            api.poolName = "";
        } else {
            api.poolName = resource;
            api.vmName = "";
        }
        api.permissions = permissions;
    });

JGConsole.registerConletFunction("org.jdrupes.vmoperator.vmaccess.VmAccess",
    "updateVmDefinition", function(conletId: string, vmDefinition: any | null) {
        const conlet = JGConsole.findConletPreview(conletId);
        if (!conlet) {
            return;
        }
        const api = getApi<Api>(conlet.element().querySelector(
            ":scope .jdrupes-vmoperator-vmaccess-preview"))!;
        if (vmDefinition) {
            // Add some short-cuts for rendering
            vmDefinition.name = vmDefinition.metadata.name;
            vmDefinition.currentCpus = vmDefinition.status.cpus;
            vmDefinition.currentRam = Number(vmDefinition.status.ram);
            vmDefinition.usedBy = vmDefinition.status.consoleClient || "";
            for (const condition of vmDefinition.status.conditions) {
                if (condition.type === "Running") {
                    vmDefinition.running = condition.status === "True";
                    vmDefinition.runningConditionSince 
                        = new Date(condition.lastTransitionTime);
                    break;
                }
            }
        } else {
            vmDefinition = {};
        }
        api.vmDefinition = vmDefinition;
    });

JGConsole.registerConletFunction("org.jdrupes.vmoperator.vmaccess.VmAccess",
    "openConsole", function(_conletId: string, mimeType: string, data: string) {
        let target = document.getElementById(
            "org.jdrupes.vmoperator.vmaccess.VmAccess.target");
        if (!target) {
            target = document.createElement("iframe");
            target.id = "org.jdrupes.vmoperator.vmaccess.VmAccess.target";
            target.setAttribute("name", target.id);
            target.setAttribute("style", "display: none;");            
            document.querySelector("body")!.append(target);
        }
        const url = "data:" + mimeType + ";base64," + data;
        window.open(url, target.id);
    });

window.orgJDrupesVmOperatorVmAccess.initEdit = (dialogDom: HTMLElement,
    isUpdate: boolean) => {
    if (isUpdate) {
        return;
    }
    const app = createApp({
        setup() {
            const formId = (<HTMLElement>dialogDom
                .closest("*[data-conlet-id]")!).id + "-form";

            const localize = (key: string) => {
                return JGConsole.localize(
                    l10nBundles, JGWC.lang()!, key);
            };

            const resource = ref<string>("vm");
            const vmNameInput = ref<string>("");
            const poolNameInput = ref<string>("");
            
            watch(resource, (resource: string) => {
                if (resource === "vm") {
                    poolNameInput.value = "";
                }
                if (resource === "pool")
                    vmNameInput.value = "";
            });
            
            const conletId = (<HTMLElement>dialogDom.closest(
                "[data-conlet-id]")!).dataset["conletId"]!;
            const conlet = JGConsole.findConletPreview(conletId);
            if (conlet) {
                const api = getApi<Api>(conlet.element().querySelector(
                    ":scope .jdrupes-vmoperator-vmaccess-preview"))!;
                if (api.poolName) {
                    resource.value = "pool";
                }
                vmNameInput.value = api.vmName;
                poolNameInput.value = api.poolName;
            }

            provideApi(dialogDom, { resource: () => resource.value,
                name: () => resource.value === "vm"
                    ? vmNameInput.value : poolNameInput.value });
                        
            return { formId, localize, resource, vmNameInput, poolNameInput };
        }
    });
    app.use(JgwcPlugin);
    app.mount(dialogDom);
}

window.orgJDrupesVmOperatorVmAccess.applyEdit =
    (dialogDom: HTMLElement, apply: boolean) => {
    if (!apply) {
        return;
    }
    const conletId = (<HTMLElement>dialogDom.closest("[data-conlet-id]")!)
        .dataset["conletId"]!;
    const editApi = getApi<ref<string>>(dialogDom!)!;
    JGConsole.notifyConletModel(conletId, "selectedResource", editApi.resource(),
        editApi.name());
}

window.orgJDrupesVmOperatorVmAccess.confirmReset = 
        (conletType: string, conletId: string) => {
    JGConsole.instance.closeModalDialog(conletType, conletId);
    JGConsole.notifyConletModel(conletId, "resetConfirmed");
}