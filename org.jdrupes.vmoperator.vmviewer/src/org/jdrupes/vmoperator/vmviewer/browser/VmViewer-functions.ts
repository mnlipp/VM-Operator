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

import "./VmViewer-style.scss";

// For global access
declare global {
    interface Window {
        orgJDrupesVmOperatorVmViewer: { 
            initPreview?: (previewDom: HTMLElement, isUpdate: boolean) => void,
            initEdit?: (viewDom: HTMLElement, isUpdate: boolean) => void
            applyEdit?: (viewDom: HTMLElement, apply: boolean) => void
        }
    }
}

window.orgJDrupesVmOperatorVmViewer = {};

interface Api {
    /* eslint-disable @typescript-eslint/no-explicit-any */
    vmName: string;
    vmDefinition: any;
}

const localize = (key: string) => {
    return JGConsole.localize(
        l10nBundles, JGWC.lang(), key);
};

window.orgJDrupesVmOperatorVmViewer.initPreview = (previewDom: HTMLElement,
    _isUpdate: boolean) => {
    const app = createApp({
        setup(_props: object) {
            const conletId = (<HTMLElement>previewDom.closest(
                "[data-conlet-id]")!).dataset["conletId"]!;
            const resourceBase = (<HTMLElement>previewDom.closest(
                "*[data-conlet-resource-base]")!).dataset.conletResourceBase;

            const previewApi: Api = reactive({
                vmName: "",
                vmDefinition: {}
            });
            const vmDef = computed(() => previewApi.vmDefinition);

            watch(() => previewApi.vmName, (name: string) => {
                if (name !== "") {
                    JGConsole.instance.updateConletTitle(conletId, name);
                }
            });
        
            provideApi(previewDom, previewApi);

            const vmAction = (vmName: string, action: string) => {
                JGConsole.notifyConletModel(conletId, action, vmName);
            };
        
            return { localize, resourceBase, vmDef, vmAction };
        },
        template: `
          <table>
            <tbody>
              <tr>
                <td><img role=button 
                  v-on:click="vmAction(vmDef.name, 'openConsole')"
                  :src="resourceBase + (vmDef.spec 
                  && vmDef.spec.vm.state == 'Running'
                  ? 'computer.svg' : 'computer-off.svg')"></td>
                <td v-if="vmDef.spec"
                  class="jdrupes-vmoperator-vmviewer-preview-action-list">
                  <span role="button" v-if="vmDef.spec.vm.state != 'Running'" 
                      tabindex="0" class="fa fa-play" :title="localize('Start VM')"
                    v-on:click="vmAction(vmDef.name, 'start')"></span>
                  <span role="button" v-else class="fa fa-play"
                    aria-disabled="true" :title="localize('Start VM')"></span>
                  <span role="button" v-if="vmDef.spec.vm.state != 'Stopped'"
                    tabindex="0" class="fa fa-stop" :title="localize('Stop VM')"
                    v-on:click="vmAction(vmDef.name, 'stop')"></span>
                  <span role="button" v-else class="fa fa-stop"
                    aria-disabled="true" :title="localize('Stop VM')"></span>
                </td>
                <td v-else>
                </td>
              </tr>
            </tbody>
          </table>`
    });
    app.use(JgwcPlugin, []);
    app.config.globalProperties.window = window;
    app.mount(previewDom);
};

JGConsole.registerConletFunction("org.jdrupes.vmoperator.vmviewer.VmViewer",
    "updateConfig", function(conletId: string, vmName: string) {
        const conlet = JGConsole.findConletPreview(conletId);
        if (!conlet) {
            return;
        }
        const api = getApi<Api>(conlet.element().querySelector(
            ":scope .jdrupes-vmoperator-vmviewer-preview"))!;
        api.vmName = vmName;
    });

JGConsole.registerConletFunction("org.jdrupes.vmoperator.vmviewer.VmViewer",
    "updateVmDefinition", function(conletId: string, vmDefinition: any) {
        const conlet = JGConsole.findConletPreview(conletId);
        if (!conlet) {
            return;
        }
        const api = getApi<Api>(conlet.element().querySelector(
            ":scope .jdrupes-vmoperator-vmviewer-preview"))!;
        // Add some short-cuts for rendering
        vmDefinition.name = vmDefinition.metadata.name;
        vmDefinition.currentCpus = vmDefinition.status.cpus;
        vmDefinition.currentRam = Number(vmDefinition.status.ram);
        api.vmDefinition = vmDefinition;
    });

window.orgJDrupesVmOperatorVmViewer.initEdit = (dialogDom: HTMLElement,
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

            const vmNameInput = ref<string>("");
            const conletId = (<HTMLElement>dialogDom.closest(
                "[data-conlet-id]")!).dataset["conletId"]!;
            const conlet = JGConsole.findConletPreview(conletId);
            if (conlet) {
                const api = getApi<Api>(conlet.element().querySelector(
                    ":scope .jdrupes-vmoperator-vmviewer-preview"))!;
                vmNameInput.value = api.vmName;
            }

            provideApi(dialogDom, vmNameInput);
                        
            return { formId, localize, vmNameInput };
        }
    });
    app.use(JgwcPlugin);
    app.mount(dialogDom);
}

window.orgJDrupesVmOperatorVmViewer.applyEdit =
    (dialogDom: HTMLElement, apply: boolean) => {
    if (!apply) {
        return;
    }
    const conletId = (<HTMLElement>dialogDom.closest("[data-conlet-id]")!)
        .dataset["conletId"]!;
    const vmName = getApi<ref<string>>(dialogDom!)!.value;
    JGConsole.notifyConletModel(conletId, "selectedVm", vmName);
}
