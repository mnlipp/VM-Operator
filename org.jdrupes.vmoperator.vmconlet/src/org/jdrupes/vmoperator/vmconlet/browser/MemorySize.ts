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

const unitMap = new Map<string, number>();
const unitMappings = new Array<{ key: string; value: number }>();
const memorySize = /^(\d+(\.\d+)?)\s*(B|kB|MB|GB|TB|PB|EB|KiB|MiB|GiB|TiB|PiB|EiB)?$/;

// SI units and common abbreviations
let factor = 1;
unitMap.set("", factor);
let scale = 1000;
for (const unit of ["B", "kB", "MB", "GB", "TB", "PB", "EB"]) {
    unitMap.set(unit, factor);
    factor = factor * scale;
}

// Binary units
factor = 1024;
scale = 1024;
for (const unit of ["KiB", "MiB", "GiB", "TiB", "PiB", "EiB"]) {
    unitMap.set(unit, factor);
    factor = factor * scale;
}
unitMap.forEach((value: number, key: string) => {
    unitMappings.push({ key, value });
});
unitMappings.sort((a, b) => a.value < b.value ? 1 : a.value > b.value ? -1 : 0);

export function formatMemory(size: number): string {
    for (const mapping of unitMappings) {
        if (size >= mapping.value
            && (size % mapping.value) === 0) {
            return (size / mapping.value + " " + mapping.key).trim();
        }
    }
    return size.toString();
}

export function parseMemory(value: string): number | null {
    const match = value.match(memorySize);
    if (!match) {
        return null;
    }
    
    let unit = 1;
    if (match[3]) {
        unit = unitMap.get(match[3])!;
    }
    return Number(match[1]) * unit;   
}
