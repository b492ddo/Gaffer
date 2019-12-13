/*
 * Copyright 2019 Crown Copyright
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

package uk.gov.gchq.gaffer.script.operation.container;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.gov.gchq.gaffer.jsonserialisation.JSONSerialiser;
import uk.gov.gchq.gaffer.script.operation.generator.RandomPortGenerator;
import uk.gov.gchq.gaffer.script.operation.util.DockerClientSingleton;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

public class LocalDockerContainer implements Container {
    private static final Logger LOGGER = LoggerFactory.getLogger(LocalDockerContainer.class);

    private static final String LOCALHOST = "127.0.0.1";
    private static final Integer ONE_SECOND = 1000;
    private static final Integer TIMEOUT_100 = 100;
    private static final Integer TIMEOUT_200 = 200;
    private static final Integer MAX_BYTES = 65000;
    private static final Integer MAX_TRIES = 100;

    private Socket clientSocket = null;
    private String containerId;
    private DataInputStream inputStream;
    private DataOutputStream outputStream;
    private int port;

    public LocalDockerContainer(final String containerId, final int port) {
        this.containerId = containerId;
        this.port = port;
    }

    /**
     * Sends data to the docker container
     *
     * @param data             the data being sent
     */
    @Override
    public void sendData(final Iterable data) {
        LOGGER.info("Attempting to connect with the container...");

        sleep(ONE_SECOND);
        // The container will need some time to start up, so keep trying to connect and check
        // that its ready to receive data.
        Exception error = null;
        for (int i = 0; i < MAX_TRIES; i++) {
            try {
                // Connect to the container
                clientSocket = new Socket(LOCALHOST, port);
                LOGGER.info("Connected to container port at {}", clientSocket.getRemoteSocketAddress());

                // Check the container is ready
                inputStream = getInputStream(clientSocket);
                LOGGER.info("Container ready status: {}", inputStream.readBoolean());

                // Send the data
                OutputStream outToContainer = clientSocket.getOutputStream();
                outputStream = new DataOutputStream(outToContainer);
                boolean firstObject = true;
                for (final Object current : data) {
                    if (firstObject) {
                        outputStream.writeUTF("[" + new String(JSONSerialiser.serialise(current)));
                        firstObject = false;
                    } else {
                        outputStream.writeUTF(", " + new String(JSONSerialiser.serialise(current)));
                    }
                }
                outputStream.writeUTF("]");
                LOGGER.info("Sending data to docker container from {}", clientSocket.getLocalSocketAddress() + "...");

                outputStream.flush();
                break;
            } catch (final IOException e) {
                LOGGER.error(e.getMessage());
                error = e;
                sleep(TIMEOUT_100);
            }
        }
        // Only print an error if it still fails after many tries
        if (error != null) {
            LOGGER.error(error.getMessage());
            RandomPortGenerator.getInstance().releasePort(port);
            DockerClientSingleton.close();
        }
    }

    /**
     * Retrieves the length of the data being received from the container, then retrieves the data itself as a StringBuilder
     *
     * @return StringBuilder dataReceived
     */
    @Override
    public StringBuilder receiveData() {
        // First get the length of the data coming from the container. Keep trying until the container is ready.
        LOGGER.info("Inputstream is: {}", inputStream);
        int incomingDataLength = getIncomingDataLength();

        StringBuilder dataReceived = new StringBuilder();
        dataReceived = getDataReceived(incomingDataLength, dataReceived);

        try {
            if (clientSocket != null) {
                clientSocket.close();
            }
        } catch (final IOException e) {
            LOGGER.error(e.getMessage());
        }

        return dataReceived;
    }

    private StringBuilder getDataReceived(final int incomingDataLength, final StringBuilder dataReceived) {
        // If it failed to get the length of the incoming data then show the error, otherwise return the data.
        StringBuilder dataRecvd = dataReceived;

        if (incomingDataLength == 0) {
            LOGGER.info("Connection failed, stopping the container...");
        } else {
            try {
                // Get the data
                for (int i = 0; i < incomingDataLength / MAX_BYTES; i++) {
                    dataReceived.append(inputStream.readUTF());
                }
                dataReceived.append(inputStream.readUTF());
                dataRecvd = checkIfDataReceivedBeginsWithError(dataReceived);
            } catch (final IOException e) {
                LOGGER.error(e.getMessage());
                DockerClientSingleton.close();
            }
        }
        try {
            if (clientSocket != null) {
                clientSocket.close();
            }
        } catch (final IOException e) {
            LOGGER.error(e.getMessage());
    }
        return dataRecvd;
    }

    private StringBuilder checkIfDataReceivedBeginsWithError(final StringBuilder dataReceived) {
        // Show the error message if the script failed and return no data
        StringBuilder dataRecvd = dataReceived;

        if (dataReceived.subSequence(0, 5) == "Error") {
            LOGGER.info(dataReceived.subSequence(5, dataReceived.length()).toString());
            dataRecvd = null;
        }
        return dataRecvd;
    }

    private int getIncomingDataLength() {
        int incomingDataLength = 0;

        if (clientSocket != null && inputStream != null) {
            int tries = 0;
            while (tries < MAX_TRIES) {
                try {
                    incomingDataLength = inputStream.readInt();
                    LOGGER.info("Length of container...{}", incomingDataLength);
                    break;
                } catch (final IOException e) {
                    tries += 1;
                    LOGGER.error(e.getMessage());
                    sleep(TIMEOUT_200);
                }
            }
        }
        return incomingDataLength;
    }

    @Override
    public String getContainerId() {
        return containerId;
    }

    @Override
    public int getPort() {
        return port;
    }

    private DataInputStream getInputStream(final Socket clientSocket) throws IOException {
        return new DataInputStream(clientSocket.getInputStream());
    }

    private void sleep(final Integer time) {
        try {
            Thread.sleep(time);
        } catch (final InterruptedException e) {
            LOGGER.error(e.getMessage());
        }
    }
}
