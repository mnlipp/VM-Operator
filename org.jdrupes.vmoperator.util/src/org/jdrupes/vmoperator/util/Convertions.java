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

package org.jdrupes.vmoperator.util;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Provides methods for parsing "official" memory sizes..
 */
@SuppressWarnings("PMD.UseUtilityClass")
public class Convertions {

    @SuppressWarnings({ "PMD.UseConcurrentHashMap",
        "PMD.FieldNamingConventions", "PMD.VariableNamingConventions" })
    private static final Map<String, BigInteger> unitMap = new HashMap<>();
    @SuppressWarnings({ "PMD.FieldNamingConventions",
        "PMD.VariableNamingConventions" })
    private static final List<Map.Entry<String, BigInteger>> unitMappings;
    @SuppressWarnings({ "PMD.FieldNamingConventions",
        "PMD.VariableNamingConventions" })
    private static final Pattern memorySize
        = Pattern.compile("^\\s*(\\d+(\\.\\d+)?)\\s*([A-Za-z]*)\\s*");

    static {
        // SI units and common abbreviations
        BigInteger factor = BigInteger.ONE;
        unitMap.put("", factor);
        BigInteger scale = BigInteger.valueOf(1000);
        for (var unit : List.of("B", "kB", "MB", "GB", "TB", "PB", "EB")) {
            unitMap.put(unit, factor);
            factor = factor.multiply(scale);
        }
        // Binary units
        factor = BigInteger.valueOf(1024);
        scale = BigInteger.valueOf(1024);
        for (var unit : List.of("KiB", "MiB", "GiB", "TiB", "PiB", "EiB")) {
            unitMap.put(unit, factor);
            factor = factor.multiply(scale);
        }
        unitMappings = unitMap.entrySet().stream()
            .sorted((a, b) -> -1 * a.getValue().compareTo(b.getValue()))
            .toList();
    }

    /**
     * Parses a memory size specification.
     *
     * @param amount the amount
     * @return the big integer
     */
    @SuppressWarnings("PMD.DataflowAnomalyAnalysis")
    public static BigInteger parseMemory(Object amount) {
        if (amount == null) {
            return (BigInteger) amount;
        }
        if (amount instanceof BigInteger number) {
            return number;
        }
        if (amount instanceof Number number) {
            return BigInteger.valueOf(number.longValue());
        }
        var matcher = memorySize.matcher(amount.toString());
        if (!matcher.matches()) {
            throw new NumberFormatException(amount.toString());
        }
        var unit = BigInteger.ONE;
        if (matcher.group(3) != null) {
            unit = unitMap.get(matcher.group(3));
            if (unit == null) {
                throw new NumberFormatException("Illegal unit \""
                    + matcher.group(3) + "\" in \"" + amount.toString() + "\"");
            }
        }
        var number = matcher.group(1);
        return new BigDecimal(number).multiply(new BigDecimal(unit))
            .toBigInteger();
    }

    /**
     * Format memory size for humans.
     *
     * @param size the size
     * @return the string
     */
    public static String formatMemory(BigInteger size) {
        for (var mapping : unitMappings) {
            if (size.compareTo(mapping.getValue()) >= 0
                && size.mod(mapping.getValue()).equals(BigInteger.ZERO)) {
                return size.divide(mapping.getValue()).toString()
                    + " " + mapping.getKey();
            }
        }
        return size.toString();
    }
}
