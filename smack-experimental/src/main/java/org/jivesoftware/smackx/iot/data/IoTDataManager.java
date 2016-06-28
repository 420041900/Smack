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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jivesoftware.smack.ConnectionCreationListener;
import org.jivesoftware.smack.Manager;
import org.jivesoftware.smack.PacketCollector;
import org.jivesoftware.smack.SmackException.NoResponseException;
import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPConnectionRegistry;
import org.jivesoftware.smack.XMPPException.XMPPErrorException;
import org.jivesoftware.smack.filter.StanzaFilter;
import org.jivesoftware.smack.iqrequest.AbstractIqRequestHandler;
import org.jivesoftware.smack.iqrequest.IQRequestHandler.Mode;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smackx.iot.Thing;
import org.jivesoftware.smackx.iot.data.element.IoTDataField;
import org.jivesoftware.smackx.iot.data.element.IoTDataReadOutAccepted;
import org.jivesoftware.smackx.iot.data.element.IoTDataRequest;
import org.jivesoftware.smackx.iot.data.element.IoTFieldsExtension;
import org.jivesoftware.smackx.iot.data.filter.IoTFieldsExtensionFilter;
import org.jivesoftware.smackx.iot.element.NodeInfo;
import org.jxmpp.jid.EntityFullJid;

public final class IoTDataManager extends Manager {

    private static final Logger LOGGER = Logger.getLogger(IoTDataManager.class.getName());

    private static final Map<XMPPConnection, IoTDataManager> INSTANCES = new WeakHashMap<>();

    // Ensure a IoTDataManager exists for every connection.
    static {
        XMPPConnectionRegistry.addConnectionCreationListener(new ConnectionCreationListener() {
            public void connectionCreated(XMPPConnection connection) {
                getInstanceFor(connection);
            }
        });
    }

    public static synchronized IoTDataManager getInstanceFor(XMPPConnection connection) {
        IoTDataManager manager = INSTANCES.get(connection);
        if (manager == null) {
            manager = new IoTDataManager(connection);
            INSTANCES.put(connection, manager);
        }
        return manager;
    }

    private final AtomicInteger nextSeqNr = new AtomicInteger();

    private final Map<NodeInfo, Thing> things = new ConcurrentHashMap<>();

    private IoTDataManager(XMPPConnection connection) {
        super(connection);

        connection.registerIQRequestHandler(new AbstractIqRequestHandler(IoTDataRequest.ELEMENT,
                        IoTDataRequest.NAMESPACE, IQ.Type.get, Mode.async) {
            @Override
            public IQ handleIQRequest(IQ iqRequest) {
                // TODO verify that iqRequest.from is friend.
                // TODO return error if not at least one thing registered.
                final IoTDataRequest dataRequest = (IoTDataRequest) iqRequest;

                if (!dataRequest.isMomentary()) {
                    // TODO return error IQ that non momentary requests are not implemented yet.
                    return null;
                }

                final Thing thing = things.get(NodeInfo.EMPTY);
                ThingMomentaryReadOutRequest readOutRequest = thing.getMomentaryReadOutRequestHandler();
                if (readOutRequest == null) {
                    // TODO Thing does not provide momentary read-out
                    return null;
                }

                // Callback hell begins here. :) XEP-0323 decouples the read-out results from the IQ result. I'm not
                // sure if I would have made the same design decision but the reasons where likely being able to get a
                // fast read-out acknowledgement back to the requester even with sensors that take "a long time" to
                // read-out their values. I had designed that as special case and made the "results in IQ response" the
                // normal case.
                readOutRequest.momentaryReadOutRequest(new ThingMomentaryReadOutResult() {
                    @Override
                    public void momentaryReadOut(List<? extends IoTDataField> results) {
                        IoTFieldsExtension iotFieldsExtension = IoTFieldsExtension.buildFor(dataRequest.getSequenceNr(), true, thing.getNodeInfo(), results);
                        Message message = new Message(dataRequest.getFrom());
                        message.addExtension(iotFieldsExtension);
                        try {
                            connection().sendStanza(message);
                        }
                        catch (NotConnectedException | InterruptedException e) {
                            LOGGER.log(Level.SEVERE, "Could not send read-out response " + message, e);
                        }
                    }
                });

                return new IoTDataReadOutAccepted(dataRequest);
            }
        });
    }

    public void installThing(Thing thing) {
        things.put(thing.getNodeInfo(), thing);
    }

    public Thing uninstallThing(Thing thing) {
        return uninstallThing(thing.getNodeInfo());
    }

    public Thing uninstallThing(NodeInfo nodeInfo) {
        return things.remove(nodeInfo);
    }

    public List<IoTFieldsExtension> requestMomentaryValuesReadOut(EntityFullJid jid)
                    throws NoResponseException, XMPPErrorException, NotConnectedException, InterruptedException {
        final XMPPConnection connection = connection();
        final int seqNr = nextSeqNr.incrementAndGet();
        IoTDataRequest iotDataRequest = new IoTDataRequest(seqNr, true);
        iotDataRequest.setTo(jid);

        StanzaFilter doneFilter = new IoTFieldsExtensionFilter(seqNr, true);
        StanzaFilter dataFilter = new IoTFieldsExtensionFilter(seqNr, false);

        // Setup the IoTFieldsExtension message collectors before sending the IQ to avoid a data race.
        PacketCollector doneCollector = connection.createPacketCollector(doneFilter);

        PacketCollector.Configuration dataCollectorConfiguration = PacketCollector.newConfiguration().setStanzaFilter(
                        dataFilter).setCollectorToReset(doneCollector);
        PacketCollector dataCollector = connection.createPacketCollector(dataCollectorConfiguration);

        try {
            connection.createPacketCollectorAndSend(iotDataRequest).nextResultOrThrow();
            // Wait until a message with an IoTFieldsExtension and the done flag comes in.
            doneCollector.nextResult();
        }
        finally {
            // Ensure that the two collectors are canceled in any case.
            dataCollector.cancel();
            doneCollector.cancel();
        }

        int collectedCount = dataCollector.getCollectedCount();
        List<IoTFieldsExtension> res = new ArrayList<>(collectedCount);
        for (int i = 0; i < collectedCount; i++) {
            Message message = dataCollector.pollResult();
            IoTFieldsExtension iotFieldsExtension = IoTFieldsExtension.from(message);
            res.add(iotFieldsExtension);
        }

        return res;
    }
}
