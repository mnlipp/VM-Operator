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
import JGConsole from "jgconsole"
import JgwcPlugin, { JGWC } from "jgwc";
import { provideApi, getApi } from "aash-plugin";
import l10nBundles from "l10nBundles";

import "./VmConlet-style.scss"

// For global access
declare global {
  interface Window {
    orgJDrupesVmOperatorVmConlet: any;
  }
}

window.orgJDrupesVmOperatorVmConlet = {};

window.orgJDrupesVmOperatorVmConlet.initPreview 
        = (previewDom: HTMLElement, isUpdate: boolean) => {
    const app = createApp({});
    app.use(JgwcPlugin, []);
    app.config.globalProperties.window = window;
    app.mount(previewDom);
};

window.orgJDrupesVmOperatorVmConlet.initView 
        = (viewDom: HTMLElement, isUpdate: boolean) => {
    
    const app = createApp({
        setup(_props: any) {
            const conletId: string 
                = (<HTMLElement>viewDom.parentNode!).dataset["conletId"]!;

            provideApi(viewDom, {
            });

            return { }            
        }
    });
    app.use(JgwcPlugin);
    app.config.globalProperties.window = window;
    app.mount(viewDom);
};
