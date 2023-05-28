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

import freemarker.ext.util.WrapperTemplateModel;
import freemarker.template.ObjectWrapper;
import freemarker.template.TemplateHashModel;
import freemarker.template.TemplateModel;
import freemarker.template.TemplateModelException;
import freemarker.template.WrappingTemplateModel;

/**
 * Wraps a DTO in a {@link TemplateHashModel}.
 */
public class DtoTemplateModel extends WrappingTemplateModel
        implements WrapperTemplateModel, TemplateHashModel {

    private final Dto dto;

    /**
     * Instantiates a new DTO template model.
     *
     * @param objectWrapper the object wrapper
     * @param dto the dto
     */
    public DtoTemplateModel(ObjectWrapper objectWrapper, Dto dto) {
        super(objectWrapper);
        this.dto = dto;
    }

    @Override
    @SuppressWarnings("PMD.PreserveStackTrace")
    public TemplateModel get(String key) throws TemplateModelException {
        try {
            var field = dto.getClass().getDeclaredField(key);
            return wrap(field.get(dto));
        } catch (NoSuchFieldException | SecurityException e) {
            throw new TemplateModelException("No Field " + key
                + " in class " + dto.getClass());
        } catch (IllegalArgumentException | IllegalAccessException e) {
            throw new TemplateModelException("Cannot access field " + key
                + " in class " + dto.getClass());
        }
    }

    @Override
    public boolean isEmpty() throws TemplateModelException {
        return false;
    }

    @Override
    public Object getWrappedObject() {
        return dto;
    }

}
