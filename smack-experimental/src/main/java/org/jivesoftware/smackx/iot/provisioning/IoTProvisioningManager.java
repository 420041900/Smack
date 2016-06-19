/**
 *
 * Copyright 2016 Florian Schmaus
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jivesoftware.smackx.iot.provisioning;

import java.util.Map;
import java.util.WeakHashMap;

import org.jivesoftware.smack.Manager;
import org.jivesoftware.smack.XMPPConnection;

public final class IoTProvisioningManager extends Manager {

    private static Map<XMPPConnection, IoTProvisioningManager> INSTANCES = new WeakHashMap<>();

    public static synchronized IoTProvisioningManager getInstanceFor(XMPPConnection connection) {
        IoTProvisioningManager manager = INSTANCES.get(connection);
        if (manager == null) {
            manager = new IoTProvisioningManager(connection);
            INSTANCES.put(connection, manager);
        }
        return manager;
    }

    private IoTProvisioningManager(XMPPConnection connection) {
        super(connection);
    }

}
