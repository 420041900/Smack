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
package org.jivesoftware.smackx.iot.data;

import java.util.Map;
import java.util.WeakHashMap;

import org.jivesoftware.smack.Manager;
import org.jivesoftware.smack.XMPPConnection;

public final class IoTDataManager extends Manager {

    private static Map<XMPPConnection, IoTDataManager> INSTANCES = new WeakHashMap<>();

    public static synchronized IoTDataManager getInstanceFor(XMPPConnection connection) {
        IoTDataManager manager = INSTANCES.get(connection);
        if (manager == null) {
            manager = new IoTDataManager(connection);
            INSTANCES.put(connection, manager);
        }
        return manager;
    }

    private IoTDataManager(XMPPConnection connection) {
        super(connection);
    }

}
