/**
 *
 * Copyright 2014 Vyacheslav Blinov, 2017 Florian Schmaus.
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
package org.jivesoftware.smack.debugger;

import org.jivesoftware.smack.XMPPConnection;

public interface SmackDebuggerFactory {
    /**
     * Initialize the new SmackDebugger instance.
     *
     * @param connection the XMPP connection this debugger is going to get attached to.
     * @throws IllegalArgumentException if the SmackDebugger can't be loaded.
     */
    SmackDebugger create(XMPPConnection connection) throws IllegalArgumentException;
}
