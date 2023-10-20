/*
 * Moodle Tools Console
 * Copyright (C) 2022 Michael N. Lipp
 *
 * This program is free software; you can redistribute it and/or modify it 
 * under the terms of the GNU General Public License as published by 
 * the Free Software Foundation; either version 3 of the License, or 
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but 
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License 
 * for more details.
 *
 * You should have received a copy of the GNU General Public License along 
 * with this program; if not, see <http://www.gnu.org/licenses/>.
 */

'use strict';

const l10nBundles = new Map();
let entries = null;
// <#list supportedLanguages() as l>
entries = new Map();
l10nBundles.set('${l.locale.toLanguageTag()}', entries);
// <#list l.l10nBundle.keys as key>
entries.set('${key}', '${l.l10nBundle.getString(key)}')
// </#list>
// </#list>    

export default l10nBundles;
