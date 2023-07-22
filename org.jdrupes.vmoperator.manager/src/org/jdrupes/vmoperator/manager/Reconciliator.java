package org.jdrupes.vmoperator.manager;

import org.jgrapes.core.Channel;
import org.jgrapes.core.Component;
import org.jgrapes.core.annotation.Handler;

public class Reconciliator extends Component {

    public Reconciliator(Channel componentChannel) {
        super(componentChannel);
    }

    @Handler
    public void onVmChanged(VmChangedEvent event, WatchChannel channel) {
        event = null;
    }

}
