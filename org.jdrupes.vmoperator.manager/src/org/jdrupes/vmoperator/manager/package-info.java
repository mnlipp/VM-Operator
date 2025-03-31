/*
 * VM-Operator
 * Copyright (C) 2023,2025 Michael N. Lipp
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

/**
 * The following diagram shows the components of the manager application.
 *  
 * In framework terms, the {@link org.jdrupes.vmoperator.manager.Manager}
 * is the root component of the application. Two of its child components, 
 * the {@link org.jdrupes.vmoperator.manager.Controller} and the WebGui
 * provide the functions that are apparent to the user.
 * 
 * The position of the components in the component tree is important
 * when writing the configuration file for the manager and therefore
 * shown below. In order to  keep the diagram readable, the 
 * components attached to the 
 * {@link org.jgrapes.webconsole.base.WebConsole} are shown in a 
 * separate diagram further down.
 * 
 * ![Manager components](manager-components.svg)
 * 
 * Component hierarchy of the web console:
 * 
 * ![Web console components](console-components.svg)
 *
 * The components marked as "&lt;&lt;internal&gt;&gt;" have no 
 * configuration options or use their default values when used 
 * in this application.
 * 
 * As an example, the following YAML configures a different port for the 
 * GUI and some users. The relationship to the component tree should
 * be obvious.  
 * ```
 * "/Manager":
 *   "/GuiSocketServer":
 *     port: 8888
 *   "/GuiHttpServer":
 *     "/ConsoleWeblet":
 *       "/WebConsole":
 *         "/LoginConlet":
 *           users:
 *             ...
 * ```
 *  
 * Developers may also be interested in the usage of channels
 * by the application's components:
 *
 * ![Main channels](app-channels.svg)
 * 
 * @startuml manager-components.svg
 * skinparam component {
 *   BackGroundColor #FEFECE
 *   BorderColor #A80036
 *   BorderThickness 1.25
 *   BackgroundColor<<internal>> #F1F1F1
 *   BorderColor<<internal>> #181818
 *   BorderThickness<<internal>> 1
 * }
 * skinparam packageStyle rectangle
 * 
 * Component NioDispatcher as NioDispatcher <<internal>>
 * [Manager] *-up- [NioDispatcher]
 * Component HttpConnector as HttpConnector <<internal>>
 * [Manager] *-up- [HttpConnector]
 * Component FileSystemWatcher as FileSystemWatcher <<internal>>
 * [Manager] *-up- [FileSystemWatcher]
 * Component YamlConfigurationStore as YamlConfigurationStore <<internal>>
 * [Manager] *-left- [YamlConfigurationStore]
 * [YamlConfigurationStore] *-right[hidden]- [Controller]
 *
 * [Manager] *-- [Controller]
 * Component VmMonitor as VmMonitor <<internal>>
 * [Controller] *-- [VmMonitor]
 * [VmMonitor] -right[hidden]- [PoolMonitor]
 * Component PoolMonitor as PoolMonitor <<internal>>
 * [Controller] *-- [PoolMonitor]
 * Component PodMonitor as PodMonitor <<internal>>
 * [Controller] *-- [PodMonitor]
 * [PodMonitor] -up[hidden]- VmMonitor
 * Component DisplaySecretMonitor as DisplaySecretMonitor <<internal>>
 * [Controller] *-- [DisplaySecretMonitor]
 * [DisplaySecretMonitor] -up[hidden]- VmMonitor
 * [Controller] *-left- [Reconciler]
 * [Controller] -right[hidden]- [GuiHttpServer]
 * 
 * [Manager] *-down- [GuiSocketServer:8080]
 * [Manager] *-- [GuiHttpServer]
 * Component PreferencesStore as PreferencesStore <<internal>>
 * [GuiHttpServer] *-up- [PreferencesStore]
 * Component InMemorySessionManager as InMemorySessionManager <<internal>>
 * [GuiHttpServer] *-up- [InMemorySessionManager]
 * Component LanguageSelector as LanguageSelector <<internal>>
 * [GuiHttpServer] *-right- [LanguageSelector]
 * 
 * package "Conceptual WebConsole" {
 *   [ConsoleWeblet] *-- [WebConsole]
 * }
 * [GuiHttpServer] *-- [ConsoleWeblet]
 * @enduml
 * 
 * @startuml console-components.svg
 * skinparam component {
 *   BackGroundColor #FEFECE
 *   BorderColor #A80036
 *   BorderThickness 1.25
 *   BackgroundColor<<internal>> #F1F1F1
 *   BorderColor<<internal>> #181818
 *   BorderThickness<<internal>> 1
 * }
 * skinparam packageStyle rectangle
 * 
 * Component BrowserLocalBackedKVStore as BrowserLocalBackedKVStore <<internal>>
 * [WebConsole] *-up- [BrowserLocalBackedKVStore]
 * Component KVStoreBasedConsolePolicy as KVStoreBasedConsolePolicy <<internal>>
 * [WebConsole] *-up- [KVStoreBasedConsolePolicy]
 * 
 * [WebConsole] *-- [RoleConfigurator]
 * [WebConsole] *-- [RoleConletFilter]
 * [WebConsole] *-left- [LoginConlet]
 * [WebConsole] *-right- [OidcClient]
 * 
 * Component "ComponentCollector\nfor page resources" as cpr <<internal>>
 * [WebConsole] *-- [cpr]
 * Component "ComponentCollector\nfor conlets" as cc <<internal>>
 * [WebConsole] *-- [cc]
 * 
 * package "Providers and Conlets" {
 *   [Some component]
 * }
 * 
 * [cpr] *-- [Some component]
 * [cc] *-- [Some component]
 * @enduml
 * 
 * @startuml app-channels.svg
 * skinparam packageStyle rectangle
 * 
 * () "manager" as mgr
 * mgr .left. [FileSystemWatcher]
 * mgr .right. [YamlConfigurationStore]
 * mgr .. [Controller]
 * mgr .up. [Manager]
 * mgr .up. [VmWatcher]
 * mgr .. [Reconciler]
 * 
 * () "guiTransport" as hT
 * hT .up. [GuiSocketServer:8080]
 * hT .down. [GuiHttpServer]
 * hT .right[hidden]. [HttpConnector]
 * 
 * [YamlConfigurationStore] -right[hidden]- hT
 * 
 * () "guiHttp" as http
 * http .up. [GuiHttpServer]
 * http .up. [HttpConnector]
 * note top of [HttpConnector]: transport layer com-\nponents omitted
 * 
 * [PreferencesStore] .. http
 * [OidcClient] .up. http
 * [LanguageSelector] .left. http
 * [InMemorySessionManager] .up. http
 * 
 * package "Conceptual WebConsole" {
 *   [ConsoleWeblet] .right. http
 *   [ConsoleWeblet] *-down- [WebConsole]
 * }
 * 
 * [Controller] .down[hidden]. [ConsoleWeblet]
 * 
 * () "console" as console
 * console .. WebConsole
 * 
 * [OidcClient] .. console
 * [LoginConlet] .right. console
 * 
 * note right of console: More conlets\nconnect here
 * 
 * @enduml
 */
package org.jdrupes.vmoperator.manager;
