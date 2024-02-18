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

package org.jdrupes.vmoperator.vmconlet;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * The Class TimeSeries.
 */
@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
public class TimeSeries {

    private final List<Entry> data = new LinkedList<>();
    private final Duration period;

    /**
     * Instantiates a new time series.
     *
     * @param period the period
     */
    public TimeSeries(Duration period) {
        this.period = period;
    }

    /**
     * Adds data to the series.
     *
     * @param time the time
     * @param numbers the numbers
     * @return the time series
     */
    @SuppressWarnings("PMD.AvoidLiteralsInIfCondition")
    public TimeSeries add(Instant time, Number... numbers) {
        var newEntry = new Entry(time, numbers);
        boolean adjust = false;
        if (data.size() >= 2) {
            var lastEntry = data.get(data.size() - 1);
            var lastButOneEntry = data.get(data.size() - 2);
            adjust = lastEntry.valuesEqual(lastButOneEntry)
                && lastEntry.valuesEqual(newEntry);
        }
        if (adjust) {
            data.get(data.size() - 1).adjustTime(time);
        } else {
            data.add(new Entry(time, numbers));
        }

        // Purge
        Instant limit = time.minus(period);
        while (data.size() > 2
            && data.get(0).getTime().isBefore(limit)
            && data.get(1).getTime().isBefore(limit)) {
            data.remove(0);
        }
        return this;
    }

    /**
     * Returns the entries.
     *
     * @return the list
     */
    public List<Entry> entries() {
        return data;
    }

    /**
     * The Class Entry.
     */
    public static class Entry {
        private Instant timestamp;
        private final Number[] values;

        /**
         * Instantiates a new entry.
         *
         * @param time the time
         * @param numbers the numbers
         */
        @SuppressWarnings("PMD.ArrayIsStoredDirectly")
        public Entry(Instant time, Number... numbers) {
            timestamp = time;
            values = numbers;
        }

        /**
         * Changes the entry's time.
         *
         * @param time the time
         */
        public void adjustTime(Instant time) {
            timestamp = time;
        }

        /**
         * Returns the entry's time.
         *
         * @return the instant
         */
        public Instant getTime() {
            return timestamp;
        }

        /**
         * Returns the values.
         *
         * @return the number[]
         */
        @SuppressWarnings("PMD.MethodReturnsInternalArray")
        public Number[] getValues() {
            return values;
        }

        /**
         * Returns `true` if both entries have the same values.
         *
         * @param other the other
         * @return true, if successful
         */
        public boolean valuesEqual(Entry other) {
            return Arrays.equals(values, other.values);
        }
    }
}
