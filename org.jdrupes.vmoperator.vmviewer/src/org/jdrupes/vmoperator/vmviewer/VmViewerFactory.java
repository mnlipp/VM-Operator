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

package org.jdrupes.vmoperator.vmviewer;

import java.util.Map;
import java.util.Optional;
import org.jgrapes.core.Channel;
import org.jgrapes.core.ComponentType;
import org.jgrapes.webconsole.base.ConletComponentFactory;

/**
 * The factory service for {@link VmViewer}s.
 */
public class VmViewerFactory implements ConletComponentFactory {

    /*
     * (non-Javadoc)
     * 
     * @see org.jgrapes.core.ComponentFactory#componentType()
     */
    @Override
    public Class<? extends ComponentType> componentType() {
        return VmViewer.class;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.jgrapes.core.ComponentFactory#create(org.jgrapes.core.Channel,
     * java.util.Map)
     */
    @Override
    public Optional<ComponentType> create(Channel componentChannel,
            Map<?, ?> properties) {
        return Optional.of(new VmViewer(componentChannel));
    }

}
