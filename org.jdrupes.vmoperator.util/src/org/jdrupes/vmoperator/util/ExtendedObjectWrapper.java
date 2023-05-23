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

import com.fasterxml.jackson.databind.ObjectMapper;
import freemarker.template.DefaultObjectWrapper;
import freemarker.template.TemplateModel;
import freemarker.template.TemplateModelException;
import freemarker.template.Version;
import java.util.Map;

public class ExtendedObjectWrapper extends DefaultObjectWrapper {

    private ObjectMapper mapper;

    public ExtendedObjectWrapper(Version incompatibleImprovements,
            ObjectMapper mapper) {
        super(incompatibleImprovements);
        this.mapper = mapper;
    }

    @Override
    protected TemplateModel handleUnknownType(final Object obj)
            throws TemplateModelException {
        if (obj instanceof Dto) {
            var asMap = mapper.convertValue(obj, Map.class);
            return this.wrap(asMap);
        }
        return super.handleUnknownType(obj);
    }

}
